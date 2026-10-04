package com.backrooms.client.glow;

import com.backrooms.BackroomsMod;
import com.backrooms.client.postfx.DepthOcclusion;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostChainConfig;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.UniformValue;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

/**
 * Menjalankan post effect glow di tengah portal yang sedang mengisi energi.
 *
 * <p>Di 26.2, uniform sebuah PostPass dibekukan saat chain dibuat. Agar glow bisa mengikuti kamera dan berkedip tanpa
 * membangun chain baru tiap frame, data dinamis (posisi layar, radius, intensitas, warna) dikirim lewat tekstur kecil
 * yang ditulis ulang tiap frame. Chain dibuat sekali: {@code portal_glow.fsh} ke target swap, lalu blit ke main.
 * Format tekstur data ada di komentar header shader.
 */
public final class PortalGlowRenderer {
	private static final int MAX_SOURCES = 4;
	private static final int DATA_WIDTH = DepthOcclusion.WIDTH;
	/** Baris setelah sumber: parameter efek kamera (warp/blur, exposure, flashbang), lalu posisi bayangan sisa flashbang. */
	private static final int ROW_CAMERA = MAX_SOURCES;
	private static final int ROW_FLASH = MAX_SOURCES + 1;
	/** Oklusi depth per piksel (API DepthOcclusion); baris pertamanya harus sama dengan OCCLUSION_ROW di portal_glow.fsh. */
	private static final DepthOcclusion OCCLUSION = DepthOcclusion.at(MAX_SOURCES + 2, MAX_SOURCES).range(0.3f, 0.6f).planeThickness(0.6f);
	/** Radius bidang portal (setengah tinggi 2,5 + margin) untuk oklusi: di dalamnya hanya permukaan di depan bidang portal (melebihi ketebalan bingkai) yang memotong glow. */
	private static final float PORTAL_PLANE_RADIUS_BLOCKS = 2.6f;
	private static final List<DepthOcclusion.Source> OCCLUDERS = new ArrayList<>(MAX_SOURCES);
	private static final int DATA_HEIGHT = OCCLUSION.endRow();

	/** Tempat tekstur data didaftarkan. PostChain me-resolve input tekstur ke {@code textures/effect/<path>.png}. */
	private static final Identifier DATA_TEXTURE_ID = Identifier.fromNamespaceAndPath(BackroomsMod.MOD_ID, "textures/effect/portal_glow_data.png");
	private static final Identifier DATA_INPUT_ID = Identifier.fromNamespaceAndPath(BackroomsMod.MOD_ID, "portal_glow_data");
	private static final Identifier SCREENQUAD = Identifier.parse("minecraft:core/screenquad");
	private static final Identifier MAIN = Identifier.parse("minecraft:main");
	private static final Identifier SWAP = Identifier.fromNamespaceAndPath(BackroomsMod.MOD_ID, "portal_glow_swap");
	/** Hasil frame sebelumnya (target persisten) untuk jejak flashbang. */
	private static final Identifier HISTORY = Identifier.fromNamespaceAndPath(BackroomsMod.MOD_ID, "portal_glow_history");

