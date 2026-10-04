package com.backrooms.client.glow;

import com.backrooms.client.postfx.LevelMatrices;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import qouteall.imm_ptl.core.portal.Portal;

/**
 * Efek layar portal Magnet (shader {@code portal_distort.fsh}), tiga fase:
 * <ul>
 * <li>pengisian energi: api hijau yang mengalir memutar seperti fluida di dalam bingkai (menggantikan partikel),</li>
 * <li>portal terbuka: distorsi medan magnet melengkung yang permanen selama portal ada, paling kuat sesaat setelah
 * muncul lalu mereda ke tingkat tetap (bukan nol),</li>
 * <li>redstone padam: lengkungan menguat, putih kehijauan bercahaya, lalu portal mengecil ke tengah dan memudar.</li>
 * </ul>
 * Seperti {@link PortalGlowRenderer}, parameter dinamis dikirim lewat tekstur data kecil karena uniform PostPass
 * dibekukan saat chain dibuat.
 */
public final class PortalDistortRenderer {
	private static final int MAX_SOURCES = 4;
	private static final int DATA_WIDTH = DepthOcclusion.WIDTH;
	private static final int ROW_GLOBAL = MAX_SOURCES;
	/** Oklusi depth per piksel (API DepthOcclusion); baris pertamanya harus sama dengan OCCLUSION_ROW di portal_distort.fsh. */
	private static final DepthOcclusion OCCLUSION = DepthOcclusion.at(MAX_SOURCES + 1, MAX_SOURCES).range(0.3f, 0.6f).planeThickness(0.6f);
	private static final int DATA_HEIGHT = OCCLUSION.endRow();
	private static final float PLANE_RADIUS_BLOCKS = 3.1f;
	private static final List<DepthOcclusion.Source> OCCLUDERS = new ArrayList<>(MAX_SOURCES);

	private static final Identifier DATA_TEXTURE_ID = Identifier.fromNamespaceAndPath(BackroomsMod.MOD_ID, "textures/effect/portal_distort_data.png");
	private static final Identifier DATA_INPUT_ID = Identifier.fromNamespaceAndPath(BackroomsMod.MOD_ID, "portal_distort_data");
	private static final Identifier SCREENQUAD = Identifier.parse("minecraft:core/screenquad");
	private static final Identifier MAIN = Identifier.parse("minecraft:main");
	private static final Identifier SWAP = Identifier.fromNamespaceAndPath(BackroomsMod.MOD_ID, "portal_distort_swap");

	// --- Tampilan ---
	/** Setengah lebar dan tinggi ruang portal (blok); sama dengan MagnetFrame.WIDTH / HEIGHT dibagi dua. */
	private static final double HALF_WIDTH = 1.5;
	private static final double HALF_HEIGHT = 2.5;
	/** Kekuatan tetap selama portal ada, dan puncak sesaat setelah muncul. */
	private static final float STEADY_STRENGTH = 0.42f;
	private static final float PEAK_STRENGTH = 1.0f;
	/** Puncak bertahan sebentar lalu mereda ke tingkat tetap. */
	private static final float PEAK_HOLD_TICKS = 24.0f;
	private static final float SETTLE_TICKS = 140.0f;
	private static final float FADE_IN_TICKS = 6.0f;
	/** Efek penuh sampai jarak ini (blok), lalu memudar sampai hilang. */
	private static final float FULL_DISTANCE = 14.0f;
	private static final float FADE_DISTANCE = 14.0f;
	private static final float VISIBILITY_RATE = 10.0f;
	/** Kekuatan distorsi puncak saat menutup, dan titik bagi animasi penutupan (harus sama dengan CLOSE_SPLIT di shader). */
	private static final float CLOSE_STRENGTH = 1.2f;
	private static final float CLOSE_SPLIT = 0.40f;
	/** Ukuran penuh ruang portal (blok), untuk mengecilkan entitas Portal di client saat menutup. */
	private static final double PORTAL_WIDTH = HALF_WIDTH * 2.0;
	private static final double PORTAL_HEIGHT = HALF_HEIGHT * 2.0;
	/** Ukuran minimum agar entitas Portal tidak degenerat saat hampir hilang. */
	private static final double MIN_PORTAL_SIZE = 0.02;
	/** Api pengisian energi: jumlah awal, lama menyala masuk, dan lama memudar saat bloom burst mengambil alih (tick). */
	private static final float FLAME_START = 0.3f;
	private static final float FLAME_FADE_IN_TICKS = 30.0f;
	private static final float FLAME_FADE_OUT_TICKS = 18.0f;

	private static final Projection PROJECTION = new Projection();
	private static final Matrix4f MATRIX = new Matrix4f();
	private static final Vector4f TMP = new Vector4f();

