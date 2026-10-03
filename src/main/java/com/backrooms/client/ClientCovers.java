package com.backrooms.client;

import com.backrooms.cover.CoverAttachments;
import com.backrooms.cover.CoverData;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import com.backrooms.mixin.client.RenderSectionRegionAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

/** Salinan sisi-client dari data carpet per chunk, dibaca thread yang membangun mesh chunk. */
public final class ClientCovers {
	/**
	 * Status per ClientLevel. Mod lain (mis. Seamless Portals / Immersive Portals) memuat beberapa ClientLevel
	 * sekaligus, jadi koordinat chunk saja tidak unik: kunci harus mencakup level-nya.
	 */
	private static final class LevelState {
		final Map<Long, CoverData> chunks = new ConcurrentHashMap<>();
		/** Chunk client yang sedang dimuat; dipakai untuk memeriksa ulang data carpet secara berkala. */
		final Map<Long, LevelChunk> loaded = new ConcurrentHashMap<>();
	}

	private static final Map<ClientLevel, LevelState> LEVELS = new ConcurrentHashMap<>();
	private static final int POLL_INTERVAL_TICKS = 10;
	private static int pollCounter;

	private ClientCovers() {
	}

	public static void init() {
		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> {
			LEVELS.computeIfAbsent(level, l -> new LevelState()).loaded.put(key(chunk.getPos().x(), chunk.getPos().z()), chunk);
			AttachmentTarget target = (AttachmentTarget) chunk;
			update(level, chunk, null, target.getAttached(CoverAttachments.COVERS));
			target.onAttachedSet(CoverAttachments.COVERS).register((oldData, newData) -> update(level, chunk, oldData, newData));
		});
		ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
			long chunkKey = key(chunk.getPos().x(), chunk.getPos().z());
			LevelState state = LEVELS.get(level);
			if (state != null) {
				state.chunks.remove(chunkKey);
				state.loaded.remove(chunkKey);
				if (state.loaded.isEmpty()) {
					LEVELS.remove(level, state);
				}
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(ClientCovers::poll);
	}

	/**
	 * Pengaman: data attachment bisa tiba setelah chunk dimuat atau tanpa memicu event di client.
	 * Secara berkala bandingkan salinan lokal dengan attachment chunk dan bangun ulang mesh jika berbeda.
	 */
	private static void poll(Minecraft minecraft) {
		if (minecraft.level == null) {
			LEVELS.clear();
			return;
		}
		if (++pollCounter < POLL_INTERVAL_TICKS) {
			return;
		}
		pollCounter = 0;
		for (Map.Entry<ClientLevel, LevelState> entry : LEVELS.entrySet()) {
			ClientLevel level = entry.getKey();
			LevelState state = entry.getValue();
			for (LevelChunk chunk : state.loaded.values()) {
				CoverData current = ((AttachmentTarget) chunk).getAttached(CoverAttachments.COVERS);
				if (current != null && current.isEmpty()) {
					current = null;
				}
				CoverData cached = state.chunks.get(key(chunk.getPos().x(), chunk.getPos().z()));
				if (current != cached) {
					update(level, chunk, cached, current);
				}
			}
		}
	}

	private static long key(int chunkX, int chunkZ) {
		return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
	}

	/**
	 * Bitmask sisi berlapis carpet di posisi ini (0 jika tidak ada). Aman dipanggil dari thread render chunk.
	 * {@code getter} dipakai untuk menentukan ClientLevel mana yang sedang digambar; jika tidak bisa ditentukan, 0.
	 */
	public static int mask(BlockAndTintGetter getter, BlockPos pos) {
		if (LEVELS.isEmpty()) {
			return 0;
		}
		ClientLevel level = levelOf(getter);
		if (level == null) {
			return 0;
		}
		LevelState state = LEVELS.get(level);
		if (state == null) {
			return 0;
		}
		CoverData data = state.chunks.get(key(pos.getX() >> 4, pos.getZ() >> 4));
		return data == null ? 0 : data.mask(pos.asLong());
	}

	private static ClientLevel levelOf(BlockAndTintGetter getter) {
		if (getter instanceof ClientLevel clientLevel) {
			return clientLevel;
		}
		if (getter instanceof RenderSectionRegion region) {
			return ((RenderSectionRegionAccessor) region).backrooms$getLevel();
		}
		return null;
	}

	private static void update(ClientLevel level, LevelChunk chunk, CoverData oldData, CoverData newData) {
		long chunkKey = key(chunk.getPos().x(), chunk.getPos().z());
		LevelState state = LEVELS.computeIfAbsent(level, l -> new LevelState());
		if (newData == null || newData.isEmpty()) {
			state.chunks.remove(chunkKey);
		} else {
			state.chunks.put(chunkKey, newData);
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