	// --- Tampilan: ubah di sini untuk menyetel glow ---
	// Fase isi energi: glow muncul perlahan, makin lebar dan makin terang sampai portal terbuka.
	private static final float START_RADIUS_BLOCKS = 0.5f;
	private static final float PEAK_RADIUS_BLOCKS = 28.0f;
	private static final float START_INTENSITY = 0.04f;
	private static final float PEAK_INTENSITY = 1.0f;
	/** Bloom naik pelan sampai fraksi durasi ini (detik 9 dari 10), lalu melebar mendadak (tetap mulus, ada easing). */
	static final float BURST_START_FRACTION = 0.9f;
	/** Tingkat bloom (0..1) yang sudah tercapai sesaat sebelum burst dimulai. */
	private static final float PRE_BURST_LEVEL = 0.12f;
	// Fase setelah portal terbuka: radius mengecil dan intensitas turun perlahan.
	private static final float END_RADIUS_BLOCKS = 2.0f;
	static final float GLOW_DECAY_TICKS = 160.0f;
	private static final float FADE_IN_TICKS = 8.0f;
	// Efek kamera (distorsi + blur) di dekat portal.
	private static final float WARP_RANGE_BLOCKS = 7.0f;
	// Exposure: saat bloom besar dan terang, ruangan di luar bloom terlihat lebih gelap (kekuatan maksimum di portal_glow.fsh).
	/** Tingkat bloom (0..1) saat ruangan mulai menggelap; di bawah ini (fase isi pelan) tidak ada efek. */
	private static final float EXPOSURE_START_LEVEL = 0.15f;
	/** Di luar jarak 1.15x radius bloom (minimal ini) tidak ada efek; penuh di dalam 0.35x. */
	private static final float EXPOSURE_MIN_REACH_BLOCKS = 8.0f;
	/** Menghadap jauh dari bloom tetap menggelap sedikit (porsi dari penuh). */
	private static final float EXPOSURE_BACK_FACTOR = 0.25f;
	/** Persistensi jejak per 1/60 detik pada kekuatan penuh: 0.98 = tiap frame 98% gambar lama dipertahankan (jejak sekitar 0,6 detik). */
	private static final float TRAIL_PERSISTENCE = 0.98f;
	/** Radius bayangan sisa flashbang (blok di posisi portal, diproyeksikan ke layar saat flash terjadi). */
	private static final float GHOST_RADIUS_BLOCKS = 5.0f;
	/** Warna tengah glow (kuning kehijauan). Inti memutih dan tepi lebih hijau; gradiennya dihitung di portal_glow.fsh. */
	private static final int TINT_R = 226;
	private static final int TINT_G = 255;
	private static final int TINT_B = 120;
	/** Di dekat portal intensitas diturunkan sampai porsi ini agar layar tidak terbakar putih. */
	private static final float MIN_NEAR_FACTOR = 0.35f;
	private static final float NEAR_DISTANCE_BLOCKS = 3.0f;
	private static final double MAX_RANGE_BLOCKS = 96.0;
	/** Kecepatan fade saat tengah portal tertutup / terlihat (per detik). */
	private static final float VISIBILITY_RATE = 10.0f;

	private static final Projection PROJECTION = new Projection();
	private static final Matrix4f MATRIX = new Matrix4f();
	private static final Vector4f TMP = new Vector4f();

	private static DynamicTexture dataTexture;
	private static ProjectionMatrixBuffer projectionBuffer;
	private static PostChain chain;
	private static boolean failed;
	private static long lastFrameNanos;
	/** False sampai chain pernah memproses satu frame berturut-turut; history lama (dari efek sebelumnya) tidak dipakai. */
	private static boolean historyValid;

	private PortalGlowRenderer() {
	}

	/** Posisi layar ternormalisasi (asal kiri-atas), radius sebagai fraksi tinggi layar, dan intensitas akhir. */
	private record Glow(float x, float y, float radius, float intensity, float spread, DepthOcclusion.Source occluder) {
	}

