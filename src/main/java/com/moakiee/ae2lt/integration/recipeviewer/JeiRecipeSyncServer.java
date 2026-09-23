package com.moakiee.ae2lt.integration.recipeviewer;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.registry.ModRecipeTypes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

/** NeoForge 26 sends only recipe displays unless a mod requests its custom recipe types. */
@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class JeiRecipeSyncServer {
    private JeiRecipeSyncServer() {}

    @SubscribeEvent
    public static void syncRecipes(OnDatapackSyncEvent event) {
        event.sendRecipes(
                ModRecipeTypes.CRYSTAL_CATALYZER_TYPE.get(),
                ModRecipeTypes.LIGHTNING_ASSEMBLY_TYPE.get(),
                ModRecipeTypes.LIGHTNING_SIMULATION_TYPE.get(),
                ModRecipeTypes.LIGHTNING_TRANSFORM_TYPE.get(),
                ModRecipeTypes.LIGHTNING_STRIKE_TYPE.get(),
                ModRecipeTypes.OVERLOAD_PROCESSING_TYPE.get(),
                ModRecipeTypes.FIRMAMENT_CONVERSION_TYPE.get());
    }
}
