package com.backrooms.cover;

import com.backrooms.ModBlocks;
import com.backrooms.block.PanelCover;
import com.backrooms.block.ScrewPilesBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Nama item hasil clone (middle click) blok berlapis: "Screw Piles Attached Wallpaper", "Stone Attached Carpet", dst. */
public final class CoverNames {
	private CoverNames() {
	}

	public static Component attachedName(Block host, boolean wallpaper, boolean carpet) {
		String key;
		if (wallpaper && carpet) {
			key = "backrooms.name.attached_wallpaper_carpet";
		} else if (wallpaper) {
			key = "backrooms.name.attached_wallpaper";
		} else {
			key = "backrooms.name.attached_carpet";
		}
		return Component.translatable(key, host.getName());
	}

	/** Nama untuk HUD: null jika blok tidak berlapis Wallpaper/Carpet. */
	public static Component describe(Level level, BlockPos pos, BlockState state) {
		boolean wallpaper = false;
		boolean carpet = false;
		if (state.is(ModBlocks.SCREW_PILES)) {
			for (Direction face : Direction.values()) {
				PanelCover cover = ScrewPilesBlock.getCover(state, face);
				wallpaper |= cover == PanelCover.WALLPAPER;
				carpet |= cover == PanelCover.CARPET;
			}
		} else {
			carpet = CoverManager.mask(level, pos) != 0;
		}
		if (!wallpaper && !carpet) {
			return null;
		}
		return attachedName(state.getBlock(), wallpaper, carpet);
	}
}
