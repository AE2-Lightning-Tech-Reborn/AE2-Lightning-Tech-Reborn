package com.moakiee.ae2lt.client;

import java.io.IOException;

import com.moakiee.ae2lt.AE2LightningTech;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

/** The local tick offset is interpolated before colour evaluation, once per surface pixel. */
@EventBusSubscriber(modid = AE2LightningTech.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RainbowPigmeeShader {
    private static ShaderInstance shader;

    private RainbowPigmeeShader() {
    }

    static ShaderInstance get() {
        return shader;
    }

    public static boolean isLoaded() {
        return shader != null;
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(new TickShader(event.getResourceProvider()), loaded -> shader = loaded);
    }

    private static final class TickShader extends ShaderInstance {
        private final Uniform animationTicks;

        TickShader(ResourceProvider resources) throws IOException {
            super(resources, ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "rainbow_pigmee"),
                    DefaultVertexFormat.POSITION_TEX_COLOR);
            animationTicks = getUniform("AnimationTicks");
        }

        @Override
        public void setDefaultUniforms(VertexFormat.Mode mode, Matrix4f modelView, Matrix4f projection, Window window) {
            super.setDefaultUniforms(mode, modelView, projection, window);
            if (animationTicks != null) {
                animationTicks.set((float) RainbowPigmeeColors.animationTicks());
            }
        }
    }
}
