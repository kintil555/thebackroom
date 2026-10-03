package com.backrooms;

import com.backrooms.block.CoverDisplayBlock;
import com.backrooms.block.CoveredBlock;
import com.backrooms.block.LampBlock;
import com.backrooms.block.MagnetBlock;
import com.backrooms.block.PlaceholderPortalBlock;
import com.backrooms.block.SirenBlock;
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
import net.minecraft.world.level.material.PushReaction;

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

	/** Lamp: light level 15 saat menyala (sama dengan Ochre Froglight), kadang berkedip mati-nyala. */
	public static final Block LAMP = registerWithItem(
		"lamp",
		LampBlock::new,
		BlockBehaviour.Properties.of()
			.mapColor(MapColor.SAND)
			.strength(0.3F)
			.sound(SoundType.GLASS)
			.lightLevel(state -> state.getValue(LampBlock.LIT) ? 15 : 0)
			.randomTicks()
	);

	/** Magnet: 13 Magnet membentuk bingkai portal 3x5 (kiri, kanan, atas; bawah blok apa saja). Power 15 membuka portal. */
	public static final Block MAGNET = registerWithItem(
		"magnet",
		MagnetBlock::new,
		BlockBehaviour.Properties.of()
			.mapColor(MapColor.METAL)
			.strength(3.0F, 6.0F)
			.sound(SoundType.METAL)
			.requiresCorrectToolForDrops()
			.noOcclusion()
	);

	/** Siren Alert: berbunyi dan lampunya berputar saat Magnet menghitung mundur. */
	public static final Block SIREN = registerWithItem(
		"siren",
		SirenBlock::new,
		BlockBehaviour.Properties.of()
			.mapColor(MapColor.METAL)
			.strength(2.0F, 4.0F)
			.sound(SoundType.METAL)
			.requiresCorrectToolForDrops()
			.noOcclusion()
			.lightLevel(state -> state.getValue(SirenBlock.ACTIVE) ? 10 : 0)
	);

	/** Placeholder portal (perilaku Nether Portal) untuk testing Magnet; tanpa item. TODO: ganti ke portal Backrooms. */
	public static final Block PLACEHOLDER_PORTAL = registerBlock(
		"placeholder_portal",
		PlaceholderPortalBlock::new,
		BlockBehaviour.Properties.of()
			.noCollision()
			.randomTicks()
			.strength(-1.0F)
			.sound(SoundType.GLASS)
			.lightLevel(state -> 11)
			.pushReaction(PushReaction.BLOCK)
			.noLootTable()
	);

	/** Blok model-saja untuk menggambar lapisan di sisi blok mana pun (tanpa item, tidak ada di dunia). */
	public static final CoverDisplayBlock COVER_DISPLAY = (CoverDisplayBlock) registerBlock(
		"cover_display",
		CoverDisplayBlock::new,
		BlockBehaviour.Properties.of().noCollision().noOcclusion().noLootTable()
	);

	/** Pembungkus blok biasa yang ditempeli carpet; blok asal + sisi carpet disimpan di block entity (tanpa item). */
	public static final Block COVERED_BLOCK = registerBlock(
		"covered_block",
		CoveredBlock::new,
		BlockBehaviour.Properties.of().strength(1.0F, 3.0F).sound(SoundType.WOOL).noLootTable()
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
			output.accept(LAMP);
			output.accept(MAGNET);
			output.accept(SIREN);
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
