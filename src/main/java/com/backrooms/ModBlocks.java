package com.backrooms;

import com.backrooms.block.CoverDisplayBlock;
import com.backrooms.block.ScrewPilesBlock;
import com.backrooms.item.CarpetItem;
import com.backrooms.item.WallpaperItem;
import com.backrooms.item.WallpapersItem;
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

public final class ModBlocks {
	public static final Block SCREW_PILES = registerWithItem(
		"screw_piles",
		ScrewPilesBlock::new,
		BlockBehaviour.Properties.of()
			.mapColor(MapColor.METAL)
			.strength(3.0F, 6.0F)
			.sound(SoundType.METAL)
			.requiresCorrectToolForDrops()
			.noOcclusion() // tekstur punya bagian transparan
	);

	/** Ceiling: blok langit-langit penuh, bisa dilapisi Carpet seperti blok penuh lainnya. */
	public static final Block CEILING = registerWithItem(
		"ceiling",
		Block::new,
		BlockBehaviour.Properties.of()
			.mapColor(MapColor.QUARTZ)
			.strength(1.5F, 3.0F)
			.sound(SoundType.STONE)
	);

	/** Blok model-saja untuk menggambar lapisan di sisi blok mana pun (tanpa item, tidak ada di dunia). */
	public static final CoverDisplayBlock COVER_DISPLAY = (CoverDisplayBlock) registerBlock(
		"cover_display",
		CoverDisplayBlock::new,
		BlockBehaviour.Properties.of().noCollision().noOcclusion().noLootTable()
	);

	/** Wallpaper dan Carpet hanya item: lapisannya disimpan di Screw Piles (blockstate) atau chunk (attachment). */
	public static final Item WALLPAPER = registerItem("wallpaper", WallpaperItem::new);
	public static final Item CARPET = registerItem("carpet", CarpetItem::new);

	/** Wallpapers: hanya lewat creative. Screw Piles dengan keenam sisinya berlapis Wallpaper. */
	public static final Item WALLPAPERS = registerItem("wallpapers", WallpapersItem::new);

	private ModBlocks() {
	}

	/** Memanggil method ini memaksa class diload sehingga semua blok dan item terdaftar. */
	public static void init() {
		ResourceKey<CreativeModeTab> buildingBlocks =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("building_blocks"));
		CreativeModeTabEvents.modifyOutputEvent(buildingBlocks).register(output -> {
			output.accept(SCREW_PILES);
			output.accept(CEILING);
			output.accept(WALLPAPER);
			output.accept(WALLPAPERS);
			output.accept(CARPET);
		});
	}

	private static Block registerBlock(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		Identifier id = Identifier.fromNamespaceAndPath(BackroomsMod.MOD_ID, name);
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
		return Registry.register(BuiltInRegistries.BLOCK, blockKey, factory.apply(properties.setId(blockKey)));
	}

	private static Block registerWithItem(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		Block block = registerBlock(name, factory, properties);
		Identifier id = Identifier.fromNamespaceAndPath(BackroomsMod.MOD_ID, name);
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}

	private static Item registerItem(String name, Function<Item.Properties, Item> factory) {
		Identifier id = Identifier.fromNamespaceAndPath(BackroomsMod.MOD_ID, name);
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
		return Registry.register(BuiltInRegistries.ITEM, itemKey, factory.apply(new Item.Properties().setId(itemKey).useItemDescriptionPrefix()));
	}
}
