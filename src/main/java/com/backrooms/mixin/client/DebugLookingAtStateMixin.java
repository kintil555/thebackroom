package com.backrooms.mixin.client;

import com.backrooms.client.ClientCoverPick;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.debug.DebugEntryLookingAt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugEntryLookingAt.DebugEntryLookingAtState.class)
public abstract class DebugLookingAtStateMixin {
	/** F3 "Targeted Block": sisi berlapis menampilkan backrooms:wallpaper / backrooms:carpet tanpa property blok pemilik. */
	@Inject(method = "extractInfo", at = @At("HEAD"), cancellable = true)
	private void backrooms$showCover(List<String> result, Level level, BlockPos pos, CallbackInfo ci) {
		if (!((Object) this instanceof DebugEntryLookingAt.BlockStateInfo)) {
			return;
		}
		Item cover = ClientCoverPick.aimedCoverItem(level, pos);
		if (cover == null) {
			return;
		}
		result.add(ChatFormatting.UNDERLINE + "Targeted Block: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
		result.add(BuiltInRegistries.ITEM.getKey(cover).toString());
		ci.cancel();
	}
}
