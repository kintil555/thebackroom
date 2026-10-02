package com.backrooms.item;

import com.backrooms.block.PanelCover;
import com.backrooms.block.ScrewPilesBlock;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** Wallpaper: klik kanan sisi Screw Piles untuk melapisi sisi itu. Tidak bisa ditaruh di tempat lain. */
public class WallpaperItem extends Item {
	public WallpaperItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		return ScrewPilesBlock.tryApplyCover(context, PanelCover.WALLPAPER);
	}
}
