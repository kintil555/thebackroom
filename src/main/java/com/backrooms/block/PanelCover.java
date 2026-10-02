package com.backrooms.block;

import net.minecraft.util.StringRepresentable;

/** Lapisan tipis yang menempel langsung pada satu sisi Screw Piles. */
public enum PanelCover implements StringRepresentable {
	NONE("none"),
	WALLPAPER("wallpaper"),
	CARPET("carpet");

	private final String name;

	PanelCover(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}
}
