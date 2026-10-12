package com.moakiee.ae2lt.event;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.util.NaturalLightningOrigin;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LightningBolt;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class LightningOriginSyncHandler {
    private LightningOriginSyncHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLightningJoin(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel && event.getEntity() instanceof LightningBolt bolt
                && bolt.getPersistentData().getBoolean(
                        NaturalLightningTransformationHandler.NATURAL_WEATHER_LIGHTNING_TAG)) {
            // ServerEntity includes non-default metadata in the initial spawn bundle.
            // Use the recipe/collector origin marker, not the nullable player cause.
            ((NaturalLightningOrigin) bolt).ae2lt$setNaturalWeatherLightning(true);
        }
    }
}