	private static DynamicTexture dataTexture;
	private static ProjectionMatrixBuffer projectionBuffer;
	private static PostChain chain;
	private static boolean failed;
	private static long lastFrameNanos;

	private PortalDistortRenderer() {
	}

	/** Satu portal yang digambar: titik tengah dan vektor ke titik setengah-lebar (R) dan setengah-tinggi (U), fraksi layar. */
	private record Quad(float cx, float cy, float rx, float ry, float ux, float uy, float strength, int seed, float distance,
		float flame, float close, DepthOcclusion.Source occluder) {
	}

	/** Dipanggil tiap frame setelah dunia tergambar dan sebelum GUI. Tidak melakukan apa-apa jika tak ada portal terbuka. */
	public static void render(RenderTarget main, CrossFrameResourcePool pool, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		LocalPlayer player = minecraft.player;
		List<OpenPortals.Entry> entries = OpenPortals.entries();
		List<PortalGlowManager.Source> sources = PortalGlowManager.sources();
		if (level == null || player == null || failed || (entries.isEmpty() && sources.isEmpty())) {
			lastFrameNanos = 0L;
			return;
		}
		long nowNanos = System.nanoTime();
		float frameSeconds = lastFrameNanos == 0L ? 0.016f : Math.min(0.1f, (nowNanos - lastFrameNanos) / 1.0e9f);
		lastFrameNanos = nowNanos;

		double nowTicks = level.getGameTime() + deltaTracker.getGameTimeDeltaPartialTick(false);
		Camera camera = minecraft.gameRenderer.mainCamera();
		Vec3 eye = camera.position();

		List<Quad> quads = new ArrayList<>(entries.size());
		for (OpenPortals.Entry entry : entries) {
			Vec3 center = Vec3.atCenterOf(entry.center);
			float close = entry.closeProgress(nowTicks);
			if (close > 0.0f) {
				shrinkPortal(level, entry, close);
			}
			float visible = PortalSight.visibility(level, eye, center, entry.right);
			entry.visibility = Mth.lerp(Math.min(1.0f, frameSeconds * VISIBILITY_RATE), entry.visibility, visible);
			if (entry.visibility < 0.01f) {
				entry.visibility = 0.0f;
				continue;
			}
			Quad quad = evaluate(entry, center, camera, nowTicks, close, main.width, main.height);
			if (quad != null) {
				quads.add(quad);
			}
		}
		for (PortalGlowManager.Source source : sources) {
			if (source.openedTick >= 0L) {
				continue;
			}
			Vec3 center = Vec3.atCenterOf(source.center);
			float visible = PortalSight.visibility(level, eye, center, source.right);
			source.flameVisibility = Mth.lerp(Math.min(1.0f, frameSeconds * VISIBILITY_RATE), source.flameVisibility, visible);
			if (source.flameVisibility < 0.01f) {
				source.flameVisibility = 0.0f;
				continue;
			}
			Quad quad = evaluateFlame(source, center, camera, nowTicks, main.width, main.height);
			if (quad != null) {
				quads.add(quad);
			}
		}
		if (quads.isEmpty() || !ensureResources(minecraft)) {
			return;
		}
		// Yang paling dekat lebih dulu: hanya MAX_SOURCES baris yang muat di tekstur data.
		quads.sort(Comparator.comparingDouble(Quad::distance));
		writeData(camera, quads, (float) (nowTicks / 20.0 % 64.0));
		chain.process(main, pool);
	}

	private static Quad evaluate(OpenPortals.Entry entry, Vec3 center, Camera camera, double nowTicks, float close, int width, int height) {
		Vec3 eye = camera.position();
		float distance = (float) eye.distanceTo(center);
		float range = 1.0f - PortalGlowFlicker.smooth(FADE_DISTANCE, distance - FULL_DISTANCE);
		if (range <= 0.0f || close >= 1.0f) {
			return null;
		}
		float since = (float) Math.max(0.0, nowTicks - entry.openedTick);
		float settle = PortalGlowFlicker.smooth(SETTLE_TICKS, since - PEAK_HOLD_TICKS);
		float base = Mth.lerp(settle, PEAK_STRENGTH, STEADY_STRENGTH) * PortalGlowFlicker.smooth(FADE_IN_TICKS, since);
		if (close > 0.0f) {
			base = Mth.lerp(PortalGlowFlicker.smooth(CLOSE_SPLIT, close), base, CLOSE_STRENGTH);
		}
		float strength = base * range * entry.visibility;
		if (strength < 0.01f) {
			return null;
		}
		return buildQuad(camera, center, entry.right, entry.center.hashCode() & 0xFF, strength, 0.0f, close, distance, width, height);
	}

