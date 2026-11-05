package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.dimension.PigmeeDimensions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import org.joml.Vector3f;

/** Visual full brightness without changing the light used by monster/spawner rules. */
@EventBusSubscriber(modid = "ae2lt", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PigmeeDimensionEffects extends DimensionSpecialEffects.OverworldEffects {
    @SubscribeEvent
    public static void register(RegisterDimensionSpecialEffectsEvent event) {
        event.register(PigmeeDimensions.TYPE, new PigmeeDimensionEffects());
    }

    @Override
    public void adjustLightmapColors(ClientLevel level, float partialTicks, float skyDarken,
            float blockLightRedFlicker, float skyLight, int pixelX, int pixelY, Vector3f colors) {
        colors.set(1.0F, 1.0F, 1.0F);
    }
}