	/** Dipanggil tiap frame setelah dunia tergambar dan sebelum GUI. Tidak melakukan apa-apa jika tak ada sumber. */
	public static void render(RenderTarget main, CrossFrameResourcePool pool, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		LocalPlayer player = minecraft.player;
		List<PortalGlowManager.Source> sources = PortalGlowManager.sources();
		if (level == null || player == null || failed) {
			lastFrameNanos = 0L;
			historyValid = false;
			return;
		}
		double nowTicks = level.getGameTime() + deltaTracker.getGameTimeDeltaPartialTick(false);
		if (sources.isEmpty() && !PortalFlash.active(nowTicks)) {
			lastFrameNanos = 0L;
			historyValid = false;
			return;
		}

		long nowNanos = System.nanoTime();
		float frameSeconds = lastFrameNanos == 0L ? 0.016f : Math.min(0.1f, (nowNanos - lastFrameNanos) / 1.0e9f);
		lastFrameNanos = nowNanos;

		Camera camera = minecraft.gameRenderer.mainCamera();
		if (PortalFlash.needsCapture()) {
			captureGhost(camera, main.width, main.height);
		}
		PortalFlash.updateView(level, camera.position(), frameSeconds);
		List<Glow> glows = new ArrayList<>(sources.size());
		float warp = 0.0f;
		float dark = 0.0f;
		for (PortalGlowManager.Source source : sources) {
			updateVisibility(source, level, camera.position(), frameSeconds);
			Glow glow = evaluate(source, level, player, camera, nowTicks, frameSeconds, main.width, main.height);
			if (glow != null) {
				glows.add(glow);
			}
			warp = Math.max(warp, warpAmount(source, camera, nowTicks));
			dark = Math.max(dark, exposureAmount(source, camera, nowTicks));
		}
		float flashWhite = PortalFlash.white(nowTicks);
		float ghost = PortalFlash.ghost(nowTicks);
		float trailBase = PortalFlash.trail(nowTicks);
		boolean idle = glows.isEmpty() && warp <= 0.001f && dark <= 0.001f && flashWhite <= 0.001f && ghost <= 0.001f && trailBase <= 0.001f;
		if (idle || !ensureResources(minecraft)) {
			historyValid = false;
			return;
		}
		// Persistensi per frame disesuaikan dengan waktu frame agar panjang jejak sama di 60 maupun 240 FPS.
		float trail = historyValid ? trailBase * (float) Math.pow(TRAIL_PERSISTENCE, frameSeconds * 60.0f) : 0.0f;

		// Yang paling dekat (radius layar terbesar) lebih dulu: hanya MAX_SOURCES baris yang muat di tekstur data.
		glows.sort(Comparator.comparingDouble(Glow::radius).reversed());
		writeData(camera, glows, warp, (float) (nowTicks / 80.0 % 1.0), dark, flashWhite, ghost, trail);
		chain.process(main, pool);
		historyValid = true;
	}

	private static Glow evaluate(
		PortalGlowManager.Source source, ClientLevel level, LocalPlayer player, Camera camera,
		double nowTicks, float frameSeconds, int width, int height
	) {
		Vec3 eye = camera.position();
		Vec3 target = Vec3.atCenterOf(source.center);
		double distanceSq = eye.distanceToSqr(target);
		if (distanceSq > MAX_RANGE_BLOCKS * MAX_RANGE_BLOCKS) {
			return null;
		}

		// Oklusi glow dihitung per piksel di portal_glow.fsh dari depth buffer, jadi bloom yang tertutup sebagian tetap tergambar
		// di bagian yang tidak tertutup. source.visibility hanya dipakai efek layar penuh (warp, exposure, flashbang).
		double elapsedTicks = nowTicks - source.startTick;
		float progress = Mth.clamp((float) (elapsedTicks / source.durationTicks), 0.0f, 1.0f);
		float rise = riseLevel(progress);
		float radiusBlocks = Mth.lerp(rise, START_RADIUS_BLOCKS, PEAK_RADIUS_BLOCKS);
		float envelope = Mth.lerp(rise, START_INTENSITY, PEAK_INTENSITY);
		float flicker = PortalGlowFlicker.value(nowTicks / 20.0, source.seed);
		// Spread 0..1: seberapa lebar plateau terang bloom. Melebar bersama burst agar seluruh portal tertutup.
		float spread = PortalGlowFlicker.smooth(1.0f, (progress - BURST_START_FRACTION) / (1.0f - BURST_START_FRACTION));

		if (source.openedTick >= 0L) {
			float decay = PortalGlowFlicker.smooth(GLOW_DECAY_TICKS, (float) Math.max(0.0, nowTicks - source.openedTick));
			radiusBlocks = Mth.lerp(decay, PEAK_RADIUS_BLOCKS, END_RADIUS_BLOCKS);
			envelope = PEAK_INTENSITY * (1.0f - decay);
			flicker = Mth.lerp(decay, flicker, 1.0f);
			spread = Mth.lerp(decay, 1.0f, 0.35f);
		}
		float[] projected = project(camera, target, radiusBlocks, width, height);
		if (projected == null) {
			return null;
		}

		float fadeIn = PortalGlowFlicker.smooth(FADE_IN_TICKS, (float) elapsedTicks);
		float near = Mth.clamp((float) Math.sqrt(distanceSq) / NEAR_DISTANCE_BLOCKS, MIN_NEAR_FACTOR, 1.0f);
		float intensity = envelope * flicker * fadeIn * near;

		return new Glow(projected[0] / width, projected[1] / height, Math.min(2.0f, projected[2] / height), intensity, spread,
			DepthOcclusion.Source.plane(target, portalNormal(source), PORTAL_PLANE_RADIUS_BLOCKS));
	}

