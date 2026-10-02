package com.backrooms.mixin.client;

import com.backrooms.block.PanelCover;
import com.backrooms.client.CoverModels;
import com.backrooms.cover.CoverManager;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.state.level.BlockBreakingRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
	@Shadow
	@Final
	private ModelManager modelManager;

	/**
	 * Efek retak saat break: jika sisi yang dibidik berlapis, retak hanya digambar di lapisan itu
	 * (bukan di seluruh blok). Kalau tidak ada sisi berlapis, perilaku vanilla tidak disentuh.
	 */
	@Inject(method = "submitBlockDestroyAnimation", at = @At("HEAD"), cancellable = true)
	private void backrooms$crackOnlyCoveredFace(
		PoseStack poseStack, SubmitNodeCollector submitNodeCollector, LevelRenderState levelRenderState, CallbackInfo ci
	) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || levelRenderState.blockBreakingRenderStates.isEmpty()) {
			return;
		}

		boolean anyCovered = false;
		for (BlockBreakingRenderState state : levelRenderState.blockBreakingRenderStates) {
			Direction face = backrooms$aimedFace(state.blockPos());
			if (face != null && CoverManager.coverAt(level, state.blockPos(), state.blockState(), face) != PanelCover.NONE) {
				anyCovered = true;
				break;
			}
		}
		if (!anyCovered) {
			return;
		}

		Vec3 cameraPos = levelRenderState.cameraRenderState.pos;
		List<BlockStateModelPart> parts = new ArrayList<>();
		RandomSource random = RandomSource.createThreadLocalInstance();
		for (BlockBreakingRenderState state : levelRenderState.blockBreakingRenderStates) {
			if (state.blockState().getRenderShape() != RenderShape.MODEL) {
				continue;
			}
			BlockPos pos = state.blockPos();
			Direction face = backrooms$aimedFace(pos);
			PanelCover cover = face == null ? PanelCover.NONE : CoverManager.coverAt(level, pos, state.blockState(), face);

			poseStack.pushPose();
			poseStack.translate(pos.getX() - cameraPos.x(), pos.getY() - cameraPos.y(), pos.getZ() - cameraPos.z());
			poseStack.translate(state.blockState().getOffset(pos));
			BlockStateModel model = cover == PanelCover.NONE
				? this.modelManager.getBlockStateModelSet().get(state.blockState())
				: CoverModels.get(face, cover);
			random.setSeed(state.blockState().getSeed(pos));
			model.collectParts(random, parts);
			submitNodeCollector.submitBreakingBlockModel(poseStack, List.copyOf(parts), state.progress());
			parts.clear();
			poseStack.popPose();
		}
		ci.cancel();
	}

	private static Direction backrooms$aimedFace(BlockPos pos) {
		HitResult hit = Minecraft.getInstance().hitResult;
		if (hit instanceof BlockHitResult blockHit && blockHit.getBlockPos().equals(pos)) {
			return blockHit.getDirection();
		}
		return null;
	}
}
