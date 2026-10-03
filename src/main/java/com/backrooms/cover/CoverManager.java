package com.backrooms.cover;

import com.backrooms.ModBlocks;
import com.backrooms.block.CoveredBlock;
import com.backrooms.block.CoveredBlockEntity;
import com.backrooms.block.PanelCover;
import com.backrooms.block.ScrewPilesBlock;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Carpet pada blok biasa. Datanya (posisi + sisi) disimpan di attachment chunk, bukan sebagai blok,
 * sehingga blok lain di sekitarnya tidak bisa menghapusnya.
 */
public final class CoverManager {
	/** Progres break carpet per tick: ~3 tick, apa pun blok yang ditempelinya. */
	public static final float CARPET_PROGRESS_PER_TICK = 1.0F / 3.0F;

	private CoverManager() {
	}

	public static int bit(Direction face) {
		return 1 << face.ordinal();
	}

	public static int mask(LevelChunk chunk, BlockPos pos) {
		CoverData data = ((AttachmentTarget) chunk).getAttached(CoverAttachments.COVERS);
		return data == null ? 0 : data.mask(pos.asLong());
	}

	public static int mask(Level level, BlockPos pos) {
		if (!level.hasChunkAt(pos)) {
			return 0;
		}
		if (level.getBlockState(pos).is(ModBlocks.COVERED_BLOCK)) {
			return level.getBlockEntity(pos) instanceof CoveredBlockEntity covered ? covered.mask() : 0;
		}
		return mask(level.getChunkAt(pos), pos);
	}

	public static boolean has(Level level, BlockPos pos, Direction face) {
		return (mask(level, pos) & bit(face)) != 0;
	}

	/** Lapisan yang menempel pada sisi tertentu, baik via blockstate Screw Piles maupun attachment. */
	public static PanelCover coverAt(Level level, BlockPos pos, BlockState state, Direction face) {
		if (state.is(ModBlocks.SCREW_PILES)) {
			return ScrewPilesBlock.getCover(state, face);
		}
		return has(level, pos, face) ? PanelCover.CARPET : PanelCover.NONE;
	}

	/** Tanpa update tetangga: dari sisi dunia, blok ini "tetap sama" (hanya dibungkus / dibuka). */
	private static final int SWAP_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

	/** Method yang menandakan perilaku khusus: blok seperti itu tidak dibungkus (tetap memakai attachment chunk). */
	private static final Set<String> BEHAVIOR_METHODS = Set.of(
		"updateShape", "neighborChanged", "tick", "randomTick", "onPlace", "affectNeighborsAfterRemoval", "entityInside", "stepOn",
		"fallOn", "animateTick", "useWithoutItem", "useItemOn", "getAnalogOutputSignal", "getSignal", "getDirectSignal", "attack",
		"onProjectileHit", "playerWillDestroy", "spawnAfterBreak", "wasExploded", "onExplosionHit", "setPlacedBy", "getFluidState",
		"getCloneItemStack", "getSoundType", "getShape", "getCollisionShape"
	);
	private static final Map<Block, Boolean> PLAIN_CACHE = new ConcurrentHashMap<>();

	/** Blok sederhana (kubus penuh, tanpa block entity / interaksi / tick / redstone) yang aman dibungkus CoveredBlock. */
	public static boolean canWrap(Level level, BlockPos pos, BlockState state) {
		Block block = state.getBlock();
		if (block instanceof EntityBlock || state.hasBlockEntity() || block instanceof CoveredBlock
			|| block == ModBlocks.SCREW_PILES || block == ModBlocks.COVER_DISPLAY) {
			return false;
		}
		if (state.getRenderShape() != RenderShape.MODEL || !state.canOcclude() || !state.isCollisionShapeFullBlock(level, pos)
			|| state.getLightEmission() != 0 || state.isRandomlyTicking() || state.isSignalSource() || state.hasAnalogOutputSignal()
			|| state.getDestroySpeed(level, pos) < 0.0F) {
			return false;
		}
		return PLAIN_CACHE.computeIfAbsent(block, CoverManager::hasNoSpecialBehavior);
	}

	private static boolean hasNoSpecialBehavior(Block block) {
		for (Class<?> type = block.getClass(); type != null && type != Block.class; type = type.getSuperclass()) {
			for (Method method : type.getDeclaredMethods()) {
				if (BEHAVIOR_METHODS.contains(method.getName())) {
					return false;
				}
			}
		}
		return true;
	}

	private static void setAttachmentMask(ServerLevel level, BlockPos pos, int mask) {
		LevelChunk chunk = level.getChunkAt(pos);
		AttachmentTarget target = (AttachmentTarget) chunk;
		CoverData current = target.getAttached(CoverAttachments.COVERS);
		if (current == null && mask == 0) {
			return;
		}
		CoverData updated = (current == null ? CoverData.EMPTY : current).with(pos.asLong(), mask);
		target.setAttached(CoverAttachments.COVERS, updated.isEmpty() ? null : updated);
		chunk.markUnsaved();
	}

