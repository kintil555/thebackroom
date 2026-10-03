package com.backrooms.cover;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.jspecify.annotations.Nullable;

/**
 * Sisi client: paket data block entity yang tiba sebelum block entity-nya ada. Terjadi saat pemain menaruh blok:
 * vanilla menahan perubahan blok dari server sampai ack, sehingga paket data dibuang dan blok jadi tak terlihat.
 */
public final class PendingCoverData {
	private static final int MAX_ENTRIES = 256;
	private static final Map<Long, CompoundTag> PENDING = new ConcurrentHashMap<>();

	private PendingCoverData() {
	}

	public static void put(BlockPos pos, CompoundTag tag) {
		if (PENDING.size() >= MAX_ENTRIES) {
			PENDING.clear();
		}
		PENDING.put(pos.asLong(), tag.copy());
	}

	public static @Nullable CompoundTag take(BlockPos pos) {
		return PENDING.isEmpty() ? null : PENDING.remove(pos.asLong());
	}

	public static void clear() {
		PENDING.clear();
	}
}
