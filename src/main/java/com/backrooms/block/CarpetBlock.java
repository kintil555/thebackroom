package com.backrooms.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Carpet: menempel pada sisi mana pun dari blok penuh (full block). */
public class CarpetBlock extends AttachedPanelBlock {
	public CarpetBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected boolean canAttachToBlock(BlockGetter level, BlockPos neighbourPos, BlockState neighbourState, Direction towardsNeighbour) {
		// Aturan vanilla: sisi tetangga yang menghadap kita harus penuh.
		return MultifaceBlock.canAttachTo(level, towardsNeighbour, neighbourPos, neighbourState);
	}
}
