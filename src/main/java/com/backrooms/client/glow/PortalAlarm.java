package com.backrooms.client.glow;

import com.backrooms.ModSounds;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Alarm portal: setelah portal terbuka, {@code portal_alarm} diputar sampai habis, jeda {@link #GAP_SECONDS} detik,
 * lalu diputar lagi, terus berulang selama portal masih terbuka (lihat {@link OpenPortals}). Berdiri sendiri (tidak bergantung pada
 * {@code PortalGlowManager.Source}, yang dibuang beberapa detik setelah portal terbuka).
 */
final class PortalAlarm {
	private static final int TICKS_PER_SECOND = 20;
	/** Panjang file portal_alarm.ogg (2,2149 detik) dibulatkan ke atas ke tick. */
	private static final int SOUND_TICKS = (int) Math.ceil(2.215 * TICKS_PER_SECOND);
	/** Jeda antara akhir suara dan awal pengulangan berikutnya. */
	private static final double GAP_SECONDS = 2.0;
	private static final int PERIOD_TICKS = SOUND_TICKS + (int) Math.round(GAP_SECONDS * TICKS_PER_SECOND);

	private static final Map<BlockPos, Alarm> ALARMS = new HashMap<>();
	private static @Nullable ClientLevel owner;

	private PortalAlarm() {
	}

	private static final class Alarm {
		final Vec3 position;
		long nextPlayTick;
		@Nullable SimpleSoundInstance current;

		Alarm(Vec3 position, long nextPlayTick) {
			this.position = position;
			this.nextPlayTick = nextPlayTick;
		}
	}

	/** Dipanggil saat client melihat portal di {@code center} terbuka; suara pertama diputar langsung. */
	static void start(ClientLevel level, BlockPos center, long now) {
		if (owner != level) {
			stopAll();
			owner = level;
		}
		ALARMS.putIfAbsent(center.immutable(), new Alarm(Vec3.atCenterOf(center), now));
	}

	/** Dipanggil tiap tick client; {@code level} null saat tidak di dunia. */
	static void tick(@Nullable ClientLevel level) {
		if (level == null || level != owner) {
			stopAll();
			owner = level;
			return;
		}
		if (ALARMS.isEmpty()) {
			return;
		}
		SoundManager soundManager = Minecraft.getInstance().getSoundManager();
		long now = level.getGameTime();
		Iterator<Map.Entry<BlockPos, Alarm>> iterator = ALARMS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<BlockPos, Alarm> entry = iterator.next();
			Alarm alarm = entry.getValue();
			if (!OpenPortals.isOpen(entry.getKey())) {
				if (alarm.current != null) {
					soundManager.stop(alarm.current);
				}
				iterator.remove();
				continue;
			}
			if (now >= alarm.nextPlayTick) {
				alarm.current = new SimpleSoundInstance(ModSounds.PORTAL_ALARM, SoundSource.BLOCKS, 1.0f, 1.0f,
					RandomSource.create(), alarm.position.x, alarm.position.y, alarm.position.z);
				soundManager.play(alarm.current);
				alarm.nextPlayTick = now + PERIOD_TICKS;
			}
		}
	}

	private static void stopAll() {
		if (ALARMS.isEmpty()) {
			return;
		}
		SoundManager soundManager = Minecraft.getInstance().getSoundManager();
		for (Alarm alarm : ALARMS.values()) {
			if (alarm.current != null) {
				soundManager.stop(alarm.current);
			}
		}
		ALARMS.clear();
	}
}
