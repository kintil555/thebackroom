package com.backrooms.cover;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class CoverAim {
	private CoverAim() {
	}

	/** Sisi blok di pos yang sedang dibidik crosshair pemain, atau null jika tidak membidik blok itu. */
	public static Direction aimedFace(Player player, BlockPos pos) {
		HitResult hit = player.pick(player.blockInteractionRange() + 1.0, 1.0F, false);
		if (hit instanceof BlockHitResult blockHit && blockHit.getBlockPos().equals(pos)) {
			return blockHit.getDirection();
		}
		return null;
	}
}
