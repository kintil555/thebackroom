package com.backrooms.client;

import com.backrooms.BackroomsMod;
import com.backrooms.block.SirenBlock;
import com.backrooms.block.SirenBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Menggambar sinar lampu sirine (spinning light) yang berputar di sumbu Y selama blok aktif. */
public class SirenRenderer implements BlockEntityRenderer<SirenBlockEntity, SirenRenderer.State> {
	private static final Identifier LIGHT = Identifier.fromNamespaceAndPath(BackroomsMod.MOD_ID, "textures/block/siren_light.png");
	/** Putaran (derajat) per tick. */
	private static final float DEGREES_PER_TICK = 15.0F;
	private static final int FULL_BRIGHT = 15728880;

	// Geometri dari spinning_light_sirine_effect.json (satuan piksel blok 1/16): bidang z=8, x -7..22, y 7..12.
	private static final float X0 = -7.0F / 16.0F;
	private static final float X1 = 22.0F / 16.0F;
	private static final float Y0 = 7.0F / 16.0F;
	private static final float Y1 = 12.0F / 16.0F;
	/** Pusat putaran = tengah bidang sinar (x 7.5, z 8). */
	private static final float PIVOT_X = 7.5F / 16.0F;
	private static final float PIVOT_Z = 0.5F;
	// UV sisi depan (0,0)-(29,5) dan belakang (29,0)-(58,5) pada tekstur 64x64.
	private static final float U_FRONT_0 = 0.0F;
	private static final float U_FRONT_1 = 29.0F / 64.0F;
	private static final float U_BACK_0 = 29.0F / 64.0F;
	private static final float U_BACK_1 = 58.0F / 64.0F;
	private static final float V0 = 0.0F;
	private static final float V1 = 5.0F / 64.0F;

	public SirenRenderer(BlockEntityRendererProvider.Context context) {
	}

	public static class State extends BlockEntityRenderState {
		public boolean active;
		public float angle;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(SirenBlockEntity blockEntity, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
		state.active = blockEntity.getBlockState().getValue(SirenBlock.ACTIVE);
		long time = blockEntity.getLevel() != null ? blockEntity.getLevel().getGameTime() : 0L;
		state.angle = ((time % 24L) + partialTicks) * DEGREES_PER_TICK; // 24 tick = 360 derajat penuh
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (!state.active) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(PIVOT_X, 0.0F, PIVOT_Z);
		poseStack.mulPose(Axis.YP.rotationDegrees(state.angle));
		poseStack.translate(-PIVOT_X, 0.0F, -PIVOT_Z);
		collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(LIGHT), (pose, buffer) -> {
			// Dua sisi (urutan vertex dibalik) agar terlihat dari depan maupun belakang.
			quad(pose, buffer, U_FRONT_0, U_FRONT_1, false);
			quad(pose, buffer, U_BACK_0, U_BACK_1, true);
		});
		poseStack.popPose();
	}

	private static void quad(PoseStack.Pose pose, VertexConsumer buffer, float u0, float u1, boolean reversed) {
		float z = PIVOT_Z;
		if (!reversed) {
			vertex(pose, buffer, X0, Y1, z, u0, V0);
			vertex(pose, buffer, X0, Y0, z, u0, V1);
			vertex(pose, buffer, X1, Y0, z, u1, V1);
			vertex(pose, buffer, X1, Y1, z, u1, V0);
		} else {
			vertex(pose, buffer, X1, Y1, z, u0, V0);
			vertex(pose, buffer, X1, Y0, z, u0, V1);
			vertex(pose, buffer, X0, Y0, z, u1, V1);
			vertex(pose, buffer, X0, Y1, z, u1, V0);
		}
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, float v) {
		buffer.addVertex(pose, x, y, z).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose, 0.0F, 1.0F, 0.0F);
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true; // sinar menjulur keluar dari batas 1 blok
	}
}
