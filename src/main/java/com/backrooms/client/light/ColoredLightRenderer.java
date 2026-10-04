package com.backrooms.client.light;

import com.backrooms.BackroomsMod;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
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
 * Menjalankan post effect cahaya berwarna ({@code colored_light.fsh}). Uniform PostPass dibekukan saat chain dibuat,
 * jadi data per frame dikirim lewat tekstur kecil (pola yang sama dengan PortalGlowRenderer). Float dikirim utuh
 * sebagai 4 byte per texel (bit IEEE), bukan dikuantisasi.
 *
 * <p>Tata letak tekstur data (lebar {@link #DATA_WIDTH}):
 * <ul>
 *   <li>baris 0: 16 texel = invers matriks (proyeksi * rotasi view), urutan kolom GLSL;</li>
 *   <li>baris 1, texel 0: 1.0 jika depth device zero-to-one, 0.0 jika -1..1;</li>
 *   <li>baris 2..: satu cahaya per baris. Texel 0..2 = posisi relatif kamera (xyz), texel 3 = radius,
 *       texel 4 = r,g,b = warna dan a = intensitas (8 bit), texel 5 = r emisi (8 bit).</li>
 * </ul>
 */
public final class ColoredLightRenderer {
	private static final int MAX_LIGHTS = 8;
	private static final int FIRST_LIGHT_ROW = 2;
	private static final int DATA_WIDTH = 16;
	private static final int DATA_HEIGHT = FIRST_LIGHT_ROW + MAX_LIGHTS;
	/** Cahaya dengan jarak kamera melebihi radius + nilai ini tidak diproses. */
	private static final double EXTRA_RANGE_BLOCKS = 72.0;

	private static final Identifier DATA_TEXTURE_ID = Identifier.fromNamespaceAndPath(BackroomsMod.MOD_ID, "textures/effect/colored_light_data.png");
	private static final Identifier DATA_INPUT_ID = Identifier.fromNamespaceAndPath(BackroomsMod.MOD_ID, "colored_light_data");
	private static final Identifier SCREENQUAD = Identifier.parse("minecraft:core/screenquad");
	private static final Identifier MAIN = Identifier.parse("minecraft:main");
	private static final Identifier SWAP = Identifier.fromNamespaceAndPath(BackroomsMod.MOD_ID, "colored_light_swap");

	private static final Projection PROJECTION = new Projection();
	private static final Matrix4f MATRIX = new Matrix4f();
	private static final Matrix4f INVERSE = new Matrix4f();
	private static final float[] MATRIX_VALUES = new float[16];

	private static DynamicTexture dataTexture;
	private static ProjectionMatrixBuffer projectionBuffer;
	private static PostChain chain;
	private static boolean failed;

	private ColoredLightRenderer() {
	}

	/** Dipanggil tiap frame setelah dunia tergambar, sebelum glow portal dan GUI. Tidak melakukan apa pun jika tak ada cahaya. */
	public static void render(RenderTarget main, CrossFrameResourcePool pool, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			ColoredLights.clear();
			return;
		}
		if (failed) {
			return;
		}
		double nowTicks = level.getGameTime() + deltaTracker.getGameTimeDeltaPartialTick(false);
		List<ColoredLights.Sample> samples = ColoredLights.samples(nowTicks);
		if (samples.isEmpty()) {
			return;
		}

		Camera camera = minecraft.gameRenderer.mainCamera();
		Vec3 eye = camera.position();
		List<ColoredLights.Sample> nearest = samples.stream()
			.filter(sample -> sample.position().distanceTo(eye) < sample.radius() + EXTRA_RANGE_BLOCKS && sample.intensity() > 0.003f)
			.sorted(Comparator.comparingDouble(sample -> sample.position().distanceToSqr(eye)))
			.limit(MAX_LIGHTS)
			.toList();
		if (nearest.isEmpty() || !ensureResources(minecraft)) {
			return;
		}

		writeData(camera, eye, nearest);
		chain.process(main, pool);
	}

	private static void writeData(Camera camera, Vec3 eye, List<ColoredLights.Sample> lights) {
		NativeImage image = dataTexture.getPixels();
		camera.getViewRotationProjectionMatrix(MATRIX);
		MATRIX.invert(INVERSE);
		INVERSE.get(MATRIX_VALUES);
		for (int i = 0; i < 16; i++) {
			image.setPixelABGR(i, 0, Float.floatToRawIntBits(MATRIX_VALUES[i]));
		}
		boolean zeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
		for (int x = 0; x < DATA_WIDTH; x++) {
			image.setPixelABGR(x, 1, x == 0 ? Float.floatToRawIntBits(zeroToOne ? 1.0f : 0.0f) : 0);
		}
		for (int row = 0; row < MAX_LIGHTS; row++) {
			int y = FIRST_LIGHT_ROW + row;
			for (int x = 0; x < DATA_WIDTH; x++) {
				image.setPixelABGR(x, y, 0);
			}
			if (row >= lights.size()) {
				continue;
			}
			ColoredLights.Sample light = lights.get(row);
			Vec3 relative = light.position().subtract(eye);
			image.setPixelABGR(0, y, Float.floatToRawIntBits((float) relative.x));
			image.setPixelABGR(1, y, Float.floatToRawIntBits((float) relative.y));
			image.setPixelABGR(2, y, Float.floatToRawIntBits((float) relative.z));
			image.setPixelABGR(3, y, Float.floatToRawIntBits(light.radius()));
			image.setPixelABGR(4, y, abgr(light.red(), light.green(), light.blue(), light.intensity()));
			image.setPixelABGR(5, y, abgr(light.emission(), 0.0f, 0.0f, 0.0f));
		}
		dataTexture.upload();
	}

	private static int abgr(float r, float g, float b, float a) {
		return toByte(a) << 24 | toByte(b) << 16 | toByte(g) << 8 | toByte(r);
	}

	private static int toByte(float value) {
		return Mth.clamp(Math.round(value * 255.0f), 0, 255);
	}

	private static boolean ensureResources(Minecraft minecraft) {
		if (chain != null) {
			return true;
		}
		try {
			dataTexture = new DynamicTexture("backrooms colored light data", DATA_WIDTH, DATA_HEIGHT, true);
			minecraft.getTextureManager().register(DATA_TEXTURE_ID, dataTexture);
			projectionBuffer = new ProjectionMatrixBuffer("backrooms_colored_light");
			chain = PostChain.load(buildConfig(), minecraft.getTextureManager(), LevelTargetBundle.MAIN_TARGETS,
				Identifier.fromNamespaceAndPath(BackroomsMod.MOD_ID, "colored_light"), PROJECTION, projectionBuffer);
			return true;
		} catch (Exception e) {
			failed = true;
			BackroomsMod.LOGGER.error("Gagal membangun post chain cahaya berwarna; efek dimatikan", e);
			minecraft.getTextureManager().release(DATA_TEXTURE_ID);
			dataTexture = null;
			return false;
		}
	}

	/** main (warna + depth) -> colored_light.fsh -> swap -> blit -> main. Urutan input menentukan urutan SamplerInfo di shader. */
	private static PostChainConfig buildConfig() {
		List<PostChainConfig.Input> inputs = List.of(
			new PostChainConfig.TargetInput("In", MAIN, false, false),
			new PostChainConfig.TargetInput("Depth", MAIN, true, false),
			new PostChainConfig.TextureInput("Data", DATA_INPUT_ID, DATA_WIDTH, DATA_HEIGHT, false)
		);
		PostChainConfig.Pass lightPass = new PostChainConfig.Pass(
			SCREENQUAD, Identifier.fromNamespaceAndPath(BackroomsMod.MOD_ID, "post/colored_light"), inputs, SWAP, new LinkedHashMap<>());

		Map<String, List<UniformValue>> blitUniforms = new LinkedHashMap<>();
		blitUniforms.put("BlitConfig", List.of(new UniformValue.Vec4Uniform(new Vector4f(1.0f, 1.0f, 1.0f, 1.0f))));
		PostChainConfig.Pass blitPass = new PostChainConfig.Pass(
			SCREENQUAD, Identifier.parse("minecraft:post/blit"),
			List.of(new PostChainConfig.TargetInput("In", SWAP, false, false)), MAIN, blitUniforms);

		Map<Identifier, PostChainConfig.InternalTarget> targets = new LinkedHashMap<>();
		targets.put(SWAP, new PostChainConfig.InternalTarget(Optional.empty(), Optional.empty(), false, 0));
		return new PostChainConfig(targets, List.of(lightPass, blitPass));
	}
}
