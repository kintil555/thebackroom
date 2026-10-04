package com.backrooms.client.glow;

import com.backrooms.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Api/aura hijau di tepi bingkai portal selama mengisi energi, berhenti saat bloom memasuki burst (percikan listrik
 * mengambil alih, lihat {@link PortalSparks}). Terlihat seperti energi mengalir: beberapa "kepala" aliran berputar
 * mengelilingi tepi dalam bingkai, makin cepat dan makin rapat seiring pengisian, dan meninggalkan ekor api hijau.
 */
final class PortalFlames {
	private static final double MAX_DISTANCE_SQ = 48.0 * 48.0;

	/** Jumlah kepala aliran yang berputar bersamaan, tersebar rata. */
	private static final int STREAMS = 3;
	/** Satu putaran penuh mengelilingi bingkai (tick): lambat di awal, cepat menjelang burst. */
	private static final float LAP_TICKS_START = 70.0f;
	private static final float LAP_TICKS_END = 26.0f;
	/** Partikel per tick per aliran: awal dan puncak. */
	private static final float RATE_START = 0.7f;
	private static final float RATE_END = 3.2f;
	/** Panjang ekor di belakang kepala, sebagai fraksi keliling bingkai. */
	private static final float TAIL_FRACTION = 0.09f;
	/** Kecepatan partikel menyusuri tepi (blok/tick) dan naik (blok/tick). */
	private static final double FLOW_SPEED = 0.05;
	private static final double RISE_SPEED = 0.018;

	// Tepi dalam ruang portal relatif terhadap tengahnya (blok), sedikit di dalam agar menempel di sisi Magnet.
	private static final double LEFT = -1.42;
	private static final double RIGHT = 1.42;
	private static final double BOTTOM = -2.42;
	private static final double TOP = 2.42;

	private PortalFlames() {
	}

	static void tick(ClientLevel level, PortalGlowManager.Source source, long now) {
		if (source.openedTick >= 0L) {
			return;
		}
		float progress = (float) (now - source.startTick) / source.durationTicks;
		if (progress < 0.0f || progress >= PortalGlowRenderer.BURST_START_FRACTION) {
			return;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || player.distanceToSqr(Vec3.atCenterOf(source.center)) > MAX_DISTANCE_SQ) {
			return;
		}
		// 0 -> 1 sepanjang fase pengisian pelan; mulai halus agar api tidak muncul tiba-tiba.
		float ramp = Mth.clamp(progress / PortalGlowRenderer.BURST_START_FRACTION, 0.0f, 1.0f);
		float eased = ramp * ramp * (3.0f - 2.0f * ramp);
		float fadeIn = PortalGlowFlicker.smooth(12.0f, (float) (now - source.startTick));
		float lapTicks = Mth.lerp(eased, LAP_TICKS_START, LAP_TICKS_END);
		float rate = Mth.lerp(eased, RATE_START, RATE_END) * fadeIn;

		// Fase kepala aliran diintegrasikan per tick agar posisinya tidak melompat saat kecepatan putar berubah.
		source.flamePhase += 1.0f / lapTicks;
		float lap = source.flamePhase;

		RandomSource random = level.getRandom();
		PortalSparks.Frame frame = new PortalSparks.Frame(source);
		for (int stream = 0; stream < STREAMS; stream++) {
			float head = lap + (float) stream / STREAMS;
			for (int i = count(rate, random); i > 0; i--) {
				float s = head - random.nextFloat() * TAIL_FRACTION;
				spawn(level, frame, s - (float) Math.floor(s), random);
			}
		}
	}

	/** Pembulatan acak: 2.3 -> 2 atau 3 dengan peluang 30% untuk 3. */
	private static int count(float rate, RandomSource random) {
		int whole = (int) rate;
		return whole + (random.nextFloat() < rate - whole ? 1 : 0);
	}

	/**
	 * Satu partikel di titik {@code s} (0..1) pada keliling bingkai, searah jarum jam: naik di sisi kiri, ke kanan di
	 * atas, turun di sisi kanan, ke kiri di bawah. Kecepatannya menyusuri tepi (aliran) ditambah naik ringan (api).
	 */
	private static void spawn(ClientLevel level, PortalSparks.Frame frame, float s, RandomSource random) {
		double width = RIGHT - LEFT;
		double height = TOP - BOTTOM;
		double perimeter = 2.0 * (width + height);
		double distance = s * perimeter;
		double u;
		double v;
		double tu;
		double tv;
		if (distance < height) {
			u = LEFT;
			v = BOTTOM + distance;
			tu = 0.0;
			tv = 1.0;
		} else if (distance < height + width) {
			u = LEFT + (distance - height);
			v = TOP;
			tu = 1.0;
			tv = 0.0;
		} else if (distance < 2.0 * height + width) {
			u = RIGHT;
			v = TOP - (distance - height - width);
			tu = 0.0;
			tv = -1.0;
		} else {
			u = RIGHT - (distance - 2.0 * height - width);
			v = BOTTOM;
			tu = -1.0;
			tv = 0.0;
		}
		double w = random.nextGaussian() * 0.09;
		double flow = FLOW_SPEED * (0.7 + random.nextDouble() * 0.6);
		double vu = tu * flow + random.nextGaussian() * 0.004;
		double vv = tv * flow + RISE_SPEED * (0.5 + random.nextDouble());
		double vw = random.nextGaussian() * 0.004;
		level.addParticle(ModParticles.ENERGY_FLAME,
			frame.x(u, w), frame.y(v), frame.z(u, w),
			frame.x(vu, vw) - frame.cx, vv, frame.z(vu, vw) - frame.cz);
	}
}
