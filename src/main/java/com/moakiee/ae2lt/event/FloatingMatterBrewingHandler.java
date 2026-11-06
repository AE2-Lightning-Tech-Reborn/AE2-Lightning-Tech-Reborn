package com.moakiee.ae2lt.event;

import com.moakiee.ae2lt.registry.ModItems;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import net.minecraftforge.common.crafting.PartialNBTIngredient;

public final class FloatingMatterBrewingHandler {
    private static final ResourceLocation LEVITATION_POTION =
            new ResourceLocation("apotheosis", "levitation");

    private FloatingMatterBrewingHandler() {}

    public static void register() {
        BuiltInRegistries.POTION.getOptional(LEVITATION_POTION).ifPresent(potion -> {
            for (var container : List.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION)) {
                var input = PotionUtils.setPotion(new ItemStack(container), Potions.AWKWARD);
                BrewingRecipeRegistry.addRecipe(
                        PartialNBTIngredient.of(container, input.getOrCreateTag()),
                        Ingredient.of(ModItems.FLOATING_MATTER.get()),
                        PotionUtils.setPotion(new ItemStack(container), potion));
            }
        });
    }
}
