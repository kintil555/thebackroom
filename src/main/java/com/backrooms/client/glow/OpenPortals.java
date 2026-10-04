package com.backrooms.client.glow;

import com.backrooms.block.MagnetFrame;
import com.backrooms.network.PortalClosingPayload;
import com.backrooms.network.PortalOpenedPayload;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import qouteall.imm_ptl.core.portal.Portal;

/**
 * Portal Seamless Portals milik bingkai Magnet yang ada menurut client. Diisi dua jalur: {@link PortalOpenedPayload}
 * (portal baru terbuka, memicu flash dan alarm) dan pemindaian entitas Portal di sekitar pemain, sehingga portal
 * yang sudah lama terbuka tetap berdistorsi permanen (pemain baru masuk, mendekat dari jauh, atau pindah dunia dan
 * kembali). Entri dibuang saat entitas Portal-nya hilang. {@link PortalClosingPayload} menandai entri sedang menutup.
 */
final class OpenPortals {
	/** Entitas Portal butuh waktu tiba di client setelah paket; selama ini keberadaannya tidak diperiksa. */
	private static final int SYNC_GRACE_TICKS = 60;
	private static final int CHECK_INTERVAL_TICKS = 10;
	/** Jarak pemindaian entitas Portal dari pemain (blok). */
	private static final double SCAN_RANGE = 96.0;
	/** Entitas hanya dianggap hilang jika pemain cukup dekat sehingga seharusnya sudah ditrack (blok). */
	private static final double TRACK_RANGE_SQ = 48.0 * 48.0;
	/** Umur semu portal yang ditemukan lewat pemindaian: sudah lewat fase puncak distorsi, langsung tingkat tetap. */
	private static final long DISCOVERED_AGE_TICKS = 100_000L;

	private static final Map<BlockPos, Entry> ENTRIES = new LinkedHashMap<>();
	private static @Nullable ClientLevel owner;

	private OpenPortals() {
	}

	/** Satu portal terbuka. */
	static final class Entry {
		final BlockPos center;
		final Direction right;
		final long openedTick;
		/** Keterlihatan hasil smoothing 0..1 (turun saat tengah portal terhalang blok dari kamera). */
		float visibility;
		/** Tick game saat animasi penutupan mulai; -1 selama portal terbuka normal. */
		long closeStartTick = -1L;
		int closeTicks;

		Entry(BlockPos center, Direction right, long openedTick) {
			this.center = center;
			this.right = right;
			this.openedTick = openedTick;
		}

		/** Kemajuan penutupan 0..1 (0 = tidak menutup). */
		float closeProgress(double nowTicks) {
			if (this.closeStartTick < 0L) {
				return 0.0f;
			}
			return (float) Math.min(1.0, Math.max(0.0, (nowTicks - this.closeStartTick) / Math.max(1, this.closeTicks)));
		}
	}

	static void onOpened(PortalOpenedPayload payload, @Nullable ClientLevel level) {
		if (level == null) {
			return;
		}
		syncOwner(level);
		BlockPos center = payload.center().immutable();
		ENTRIES.put(center, new Entry(center, payload.right(), level.getGameTime()));
	}

	static void onClosing(PortalClosingPayload payload, @Nullable ClientLevel level) {
		if (level == null) {
			return;
		}
		syncOwner(level);
		BlockPos center = payload.center().immutable();
		Entry entry = ENTRIES.computeIfAbsent(center, c -> new Entry(c, payload.right(), level.getGameTime() - DISCOVERED_AGE_TICKS));
		entry.closeStartTick = level.getGameTime();
		entry.closeTicks = payload.durationTicks();
	}

	static boolean isOpen(BlockPos center) {
		Entry entry = ENTRIES.get(center);
		return entry != null && entry.closeStartTick < 0L;
	}

	static List<Entry> entries() {
		return new ArrayList<>(ENTRIES.values());
	}

	/** Dipanggil tiap tick client; {@code level} null saat tidak di dunia. */
	static void tick(@Nullable ClientLevel level) {
		if (level == null) {
			ENTRIES.clear();
			owner = null;
			return;
		}
		syncOwner(level);
		long now = level.getGameTime();
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || now % CHECK_INTERVAL_TICKS != 0) {
			return;
		}
		scan(level, player, now);
		Iterator<Entry> iterator = ENTRIES.values().iterator();
		while (iterator.hasNext()) {
			Entry entry = iterator.next();
			if (now - entry.openedTick < SYNC_GRACE_TICKS) {
				continue;
			}
			Vec3 center = Vec3.atCenterOf(entry.center);
			if (player.distanceToSqr(center) > TRACK_RANGE_SQ) {
				continue;
			}
			if (level.getEntitiesOfClass(Portal.class, new AABB(entry.center).inflate(0.5)).isEmpty()) {
				iterator.remove();
			}
		}
	}

	/** Mendaftarkan entitas Portal di sekitar yang berdiri tepat di tengah bingkai Magnet utuh (portal tujuan tidak punya bingkai, jadi diabaikan). */
	private static void scan(ClientLevel level, LocalPlayer player, long now) {
		for (Portal portal : level.getEntitiesOfClass(Portal.class, player.getBoundingBox().inflate(SCAN_RANGE))) {
			BlockPos center = BlockPos.containing(portal.getOriginPos());
			if (ENTRIES.containsKey(center)) {
				continue;
			}
			Vec3 axisW = portal.getAxisW();
			Direction right = Math.abs(axisW.x) > 0.5 ? Direction.EAST : Math.abs(axisW.z) > 0.5 ? Direction.SOUTH : null;
			if (right == null || Math.abs(portal.getAxisH().y) < 0.99) {
				continue;
			}
			// Magnet kolom kiri tepat 2 blok di sisi kiri tengah ruang portal.
			MagnetFrame frame = MagnetFrame.find(level, center.relative(right, -2), false);
			if (frame == null || frame.right() != right || !frame.center().equals(center)) {
				continue;
			}
			ENTRIES.put(center, new Entry(center, right, now - DISCOVERED_AGE_TICKS));
		}
	}

	private static void syncOwner(ClientLevel level) {
		if (owner != level) {
			ENTRIES.clear();
			owner = level;
		}
	}
}
