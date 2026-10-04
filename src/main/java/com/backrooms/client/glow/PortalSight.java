package com.backrooms.client.glow;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Garis pandang kamera ke tengah portal untuk efek layar. Blok yang tidak menutup pandangan (kaca, iron bars, dan
 * blok lain yang {@code canOcclude()}-nya false) dilewati, jadi bloom, exposure, dan flashbang tetap terlihat
 * menembus blok bertekstur transparan. Raycast COLLIDER vanilla menganggap blok-blok itu dinding.
 */
public final class PortalSight {
	private PortalSight() {
	}

	/** True jika tidak ada blok penutup pandangan di antara {@code from} dan {@code to}. */
	public static boolean clear(BlockGetter level, Vec3 from, Vec3 to) {
		return BlockGetter.traverseBlocks(from, to, level, (getter, pos) -> blocks(getter, pos, from, to) ? Boolean.FALSE : null, getter -> Boolean.TRUE);
	}

	/**
	 * True jika kamera melihat portal: garis pandang ke tengah ruang portal, atau ke bagian atas/bawahnya (portal 5 blok
	 * tinggi), tidak terhalang. Mengintip dari celah tetap terhitung terlihat; berlindung penuh di balik blok tidak.
	 */
	static boolean visible(BlockGetter level, Vec3 eye, Vec3 center) {
		return clear(level, eye, center) || clear(level, eye, center.add(0.0, 1.8, 0.0)) || clear(level, eye, center.add(0.0, -1.8, 0.0));
	}

	/**
	 * Keterlihatan portal 0..1 dari 9 garis pandang yang tersebar di ruang portal (lebar sepanjang {@code right}, tinggi 5 blok).
	 * Tidak lagi hidup/mati dari satu garis ke tengah: tengah tertutup tiang tetap terhitung sebagian terlihat. Pembagian per piksel
	 * dilakukan depth buffer di shader; nilai ini hanya menskalakan kekuatan efek. Tiga garis terbuka sudah dianggap penuh.
	 */
	static float visibility(BlockGetter level, Vec3 eye, Vec3 center, Direction right) {
		Vec3 axis = Vec3.atLowerCornerOf(right.getUnitVec3i()).scale(1.0);
		int clear = 0;
		for (int column = -1; column <= 1; column++) {
			for (int row = -1; row <= 1; row++) {
				Vec3 point = center.add(axis.scale(column * 1.0)).add(0.0, row * 1.8, 0.0);
				if (clear(level, eye, point)) {
					clear++;
				}
			}
		}
		return Math.min(1.0f, clear / 3.0f);
	}

	private static boolean blocks(BlockGetter level, BlockPos pos, Vec3 from, Vec3 to) {
		BlockState state = level.getBlockState(pos);
		return state.canOcclude() && state.getCollisionShape(level, pos).clip(from, to, pos) != null;
	}
}
