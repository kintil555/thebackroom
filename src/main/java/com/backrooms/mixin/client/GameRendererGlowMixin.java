package com.backrooms.mixin.client;

import com.backrooms.client.glow.PortalDistortRenderer;
import com.backrooms.client.glow.PortalGlowRenderer;
import com.backrooms.client.light.ColoredLightRenderer;
import com.backrooms.client.light.LampBloomRenderer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Menjalankan post effect glow portal tepat setelah {@code LevelRenderer.doEntityOutline()}: dunia sudah tergambar,
 * GUI belum (titik yang sama dengan post effect spectator vanilla).
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererGlowMixin {
	@Shadow
	@Final
	private RenderTarget mainRenderTarget;
	@Shadow
	@Final
	private CrossFrameResourcePool resourcePool;

	@Inject(method = "render", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/LevelRenderer;doEntityOutline()V", shift = At.Shift.AFTER))
	private void backrooms$portalGlow(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
		// Cahaya berwarna lebih dulu agar bloom portal tergambar di atasnya.
		ColoredLightRenderer.render(this.mainRenderTarget, this.resourcePool, deltaTracker);
		LampBloomRenderer.render(this.mainRenderTarget, this.resourcePool, deltaTracker);
		PortalGlowRenderer.render(this.mainRenderTarget, this.resourcePool, deltaTracker);
		// Distorsi medan magnet pada portal yang terbuka, di atas bloom.
		PortalDistortRenderer.render(this.mainRenderTarget, this.resourcePool, deltaTracker);
	}
}
