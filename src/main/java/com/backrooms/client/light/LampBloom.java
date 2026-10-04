package com.backrooms.client.light;

import com.backrooms.client.postfx.LevelMatrices;
import com.backrooms.BackroomsMod;
import com.backrooms.ModBlocks;
import com.backrooms.block.LampBlock;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

/**
 * Daftar Lamp menyala yang mendapat bloom di sisi client. Chunk di sekitar kamera dipindai berkala untuk mencari
 * posisi Lamp (murah: bagian chunk tanpa Lamp dilewati lewat palet), sedangkan status menyala/mati dibaca tiap frame
 * sehingga kedip Lamp langsung ikut. Warna bloom diambil dari tekstur Lamp (rata-rata piksel terang), jadi mengikuti
 * resource pack. Bloom digambar oleh {@link LampBloomRenderer}.
 */
final class LampBloom {
	/** Bloom menutupi 1 blok penuh Lamp dan meluas sejauh ini (blok) di luar tepinya. */
	static final float RADIUS_BLOCKS = 0.9f;
	static final int MAX_LAMPS = 32;

	private static final int SCAN_RADIUS_CHUNKS = 3;
	private static final long SCAN_INTERVAL_TICKS = 20L;
	private static final int MAX_CANDIDATES = 2048;
	/** Bloom penuh sampai jarak ini dari kamera, lalu memudar sampai {@link #FAR_BLOCKS}. */
	private static final float NEAR_BLOCKS = 24.0f;
	private static final float FAR_BLOCKS = 40.0f;
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(BackroomsMod.MOD_ID, "textures/block/lamp.png");
	/** Dipakai jika tekstur tidak bisa dibaca atau tidak punya piksel terang. */
	private static final float[] FALLBACK_TINT = {1.0f, 0.93f, 0.72f};

	private static final List<BlockPos> CANDIDATES = new ArrayList<>();
	private static final Matrix4f MATRIX = new Matrix4f();
	private static final Vector4f TMP = new Vector4f();
	private static @Nullable ClientLevel scannedLevel;
	private static long lastScanTick;
	private static float @Nullable [] tint;

	private LampBloom() {
	}

	/** Satu Lamp siap gambar: pusat blok (dunia) dan kekuatan 0..1 menurut jarak. */
	record Lamp(Vec3 center, float strength) {
	}

	/** Daftarkan Lamp yang baru diletakkan segera, tanpa menunggu rescan berkala. */
	static void addCandidate(BlockPos pos) {
		BlockPos immutable = pos.immutable();
		if (!CANDIDATES.contains(immutable) && CANDIDATES.size() < MAX_CANDIDATES) {
			CANDIDATES.add(immutable);
		}
	}

	/** Reset saat keluar dunia; warna dibaca ulang pada sesi berikutnya (resource pack bisa berubah). */
	static void clear() {
		CANDIDATES.clear();
		scannedLevel = null;
		tint = null;
	}

	/** Warna bloom 0..1 (kanal dominan = 1). */
	static float[] tint() {
		if (tint == null) {
			tint = readTint();
		}
		return tint;
	}

	/** Lamp menyala terdekat yang masuk atau dekat pandangan, paling banyak {@link #MAX_LAMPS}. */
	static List<Lamp> visibleLamps(ClientLevel level, Camera camera) {
		Vec3 eye = camera.position();
		long now = level.getGameTime();
		if (scannedLevel != level || now - lastScanTick >= SCAN_INTERVAL_TICKS || now < lastScanTick) {
			rescan(level, eye);
			scannedLevel = level;
			lastScanTick = now;
		}
		if (CANDIDATES.isEmpty()) {
			return List.of();
		}
		LevelMatrices.viewProjection(camera, MATRIX);
		List<Lamp> lamps = new ArrayList<>();
		for (BlockPos pos : CANDIDATES) {
			BlockState state = level.getBlockState(pos);
			if (!state.is(ModBlocks.LAMP) || !state.getValue(LampBlock.LIT)) {
				continue;
			}
			Vec3 center = Vec3.atCenterOf(pos);
			float distance = (float) center.distanceTo(eye);
			if (distance >= FAR_BLOCKS || !inView(center, eye, distance)) {
				continue;
			}
			// Oklusi ditangani per piksel di shader (sampel depth pada permukaan Lamp), jadi tidak ada gating di sisi CPU.
			float strength = 1.0f - smooth((distance - NEAR_BLOCKS) / (FAR_BLOCKS - NEAR_BLOCKS));
			lamps.add(new Lamp(center, strength));
		}
		lamps.sort(Comparator.comparingDouble(lamp -> lamp.center().distanceToSqr(eye)));
		return lamps.size() > MAX_LAMPS ? lamps.subList(0, MAX_LAMPS) : lamps;
	}