	/**
	 * Memperbarui keterlihatan portal dari kamera. Efek layar penuh tanpa depth test akan tembus dinding, jadi
	 * efek sampingnya (warp, exposure, flashbang, jejak) hanya digambar selagi portal terlihat (bloom sendiri memakai depth buffer per piksel); berlindung di balik blok
	 * memudar cepat sampai 0. Kaca, iron bars, dan blok transparan lain tidak dianggap penutup.
	 */
	private static void updateVisibility(PortalGlowManager.Source source, ClientLevel level, Vec3 eye, float frameSeconds) {
		boolean visible = PortalSight.visible(level, eye, Vec3.atCenterOf(source.center));
		source.visibility = Mth.lerp(Math.min(1.0f, frameSeconds * VISIBILITY_RATE), source.visibility, visible ? 1.0f : 0.0f);
		if (source.visibility < 0.01f) {
			source.visibility = 0.0f;
		}
	}

	/**
	 * Kurva bloom: naik halus dan pelan sampai {@link #PRE_BURST_LEVEL} pada {@link #BURST_START_FRACTION}, lalu melonjak
	 * ke 1.0 di sisa durasi dengan easing smoothstep (tanpa patahan di awal maupun akhir).
	 */
	static float riseLevel(float progress) {
		float slow = PRE_BURST_LEVEL * PortalGlowFlicker.smooth(1.0f, Math.min(1.0f, progress / BURST_START_FRACTION));
		float burst = PortalGlowFlicker.smooth(1.0f, (progress - BURST_START_FRACTION) / (1.0f - BURST_START_FRACTION));
		return slow + (1.0f - slow) * burst;
	}

	/**
	 * Kekuatan distorsi+blur 0..1. Naik bertahap sejak glow mulai (mengikuti progres pengisian), penuh sampai glow
	 * selesai meredup, lalu memudar bertahap selama sisa {@link PortalGlowManager#AFTER_OPEN_TICKS} (ekor 5 detik).
	 * Makin kuat saat player mendekat.
	 */
	private static float warpAmount(PortalGlowManager.Source source, Camera camera, double nowTicks) {
		float time;
		if (source.openedTick < 0L) {
			float progress = Mth.clamp((float) ((nowTicks - source.startTick) / source.durationTicks), 0.0f, 1.0f);
			time = PortalGlowFlicker.smooth(1.0f, progress);
		} else {
			float since = (float) Math.max(0.0, nowTicks - source.openedTick);
			float tail = PortalGlowManager.AFTER_OPEN_TICKS - GLOW_DECAY_TICKS;
			time = since <= GLOW_DECAY_TICKS ? 1.0f : 1.0f - PortalGlowFlicker.smooth(tail, since - GLOW_DECAY_TICKS);
		}
		float distance = (float) camera.position().distanceTo(Vec3.atCenterOf(source.center));
		float near = Mth.clamp(1.0f - distance / WARP_RANGE_BLOCKS, 0.0f, 1.0f);
		return time * near * near * (3.0f - 2.0f * near) * source.visibility;
	}

