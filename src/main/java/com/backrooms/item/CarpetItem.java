package com.backrooms.item;

import com.backrooms.block.PanelCover;
import com.backrooms.block.ScrewPilesBlock;
import com.backrooms.cover.CoverManager;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** Carpet: klik kanan sisi penuh blok mana pun untuk melapisinya. Tidak pernah menjadi blok terpisah. */
public class CarpetItem extends Item {
	public CarpetItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		InteractionResult result = ScrewPilesBlock.tryApplyCover(context, PanelCover.CARPET);
		return result == InteractionResult.PASS ? CoverManager.tryApplyCarpet(context) : result;
	}
}
