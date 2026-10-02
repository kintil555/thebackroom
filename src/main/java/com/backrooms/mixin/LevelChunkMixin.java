package com.backrooms.mixin;

import com.backrooms.cover.CoverManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {
	@Shadow
	@Final
	private Level level;

	/** Blok pemilik carpet berubah (dihancurkan, diganti, bentuknya tidak penuh lagi): carpet ikut lepas. */
	@Inject(method = "setBlockState", at = @At("RETURN"))
	private void backrooms$dropCoversOnHostChange(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<BlockState> cir) {
		if (cir.getReturnValue() != null && this.level instanceof ServerLevel serverLevel) {
			CoverManager.onHostChanged(serverLevel, (LevelChunk) (Object) this, pos, state);
		}
	}
}
