package com.backrooms.cover;

import com.backrooms.ModBlocks;
import com.backrooms.block.PanelCover;
import com.backrooms.block.ScrewPilesBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Nama tampilan blok yang sedang berlapis: "Screw Piles Attached Wallpaper", "Concrete Attached Carpet", dst. */
public final class CoverNames {
	private CoverNames() {
	}

	/** Nama gabungan, atau null jika blok di posisi itu tidak berlapis apa pun. */
	public static @Nullable Component describe(Level level, BlockPos pos, BlockState state) {
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

		String key;
		if (wallpaper && carpet) {
			key = "backrooms.name.attached_wallpaper_carpet";
		} else if (wallpaper) {
			key = "backrooms.name.attached_wallpaper";
		} else if (carpet) {
			key = "backrooms.name.attached_carpet";
		} else {
			return null;
		}
		return Component.translatable(key, state.getBlock().getName());
	}
}
