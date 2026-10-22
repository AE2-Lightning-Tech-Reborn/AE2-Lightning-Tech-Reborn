package com.moakiee.ae2lt.integration.emi;

import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingRecipeCatalog;
import com.moakiee.ae2lt.registry.ModBlocks;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.pedroksl.advanced_ae.recipes.ReactionChamberRecipe;

/** Reuse bridged reaction recipes, or provide them when only EMI is installed. */
final class AdvancedAeFactoryEmiCompat {
    private static final ResourceLocation REACTION_CHAMBER = new ResourceLocation("advanced_ae", "reaction_chamber");

    private AdvancedAeFactoryEmiCompat() {}

    static void registerWorkstation(EmiRegistry registry) {
        if (ModList.get().isLoaded("jei")) {
            // EMI skips JEI callbacks for mods with native EMI plugins, including our catalyst.
            // JEMI registers after us; wait until its actual category instance is available.
            registry.addDeferredRecipes(ignored -> registerBridgedWorkstation(registry));
            return;
        }
        var chamber = BuiltInRegistries.ITEM.get(REACTION_CHAMBER);
        var category = new EmiRecipeCategory(REACTION_CHAMBER, EmiStack.of(chamber)) {
            @Override public net.minecraft.network.chat.Component getName() { return chamber.getDescription(); }
        };
        registry.addCategory(category);
        registry.addWorkstation(category, EmiStack.of(chamber));
        registry.addWorkstation(category, EmiStack.of(ModBlocks.OVERLOAD_PROCESSING_FACTORY.get()));
        var manager = registry.getRecipeManager();
        for (var source : manager.getAllRecipesFor(ReactionChamberRecipe.TYPE)) {
            OverloadProcessingRecipeCatalog.find(manager, source.getId()).ifPresent(recipe ->
                    registry.addRecipe(new EmiOverloadProcessingRecipe(category, source.getId(), recipe)));
        }
    }

    private static void registerBridgedWorkstation(EmiRegistry registry) {
        try {
            // EMI 1.1 exposes pending categories only through this public implementation field.
            // Its API recipe manager is still empty until after deferred registrations finish.
            var pending = Class.forName("dev.emi.emi.registry.EmiRecipes").getField("categories").get(null);
            if (pending instanceof Iterable<?> categories) {
                for (var candidate : categories) {
                    if (candidate instanceof EmiRecipeCategory category && REACTION_CHAMBER.equals(category.getId())) {
                        registry.addWorkstation(category, EmiStack.of(ModBlocks.OVERLOAD_PROCESSING_FACTORY.get()));
                        return;
                    }
                }
            }
            com.mojang.logging.LogUtils.getLogger().warn("AdvancedAE reaction category was not registered by the JEI bridge");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            com.mojang.logging.LogUtils.getLogger().warn("Could not add the overload factory to EMI's bridged reaction category", failure);
        }
    }
}
