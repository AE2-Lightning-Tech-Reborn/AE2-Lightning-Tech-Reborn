package com.moakiee.ae2lt.event;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.logic.energy.WirelessEnergyEndpoints;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class WirelessEnergyLifecycleEvents {
    private WirelessEnergyLifecycleEvents() {}

    @SubscribeEvent
    public static void onBlockChanged(BlockEvent.NeighborNotifyEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            WirelessEnergyEndpoints.onBlockChanged(level, event.getPos());
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            WirelessEnergyEndpoints.onChunkChanged(level, event.getChunk().getPos(), true);
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            WirelessEnergyEndpoints.onChunkChanged(level, event.getChunk().getPos(), false);
        }
    }
}
