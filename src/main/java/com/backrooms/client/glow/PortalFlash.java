package com.backrooms.client.glow;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Efek seperti terkena flashbang: saat burst bloom berakhir (portal terbuka), pemain dalam {@link #RANGE_BLOCKS} dari
 * titik portal mendapat layar putih yang memudar, bayangan sisa (afterimage) di posisi layar tempat bloom tadi terlihat,
 * dan jejak frame sebelumnya: kamera bergerak tetapi gambar lama bertahan dan memudar pelan seperti motion blur.
 *
 * <p>Seluruhnya client-side dan hanya satu efek aktif pada satu waktu (efek baru menggantikan yang lama). Shader
 * {@code portal_glow.fsh} yang menggambarnya; parameter dikirim lewat {@link PortalGlowRenderer}.
 */
final class PortalFlash {
	/** Jarak maksimum dari tengah portal agar pemain terkena. */
	static final double RANGE_BLOCKS = 12.0;
	/** Putih penuh bertahan sebentar, lalu memudar halus. */
	private static final float WHITE_HOLD_TICKS = 6.0f;
	private static final float WHITE_FADE_TICKS = 54.0f;
	/** Bayangan sisa muncul cepat (tertutup putih) dan memudar jauh lebih lama dari putihnya. */
	private static final float GHOST_ATTACK_TICKS = 4.0f;
	private static final float GHOST_TOTAL_TICKS = 140.0f;
	/** Jejak frame sebelumnya: bertahan kuat 5 detik setelah portal menyala, lalu memudar halus. */
	private static final float TRAIL_HOLD_TICKS = 100.0f;
	private static final float TRAIL_FADE_TICKS = 40.0f;
	/** Kecepatan fade keterlihatan portal (per detik) saat kamera berlindung / keluar dari balik blok. */
	private static final float VISIBILITY_RATE = 10.0f;
	/** Jejak tetap penuh sampai jarak ini dari portal (cahaya setinggi itu di jarak 10 blok tetap menyilaukan), lalu memudar. */
	private static final float TRAIL_FULL_DISTANCE = 10.0f;
	private static final float TRAIL_FADE_DISTANCE = 6.0f;

	private static double startTick = -1.0;
	private static float strength;
	/** Keterlihatan portal dari kamera 0..1 (smoothing); 0 = berlindung di balik blok, semua efek layar tidak digambar. */
	private static float visibility;
	private static float viewDistance;
	private static Vec3 target = Vec3.ZERO;
	private static boolean needsCapture;
	private static float ghostX;
	private static float ghostY;
	private static float ghostRadius;

	private PortalFlash() {
	}

	/** Dipanggil saat client melihat portal terbuka. Tidak melakukan apa-apa jika pemain di luar jangkauan. */
	static void onOpened(ClientLevel level, LocalPlayer player, PortalGlowManager.Source source) {
		Vec3 center = Vec3.atCenterOf(source.center);
		double distance = Math.sqrt(player.distanceToSqr(center));
		if (distance > RANGE_BLOCKS) {
			return;
		}
		float falloff = 1.0f - 0.35f * (float) (distance / RANGE_BLOCKS);
		strength = falloff;
		visibility = Mth.clamp(source.visibility, 0.0f, 1.0f);
		viewDistance = (float) distance;
		startTick = level.getGameTime();
		target = center;
		needsCapture = true;
		ghostRadius = 0.0f;
	}

	static void reset() {
		startTick = -1.0;
		needsCapture = false;
		visibility = 0.0f;
	}

	/** Dipanggil tiap frame selagi efek aktif: memperbarui keterlihatan portal dan jarak kamera. Terhalang blok = efek tak tergambar. */
	static void updateView(ClientLevel level, Vec3 eye, float frameSeconds) {
		if (startTick < 0.0) {
			return;
		}
		boolean visible = PortalSight.visible(level, eye, target);
		visibility = Mth.lerp(Math.min(1.0f, frameSeconds * VISIBILITY_RATE), visibility, visible ? 1.0f : 0.0f);
		if (visibility < 0.01f) {
			visibility = 0.0f;
		}
		viewDistance = (float) eye.distanceTo(target);
	}

	static boolean active(double nowTicks) {
		return startTick >= 0.0 && nowTicks - startTick < Math.max(GHOST_TOTAL_TICKS, TRAIL_HOLD_TICKS + TRAIL_FADE_TICKS);
	}

	/** True sekali setelah efek dipicu: renderer lalu memproyeksikan {@link #target()} dan memanggil {@link #setGhost}. */
	static boolean needsCapture() {
		return needsCapture;
	}

	static Vec3 target() {
		return target;
	}

	/** Menyimpan posisi layar tetap (0..1, asal kiri-atas) dan radius (fraksi tinggi layar) bayangan sisa; radius 0 = tanpa bayangan. */
	static void setGhost(float x, float y, float radius) {
		ghostX = x;
		ghostY = y;
		ghostRadius = radius;
		needsCapture = false;
	}

	static float ghostX() {
		return ghostX;
	}

	static float ghostY() {
		return ghostY;
	}

	static float ghostRadius() {
		return ghostRadius;
	}

	/** Lapisan putih 0..1. */
	static float white(double nowTicks) {
		if (!active(nowTicks)) {
			return 0.0f;
		}
		float t = (float) (nowTicks - startTick);
		float fade = PortalGlowFlicker.smooth(WHITE_FADE_TICKS, t - WHITE_HOLD_TICKS);
		return strength * (1.0f - fade) * visibility;
	}

	/** Kekuatan jejak frame sebelumnya 0..1: naik cepat saat portal menyala, penuh selama 5 detik, lalu memudar halus. */
	static float trail(double nowTicks) {
		if (!active(nowTicks)) {
			return 0.0f;
		}
		float t = (float) (nowTicks - startTick);
		float attack = PortalGlowFlicker.smooth(GHOST_ATTACK_TICKS, t);
		float fade = PortalGlowFlicker.smooth(TRAIL_FADE_TICKS, t - TRAIL_HOLD_TICKS);
		float range = 1.0f - PortalGlowFlicker.smooth(TRAIL_FADE_DISTANCE, viewDistance - TRAIL_FULL_DISTANCE);
		return attack * (1.0f - fade) * range * visibility;
	}

	/** Kekuatan bayangan sisa 0..1. */
	static float ghost(double nowTicks) {
		if (!active(nowTicks)) {
			return 0.0f;
		}
		float t = (float) (nowTicks - startTick);
		float attack = PortalGlowFlicker.smooth(GHOST_ATTACK_TICKS, t);
		float fade = PortalGlowFlicker.smooth(GHOST_TOTAL_TICKS, t);
		return strength * attack * (1.0f - fade) * visibility;
	}
}
