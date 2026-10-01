package com.backrooms.block;

import com.backrooms.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/** Wallpaper: hanya bisa menempel pada Screw Piles, butuh 4 detik untuk dilepas. */
public class WallpaperBlock extends AttachedPanelBlock {
	public static final int BREAK_SECONDS = 4;
	private static final float BREAK_PROGRESS_PER_TICK = 1.0F / (BREAK_SECONDS * 20);

	public WallpaperBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected boolean canAttachToBlock(BlockGetter level, BlockPos neighbourPos, BlockState neighbourState, Direction towardsNeighbour) {
		return neighbourState.is(ModBlocks.SCREW_PILES);
	}

	/** Progres break per tick dibuat tetap, apa pun alat yang dipakai: 80 tick = 4 detik. */
	@Override
	protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
		return BREAK_PROGRESS_PER_TICK;
	}
}
