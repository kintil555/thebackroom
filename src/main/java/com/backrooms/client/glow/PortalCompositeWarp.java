package com.backrooms.client.glow;

import com.backrooms.BackroomsMod;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.lang.reflect.Method;
import java.util.Optional;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Distorsi portal Magnet yang dijalankan DI DALAM komposit dunia seberang milik Seamless Portals (lihat PortalCompositeWarpMixin),
 * menggantikan blit lurus dengan blit ber-warp. Komposit itu sudah di-stencil ke bentuk portal, sehingga distorsi hanya ada di
 * permukaan portal. Data portal (pusat, sumbu, kekuatan, waktu) dibaca dari tekstur data PortalDistortRenderer (frame sebelumnya).
 */
public final class PortalCompositeWarp {
	private static final BindGroupLayout WARP_SAMPLERS = BindGroupLayout.builder().withSampler("InSampler").withSampler("DataSampler").build();

	private static @Nullable RenderPipeline pipeline;
	private static boolean failed;

	private PortalCompositeWarp() {
	}

	/** Menggambar komposit ber-warp menggantikan draw biasa; false jika tidak ada yang perlu di-warp (pakai draw asli). */
	public static boolean draw(RenderPass pass, @Nullable GpuTextureView source) {
		GpuTextureView data = PortalDistortRenderer.warpDataView();
		if (source == null || data == null || failed) {
			return false;
		}
		RenderPipeline warp = pipeline();
		if (warp == null) {
			return false;
		}
		pass.setPipeline(warp);
		RenderSystem.bindDefaultUniforms(pass);
		pass.bindTexture("InSampler", source, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
		pass.bindTexture("DataSampler", data, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
		pass.draw(3, 1, 0, 0);
		return true;
	}

	private static @Nullable RenderPipeline pipeline() {
		if (pipeline != null) {
			return pipeline;
		}
		try {
			RenderPipeline built = RenderPipeline.builder()
				.withLocation(Identifier.fromNamespaceAndPath(BackroomsMod.MOD_ID, "pipeline/portal_composite_warp"))
				.withBindGroupLayout(BindGroupLayouts.GLOBALS)
				.withVertexShader(Identifier.parse("minecraft:core/screenquad"))
				.withFragmentShader(Identifier.fromNamespaceAndPath(BackroomsMod.MOD_ID, "core/portal_composite_warp"))
				.withBindGroupLayout(WARP_SAMPLERS)
				.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
				.withDepthStencilState(Optional.empty())
				.withCull(false)
				.build();
			Method register = RenderPipelines.class.getDeclaredMethod("register", RenderPipeline.class);
			register.setAccessible(true);
			pipeline = (RenderPipeline) register.invoke(null, built);
		} catch (Exception | LinkageError e) {
			failed = true;
			BackroomsMod.LOGGER.error("Gagal membangun pipeline komposit warp portal; distorsi di render portal dimatikan", e);
		}
		return pipeline;
	}
}
