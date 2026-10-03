package com.backrooms.network;

import com.backrooms.BackroomsMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client ke server: mood player baru saja mencapai 100% (suara mood vanilla akan diputar). Tanpa data. */
public record MoodPeakPayload() implements CustomPacketPayload {
	public static final MoodPeakPayload INSTANCE = new MoodPeakPayload();

	public static final CustomPacketPayload.Type<MoodPeakPayload> TYPE =
		new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(BackroomsMod.MOD_ID, "mood_peak"));

	public static final StreamCodec<RegistryFriendlyByteBuf, MoodPeakPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
