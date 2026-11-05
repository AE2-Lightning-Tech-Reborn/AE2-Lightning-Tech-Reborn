package com.moakiee.ae2lt.dimension;

import com.moakiee.ae2lt.AE2LightningTech;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class PigmeeDimensionSpawning {
    private PigmeeDimensionSpawning() {}

    public static boolean blocks(MobSpawnType reason) {
        return reason == MobSpawnType.NATURAL || reason == MobSpawnType.CHUNK_GENERATION
                || reason == MobSpawnType.PATROL || reason == MobSpawnType.STRUCTURE;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void checkPlacement(MobSpawnEvent.SpawnPlacementCheck event) {
        if (PigmeeDimensions.isPigmee(event.getLevel().getLevel()) && blocks(event.getSpawnType())) {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void checkPosition(MobSpawnEvent.PositionCheck event) {
        if (PigmeeDimensions.isPigmee(event.getLevel().getLevel()) && blocks(event.getSpawnType())) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }
}
