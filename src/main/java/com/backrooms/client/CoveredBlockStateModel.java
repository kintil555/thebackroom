package com.backrooms.client;

import com.backrooms.block.CoveredBlockEntity;
import com.backrooms.block.PanelCover;
import com.backrooms.cover.CoverManager;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Model blok pembungkus: menggambar model blok asal (dari block entity) lalu carpet di sisi-sisi berlapis. */
public final class CoveredBlockStateModel extends WrapperBlockStateModel {
	public CoveredBlockStateModel(BlockStateModel wrapped) {
		super(wrapped);
	}

	@Override
	public void emitQuads(
		QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, Predicate<@Nullable Direction> cullTest
	) {
		if (!(level.getBlockEntity(pos) instanceof CoveredBlockEntity covered) || covered.host() == null) {
			return; // data belum tiba: jangan gambar apa pun (hindari kilatan model sementara)
		}
		BlockState host = covered.host();
		int mask = covered.mask();
		BlockStateModel hostModel = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(host);
		// Carpet menempel rata dengan permukaan blok, jadi sisi blok di bawahnya tidak digambar (anti z-fighting).
		hostModel.emitQuads(emitter, level, pos, host, random, face -> (face != null && (mask & CoverManager.bit(face)) != 0) || cullTest.test(face));
		for (Direction face : Direction.values()) {
			if ((mask & CoverManager.bit(face)) != 0) {
				CoverModels.get(face, PanelCover.CARPET).emitQuads(emitter, level, pos, host, random, cullTest);
			}
		}
	}

	@Override
	public @Nullable Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
		return null;
	}
}
