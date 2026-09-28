package com.moakiee.ae2lt.machine.overloadfactory.recipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.WeakHashMap;

import com.mojang.logging.LogUtils;
import com.moakiee.ae2lt.registry.ModRecipeTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.fml.ModList;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

/**
 * The factory's execution catalog. Borrowed reaction views never enter RecipeManager.
 * Reaction views use the original viewer category; expanded inscriber views use the factory category.
 * All sources, including pre-script inscriber wrappers, come from the final post-script recipe manager.
 */
@EventBusSubscriber(modid = "ae2lt")
public final class OverloadProcessingRecipeCatalog {
    private static final Map<RecipeManager, Snapshot> CACHE = new WeakHashMap<>();
    private static final Comparator<RecipeHolder<OverloadProcessingRecipe>> ORDER = Comparator
            .<RecipeHolder<OverloadProcessingRecipe>>comparingInt(holder -> holder.value().priority()).reversed()
            .thenComparing(Comparator.comparingInt(
                    (RecipeHolder<OverloadProcessingRecipe> holder) -> holder.value().itemInputs().size()).reversed())
            .thenComparing(Comparator.comparingInt(
                    (RecipeHolder<OverloadProcessingRecipe> holder) -> holder.value().totalInputCount()).reversed())
            .thenComparing(holder -> holder.id().toString());

    private OverloadProcessingRecipeCatalog() {
    }

    public static synchronized List<RecipeHolder<OverloadProcessingRecipe>> recipes(RecipeManager manager) {
        var previous = CACHE.get(manager);
        // RecipeManager replaces its immutable byName map on both datapack reload and client sync.
        // Its values view is stable between changes, so active jobs don't walk the recipe graph per tick.
        if (previous != null && previous.sources() == manager.getRecipes()) return previous.recipes();
        var nativeRecipes = com.moakiee.ae2lt.recipe.compat.LegacyRecipeAccess.recipesOfType(manager, ModRecipeTypes.OVERLOAD_PROCESSING_TYPE.get());
        List<? extends RecipeHolder<?>> reactions = ModList.get() != null && ModList.get().isLoaded("advanced_ae")
                ? AdvancedAeReactionAdapter.sourceRecipes(manager) : List.of();
        return recipes(manager, nativeRecipes, reactions);
    }

    public static synchronized List<RecipeHolder<OverloadProcessingRecipe>> displayRecipes(RecipeManager manager) {
        recipes(manager);
        return CACHE.get(manager).displayRecipes();
    }

    @SubscribeEvent
    public static synchronized void tagsUpdated(TagsUpdatedEvent event) {
        CACHE.clear();
    }

    /** Both original and derived IDs use the current catalog; unchanged jobs never rescan the recipe graph. */
    public static synchronized Optional<RecipeHolder<OverloadProcessingRecipe>> find(
            RecipeManager manager, Identifier id) {
        var source = com.moakiee.ae2lt.recipe.compat.LegacyRecipeAccess.byId(manager, id).orElse(null);
        if (source != null && source.value() instanceof OverloadProcessingRecipe nativeRecipe
                && nativeRecipe.getType() == ModRecipeTypes.OVERLOAD_PROCESSING_TYPE.get()) {
            return Optional.of(new RecipeHolder<>(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, id), nativeRecipe));
        }
        recipes(manager);
        return Optional.ofNullable(CACHE.get(manager).byId().get(id));
    }

    // Separate source collection from conversion so reloads can be tested without registering mod globals.
    static synchronized List<RecipeHolder<OverloadProcessingRecipe>> recipes(
            RecipeManager manager, List<RecipeHolder<OverloadProcessingRecipe>> nativeRecipes,
            List<? extends RecipeHolder<?>> reactions) {
        Snapshot previous = CACHE.get(manager);
        if (previous != null && previous.sources() == manager.getRecipes()
                && sameHolders(previous.nativeRecipes(), nativeRecipes)
                && sameHolders(previous.reactions(), reactions)) {
            return previous.recipes();
        }
        var recipes = new ArrayList<>(nativeRecipes);
        for (var holder : reactions) {
            try {
                recipes.add(AdvancedAeReactionAdapter.convert(holder));
            } catch (IllegalArgumentException e) {
                LogUtils.getLogger().warn("Overload factory cannot borrow reaction {}: {}", holder.id(), e.getMessage());
            }
        }
        recipes.sort(ORDER);
        var result = List.copyOf(recipes);
        var displayed = new ArrayList<>(nativeRecipes);
        displayed.sort(ORDER);
        var byId = new HashMap<Identifier, RecipeHolder<OverloadProcessingRecipe>>();
        for (var recipe : result) byId.put(recipe.id().identifier(), recipe);
        CACHE.put(manager, new Snapshot(manager.getRecipes(), List.copyOf(nativeRecipes), List.copyOf(reactions),
                result, List.copyOf(displayed), Map.copyOf(byId)));
        return result;
    }

    private static boolean sameHolders(List<? extends RecipeHolder<?>> left, List<? extends RecipeHolder<?>> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            if (!left.get(i).id().equals(right.get(i).id()) || left.get(i).value() != right.get(i).value()) {
                return false;
            }
        }
        return true;
    }

    private record Snapshot(Collection<RecipeHolder<?>> sources, List<RecipeHolder<OverloadProcessingRecipe>> nativeRecipes,
            List<? extends RecipeHolder<?>> reactions, List<RecipeHolder<OverloadProcessingRecipe>> recipes,
            List<RecipeHolder<OverloadProcessingRecipe>> displayRecipes,
            Map<Identifier, RecipeHolder<OverloadProcessingRecipe>> byId) {
    }
}
