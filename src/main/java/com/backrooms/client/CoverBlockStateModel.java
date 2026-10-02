package com.backrooms.client;

import com.backrooms.block.PanelCover;
import com.backrooms.cover.CoverManager;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Pembungkus model blok biasa: setelah menggambar blok, gambar carpet di sisi-sisi yang berlapis.
 * Memakai jalur emitQuads milik Fabric Renderer API, karena Fabric mengalihkan seluruh render terrain
 * dari ModelBlockRenderer vanilla (mixin langsung ke ModelBlockRenderer tidak pernah terpanggil).
 */
public final class CoverBlockStateModel extends WrapperBlockStateModel {
	public CoverBlockStateModel(BlockStateModel wrapped) {
		super(wrapped);
	}

	@Override
	public void emitQuads(
		QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, Predicate<@Nullable Direction> cullTest
	) {
		int mask = ClientCovers.mask(pos);
		if (mask == 0) {
			super.emitQuads(emitter, level, pos, state, random, cullTest);
			return;
		}
		// Cover menempel rata dengan permukaan blok, jadi sisi blok di bawahnya tidak digambar (anti z-fighting).
		super.emitQuads(emitter, level, pos, state, random, face -> (face != null && (mask & CoverManager.bit(face)) != 0) || cullTest.test(face));
		for (Direction face : Direction.values()) {
			if ((mask & CoverManager.bit(face)) != 0) {
				CoverModels.get(face, PanelCover.CARPET).emitQuads(emitter, level, pos, state, random, cullTest);
			}
		}
	}

	/** Geometri blok berlapis berbeda per posisi, jadi tidak boleh di-cache berdasarkan key model saja. */
	@Override
	public @Nullable Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
		return ClientCovers.mask(pos) == 0 ? super.createGeometryKey(level, pos, state, random) : null;
	}
}
