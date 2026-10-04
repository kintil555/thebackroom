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
final class PortalSight {
	private PortalSight() {
	}

	/** True jika tidak ada blok penutup pandangan di antara {@code from} dan {@code to}. */
	static boolean clear(BlockGetter level, Vec3 from, Vec3 to) {
		return BlockGetter.traverseBlocks(from, to, level, (getter, pos) -> blocks(getter, pos, from, to) ? Boolean.FALSE : null, getter -> Boolean.TRUE);
	}

	private static boolean blocks(BlockGetter level, BlockPos pos, Vec3 from, Vec3 to) {
		BlockState state = level.getBlockState(pos);
		return state.canOcclude() && state.getCollisionShape(level, pos).clip(from, to, pos) != null;
	}
}
