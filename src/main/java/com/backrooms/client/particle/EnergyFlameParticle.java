package com.backrooms.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/**
 * Api/aura hijau yang mengalir di tepi bingkai portal. Empat frame (energy_flame_0..3) bergantian sepanjang hidup,
 * partikel mengecil dan memudar menjelang akhir, serta selalu terang penuh (tidak terpengaruh cahaya sekitar).
 */
public class EnergyFlameParticle extends SingleQuadParticle {
	private static final int FRAMES = 4;
	private static final int FULL_BRIGHT = 15728880;

	private final SpriteSet sprites;
	private final float startSize;

	private EnergyFlameParticle(ClientLevel level, double x, double y, double z, double xa, double ya, double za, SpriteSet sprites) {
		super(level, x, y, z, sprites.first());
		this.sprites = sprites;
		this.xd = xa;
		this.yd = ya;
		this.zd = za;
		this.gravity = 0.0F;
		this.friction = 0.94F;
		this.hasPhysics = false;
		this.startSize = 0.28F + this.random.nextFloat() * 0.26F;
		this.quadSize = this.startSize;
		this.lifetime = 12 + this.random.nextInt(10);
		this.setSpriteFromAge(sprites);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.removed) {
			return;
		}
		this.setSpriteFromAge(this.sprites);
		float life = (float) this.age / this.lifetime;
		this.quadSize = this.startSize * (1.0F - 0.55F * life);
		this.alpha = life < 0.7F ? 1.0F : Math.max(0.0F, (1.0F - life) / 0.3F);
		// Nyala sedikit bergoyang ke samping seperti api.
		this.xd += (this.random.nextFloat() - 0.5F) * 0.004D;
		this.zd += (this.random.nextFloat() - 0.5F) * 0.004D;
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

		public Provider(SpriteSet sprites) {
			this.sprites = sprites;
		}

		@Override
		public Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z, double xAux, double yAux, double zAux, RandomSource random) {
			return new EnergyFlameParticle(level, x, y, z, xAux, yAux, zAux, this.sprites);
		}
	}
}