	/** Pusat di depan kamera atau sangat dekat; di luar layar dibuang dengan margin lebar (bloom hanya meluas 0,3 blok). */
	private static boolean inView(Vec3 center, Vec3 eye, float distance) {
		if (distance < 2.5f) {
			return true;
		}
		TMP.set((float) (center.x - eye.x), (float) (center.y - eye.y), (float) (center.z - eye.z), 1.0f);
		MATRIX.transform(TMP);
		if (TMP.w <= 0.0f) {
			return false;
		}
		return Math.abs(TMP.x / TMP.w) <= 1.6f && Math.abs(TMP.y / TMP.w) <= 1.6f;
	}

	private static float smooth(float x) {
		float t = Mth.clamp(x, 0.0f, 1.0f);
		return t * t * (3.0f - 2.0f * t);
	}

	private static void rescan(ClientLevel level, Vec3 eye) {
		CANDIDATES.clear();
		int centerX = Mth.floor(eye.x) >> 4;
		int centerZ = Mth.floor(eye.z) >> 4;
		for (int chunkX = centerX - SCAN_RADIUS_CHUNKS; chunkX <= centerX + SCAN_RADIUS_CHUNKS; chunkX++) {
			for (int chunkZ = centerZ - SCAN_RADIUS_CHUNKS; chunkZ <= centerZ + SCAN_RADIUS_CHUNKS; chunkZ++) {
				LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
				if (chunk != null) {
					scanChunk(chunk, chunkX, chunkZ);
				}
			}
		}
	}

	private static void scanChunk(LevelChunk chunk, int chunkX, int chunkZ) {
		LevelChunkSection[] sections = chunk.getSections();
		for (int index = 0; index < sections.length; index++) {
			LevelChunkSection section = sections[index];
			if (section.hasOnlyAir() || !section.maybeHas(state -> state.is(ModBlocks.LAMP))) {
				continue;
			}
			int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(index));
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						if (section.getBlockState(x, y, z).is(ModBlocks.LAMP) && CANDIDATES.size() < MAX_CANDIDATES) {
							CANDIDATES.add(new BlockPos((chunkX << 4) + x, baseY + y, (chunkZ << 4) + z));
						}
					}
				}
			}
		}
	}

	/** Rata-rata warna piksel terang tekstur Lamp (bobot = kecerahan kuadrat), dinormalkan agar kanal dominan = 1. */
	private static float[] readTint() {
		Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(TEXTURE);
		if (resource.isEmpty()) {
			return FALLBACK_TINT;
		}
		try (InputStream stream = resource.get().open(); NativeImage image = NativeImage.read(stream)) {
			double red = 0.0;
			double green = 0.0;
			double blue = 0.0;
			double total = 0.0;
			for (int y = 0; y < image.getHeight(); y++) {
				for (int x = 0; x < image.getWidth(); x++) {
					int argb = image.getPixel(x, y);
					double alpha = (argb >>> 24) / 255.0;
					double r = ((argb >> 16) & 0xFF) / 255.0;
					double g = ((argb >> 8) & 0xFF) / 255.0;
					double b = (argb & 0xFF) / 255.0;
					double luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b;
					double weight = alpha * luminance * luminance;
					red += r * weight;
					green += g * weight;
					blue += b * weight;
					total += weight;
				}
			}
			double max = Math.max(red, Math.max(green, blue));
			if (total <= 1.0e-6 || max <= 1.0e-6) {
				return FALLBACK_TINT;
			}
			return new float[] {(float) (red / max), (float) (green / max), (float) (blue / max)};
		} catch (Exception e) {
			BackroomsMod.LOGGER.warn("Gagal membaca tekstur Lamp untuk warna bloom; memakai warna bawaan", e);
			return FALLBACK_TINT;
		}
	}
}
