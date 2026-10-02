package com.backrooms.mixin.client;

import com.backrooms.block.PanelCover;
import com.backrooms.client.CoverModels;
import com.backrooms.cover.CoverClone;
import com.backrooms.cover.CoverManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {
	@Shadow
	protected abstract ItemModel getItemModel(Identifier modelId);

	/** Item hasil pick blok biasa yang membawa carpet: tambahkan layer Carpet di tiap sisi yang dibawa (Screw Piles memakai item model JSON). */
	@Inject(method = "appendItemLayers", at = @At("TAIL"))
	private void backrooms$appendCarriedCovers(
		ItemStackRenderState output, ItemStack item, ItemDisplayContext displayContext, @Nullable Level level, @Nullable ItemOwner owner, int seed, CallbackInfo ci
	) {
		int mask = CoverClone.carriedMask(item);
		if (mask == 0) {
			return;
		}
		ClientLevel clientLevel = level instanceof ClientLevel client ? client : null;
		for (Direction face : Direction.values()) {
			if ((mask & CoverManager.bit(face)) != 0) {
				this.getItemModel(CoverModels.itemModelId(face, PanelCover.CARPET))
					.update(output, item, (ItemModelResolver) (Object) this, displayContext, clientLevel, owner, seed);
			}
		}
	}
}
