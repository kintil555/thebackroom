package com.backrooms;

import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/** Partikel mod. Perilaku client ada di {@code ElectricSparkParticle}; tekstur di {@code particles/electric_spark.json}. */
public final class ModParticles {
	public static final SimpleParticleType ELECTRIC_SPARK = new SimpleParticleType(false) {
	};

	/** Versi besar dan kuat untuk semburan saat bloom burst; tekstur sama, ukuran dan jangkauan lebih besar. */
	public static final SimpleParticleType ELECTRIC_SPARK_BURST = new SimpleParticleType(false) {
	};

	private ModParticles() {
	}

	public static void init() {
		Registry.register(BuiltInRegistries.PARTICLE_TYPE, Identifier.fromNamespaceAndPath(BackroomsMod.MOD_ID, "electric_spark"), ELECTRIC_SPARK);
		Registry.register(BuiltInRegistries.PARTICLE_TYPE, Identifier.fromNamespaceAndPath(BackroomsMod.MOD_ID, "electric_spark_burst"), ELECTRIC_SPARK_BURST);
	}
}
