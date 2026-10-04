package com.backrooms;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public final class ModSounds {
	public static final SoundEvent LAMP_FLICKER = register("lamp_flicker");
	public static final SoundEvent UNTITLED = register("untitled");
	public static final SoundEvent SIREN_ALARM = register("siren_alarm");
	public static final SoundEvent PORTAL_RISING = register("portal_rising");
	public static final SoundEvent PORTAL_LOOP = register("portal_loop");
	public static final SoundEvent PORTAL_BURST_ENERGY = register("portal_burst_energy");
	public static final SoundEvent PORTAL_AFTER_SPAWN = register("portal_after_spawn");
	public static final SoundEvent PORTAL_ALARM = register("portal_alarm");

	private ModSounds() {
	}

	/** Memanggil method ini memaksa class diload sehingga sound event terdaftar. */
	public static void init() {
	}

	private static SoundEvent register(String name) {
		Identifier id = Identifier.fromNamespaceAndPath(BackroomsMod.MOD_ID, name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
