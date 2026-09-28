package com.moakiee.ae2lt.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Native pipeline; interpolated model-local animation time travels in vertex UV0. */
@net.neoforged.fml.common.EventBusSubscriber(modid = "ae2lt", value = net.neoforged.api.distmarker.Dist.CLIENT)
public final class RainbowPigmeeShader {
    static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(Identifier.parse("ae2lt:pipeline/rainbow_pigmee"))
            .withVertexShader(Identifier.parse("ae2lt:core/rainbow_pigmee"))
            .withFragmentShader(Identifier.parse("ae2lt:core/rainbow_pigmee"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withDepthStencilState(DepthStencilState.DEFAULT).build();
    private RainbowPigmeeShader() {}
    @net.neoforged.bus.api.SubscribeEvent
    public static void register(net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent event) {
        event.registerPipeline(PIPELINE);
    }
}
