package com.backrooms.client.glow;

import net.minecraft.util.Mth;

/**
 * Kedip glow: tiap denyut melompat ke intensitas tinggi lalu meredup sedikit, denyut berikutnya kembali tinggi
 * tetapi sedikit lebih rendah dari sebelumnya. Beberapa denyut membentuk satu "burst"; burst berikutnya mulai lagi
 * dari puncak penuh (dengan variasi kecil). Laju denyut bergeser pelan agar tidak terdengar/terlihat seperti metronom.
 */
final class PortalGlowFlicker {
	/** Seberapa dalam intensitas turun di ujung sebuah denyut (0..1 dari puncaknya). */
	private static final float DIP = 0.30f;
	/** Penurunan puncak per denyut di dalam satu burst. */
	private static final float PEAK_STEP = 0.11f;
	private static final float RATE = 4.6f;
	/** Jumlah denyut per burst. */
	private static final int BURST_LENGTH = 4;
	/** Lama (fraksi denyut) lonjakan naik agar tidak patah antar frame. */
	private static final float ATTACK = 0.08f;

	private PortalGlowFlicker() {
	}

	/**
	 * @param seconds waktu dalam detik (game time, ikut berhenti saat pause)
	 * @param seed    pergeseran per sumber agar dua portal tidak berkedip serempak
	 * @return intensitas relatif, kira-kira 0.5 .. 1.03
	 */
	static float value(double seconds, float seed) {
		// Integral dari laju yang berubah pelan (selalu > 0): fase monoton, tanpa loncatan saat laju bergeser.
		double phase = RATE * seconds
			- (1.3 / 0.9) * Math.cos(0.9 * seconds + seed)
			- (0.6 / 2.3) * Math.cos(2.3 * seconds + seed * 1.7);
		long pulse = (long) Math.floor(phase);
		float frac = (float) (phase - pulse);

		float previousEnd = peak(pulse - 1) * (1.0f - DIP);
		float level = pulseLevel(pulse, frac, previousEnd);

		float jitter = 1.0f + 0.03f * (float) Math.sin(seconds * 37.0 + seed);
		return level * jitter;
	}

	private static float pulseLevel(long pulse, float frac, float previousEnd) {
		float peak = peak(pulse);
		float attack = smooth(ATTACK, frac);
		float decay = 1.0f - DIP * (float) Math.pow(frac, 1.4);
		return Mth.lerp(attack, previousEnd, peak * decay);
	}

	/**
	 * Puncak denyut ke-{@code pulse}. Burst terdiri dari {@link #BURST_LENGTH} denyut: turun {@link #PEAK_STEP} tiap
	 * denyut, lalu burst berikutnya kembali ke puncak penuh. Tinggi burst dan tiap denyut diberi variasi hash.
	 * O(1) agar tetap murah dan tidak berubah perilaku seiring game time bertambah.
	 */
	private static float peak(long pulse) {
		long burst = Math.floorDiv(pulse, (long) BURST_LENGTH);
		int index = (int) Math.floorMod(pulse, (long) BURST_LENGTH);
		float burstDrop = 0.05f * hash(burst);
		float pulseJitter = (hash(pulse * 31L + 7L) - 0.5f) * 0.06f;
		return 1.0f - PEAK_STEP * index - burstDrop + pulseJitter;
	}

	/** Smoothstep 0..edge: 0 di awal, 1 di {@code edge}, kemiringan nol di kedua ujung. */
	static float smooth(float edge, float x) {
		float t = Mth.clamp(x / edge, 0.0f, 1.0f);
		return t * t * (3.0f - 2.0f * t);
	}

	/** Hash deterministik ke 0..1. */
	private static float hash(long value) {
		long x = value * 0x9E3779B97F4A7C15L;
		x ^= x >>> 29;
		x *= 0xBF58476D1CE4E5B9L;
		x ^= x >>> 32;
		return (x & 0xFFFFFFL) / (float) 0x1000000;
	}
}
