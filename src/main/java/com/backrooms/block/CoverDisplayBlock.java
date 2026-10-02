package com.backrooms.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Blok "model saja": tidak pernah ada di dunia dan tidak punya item.
 * Dipakai client untuk menggambar lapisan di sisi blok mana pun dan untuk efek crack satu sisi.
 */
public class CoverDisplayBlock extends Block {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
	public static final EnumProperty<PanelCover> COVER = EnumProperty.create("cover", PanelCover.class, PanelCover.WALLPAPER, PanelCover.CARPET);

	public CoverDisplayBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(COVER, PanelCover.CARPET));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, COVER);
	}

	public BlockState stateFor(Direction face, PanelCover cover) {
		return this.defaultBlockState().setValue(FACING, face).setValue(COVER, cover);
	}
}
