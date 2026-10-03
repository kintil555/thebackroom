package com.backrooms.block;

import com.backrooms.network.PortalChargePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
 * Sisi menjorok Magnet (FACING) menghadap pemain saat dipasang. Kolom kiri/kanan harus menghadap ke tengah bingkai;
 * baris atas (HANGING) diputar mengikuti arah pemain.
 */
public class MagnetBlock extends Block {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	/** True untuk Magnet baris atas bingkai (tergantung di langit-langit); FACING tetap mengikuti arah pemain. */
	public static final BooleanProperty HANGING = BooleanProperty.create("hanging");
	/** True selama hitung mundur sebelum portal terbuka (dipasang di semua Magnet pada bingkai). */
	public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

	/** Power minimum redstone untuk memicu portal. */
	public static final int REQUIRED_POWER = 12;
	/** True pada Magnet pemimpin (kiri-bawah) setelah sirine dinyalakan. */
	public static final BooleanProperty WARNING = BooleanProperty.create("warning");
	/** Lama pengumpulan energi sebelum portal terbuka: 10 detik. */
	public static final int WARNING_TICKS = 200;
	/** Sirine mulai berbunyi 4 detik setelah power menyala. */
	public static final int SIREN_DELAY_TICKS = 80;
	/** Pemain dalam jarak ini (blok) dari tengah bingkai menerima efek glow pengisian energi. */
	private static final double GLOW_SYNC_RANGE_SQ = 128.0 * 128.0;

	public MagnetBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(HANGING, false).setValue(ACTIVE, false).setValue(WARNING, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, HANGING, ACTIVE, WARNING);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction clickedFace = context.getClickedFace();
		boolean hanging = clickedFace == Direction.DOWN;
		if (!hanging && clickedFace.getAxis().isHorizontal()) {
			// Disambung ke samping Magnet baris atas: ikut tergantung.
			BlockState clicked = context.getLevel().getBlockState(context.getClickedPos().relative(clickedFace.getOpposite()));
			hanging = clicked.getBlock() instanceof MagnetBlock && clicked.getValue(HANGING);
		}
		return defaultBlockState()
			.setValue(FACING, context.getHorizontalDirection().getOpposite())
			.setValue(HANGING, hanging);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (!state.is(oldState.getBlock())) {
			tryActivate(level, pos, state);
		}
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
		tryActivate(level, pos, state);
	}

	/** Mulai hitung mundur jika bingkai lengkap dan salah satu Magnet bertenaga (juga saat Magnet terakhir baru dipasang). */
	private void tryActivate(Level level, BlockPos pos, BlockState state) {
		if (!(level instanceof ServerLevel serverLevel) || state.getValue(ACTIVE)) {
			return;
		}
		MagnetFrame frame = MagnetFrame.find(serverLevel, pos, true);
		if (frame == null || !frame.isPowered(serverLevel)) {
			reportDebug(serverLevel, pos, frame);
			return;
		}
		// Semua Magnet bingkai ditandai ACTIVE. Magnet pertama (pemimpin) menyalakan sirine setelah SIREN_DELAY_TICKS,
		// lalu menyelesaikan sisa hitung mundur; Magnet lain langsung dijadwalkan selesai (tahan jika salah satu rusak).
		for (BlockPos magnetPos : frame.magnets()) {
			BlockState magnetState = serverLevel.getBlockState(magnetPos);
			serverLevel.setBlock(magnetPos, magnetState.setValue(ACTIVE, true).setValue(WARNING, false), Block.UPDATE_ALL);
			boolean leader = magnetPos.equals(frame.magnets().get(0));
			serverLevel.scheduleTick(magnetPos, this, leader ? SIREN_DELAY_TICKS : WARNING_TICKS);
		}
		syncChargeGlow(serverLevel, frame);
	}

	/** Beri tahu client di sekitar bingkai agar menggambar glow berkedip di tengah portal selama pengisian energi. */
	private static void syncChargeGlow(ServerLevel level, MagnetFrame frame) {
		BlockPos center = frame.center();
		PortalChargePayload payload = new PortalChargePayload(frame.magnets().get(0), center, WARNING_TICKS);
		for (ServerPlayer player : level.players()) {
			if (player.blockPosition().distSqr(center) < GLOW_SYNC_RANGE_SQ) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	/** Diagnosa sementara (hapus setelah portal beres): beri tahu pemain di dekat Magnet yang menerima power tetapi tidak membuka. */
	private static void reportDebug(ServerLevel level, BlockPos pos, @Nullable MagnetFrame frame) {
		int power = level.getBestNeighborSignal(pos);
		if (power <= 0) {
			return;
		}
		String reason = frame == null ? MagnetFrame.explain(level, pos) : "bingkai OK, tapi power kurang dari " + REQUIRED_POWER;
		Component message = Component.literal("[Magnet " + pos.toShortString() + "] power=" + power + "/" + REQUIRED_POWER + " | " + reason);
		for (ServerPlayer player : level.players()) {
			if (player.blockPosition().distSqr(pos) < 24 * 24) {
				player.sendSystemMessage(message);
			}
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!state.getValue(ACTIVE)) {
			return;
		}
		MagnetFrame frame = MagnetFrame.find(level, pos, false);
		if (frame != null && !state.getValue(WARNING) && pos.equals(frame.magnets().get(0))) {
			// Tahap 1 (pemimpin): sirine mulai berbunyi, lalu jadwalkan penyelesaian.
			level.setBlock(pos, state.setValue(WARNING, true), Block.UPDATE_ALL);
			SirenBlock.setNearby(level, frame.center(), true);
			level.scheduleTick(pos, this, WARNING_TICKS - SIREN_DELAY_TICKS);
			return;
		}
		level.setBlock(pos, state.setValue(ACTIVE, false).setValue(WARNING, false), Block.UPDATE_ALL);
		SirenBlock.setNearby(level, frame != null ? frame.center() : pos, false);
		// Bingkai masih utuh: buka (daya boleh hanya pulsa). Tick Magnet lain menemukan ruang sudah terisi, jadi tidak ganda.
		if (frame != null) {
			frame.openPortal(level);
		}
	}

	/** Efek mengumpulkan energi: partikel portal di sekitar Magnet selama hitung mundur. */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (!state.getValue(ACTIVE)) {
			return;
		}
		for (int i = 0; i < 2; i++) {
			level.addParticle(ParticleTypes.PORTAL,
				pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(),
				(random.nextDouble() - 0.5) * 0.5, (random.nextDouble() - 0.5) * 0.5, (random.nextDouble() - 0.5) * 0.5);
		}
	}
}
