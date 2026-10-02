package com.backrooms.cover;

import com.backrooms.ModBlocks;
import com.backrooms.block.PanelCover;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
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

	/** Item Wallpaper/Carpet yang menutupi sisi yang kena hit, atau null jika sisi itu tidak berlapis. */
	public static Item coverItem(Level level, BlockHitResult hit) {
		if (hit.getType() != HitResult.Type.BLOCK) {
			return null;
		}
		BlockPos pos = hit.getBlockPos();
		PanelCover cover = CoverManager.coverAt(level, pos, level.getBlockState(pos), hit.getDirection());
		return switch (cover) {
			case WALLPAPER -> ModBlocks.WALLPAPER;
			case CARPET -> ModBlocks.CARPET;
			case NONE -> null;
		};
	}
}
