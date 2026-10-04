package com.backrooms.client.light;

import com.backrooms.block.SirenBlock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Daftar cahaya berwarna di sisi client. Cahaya vanilla hanya satu kanal (putih), jadi warna ditambahkan oleh post
 * effect {@link ColoredLightRenderer}: tiap piksel direkonstruksi posisinya dari depth buffer lalu diberi tint sesuai
 * jarak ke sumber. Tidak menyentuh light engine, mesh chunk, maupun Sodium, dan tidak mengubah gameplay.
 */
public final class ColoredLights {
	/** Warna sirine menyala (merah). */
	private static final int SIREN_RGB = 0xFF1408;
	private static final float SIREN_RADIUS_BLOCKS = 12.0f;
	private static final float FADE_IN_TICKS = 10.0f;
	/** Lama redup setelah sirine mati; sumber dibuang sesudah {@link #REMOVE_AFTER_TICKS}. */
	private static final float FADE_OUT_TICKS = 20.0f;
	private static final long REMOVE_AFTER_TICKS = 24L;
	/** Putaran lampu sirine = 24 tick (SirenRenderer); cahaya berdenyut dengan periode yang sama. */
	private static final double PULSE_PERIOD_TICKS = 24.0;

	private static final Map<BlockPos, Entry> SIRENS = new HashMap<>();

	private ColoredLights() {
	}

	private static final class Entry {
		final Vec3 position;
		final long firstSeen;
		long lastSeen;

		Entry(Vec3 position, long firstSeen) {
			this.position = position;
			this.firstSeen = firstSeen;
			this.lastSeen = firstSeen;
		}
	}

	/** Satu cahaya siap gambar: posisi dunia, warna 0..1, radius (blok), dan intensitas 0..1. */
	public record Sample(Vec3 position, float red, float green, float blue, float radius, float intensity) {
	}

	/** Dipanggil tiap tick client oleh ticker block entity sirine; hanya sirine aktif yang tercatat. */
	public static void markSiren(ClientLevel level, BlockPos pos, BlockState state) {
		if (!state.getValue(SirenBlock.ACTIVE)) {
			return;
		}
		long now = level.getGameTime();
		Entry entry = SIRENS.get(pos);
		if (entry == null || now - entry.lastSeen > REMOVE_AFTER_TICKS) {
			Direction facing = state.getValue(SirenBlock.FACING);
			Vec3 position = Vec3.atCenterOf(pos).add(facing.getStepX() * 0.15, facing.getStepY() * 0.15, facing.getStepZ() * 0.15);
			entry = new Entry(position, now);
			SIRENS.put(pos.immutable(), entry);
		}
		entry.lastSeen = now;
	}

	public static void clear() {
		SIRENS.clear();
	}

	/** Cahaya yang aktif saat ini ({@code nowTicks} = game time + partial tick). Membuang sumber yang sudah padam. */
	static List<Sample> samples(double nowTicks) {
		if (SIRENS.isEmpty()) {
			return List.of();
		}
		List<Sample> samples = new ArrayList<>(SIRENS.size());
		Iterator<Entry> iterator = SIRENS.values().iterator();
		float red = ((SIREN_RGB >> 16) & 0xFF) / 255.0f;
		float green = ((SIREN_RGB >> 8) & 0xFF) / 255.0f;
		float blue = (SIREN_RGB & 0xFF) / 255.0f;
		while (iterator.hasNext()) {
			Entry entry = iterator.next();
			if (nowTicks - entry.lastSeen > REMOVE_AFTER_TICKS) {
				iterator.remove();
				continue;
			}
			float fadeIn = Mth.clamp((float) (nowTicks - entry.firstSeen) / FADE_IN_TICKS, 0.0f, 1.0f);
			float since = (float) (nowTicks - entry.lastSeen);
			float fadeOut = 1.0f - Mth.clamp((since - 1.5f) / FADE_OUT_TICKS, 0.0f, 1.0f);
			float pulse = 0.6f + 0.4f * (0.5f + 0.5f * (float) Math.cos(nowTicks * (Math.PI * 2.0 / PULSE_PERIOD_TICKS)));
			samples.add(new Sample(entry.position, red, green, blue, SIREN_RADIUS_BLOCKS, fadeIn * fadeOut * pulse));
		}
		return samples;
	}
}
