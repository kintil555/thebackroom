package com.backrooms.client.glow;

import com.backrooms.ModBlocks;
import com.backrooms.block.MagnetFrame;
import com.backrooms.network.PortalChargePayload;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * Daftar portal yang sedang mengisi energi di sisi client. Sumber masuk lewat {@link PortalChargePayload} dan keluar
 * saat durasi habis, portal sudah terbuka, atau bingkai Magnet rusak.
 */
public final class PortalGlowManager {
	/** Cek bingkai masih utuh tiap sekian tick (MagnetFrame.find memindai puluhan posisi). */
	private static final int VALIDATE_INTERVAL_TICKS = 10;

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
		/** Keterlihatan hasil smoothing 0..1: turun saat tengah portal terhalang blok dari kamera. */
		float visibility;

		Source(BlockPos leader, BlockPos center, long startTick, int durationTicks) {
			this.leader = leader;
			this.center = center;
			this.startTick = startTick;
			this.durationTicks = durationTicks;
			this.seed = (center.hashCode() & 0xFFFF) / 65535.0f * (float) (Math.PI * 2.0);
		}
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(PortalChargePayload.TYPE,
			(payload, context) -> onCharge(payload, context.client().level));
		ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client.level));
	}

	static List<Source> sources() {
		return SOURCES;
	}

	private static void onCharge(PortalChargePayload payload, @Nullable ClientLevel level) {
		if (level == null) {
			return;
		}
		SOURCES.removeIf(source -> source.center.equals(payload.center()));
		SOURCES.add(new Source(payload.leader(), payload.center(), level.getGameTime(), payload.durationTicks()));
	}

	private static void tick(@Nullable ClientLevel level) {
		if (level == null) {
			SOURCES.clear();
			return;
		}
		long now = level.getGameTime();
		Iterator<Source> iterator = SOURCES.iterator();
		while (iterator.hasNext()) {
			Source source = iterator.next();
			boolean expired = now - source.startTick >= source.durationTicks;
			boolean opened = level.getBlockState(source.center).is(ModBlocks.PLACEHOLDER_PORTAL);
			boolean broken = !expired && !opened && (now - source.startTick) % VALIDATE_INTERVAL_TICKS == 0
				&& MagnetFrame.find(level, source.leader, false) == null;
			if (expired || opened || broken) {
				iterator.remove();
			}
		}
	}
}
