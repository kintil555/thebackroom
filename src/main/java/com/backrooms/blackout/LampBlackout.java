package com.backrooms.blackout;

import com.backrooms.ModBlocks;
import com.backrooms.ModSounds;
import com.backrooms.block.LampBlock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Event blackout: saat mood player mencapai 100%, semua {@link LampBlock} dalam radius {@link #RADIUS}
 * mati satu per satu dari yang terjauh ke yang terdekat dengan player, lalu tetap mati selama
 * {@link #DURATION_TICKS} (2 menit) sebelum menyala kembali.
 *
 * Semua state hanya diakses dari thread server. Jika server berhenti saat blackout, state hilang dan
 * lampu yang mati dinyalakan lagi oleh random tick {@link LampBlock}.
 */
public final class LampBlackout {
	/** Radius (blok) lampu yang terkena. */
	public static final int RADIUS = 70;
	/** Lama lampu tetap mati: 2 menit. */
	public static final long DURATION_TICKS = 2L * 60L * 20L;

	/** Jeda (tick) antar langkah pemadaman: lampu mati "satu per satu" dengan cepat. */
	private static final int STEP_TICKS = 2;
	/** Batas durasi seluruh urutan pemadaman (tick); jika lampu banyak, tiap langkah memadamkan beberapa lampu. */
	private static final int MAX_SEQUENCE_TICKS = 120;
	/** Batas suara "untitled" bersamaan dalam {@link #SOUND_RANGE} blok. */
	private static final int MAX_SOUNDS_IN_RANGE = 6;
	private static final double SOUND_RANGE = 16.0;
	/** Lama suara masih dihitung aktif (tick); sedikit di atas durasi file (~1,65 detik). */
	private static final long SOUND_ACTIVE_TICKS = 36L;

	/** Lampu yang sedang dipadamkan blackout per dimensi: BlockPos.asLong() ke tick berakhirnya. */
	private static final Map<ResourceKey<Level>, Map<Long, Long>> OFF_UNTIL = new HashMap<>();
	/** Blackout per player yang belum selesai; mencegah pemicu ganda selama blackout masih berjalan. */
	private static final Map<UUID, Blackout> ACTIVE = new HashMap<>();
	private static final List<ActiveSound> ACTIVE_SOUNDS = new ArrayList<>();

	private record ActiveSound(ResourceKey<Level> dimension, BlockPos pos, long endTick) {
	}

	private static final class Blackout {
		final ResourceKey<Level> dimension;
		final List<BlockPos> lamps; // urut dari terjauh ke terdekat
		final int perStep;
		final long endTick;
		int index;
		long nextStepTick;

		Blackout(ResourceKey<Level> dimension, List<BlockPos> lamps, int perStep, long startTick) {
			this.dimension = dimension;
			this.lamps = lamps;
			this.perStep = perStep;
			this.endTick = startTick + DURATION_TICKS;
			this.nextStepTick = startTick;
		}
	}

	private LampBlackout() {
	}

	/** True jika lampu di pos sedang dipadamkan blackout dan tidak boleh menyala/berkedip. */
	public static boolean isBlackedOut(ServerLevel level, BlockPos pos) {
		Map<Long, Long> map = OFF_UNTIL.get(level.dimension());
		if (map == null) {
			return false;
		}
		Long end = map.get(pos.asLong());
		return end != null && end > level.getGameTime();
	}

	/** Dipanggil saat server menerima laporan mood 100% dari player. */
	public static void trigger(ServerPlayer player) {
		ServerLevel level = player.level();
		if (ACTIVE.containsKey(player.getUUID())) {
			return;
		}

		double px = player.getX();
		double py = player.getY();
		double pz = player.getZ();
		List<BlockPos> lamps = findLamps(level, px, py, pz);
		if (lamps.isEmpty()) {
			return;
		}
		// Terjauh lebih dulu sehingga gelap merambat mendekati player.
		lamps.sort((a, b) -> Double.compare(distSqr(b, px, py, pz), distSqr(a, px, py, pz)));

		int maxSteps = Math.max(1, MAX_SEQUENCE_TICKS / STEP_TICKS);
		int perStep = Math.max(1, (lamps.size() + maxSteps - 1) / maxSteps);
		long now = level.getGameTime();
		Blackout blackout = new Blackout(level.dimension(), lamps, perStep, now);

		Map<Long, Long> offUntil = OFF_UNTIL.computeIfAbsent(level.dimension(), key -> new HashMap<>());
		for (BlockPos pos : lamps) {
			offUntil.merge(pos.asLong(), blackout.endTick, Math::max);
		}
		ACTIVE.put(player.getUUID(), blackout);
	}

	/** Dipanggil tiap akhir tick server. */
	public static void tick(MinecraftServer server) {
		if (ACTIVE.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, Blackout>> iterator = ACTIVE.entrySet().iterator();
		while (iterator.hasNext()) {
			Blackout blackout = iterator.next().getValue();
			ServerLevel level = server.getLevel(blackout.dimension);
			if (level == null) {
				iterator.remove();
				continue;
			}
			long now = level.getGameTime();
			if (now >= blackout.endTick) {
				restore(level, blackout);
				iterator.remove();
			} else if (blackout.index < blackout.lamps.size() && now >= blackout.nextStepTick) {
				switchOffStep(level, blackout, now);
			}
		}
	}

	private static void switchOffStep(ServerLevel level, Blackout blackout, long now) {
		int end = Math.min(blackout.index + blackout.perStep, blackout.lamps.size());
		for (; blackout.index < end; blackout.index++) {
			BlockPos pos = blackout.lamps.get(blackout.index);
			if (!level.hasChunkAt(pos)) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if (!state.is(ModBlocks.LAMP) || !state.getValue(LampBlock.LIT)) {
				continue;
			}
			level.setBlock(pos, state.setValue(LampBlock.LIT, false), Block.UPDATE_CLIENTS);
			playSound(level, pos, now);
		}
		blackout.nextStepTick = now + STEP_TICKS;
	}

	private static void restore(ServerLevel level, Blackout blackout) {
		Map<Long, Long> offUntil = OFF_UNTIL.get(blackout.dimension);
		for (BlockPos pos : blackout.lamps) {
			if (offUntil == null) {
				break;
			}
			Long end = offUntil.get(pos.asLong());
			// Blackout lain yang lebih lama berjalan di lampu ini: biarkan dia yang menyalakan.
			if (end == null || end != blackout.endTick) {
				continue;
			}
			offUntil.remove(pos.asLong());
			if (!level.hasChunkAt(pos)) {
				continue; // chunk tidak termuat; random tick Lamp menyalakannya nanti
			}
			BlockState state = level.getBlockState(pos);
			if (state.is(ModBlocks.LAMP) && !state.getValue(LampBlock.LIT)) {
				level.setBlock(pos, state.setValue(LampBlock.LIT, true), Block.UPDATE_CLIENTS);
			}
		}
	}

	/** Mengumpulkan posisi Lamp dalam radius pada chunk termuat; section tanpa Lamp dilewati. */
	private static List<BlockPos> findLamps(ServerLevel level, double cx, double cy, double cz) {
		List<BlockPos> result = new ArrayList<>();
		double maxDistSqr = (double) RADIUS * RADIUS;
		int minChunkX = ((int) Math.floor(cx) - RADIUS) >> 4;
		int maxChunkX = ((int) Math.floor(cx) + RADIUS) >> 4;
		int minChunkZ = ((int) Math.floor(cz) - RADIUS) >> 4;
		int maxChunkZ = ((int) Math.floor(cz) + RADIUS) >> 4;

		for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
			for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
				if (chunk == null) {
					continue;
				}
				ChunkPos chunkPos = chunk.getPos();
				LevelChunkSection[] sections = chunk.getSections();
				for (int index = 0; index < sections.length; index++) {
					LevelChunkSection section = sections[index];
					if (section.hasOnlyAir() || !section.maybeHas(state -> state.is(ModBlocks.LAMP))) {
						continue;
					}
					int baseY = chunk.getSectionYFromSectionIndex(index) << 4;
					for (int y = 0; y < 16; y++) {
						for (int z = 0; z < 16; z++) {
							for (int x = 0; x < 16; x++) {
								if (!section.getBlockState(x, y, z).is(ModBlocks.LAMP)) {
									continue;
								}
								BlockPos pos = new BlockPos(chunkPos.getMinBlockX() + x, baseY + y, chunkPos.getMinBlockZ() + z);
								if (distSqr(pos, cx, cy, cz) <= maxDistSqr) {
									result.add(pos);
								}
							}
						}
					}
				}
			}
		}
		return result;
	}

	private static double distSqr(BlockPos pos, double x, double y, double z) {
		double dx = pos.getX() + 0.5 - x;
		double dy = pos.getY() + 0.5 - y;
		double dz = pos.getZ() + 0.5 - z;
		return dx * dx + dy * dy + dz * dz;
	}

	/** Suara "untitled" saat lampu mati; dibatasi {@link #MAX_SOUNDS_IN_RANGE} suara aktif dalam jangkauan dengar. */
	private static void playSound(ServerLevel level, BlockPos pos, long now) {
		ACTIVE_SOUNDS.removeIf(sound -> sound.endTick() <= now);
		double maxDistSqr = SOUND_RANGE * SOUND_RANGE;
		int nearby = 0;
		for (ActiveSound sound : ACTIVE_SOUNDS) {
			if (sound.dimension() == level.dimension() && sound.pos().distSqr(pos) <= maxDistSqr) {
				nearby++;
			}
		}
		if (nearby >= MAX_SOUNDS_IN_RANGE) {
			return;
		}
		ACTIVE_SOUNDS.add(new ActiveSound(level.dimension(), pos.immutable(), now + SOUND_ACTIVE_TICKS));
		level.playSound(null, pos, ModSounds.UNTITLED, SoundSource.BLOCKS, 1.0F, 1.0F);
	}
}
