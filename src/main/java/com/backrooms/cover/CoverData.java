package com.backrooms.cover;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Carpet yang menempel pada sisi blok biasa (non Screw Piles) dalam satu chunk.
 * Immutable: key = BlockPos.asLong(), value = bitmask sisi (bit ke-ordinal Direction).
 */
public final class CoverData {
	public static final CoverData EMPTY = new CoverData(Map.of());

	public record Entry(long pos, int mask) {
	}

	private static final Codec<Entry> ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.LONG.fieldOf("pos").forGetter(Entry::pos),
		Codec.INT.fieldOf("mask").forGetter(Entry::mask)
	).apply(instance, Entry::new));

	private static final StreamCodec<ByteBuf, Entry> ENTRY_STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_LONG, Entry::pos,
		ByteBufCodecs.VAR_INT, Entry::mask,
		Entry::new
	);

	public static final Codec<CoverData> CODEC = ENTRY_CODEC.listOf().xmap(CoverData::fromEntries, CoverData::entries);
	public static final StreamCodec<ByteBuf, CoverData> STREAM_CODEC =
		ENTRY_STREAM_CODEC.apply(ByteBufCodecs.list()).map(CoverData::fromEntries, CoverData::entries);

	private final Map<Long, Integer> masks;

	private CoverData(Map<Long, Integer> masks) {
		this.masks = masks;
	}

	private static CoverData fromEntries(List<Entry> entries) {
		Map<Long, Integer> map = new HashMap<>();
		for (Entry entry : entries) {
			if (entry.mask() != 0) {
				map.put(entry.pos(), entry.mask());
			}
		}
		return map.isEmpty() ? EMPTY : new CoverData(Map.copyOf(map));
	}

	public List<Entry> entries() {
		return this.masks.entrySet().stream().map(e -> new Entry(e.getKey(), e.getValue())).toList();
	}

	public int mask(long pos) {
		return this.masks.getOrDefault(pos, 0);
	}

	public boolean isEmpty() {
		return this.masks.isEmpty();
	}

	public Iterable<Long> positions() {
		return this.masks.keySet();
	}

	/** Salinan baru dengan mask posisi tersebut diganti (mask 0 = hapus entri). */
	public CoverData with(long pos, int mask) {
		Map<Long, Integer> copy = new HashMap<>(this.masks);
		if (mask == 0) {
			copy.remove(pos);
		} else {
			copy.put(pos, mask);
		}
		return copy.isEmpty() ? EMPTY : new CoverData(Map.copyOf(copy));
	}
}
