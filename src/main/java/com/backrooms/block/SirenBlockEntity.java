package com.backrooms.block;

import com.backrooms.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Tanpa data: hanya penanda agar SirenRenderer bisa menggambar lampu berputar. */
public class SirenBlockEntity extends BlockEntity {
	public SirenBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.SIREN, pos, state);
	}
}
