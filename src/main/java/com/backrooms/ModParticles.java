package com.backrooms;

import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/** Partikel mod. Perilaku client ada di {@code ElectricSparkParticle}; tekstur di {@code particles/electric_spark.json}. */
public final class ModParticles {
	public static final SimpleParticleType ELECTRIC_SPARK = new SimpleParticleType(false) {
	};

	private ModParticles() {
	}

	public static void init() {
		Registry.register(BuiltInRegistries.PARTICLE_TYPE, Identifier.fromNamespaceAndPath(BackroomsMod.MOD_ID, "electric_spark"), ELECTRIC_SPARK);
	}
}
