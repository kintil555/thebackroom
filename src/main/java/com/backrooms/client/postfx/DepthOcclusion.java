package com.backrooms.client.postfx;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.PostChainConfig;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

/**
 * API oklusi depth buffer per piksel untuk post effect berbasis sumber titik (glow, bloom, flare, dan sejenisnya).
 * Tanpa ini efek layar penuh tembus dinding; dengan ini tiap piksel membandingkan jarak permukaan terlihat (depth buffer)
 * dengan jarak sumber, jadi sumber yang tertutup setengah tetap tergambar di bagian yang tidak tertutup.
 *
 * <p>Cara pakai pada efek baru (semuanya di thread render):
 * <ol>
 *   <li>Java: simpan {@code static final DepthOcclusion OCCLUSION = DepthOcclusion.at(firstRow).range(near, far);}
 *       dengan {@code firstRow} = baris pertama di tekstur data efek yang masih kosong ({@link #ROWS} baris dipakai) dan
 *       lebar tekstur data minimal {@link #WIDTH}.</li>
 *   <li>Chain: tambahkan {@code OCCLUSION.depthInput("Depth")} ke input pass efek. Urutan input menentukan urutan
 *       {@code SamplerInfo} di shader; deklarasikan {@code uniform sampler2D DepthSampler} dan {@code vec2 DepthSize}.</li>
 *   <li>Tiap frame: panggil {@link #write} sebelum {@code dataTexture.upload()} dengan jarak kamera ke tiap sumber
 *       (indeks sama dengan indeks sumber yang dipakai shader).</li>
 *   <li>Shader: {@code #moj_import <backrooms:depth_occlusion.glsl>}, lalu
 *       {@code float open = depthOcclusionOpenness(DepthSampler, DepthSize, DataSampler, FIRST_ROW, i, texCoord);}
 *       dan kalikan kontribusi sumber i dengan {@code open}.</li>
 * </ol>
 *
 * <p>Tata letak tekstur data (float utuh, 4 byte per texel, urutan sama dengan {@code LampBloomRenderer}):
 * baris {@code firstRow} = invers (proyeksi * rotasi view), 16 texel; baris {@code firstRow + 1}: texel 0 = 1.0 jika depth
 * zero-to-one, texel 1 = near, texel 2 = far, texel 3.. = jarak sumber (blok).
 *
 * @param firstRow baris pertama di tekstur data
 * @param near     permukaan lebih dekat dari (jarak sumber - near) mulai menutup sumber
 * @param far      permukaan lebih dekat dari (jarak sumber - far) menutup penuh; far harus lebih besar dari near. Rentang ini
 *                 menjaga blok di sekitar sumber (rangka, dinding tempat sumber menempel) tidak menutup sumbernya sendiri.
 */
public record DepthOcclusion(int firstRow, float near, float far) {
	/** Lebar minimal tekstur data. */
	public static final int WIDTH = 16;
	/** Jumlah baris tekstur data yang dipakai. */
	public static final int ROWS = 2;
	/** Jumlah sumber maksimum per efek. */
	public static final int MAX_SOURCES = WIDTH - 3;

	private static final int FIRST_SOURCE_TEXEL = 3;
	private static final Identifier MAIN = Identifier.parse("minecraft:main");
	private static final Matrix4f MATRIX = new Matrix4f();
	private static final Matrix4f INVERSE = new Matrix4f();
	private static final float[] VALUES = new float[16];

	public DepthOcclusion {
		if (firstRow < 0) {
			throw new IllegalArgumentException("firstRow < 0");
		}
		if (near < 0.0f || far <= near) {
			throw new IllegalArgumentException("butuh 0 <= near < far");
		}
	}

	/** Oklusi dengan rentang bawaan (near 1,0 blok, far 2,5 blok) mulai dari baris {@code firstRow}. */
	public static DepthOcclusion at(int firstRow) {
		return new DepthOcclusion(firstRow, 1.0f, 2.5f);
	}

	public DepthOcclusion range(float newNear, float newFar) {
		return new DepthOcclusion(this.firstRow, newNear, newFar);
	}

	/** Baris pertama setelah blok ini; dipakai untuk menghitung tinggi tekstur data. */
	public int endRow() {
		return this.firstRow + ROWS;
	}

	/** Input pass untuk depth buffer main (use_depth_buffer). Sampler-nya dibaca shader sebagai {@code <samplerName>Sampler}. */
	public PostChainConfig.Input depthInput(String samplerName) {
		return new PostChainConfig.TargetInput(samplerName, MAIN, true, false);
	}

	/**
	 * Menulis invers matriks kamera dan jarak sumber ke tekstur data. Sumber di atas {@link #MAX_SOURCES} diabaikan;
	 * slot yang tak terpakai diisi 0. Pemanggil tetap harus memanggil {@code upload()} pada tekstur.
	 */
	public void write(NativeImage image, Camera camera, float[] distances, int count) {
		camera.getViewRotationProjectionMatrix(MATRIX);
		MATRIX.invert(INVERSE);
		INVERSE.get(VALUES);
		for (int x = 0; x < WIDTH; x++) {
			image.setPixelABGR(x, this.firstRow, Float.floatToRawIntBits(VALUES[x]));
		}
		boolean zeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
		int row = this.firstRow + 1;
		image.setPixelABGR(0, row, Float.floatToRawIntBits(zeroToOne ? 1.0f : 0.0f));
		image.setPixelABGR(1, row, Float.floatToRawIntBits(this.near));
		image.setPixelABGR(2, row, Float.floatToRawIntBits(this.far));
		int used = Math.min(Math.min(count, distances.length), MAX_SOURCES);
		for (int i = 0; i < MAX_SOURCES; i++) {
			image.setPixelABGR(FIRST_SOURCE_TEXEL + i, row, i < used ? Float.floatToRawIntBits(distances[i]) : 0);
		}
	}
}
