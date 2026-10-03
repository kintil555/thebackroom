package com.backrooms.block;

import com.backrooms.ModSounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Lamp: menyala (light level 15, sama dengan Ochre Froglight) dan kadang berkedip.
 *
 * Alur kedip tanpa block entity:
 * - random tick pada lampu menyala: peluang {@link #FLICKER_CHANCE} mulai berkedip (mati + jadwalkan tick)
 * - scheduled tick hanya pernah dijadwalkan oleh kedip, jadi tiap tick membalik LIT
 * - tiap kali lampu kembali menyala, peluang {@link #FLICKER_CONTINUE_CHANCE} untuk kedip lagi,
 *   jika tidak lampu tetap menyala normal
 * - jika lampu ditemukan mati saat random tick (mis. tick hilang), dinyalakan kembali
 *
 * Suara: dimainkan sekali di awal sesi kedip. Maksimal {@link #MAX_SOUNDS_IN_RANGE} suara lampu
 * yang masih terdengar dalam radius {@link #SOUND_RANGE} blok; lampu lain tetap berkedip tanpa suara.
 */
public class LampBlock extends Block {
	public static final BooleanProperty LIT = BooleanProperty.create("lit");

	/** Peluang 67% tiap random tick untuk memulai sesi kedip. */
	public static final float FLICKER_CHANCE = 0.67F;
	/** Peluang lanjut kedip lagi setelah satu siklus mati-nyala (rata-rata ~2-3 kedip per sesi). */
	private static final float FLICKER_CONTINUE_CHANCE = 0.6F;

	/** Batas suara lampu yang bersamaan dalam jangkauan. */
	public static final int MAX_SOUNDS_IN_RANGE = 10;
	/** Radius (blok) pengecekan batas suara; sama dengan jangkauan dengar volume 1. */
	private static final double SOUND_RANGE = 16.0;
	/** Lama suara masih dihitung aktif (tick); sedikit di atas durasi file (~2,2 detik). */
	private static final long SOUND_ACTIVE_TICKS = 44L;

	/** Suara aktif per dimensi: posisi dan tick berakhirnya. Hanya diakses dari thread server. */
	private static final Map<ResourceKey<Level>, List<ActiveSound>> ACTIVE_SOUNDS = new HashMap<>();

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
		if (random.nextFloat() < FLICKER_CHANCE) {
			level.setBlock(pos, state.setValue(LIT, false), Block.UPDATE_CLIENTS);
			level.scheduleTick(pos, this, nextDelay(random));
			playFlickerSound(level, pos, random);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		boolean lit = state.getValue(LIT);
		level.setBlock(pos, state.setValue(LIT, !lit), Block.UPDATE_CLIENTS);
		// Baru saja menyala lagi: lanjut berkedip, atau selesai dan kembali normal.
		// Baru saja mati: selalu jadwalkan tick agar lampu menyala kembali.
		if (lit || random.nextFloat() < FLICKER_CONTINUE_CHANCE) {
			level.scheduleTick(pos, this, nextDelay(random));
		}
	}

	/** Memainkan suara kedip kecuali sudah ada {@link #MAX_SOUNDS_IN_RANGE} suara aktif dalam radius. */
	private static void playFlickerSound(ServerLevel level, BlockPos pos, RandomSource random) {
		long now = level.getGameTime();
		List<ActiveSound> active = ACTIVE_SOUNDS.computeIfAbsent(level.dimension(), key -> new ArrayList<>());
		active.removeIf(sound -> sound.endTick() <= now);

		double maxDistSqr = SOUND_RANGE * SOUND_RANGE;
		int nearby = 0;
		for (ActiveSound sound : active) {
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
