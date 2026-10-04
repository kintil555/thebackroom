package com.backrooms.portal;

import com.backrooms.block.MagnetFrame;
import com.backrooms.network.PortalOpenedPayload;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalManipulation;

/**
 * Portal Seamless Portals (Immersive Portals) untuk bingkai Magnet. Satu bukaan = 4 entitas Portal: dua sisi
 * (depan/belakang) di bingkai, dan pasangannya di tujuan agar bisa kembali. Entitas utama diberi tag
 * {@link #TAG}; menutup portal cukup menghapus entitas utama beserta pasangannya.
 */
public final class BackroomsPortals {
	public static final String TAG = "backrooms_portal";
	/** Pemain dalam jarak ini (blok) dari tengah bingkai menerima {@link PortalOpenedPayload}. */
	private static final double NOTIFY_RANGE_SQ = 128.0 * 128.0;

	private BackroomsPortals() {
	}

	/** True jika portal bertag sudah ada di tengah {@code center}. */
	public static boolean isOpen(ServerLevel level, BlockPos center) {
		return !find(level, center).isEmpty();
	}

	/** Membuka portal di ruang 3x5 bingkai. Idempoten: tidak melakukan apa-apa jika sudah terbuka. */
	public static void open(ServerLevel level, MagnetFrame frame) {
		BlockPos centerPos = frame.center();
		if (isOpen(level, centerPos)) {
			return;
		}
		Direction right = frame.right();
		Vec3 center = Vec3.atCenterOf(centerPos);
		PortalDestination.Target target = PortalDestination.resolve(level, center, right);

		Portal portal = new Portal(Portal.ENTITY_TYPE, level);
		portal.setOriginPos(center);
		portal.setOrientationAndSize(Vec3.atLowerCornerOf(right.getUnitVec3i()), new Vec3(0.0, 1.0, 0.0), MagnetFrame.WIDTH, MagnetFrame.HEIGHT);
		PortalAPI.setPortalTransformation(portal, target.dimension(), target.center(), null, 1.0);
		portal.entityTags().add(TAG);
		PortalAPI.spawnServerEntity(portal);
		PortalManipulation.completeBiWayBiFacedPortal(portal, removed -> {
		}, added -> {
		}, Portal.ENTITY_TYPE);

		PortalOpenedPayload payload = new PortalOpenedPayload(centerPos, right);
		for (ServerPlayer player : level.players()) {
			if (player.blockPosition().distSqr(centerPos) < NOTIFY_RANGE_SQ) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	/**
	 * Menutup portal yang bingkainya terkena {@code pos} (Magnet dihancurkan), beserta pasangan di tujuan. Hanya Magnet
	 * pada kolom/baris bingkai portal itu yang dihitung; Magnet bingkai lain di dekatnya tidak menutup portal ini.
	 */
	public static void closeNear(ServerLevel level, BlockPos pos) {
		AABB area = new AABB(pos).inflate(MagnetFrame.HEIGHT + 1.0);
		for (Portal portal : level.getEntitiesOfClass(Portal.class, area, p -> p.isAlive() && p.entityTags().contains(TAG))) {
			BlockPos center = BlockPos.containing(portal.getOriginPos());
			boolean alongX = Math.abs(portal.getAxisW().x) > 0.5;
			int along = alongX ? pos.getX() - center.getX() : pos.getZ() - center.getZ();
			int across = alongX ? pos.getZ() - center.getZ() : pos.getX() - center.getX();
			// Magnet bingkai: kolom kiri/kanan (jarak 2 dari tengah) dan baris atas, setinggi -2..+3 dari tengah.
			if (across != 0 || Math.abs(along) > MagnetFrame.WIDTH / 2 + 1 || pos.getY() - center.getY() < -MagnetFrame.HEIGHT / 2
				|| pos.getY() - center.getY() > MagnetFrame.HEIGHT / 2 + 1) {
				continue;
			}
			PortalManipulation.removeConnectedPortals(portal, removed -> {
			});
			portal.remove(Entity.RemovalReason.KILLED);
		}
	}

	private static List<Portal> find(ServerLevel level, BlockPos center) {
		return level.getEntitiesOfClass(Portal.class, new AABB(center).inflate(0.5), p -> p.isAlive() && p.entityTags().contains(TAG));
	}
}
