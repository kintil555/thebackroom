package com.backrooms.client.glow;

import com.backrooms.ModBlocks;
import com.backrooms.block.MagnetFrame;
import com.backrooms.network.PortalChargePayload;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Daftar portal yang sedang mengisi energi di sisi client. Sumber masuk lewat {@link PortalChargePayload} dan keluar
 * saat durasi habis, portal sudah terbuka, atau bingkai Magnet rusak.
 */
public final class PortalGlowManager {
	/** Cek bingkai masih utuh tiap sekian tick (MagnetFrame.find memindai puluhan posisi). */
	private static final int VALIDATE_INTERVAL_TICKS = 10;
	/** Sumber dipertahankan sebanyak ini setelah portal terbuka agar glow bisa meredup dan efek kamera memudar (glow 160 tick + ekor efek kamera 100 tick). */
	public static final int AFTER_OPEN_TICKS = 260;
	/** Toleransi menunggu paket blok portal tiba setelah durasi pengisian habis. */
	private static final int OPEN_GRACE_TICKS = 40;

	private static final List<Source> SOURCES = new ArrayList<>();

	private PortalGlowManager() {
	}

	/** Satu portal yang sedang mengisi energi. */
	static final class Source {
		final BlockPos leader;
		final BlockPos center;
		final long startTick;
		final int durationTicks;
		/** Pergeseran pola kedip (radian), turunan dari posisi agar tiap portal berkedip berbeda. */
		final float seed;
		/** Arah sumbu lebar bingkai (EAST/SOUTH); normal portal = sumbu horizontal satunya. */
		final Direction right;
		/** Tick game saat client melihat portal terbuka; -1 selama masih mengisi energi. */
		long openedTick = -1L;
		/** Urutan suara pengisian energi di posisi portal ini. */
		final PortalChargeSounds sounds;
		/** Keterlihatan hasil smoothing 0..1: turun saat tengah portal terhalang blok dari kamera. */
		float visibility;

		Source(BlockPos leader, BlockPos center, long startTick, int durationTicks, Direction right) {
			this.right = right;
			this.leader = leader;
			this.center = center;
			this.startTick = startTick;
			this.durationTicks = durationTicks;
			this.seed = (center.hashCode() & 0xFFFF) / 65535.0f * (float) (Math.PI * 2.0);
			this.sounds = new PortalChargeSounds(Vec3.atCenterOf(center));
		}

		/** Tingkat cahaya dinamis 0..15: mengikuti kurva bloom (naik pelan, melonjak saat burst, meredup setelah terbuka). */
		float lightLevel(long now) {
			float flicker = 0.7f + 0.3f * Mth.clamp(PortalGlowFlicker.value(now / 20.0, this.seed), 0.0f, 1.0f);
			if (this.openedTick >= 0L) {
				float decay = PortalGlowFlicker.smooth(PortalGlowRenderer.GLOW_DECAY_TICKS, (float) Math.max(0L, now - this.openedTick));
				return 15.0f * (1.0f - decay) * Mth.lerp(decay, flicker, 1.0f);
			}
			float progress = Mth.clamp((float) (now - this.startTick) / this.durationTicks, 0.0f, 1.0f);
			return 15.0f * PortalGlowRenderer.riseLevel(progress) * flicker;
		}
	}

	/** Titik cahaya dinamis satu portal (tengah ruang portal dan level 0..15). Dipakai kompatibilitas LambDynamicLights. */
	public record LightPoint(BlockPos center, float level) {
	}

	/** Semua portal yang sedang bercahaya; kosong jika tak ada. Aman dipanggil tiap tick client. */
	public static List<LightPoint> lightPoints(@Nullable ClientLevel level) {
		if (level == null || SOURCES.isEmpty()) {
			return List.of();
		}
		long now = level.getGameTime();
		List<LightPoint> points = new ArrayList<>(SOURCES.size());
		for (Source source : SOURCES) {
			float value = source.lightLevel(now);
			if (value >= 0.5f) {
				points.add(new LightPoint(source.center, Math.min(15.0f, value)));
			}
		}
		return points;
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(PortalChargePayload.TYPE,
			(payload, context) -> onCharge(payload, context.client().level));
		ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client.level));
	}

	/** Offset (derajat) goyangan kamera dari semua portal yang baru terbuka: {yaw, pitch}. */
	public static float[] shakeOffset(double nowTicks, Vec3 cameraPos) {
		return PortalShake.offset(SOURCES, nowTicks, cameraPos);
	}

	static List<Source> sources() {
		return SOURCES;
	}

	private static void onCharge(PortalChargePayload payload, @Nullable ClientLevel level) {
		if (level == null) {
			return;
		}
		SOURCES.removeIf(source -> {
			if (source.center.equals(payload.center())) {
				source.sounds.stop();
				return true;
			}
			return false;
		});
		MagnetFrame frame = MagnetFrame.find(level, payload.leader(), false);
		Direction right = frame != null ? frame.right() : Direction.EAST;
		SOURCES.add(new Source(payload.leader(), payload.center(), level.getGameTime(), payload.durationTicks(), right));
	}

	private static void tick(@Nullable ClientLevel level) {
		if (level == null) {
			SOURCES.forEach(source -> source.sounds.stop());
			SOURCES.clear();
			return;
		}
		long now = level.getGameTime();
		Iterator<Source> iterator = SOURCES.iterator();
		while (iterator.hasNext()) {
			Source source = iterator.next();
			if (source.openedTick < 0L && level.getBlockState(source.center).is(ModBlocks.PLACEHOLDER_PORTAL)) {
				source.openedTick = now;
			}
			source.sounds.tick(now - source.startTick, source.openedTick < 0L ? -1L : now - source.openedTick);
			PortalSparks.tick(level, source, now);
			boolean opened = source.openedTick >= 0L;
			boolean charging = !opened && now - source.startTick < source.durationTicks;
			boolean finished = opened && now - source.openedTick >= AFTER_OPEN_TICKS;
			boolean neverOpened = !opened && now - source.startTick >= source.durationTicks + OPEN_GRACE_TICKS;
			boolean broken = charging && (now - source.startTick) % VALIDATE_INTERVAL_TICKS == 0
				&& MagnetFrame.find(level, source.leader, false) == null;
			if (finished || neverOpened || broken) {
				if (broken) {
					source.sounds.stop();
				}
				iterator.remove();
			}
		}
	}
}
