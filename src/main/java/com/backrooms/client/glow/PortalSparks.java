package com.backrooms.client.glow;

import com.backrooms.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Percikan listrik di sekitar portal (client). Selama mengisi energi: kilat kecil di bingkai dan ruang portal yang
 * makin sering. Saat bloom memasuki burst (dan sesaat setelah portal terbuka): semburan besar ke segala arah, paling
 * banyak menembus bidang portal. Animasi frame (2 tick per frame) ada di {@code ElectricSparkParticle}.
 */
final class PortalSparks {
	private static final double MAX_DISTANCE_SQ = 64.0 * 64.0;

	/** Partikel per tick: ambient awal, ambient puncak, dan semburan burst. */
	private static final float AMBIENT_MIN = 0.35f;
	private static final float AMBIENT_MAX = 3.5f;
	private static final float BURST_RATE = 70.0f;
	/** Semburan masih berlanjut sebanyak ini setelah portal terbuka, menurun sampai nol. */
	private static final int BURST_TAIL_TICKS = 30;
	/** Burst membesar penuh dalam fraksi durasi ini sejak BURST_START_FRACTION (sekitar 4 tick). */
	private static final float BURST_RAMP_FRACTION = 0.02f;

	// Ukuran relatif terhadap tengah ruang portal (blok): ruang 3 x 5, magnet persis di luar sisinya.
	private static final double HALF_WIDTH = 1.5;
	private static final double HALF_HEIGHT = 2.5;
	private static final double RIM_U = 1.6;
	private static final double RIM_BOTTOM = -2.45;
	private static final double RIM_TOP = 2.6;

	private PortalSparks() {
	}

	static void tick(ClientLevel level, PortalGlowManager.Source source, long now) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || player.distanceToSqr(Vec3.atCenterOf(source.center)) > MAX_DISTANCE_SQ) {
			return;
		}
		float ambient = 0.0f;
		float burst = 0.0f;
		if (source.openedTick < 0L) {
			float progress = (float) (now - source.startTick) / source.durationTicks;
			if (progress >= PortalGlowRenderer.BURST_START_FRACTION) {
				ambient = AMBIENT_MAX;
				burst = BURST_RATE * Mth.clamp((progress - PortalGlowRenderer.BURST_START_FRACTION) / BURST_RAMP_FRACTION, 0.0f, 1.0f);
			} else {
				float ramp = PortalGlowFlicker.smooth(1.0f, progress / PortalGlowRenderer.BURST_START_FRACTION);
				ambient = Mth.lerp(ramp, AMBIENT_MIN, AMBIENT_MAX);
			}
		} else {
			long since = now - source.openedTick;
			if (since < BURST_TAIL_TICKS) {
				ambient = AMBIENT_MAX;
				burst = BURST_RATE * (1.0f - (float) since / BURST_TAIL_TICKS);
			} else {
				float decay = PortalGlowFlicker.smooth(PortalGlowRenderer.GLOW_DECAY_TICKS, (float) since);
				ambient = AMBIENT_MAX * (1.0f - decay);
			}
		}

		RandomSource random = level.getRandom();
		Frame frame = new Frame(source);
		for (int i = count(ambient, random); i > 0; i--) {
			spawnAmbient(level, frame, random);
		}
		for (int i = count(burst, random); i > 0; i--) {
			spawnBurst(level, frame, random);
		}
	}

	/** Pembulatan acak: 2.3 -> 2 atau 3 dengan peluang 30% untuk 3. */
	private static int count(float rate, RandomSource random) {
		int whole = (int) rate;
		return whole + (random.nextFloat() < rate - whole ? 1 : 0);
	}

	/** Kilat kecil di bingkai (separuh) atau di dalam ruang portal, melaju pelan menyeberangi portal. */
	private static void spawnAmbient(ClientLevel level, Frame frame, RandomSource random) {
		double u;
		double v;
		if (random.nextInt(2) == 0) {
			int edge = random.nextInt(3);
			if (edge == 2) {
				u = Mth.lerp(random.nextDouble(), -RIM_U, RIM_U);
				v = RIM_TOP;
			} else {
				u = edge == 0 ? -RIM_U : RIM_U;
				v = Mth.lerp(random.nextDouble(), RIM_BOTTOM, RIM_TOP);
			}
		} else {
			u = Mth.lerp(random.nextDouble(), -HALF_WIDTH, HALF_WIDTH);
			v = Mth.lerp(random.nextDouble(), -HALF_HEIGHT, HALF_HEIGHT);
		}
		double w = random.nextGaussian() * 0.12;
		double targetU = Mth.lerp(random.nextDouble(), -HALF_WIDTH, HALF_WIDTH);
		double targetV = Mth.lerp(random.nextDouble(), -HALF_HEIGHT, HALF_HEIGHT);
		double vu = (targetU - u) * 0.05;
		double vv = (targetV - v) * 0.05;
		double vw = random.nextGaussian() * 0.015;
		level.addParticle(ModParticles.ELECTRIC_SPARK,
			frame.x(u, w), frame.y(v), frame.z(u, w),
			frame.x(vu, vw) - frame.cx, vv, frame.z(vu, vw) - frame.cz);
	}

	/** Semburan dari dalam ruang portal; arah acak, condong menembus bidang portal ke dua sisi. */
	private static void spawnBurst(ClientLevel level, Frame frame, RandomSource random) {
		double u = Mth.clamp(random.nextGaussian() * 0.7, -HALF_WIDTH, HALF_WIDTH);
		double v = Mth.clamp(random.nextGaussian() * 1.1, -HALF_HEIGHT, HALF_HEIGHT);
		double side = random.nextBoolean() ? 1.0 : -1.0;
		double du = random.nextGaussian() * 0.8;
		double dv = random.nextGaussian() * 0.8;
		double dw = side * (0.5 + random.nextDouble());
		double length = Math.sqrt(du * du + dv * dv + dw * dw);
		double speed = (0.5 + random.nextDouble() * 1.1) / Math.max(length, 1.0e-4);
		du *= speed;
		dv *= speed;
		dw *= speed;
		level.addParticle(ModParticles.ELECTRIC_SPARK_BURST,
			frame.x(u, 0.0), frame.y(v), frame.z(u, 0.0),
			frame.x(du, dw) - frame.cx, dv, frame.z(du, dw) - frame.cz);
	}

	/** Basis portal: pusat ruang, sumbu lebar (right) dan sumbu normal (horizontal tegak lurus). */
	private static final class Frame {
		final double cx;
		final double cy;
		final double cz;
		private final double rightX;
		private final double rightZ;
		private final double normalX;
		private final double normalZ;

		Frame(PortalGlowManager.Source source) {
			Vec3 center = Vec3.atCenterOf(source.center);
			this.cx = center.x;
			this.cy = center.y;
			this.cz = center.z;
			boolean east = source.right == Direction.EAST;
			this.rightX = east ? 1.0 : 0.0;
			this.rightZ = east ? 0.0 : 1.0;
			this.normalX = east ? 0.0 : 1.0;
			this.normalZ = east ? 1.0 : 0.0;
		}

		double x(double u, double w) {
			return this.cx + this.rightX * u + this.normalX * w;
		}

		double y(double v) {
			return this.cy + v;
		}

		double z(double u, double w) {
			return this.cz + this.rightZ * u + this.normalZ * w;
		}
	}
}
