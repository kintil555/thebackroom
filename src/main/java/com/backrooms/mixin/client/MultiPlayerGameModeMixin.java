package com.backrooms.mixin.client;

import com.backrooms.block.PanelCover;
import com.backrooms.cover.CoverManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
	/**
	 * Vanilla memprediksi break di client dengan menghapus blok (setBlock air) sebelum server menjawab.
	 * Untuk sisi berlapis, server hanya melepas lapisan sehingga blok muncul kembali (flicker).
	 * Prediksi dilewati agar blok tidak pernah hilang di client; paket break tetap dikirim.
	 */
	@Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
	private void backrooms$skipPredictionOnCover(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null || !(minecraft.hitResult instanceof BlockHitResult hit) || !hit.getBlockPos().equals(pos)) {
			return;
		}
		if (CoverManager.coverAt(level, pos, level.getBlockState(pos), hit.getDirection()) != PanelCover.NONE) {
			cir.setReturnValue(false);
		}
	}
}