	/**
	 * Seberapa gelap ruangan di luar bloom 0..1 (efek exposure kamera, seperti menyorot api korek di siang hari: latar
	 * jadi gelap sementara sumber cahaya tetap terang). Naik bersama tingkat bloom, jadi baru terasa saat bloom besar
	 * dan terang; mengikuti jangkauan bloom, arah pandang, dan keterlihatan portal.
	 */
	private static float exposureAmount(PortalGlowManager.Source source, Camera camera, double nowTicks) {
		float level;
		float radiusBlocks;
		if (source.openedTick < 0L) {
			float progress = Mth.clamp((float) ((nowTicks - source.startTick) / source.durationTicks), 0.0f, 1.0f);
			float rise = riseLevel(progress);
			level = PortalGlowFlicker.smooth(1.0f - EXPOSURE_START_LEVEL, rise - EXPOSURE_START_LEVEL);
			radiusBlocks = Mth.lerp(rise, START_RADIUS_BLOCKS, PEAK_RADIUS_BLOCKS);
		} else {
			float decay = PortalGlowFlicker.smooth(GLOW_DECAY_TICKS, (float) Math.max(0.0, nowTicks - source.openedTick));
			level = 1.0f - decay;
			radiusBlocks = Mth.lerp(decay, PEAK_RADIUS_BLOCKS, END_RADIUS_BLOCKS);
		}
		if (level <= 0.0f) {
			return 0.0f;
		}
		// Napas kedipan bloom ikut terasa di exposure, tetapi redam agar layar tidak berkedip kasar.
		float flicker = Mth.clamp(PortalGlowFlicker.value(nowTicks / 20.0, source.seed), 0.0f, 1.0f);
		level *= 0.85f + 0.15f * flicker;

		Vec3 eye = camera.position();
		Vec3 toTarget = Vec3.atCenterOf(source.center).subtract(eye);
		float distance = (float) toTarget.length();
		float reach = Math.max(radiusBlocks, EXPOSURE_MIN_REACH_BLOCKS);
		float proximity = 1.0f - PortalGlowFlicker.smooth(0.8f * reach, distance - 0.35f * reach);

		// Menghadap bloom = penuh; membelakangi = tetap sedikit. Di dekat titik portal arah tidak lagi dipakai.
		Vector3fc forward = camera.forwardVector();
		float dot = distance > 1.0e-3f
			? (float) (forward.x() * toTarget.x + forward.y() * toTarget.y + forward.z() * toTarget.z) / distance
			: 1.0f;
		float facing = PortalGlowFlicker.smooth(0.9f, dot + 0.3f);
		facing = Mth.lerp(PortalGlowFlicker.smooth(2.0f, distance), 1.0f, facing);
		float view = Mth.lerp(facing, EXPOSURE_BACK_FACTOR, 1.0f);

		return level * proximity * view * source.visibility;
	}

	/** Menyimpan posisi layar tempat bloom terlihat saat flash terjadi; bayangan sisa tertinggal di sana (tetap di layar, tidak ikut kamera). */
	private static void captureGhost(Camera camera, int width, int height) {
		float[] projected = project(camera, PortalFlash.target(), GHOST_RADIUS_BLOCKS, width, height);
		if (projected == null) {
			PortalFlash.setGhost(0.0f, 0.0f, 0.0f);
			return;
		}
		PortalFlash.setGhost(projected[0] / width, projected[1] / height, Mth.clamp(projected[2] / height, 0.12f, 0.6f));
	}

	/** Proyeksi titik dunia ke piksel layar (asal kiri-atas) beserta radius piksel pada kedalaman itu; null jika di luar pandangan. */
	private static float[] project(Camera camera, Vec3 target, float radiusBlocks, int width, int height) {
		Vec3 eye = camera.position();
		camera.getViewRotationProjectionMatrix(MATRIX);
		TMP.set((float) (target.x - eye.x), (float) (target.y - eye.y), (float) (target.z - eye.z), 1.0f);
		MATRIX.transform(TMP);
		if (TMP.w <= 0.0f) {
			return null;
		}
		float px = (TMP.x / TMP.w * 0.5f + 0.5f) * width;
		float py = (1.0f - (TMP.y / TMP.w * 0.5f + 0.5f)) * height;
		float pixelsPerBlock = Math.abs(MATRIX.m11()) * 0.5f * height / TMP.w;
		float radius = radiusBlocks * pixelsPerBlock;
		if (px < -radius || px > width + radius || py < -radius || py > height + radius) {
			return null;
		}
		return new float[] {px, py, radius};
	}

