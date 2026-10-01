package com.backrooms.block;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Panel tipis (1/16 blok) yang menempel pada satu sisi blok tetangga.
 * Dipasang pada sisi yang di-klik kanan, dan bisa dilepas per sisi.
 */
public abstract class AttachedPanelBlock extends MultifaceBlock {
	protected AttachedPanelBlock(Properties properties) {
		super(properties);
	}

	/** Apakah panel boleh menempel pada blok tetangga ini? */
	protected abstract boolean canAttachToBlock(BlockGetter level, BlockPos neighbourPos, BlockState neighbourState, Direction towardsNeighbour);

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		// Sisi yang di-klik menentukan arah tempel: panel menempel ke blok yang di-klik.
		return this.getStateForPlacement(level.getBlockState(pos), level, pos, context.getClickedFace().getOpposite());
	}

	@Override
	public boolean isValidStateForPlacement(BlockGetter level, BlockState oldState, BlockPos placementPos, Direction placementDirection) {
		if (!this.isFaceSupported(placementDirection) || (oldState.is(this) && hasFace(oldState, placementDirection))) {
			return false;
		}
		BlockPos neighbourPos = placementPos.relative(placementDirection);
		return this.canAttachToBlock(level, neighbourPos, level.getBlockState(neighbourPos), placementDirection);
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		boolean hasAtLeastOneFace = false;
		for (Direction direction : DIRECTIONS) {
			if (hasFace(state, direction)) {
				BlockPos neighbourPos = pos.relative(direction);
				if (!this.canAttachToBlock(level, neighbourPos, level.getBlockState(neighbourPos), direction)) {
					return false;
				}
				hasAtLeastOneFace = true;
			}
		}
		return hasAtLeastOneFace;
	}

	@Override
	protected BlockState updateShape(
		BlockState state,
		LevelReader level,
		ScheduledTickAccess ticks,
		BlockPos pos,
		Direction directionToNeighbour,
		BlockPos neighbourPos,
		BlockState neighbourState,
		RandomSource random
	) {
		if (state.getValue(WATERLOGGED)) {
			ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		}
		if (!hasAnyFace(state)) {
			return Blocks.AIR.defaultBlockState();
		}
		if (hasFace(state, directionToNeighbour) && !this.canAttachToBlock(level, neighbourPos, neighbourState, directionToNeighbour)) {
			BlockState without = state.setValue(getFaceProperty(directionToNeighbour), false);
			return hasAnyFace(without) ? without : Blocks.AIR.defaultBlockState();
		}
		return state;
	}

	/**
	 * Dipanggil saat pemain selesai "break". Jika blok punya lebih dari satu sisi,
	 * hanya sisi yang dibidik crosshair yang dilepas.
	 *
	 * @return true jika break sudah ditangani di sini (break vanilla harus dibatalkan)
	 */
	public boolean tryBreakAimedFace(Level level, Player player, BlockPos pos, BlockState state) {
		Set<Direction> faces = availableFaces(state);
		if (faces.size() <= 1) {
			return false; // satu sisi saja: biarkan break vanilla + loot table
		}
		if (level.isClientSide()) {
			return true; // server yang memutuskan, client menunggu sinkronisasi
		}

		Direction face = this.findAimedFace(player, pos, faces);
		level.setBlock(pos, state.setValue(getFaceProperty(face), false), Block.UPDATE_ALL);
		level.levelEvent(2001, pos, Block.getId(state));
		level.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(player, state));
		if (!player.isCreative()) {
			Block.popResource(level, pos, new ItemStack(this));
		}
		return true;
	}

	/** Sisi panel yang paling dekat dengan titik yang dibidik pemain. */
	private Direction findAimedFace(Player player, BlockPos pos, Set<Direction> faces) {
		HitResult hit = player.pick(player.blockInteractionRange() + 1.0, 1.0F, false);
		Direction best = faces.iterator().next();
		if (hit.getType() != HitResult.Type.BLOCK) {
			return best;
		}

		Vec3 location = hit.getLocation();
		double bestDistance = Double.MAX_VALUE;
		for (Direction face : faces) {
			Direction.Axis axis = face.getAxis();
			double plane = pos.get(axis) + (face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0);
			double distance = Math.abs(location.get(axis) - plane);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = face;
			}
		}
		return best;
	}
}
