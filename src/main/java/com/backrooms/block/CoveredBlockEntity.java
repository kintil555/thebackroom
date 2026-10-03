package com.backrooms.block;

import com.backrooms.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import com.backrooms.cover.PendingCoverData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.Level;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Data blok berlapis: blok asal (host, lengkap dengan properti) + bitmask sisi carpet.
 * Disimpan sebagai NBT block entity, sehingga ikut ter-copy oleh Axiom, structure block, /clone, WorldEdit, dsb.
 */
public class CoveredBlockEntity extends BlockEntity {
	private volatile @Nullable BlockState host;
	private volatile int mask;

	public CoveredBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.COVERED_BLOCK, pos, state);
	}

	public @Nullable BlockState host() {
		return this.host;
	}

	public int mask() {
		return this.mask;
	}

	public void init(BlockState host, int mask) {
		this.host = host;
		this.mask = mask;
		this.sync();
	}

	public void setMask(int mask) {
		this.mask = mask;
		this.sync();
	}

	private void sync() {
		this.setChanged();
		if (this.level != null) {
			this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_ALL);
		}
	}

	/** Client: terapkan data yang tiba lebih dulu daripada block entity ini (lihat PendingCoverData). */
	@Override
	public void setLevel(Level level) {
		super.setLevel(level);
		if (level.isClientSide()) {
			CompoundTag pending = PendingCoverData.take(this.worldPosition);
			if (pending != null) {
				this.host = pending.get("host") == null ? null : BlockState.CODEC.parse(NbtOps.INSTANCE, pending.get("host")).result().orElse(null);
				this.mask = pending.getIntOr("mask", 0);
				level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_IMMEDIATE);
			}
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.host = input.read("host", BlockState.CODEC).orElse(null);
		this.mask = input.getIntOr("mask", 0);
		// Di client: mesh chunk harus dibangun ulang begitu data tiba (UPDATE_IMMEDIATE = flag 8 menandai section kotor).
		if (this.level != null && this.level.isClientSide()) {
			this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_IMMEDIATE);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		BlockState current = this.host;
		if (current != null) {
			output.store("host", BlockState.CODEC, current);
		}
		output.putInt("mask", this.mask);
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return this.saveCustomOnly(registries);
	}
}
