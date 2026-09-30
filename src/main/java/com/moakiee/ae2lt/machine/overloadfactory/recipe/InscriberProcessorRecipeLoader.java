package com.moakiee.ae2lt.machine.overloadfactory.recipe;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DynamicOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.conditions.ICondition;

/** Runs once on fresh datapack JSON, before recipe scripts; never called during factory execution. */
public final class InscriberProcessorRecipeLoader {
    private static final Set<String> SOURCE_TYPES = Set.of("ae2:inscriber", "minecraft:crafting_shaped",
            "minecraft:crafting_shapeless", "ae2lt:overload_processing");

    private InscriberProcessorRecipeLoader() { }

    public static void addRecipes(Map<ResourceLocation, JsonElement> json, DynamicOps<JsonElement> ops,
            ICondition.IContext context) {
        var sources = new ArrayList<RecipeHolder<?>>();
        var existing = new ArrayList<RecipeHolder<OverloadProcessingRecipe>>();
        for (var entry : json.entrySet()) {
            if (entry.getKey().getPath().startsWith("_") || !entry.getValue().isJsonObject()) continue;
            var type = entry.getValue().getAsJsonObject().get("type");
            if (type == null || !type.isJsonPrimitive() || !SOURCE_TYPES.contains(type.getAsString())) continue;
            // Use the same conditional codec/context as RecipeManager, including optional-mod conditions.
            var decoded = Recipe.CONDITIONAL_CODEC.parse(ops, entry.getValue()).result().orElse(java.util.Optional.empty());
            if (decoded.isEmpty()) continue; // Vanilla will report any malformed original recipe later.
            var recipe = decoded.get().carrier();
            sources.add(new RecipeHolder<>(entry.getKey(), recipe));
            if (recipe instanceof OverloadProcessingRecipe nativeRecipe) existing.add(new RecipeHolder<>(entry.getKey(), nativeRecipe));
        }
        int count = 0;
        for (var holder : InscriberProcessorAdapter.derive(sources, existing, new ReloadIngredientLookup(context, ops))) {
            if (json.containsKey(holder.id())) continue;
            var encoded = Recipe.CODEC.encodeStart(ops, holder.value());
            if (encoded.result().isPresent()) {
                json.put(holder.id(), encoded.result().get());
                count++;
            } else {
                LogUtils.getLogger().warn("Cannot encode derived inscriber {}: {}", holder.id(), encoded.error());
            }
        }
        LogUtils.getLogger().info("Generated {} missing bulk inscriber recipes before recipe scripts", count);
    }
}