	private static void writeData(Camera camera, List<Glow> glows, float warp, float phase, float dark, float flashWhite, float ghost, float trail) {
		NativeImage image = dataTexture.getPixels();
		for (int row = 0; row < MAX_SOURCES; row++) {
			if (row >= glows.size()) {
				for (int column = 0; column < DATA_WIDTH; column++) {
					image.setPixelABGR(column, row, 0);
				}
				continue;
			}
			Glow glow = glows.get(row);
			int x = encode16((glow.x() + 0.5f) / 2.0f);
			int y = encode16((glow.y() + 0.5f) / 2.0f);
			int radius = encode16(glow.radius() / 2.0f);
			int intensity = encode16(glow.intensity());
			image.setPixelABGR(0, row, abgr(x >> 8, x & 0xFF, y >> 8, y & 0xFF));
			image.setPixelABGR(1, row, abgr(radius >> 8, radius & 0xFF, intensity >> 8, intensity & 0xFF));
			// Alpha texel 2 = spread (8 bit).
			image.setPixelABGR(2, row, abgr(TINT_R, TINT_G, TINT_B, Math.round(Mth.clamp(glow.spread(), 0.0f, 1.0f) * 255.0f)));
		}
		// Baris parameter kamera: texel 0 = warp (16 bit) + blur (16 bit), texel 1 = fase waktu 0..1 (16 bit),
		// texel 2 = r,g exposure/gelap (16 bit), b = putih flashbang (8 bit), a = bayangan sisa flashbang (8 bit).
		int warpBits = encode16(warp);
		int phaseBits = encode16(phase);
		int darkBits = encode16(dark);
		image.setPixelABGR(0, ROW_CAMERA, abgr(warpBits >> 8, warpBits & 0xFF, warpBits >> 8, warpBits & 0xFF));
		image.setPixelABGR(1, ROW_CAMERA, abgr(phaseBits >> 8, phaseBits & 0xFF, 0, 255));
		image.setPixelABGR(2, ROW_CAMERA, abgr(darkBits >> 8, darkBits & 0xFF,
			Math.round(Mth.clamp(flashWhite, 0.0f, 1.0f) * 255.0f), Math.round(Mth.clamp(ghost, 0.0f, 1.0f) * 255.0f)));
		// Baris flashbang: posisi layar bayangan sisa (kodenya sama dengan baris sumber) dan radiusnya.
		int ghostX = encode16((PortalFlash.ghostX() + 0.5f) / 2.0f);
		int ghostY = encode16((PortalFlash.ghostY() + 0.5f) / 2.0f);
		int ghostRadius = encode16(PortalFlash.ghostRadius() / 2.0f);
		image.setPixelABGR(0, ROW_FLASH, abgr(ghostX >> 8, ghostX & 0xFF, ghostY >> 8, ghostY & 0xFF));
		image.setPixelABGR(1, ROW_FLASH, abgr(ghostRadius >> 8, ghostRadius & 0xFF, 0, 0));
		// Texel 2 baris flashbang: r = jejak frame sebelumnya (8 bit).
		image.setPixelABGR(2, ROW_FLASH, abgr(Math.round(Mth.clamp(trail, 0.0f, 1.0f) * 255.0f), 0, 0, 0));
		writeDepthRows(image, camera, glows);
		dataTexture.upload();
	}

	/** Normal bidang portal: sumbu horizontal yang tegak lurus sumbu lebar bingkai. */
	private static Vec3 portalNormal(PortalGlowManager.Source source) {
		return source.right.getAxis() == Direction.Axis.X ? new Vec3(0.0, 0.0, 1.0) : new Vec3(1.0, 0.0, 0.0);
	}

	/** Data oklusi (matriks kamera, rentang, bidang tiap sumber) ke tekstur data lewat API DepthOcclusion. */
	private static void writeDepthRows(NativeImage image, Camera camera, List<Glow> glows) {
		OCCLUDERS.clear();
		for (int i = 0; i < glows.size() && i < MAX_SOURCES; i++) {
			OCCLUDERS.add(glows.get(i).occluder());
		}
		OCCLUSION.write(image, camera, OCCLUDERS);
	}

