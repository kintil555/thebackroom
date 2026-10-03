package com.backrooms.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Placeholder portal: perilaku Nether Portal, tetapi bingkainya boleh Magnet (bukan hanya obsidian).
 * Portal hilang jika tetangga sebidang (bingkai Magnet / blok bawah) hilang.
 */
public class PlaceholderPortalBlock extends NetherPortalBlock {
	public PlaceholderPortalBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected BlockState updateShape(
		BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
		Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random
	) {
		Direction.Axis updateAxis = directionToNeighbour.getAxis();
		boolean wrongAxis = state.getValue(AXIS) != updateAxis && updateAxis.isHorizontal();
		if (!wrongAxis && !neighbourState.is(this) && (neighbourState.isAir() || neighbourState.canBeReplaced())) {
			return Blocks.AIR.defaultBlockState();
		}
		return state;
	}
}
