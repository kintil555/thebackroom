package com.backrooms.client.glow;

import com.backrooms.BackroomsMod;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Distorsi portal Magnet yang dijalankan DI DALAM komposit dunia seberang milik Seamless Portals (lihat PortalStencilWarpMixin),
 * menggantikan blit lurus dengan blit ber-warp. Komposit itu sudah di-stencil ke bentuk portal, sehingga distorsi hanya ada di
 * permukaan portal. Data portal (pusat, sumbu, kekuatan, waktu) dibaca dari tekstur data PortalDistortRenderer (frame sebelumnya).
 */
public final class PortalStencilWarp {
	private static final BindGroupLayout WARP_SAMPLERS = BindGroupLayout.builder().withSampler("InSampler").withSampler("DataSampler").build();

	private static @Nullable RenderPipeline pipeline;
	private static @Nullable TextureTarget scratch;
	private static boolean failed;

	private PortalStencilWarp() {
	}

	/**
	 * Dipanggil dari mixin saat konten dunia seberang sudah tergambar di target utama dan stencil portal (EQUAL 1) masih aktif:
	 * menyalin warna layar ke target sementara lalu menggambar ulang lewat shader warp, hanya pada piksel stencil = 1 (bentuk portal).
	 */
	public static void apply() {
		GpuTextureView data = PortalDistortRenderer.warpDataView();
		if (data == null || failed) {
			return;
		}
		RenderPipeline warp = pipeline();
		RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
		if (warp == null || main == null) {
			return;
		}
		try {
			if (scratch == null || scratch.width != main.width || scratch.height != main.height) {
				if (scratch != null) {
					scratch.destroyBuffers();
				}
				scratch = new TextureTarget("backrooms portal warp", main.width, main.height, false, GpuFormat.RGBA8_UNORM);
			}
			RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(main.getColorTexture(), scratch.getColorTexture(), 0, 0, 0, 0, 0, main.width, main.height);
			GL11.glDisable(GL11.GL_BLEND);
			GL11.glDisable(GL11.GL_DEPTH_TEST);
			try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
				() -> "backrooms_portal_warp",
				main.getColorTextureView(),
				Optional.empty(),
				main.getDepthTextureView(),
				OptionalDouble.empty(),
				new RenderPass.RenderArea(0, 0, main.width, main.height))) {
				pass.setPipeline(warp);
				RenderSystem.bindDefaultUniforms(pass);
				pass.bindTexture("InSampler", scratch.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
				pass.bindTexture("DataSampler", data, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
				pass.draw(3, 1, 0, 0);
			}
			GL11.glEnable(GL11.GL_BLEND);
			GL11.glEnable(GL11.GL_DEPTH_TEST);
			PortalDistortRenderer.markDirectWarpRan();
		} catch (Exception | LinkageError e) {
			failed = true;
			BackroomsMod.LOGGER.error("Pass warp stencil portal gagal; distorsi kembali ke postfx", e);
		}
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
