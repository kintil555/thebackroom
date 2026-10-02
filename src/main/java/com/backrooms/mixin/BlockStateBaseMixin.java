package com.backrooms.mixin;

import com.backrooms.cover.CoverAim;
import com.backrooms.cover.CoverManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
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
}
