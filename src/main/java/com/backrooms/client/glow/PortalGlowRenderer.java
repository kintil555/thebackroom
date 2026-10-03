package com.backrooms.client.glow;

import com.backrooms.BackroomsMod;
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
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
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
	private static final int DATA_WIDTH = 3;

	/** Tempat tekstur data didaftarkan. PostChain me-resolve input tekstur ke {@code textures/effect/<path>.png}. */
	private static final Identifier DATA_TEXTURE_ID = Identifier.fromNamespaceAndPath(BackroomsMod.MOD_ID, "textures/effect/portal_glow_data.png");
	private static final Identifier DATA_INPUT_ID = Identifier.fromNamespaceAndPath(BackroomsMod.MOD_ID, "portal_glow_data");
	private static final Identifier SCREENQUAD = Identifier.parse("minecraft:core/screenquad");
	private static final Identifier MAIN = Identifier.parse("minecraft:main");
	private static final Identifier SWAP = Identifier.fromNamespaceAndPath(BackroomsMod.MOD_ID, "portal_glow_swap");

	// --- Tampilan: ubah di sini untuk menyetel glow ---
	/** Radius glow (blok, di dunia) di awal dan tambahan saat energi penuh. */
	private static final float BASE_RADIUS_BLOCKS = 2.6f;
	private static final float GROW_RADIUS_BLOCKS = 1.0f;
	/** Kecerahan awal (0..1 dari penuh) yang naik halus sampai 1.0 menjelang portal terbuka. */
	private static final float MIN_ENVELOPE = 0.6f;
	private static final float FADE_IN_TICKS = 8.0f;
	/** Warna tint glow (putih hangat kekuningan). */
	private static final int TINT_R = 255;
	private static final int TINT_G = 236;
	private static final int TINT_B = 170;
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

	private PortalGlowRenderer() {
	}

	/** Posisi layar ternormalisasi (asal kiri-atas), radius sebagai fraksi tinggi layar, dan intensitas akhir. */
	private record Glow(float x, float y, float radius, float intensity) {
	}

	/** Dipanggil tiap frame setelah dunia tergambar dan sebelum GUI. Tidak melakukan apa-apa jika tak ada sumber. */
	public static void render(RenderTarget main, CrossFrameResourcePool pool, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		LocalPlayer player = minecraft.player;
		List<PortalGlowManager.Source> sources = PortalGlowManager.sources();
		if (sources.isEmpty() || level == null || player == null || failed) {
			lastFrameNanos = 0L;
			return;
		}

		long nowNanos = System.nanoTime();
		float frameSeconds = lastFrameNanos == 0L ? 0.016f : Math.min(0.1f, (nowNanos - lastFrameNanos) / 1.0e9f);
		lastFrameNanos = nowNanos;

		double nowTicks = level.getGameTime() + deltaTracker.getGameTimeDeltaPartialTick(false);
		Camera camera = minecraft.gameRenderer.mainCamera();
		List<Glow> glows = new ArrayList<>(sources.size());
		for (PortalGlowManager.Source source : sources) {
			Glow glow = evaluate(source, level, player, camera, nowTicks, frameSeconds, main.width, main.height);
			if (glow != null) {
				glows.add(glow);
			}
		}
		if (glows.isEmpty() || !ensureResources(minecraft)) {
			return;
		}

		// Yang paling dekat (radius layar terbesar) lebih dulu: hanya MAX_SOURCES baris yang muat di tekstur data.
		glows.sort(Comparator.comparingDouble(Glow::radius).reversed());
		writeData(glows);
		chain.process(main, pool);
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

		// Glow ini efek layar tanpa depth test; tanpa cek ini ia akan tembus dinding. Tengah portal yang tertutup
		// blok memudar halus, bukan lenyap seketika.
		boolean clear = level.clip(new ClipContext(eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
		source.visibility = Mth.lerp(Math.min(1.0f, frameSeconds * VISIBILITY_RATE), source.visibility, clear ? 1.0f : 0.0f);
		if (source.visibility <= 0.01f) {
			return null;
		}

		double elapsedTicks = nowTicks - source.startTick;
		float progress = Mth.clamp((float) (elapsedTicks / source.durationTicks), 0.0f, 1.0f);
		float radiusBlocks = BASE_RADIUS_BLOCKS + GROW_RADIUS_BLOCKS * progress;
		float[] projected = project(camera, target, radiusBlocks, width, height);
		if (projected == null) {
			return null;
		}

		float envelope = Mth.lerp(PortalGlowFlicker.smooth(1.0f, progress), MIN_ENVELOPE, 1.0f);
		float fadeIn = PortalGlowFlicker.smooth(FADE_IN_TICKS, (float) elapsedTicks);
		float flicker = PortalGlowFlicker.value(nowTicks / 20.0, source.seed);
		float near = Mth.clamp((float) Math.sqrt(distanceSq) / NEAR_DISTANCE_BLOCKS, MIN_NEAR_FACTOR, 1.0f);
		float intensity = envelope * flicker * fadeIn * near * source.visibility;

		return new Glow(projected[0] / width, projected[1] / height, Math.min(1.0f, projected[2] / height), intensity);
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

	private static void writeData(List<Glow> glows) {
		NativeImage image = dataTexture.getPixels();
		int tint = abgr(TINT_R, TINT_G, TINT_B, 255);
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
			int radius = encode16(glow.radius());
			int intensity = encode16(glow.intensity());
			image.setPixelABGR(0, row, abgr(x >> 8, x & 0xFF, y >> 8, y & 0xFF));
			image.setPixelABGR(1, row, abgr(radius >> 8, radius & 0xFF, intensity >> 8, intensity & 0xFF));
			image.setPixelABGR(2, row, tint);
		}
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
			dataTexture = new DynamicTexture("backrooms portal glow data", DATA_WIDTH, MAX_SOURCES, true);
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

	/** main -> portal_glow.fsh -> swap -> blit -> main (vanilla tidak pernah membaca dan menulis target yang sama dalam satu pass). */
	private static PostChainConfig buildConfig() {
		List<PostChainConfig.Input> inputs = List.of(
			new PostChainConfig.TargetInput("In", MAIN, false, false),
			new PostChainConfig.TextureInput("Data", DATA_INPUT_ID, DATA_WIDTH, MAX_SOURCES, false)
		);
		PostChainConfig.Pass glowPass = new PostChainConfig.Pass(
			SCREENQUAD, Identifier.fromNamespaceAndPath(BackroomsMod.MOD_ID, "post/portal_glow"), inputs, SWAP, new LinkedHashMap<>());

		Map<String, List<UniformValue>> blitUniforms = new LinkedHashMap<>();
		blitUniforms.put("BlitConfig", List.of(new UniformValue.Vec4Uniform(new Vector4f(1.0f, 1.0f, 1.0f, 1.0f))));
		PostChainConfig.Pass blitPass = new PostChainConfig.Pass(
			SCREENQUAD, Identifier.parse("minecraft:post/blit"),
			List.of(new PostChainConfig.TargetInput("In", SWAP, false, false)), MAIN, blitUniforms);

		Map<Identifier, PostChainConfig.InternalTarget> targets = new LinkedHashMap<>();
		targets.put(SWAP, new PostChainConfig.InternalTarget(Optional.empty(), Optional.empty(), false, 0));
		return new PostChainConfig(targets, List.of(glowPass, blitPass));
	}
}
