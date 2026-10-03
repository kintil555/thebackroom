package com.backrooms;

import com.backrooms.block.ScrewPilesBlock;
import com.backrooms.cover.CoverAttachments;
import com.backrooms.cover.CoverManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BackroomsMod implements ModInitializer {
	public static final String MOD_ID = "backrooms";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		CoverAttachments.init();
		ModSounds.init();
		ModBlocks.init();

		// Break pada sisi berlapis hanya melepas lapisannya; blok yang dilapisi tetap berdiri.
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (state.getBlock() instanceof ScrewPilesBlock screwPiles && screwPiles.tryBreakCover(level, player, pos, state)) {
				return false;
			}
			return !CoverManager.tryBreakCover(level, player, pos);
		});

		LOGGER.info("Backrooms mod loaded");
	}
}
