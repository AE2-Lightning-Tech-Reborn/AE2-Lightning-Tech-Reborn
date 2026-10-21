package com.moakiee.ae2lt.event;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.registry.ModAttachmentTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LightningBolt;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class LightningOriginSyncHandler {
    private LightningOriginSyncHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLightningJoin(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel && event.getEntity() instanceof LightningBolt bolt
                && bolt.getPersistentData().getBoolean(
                        NaturalLightningTransformationHandler.NATURAL_WEATHER_LIGHTNING_TAG)) {
            // NeoForge includes this in the spawn bundle, before the first rendered frame.
            // Use the existing recipe/collector origin marker, not the nullable player cause.
            bolt.setData(ModAttachmentTypes.NATURAL_LIGHTNING, true);
        }
    }
}
