package com.backrooms.portal;

import com.backrooms.block.MagnetBlock;
import com.backrooms.block.MagnetFrame;
import com.backrooms.network.PortalClosingPayload;
import com.backrooms.network.PortalOpenedPayload;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
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
	/** Tag tambahan selama animasi penutupan; portal bertag ini dianggap sudah tidak terbuka. */
	public static final String CLOSING_TAG = "backrooms_portal_closing";
	/** Lama animasi penutupan: 2 detik. Harus sama dengan yang diputar client (lihat {@link PortalClosingPayload}). */
	public static final int CLOSE_TICKS = 40;
	/** Pemain dalam jarak ini (blok) dari tengah bingkai menerima {@link PortalOpenedPayload}. */
	private static final double NOTIFY_RANGE_SQ = 128.0 * 128.0;

	private static final List<PendingClose> PENDING = new ArrayList<>();

	/** Penutupan yang menunggu animasi selesai; {@code magnet} dipakai memicu ulang pengisian jika redstone sudah menyala lagi. */
	private record PendingClose(ServerLevel level, BlockPos center, BlockPos magnet, long endTick) {
	}

	private BackroomsPortals() {
	}

	/** True jika portal bertag sudah ada di tengah {@code center} dan tidak sedang menutup. */
	public static boolean isOpen(ServerLevel level, BlockPos center) {
		return find(level, center).stream().anyMatch(p -> !p.entityTags().contains(CLOSING_TAG));
	}

	/** True jika ada portal bertag di tengah {@code center}, terbuka maupun sedang menutup. */
	public static boolean isBusy(ServerLevel level, BlockPos center) {
		return !find(level, center).isEmpty();
	}

	/**
	 * Menutup portal bingkai dengan animasi: portal dikunci agar tidak bisa dilewati, client memutar animasi
	 * selama {@link #CLOSE_TICKS}, lalu entitas dihapus di {@link #tick}. Aman dipanggil berulang.
	 */
	public static void beginClose(ServerLevel level, MagnetFrame frame) {
		BlockPos centerPos = frame.center();
		List<Portal> portals = find(level, centerPos);
		boolean started = false;
		for (Portal portal : portals) {
			if (portal.entityTags().contains(CLOSING_TAG)) {
				continue;
			}
			started = true;
			portal.entityTags().add(CLOSING_TAG);
			for (Vec3 normal : new Vec3[] {portal.getNormal(), portal.getNormal().scale(-1.0)}) {
				for (Portal face : PortalManipulation.getPortalCluster(level, portal.getOriginPos(), normal, p -> true)) {
					face.setTeleportable(false);
					face.reloadAndSyncToClient();
				}
			}
		}
		if (!started) {
			return;
		}
		PENDING.add(new PendingClose(level, centerPos, frame.magnets().get(0), level.getGameTime() + CLOSE_TICKS));
		PortalClosingPayload payload = new PortalClosingPayload(centerPos, frame.right(), CLOSE_TICKS);
		for (ServerPlayer player : level.players()) {
			if (player.blockPosition().distSqr(centerPos) < NOTIFY_RANGE_SQ) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	/** Dipanggil tiap tick server: menghapus portal yang animasi penutupannya selesai. */
	public static void tick(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}
		for (PendingClose pending : new ArrayList<>(PENDING)) {
			if (pending.level().getGameTime() < pending.endTick()) {
				continue;
			}
			PENDING.remove(pending);
			for (Portal portal : find(pending.level(), pending.center())) {
				if (portal.isAlive()) {
					discard(portal);
				}
			}
			// Jika redstone sudah menyala lagi selama animasi, mulai pengisian energi berikutnya.
			MagnetBlock.recheck(pending.level(), pending.magnet());
		}
	}

	/** Portal bertanda menutup yang tersisa dari sesi sebelumnya (server berhenti saat animasi) dibuang saat dimuat. */
	public static void onEntityLoad(Entity entity, ServerLevel level) {
		if (entity instanceof Portal portal && portal.entityTags().contains(CLOSING_TAG)) {
			level.getServer().execute(() -> {
				if (portal.isAlive()) {
					discard(portal);
				}
			});
		}
	}

	private static void discard(Portal portal) {
		PortalManipulation.removeConnectedPortals(portal, removed -> {
		});
		portal.remove(Entity.RemovalReason.KILLED);
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
			discard(portal);
		}
	}

	private static List<Portal> find(ServerLevel level, BlockPos center) {
		return level.getEntitiesOfClass(Portal.class, new AABB(center).inflate(0.5), p -> p.isAlive() && p.entityTags().contains(TAG));
	}
}
