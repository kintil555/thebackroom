package com.backrooms.client;

import com.backrooms.BackroomsMod;
import com.backrooms.cover.CoverNames;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Menampilkan nama blok yang dibidik di bawah crosshair, khusus untuk blok yang berlapis Wallpaper/Carpet. */
public final class CoverHud {
	private static final Identifier ID = Identifier.fromNamespaceAndPath(BackroomsMod.MOD_ID, "cover_name");
	private static final int WHITE = 0xFFFFFFFF;

	private CoverHud() {
	}

	public static void init() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, ID, CoverHud::render);
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || !(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
			return;
		}
		BlockPos pos = hit.getBlockPos();
		Component name = CoverNames.describe(minecraft.level, pos, minecraft.level.getBlockState(pos));
		if (name != null) {
			graphics.centeredText(minecraft.font, name, graphics.guiWidth() / 2, graphics.guiHeight() / 2 + 14, WHITE);
		}
	}
}
