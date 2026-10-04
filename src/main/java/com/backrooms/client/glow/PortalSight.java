package com.backrooms.client.glow;

import net.minecraft.core.BlockPos;
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

	private static boolean blocks(BlockGetter level, BlockPos pos, Vec3 from, Vec3 to) {
		BlockState state = level.getBlockState(pos);
		return state.canOcclude() && state.getCollisionShape(level, pos).clip(from, to, pos) != null;
	}
}
