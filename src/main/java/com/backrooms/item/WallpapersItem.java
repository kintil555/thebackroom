package com.backrooms.item;

import com.backrooms.ModBlocks;
import com.backrooms.block.PanelCover;
import com.backrooms.block.ScrewPilesBlock;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.core.BlockPos;

/**
 * Wallpapers (creative only): meletakkan Screw Piles yang keenam sisinya sudah berlapis Wallpaper.
 * Menghancurkan satu sisi hanya melepas wallpaper sisi itu dan menampakkan Screw Piles di baliknya.
 */
public class WallpapersItem extends Item {
	public WallpapersItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		BlockPlaceContext placeContext = new BlockPlaceContext(context);
		if (!placeContext.canPlace()) {
			return InteractionResult.FAIL;
		}

		Level level = placeContext.getLevel();
		BlockPos pos = placeContext.getClickedPos();
		Player player = placeContext.getPlayer();
		BlockState state = ScrewPilesBlock.withAllCovers(ModBlocks.SCREW_PILES.defaultBlockState(), PanelCover.WALLPAPER);

		if (!level.isUnobstructed(state, pos, CollisionContext.placementContext(player))) {
			return InteractionResult.FAIL;
		}
		if (!level.setBlock(pos, state, Block.UPDATE_ALL_IMMEDIATE)) {
			return InteractionResult.FAIL;
		}

		SoundType sound = state.getSoundType();
		level.playSound(player, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
		level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(player, state));
		context.getItemInHand().consume(1, player);
		return InteractionResult.SUCCESS;
	}
}
