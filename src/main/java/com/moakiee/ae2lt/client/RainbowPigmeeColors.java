package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.registry.ModFumos;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/** Client-only colour animation; no server ticking or block updates are needed. */
@EventBusSubscriber(modid = AE2LightningTech.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RainbowPigmeeColors {
    private RainbowPigmeeColors() {
    }

    public static int currentColor() {
        var minecraft = Minecraft.getInstance();
        double ticks = minecraft.level == null ? 0 : minecraft.level.getGameTime() % 240L
                + minecraft.getTimer().getGameTimeDeltaPartialTick(true);
        return 0xFF000000 | Mth.hsvToRgb((float) (ticks / 240.0), 0.65F, 1.0F);
    }

    @SubscribeEvent
    public static void blockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, index) -> currentColor(), ModFumos.RAINBOW_PIGMEE_FUMO.get());
    }

    @SubscribeEvent
    public static void itemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, index) -> currentColor(), ModFumos.RAINBOW_PIGMEE_FUMO_ITEM.get());
    }
}
