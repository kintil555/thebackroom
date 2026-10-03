package com.backrooms.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.Nullable;

/**
 * Magnet: bagian bingkai portal ({@link MagnetFrame}). Jika bingkai lengkap dan salah satu Magnet dialiri
 * redstone power 15, sirine di sekitar berbunyi {@link #WARNING_TICKS}, lalu portal 3x5 terbuka di dalam bingkai.
 * Sisi menjorok Magnet (FACING) menghadap pemain saat dipasang, seperti End Portal Frame.
 */
public class MagnetBlock extends Block {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	/** True selama hitung mundur sebelum portal terbuka (dipasang di semua Magnet pada bingkai). */
	public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

	public static final int REQUIRED_POWER = 15;
	/** Lama peringatan sebelum portal terbuka: 5 detik. */
	public static final int WARNING_TICKS = 100;

	public MagnetBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(ACTIVE, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, ACTIVE);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
		if (!(level instanceof ServerLevel serverLevel) || state.getValue(ACTIVE) || level.getBestNeighborSignal(pos) < REQUIRED_POWER) {
			return;
		}
		MagnetFrame frame = MagnetFrame.find(serverLevel, pos, true);
		if (frame == null) {
			return;
		}
		// Semua Magnet bingkai ditandai ACTIVE dan masing-masing menjadwalkan tick selesai (tahan jika salah satu rusak).
		for (BlockPos magnetPos : frame.magnets()) {
			BlockState magnetState = serverLevel.getBlockState(magnetPos);
			serverLevel.setBlock(magnetPos, magnetState.setValue(ACTIVE, true), Block.UPDATE_ALL);
			serverLevel.scheduleTick(magnetPos, this, WARNING_TICKS);
		}
		SirenBlock.setNearby(serverLevel, frame.center(), true);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!state.getValue(ACTIVE)) {
			return;
		}
		level.setBlock(pos, state.setValue(ACTIVE, false), Block.UPDATE_ALL);
		MagnetFrame frame = MagnetFrame.find(level, pos, false);
		SirenBlock.setNearby(level, frame != null ? frame.center() : pos, false);
		// Bingkai masih utuh dan daya masih ada: buka. Tick Magnet lain menemukan ruang sudah terisi, jadi tidak ganda.
		if (frame != null && frame.isPowered(level)) {
			frame.openPortal(level);
		}
	}
}
