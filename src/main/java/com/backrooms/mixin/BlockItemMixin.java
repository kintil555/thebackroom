package com.backrooms.mixin;

import com.backrooms.cover.CoverClone;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
	/** Item hasil clone blok berlapis: setelah blok ditaruh (item belum dikonsumsi), pasang carpet yang dibawanya. */
	@Inject(method = "updateBlockStateFromTag", at = @At("HEAD"))
	private void backrooms$applyCarriedCovers(BlockPos pos, Level level, ItemStack itemStack, BlockState placedState, CallbackInfoReturnable<BlockState> cir) {
		CoverClone.applyOnPlace(level, pos, placedState, itemStack);
	}
}
