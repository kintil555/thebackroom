package com.backrooms.mixin.client;

import com.backrooms.ModBlockEntities;
import com.backrooms.block.CoveredBlockEntity;
import com.backrooms.cover.PendingCoverData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	/**
	 * Vanilla membuang paket data block entity jika block entity-nya belum ada. Untuk blok yang baru ditaruh pemain,
	 * blok dari server baru terpasang setelah ack sehingga data carpet hilang (blok invisible). Simpan datanya
	 * dan terapkan begitu block entity dibuat (lihat CoveredBlockEntity#setLevel).
	 */
	@Inject(
		method = "handleBlockEntityData",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
			shift = At.Shift.AFTER
		)
	)
	private void backrooms$keepCoverData(ClientboundBlockEntityDataPacket packet, CallbackInfo ci) {
		if (packet.getType() != ModBlockEntities.COVERED_BLOCK) {
			return;
		}
		ClientLevel level = Minecraft.getInstance().level;
		if (level != null && !(level.getBlockEntity(packet.getPos()) instanceof CoveredBlockEntity)) {
			PendingCoverData.put(packet.getPos(), packet.getTag());
		}
	}
}
