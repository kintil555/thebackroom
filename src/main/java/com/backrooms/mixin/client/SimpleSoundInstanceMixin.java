package com.backrooms.mixin.client;

import com.backrooms.network.MoodPeakPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SimpleSoundInstance.class)
public abstract class SimpleSoundInstanceMixin {
	/**
	 * Mood vanilla (BiomeAmbientSoundsHandler) langsung di-reset ke 0 pada tick yang sama saat mencapai 100%,
	 * lalu memanggil forAmbientMood untuk memutar suara mood. Hanya handler itu yang memanggilnya,
	 * jadi pemanggilan ini = mood player mencapai 100%.
	 */
	@Inject(method = "forAmbientMood", at = @At("HEAD"))
	private static void backrooms$onMoodPeak(CallbackInfoReturnable<SimpleSoundInstance> cir) {
		if (Minecraft.getInstance().player != null) {
			ClientPlayNetworking.send(MoodPeakPayload.INSTANCE);
		}
	}
}
