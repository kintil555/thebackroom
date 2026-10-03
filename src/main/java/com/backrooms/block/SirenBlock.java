package com.backrooms.block;

import com.backrooms.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jspecify.annotations.Nullable;

/**
 * Siren Alert: saat aktif (diatur Magnet) outline model tampil, lampu berputar (SirenRenderer), dan alarm
 * diulang tiap {@link #LOOP_TICKS}. Saat mati outline disembunyikan (model siren_off).
 */
public class SirenBlock extends Block implements EntityBlock {
	public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
	/** Arah sirine menghadap (menjauh dari permukaan tempat dipasang): UP = di lantai, DOWN = di langit-langit, horizontal = di dinding. */
	public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

	/** Jangkauan (blok) sirine yang ikut dibunyikan oleh sebuah Magnet. */
	public static final int RANGE = 16;
	/** Durasi file alarm ~2,48 detik; diulang tiap 50 tick (2,5 detik). */
	private static final int LOOP_TICKS = 50;
	/** Volume > 1 memperluas jangkauan dengar (3.0 = 48 blok). */
	private static final float VOLUME = 3.0F;

	public SirenBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(ACTIVE, false).setValue(FACING, Direction.UP));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(ACTIVE, FACING);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		// Sisi yang diklik = arah hadap: klik atas blok -> berdiri di lantai, klik bawah -> menggantung, klik samping -> di dinding.
		return defaultBlockState().setValue(FACING, context.getClickedFace());
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new SirenBlockEntity(pos, state);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!state.getValue(ACTIVE)) {
			return;
		}
		level.playSound(null, pos, ModSounds.SIREN_ALARM, SoundSource.BLOCKS, VOLUME, 1.0F);
		level.scheduleTick(pos, this, LOOP_TICKS);
	}

	/** Menyalakan/mematikan semua Siren Alert dalam {@link #RANGE} blok dari origin. */
	public static void setNearby(ServerLevel level, BlockPos origin, boolean active) {
		BlockPos min = origin.offset(-RANGE, -RANGE, -RANGE);
		BlockPos max = origin.offset(RANGE, RANGE, RANGE);
		for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
			if (!level.hasChunkAt(pos)) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof SirenBlock siren && state.getValue(ACTIVE) != active) {
				BlockPos target = pos.immutable();
				level.setBlock(target, state.setValue(ACTIVE, active), Block.UPDATE_ALL);
				if (active) {
					level.scheduleTick(target, siren, 1);
				}
			}
		}
	}
}
