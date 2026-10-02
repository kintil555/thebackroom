package com.backrooms.mixin;

import com.backrooms.ModBlocks;
import com.backrooms.block.PanelCover;
import com.backrooms.cover.CoverManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {
	/**
	 * Berlari di atas sisi atas yang berlapis: partikel memakai tekstur Carpet/Wallpaper, bukan blok di bawahnya.
	 * Harus partikel BLOCK (bukan ITEM): ItemParticle menambahkan kecepatan mentah sehingga debu melesat ke atas,
	 * sedangkan partikel blok menormalkannya seperti vanilla.
	 */
	@Inject(method = "spawnSprintParticle", at = @At("HEAD"), cancellable = true)
	private void backrooms$coverSprintParticle(CallbackInfo ci) {
		Entity self = (Entity) (Object) this;
		BlockPos pos = self.getOnPosLegacy();
		BlockState state = self.level().getBlockState(pos);
		PanelCover cover = CoverManager.coverAt(self.level(), pos, state, Direction.UP);
		if (cover == PanelCover.NONE) {
			return;
		}

		Vec3 movement = self.getDeltaMovement();
		BlockPos entityPosition = self.blockPosition();
		double x = self.getX() + (self.getRandom().nextDouble() - 0.5) * self.getBbWidth();
		double z = self.getZ() + (self.getRandom().nextDouble() - 0.5) * self.getBbWidth();
		if (entityPosition.getX() != pos.getX()) {
			x = Mth.clamp(x, pos.getX(), pos.getX() + 1.0);
		}
		if (entityPosition.getZ() != pos.getZ()) {
			z = Mth.clamp(z, pos.getZ(), pos.getZ() + 1.0);
		}
		self.level().addParticle(
			new BlockParticleOption(ParticleTypes.BLOCK, ModBlocks.COVER_DISPLAY.stateFor(Direction.UP, cover)),
			x, self.getY() + 0.1, z, movement.x * -4.0, 1.5, movement.z * -4.0
		);
		ci.cancel();
	}
}
