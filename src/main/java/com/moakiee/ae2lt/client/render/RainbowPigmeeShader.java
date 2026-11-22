package com.moakiee.ae2lt.client.render;

import java.io.IOException;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.client.ldlib.LdlibShaders;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/** The local tick offset is interpolated before colour evaluation, once per surface pixel. */
@EventBusSubscriber(modid = AE2LightningTech.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RainbowPigmeeShader {
    private static final ResourceLocation LOCATION =
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "rainbow_pigmee");

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
        LdlibShaders.register(event, LOCATION, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, "AnimationTicks",
                ticks -> ticks.set((float) RainbowPigmeeColors.animationTicks()), loaded -> shader = loaded);
    }
}
