package com.moakiee.ae2lt.event;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.logic.persistence.MultiblockRenderCleanupSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class MultiblockRenderCleanupEvents {
    private MultiblockRenderCleanupEvents() {
    }

    @SubscribeEvent
    public static void chunkLoaded(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            var pos = event.getChunk().getPos();
            level.getServer().execute(() -> {
                var data = MultiblockRenderCleanupSavedData.getIfPresent(level);
                if (data != null) data.chunkLoaded(pos);
            });
        }
    }

    @SubscribeEvent
    public static void levelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) {
            // ChunkEvent.Load can precede FULL availability. Change blocks only after the level tick.
            var data = MultiblockRenderCleanupSavedData.getIfPresent(level);
            if (data != null) data.applyLoadedChunks(level);
        }
    }
}
