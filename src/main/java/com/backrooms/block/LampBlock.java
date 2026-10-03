package com.backrooms.block;

import net.minecraft.core.BlockPos;
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
 */
public class LampBlock extends Block {
	public static final BooleanProperty LIT = BooleanProperty.create("lit");

	/** Peluang 67% tiap random tick untuk memulai sesi kedip. */
	public static final float FLICKER_CHANCE = 0.67F;
	/** Peluang lanjut kedip lagi setelah satu siklus mati-nyala (rata-rata ~2-3 kedip per sesi). */
	private static final float FLICKER_CONTINUE_CHANCE = 0.6F;

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

	/** Jeda acak 2-6 tick (0,1-0,3 detik) antar perubahan. */
	private static int nextDelay(RandomSource random) {
		return 2 + random.nextInt(5);
	}
}
