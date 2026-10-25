package com.moakiee.ae2lt.machine.crystalcatalyzer.recipe;

import com.moakiee.ae2lt.registry.ModRecipeTypes;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

/** Resolve on the server thread before machines run or recipe packets reach JEI/EMI. */
@EventBusSubscriber(modid = "ae2lt")
public final class CrystalHarvestRecipes {
    private CrystalHarvestRecipes() {
    }

    @SubscribeEvent
    public static void serverStarting(ServerStartingEvent event) {
        resolve(event.getServer());
    }

    @SubscribeEvent
    public static void datapackSync(OnDatapackSyncEvent event) {
        resolve(event.getPlayerList().getServer());
    }

    private static void resolve(MinecraftServer server) {
        var level = server.overworld();
        if (level == null) return;
        for (var holder : server.getRecipeManager().getAllRecipesFor(ModRecipeTypes.CRYSTAL_CATALYZER_TYPE.get())) {
            if (holder.value().outputSpec() instanceof CrystalHarvestOutput harvest) {
                harvest.resolveHarvest(level);
            }
        }
    }
}
