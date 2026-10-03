package com.backrooms.block;

import com.backrooms.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Bingkai portal dari Magnet, seperti End Portal Frame. Ruang portal {@link #WIDTH} x {@link #HEIGHT} (lebar x tinggi),
 * koordinat (a, h): a = geser sepanjang {@link #right}, h = naik dari {@link #base}.
 * - Magnet: kolom kiri (a=-1) dan kanan (a=WIDTH) setinggi portal, plus baris atas (h=HEIGHT). Sudut tidak perlu.
 * - Bawah (h=-1): blok apa saja asal bukan udara/replaceable.
 * - Sisi magnet yang menjorok (FACING) di kolom kiri/kanan harus menghadap ke dalam, jadi pemain berdiri di tengah
 *   saat memasangnya. Baris atas bebas menghadap ke mana saja.
 */
public record MagnetFrame(BlockPos base, Direction right, List<BlockPos> magnets) {
	public static final int WIDTH = 3;
	public static final int HEIGHT = 5;

	/** Offset {a, h} semua Magnet pada bingkai (13 buah). */
	private static final List<int[]> MAGNET_OFFSETS = buildOffsets();

	private static List<int[]> buildOffsets() {
		List<int[]> offsets = new ArrayList<>();
		for (int h = 0; h < HEIGHT; h++) {
			offsets.add(new int[] {-1, h});
			offsets.add(new int[] {WIDTH, h});
		}
		for (int a = 0; a < WIDTH; a++) {
			offsets.add(new int[] {a, HEIGHT});
		}
		return List.copyOf(offsets);
	}

	/** Mencari bingkai valid yang memuat Magnet di {@code pos}. {@code requireIdle}: semua Magnet belum ACTIVE. */
	public static @Nullable MagnetFrame find(Level level, BlockPos pos, boolean requireIdle) {
		for (Direction right : new Direction[] {Direction.EAST, Direction.SOUTH}) {
			for (int[] offset : MAGNET_OFFSETS) {
				BlockPos base = pos.relative(right.getOpposite(), offset[0]).below(offset[1]);
				MagnetFrame frame = validate(level, base, right, requireIdle);
				if (frame != null) {
					return frame;
				}
			}
		}
		return null;
	}

	private static @Nullable MagnetFrame validate(Level level, BlockPos base, Direction right, boolean requireIdle) {
		for (int a = 0; a < WIDTH; a++) {
			BlockState floor = stateAt(level, at(base, right, a, -1));
			if (floor.isAir() || floor.canBeReplaced()) {
				return null;
			}
			for (int h = 0; h < HEIGHT; h++) {
				BlockState inside = stateAt(level, at(base, right, a, h));
				if (!inside.isAir() && !inside.canBeReplaced()) {
					return null;
				}
			}
		}
		List<BlockPos> magnets = new ArrayList<>(MAGNET_OFFSETS.size());
		for (int[] offset : MAGNET_OFFSETS) {
			int a = offset[0];
			BlockPos magnetPos = at(base, right, a, offset[1]);
			BlockState state = stateAt(level, magnetPos);
			if (!(state.getBlock() instanceof MagnetBlock) || (requireIdle && state.getValue(MagnetBlock.ACTIVE))) {
				return null;
			}
			Direction facing = state.getValue(MagnetBlock.FACING);
			if (a == -1 && facing != right || a == WIDTH && facing != right.getOpposite()) {
				return null;
			}
			magnets.add(magnetPos);
		}
		return new MagnetFrame(base, right, List.copyOf(magnets));
	}

	private static BlockPos at(BlockPos base, Direction right, int a, int h) {
		return base.relative(right, a).above(h);
	}

	private static BlockState stateAt(Level level, BlockPos pos) {
		return level.hasChunkAt(pos) ? level.getBlockState(pos) : Blocks.VOID_AIR.defaultBlockState();
	}

	/** Tengah ruang portal. */
	public BlockPos center() {
		return at(this.base, this.right, WIDTH / 2, HEIGHT / 2);
	}

	/** True jika salah satu Magnet menerima redstone power 15. */
	public boolean isPowered(Level level) {
		for (BlockPos pos : this.magnets) {
			if (level.getBestNeighborSignal(pos) >= MagnetBlock.REQUIRED_POWER) {
				return true;
			}
		}
		return false;
	}

	/** Placeholder: mengisi ruang 3x5 dengan Nether Portal. TODO: ganti ke portal dimensi Backrooms. */
	public void openPortal(ServerLevel level) {
		BlockState portal = ModBlocks.PLACEHOLDER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, this.right.getAxis());
		for (int a = 0; a < WIDTH; a++) {
			for (int h = 0; h < HEIGHT; h++) {
				level.setBlock(at(this.base, this.right, a, h), portal, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			}
		}
	}
}
