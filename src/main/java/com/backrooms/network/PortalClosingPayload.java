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
 * Server ke client: portal di bingkai Magnet mulai menutup karena redstone padam. Client memainkan animasi
 * penutupan (distorsi melengkung, putih kehijauan, glow, lalu mengecil ke tengah) selama {@code durationTicks};
 * server menghapus entitas portal setelah durasi itu.
 *
 * @param center        blok tengah ruang portal
 * @param right         sumbu lebar bingkai (EAST atau SOUTH)
 * @param durationTicks lama animasi penutupan (tick)
 */
public record PortalClosingPayload(BlockPos center, Direction right, int durationTicks) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<PortalClosingPayload> TYPE =
		new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(BackroomsMod.MOD_ID, "portal_closing"));

	public static final StreamCodec<RegistryFriendlyByteBuf, PortalClosingPayload> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, PortalClosingPayload::center,
		Direction.STREAM_CODEC, PortalClosingPayload::right,
		ByteBufCodecs.VAR_INT, PortalClosingPayload::durationTicks,
		PortalClosingPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
