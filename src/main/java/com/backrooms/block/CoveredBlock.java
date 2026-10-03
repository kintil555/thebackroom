package com.backrooms.block;

import com.backrooms.ModBlocks;
import com.backrooms.cover.CoverManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jspecify.annotations.Nullable;

/**
 * Blok pembungkus untuk blok biasa yang ditempeli carpet. Blok asal + sisi carpet disimpan di
 * {@link CoveredBlockEntity}; sifat blok asal (kekerasan, drop, pick block, tampilan) diteruskan ke sana.
 * Hanya dibuat untuk blok sederhana (lihat {@link CoverManager#canWrap}); sisanya tetap memakai attachment chunk.
 */
public class CoveredBlock extends Block implements EntityBlock {
	public CoveredBlock(Properties properties) {
		super(properties);
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new CoveredBlockEntity(pos, state);
	}

	/** Blok asal di posisi ini, atau null jika bukan blok pembungkus / datanya belum ada. */
	public static @Nullable BlockState hostOf(BlockGetter level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof CoveredBlockEntity covered ? covered.host() : null;
	}

	@Override
	protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
		BlockState host = hostOf(level, pos);
		return host == null ? super.getDestroyProgress(state, player, level, pos) : host.getDestroyProgress(player, level, pos);
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		BlockState host = hostOf(level, pos);
		return host == null ? ItemStack.EMPTY : host.getCloneItemStack(level, pos, includeData);
	}

	/** Drop = drop blok asal (hanya jika alat benar, seperti vanilla) + satu Carpet per sisi berlapis. */
	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
		List<ItemStack> drops = new ArrayList<>();
		if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof CoveredBlockEntity covered && covered.host() != null) {
			BlockState host = covered.host();
			Entity breaker = params.getOptionalParameter(LootContextParams.THIS_ENTITY);
			ItemInstance tool = params.getOptionalParameter(LootContextParams.TOOL);
			boolean toolOk = !(breaker instanceof Player) || !host.requiresCorrectToolForDrops()
				|| (tool instanceof ItemStack stack && stack.isCorrectToolForDrops(host));
			if (toolOk) {
				drops.addAll(host.getDrops(params));
			}
			for (Direction face : Direction.values()) {
				if ((covered.mask() & CoverManager.bit(face)) != 0) {
					drops.add(new ItemStack(ModBlocks.CARPET));
				}
			}
		}
		return drops;
	}
}
