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
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Efek distorsi "gangguan medan magnet" pada portal yang terbuka (shader {@code portal_distort.fsh}). Berjalan selama
 * portal ada, paling kuat sesaat setelah portal muncul lalu mereda ke tingkat tetap. Seperti {@link PortalGlowRenderer},
 * parameter dinamis dikirim lewat tekstur data kecil karena uniform PostPass dibekukan saat chain dibuat.
 */
public final class PortalDistortRenderer {
	private static final int MAX_SOURCES = 4;
	private static final int DATA_WIDTH = 4;
	private static final int ROW_GLOBAL = MAX_SOURCES;
	private static final int DATA_HEIGHT = MAX_SOURCES + 1;

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
	private record Quad(float cx, float cy, float rx, float ry, float ux, float uy, float strength, int seed, float distance) {
	}

	/** Dipanggil tiap frame setelah dunia tergambar dan sebelum GUI. Tidak melakukan apa-apa jika tak ada portal terbuka. */
	public static void render(RenderTarget main, CrossFrameResourcePool pool, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		LocalPlayer player = minecraft.player;
		List<OpenPortals.Entry> entries = OpenPortals.entries();
		if (level == null || player == null || failed || entries.isEmpty()) {
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
			boolean visible = PortalSight.visible(level, eye, center);
			entry.visibility = Mth.lerp(Math.min(1.0f, frameSeconds * VISIBILITY_RATE), entry.visibility, visible ? 1.0f : 0.0f);
			if (entry.visibility < 0.01f) {
				entry.visibility = 0.0f;
				continue;
			}
			Quad quad = evaluate(entry, center, camera, nowTicks, main.width, main.height);
			if (quad != null) {
				quads.add(quad);
			}
		}
		if (quads.isEmpty() || !ensureResources(minecraft)) {
			return;
		}
		// Yang paling dekat lebih dulu: hanya MAX_SOURCES baris yang muat di tekstur data.
		quads.sort(Comparator.comparingDouble(Quad::distance));
		writeData(quads, (float) (nowTicks / 20.0 % 64.0));
		chain.process(main, pool);
	}

	private static Quad evaluate(OpenPortals.Entry entry, Vec3 center, Camera camera, double nowTicks, int width, int height) {
		Vec3 eye = camera.position();
		float distance = (float) eye.distanceTo(center);
		float range = 1.0f - PortalGlowFlicker.smooth(FADE_DISTANCE, distance - FULL_DISTANCE);
		if (range <= 0.0f) {
			return null;
		}
		float since = (float) Math.max(0.0, nowTicks - entry.openedTick);
		float settle = PortalGlowFlicker.smooth(SETTLE_TICKS, since - PEAK_HOLD_TICKS);
		float strength = Mth.lerp(settle, PEAK_STRENGTH, STEADY_STRENGTH)
			* PortalGlowFlicker.smooth(FADE_IN_TICKS, since) * range * entry.visibility;
		if (strength < 0.01f) {
			return null;
		}

		float[] c = project(camera, center, width, height);
		if (c == null) {
			return null;
		}
		Vec3 right = Vec3.atLowerCornerOf(entry.right.getUnitVec3i());
		float[] r = projectAxis(camera, center, right.scale(HALF_WIDTH), c, width, height);
		float[] u = projectAxis(camera, center, new Vec3(0.0, HALF_HEIGHT, 0.0), c, width, height);
		if (r == null || u == null) {
			return null;
		}
		int seed = entry.center.hashCode() & 0xFF;
		return new Quad(c[0] / width, c[1] / height, r[0] / width, r[1] / height, u[0] / width, u[1] / height, strength, seed, distance);
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
		camera.getViewRotationProjectionMatrix(MATRIX);
		TMP.set((float) (target.x - eye.x), (float) (target.y - eye.y), (float) (target.z - eye.z), 1.0f);
		MATRIX.transform(TMP);
		if (TMP.w <= 0.0f) {
			return null;
		}
		return new float[] {(TMP.x / TMP.w * 0.5f + 0.5f) * width, (1.0f - (TMP.y / TMP.w * 0.5f + 0.5f)) * height};
	}

	private static void writeData(List<Quad> quads, float seconds) {
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
			image.setPixelABGR(0, row, abgr(cx >> 8, cx & 0xFF, cy >> 8, cy & 0xFF));
			image.setPixelABGR(1, row, abgr(rx >> 8, rx & 0xFF, ry >> 8, ry & 0xFF));
			image.setPixelABGR(2, row, abgr(ux >> 8, ux & 0xFF, uy >> 8, uy & 0xFF));
			image.setPixelABGR(3, row, abgr(strength >> 8, strength & 0xFF, q.seed(), 255));
		}
		int time = encode16(seconds / 64.0f);
		image.setPixelABGR(0, ROW_GLOBAL, abgr(time >> 8, time & 0xFF, 0, 255));
		for (int column = 1; column < DATA_WIDTH; column++) {
			image.setPixelABGR(column, ROW_GLOBAL, 0);
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
				new PostChainConfig.TextureInput("Data", DATA_INPUT_ID, DATA_WIDTH, DATA_HEIGHT, false)),
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
