package com.backrooms;

import com.backrooms.block.CoveredBlockEntity;
import com.backrooms.block.SirenBlockEntity;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
	public static final BlockEntityType<CoveredBlockEntity> COVERED_BLOCK = register(
		"covered_block", new BlockEntityType<>(CoveredBlockEntity::new, Set.of(ModBlocks.COVERED_BLOCK))
	);

	public static final BlockEntityType<SirenBlockEntity> SIREN = register(
		"siren", new BlockEntityType<>(SirenBlockEntity::new, Set.of(ModBlocks.SIREN))
	);

	private ModBlockEntities() {
	}

	/** Memanggil method ini memaksa class diload sehingga tipe block entity terdaftar (setelah ModBlocks). */
	public static void init() {
	}

	private static <T extends BlockEntityType<?>> T register(String name, T type) {
		Identifier id = Identifier.fromNamespaceAndPath(BackroomsMod.MOD_ID, name);
		return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, id), type);
	}
}
