package com.backrooms.client.glow;

import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Getar kamera satu kurva mulus: mulai kecil di {@link #START_FRACTION} durasi pengisian (detik 8 dari 10), naik
 * perlahan sampai puncak sedikit setelah portal terbuka, lalu memudar sampai habis. Tidak ada lompatan intensitas.
 * Amplitudo turun seiring jarak. Memakai waktu game (ikut berhenti saat pause), tanpa random agar mulus.
 */
final class PortalShake {
	/** Getar mulai pada fraksi durasi pengisian ini. */
	private static final float START_FRACTION = 0.8f;
	/** Lama naik dari 0 ke puncak (tick), dihitung sejak getar mulai: 2 detik sampai portal terbuka + 0,5 detik. */
	private static final float RISE_TICKS = 50.0f;
	/** Lama memudar dari puncak sampai habis (tick). Total harus lebih kecil dari {@link PortalGlowManager#AFTER_OPEN_TICKS}. */
	private static final float FALL_TICKS = 120.0f;
	/** Amplitudo maksimum (derajat) di puncak, di dekat portal. */
	private static final float MAX_YAW_DEGREES = 2.6f;
	private static final float MAX_PITCH_DEGREES = 2.0f;
	/** Di luar jarak ini tidak ada getar. */
	private static final float RANGE_BLOCKS = 40.0f;
	/** Kecepatan getar (radian per tick); beberapa frekuensi dijumlahkan agar tidak terasa berulang. */
	private static final float SPEED = 2.3f;

	private PortalShake() {
	}

	/** Envelope 0..1 pada waktu {@code t} tick sejak getar mulai. */
	private static float envelope(float t) {
		if (t <= 0.0f || t >= RISE_TICKS + FALL_TICKS) {
			return 0.0f;
		}
		if (t < RISE_TICKS) {
			// Awal landai (kuadrat di dalam smoothstep) agar di detik 8 getarnya baru terasa samar.
			float x = t / RISE_TICKS;
			return PortalGlowFlicker.smooth(1.0f, x * x);
		}
		float remaining = 1.0f - (t - RISE_TICKS) / FALL_TICKS;
		return remaining * remaining * (3.0f - 2.0f * remaining);
	}

	static float[] offset(List<PortalGlowManager.Source> sources, double nowTicks, Vec3 cameraPos) {
		float yaw = 0.0f;
		float pitch = 0.0f;
		for (PortalGlowManager.Source source : sources) {
			float t = (float) (nowTicks - source.startTick - START_FRACTION * source.durationTicks);
			float amount = envelope(t);
			if (amount <= 0.0f) {
				continue;
			}
			float distance = (float) cameraPos.distanceTo(Vec3.atCenterOf(source.center));
			float near = 1.0f - Mth.clamp(distance / RANGE_BLOCKS, 0.0f, 1.0f);
			amount *= near * near * (3.0f - 2.0f * near);

			float w = t * SPEED;
			float s = source.seed;
			float noiseYaw = Mth.sin(w + s) * 0.55f + Mth.sin(w * 2.31f + s * 1.7f) * 0.30f + Mth.sin(w * 4.07f + s * 0.6f) * 0.15f;
			float noisePitch = Mth.sin(w * 1.17f + s * 2.3f) * 0.55f + Mth.sin(w * 2.89f + s) * 0.30f + Mth.sin(w * 5.3f + s * 1.1f) * 0.15f;
			yaw += noiseYaw * MAX_YAW_DEGREES * amount;
			pitch += noisePitch * MAX_PITCH_DEGREES * amount;
		}
		return new float[] {yaw, pitch};
	}
}
