package com.moakiee.ae2lt.client.core;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.client.ldlib.LdlibShaders;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.util.function.Supplier;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.slf4j.Logger;

@EventBusSubscriber(modid = AE2LightningTech.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CoreEffectShaders {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long TIME_WRAP_MILLIS = 3_600_000L;
    private static final ResourceLocation TIANSHU_SHADER =
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "multiblock/tianshu_core");
    private static final ResourceLocation MATRIX_SHADER =
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "multiblock/matrix_core");

    private static final ShaderTracker TIANSHU = new ShaderTracker();
    private static final ShaderTracker MATRIX = new ShaderTracker();

    private CoreEffectShaders() {
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        if (CoreEffectBackend.useVeil()) {
            LOGGER.info("Compatible Veil detected; preparing LDLib core-effect shaders as fallback");
        } else {
            LOGGER.info("Using the LDLib core-effect shader backend");
        }

        registerShader(event, TIANSHU_SHADER, TIANSHU);
        registerShader(event, MATRIX_SHADER, MATRIX);
    }

    static RenderStateShard.ShaderStateShard tianshu() {
        return TIANSHU.shard;
    }

    static RenderStateShard.ShaderStateShard matrix() {
        return MATRIX.shard;
    }

    private static void registerShader(RegisterShadersEvent event,
                                       ResourceLocation location,
                                       ShaderTracker tracker) throws IOException {
        LdlibShaders.register(event, location, DefaultVertexFormat.POSITION_COLOR_NORMAL, "EffectTime",
                time -> time.set((System.currentTimeMillis() % TIME_WRAP_MILLIS) / 1000.0F),
                tracker::setInstance);
    }

    private static final class ShaderTracker implements Supplier<ShaderInstance> {
        private final RenderStateShard.ShaderStateShard shard =
                new RenderStateShard.ShaderStateShard(this);
        private ShaderInstance instance;

        private void setInstance(ShaderInstance instance) {
            this.instance = instance;
        }

        @Override
        public ShaderInstance get() {
            return instance;
        }
    }
}
