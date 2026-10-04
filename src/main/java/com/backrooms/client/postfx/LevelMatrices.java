package com.backrooms.client.postfx;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;

/**
 * Matriks proyeksi * view yang benar-benar dipakai vanilla untuk menggambar dunia frame ini.
 *
 * <p>{@code Camera.getViewRotationProjectionMatrix} tidak memuat view-bobbing dan hurt-cam, padahal {@code GameRenderer.renderLevel}
 * mengalikannya ke proyeksi dunia (juga efek putar nausea/portal). Memakai matriks kamera polos membuat posisi layar dan rekonstruksi depth
 * post effect bergeser dari dunia saat pemain berjalan atau terkena damage. Mixin menangkap matriks akhir ke sini.
 */
public final class LevelMatrices {
	private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
	private static final Matrix4f VIEW = new Matrix4f();
	private static boolean captured;

	private LevelMatrices() {
	}

	/** Dipanggil mixin tepat saat vanilla mengunggah proyeksi dunia (sudah termasuk bob, hurt, dan efek putar). */
	public static Matrix4f capture(Matrix4f levelProjection) {
		Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
		camera.getViewRotationMatrix(VIEW);
		VIEW_PROJECTION.set(levelProjection).mul(VIEW);
		captured = true;
		return levelProjection;
	}

	/** Matriks proyeksi * rotasi view dunia frame ini; jatuh ke matriks kamera polos jika belum pernah ditangkap. */
	public static Matrix4f viewProjection(Camera camera, Matrix4f dest) {
		if (captured) {
			return dest.set(VIEW_PROJECTION);
		}
		return camera.getViewRotationProjectionMatrix(dest);
	}
}
