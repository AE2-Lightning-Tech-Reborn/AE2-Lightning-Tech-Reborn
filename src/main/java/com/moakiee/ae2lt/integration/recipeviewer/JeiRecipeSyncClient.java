package com.moakiee.ae2lt.integration.recipeviewer;

import com.moakiee.ae2lt.AE2LightningTech;
import net.minecraft.world.item.crafting.RecipeMap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;

/** Holds the server's recipes for optional client recipe viewers. */
@EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public final class JeiRecipeSyncClient {
    private static RecipeMap recipes = RecipeMap.EMPTY;

    private JeiRecipeSyncClient() {}

    public static RecipeMap recipes() {
        return recipes;
    }

    @SubscribeEvent
    public static void received(RecipesReceivedEvent event) {
        recipes = event.getRecipeMap();
        if (ModList.get().isLoaded("jei")) {
            com.moakiee.ae2lt.integration.jei.JEIPlugin.onRecipesChanged();
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        recipes = RecipeMap.EMPTY;
        if (ModList.get().isLoaded("jei")) {
            com.moakiee.ae2lt.integration.jei.JEIPlugin.onRecipesChanged();
        }
    }
}
