package com.backrooms.mixin.client;

import com.backrooms.client.glow.PortalCompositeWarp;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Menyisipkan distorsi ke render portal Seamless Portals: pass komposit dunia seberang (full-screen triangle yang di-stencil ke bentuk
 * portal) menggambar lewat shader ber-warp, bukan blit lurus. Tanpa Seamless Portals mixin ini dilewati (@Pseudo).
 */
@Pseudo
@Mixin(targets = "com.warwa.seamlessportals.render.PortalContextSwitch", remap = false)
public abstract class PortalCompositeWarpMixin {
	@Shadow
	private static TextureTarget secondaryFbo;

	@Redirect(
		method = "compositePortalFbo",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;draw(IIII)V"),
		remap = false
	)
	private static void backrooms$warpComposite(RenderPass pass, int vertexCount, int instanceCount, int firstVertex, int firstInstance) {
		RenderTarget source = secondaryFbo;
		if (source != null && PortalCompositeWarp.draw(pass, source.getColorTextureView())) {
			return;
		}
		pass.draw(vertexCount, instanceCount, firstVertex, firstInstance);
	}
}
