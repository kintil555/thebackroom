package com.backrooms.client.light;

import com.backrooms.client.postfx.LevelMatrices;
import com.backrooms.BackroomsMod;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.systems.RenderSystem;
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
 * Post effect bloom Lamp ({@code lamp_bloom.fsh}): tiap Lamp menyala dibloom secara screen-space (jarak 2D ke Lamp terproyeksi, keterlihatan dari sampel depth) dan meluas
 * {@link LampBloom#RADIUS_BLOCKS} blok di luar tepinya, berwarna sesuai tekstur Lamp. Oklusi memakai depth buffer, jadi
 * bloom tidak tembus dinding. Pola sama dengan {@link ColoredLightRenderer}: uniform PostPass dibekukan saat chain
 * dibuat, jadi data per frame dikirim lewat tekstur kecil.
 *
 * <p>Tata letak tekstur data (lebar {@link #DATA_WIDTH}, float utuh 4 byte per texel):
 * <ul>
 *   <li>baris 0: 16 texel = invers matriks (proyeksi * rotasi view), urutan kolom GLSL;</li>
 *   <li>baris 1: texel 0 = 1.0 jika depth zero-to-one, texel 1 = radius bloom (blok);</li>
 *   <li>baris 2: 16 texel = matriks maju (proyeksi * rotasi view);</li>
 *   <li>baris 3..: satu Lamp per baris. Texel 0..2 = pusat relatif kamera (xyz), texel 3 = r,g,b warna dan a = kekuatan (8 bit).</li>
 * </ul>
 */
public final class LampBloomRenderer {
	private static final int FIRST_LAMP_ROW = 3;
	private static final int DATA_WIDTH = 16;
	private static final int DATA_HEIGHT = FIRST_LAMP_ROW + LampBloom.MAX_LAMPS;

	private static final Identifier DATA_TEXTURE_ID = Identifier.fromNamespaceAndPath(BackroomsMod.MOD_ID, "textures/effect/lamp_bloom_data.png");
	private static final Identifier DATA_INPUT_ID = Identifier.fromNamespaceAndPath(BackroomsMod.MOD_ID, "lamp_bloom_data");
	private static final Identifier SCREENQUAD = Identifier.parse("minecraft:core/screenquad");
	private static final Identifier MAIN = Identifier.parse("minecraft:main");
	private static final Identifier SWAP = Identifier.fromNamespaceAndPath(BackroomsMod.MOD_ID, "lamp_bloom_swap");

	private static final Projection PROJECTION = new Projection();
	private static final Matrix4f MATRIX = new Matrix4f();
	private static final Matrix4f INVERSE = new Matrix4f();
	private static final float[] MATRIX_VALUES = new float[16];

	private static DynamicTexture dataTexture;
	private static ProjectionMatrixBuffer projectionBuffer;
	private static PostChain chain;
	private static boolean failed;

	private LampBloomRenderer() {
	}

	/** Dipanggil dari sisi client saat Lamp diletakkan agar bloom langsung muncul. */
	public static void notifyPlaced(net.minecraft.core.BlockPos pos) {
		LampBloom.addCandidate(pos);
	}

	/** Dipanggil tiap frame setelah dunia tergambar, sebelum glow portal dan GUI. Tidak melakukan apa pun jika tak ada Lamp menyala di pandangan. */
	public static void render(RenderTarget main, CrossFrameResourcePool pool, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			LampBloom.clear();
			return;
		}
		if (failed) {
			return;
		}
		Camera camera = minecraft.gameRenderer.mainCamera();
		List<LampBloom.Lamp> lamps = LampBloom.visibleLamps(level, camera);
		if (lamps.isEmpty() || !ensureResources(minecraft)) {
			return;
		}
		writeData(camera, camera.position(), lamps);
		chain.process(main, pool);
	}

	private static void writeData(Camera camera, Vec3 eye, List<LampBloom.Lamp> lamps) {
		NativeImage image = dataTexture.getPixels();
		LevelMatrices.viewProjection(camera, MATRIX);
		MATRIX.invert(INVERSE);
		INVERSE.get(MATRIX_VALUES);
		for (int i = 0; i < 16; i++) {
			image.setPixelABGR(i, 0, Float.floatToRawIntBits(MATRIX_VALUES[i]));
		}
		MATRIX.get(MATRIX_VALUES);
		for (int i = 0; i < 16; i++) {
			image.setPixelABGR(i, 2, Float.floatToRawIntBits(MATRIX_VALUES[i]));
		}
		boolean zeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
		for (int x = 0; x < DATA_WIDTH; x++) {
			int bits = switch (x) {
				case 0 -> Float.floatToRawIntBits(zeroToOne ? 1.0f : 0.0f);
				case 1 -> Float.floatToRawIntBits(LampBloom.RADIUS_BLOCKS);
				default -> 0;
			};
			image.setPixelABGR(x, 1, bits);
		}
		float[] tint = LampBloom.tint();
		for (int row = 0; row < LampBloom.MAX_LAMPS; row++) {
			int y = FIRST_LAMP_ROW + row;
			for (int x = 0; x < DATA_WIDTH; x++) {
				image.setPixelABGR(x, y, 0);
			}
			if (row >= lamps.size()) {
				continue;
			}
			LampBloom.Lamp lamp = lamps.get(row);
			Vec3 relative = lamp.center().subtract(eye);
			image.setPixelABGR(0, y, Float.floatToRawIntBits((float) relative.x));
			image.setPixelABGR(1, y, Float.floatToRawIntBits((float) relative.y));
			image.setPixelABGR(2, y, Float.floatToRawIntBits((float) relative.z));
			image.setPixelABGR(3, y, abgr(tint[0], tint[1], tint[2], lamp.strength()));
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
			dataTexture = new DynamicTexture("backrooms lamp bloom data", DATA_WIDTH, DATA_HEIGHT, true);
			minecraft.getTextureManager().register(DATA_TEXTURE_ID, dataTexture);
			projectionBuffer = new ProjectionMatrixBuffer("backrooms_lamp_bloom");
			chain = PostChain.load(buildConfig(), minecraft.getTextureManager(), LevelTargetBundle.MAIN_TARGETS,
				Identifier.fromNamespaceAndPath(BackroomsMod.MOD_ID, "lamp_bloom"), PROJECTION, projectionBuffer);
			return true;
		} catch (Exception e) {
			failed = true;
			BackroomsMod.LOGGER.error("Gagal membangun post chain bloom Lamp; efek dimatikan", e);
			minecraft.getTextureManager().release(DATA_TEXTURE_ID);
			dataTexture = null;
			return false;
		}
	}

	/** main (warna + depth) -> lamp_bloom.fsh -> swap -> blit -> main. Urutan input menentukan urutan SamplerInfo di shader. */
	private static PostChainConfig buildConfig() {
		List<PostChainConfig.Input> inputs = List.of(
			new PostChainConfig.TargetInput("In", MAIN, false, false),
			new PostChainConfig.TargetInput("Depth", MAIN, true, false),
			new PostChainConfig.TextureInput("Data", DATA_INPUT_ID, DATA_WIDTH, DATA_HEIGHT, false)
		);
		PostChainConfig.Pass bloomPass = new PostChainConfig.Pass(
			SCREENQUAD, Identifier.fromNamespaceAndPath(BackroomsMod.MOD_ID, "post/lamp_bloom"), inputs, SWAP, new LinkedHashMap<>());

		Map<String, List<UniformValue>> blitUniforms = new LinkedHashMap<>();
		blitUniforms.put("BlitConfig", List.of(new UniformValue.Vec4Uniform(new Vector4f(1.0f, 1.0f, 1.0f, 1.0f))));
		PostChainConfig.Pass blitPass = new PostChainConfig.Pass(
			SCREENQUAD, Identifier.parse("minecraft:post/blit"),
			List.of(new PostChainConfig.TargetInput("In", SWAP, false, false)), MAIN, blitUniforms);

		Map<Identifier, PostChainConfig.InternalTarget> targets = new LinkedHashMap<>();
		targets.put(SWAP, new PostChainConfig.InternalTarget(Optional.empty(), Optional.empty(), false, 0));
		return new PostChainConfig(targets, List.of(bloomPass, blitPass));
	}
}
