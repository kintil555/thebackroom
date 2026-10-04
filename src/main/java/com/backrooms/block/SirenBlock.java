package com.backrooms.block;

import com.backrooms.ModBlockEntities;
import com.backrooms.client.SirenAlarmSound;
import com.backrooms.client.light.ColoredLights;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jspecify.annotations.Nullable;

/**
 * Siren Alert: saat aktif (diatur Magnet) outline model tampil, lampu berputar (SirenRenderer), dan alarm
 * diputar loop di client (SirenAlarmSound). Saat mati outline disembunyikan (model siren_off).
 */
public class SirenBlock extends Block implements EntityBlock {
	public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
	/** Arah sirine menghadap (menjauh dari permukaan tempat dipasang): UP = di lantai, DOWN = di langit-langit, horizontal = di dinding. */
	public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

	/** Jangkauan (blok) sirine yang ikut dibunyikan oleh sebuah Magnet. */
	public static final int RANGE = 16;
	/** Lama sirine berbunyi sejak menyala: 18 detik (tidak ikut berhenti saat portal terbuka). */
	public static final int ACTIVE_TICKS = 360;
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

	/** Waktu habis: matikan sirine ini (client fade out lalu berhenti). */
	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (state.getValue(ACTIVE)) {
			level.setBlock(pos, state.setValue(ACTIVE, false), Block.UPDATE_ALL);
		}
	}

	/** Alarm dimainkan di client (SirenAlarmSound) agar bisa fade in/out dan pitch naik; server tidak lagi memutar suara. */
	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (!level.isClientSide() || type != ModBlockEntities.SIREN) {
			return null;
		}
		return (tickLevel, tickPos, tickState, entity) -> {
			if (tickLevel instanceof ClientLevel clientLevel) {
				SirenAlarmSound.tickBlock(clientLevel, tickPos, tickState);
				ColoredLights.markSiren(clientLevel, tickPos, tickState);
			}
		};
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
					level.scheduleTick(target, siren, ACTIVE_TICKS);
				}
			}
		}
	}
}
