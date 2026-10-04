package com.backrooms.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/**
 * Percikan listrik kuning-hijau. Lima frame (electric_0..4) bergantian tiap {@link #TICKS_PER_FRAME} tick; frame awal
 * dan putaran (kelipatan 90 derajat) diacak tiap ganti frame supaya terlihat menyambar, bukan berputar halus.
 */
public class ElectricSparkParticle extends SingleQuadParticle {
	/** Jumlah frame di particles/electric_spark.json. */
	private static final int FRAMES = 5;
	private static final int TICKS_PER_FRAME = 2;
	private static final int FULL_BRIGHT = 15728880;

	private final SpriteSet sprites;
	private final int frameOffset;

	private ElectricSparkParticle(ClientLevel level, double x, double y, double z, double xa, double ya, double za, SpriteSet sprites, boolean burst) {
		super(level, x, y, z, sprites.first());
		this.sprites = sprites;
		this.xd = xa;
		this.yd = ya;
		this.zd = za;
		this.gravity = 0.0F;
		this.hasPhysics = false;
		if (burst) {
			// Semburan burst: jauh lebih besar (sesekali sangat besar), melaju lebih jauh, hidup sedikit lebih lama.
			this.friction = 0.90F;
			this.quadSize = this.random.nextFloat() < 0.15F ? 1.1F + this.random.nextFloat() * 0.6F : 0.45F + this.random.nextFloat() * 0.45F;
			this.lifetime = 9 + this.random.nextInt(10);
		} else {
			this.friction = 0.86F;
			this.quadSize = 0.14F + this.random.nextFloat() * 0.16F;
			this.lifetime = 6 + this.random.nextInt(8);
		}
		this.frameOffset = this.random.nextInt(FRAMES);
		this.applyFrame();
	}

	private void applyFrame() {
		int frame = (this.age / TICKS_PER_FRAME + this.frameOffset) % FRAMES;
		this.setSprite(this.sprites.get(frame, FRAMES - 1));
	}

	@Override
	public void tick() {
		super.tick();
		if (this.removed) {
			return;
		}
		if (this.age % TICKS_PER_FRAME == 0) {
			this.roll = this.random.nextInt(4) * (float) (Math.PI / 2.0);
			this.oRoll = this.roll;
		}
		this.applyFrame();
		int remaining = this.lifetime - this.age;
		this.alpha = remaining <= 2 ? Math.max(0.0F, remaining / 3.0F) : 1.0F;
	}

	@Override
	protected Layer getLayer() {
		return Layer.TRANSLUCENT;
	}

	@Override
	protected int getLightCoords(float partialTick) {
		return FULL_BRIGHT;
	}

	public static class Provider implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet sprites;
		private final boolean burst;

		public Provider(SpriteSet sprites, boolean burst) {
			this.sprites = sprites;
			this.burst = burst;
		}

		@Override
		public Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z, double xAux, double yAux, double zAux, RandomSource random) {
			return new ElectricSparkParticle(level, x, y, z, xAux, yAux, zAux, this.sprites, this.burst);
		}
	}
}
