package com.backrooms.network;

import com.backrooms.BackroomsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server ke client: portal Seamless Portals di bingkai Magnet baru terbuka. Menggantikan deteksi lewat blok portal:
 * client memakainya untuk memicu flash, alarm, dan efek distorsi.
 *
 * @param center blok tengah ruang portal
 * @param right  sumbu lebar bingkai (EAST atau SOUTH)
 */
public record PortalOpenedPayload(BlockPos center, Direction right) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<PortalOpenedPayload> TYPE =
		new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(BackroomsMod.MOD_ID, "portal_opened"));

	public static final StreamCodec<RegistryFriendlyByteBuf, PortalOpenedPayload> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, PortalOpenedPayload::center,
		Direction.STREAM_CODEC, PortalOpenedPayload::right,
		PortalOpenedPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
