package com.backrooms.client.postfx;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;

/**
 * Menjaga depth buffer main tetap berisi depth dunia saat post effect berjalan.
 *
 * <p>Di 26.2 {@code GameRenderer.renderLevel} meng-clear depth main (ke 0.0) tepat sebelum tangan digambar, sedangkan post effect kita
 * berjalan setelah {@code renderLevel}. Akibatnya depth yang terbaca post effect hanya berisi tangan, semua piksel lain dianggap langit,
 * dan efek tembus semua benda. 26.3 mengatasinya dengan target depth terpisah untuk tangan; di sini depth dunia disalin sebelum
 * di-clear lalu dikembalikan ke main sebelum post effect, sehingga {@code use_depth_buffer} pada {@code minecraft:main} kembali benar.
 * Depth tangan tidak dipertahankan (tangan selalu paling dekat dan tidak ikut oklusi).
 */
public final class WorldDepth {
	private static TextureTarget snapshot;
	private static boolean captured;

	private WorldDepth() {
	}

	/** Panggil tepat sebelum vanilla meng-clear depth main untuk tangan (setelah dunia selesai tergambar). */
	public static void capture(RenderTarget main) {
		if (main.getDepthTexture() == null) {
			return;
		}
		ensureSize(main);
		copy(main, snapshot, true);
		captured = true;
	}

	/** Panggil sebelum post effect pertama; mengembalikan depth dunia ke main. Tidak melakukan apa-apa jika tak ada snapshot frame ini. */
	public static void restore(RenderTarget main) {
		if (!captured || snapshot == null || main.getDepthTexture() == null) {
			return;
		}
		captured = false;
		ensureSize(main);
		copy(main, snapshot, false);
	}

	private static void ensureSize(RenderTarget main) {
		if (snapshot == null) {
			snapshot = new TextureTarget("backrooms world depth", main.width, main.height, true, GpuFormat.RGBA8_UNORM);
		} else if (snapshot.width != main.width || snapshot.height != main.height) {
			snapshot.resize(main.width, main.height);
			captured = false;
		}
	}

	private static void copy(RenderTarget main, RenderTarget snap, boolean mainToSnapshot) {
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		if (mainToSnapshot) {
			encoder.copyTextureToTexture(main.getDepthTexture(), snap.getDepthTexture(), 0, 0, 0, 0, 0, main.width, main.height);
		} else {
			encoder.copyTextureToTexture(snap.getDepthTexture(), main.getDepthTexture(), 0, 0, 0, 0, 0, main.width, main.height);
		}
	}
}
