package com.backrooms.mixin;

import com.backrooms.client.ClientCoverPick;
import com.backrooms.cover.CoverAim;
import com.backrooms.cover.CoverClone;
import com.backrooms.cover.CoverManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin {
	/** Membidik sisi yang berlapis carpet: kecepatan break mengikuti carpet, bukan blok di bawahnya. */
	@Inject(method = "getDestroyProgress", at = @At("HEAD"), cancellable = true)
	private void backrooms$coverDestroyProgress(Player player, BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
		if (level instanceof Level realLevel && CoverManager.mask(realLevel, pos) != 0) {
			Direction aimed = CoverAim.aimedFace(player, pos);
			if (aimed != null && CoverManager.has(realLevel, pos, aimed)) {
				cir.setReturnValue(CoverManager.CARPET_PROGRESS_PER_TICK);
			}
		}
	}

	/**
	 * Middle click (ditangani server): hasilnya item blok pemilik + lapisannya, bernama "<Blok> Attached Carpet/Wallpaper".
	 * Pemanggil di client (F3, Jade, WTHIT) yang membidik sisi berlapis mendapat item Wallpaper/Carpet, bukan blok pemilik.
	 */
	@Inject(method = "getCloneItemStack", at = @At("RETURN"), cancellable = true)
	private void backrooms$cloneWithCovers(LevelReader level, BlockPos pos, boolean includeData, CallbackInfoReturnable<ItemStack> cir) {
		if (level instanceof Level realLevel) {
			Item aimedCover = realLevel.isClientSide() ? ClientCoverPick.aimedCoverItem(realLevel, pos) : null;
			if (aimedCover != null) {
				cir.setReturnValue(new ItemStack(aimedCover));
				return;
			}
			cir.setReturnValue(CoverClone.applyToPick(realLevel, pos, (BlockState) (Object) this, cir.getReturnValue()));
		}
	}
}
