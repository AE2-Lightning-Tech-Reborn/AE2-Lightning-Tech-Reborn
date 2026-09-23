package com.moakiee.ae2lt.client.core;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

final class CoreEffectRenderTypes {
    private static final RenderType TIANSHU = create("tianshu", CoreEffectShaders.tianshu(), true);
    private static final RenderType MATRIX_CORE = create("matrix_core", CoreEffectShaders.matrixCore(), true);
    private static final RenderType MATRIX_GLOW = create("matrix_glow", CoreEffectShaders.matrixGlow(), false);
    private static final RenderType TIANSHU_FALLBACK = create("tianshu_fallback", CoreEffectShaders.tianshuFallback(), true);
    private static final RenderType MATRIX_CORE_FALLBACK = create("matrix_core_fallback", CoreEffectShaders.matrixCoreFallback(), true);
    private static final RenderType MATRIX_GLOW_FALLBACK = create("matrix_glow_fallback", CoreEffectShaders.matrixGlowFallback(), false);

    private CoreEffectRenderTypes() {
    }

    static RenderType tianshu(boolean shaderPackActive) {
        return shaderPackActive ? TIANSHU_FALLBACK : TIANSHU;
    }

    static RenderType matrixCore(boolean shaderPackActive) {
        return shaderPackActive ? MATRIX_CORE_FALLBACK : MATRIX_CORE;
    }

    static RenderType matrixGlow(boolean shaderPackActive) {
        return shaderPackActive ? MATRIX_GLOW_FALLBACK : MATRIX_GLOW;
    }

    private static RenderType create(String name, RenderPipeline pipeline, boolean sortOnUpload) {
        var builder = RenderSetup.builder(pipeline).bufferSize(262144);
        if (sortOnUpload) {
            builder.sortOnUpload();
        }
        return RenderType.create("ae2lt_core_effect_" + name, builder.createRenderSetup());
    }
}
