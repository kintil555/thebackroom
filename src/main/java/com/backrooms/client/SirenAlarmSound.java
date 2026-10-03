package com.backrooms.client;

import com.backrooms.ModSounds;
import com.backrooms.block.SirenBlock;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Alarm Siren Alert: satu suara loop per sirine aktif (client-side). Di awal volume fade in dan pitch naik dari
 * -7 semitone ke normal dengan kurva quartic; saat sirine mati volume fade out lalu berhenti.
 */
public final class SirenAlarmSound extends AbstractTickableSoundInstance {
	/** Pitch awal: -7 semitone = 2^(-7/12). */
	private static final float START_PITCH = (float) Math.pow(2.0, -7.0 / 12.0);
	/** Lama pitch naik dari START_PITCH ke 1.0 (tick). */
	private static final float PITCH_RISE_TICKS = 50.0f;
	/** Eksponen kurva pitch: 4 = lama di nada rendah lalu menanjak cepat di akhir. */
	private static final float PITCH_CURVE = 4.0f;
	private static final float FADE_IN_TICKS = 40.0f;
	private static final float FADE_OUT_TICKS = 30.0f;

	private static final Map<BlockPos, SirenAlarmSound> PLAYING = new HashMap<>();

	private final ClientLevel level;
	private final BlockPos pos;
	private int age;
	private int fadeOutAge = -1;

	private SirenAlarmSound(ClientLevel level, BlockPos pos) {
		super(ModSounds.SIREN_ALARM, SoundSource.BLOCKS, RandomSource.create());
		this.level = level;
		this.pos = pos.immutable();
		this.x = pos.getX() + 0.5;
		this.y = pos.getY() + 0.5;
		this.z = pos.getZ() + 0.5;
		this.looping = true;
		this.volume = 0.0f;
		this.pitch = START_PITCH;
	}

	/** Dipanggil tiap tick client oleh block entity sirine; memulai alarm jika sirine aktif dan belum berbunyi. */
	public static void tickBlock(ClientLevel level, BlockPos pos, BlockState state) {
		if (!state.getValue(SirenBlock.ACTIVE)) {
			return;
		}
		SirenAlarmSound existing = PLAYING.get(pos);
		if (existing != null && existing.level == level && !existing.isStopped() && existing.fadeOutAge < 0) {
			return;
		}
		if (existing != null && existing.level == level && !existing.isStopped()) {
			// Sirine menyala lagi saat alarm lama masih fade out: lepas yang lama, mulai alarm baru dari awal.
			existing.stop();
		}
		SirenAlarmSound sound = new SirenAlarmSound(level, pos);
		PLAYING.put(sound.pos, sound);
		Minecraft.getInstance().getSoundManager().play(sound);
	}

	/** Mulai dari volume 0 harus diizinkan, kalau tidak engine melewati suara ini. */
	@Override
	public boolean canStartSilent() {
		return true;
	}

	@Override
	public void tick() {
		if (Minecraft.getInstance().level != this.level) {
			this.end();
			return;
		}
		BlockState state = this.level.getBlockState(this.pos);
		if (!(state.getBlock() instanceof SirenBlock)) {
			this.end();
			return;
		}
		if (this.fadeOutAge < 0 && !state.getValue(SirenBlock.ACTIVE)) {
			this.fadeOutAge = 0;
		}

		this.age++;
		float fadeIn = smooth(this.age / FADE_IN_TICKS);
		float fadeOut = 1.0f;
		if (this.fadeOutAge >= 0) {
			this.fadeOutAge++;
			fadeOut = 1.0f - smooth(this.fadeOutAge / FADE_OUT_TICKS);
			if (fadeOut <= 0.0f) {
				this.end();
				return;
			}
		}
		this.volume = fadeIn * fadeOut;

		float rise = (float) Math.pow(Mth.clamp(this.age / PITCH_RISE_TICKS, 0.0f, 1.0f), PITCH_CURVE);
		this.pitch = Mth.lerp(rise, START_PITCH, 1.0f);
	}

	private void end() {
		this.stop();
		PLAYING.remove(this.pos, this);
	}

	/** Smoothstep 0..1 (kemiringan nol di kedua ujung). */
	private static float smooth(float x) {
		float t = Mth.clamp(x, 0.0f, 1.0f);
		return t * t * (3.0f - 2.0f * t);
	}
}
