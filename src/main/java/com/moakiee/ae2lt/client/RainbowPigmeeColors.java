package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.registry.ModFumos;
import com.moakiee.ae2lt.blockentity.FumoBlockEntity;
import com.moakiee.ae2lt.lightning.RainbowPigmeeTransformation;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.DyeColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/** Client-only colour animation; no server ticking or block updates are needed. */
@EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public final class RainbowPigmeeColors {
    private RainbowPigmeeColors() {
    }

    public static int currentColor() {
        return 0xFF000000 | Mth.hsvToRgb((float) (animationTicks() % 240.0 / 240.0), 0.65F, 1.0F);
    }

    static double animationTicks() {
        var minecraft = Minecraft.getInstance();
        return minecraft.level == null ? 0 : minecraft.level.getGameTime() % 24000L
                + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
    }

    /** Vanilla's dye sequence and 25-tick crossfade, lifted for the darker Pigmee texture. */
    public static int sheepColor() {
        double ticks = animationTicks();
        int index = (int) (ticks / 25.0);
        return ARGB.srgbLerp((float) (ticks % 25.0 / 25.0),
                brighten(net.minecraft.client.color.ColorLerper.Type.SHEEP.getColor(DyeColor.byId(index % 16))),
                brighten(net.minecraft.client.color.ColorLerper.Type.SHEEP.getColor(DyeColor.byId((index + 1) % 16))));
    }

    private static int brighten(int color) {
        int r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
        float scale = 255.0F / Math.max(1, Math.max(r, Math.max(g, b)));
        // Preserve each dye's hue; keep a light floor instead of cycling into near-black.
        return ARGB.color(255,
                Math.round(48 + r * scale * 207 / 255),
                Math.round(48 + g * scale * 207 / 255),
                Math.round(48 + b * scale * 207 / 255));
    }

    @SubscribeEvent
    public static void itemColors(RegisterColorHandlersEvent.ItemTintSources event) {
        event.register(net.minecraft.resources.Identifier.parse("ae2lt:pigmee_name"), NamedPigmeeTint.CODEC);
    }

    public record NamedPigmeeTint() implements net.minecraft.client.color.item.ItemTintSource {
        static final com.mojang.serialization.MapCodec<NamedPigmeeTint> CODEC =
                com.mojang.serialization.MapCodec.unit(new NamedPigmeeTint());
        @Override public int calculate(net.minecraft.world.item.ItemStack stack,
                net.minecraft.client.multiplayer.ClientLevel level, net.minecraft.world.entity.LivingEntity owner) {
            return RainbowPigmeeTransformation.matchesName(stack.get(DataComponents.CUSTOM_NAME)) ? sheepColor() : -1;
        }
        @Override public com.mojang.serialization.MapCodec<NamedPigmeeTint> type() { return CODEC; }
    }
}