	/**
	 * Blok sederhana yang ditempeli carpet menjadi CoveredBlock (blok asal + sisi disimpan di block entity, ikut ter-copy
	 * Axiom/structure/NBT); mask 0 mengembalikannya ke blok asal. Blok lain tetap memakai attachment chunk.
	 */
	private static void setMask(ServerLevel level, BlockPos pos, int mask) {
		BlockState state = level.getBlockState(pos);
		if (state.is(ModBlocks.COVERED_BLOCK)) {
			BlockEntity entity = level.getBlockEntity(pos);
			if (entity instanceof CoveredBlockEntity covered && covered.host() != null) {
				if (mask == 0) {
					level.setBlock(pos, covered.host(), SWAP_FLAGS);
				} else {
					covered.setMask(mask);
				}
			}
			return;
		}
		if (mask != 0 && canWrap(level, pos, state)) {
			int attached = mask(level.getChunkAt(pos), pos);
			setAttachmentMask(level, pos, 0); // data lama (sebelum ada pembungkus) dipindah ke block entity
			if (level.setBlock(pos, ModBlocks.COVERED_BLOCK.defaultBlockState(), SWAP_FLAGS)
				&& level.getBlockEntity(pos) instanceof CoveredBlockEntity covered) {
				covered.init(state, mask | attached);
				return;
			}
			setAttachmentMask(level, pos, mask | attached); // gagal membungkus: kembali ke attachment
			return;
		}
		setAttachmentMask(level, pos, mask);
	}

	/**
	 * Pasang carpet pada sisi-sisi (bitmask) blok yang baru ditaruh. Sisi yang tidak penuh di state baru
	 * langsung dilepas dan dijatuhkan lewat {@link #onHostChanged}.
	 */
	public static void applyMask(ServerLevel level, BlockPos pos, BlockState state, int mask) {
		setMask(level, pos, mask | mask(level, pos));
		onHostChanged(level, level.getChunkAt(pos), pos, state);
	}

	/** Klik kanan carpet pada sisi blok biasa: carpet menempel di sisi itu. */
	public static InteractionResult tryApplyCarpet(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		Direction face = context.getClickedFace();
		BlockState state = level.getBlockState(pos);

		if (!state.isFaceSturdy(level, pos, face) || has(level, pos, face)) {
			return InteractionResult.FAIL;
		}
		if (level instanceof ServerLevel serverLevel) {
			setMask(serverLevel, pos, mask(level, pos) | bit(face));
			level.playSound(null, pos, SoundType.WOOL.getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
			level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(context.getPlayer(), state));
			context.getItemInHand().consume(1, context.getPlayer());
		}
		return InteractionResult.SUCCESS;
	}

	/**
	 * Dipanggil saat pemain selesai "break" blok biasa. Jika sisi yang dibidik berlapis carpet,
	 * hanya carpet itu yang dilepas dan blok tetap berdiri.
	 *
	 * @return true jika break sudah ditangani di sini (break vanilla harus dibatalkan)
	 */
	public static boolean tryBreakCover(Level level, Player player, BlockPos pos) {
		int mask = mask(level, pos);
		if (mask == 0) {
			return false;
		}
		Direction aimed = CoverAim.aimedFace(player, pos);
		if (aimed == null || (mask & bit(aimed)) == 0) {
			return false;
		}
		if (level instanceof ServerLevel serverLevel) {
			setMask(serverLevel, pos, mask & ~bit(aimed));
			CoverEffects.breakEffects(serverLevel, pos, aimed, ModBlocks.CARPET);
			level.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(player, level.getBlockState(pos)));
			if (!player.isCreative()) {
				Block.popResource(level, pos, new ItemStack(ModBlocks.CARPET));
			}
		}
		return true; // di client: batalkan prediksi break, server yang memutuskan
	}

	/** Blok pemilik berubah: carpet pada sisi yang tidak lagi penuh dilepas dan dijatuhkan. */
	public static void onHostChanged(ServerLevel level, LevelChunk chunk, BlockPos pos, BlockState newState) {
		int mask = mask(chunk, pos);
		if (mask == 0) {
			return;
		}
		int remaining = mask;
		for (Direction face : Direction.values()) {
			if ((mask & bit(face)) != 0 && !newState.isFaceSturdy(level, pos, face)) {
				remaining &= ~bit(face);
				Block.popResource(level, pos, new ItemStack(ModBlocks.CARPET));
			}
		}
		if (remaining != mask) {
			setMask(level, pos, remaining);
		}
	}
}
