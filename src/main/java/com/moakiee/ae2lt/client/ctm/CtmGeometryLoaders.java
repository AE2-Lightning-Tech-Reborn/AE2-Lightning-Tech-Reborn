package com.moakiee.ae2lt.client.ctm;

import com.moakiee.ae2lt.AE2LightningTech;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterBlockStateModels;

@EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public final class CtmGeometryLoaders {
    private CtmGeometryLoaders() {
    }

    @SubscribeEvent
    public static void registerModels(RegisterBlockStateModels event) {
        event.registerModel(Identifier.fromNamespaceAndPath(AE2LightningTech.MODID, "connected_texture"),
                ConnectedTextureGeometry.CODEC);
    }
}
