package com.backrooms.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Menentukan tujuan portal Magnet. Sementara (sampai dimensi Backrooms ada): Overworld <-> Nether dengan skala
 * koordinat 1:8 seperti vanilla. Ganti {@link #resolve} saat dimensi Backrooms siap; pemanggil hanya butuh
 * dimensi dan titik tengah ruang portal di tujuan.
 *
 * <p>Titik tiba dicari di sekitar koordinat tujuan: ruang kosong {@code WIDTH x HEIGHT x 3} dengan lantai padat.
 * Jika tidak ada, ruangnya dibuat (lantai batu bata + ruang dikosongkan) seperti generator portal vanilla.
 */
public final class PortalDestination {
	/** Dimensi dan titik tengah ruang portal di tujuan. */
	public record Target(ResourceKey<Level> dimension, Vec3 center) {
	}

	private static final int WIDTH = 3;
	private static final int HEIGHT = 5;
	private static final int SEARCH_RADIUS = 12;

	private PortalDestination() {
	}

	public static Target resolve(ServerLevel from, Vec3 originCenter, Direction right) {
		ResourceKey<Level> key = from.dimension() == Level.NETHER ? Level.OVERWORLD : Level.NETHER;
		ServerLevel dest = from.getServer().getLevel(key);
		double scale = from.dimension() == Level.NETHER ? 8.0 : 1.0 / 8.0;
		if (dest == null) {
			// Nether dimatikan: pakai dimensi yang sama, digeser agar tidak menimpa bingkai sendiri.
			key = from.dimension();
			dest = from;
			scale = 1.0;
		}
		int x = Mth.floor(originCenter.x * scale);
		int z = Mth.floor(originCenter.z * scale);
		if (dest == from) {
			x += 128;
		}
		BlockPos spot = findSpot(dest, x, z, originCenter.y, right);
		if (spot == null) {
			spot = carve(dest, x, z, right);
		}
		// spot = dasar ruang portal pada kolom tengah; tengah ruang = naik setengah tinggi.
		return new Target(key, new Vec3(spot.getX() + 0.5, spot.getY() + HEIGHT / 2.0, spot.getZ() + 0.5));
	}

	/** Cari ruang kosong terdekat dari (x, z); null jika tidak ada. */
	private static BlockPos findSpot(ServerLevel level, int x, int z, double preferredY, Direction right) {
		int top = Math.min(level.getMaxY(), level.getMinY() + level.getLogicalHeight()) - HEIGHT - 3;
		int bottom = level.getMinY() + 2;
		for (int radius = 0; radius <= SEARCH_RADIUS; radius++) {
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
						continue;
					}
					int start = Mth.clamp((int) preferredY, bottom, top);
					for (int offset = 0; offset <= top - bottom; offset++) {
						// Bergantian dari Y yang diinginkan ke atas dan ke bawah.
						int y = start + ((offset & 1) == 0 ? offset / 2 : -(offset + 1) / 2);
						if (y < bottom || y > top) {
							continue;
						}
						BlockPos base = new BlockPos(x + dx, y, z + dz);
						if (fits(level, base, right)) {
							return base;
						}
					}
				}
			}
		}
		return null;
	}

	/** Ruang WIDTH x HEIGHT x 3 kosong (udara) dan lantai 3 x 3 padat di bawahnya. */
	private static boolean fits(ServerLevel level, BlockPos base, Direction right) {
		Direction normal = right.getClockWise();
		for (int a = -1; a <= 1; a++) {
			for (int n = -1; n <= 1; n++) {
				BlockPos column = base.relative(right, a).relative(normal, n);
				BlockState floor = level.getBlockState(column.below());
				if (!floor.isFaceSturdy(level, column.below(), Direction.UP)) {
					return false;
				}
				for (int h = 0; h < HEIGHT; h++) {
					if (!level.getBlockState(column.above(h)).isAir()) {
						return false;
					}
				}
			}
		}
		return true;
	}

	/** Membuat ruang tiba: lantai 5 x 5 dan ruang kosong di atasnya, di tinggi aman dimensi tujuan. */
	private static BlockPos carve(ServerLevel level, int x, int z, Direction right) {
		int y = Mth.clamp(level.getSeaLevel() + 8, level.getMinY() + 8, level.getMaxY() - HEIGHT - 8);
		if (level.dimension() == Level.NETHER) {
			y = 70;
		}
		BlockPos base = new BlockPos(x, y, z);
		Direction normal = right.getClockWise();
		for (int a = -2; a <= 2; a++) {
			for (int n = -2; n <= 2; n++) {
				BlockPos column = base.relative(right, a).relative(normal, n);
				level.setBlock(column.below(), Blocks.STONE_BRICKS.defaultBlockState(), Block.UPDATE_ALL);
				for (int h = 0; h < HEIGHT + 1; h++) {
					level.setBlock(column.above(h), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				}
			}
		}
		return base;
	}
}