	/** Api hijau pengisian energi: naik pelan sepanjang pengisian, padam singkat saat bloom burst mengambil alih. */
	private static Quad evaluateFlame(PortalGlowManager.Source source, Vec3 center, Camera camera, double nowTicks, int width, int height) {
		Vec3 eye = camera.position();
		float distance = (float) eye.distanceTo(center);
		float range = 1.0f - PortalGlowFlicker.smooth(FADE_DISTANCE, distance - FULL_DISTANCE);
		float elapsed = (float) (nowTicks - source.startTick);
		float progress = elapsed / source.durationTicks;
		if (range <= 0.0f || progress < 0.0f) {
			return null;
		}
		float ramp = Mth.clamp(progress / PortalGlowRenderer.BURST_START_FRACTION, 0.0f, 1.0f);
		float fadeIn = PortalGlowFlicker.smooth(FLAME_FADE_IN_TICKS, elapsed);
		float fadeOut = 1.0f - PortalGlowFlicker.smooth(FLAME_FADE_OUT_TICKS, (progress - PortalGlowRenderer.BURST_START_FRACTION) * source.durationTicks);
		float flame = Mth.lerp(ramp * ramp, FLAME_START, 1.0f) * fadeIn * fadeOut * range * source.flameVisibility;
		if (flame < 0.01f) {
			return null;
		}
		return buildQuad(camera, center, source.right, source.center.hashCode() & 0xFF, 0.0f, flame, 0.0f, distance, width, height);
	}

	private static Quad buildQuad(Camera camera, Vec3 center, Direction rightDir, int seed, float strength, float flame,
		float close, float distance, int width, int height) {
		float[] c = project(camera, center, width, height);
		if (c == null) {
			return null;
		}
		Vec3 right = Vec3.atLowerCornerOf(rightDir.getUnitVec3i());
		float[] r = projectAxis(camera, center, right.scale(HALF_WIDTH), c, width, height);
		float[] u = projectAxis(camera, center, new Vec3(0.0, HALF_HEIGHT, 0.0), c, width, height);
		if (r == null || u == null) {
			return null;
		}
		Vec3 normal = rightDir.getAxis() == Direction.Axis.X ? new Vec3(0.0, 0.0, 1.0) : new Vec3(1.0, 0.0, 0.0);
		return new Quad(c[0] / width, c[1] / height, r[0] / width, r[1] / height, u[0] / width, u[1] / height, strength, seed, distance, flame, close,
			DepthOcclusion.Source.plane(center, normal, PLANE_RADIUS_BLOCKS));
	}

	/**
	 * Mengecilkan entitas Portal di client selama animasi menutup: ukuran penuh sampai {@link #CLOSE_SPLIT}, lalu menyusut
	 * ke tengah (rumus sama dengan {@code closeGlow} di shader). Server menghapus entitasnya setelah animasi selesai.
	 */
	private static void shrinkPortal(ClientLevel level, OpenPortals.Entry entry, float close) {
		float e = Mth.clamp((close - CLOSE_SPLIT) / (1.0f - CLOSE_SPLIT), 0.0f, 1.0f);
		double scale = 1.0 - e * e;
		for (Portal portal : level.getEntitiesOfClass(Portal.class, new AABB(entry.center).inflate(0.5))) {
			portal.setWidth(Math.max(MIN_PORTAL_SIZE, PORTAL_WIDTH * scale));
			portal.setHeight(Math.max(MIN_PORTAL_SIZE, PORTAL_HEIGHT * scale));
		}
	}

	/** Vektor layar (piksel) dari {@code c} ke titik {@code center + offset}; offset dikecilkan bila titiknya di belakang kamera. */
	private static float[] projectAxis(Camera camera, Vec3 center, Vec3 offset, float[] c, int width, int height) {
		for (float factor : new float[] {1.0f, 0.5f, 0.25f, 0.1f}) {
			float[] p = project(camera, center.add(offset.scale(factor)), width, height);
			if (p != null) {
				return new float[] {(p[0] - c[0]) / factor, (p[1] - c[1]) / factor};
			}
		}
		return null;
	}

	/** Proyeksi titik dunia ke piksel layar (asal kiri-atas); null jika di belakang kamera. */
	private static float[] project(Camera camera, Vec3 target, int width, int height) {
		Vec3 eye = camera.position();
		LevelMatrices.viewProjection(camera, MATRIX);
		TMP.set((float) (target.x - eye.x), (float) (target.y - eye.y), (float) (target.z - eye.z), 1.0f);
		MATRIX.transform(TMP);
		if (TMP.w <= 0.0f) {
			return null;
		}
		return new float[] {(TMP.x / TMP.w * 0.5f + 0.5f) * width, (1.0f - (TMP.y / TMP.w * 0.5f + 0.5f)) * height};
	}

