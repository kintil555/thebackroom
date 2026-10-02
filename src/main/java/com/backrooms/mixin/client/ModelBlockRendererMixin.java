package com.backrooms.mixin.client;

import com.backrooms.block.PanelCover;
import com.backrooms.client.ClientCovers;
import com.backrooms.client.CoverModels;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelBlockRenderer.class)
public abstract class ModelBlockRendererMixin {
	@Unique
	private boolean backrooms$drawingCover;

	@Shadow
	public abstract void tesselateBlock(
		BlockQuadOutput output, float x, float y, float z, BlockAndTintGetter level, BlockPos pos, BlockState blockState, BlockStateModel model, long seed
	);

	/** Setelah blok digambar ke mesh chunk, gambar carpet di sisi-sisi yang berlapis (dengan culling & cahaya vanilla). */
	@Inject(method = "tesselateBlock", at = @At("RETURN"))
	private void backrooms$drawCovers(
		BlockQuadOutput output, float x, float y, float z, BlockAndTintGetter level, BlockPos pos, BlockState blockState, BlockStateModel model, long seed, CallbackInfo ci
	) {
		if (this.backrooms$drawingCover) {
			return;
		}
		int mask = ClientCovers.mask(pos);
		if (mask == 0) {
			return;
		}
		this.backrooms$drawingCover = true;
		try {
			for (Direction face : Direction.values()) {
				if ((mask & (1 << face.ordinal())) != 0) {
					this.tesselateBlock(output, x, y, z, level, pos, blockState, CoverModels.get(face, PanelCover.CARPET), seed);
				}
			}
		} finally {
			this.backrooms$drawingCover = false;
		}
	}
}
