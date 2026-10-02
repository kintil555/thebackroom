package com.backrooms.cover;

import com.backrooms.BackroomsMod;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;

/** Attachment per chunk: tersimpan permanen dan otomatis disinkronkan ke client yang melihat chunk itu. */
public final class CoverAttachments {
	public static final AttachmentType<CoverData> COVERS = AttachmentRegistry.create(
		Identifier.fromNamespaceAndPath(BackroomsMod.MOD_ID, "covers"),
		builder -> builder
			.persistent(CoverData.CODEC)
			.syncWith(CoverData.STREAM_CODEC, AttachmentSyncPredicate.all())
	);

	private CoverAttachments() {
	}

	/** Memanggil method ini memaksa class diload sehingga attachment terdaftar. */
	public static void init() {
	}
}
