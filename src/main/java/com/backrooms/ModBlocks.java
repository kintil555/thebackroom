package com.backrooms;

import com.backrooms.block.CarpetBlock;
import com.backrooms.block.WallpaperBlock;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public final class ModBlocks {
	public static final Block SCREW_PILES = register(
		"screw_piles",
		Block::new,
		BlockBehaviour.Properties.of()
			.mapColor(MapColor.METAL)
			.strength(3.0F, 6.0F)
			.sound(SoundType.METAL)
			.requiresCorrectToolForDrops()
			.noOcclusion() // tekstur punya bagian transparan
	);

	public static final Block WALLPAPER = register(
		"wallpaper",
		WallpaperBlock::new,
		BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_YELLOW)
			.strength(0.1F)
			.sound(SoundType.WOOL)
			.pushReaction(PushReaction.DESTROY)
	);

	public static final Block CARPET = register(
		"carpet",
		CarpetBlock::new,
		BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_YELLOW)
			.strength(0.1F)
			.sound(SoundType.WOOL)
			.pushReaction(PushReaction.DESTROY)
	);

	private ModBlocks() {
	}

	/** Memanggil method ini memaksa class diload sehingga semua blok terdaftar. */
	public static void init() {
		ResourceKey<CreativeModeTab> buildingBlocks =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("building_blocks"));
		CreativeModeTabEvents.modifyOutputEvent(buildingBlocks).register(output -> {
			output.accept(SCREW_PILES);
			output.accept(WALLPAPER);
			output.accept(CARPET);
		});
	}

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		Identifier id = Identifier.fromNamespaceAndPath(BackroomsMod.MOD_ID, name);

		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
		Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey, factory.apply(properties.setId(blockKey)));

		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}
}