	private static void writeData(Camera camera, List<Quad> quads, float seconds) {
		NativeImage image = dataTexture.getPixels();
		for (int row = 0; row < MAX_SOURCES; row++) {
			if (row >= quads.size()) {
				for (int column = 0; column < DATA_WIDTH; column++) {
					image.setPixelABGR(column, row, 0);
				}
				continue;
			}
			Quad q = quads.get(row);
			int cx = encode16((q.cx() + 0.5f) / 2.0f);
			int cy = encode16((q.cy() + 0.5f) / 2.0f);
			int rx = encode16((q.rx() + 2.0f) / 4.0f);
			int ry = encode16((q.ry() + 2.0f) / 4.0f);
			int ux = encode16((q.ux() + 2.0f) / 4.0f);
			int uy = encode16((q.uy() + 2.0f) / 4.0f);
			int strength = encode16(q.strength());
			int close = encode16(q.close());
			int flame = encode16(q.flame());
			image.setPixelABGR(0, row, abgr(cx >> 8, cx & 0xFF, cy >> 8, cy & 0xFF));
			image.setPixelABGR(1, row, abgr(rx >> 8, rx & 0xFF, ry >> 8, ry & 0xFF));
			image.setPixelABGR(2, row, abgr(ux >> 8, ux & 0xFF, uy >> 8, uy & 0xFF));
			image.setPixelABGR(3, row, abgr(strength >> 8, strength & 0xFF, q.seed(), 255));
			image.setPixelABGR(4, row, abgr(close >> 8, close & 0xFF, flame >> 8, flame & 0xFF));
		}
		int time = encode16(seconds / 64.0f);
		image.setPixelABGR(0, ROW_GLOBAL, abgr(time >> 8, time & 0xFF, 0, 255));
		for (int column = 1; column < DATA_WIDTH; column++) {
			image.setPixelABGR(column, ROW_GLOBAL, 0);
		}
		OCCLUDERS.clear();
		for (int i = 0; i < quads.size() && i < MAX_SOURCES; i++) {
			OCCLUDERS.add(quads.get(i).occluder());
		}
		OCCLUSION.write(image, camera, OCCLUDERS);
		dataTexture.upload();
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
			dataTexture = new DynamicTexture("backrooms portal distort data", DATA_WIDTH, DATA_HEIGHT, true);
			minecraft.getTextureManager().register(DATA_TEXTURE_ID, dataTexture);
			projectionBuffer = new ProjectionMatrixBuffer("backrooms_portal_distort");
			chain = PostChain.load(buildConfig(), minecraft.getTextureManager(), LevelTargetBundle.MAIN_TARGETS,
				Identifier.fromNamespaceAndPath(BackroomsMod.MOD_ID, "portal_distort"), PROJECTION, projectionBuffer);
			return true;
		} catch (Exception e) {
			failed = true;
			BackroomsMod.LOGGER.error("Gagal membangun post chain distorsi portal; efek dimatikan", e);
			minecraft.getTextureManager().release(DATA_TEXTURE_ID);
			dataTexture = null;
			return false;
		}
	}

	/** main + data -> portal_distort.fsh -> swap; swap -> blit -> main. */
	private static PostChainConfig buildConfig() {
		PostChainConfig.Pass distortPass = new PostChainConfig.Pass(
			SCREENQUAD, Identifier.fromNamespaceAndPath(BackroomsMod.MOD_ID, "post/portal_distort"),
			List.of(
				new PostChainConfig.TargetInput("In", MAIN, false, true),
				new PostChainConfig.TextureInput("Data", DATA_INPUT_ID, DATA_WIDTH, DATA_HEIGHT, false),
				// Depth buffer main: oklusi per piksel. Urutan input = urutan SamplerInfo di shader: In, Data, Depth.
				OCCLUSION.depthInput("Depth")),
			SWAP, new LinkedHashMap<>());

		Map<String, List<UniformValue>> blitUniforms = new LinkedHashMap<>();
		blitUniforms.put("BlitConfig", List.of(new UniformValue.Vec4Uniform(new Vector4f(1.0f, 1.0f, 1.0f, 1.0f))));
		PostChainConfig.Pass blitPass = new PostChainConfig.Pass(
			SCREENQUAD, Identifier.parse("minecraft:post/blit"),
			List.of(new PostChainConfig.TargetInput("In", SWAP, false, false)), MAIN, blitUniforms);

		Map<Identifier, PostChainConfig.InternalTarget> targets = new LinkedHashMap<>();
		targets.put(SWAP, new PostChainConfig.InternalTarget(Optional.empty(), Optional.empty(), false, 0));
		return new PostChainConfig(targets, List.of(distortPass, blitPass));
	}
}
