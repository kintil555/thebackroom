package com.backrooms.block;

import com.backrooms.ModSounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Lamp: menyala (light level 15, sama dengan Ochre Froglight) dan kadang berkedip.
 *
 * Alur kedip tanpa block entity:
 * - random tick pada lampu menyala: peluang {@link #FLICKER_CHANCE} memulai kedip (mati + jadwalkan tick)
 * - scheduled tick pada lampu menyala = matikan lampu; pada lampu mati = nyalakan lagi, lalu peluang
 *   {@link #FLICKER_CONTINUE_CHANCE} untuk kedip lagi, jika tidak lampu kembali normal
 * - efek domino: saat sesi kedip dimulai dari random tick, lampu menyala lain dalam radius
 *   {@link #CASCADE_RADIUS} ikut dijadwalkan berkedip. Peluang menurun menurut jarak; sebagian
 *   berkedip bersamaan (delay minimal), sisanya tertunda sesuai jarak sehingga terlihat seperti gelombang
 * - lampu yang sudah ikut kedip masuk cooldown {@link #COOLDOWN_TICKS} agar tidak memicu/dipicu ulang
 *   terus-menerus (rantai tidak pernah tak terbatas)
 *
 * Suara: dimainkan di awal sesi kedip tiap lampu. Maksimal {@link #MAX_SOUNDS_IN_RANGE} suara aktif
 * dalam radius {@link #SOUND_RANGE} blok; lampu lain tetap berkedip tanpa suara.
 */
public class LampBlock extends Block {
	public static final BooleanProperty LIT = BooleanProperty.create("lit");

	/** Peluang 67% tiap random tick untuk memulai sesi kedip. */
	public static final float FLICKER_CHANCE = 0.67F;
	/** Peluang lanjut kedip lagi setelah satu siklus mati-nyala (rata-rata ~2-3 kedip per sesi). */
	private static final float FLICKER_CONTINUE_CHANCE = 0.6F;

	/** Radius (blok) penyebaran efek domino. */
	private static final int CASCADE_RADIUS = 8;
	/** Peluang ikut kedip untuk lampu yang sangat dekat; turun linear ke {@link #CASCADE_EDGE_CHANCE} di tepi radius. */
	private static final float CASCADE_NEAR_CHANCE = 0.9F;
	private static final float CASCADE_EDGE_CHANCE = 0.3F;
	/** Peluang lampu yang ikut kedip melakukannya bersamaan dengan pemicu (tanpa delay jarak). */
	private static final float CASCADE_SIMULTANEOUS_CHANCE = 0.3F;
	/** Cooldown (tick) setelah lampu ikut/memulai sesi kedip, sebelum bisa dipicu lagi. */
	private static final long COOLDOWN_TICKS = 100L;

	/** Batas suara lampu yang bersamaan dalam jangkauan. */
	public static final int MAX_SOUNDS_IN_RANGE = 10;
	/** Radius (blok) pengecekan batas suara; sama dengan jangkauan dengar volume 1. */
	private static final double SOUND_RANGE = 16.0;
	/** Lama suara masih dihitung aktif (tick); sedikit di atas durasi file (~2,2 detik). */
	private static final long SOUND_ACTIVE_TICKS = 44L;

	/** Suara aktif per dimensi: posisi dan tick berakhirnya. Hanya diakses dari thread server. */
	private static final Map<ResourceKey<Level>, List<ActiveSound>> ACTIVE_SOUNDS = new HashMap<>();
	/** Cooldown kedip per dimensi: BlockPos.asLong() ke tick berakhirnya. Hanya diakses dari thread server. */
	private static final Map<ResourceKey<Level>, Map<Long, Long>> COOLDOWNS = new HashMap<>();

	private record ActiveSound(BlockPos pos, long endTick) {
	}

	public LampBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(LIT, true));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LIT);
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!state.getValue(LIT)) {
			level.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_CLIENTS);
			return;
		}
		if (random.nextFloat() >= FLICKER_CHANCE || isOnCooldown(level, pos)) {
			return;
		}
		level.setBlock(pos, state.setValue(LIT, false), Block.UPDATE_CLIENTS);
		level.scheduleTick(pos, this, nextDelay(random));
		playFlickerSound(level, pos, random);
		spreadFlicker(level, pos, random);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		boolean lit = state.getValue(LIT);
		level.setBlock(pos, state.setValue(LIT, !lit), Block.UPDATE_CLIENTS);
		if (lit) {
			// Mulai mati: awal sesi (pemicu domino atau lanjutan kedip). Suara dilewati jika lampu ini masih bersuara.
			playFlickerSound(level, pos, random);
		}
		// Baru saja menyala lagi: lanjut berkedip, atau selesai dan kembali normal.
		// Baru saja mati: selalu jadwalkan tick agar lampu menyala kembali.
		if (lit || random.nextFloat() < FLICKER_CONTINUE_CHANCE) {
			level.scheduleTick(pos, this, nextDelay(random));
		}
	}

	/** Menjadwalkan kedip pada lampu menyala di sekitar origin: peluang dan delay bergantung jarak. */
	private void spreadFlicker(ServerLevel level, BlockPos origin, RandomSource random) {
		long now = level.getGameTime();
		Map<Long, Long> cooldowns = COOLDOWNS.computeIfAbsent(level.dimension(), key -> new HashMap<>());
		cooldowns.values().removeIf(end -> end <= now);
		cooldowns.put(origin.asLong(), now + COOLDOWN_TICKS);

		double maxDistSqr = (double) CASCADE_RADIUS * CASCADE_RADIUS;
		BlockPos min = origin.offset(-CASCADE_RADIUS, -CASCADE_RADIUS, -CASCADE_RADIUS);
		BlockPos max = origin.offset(CASCADE_RADIUS, CASCADE_RADIUS, CASCADE_RADIUS);
		for (BlockPos candidate : BlockPos.betweenClosed(min, max)) {
			double distSqr = candidate.distSqr(origin);
			if (distSqr == 0.0 || distSqr > maxDistSqr || !level.hasChunkAt(candidate)) {
				continue;
			}
			BlockState candidateState = level.getBlockState(candidate);
			if (!candidateState.is(this) || !candidateState.getValue(LIT) || cooldowns.containsKey(candidate.asLong())) {
				continue;
			}

			double dist = Math.sqrt(distSqr);
			float chance = CASCADE_NEAR_CHANCE - (CASCADE_NEAR_CHANCE - CASCADE_EDGE_CHANCE) * (float) (dist / CASCADE_RADIUS);
			if (random.nextFloat() >= chance) {
				continue;
			}

			// Sebagian bersamaan dengan pemicu, sisanya tertunda menurut jarak (+ jitter) seperti gelombang.
			int delay = random.nextFloat() < CASCADE_SIMULTANEOUS_CHANCE
				? 1
				: 2 + (int) (dist * 2.0) + random.nextInt(6);
			BlockPos target = candidate.immutable();
			cooldowns.put(target.asLong(), now + delay + COOLDOWN_TICKS);
			level.scheduleTick(target, this, delay);
		}
	}

	private static boolean isOnCooldown(ServerLevel level, BlockPos pos) {
		Map<Long, Long> cooldowns = COOLDOWNS.get(level.dimension());
		if (cooldowns == null) {
			return false;
		}
		Long end = cooldowns.get(pos.asLong());
		return end != null && end > level.getGameTime();
	}

	/**
	 * Memainkan suara kedip kecuali lampu ini masih bersuara, atau sudah ada
	 * {@link #MAX_SOUNDS_IN_RANGE} suara aktif dalam radius.
	 */
	private static void playFlickerSound(ServerLevel level, BlockPos pos, RandomSource random) {
		long now = level.getGameTime();
		List<ActiveSound> active = ACTIVE_SOUNDS.computeIfAbsent(level.dimension(), key -> new ArrayList<>());
		active.removeIf(sound -> sound.endTick() <= now);

		double maxDistSqr = SOUND_RANGE * SOUND_RANGE;
		int nearby = 0;
		for (ActiveSound sound : active) {
			if (sound.pos().equals(pos)) {
				return; // kedip lanjutan dari sesi yang sama, suara sebelumnya belum selesai
			}
			if (sound.pos().distSqr(pos) <= maxDistSqr) {
				nearby++;
			}
		}
		if (nearby >= MAX_SOUNDS_IN_RANGE) {
			return;
		}

		active.add(new ActiveSound(pos.immutable(), now + SOUND_ACTIVE_TICKS));
		level.playSound(null, pos, ModSounds.LAMP_FLICKER, SoundSource.BLOCKS, 0.8F, 0.95F + random.nextFloat() * 0.1F);
	}

	/** Jeda acak 2-6 tick (0,1-0,3 detik) antar perubahan. */
	private static int nextDelay(RandomSource random) {
		return 2 + random.nextInt(5);
	}
}
