package com.backrooms.client;

import com.backrooms.cover.CoverAttachments;
import com.backrooms.cover.CoverData;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

/** Salinan sisi-client dari data carpet per chunk, dibaca thread yang membangun mesh chunk. */
public final class ClientCovers {
	private static final Map<Long, CoverData> CHUNKS = new ConcurrentHashMap<>();

	private ClientCovers() {
	}

	public static void init() {
		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> {
			AttachmentTarget target = (AttachmentTarget) chunk;
			update(level, chunk, null, target.getAttached(CoverAttachments.COVERS));
			target.onAttachedSet(CoverAttachments.COVERS).register((oldData, newData) -> update(level, chunk, oldData, newData));
		});
		ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> CHUNKS.remove(key(chunk.getPos().x(), chunk.getPos().z())));
	}

	private static long key(int chunkX, int chunkZ) {
		return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
	}

	/** Bitmask sisi berlapis carpet di posisi ini (0 jika tidak ada). Aman dipanggil dari thread render chunk. */
	public static int mask(BlockPos pos) {
		if (CHUNKS.isEmpty()) {
			return 0;
		}
		CoverData data = CHUNKS.get(key(pos.getX() >> 4, pos.getZ() >> 4));
		return data == null ? 0 : data.mask(pos.asLong());
	}

	private static void update(ClientLevel level, LevelChunk chunk, CoverData oldData, CoverData newData) {
		long chunkKey = key(chunk.getPos().x(), chunk.getPos().z());
		if (newData == null || newData.isEmpty()) {
			CHUNKS.remove(chunkKey);
		} else {
			CHUNKS.put(chunkKey, newData);
		}

		// Section yang posisinya berubah harus dibangun ulang agar overlay muncul/hilang.
		Set<Long> changed = new HashSet<>();
		collect(changed, oldData, newData);
		collect(changed, newData, oldData);
		for (long packed : changed) {
			BlockPos pos = BlockPos.of(packed);
			level.setSectionDirtyWithNeighbors(
				SectionPos.blockToSectionCoord(pos.getX()),
				SectionPos.blockToSectionCoord(pos.getY()),
				SectionPos.blockToSectionCoord(pos.getZ())
			);
		}
	}

	private static void collect(Set<Long> out, CoverData from, CoverData other) {
		if (from == null) {
			return;
		}
		for (long pos : from.positions()) {
			if (other == null || other.mask(pos) != from.mask(pos)) {
				out.add(pos);
			}
		}
	}
}
