package com.backrooms.cover;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;

public final class CoverEffects {
	private CoverEffects() {
	}

	/** Partikel dan suara lepasnya lapisan: hanya di sisi (face) yang berlapis, dengan tekstur lapisannya. */
	public static void breakEffects(ServerLevel level, BlockPos pos, Direction face, Item coverItem) {
		double x = pos.getX() + 0.5 + face.getStepX() * 0.52;
		double y = pos.getY() + 0.5 + face.getStepY() * 0.52;
		double z = pos.getZ() + 0.5 + face.getStepZ() * 0.52;
		double spreadX = face.getStepX() == 0 ? 0.35 : 0.02;
		double spreadY = face.getStepY() == 0 ? 0.35 : 0.02;
		double spreadZ = face.getStepZ() == 0 ? 0.35 : 0.02;
		level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, coverItem), x, y, z, 14, spreadX, spreadY, spreadZ, 0.05);
		level.playSound(null, pos, SoundType.WOOL.getBreakSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
	}
}
