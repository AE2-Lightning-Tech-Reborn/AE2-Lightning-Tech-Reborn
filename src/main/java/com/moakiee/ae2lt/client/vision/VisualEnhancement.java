package com.moakiee.ae2lt.client.vision;

import com.moakiee.ae2lt.celestweave.module.NightVisionSubmodule;
import com.moakiee.ae2lt.celestweave.service.ArmorCapabilityCollector;
import com.moakiee.ae2lt.device.capability.DeviceCapability;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class VisualEnhancement {
    private VisualEnhancement() {}

    public static boolean enabled(String option) {
        var player = Minecraft.getInstance().player;
        if (player == null) return false;
        var module = NightVisionSubmodule.INSTANCE;
        for (var active : ArmorCapabilityCollector.collectPerInstalledStack(player)) {
            if (active.submoduleId().equals(module.id())
                    && active.capability() instanceof DeviceCapability.StatusEffectGrant) {
                return module.option(active.armor(), option);
            }
        }
        return false;
    }

    public static boolean canRender(String option) {
        var player = Minecraft.getInstance().player;
        return player != null && !player.hasEffect(MobEffects.BLINDNESS)
                && !player.hasEffect(MobEffects.DARKNESS) && enabled(option);
    }

    @SubscribeEvent
    public static void renderFog(ViewportEvent.RenderFog event) {
        if (event.getCamera().getEntity() != Minecraft.getInstance().player || !canRender(NightVisionSubmodule.FOG)) return;
        event.setNearPlaneDistance(1_000_000f);
        event.setFarPlaneDistance(1_000_001f);
        event.setCanceled(true);
    }
}
