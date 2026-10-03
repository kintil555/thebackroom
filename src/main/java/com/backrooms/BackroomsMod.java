package com.backrooms;

import com.backrooms.blackout.LampBlackout;
import com.backrooms.block.ScrewPilesBlock;
import com.backrooms.cover.CoverAttachments;
import com.backrooms.cover.CoverManager;
import com.backrooms.network.MoodPeakPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
		ModBlockEntities.init();

		// Break pada sisi berlapis hanya melepas lapisannya; blok yang dilapisi tetap berdiri.
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (state.getBlock() instanceof ScrewPilesBlock screwPiles && screwPiles.tryBreakCover(level, player, pos, state)) {
				return false;
			}
			return !CoverManager.tryBreakCover(level, player, pos);
		});

		// Mood player 100% (dilaporkan client) memadamkan lampu sekitar selama 2 menit.
		PayloadTypeRegistry.serverboundPlay().register(MoodPeakPayload.TYPE, MoodPeakPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(MoodPeakPayload.TYPE, (payload, context) -> LampBlackout.trigger(context.player()));
		ServerTickEvents.END_SERVER_TICK.register(LampBlackout::tick);

		LOGGER.info("Backrooms mod loaded");
	}
}
