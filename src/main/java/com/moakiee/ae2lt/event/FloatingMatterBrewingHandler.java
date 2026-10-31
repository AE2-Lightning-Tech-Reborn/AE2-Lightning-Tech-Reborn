package com.moakiee.ae2lt.event;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.registry.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;

@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class FloatingMatterBrewingHandler {
    private static final ResourceLocation LEVITATION_POTION =
            ResourceLocation.fromNamespaceAndPath("apothic_attributes", "levitation");

    private FloatingMatterBrewingHandler() {}

    @SubscribeEvent
    public static void registerBrewingRecipes(RegisterBrewingRecipesEvent event) {
        // Reuse Apothic Attributes' potion without requiring the mod or adding a duplicate.
        BuiltInRegistries.POTION.getHolder(LEVITATION_POTION).ifPresent(potion ->
                event.getBuilder().addMix(Potions.AWKWARD, ModItems.FLOATING_MATTER.get(), potion));
    }
}
