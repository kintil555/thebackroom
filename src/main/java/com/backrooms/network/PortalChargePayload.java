package com.backrooms.network;

import com.backrooms.BackroomsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server ke client: bingkai Magnet mulai mengumpulkan energi sebelum portal terbuka.
 * Client menggambar glow berkedip di {@code center} selama {@code durationTicks}.
 *
 * @param leader        Magnet pemimpin bingkai; dipakai client untuk memastikan bingkai masih utuh
 * @param center        blok tengah ruang portal
 * @param durationTicks lama pengisian energi (tick)
 */
public record PortalChargePayload(BlockPos leader, BlockPos center, int durationTicks) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<PortalChargePayload> TYPE =
		new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(BackroomsMod.MOD_ID, "portal_charge"));

	public static final StreamCodec<RegistryFriendlyByteBuf, PortalChargePayload> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, PortalChargePayload::leader,
		BlockPos.STREAM_CODEC, PortalChargePayload::center,
		ByteBufCodecs.VAR_INT, PortalChargePayload::durationTicks,
		PortalChargePayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
