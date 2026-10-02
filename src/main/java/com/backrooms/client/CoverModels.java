package com.backrooms.client;

import com.backrooms.ModBlocks;
import com.backrooms.block.PanelCover;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

public final class CoverModels {
	private CoverModels() {
	}

	/** Model lapisan tipis untuk sisi tertentu (dari blockstate cover_display). */
	public static BlockStateModel get(Direction face, PanelCover cover) {
		return Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(ModBlocks.COVER_DISPLAY.stateFor(face, cover));
	}

	/** Definisi item (assets/backrooms/items/cover_<jenis>_<sisi>.json) untuk lapisan tipis di ikon item. */
	public static Identifier itemModelId(Direction face, PanelCover cover) {
		return Identifier.fromNamespaceAndPath(com.backrooms.BackroomsMod.MOD_ID, "cover_" + cover.getSerializedName() + "_" + face.getSerializedName());
	}
}
