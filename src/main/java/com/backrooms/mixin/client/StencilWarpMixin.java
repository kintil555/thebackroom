package com.backrooms.mixin.client;

import com.backrooms.client.glow.PortalStencilWarp;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Menyisipkan distorsi ke render portal Seamless Portals (mode stencil-direct): tepat setelah dunia seberang tergambar ke target utama
 * dan sebelum depth shield STEP 3.7, stencil portal (EQUAL 1) masih aktif, jadi pass warp hanya mengenai bentuk portal.
 * Tanpa Seamless Portals mixin ini dilewati (@Pseudo).
 */
@Pseudo
@Mixin(targets = "com.warwa.seamlessportals.render.StencilPortalRenderer", remap = false)
public abstract class StencilWarpMixin {
	@Inject(
		method = "renderOnePortal",
		at = @At(value = "INVOKE", target = "Lcom/warwa/seamlessportals/render/PortalShapeRenderer;drawMergedPortalShapeWithDepthClear(Ljava/util/List;Lnet/minecraft/client/Camera;)V", ordinal = 1),
		remap = false,
		require = 0
	)
	private static void backrooms$warpPortalView(CallbackInfo ci) {
		PortalStencilWarp.apply();
	}
}