	private static int encode16(float value) {
		return Mth.clamp(Math.round(value * 65535.0f), 0, 65535);
	}

	/** Susunan byte NativeImage: r di byte terendah. */
	private static int abgr(int r, int g, int b, int a) {
		return (a & 0xFF) << 24 | (b & 0xFF) << 16 | (g & 0xFF) << 8 | (r & 0xFF);
	}

	private static boolean ensureResources(Minecraft minecraft) {
		if (chain != null) {
			return true;
		}
		try {
			dataTexture = new DynamicTexture("backrooms portal glow data", DATA_WIDTH, DATA_HEIGHT, true);
			minecraft.getTextureManager().register(DATA_TEXTURE_ID, dataTexture);
			projectionBuffer = new ProjectionMatrixBuffer("backrooms_portal_glow");
			chain = PostChain.load(buildConfig(), minecraft.getTextureManager(), LevelTargetBundle.MAIN_TARGETS,
				Identifier.fromNamespaceAndPath(BackroomsMod.MOD_ID, "portal_glow"), PROJECTION, projectionBuffer);
			return true;
		} catch (Exception e) {
			failed = true;
			BackroomsMod.LOGGER.error("Gagal membangun post chain glow portal; efek dimatikan", e);
			minecraft.getTextureManager().release(DATA_TEXTURE_ID);
			dataTexture = null;
			return false;
		}
	}

	/** Input tekstur data; pass glow dan pass flash memakai tekstur yang sama. */
	private static PostChainConfig.Input dataInput() {
		return new PostChainConfig.TextureInput("Data", DATA_INPUT_ID, DATA_WIDTH, DATA_HEIGHT, false);
	}

	/**
	 * main + history + depth main -> portal_glow.fsh -> swap; swap -> blit -> history (persisten); swap -> portal_flash.fsh -> main.
	 * Vanilla tidak pernah membaca dan menulis target yang sama dalam satu pass.
	 */
	private static PostChainConfig buildConfig() {
		List<PostChainConfig.Input> glowInputs = List.of(
			new PostChainConfig.TargetInput("In", MAIN, false, true),
			dataInput(),
			new PostChainConfig.TargetInput("Hist", HISTORY, false, false),
			// Depth buffer main: oklusi glow per piksel. Urutan input = urutan SamplerInfo di shader.
			OCCLUSION.depthInput("Depth")
		);
		PostChainConfig.Pass glowPass = new PostChainConfig.Pass(
			SCREENQUAD, Identifier.fromNamespaceAndPath(BackroomsMod.MOD_ID, "post/portal_glow"), glowInputs, SWAP, new LinkedHashMap<>());

		// Simpan hasil glow (tanpa overlay flashbang) sebagai history frame berikutnya.
		Map<String, List<UniformValue>> blitUniforms = new LinkedHashMap<>();
		blitUniforms.put("BlitConfig", List.of(new UniformValue.Vec4Uniform(new Vector4f(1.0f, 1.0f, 1.0f, 1.0f))));
		PostChainConfig.Pass historyPass = new PostChainConfig.Pass(
			SCREENQUAD, Identifier.parse("minecraft:post/blit"),
			List.of(new PostChainConfig.TargetInput("In", SWAP, false, false)), HISTORY, blitUniforms);

		// Overlay flashbang dan tulis ke main.
		PostChainConfig.Pass flashPass = new PostChainConfig.Pass(
			SCREENQUAD, Identifier.fromNamespaceAndPath(BackroomsMod.MOD_ID, "post/portal_flash"),
			List.of(new PostChainConfig.TargetInput("In", SWAP, false, false), dataInput()), MAIN, new LinkedHashMap<>());

		Map<Identifier, PostChainConfig.InternalTarget> targets = new LinkedHashMap<>();
		targets.put(SWAP, new PostChainConfig.InternalTarget(Optional.empty(), Optional.empty(), false, 0));
		targets.put(HISTORY, new PostChainConfig.InternalTarget(Optional.empty(), Optional.empty(), true, 0));
		return new PostChainConfig(targets, List.of(glowPass, historyPass, flashPass));
	}
}
