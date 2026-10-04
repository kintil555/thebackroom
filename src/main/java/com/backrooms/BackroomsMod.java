package com.backrooms;

import com.backrooms.blackout.LampBlackout;
import com.backrooms.block.ScrewPilesBlock;
import com.backrooms.cover.CoverAttachments;
import com.backrooms.cover.CoverManager;
import com.backrooms.network.MoodPeakPayload;
import com.backrooms.network.PortalChargePayload;
import com.backrooms.network.PortalClosingPayload;
import com.backrooms.network.PortalOpenedPayload;
import com.backrooms.portal.BackroomsPortals;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
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
		ModParticles.init();
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
		// Magnet mulai mengisi energi: client menggambar glow berkedip di tengah bingkai portal.
		PayloadTypeRegistry.clientboundPlay().register(PortalChargePayload.TYPE, PortalChargePayload.CODEC);
		// Portal Seamless Portals terbuka: client memicu flash, alarm, dan distorsi.
		PayloadTypeRegistry.clientboundPlay().register(PortalOpenedPayload.TYPE, PortalOpenedPayload.CODEC);
		// Redstone padam: portal menutup dengan animasi di client, entitasnya dihapus server setelah CLOSE_TICKS.
		PayloadTypeRegistry.clientboundPlay().register(PortalClosingPayload.TYPE, PortalClosingPayload.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(BackroomsPortals::tick);
		ServerEntityEvents.ENTITY_LOAD.register(BackroomsPortals::onEntityLoad);
		ServerPlayNetworking.registerGlobalReceiver(MoodPeakPayload.TYPE, (payload, context) -> LampBlackout.trigger(context.player()));
		ServerTickEvents.END_SERVER_TICK.register(LampBlackout::tick);

		LOGGER.info("Backrooms mod loaded");
	}
}
