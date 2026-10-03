package com.backrooms.client.glow;

import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Getar kamera setelah portal berhasil menyala: hentakan kuat di awal, lalu memudar halus sampai {@link #DURATION_TICKS}.
 * Amplitudenya turun seiring jarak. Memakai waktu game (ikut berhenti saat pause) dan tanpa random agar mulus.
 */
final class PortalShake {
	/** Lama getar sampai habis (tick). Harus lebih kecil dari {@link PortalGlowManager#AFTER_OPEN_TICKS}. */
	private static final float DURATION_TICKS = 120.0f;
	/** Amplitudo maksimum (derajat) tepat saat portal terbuka, di dekat portal. */
	private static final float MAX_YAW_DEGREES = 2.6f;
	private static final float MAX_PITCH_DEGREES = 2.0f;
	/** Di luar jarak ini tidak ada getar. */
	private static final float RANGE_BLOCKS = 40.0f;
	/** Kecepatan getar (radian per tick); beberapa frekuensi dijumlahkan agar tidak terasa berulang. */
	private static final float SPEED = 2.3f;

	private PortalShake() {
	}

	static float[] offset(List<PortalGlowManager.Source> sources, double nowTicks, Vec3 cameraPos) {
		float yaw = 0.0f;
		float pitch = 0.0f;
		for (PortalGlowManager.Source source : sources) {
			if (source.openedTick < 0L) {
				continue;
			}
			float since = (float) (nowTicks - source.openedTick);
			if (since < 0.0f || since >= DURATION_TICKS) {
				continue;
			}
			float remaining = 1.0f - since / DURATION_TICKS;
			// Kuadrat: kuat di awal, memudar cepat lalu melandai.
			float fade = remaining * remaining;
			// Naik sangat singkat (2 tick) agar hentakan terasa tajam tapi tidak patah antar frame.
			float attack = PortalGlowFlicker.smooth(2.0f, since);
			float distance = (float) cameraPos.distanceTo(Vec3.atCenterOf(source.center));
			float near = 1.0f - Mth.clamp(distance / RANGE_BLOCKS, 0.0f, 1.0f);
			float amount = fade * attack * near * near * (3.0f - 2.0f * near);

			float t = since * SPEED;
			float s = source.seed;
			float noiseYaw = Mth.sin(t + s) * 0.55f + Mth.sin(t * 2.31f + s * 1.7f) * 0.30f + Mth.sin(t * 4.07f + s * 0.6f) * 0.15f;
			float noisePitch = Mth.sin(t * 1.17f + s * 2.3f) * 0.55f + Mth.sin(t * 2.89f + s) * 0.30f + Mth.sin(t * 5.3f + s * 1.1f) * 0.15f;
			yaw += noiseYaw * MAX_YAW_DEGREES * amount;
			pitch += noisePitch * MAX_PITCH_DEGREES * amount;
		}
		return new float[] {yaw, pitch};
	}
}
