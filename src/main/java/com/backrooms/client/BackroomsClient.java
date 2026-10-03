package com.backrooms.client;

import com.backrooms.ModBlockEntities;
import com.backrooms.ModBlocks;
import com.backrooms.client.glow.PortalGlowManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.world.level.block.Block;

public class BackroomsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientCovers.init();
		PortalGlowManager.init();
		BlockEntityRenderers.register(ModBlockEntities.SIREN, SirenRenderer::new);

		// Bungkus model semua blok biasa agar carpet (attachment chunk) ikut tergambar. Screw Piles menggambar
		// lapisannya lewat blockstate sendiri; cover_display tidak boleh dibungkus (dipakai sebagai model lapisan).
		ModelLoadingPlugin.register(context -> context.modifyBlockModelAfterBake().register(ModelModifier.WRAP_PHASE, (model, modifierContext) -> {
			Block block = modifierContext.state().getBlock();
			if (block == ModBlocks.COVERED_BLOCK) {
				return new CoveredBlockStateModel(model);
			}
			return block == ModBlocks.COVER_DISPLAY || block == ModBlocks.SCREW_PILES ? model : new CoverBlockStateModel(model);
		}));
	}
}
