package com.backrooms.mixin.client;

import com.backrooms.client.glow.PortalDistortRenderer;
import com.backrooms.client.glow.PortalGlowRenderer;
import com.backrooms.client.light.ColoredLightRenderer;
import com.backrooms.client.light.LampBloomRenderer;
import com.backrooms.client.postfx.LevelMatrices;
import com.backrooms.client.postfx.WorldDepth;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
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

	/** Menangkap proyeksi dunia akhir (termasuk view-bobbing dan hurt-cam) yang dipakai post effect untuk proyeksi dan rekonstruksi depth. */
	@ModifyArg(method = "renderLevel", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"), index = 0)
	private Matrix4f backrooms$captureLevelProjection(Matrix4f projection) {
		return LevelMatrices.capture(projection);
	}

	/** Vanilla meng-clear depth main untuk tangan setelah dunia tergambar; salin depth dunia dulu agar post effect bisa mengoklusi. */
	@Inject(method = "renderLevel", at = @At(value = "INVOKE",
		target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearDepthTexture(Lcom/mojang/blaze3d/textures/GpuTexture;D)V"))
	private void backrooms$captureWorldDepth(DeltaTracker deltaTracker, CallbackInfo ci) {
		WorldDepth.capture(this.mainRenderTarget);
	}

	@Inject(method = "render", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/LevelRenderer;doEntityOutline()V", shift = At.Shift.AFTER))
	private void backrooms$portalGlow(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
		WorldDepth.restore(this.mainRenderTarget);
		// Cahaya berwarna lebih dulu agar bloom portal tergambar di atasnya.
		ColoredLightRenderer.render(this.mainRenderTarget, this.resourcePool, deltaTracker);
		LampBloomRenderer.render(this.mainRenderTarget, this.resourcePool, deltaTracker);
		PortalGlowRenderer.render(this.mainRenderTarget, this.resourcePool, deltaTracker);
		// Distorsi medan magnet pada portal yang terbuka, di atas bloom.
		PortalDistortRenderer.render(this.mainRenderTarget, this.resourcePool, deltaTracker);
	}
}
