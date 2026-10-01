package com.backrooms;

import com.backrooms.block.AttachedPanelBlock;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BackroomsMod implements ModInitializer {
	public static final String MOD_ID = "backrooms";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModBlocks.init();

		// Break pada blok panel multi-sisi hanya melepas sisi yang dibidik.
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
			!(state.getBlock() instanceof AttachedPanelBlock panel) || !panel.tryBreakAimedFace(level, player, pos, state));

		LOGGER.info("Backrooms mod loaded");
	}
}
