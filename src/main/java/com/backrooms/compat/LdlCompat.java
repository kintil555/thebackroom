package com.backrooms.compat;

import com.backrooms.client.glow.PortalGlowManager;
import dev.lambdaurora.lambdynlights.api.DynamicLightsContext;
import dev.lambdaurora.lambdynlights.api.DynamicLightsInitializer;
import dev.lambdaurora.lambdynlights.api.behavior.DynamicLightBehavior;
import dev.lambdaurora.lambdynlights.api.behavior.DynamicLightBehaviorManager;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

/**
 * Entrypoint opsional {@code lambdynlights:initializer}: hanya dijalankan LambDynamicLights jika terpasang, jadi
 * class ini (dan API-nya) tidak pernah diload tanpa mod itu. Tiap portal yang sedang bercahaya mendapat satu
 * {@link PortalLight}; levelnya mengikuti bloom lewat {@link PortalGlowManager#lightPoints}.
 */
public final class LdlCompat implements DynamicLightsInitializer {
	/** Jangkauan cahaya (blok) dari tengah portal. */
	private static final int RADIUS = 14;

	private final Map<BlockPos, PortalLight> lights = new HashMap<>();

	@Override
	public void onInitializeDynamicLights(DynamicLightsContext context) {
		DynamicLightBehaviorManager manager = context.dynamicLightBehaviorManager();
		ClientTickEvents.END_CLIENT_TICK.register(client -> this.sync(manager, client.level));
	}

	private void sync(DynamicLightBehaviorManager manager, ClientLevel level) {
		Set<BlockPos> seen = new HashSet<>();
		for (PortalGlowManager.LightPoint point : PortalGlowManager.lightPoints(level)) {
			seen.add(point.center());
			PortalLight light = this.lights.get(point.center());
			if (light == null) {
				light = new PortalLight(point.center());
				this.lights.put(point.center(), light);
				manager.add(light);
			}
			light.setLevel(point.level());
		}
		Iterator<Map.Entry<BlockPos, PortalLight>> iterator = this.lights.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<BlockPos, PortalLight> entry = iterator.next();
			if (!seen.contains(entry.getKey())) {
				entry.getValue().discard();
				manager.remove(entry.getValue());
				iterator.remove();
			}
		}
	}

	/** Cahaya titik di tengah portal; level 0..15 diperbarui tiap tick client. */
	private static final class PortalLight implements DynamicLightBehavior {
		private final double x;
		private final double y;
		private final double z;
		private final BoundingBox box;
		private float level;
		private int lastLevel = -1;
		private boolean removed;

		PortalLight(BlockPos center) {
			this.x = center.getX() + 0.5;
			this.y = center.getY() + 0.5;
			this.z = center.getZ() + 0.5;
			this.box = new BoundingBox(
				center.getX() - RADIUS, center.getY() - RADIUS, center.getZ() - RADIUS,
				center.getX() + RADIUS + 1, center.getY() + RADIUS + 1, center.getZ() + RADIUS + 1);
		}

		void setLevel(float level) {
			this.level = level;
		}

		void discard() {
			this.removed = true;
		}

		@Override
		public double lightAtPos(BlockPos pos, double falloffRatio) {
			double dx = pos.getX() + 0.5 - this.x;
			// Portal tinggi 5 blok: jarak vertikal dikompres agar cahaya terasa memanjang mengikuti portal.
			double dy = (pos.getY() + 0.5 - this.y) * 0.6;
			double dz = pos.getZ() + 0.5 - this.z;
			double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
			return Math.max(0.0, Math.min(15.0, this.level - distance * falloffRatio));
		}

		@Override
		public BoundingBox getBoundingBox() {
			return this.box;
		}

		@Override
		public boolean hasChanged() {
			int current = Math.round(this.level);
			if (current != this.lastLevel) {
				this.lastLevel = current;
				return true;
			}
			return false;
		}

		@Override
		public boolean isRemoved() {
			return this.removed;
		}
	}
}
