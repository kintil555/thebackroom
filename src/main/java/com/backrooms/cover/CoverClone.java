package com.backrooms.cover;

import com.backrooms.ModBlocks;
import com.backrooms.block.PanelCover;
import com.backrooms.block.ScrewPilesBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Middle click (pick block) pada blok berlapis menghasilkan item blok pemilik yang sudah "membawa" lapisannya,
 * dengan nama seperti "Stone Attached Carpet". Saat item itu dipasang, blok dan lapisannya muncul bersamaan.
 */
public final class CoverClone {
	/** Key di custom_data item: bitmask sisi carpet untuk blok biasa. Screw Piles memakai komponen block_state. */
	private static final String MASK_KEY = "backrooms_covers";

	private CoverClone() {
	}

	/** Ubah hasil pick block menjadi versi berlapis. Stack dikembalikan apa adanya jika blok tidak berlapis. */
	public static ItemStack applyToPick(Level level, BlockPos pos, BlockState state, ItemStack stack) {
		if (stack.isEmpty()) {
			return stack;
		}
		boolean wallpaper = false;
		boolean carpet = false;
		if (state.is(ModBlocks.SCREW_PILES)) {
			BlockItemStateProperties properties = BlockItemStateProperties.EMPTY;
			for (Direction face : Direction.values()) {
				PanelCover cover = ScrewPilesBlock.getCover(state, face);
				if (cover == PanelCover.NONE) {
					continue;
				}
				wallpaper |= cover == PanelCover.WALLPAPER;
				carpet |= cover == PanelCover.CARPET;
				properties = properties.with(ScrewPilesBlock.coverProperty(face), cover);
			}
			if (!wallpaper && !carpet) {
				return stack;
			}
			stack.set(DataComponents.BLOCK_STATE, properties);
		} else {
			int mask = CoverManager.mask(level, pos);
			if (mask == 0) {
				return stack;
			}
			carpet = true;
			CompoundTag tag = new CompoundTag();
			tag.putInt(MASK_KEY, mask);
			CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
		}
		stack.set(DataComponents.ITEM_NAME, CoverNames.attachedName(state.getBlock(), wallpaper, carpet));
		return stack;
	}

	/** Dipanggil setelah blok dari item ini ditaruh: pasang carpet yang dibawa item (hanya di server). */
	public static void applyOnPlace(Level level, BlockPos pos, BlockState placedState, ItemStack stack) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return;
		}
		int mask = data.copyTag().getIntOr(MASK_KEY, 0);
		if (mask != 0) {
			CoverManager.applyMask(serverLevel, pos, placedState, mask);
		}
	}
}
