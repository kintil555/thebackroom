package com.backrooms.mixin.client;

import com.backrooms.client.glow.PortalGlowManager;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Menambah getar rotasi kamera setelah {@code alignWithEntity}, sebelum matriks view/proyeksi dihitung di
 * {@code Camera.update}. Hanya mengubah kamera render; rotasi pemain tidak berubah.
 */
@Mixin(Camera.class)
public abstract class CameraShakeMixin {
	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Shadow
	public abstract float yRot();

	@Shadow
	public abstract float xRot();

	@Shadow
	public abstract net.minecraft.world.phys.Vec3 position();

	@Inject(method = "update", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V", shift = At.Shift.AFTER))
	private void backrooms$portalShake(DeltaTracker deltaTracker, CallbackInfo ci) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		double nowTicks = level.getGameTime() + deltaTracker.getGameTimeDeltaPartialTick(false);
		float[] shake = PortalGlowManager.shakeOffset(nowTicks, this.position());
		if (shake[0] != 0.0f || shake[1] != 0.0f) {
			this.setRotation(this.yRot() + shake[0], Math.max(-90.0f, Math.min(90.0f, this.xRot() + shake[1])));
		}
	}
}
