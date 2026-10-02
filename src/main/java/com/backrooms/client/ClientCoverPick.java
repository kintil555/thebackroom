package com.backrooms.client;

import com.backrooms.cover.CoverAim;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Hanya client: sisi yang sedang dibidik crosshair, dipakai agar F3 / Jade / WTHIT mendeteksi Wallpaper atau Carpet. */
public final class ClientCoverPick {
	private ClientCoverPick() {
	}

	/** Wallpaper/Carpet pada sisi yang dibidik di pos, atau null jika crosshair tidak di pos itu atau sisinya tidak berlapis. */
	public static Item aimedCoverItem(Level level, BlockPos pos) {
		BlockHitResult hit = currentHit(pos);
		return hit == null ? null : CoverAim.coverItem(level, hit);
	}

	private static BlockHitResult currentHit(BlockPos pos) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos)) {
			return hit;
		}
		// Jangkauan Jade/WTHIT bisa melebihi jangkauan vanilla, jadi bidik ulang dari kamera.
		Entity camera = minecraft.getCameraEntity();
		if (camera != null && camera.pick(20.0, 0.0F, false) instanceof BlockHitResult hit
			&& hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos)) {
			return hit;
		}
		return null;
	}
}
