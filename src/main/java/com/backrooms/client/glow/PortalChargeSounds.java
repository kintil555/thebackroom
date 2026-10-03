package com.backrooms.client.glow;

import com.backrooms.ModSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Urutan suara satu portal yang sedang mengisi energi (waktu dihitung dalam tick sejak glow mulai):
 * <ul>
 *   <li>{@code rising} mulai 2 detik setelah pengisian dimulai dan diputar sampai habis.</li>
 *   <li>{@code burst_energy} mulai {@link #BURST_AT_SECONDS} detik setelah pengisian dimulai, tepat saat bloom melebar
 *       mendadak (kurva riseLevel di {@link PortalGlowRenderer}); dilapis beberapa pitch agar keras dan kasar.</li>
 *   <li>{@code loop}: 3 suara berbeda pitch, mulai bersama rising, berulang sampai burst habis, lalu fade out.</li>
 *   <li>{@code after_portal_spawn} diputar {@link #AFTER_DELAY_SECONDS} detik setelah portal terbuka (bukan saat terbuka).</li>
 * </ul>
 */
final class PortalChargeSounds {
	private static final int TICKS_PER_SECOND = 20;
	private static final double RISING_SECONDS = 5.08;
	private static final double BURST_SECONDS = 2.0;
	/** Burst mulai di sini (detik sejak pengisian dimulai); portal terbuka di 10 detik, burst selesai 1 detik sesudahnya. */
	private static final double BURST_AT_SECONDS = 9.0;
	/** Jeda after_portal_spawn setelah portal terbuka. */
	private static final double AFTER_DELAY_SECONDS = 2.0;

	private static final int RISING_START = ticks(2.0);
	private static final int RISING_END = RISING_START + ticks(RISING_SECONDS);
	private static final int BURST_START = ticks(BURST_AT_SECONDS);
	private static final int BURST_END = BURST_START + ticks(BURST_SECONDS);
	private static final int AFTER_DELAY = ticks(AFTER_DELAY_SECONDS);

	/**
	 * Lapisan burst {pitch, volume}. Volume suara Minecraft dibatasi 1.0 oleh engine, jadi keras dan "kotor" didapat
	 * dengan menumpuk salinan yang sedikit tidak selaras (detune) ditambah satu lapisan sub-bass.
	 */
	private static final float[][] BURST_LAYERS = {{1.0f, 1.0f}, {0.93f, 1.0f}, {1.07f, 0.9f}, {0.5f, 0.8f}};

	/** Loop mulai bersama rising dan berhenti saat burst habis. */
	private static final int LOOP_START = RISING_START;
	private static final int LOOP_END = BURST_END;
	private static final int LOOP_FADE_IN_TICKS = ticks(0.4);
	private static final int LOOP_FADE_OUT_TICKS = ticks(1.5);
	private static final float[] LOOP_PITCHES = {0.8f, 1.0f, 1.25f};
	private static final float LOOP_VOICE_VOLUME = 0.6f;

	private final Vec3 position;
	private final List<LoopVoice> loopVoices = new ArrayList<>();
	private final List<SoundInstance> oneShots = new ArrayList<>();
	private boolean risingPlayed;
	private boolean burstPlayed;
	private boolean afterPlayed;
	private boolean loopStarted;

	PortalChargeSounds(Vec3 position) {
		this.position = position;
	}

	private static int ticks(double seconds) {
		return (int) Math.round(seconds * TICKS_PER_SECOND);
	}

	/**
	 * Dipanggil tiap tick client.
	 *
	 * @param elapsedTicks   tick sejak pengisian dimulai
	 * @param sinceOpenTicks tick sejak portal terbuka, atau -1 selama portal belum terbuka
	 */
	void tick(long elapsedTicks, long sinceOpenTicks) {
		SoundManager soundManager = Minecraft.getInstance().getSoundManager();
		if (!this.risingPlayed && elapsedTicks >= RISING_START) {
			this.risingPlayed = true;
			if (elapsedTicks < RISING_END) {
				this.playOneShot(soundManager, ModSounds.PORTAL_RISING, 1.0f, 1.0f);
			}
		}
		if (!this.burstPlayed && elapsedTicks >= BURST_START) {
			this.burstPlayed = true;
			if (elapsedTicks < BURST_END) {
				for (float[] layer : BURST_LAYERS) {
					this.playOneShot(soundManager, ModSounds.PORTAL_BURST_ENERGY, layer[1], layer[0]);
				}
			}
		}
		if (!this.afterPlayed && sinceOpenTicks >= AFTER_DELAY) {
			this.afterPlayed = true;
			this.playOneShot(soundManager, ModSounds.PORTAL_AFTER_SPAWN, 1.0f, 1.0f);
		}
		this.tickLoop(soundManager, elapsedTicks);
	}

	private void tickLoop(SoundManager soundManager, long elapsedTicks) {
		if (!this.loopStarted && elapsedTicks >= LOOP_START && elapsedTicks < LOOP_END) {
			this.loopStarted = true;
			for (float pitch : LOOP_PITCHES) {
				LoopVoice voice = new LoopVoice(this.position, pitch, LOOP_VOICE_VOLUME);
				this.loopVoices.add(voice);
				soundManager.play(voice);
			}
		}
		if (this.loopVoices.isEmpty()) {
			return;
		}
		if (elapsedTicks >= LOOP_END) {
			this.finishLoop();
			return;
		}
		float fadeIn = Mth.clamp((float) (elapsedTicks - LOOP_START) / LOOP_FADE_IN_TICKS, 0.0f, 1.0f);
		float fadeOut = Mth.clamp((float) (LOOP_END - elapsedTicks) / LOOP_FADE_OUT_TICKS, 0.0f, 1.0f);
		float level = PortalGlowFlicker.smooth(1.0f, Math.min(fadeIn, fadeOut));
		for (LoopVoice voice : this.loopVoices) {
			voice.setLevel(level);
		}
	}

	private void playOneShot(SoundManager soundManager, SoundEvent event, float volume, float pitch) {
		SimpleSoundInstance instance = new SimpleSoundInstance(
			event, SoundSource.BLOCKS, volume, pitch, RandomSource.create(), this.position.x, this.position.y, this.position.z);
		this.oneShots.add(instance);
		soundManager.play(instance);
	}

	private void finishLoop() {
		for (LoopVoice voice : this.loopVoices) {
			voice.finish();
		}
		this.loopVoices.clear();
	}

	/** Hentikan semuanya segera (bingkai rusak / pindah dunia). */
	void stop() {
		SoundManager soundManager = Minecraft.getInstance().getSoundManager();
		this.finishLoop();
		for (SoundInstance instance : this.oneShots) {
			soundManager.stop(instance);
		}
		this.oneShots.clear();
	}

	/** Satu suara loop: volume diatur dari luar (fade), berhenti saat {@link #finish()}. */
	private static final class LoopVoice extends AbstractTickableSoundInstance {
		private final float baseVolume;
		private boolean finished;

		LoopVoice(Vec3 position, float pitch, float baseVolume) {
			super(ModSounds.PORTAL_LOOP, SoundSource.BLOCKS, RandomSource.create());
			this.x = position.x;
			this.y = position.y;
			this.z = position.z;
			this.pitch = pitch;
			this.baseVolume = baseVolume;
			this.looping = true;
			this.volume = 0.0f;
		}

		void setLevel(float level) {
			this.volume = this.baseVolume * level;
		}

		void finish() {
			this.finished = true;
		}

		/** Mulai dari volume 0 (fade in) harus diizinkan, kalau tidak engine melewati suara ini. */
		@Override
		public boolean canStartSilent() {
			return true;
		}

		@Override
		public void tick() {
			if (this.finished) {
				this.stop();
			}
		}
	}
}
