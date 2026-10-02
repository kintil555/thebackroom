package com.backrooms.block;

import com.backrooms.ModBlocks;
import com.backrooms.cover.CoverAim;
import com.backrooms.cover.CoverEffects;
import com.backrooms.cover.CoverManager;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Screw Piles: tiap sisinya bisa dilapisi Wallpaper atau Carpet.
 * Lapisan disimpan di blockstate Screw Piles itu sendiri (bukan blok terpisah di luarnya),
 * jadi blok lain di sebelahnya tidak bisa menghapus lapisan tersebut.
 */
public class ScrewPilesBlock extends Block {
	/** Satu property per sisi: north, east, south, west, up, down. */
	private static final Map<Direction, EnumProperty<PanelCover>> COVERS = new EnumMap<>(Direction.class);

	static {
		for (Direction direction : Direction.values()) {
			COVERS.put(direction, EnumProperty.create(direction.getSerializedName(), PanelCover.class));
		}
	}

	public static final int WALLPAPER_BREAK_SECONDS = 4;
	private static final float WALLPAPER_PROGRESS_PER_TICK = 1.0F / (WALLPAPER_BREAK_SECONDS * 20);

	public ScrewPilesBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		for (EnumProperty<PanelCover> property : COVERS.values()) {
			builder.add(property);
		}
	}

	public static EnumProperty<PanelCover> coverProperty(Direction face) {
		return COVERS.get(face);
	}

	public static PanelCover getCover(BlockState state, Direction face) {
		return state.getValue(COVERS.get(face));
	}

	public static BlockState withCover(BlockState state, Direction face, PanelCover cover) {
		return state.setValue(COVERS.get(face), cover);
	}

	/** State dengan keenam sisi berlapis cover yang sama (dipakai item Wallpapers). */
	public static BlockState withAllCovers(BlockState state, PanelCover cover) {
		BlockState result = state;
		for (Direction direction : Direction.values()) {
			result = withCover(result, direction, cover);
		}
		return result;
	}

	/**
	 * Blok ini noOcclusion (tekstur punya lubang), tapi secara fisik tetap blok penuh:
	 * tanpa override ini redaman cahayanya hanya 1 sehingga cahaya bocor menembus dinding Screw Piles.
	 */
	@Override
	protected int getLightDampening(BlockState state) {
		return 15;
	}

	/** Sisi yang saling menempel antar Screw Piles tidak digambar (tidak terlihat, dan gelap karena tidak ada cahaya di dalam). */
	@Override
	protected boolean skipRendering(BlockState state, BlockState neighborState, Direction direction) {
		return neighborState.is(this) || super.skipRendering(state, neighborState, direction);
	}

	/**
	 * Dipanggil oleh item Wallpaper/Carpet: menempelkan lapisan pada sisi Screw Piles yang di-klik.
	 *
	 * @return PASS jika blok yang di-klik bukan Screw Piles (item boleh lanjut ke perilaku lain)
	 */
	public static InteractionResult tryApplyCover(UseOnContext context, PanelCover cover) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		BlockState state = level.getBlockState(pos);
		if (!state.is(ModBlocks.SCREW_PILES)) {
			return InteractionResult.PASS;
		}

		Direction face = context.getClickedFace();
		if (getCover(state, face) != PanelCover.NONE) {
			return InteractionResult.FAIL;
		}

		if (!level.isClientSide()) {
			BlockState covered = withCover(state, face, cover);
			level.setBlock(pos, covered, Block.UPDATE_ALL);
			level.playSound(null, pos, SoundType.WOOL.getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
			level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(context.getPlayer(), covered));
			context.getItemInHand().consume(1, context.getPlayer());
		}
		return InteractionResult.SUCCESS;
	}

	/** Progres break per tick: tetap jika yang dibidik adalah lapisan, normal jika sisi polos. */
	@Override
	protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
		Direction aimed = CoverAim.aimedFace(player, pos);
		if (aimed != null) {
			switch (getCover(state, aimed)) {
				case WALLPAPER:
					return WALLPAPER_PROGRESS_PER_TICK; // 80 tick = 4 detik, apa pun alatnya
				case CARPET:
					return CoverManager.CARPET_PROGRESS_PER_TICK;
				default:
					break;
			}
		}
		return super.getDestroyProgress(state, player, level, pos);
	}

	/**
	 * Dipanggil saat pemain selesai "break". Jika sisi yang dibidik berlapis,
	 * hanya lapisan itu yang dilepas, Screw Piles tetap berdiri.
	 *
	 * @return true jika break sudah ditangani di sini (break vanilla harus dibatalkan)
	 */
	public boolean tryBreakCover(Level level, Player player, BlockPos pos, BlockState state) {
		Direction aimed = CoverAim.aimedFace(player, pos);
		if (aimed == null) {
			return false;
		}
		PanelCover cover = getCover(state, aimed);
		if (cover == PanelCover.NONE) {
			return false; // sisi polos: break Screw Piles seperti biasa
		}
		if (level.isClientSide()) {
			return true; // server yang memutuskan, client menunggu sinkronisasi
		}

		level.setBlock(pos, withCover(state, aimed, PanelCover.NONE), Block.UPDATE_ALL);
		if (level instanceof ServerLevel serverLevel) {
			CoverEffects.breakEffects(serverLevel, pos, aimed, cover == PanelCover.WALLPAPER ? ModBlocks.WALLPAPER : ModBlocks.CARPET);
		}
		level.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(player, state));
		if (!player.isCreative()) {
			Block.popResource(level, pos, new ItemStack(cover == PanelCover.WALLPAPER ? ModBlocks.WALLPAPER : ModBlocks.CARPET));
		}
		return true;
	}
}
