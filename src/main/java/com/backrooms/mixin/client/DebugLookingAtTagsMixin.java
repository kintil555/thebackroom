package com.backrooms.mixin.client;

import com.backrooms.client.ClientCoverPick;
import java.util.List;
import net.minecraft.client.gui.components.debug.DebugEntryLookingAt;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugEntryLookingAt.DebugEntryLookingAtTags.class)
public abstract class DebugLookingAtTagsMixin {
	/** F3 tag blok: sisi berlapis tidak menampilkan tag blok pemilik (lapisan bukan blok, jadi tidak punya tag blok). */
	@Inject(method = "extractInfo", at = @At("HEAD"), cancellable = true)
	private void backrooms$hideHostTags(List<String> result, Level level, BlockPos pos, CallbackInfo ci) {
		if ((Object) this instanceof DebugEntryLookingAt.BlockTagInfo && ClientCoverPick.aimedCoverItem(level, pos) != null) {
			ci.cancel();
		}
	}
}
