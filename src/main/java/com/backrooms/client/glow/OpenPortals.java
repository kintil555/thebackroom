package com.backrooms.client.glow;

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
 * Portal Seamless Portals yang sedang terbuka menurut client. Diisi {@link PortalOpenedPayload} dan dibuang saat
 * entitas Portal-nya hilang (Magnet dihancurkan) atau pemain pindah dunia. Dipakai alarm, flash, dan efek distorsi.
 */
final class OpenPortals {
	/** Entitas Portal butuh waktu tiba di client setelah paket; selama ini keberadaannya tidak diperiksa. */
	private static final int SYNC_GRACE_TICKS = 60;
	private static final int CHECK_INTERVAL_TICKS = 20;
	/** Entitas hanya dianggap hilang jika pemain cukup dekat sehingga seharusnya sudah ditrack (blok). */
	private static final double TRACK_RANGE_SQ = 48.0 * 48.0;

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

		Entry(BlockPos center, Direction right, long openedTick) {
			this.center = center;
			this.right = right;
			this.openedTick = openedTick;
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

	static boolean isOpen(BlockPos center) {
		return ENTRIES.containsKey(center);
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
		if (ENTRIES.isEmpty() || now % CHECK_INTERVAL_TICKS != 0) {
			return;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		Iterator<Entry> iterator = ENTRIES.values().iterator();
		while (iterator.hasNext()) {
			Entry entry = iterator.next();
			if (now - entry.openedTick < SYNC_GRACE_TICKS) {
				continue;
			}
			Vec3 center = Vec3.atCenterOf(entry.center);
			if (player == null || player.distanceToSqr(center) > TRACK_RANGE_SQ) {
				continue;
			}
			if (level.getEntitiesOfClass(Portal.class, new AABB(entry.center).inflate(0.5)).isEmpty()) {
				iterator.remove();
			}
		}
	}

	private static void syncOwner(ClientLevel level) {
		if (owner != level) {
			ENTRIES.clear();
			owner = level;
		}
	}
}
