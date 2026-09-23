package com.moakiee.ae2lt.client.core;

import com.moakiee.ae2lt.AE2LightningTech;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

/** Native 26.1 pipelines for the original Tianshu and Matrix shader programs. */
@EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public final class CoreEffectShaders {
    private static final Identifier VERTEX = id("core/multiblock/core");
    private static final Identifier TIANSHU_FRAGMENT = id("core/multiblock/tianshu_core");
    private static final Identifier MATRIX_FRAGMENT = id("core/multiblock/matrix_core");
    private static final Identifier VANILLA_COLOR = Identifier.withDefaultNamespace("core/position_color");

    private static final RenderPipeline TIANSHU = shader("tianshu", TIANSHU_FRAGMENT, false, true);
    private static final RenderPipeline MATRIX_CORE = shader("matrix_core", MATRIX_FRAGMENT, false, true);
    private static final RenderPipeline MATRIX_GLOW = shader("matrix_glow", MATRIX_FRAGMENT, true, false);
    private static final RenderPipeline TIANSHU_FALLBACK = shader("tianshu_fallback", VANILLA_COLOR, false, true);
    private static final RenderPipeline MATRIX_CORE_FALLBACK = shader("matrix_core_fallback", VANILLA_COLOR, false, true);
    private static final RenderPipeline MATRIX_GLOW_FALLBACK = shader("matrix_glow_fallback", VANILLA_COLOR, true, false);

    private CoreEffectShaders() {
    }

    @SubscribeEvent
    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(TIANSHU);
        event.registerPipeline(MATRIX_CORE);
        event.registerPipeline(MATRIX_GLOW);
        event.registerPipeline(TIANSHU_FALLBACK);
        event.registerPipeline(MATRIX_CORE_FALLBACK);
        event.registerPipeline(MATRIX_GLOW_FALLBACK);
    }

    static RenderPipeline tianshu() { return TIANSHU; }
    static RenderPipeline matrixCore() { return MATRIX_CORE; }
    static RenderPipeline matrixGlow() { return MATRIX_GLOW; }
    static RenderPipeline tianshuFallback() { return TIANSHU_FALLBACK; }
    static RenderPipeline matrixCoreFallback() { return MATRIX_CORE_FALLBACK; }
    static RenderPipeline matrixGlowFallback() { return MATRIX_GLOW_FALLBACK; }

    private static RenderPipeline shader(String name, Identifier fragment, boolean additive, boolean writesDepth) {
        boolean nativeShader = fragment.getNamespace().equals(AE2LightningTech.MODID);
        return RenderPipeline.builder()
                .withLocation(id("pipeline/core_effect/" + name))
                .withVertexShader(nativeShader ? VERTEX : VANILLA_COLOR)
                .withFragmentShader(fragment)
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withUniform("Globals", UniformType.UNIFORM_BUFFER)
                .withColorTargetState(new ColorTargetState(additive ? BlendFunction.ADDITIVE : BlendFunction.TRANSLUCENT))
                .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, writesDepth))
                .withCull(false)
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.TRIANGLES)
                .build();
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(AE2LightningTech.MODID, path);
    }
}
