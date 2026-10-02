package com.backrooms.cover;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

/** Nama item hasil clone (middle click) blok berlapis: "Screw Piles Attached Wallpaper", "Stone Attached Carpet", dst. */
public final class CoverNames {
	private CoverNames() {
	}

	public static Component attachedName(Block host, boolean wallpaper, boolean carpet) {
		String key;
		if (wallpaper && carpet) {
			key = "backrooms.name.attached_wallpaper_carpet";
		} else if (wallpaper) {
			key = "backrooms.name.attached_wallpaper";
		} else {
			key = "backrooms.name.attached_carpet";
		}
		return Component.translatable(key, host.getName());
	}
}
