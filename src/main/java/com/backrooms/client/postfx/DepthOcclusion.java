package com.backrooms.client.postfx;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.PostChainConfig;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * API oklusi depth buffer per piksel untuk post effect berbasis sumber (glow, bloom, flare, dan sejenisnya).
 * Tanpa ini efek layar penuh tembus dinding; dengan ini tiap piksel membandingkan jarak permukaan terlihat (depth buffer)
 * dengan jarak referensi sumber, jadi sumber yang tertutup setengah tetap tergambar di bagian yang tidak tertutup.
 *
 * <p>Ukuran dan kecerahan efek tidak disentuh; API ini hanya memotong piksel yang tertutup. Dua jenis sumber ({@link Source}):
 * <ul>
 *   <li>titik: piksel dipotong jika permukaannya lebih dekat ke kamera daripada titik itu (lampu, api, orb).</li>
 *   <li>bidang (portal, layar, kaca): di dalam {@code planeRadius} dari pusat, piksel dipotong jika permukaannya berada di depan
 *       bidang lebih dari {@code planeThickness} (ketebalan bingkai yang masih dianggap bagian bidang), apa pun sudut pandangnya;
 *       di luar radius dipakai aturan titik.</li>
 * </ul>
 *
 * <p>Cara pakai pada efek baru (semuanya di thread render):
 * <ol>
 *   <li>Java: {@code static final DepthOcclusion OCCLUSION = DepthOcclusion.at(firstRow, maxSources).range(near, far);}
 *       {@code firstRow} = baris pertama di tekstur data efek yang masih kosong ({@link #rows()} baris dipakai), lebar tekstur
 *       data minimal {@link #WIDTH}, tinggi minimal {@link #endRow()}.</li>
 *   <li>Chain: tambahkan {@code OCCLUSION.depthInput("Depth")} ke input pass efek. Urutan input menentukan urutan
 *       {@code SamplerInfo} di shader; deklarasikan {@code uniform sampler2D DepthSampler} dan {@code vec2 DepthSize}.</li>
 *   <li>Tiap frame: panggil {@link #write} sebelum {@code dataTexture.upload()}; indeks sumber = indeks yang dipakai shader.</li>
 *   <li>Shader: {@code #moj_import <backrooms:depth_occlusion.glsl>}, lalu
 *       {@code float open = depthOcclusionOpenness(DepthSampler, DepthSize, DataSampler, FIRST_ROW, i, texCoord);}
 *       dan kalikan kontribusi sumber i dengan {@code open}.</li>
 * </ol>
 *
 * <p>Tata letak tekstur data (float utuh, 4 byte per texel): baris {@code firstRow} = invers (proyeksi * rotasi view), 16 texel;
 * baris {@code firstRow + 1}: texel 0 = 1.0 jika depth zero-to-one, 1 = near, 2 = far, 3 = planeThickness; baris {@code firstRow + 2 + i} = sumber i:
 * texel 0..2 = pusat relatif kamera, 3..5 = normal bidang (satuan), 6 = radius bidang atau setengah lebar (0 = sumber titik),
 * 7 = setengah tinggi (0 = bidang bulat, > 0 = bidang persegi panjang).
 *
 * @param firstRow   baris pertama di tekstur data
 * @param maxSources jumlah sumber maksimum
 * @param near       aturan titik: permukaan lebih dekat dari (jarak sumber - near) mulai terpotong
 * @param far        aturan titik: lebih dekat dari (jarak sumber - far) terpotong penuh; far - near juga lebar tepi aturan bidang
 * @param planeThickness aturan bidang: permukaan di depan bidang kurang dari ini masih dianggap bagian bidang (tidak terpotong)
 */
public record DepthOcclusion(int firstRow, int maxSources, float near, float far, float planeThickness) {
	/** Lebar minimal tekstur data. */
	public static final int WIDTH = 16;

	private static final int HEADER_ROWS = 2;
	private static final Identifier MAIN = Identifier.parse("minecraft:main");
	private static final Matrix4f MATRIX = new Matrix4f();
	private static final Matrix4f INVERSE = new Matrix4f();
	private static final float[] VALUES = new float[16];

	/**
	 * Satu sumber efek.
	 *
	 * @param center      posisi dunia sumber
	 * @param planeNormal normal bidang sumber (tidak perlu satuan); null untuk sumber titik
	 * @param planeRadius radius bidang dalam blok; 0 untuk sumber titik
	 */
	public record Source(Vec3 center, Vec3 planeNormal, float planeRadius, float planeHalfHeight) {
		public static Source point(Vec3 center) {
			return new Source(center, null, 0.0f, 0.0f);
		}

		/** Bidang bulat (radius dalam blok). */
		public static Source plane(Vec3 center, Vec3 normal, float radius) {
			return new Source(center, normal, radius, 0.0f);
		}

		/**
		 * Bidang persegi panjang tegak (portal, layar): lebar searah garis datar pada bidang (cross(Y, normal)), tinggi searah Y.
		 * Jangan pakai bidang bulat untuk benda persegi: batas lingkaran terlihat sebagai cincin distorsi di layar.
		 */
		public static Source rect(Vec3 center, Vec3 normal, float halfWidth, float halfHeight) {
			return new Source(center, normal, halfWidth, halfHeight);
		}
	}

	public DepthOcclusion {
		if (firstRow < 0 || maxSources < 1) {
			throw new IllegalArgumentException("firstRow >= 0 dan maxSources >= 1");
		}
		if (near < 0.0f || far <= near || planeThickness < 0.0f) {
			throw new IllegalArgumentException("butuh 0 <= near < far dan planeThickness >= 0");
		}
	}

	/** Oklusi dengan nilai bawaan: tepi tajam (near 0,3 / far 0,6 blok), planeThickness 0,6 blok (bingkai 1 blok + margin). */
	public static DepthOcclusion at(int firstRow, int maxSources) {
		return new DepthOcclusion(firstRow, maxSources, 0.3f, 0.6f, 0.6f);
	}

	public DepthOcclusion range(float newNear, float newFar) {
		return new DepthOcclusion(this.firstRow, this.maxSources, newNear, newFar, this.planeThickness);
	}

	public DepthOcclusion planeThickness(float thickness) {
		return new DepthOcclusion(this.firstRow, this.maxSources, this.near, this.far, thickness);
	}

	/** Jumlah baris tekstur data yang dipakai. */
	public int rows() {
		return HEADER_ROWS + this.maxSources;
	}

	/** Baris pertama setelah blok ini; dipakai untuk menghitung tinggi tekstur data. */
	public int endRow() {
		return this.firstRow + this.rows();
	}

	/** Input pass untuk depth buffer main (use_depth_buffer). Sampler-nya dibaca shader sebagai {@code <samplerName>Sampler}. */
	public PostChainConfig.Input depthInput(String samplerName) {
		return new PostChainConfig.TargetInput(samplerName, MAIN, true, false);
	}

	/**
	 * Menulis invers matriks kamera, rentang oklusi, dan data sumber ke tekstur data. Sumber di atas {@link #maxSources}
	 * diabaikan; slot yang tak terpakai diisi 0. Pemanggil tetap harus memanggil {@code upload()} pada tekstur.
	 */
	public void write(NativeImage image, Camera camera, List<Source> sources) {
		LevelMatrices.viewProjection(camera, MATRIX);
		MATRIX.invert(INVERSE);
		INVERSE.get(VALUES);
		for (int x = 0; x < WIDTH; x++) {
			image.setPixelABGR(x, this.firstRow, Float.floatToRawIntBits(VALUES[x]));
		}
		boolean zeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
		int header = this.firstRow + 1;
		for (int x = 0; x < WIDTH; x++) {
			image.setPixelABGR(x, header, 0);
		}
		put(image, 0, header, zeroToOne ? 1.0f : 0.0f);
		put(image, 1, header, this.near);
		put(image, 2, header, this.far);
		put(image, 3, header, this.planeThickness);

		Vec3 eye = camera.position();
		for (int i = 0; i < this.maxSources; i++) {
			int row = this.firstRow + HEADER_ROWS + i;
			for (int x = 0; x < WIDTH; x++) {
				image.setPixelABGR(x, row, 0);
			}
			if (i >= sources.size()) {
				continue;
			}
			Source source = sources.get(i);
			Vec3 relative = source.center().subtract(eye);
			put(image, 0, row, (float) relative.x);
			put(image, 1, row, (float) relative.y);
			put(image, 2, row, (float) relative.z);
			Vec3 normal = source.planeNormal();
			if (normal != null && source.planeRadius() > 0.0f && normal.lengthSqr() > 1.0e-8) {
				Vec3 unit = normal.normalize();
				put(image, 3, row, (float) unit.x);
				put(image, 4, row, (float) unit.y);
				put(image, 5, row, (float) unit.z);
				put(image, 6, row, source.planeRadius());
				put(image, 7, row, source.planeHalfHeight());
			}
		}
	}

	private static void put(NativeImage image, int x, int y, float value) {
		image.setPixelABGR(x, y, Float.floatToRawIntBits(value));
	}
}
