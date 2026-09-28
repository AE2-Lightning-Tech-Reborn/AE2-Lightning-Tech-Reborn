package com.moakiee.ae2lt.machine.overloadfactory.recipe;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DynamicOps;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.conditions.ICondition;

/** Runs once on fresh datapack JSON, before recipe scripts; never called during factory execution. */
@net.neoforged.fml.common.EventBusSubscriber(modid = "ae2lt")
public final class InscriberProcessorRecipeLoader {
    private static final Set<String> SOURCE_TYPES = Set.of("ae2:inscriber", "minecraft:crafting_shaped",
            "minecraft:crafting_shapeless", "ae2lt:overload_processing");

    private InscriberProcessorRecipeLoader() { }

    @net.neoforged.bus.api.SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void beforeRecipeScripts(net.neoforged.neoforge.event.ModifyRecipeJsonsEvent event) {
        var context = net.neoforged.neoforge.common.conditions.ConditionalOps.retrieveContext().codec()
                .parse(event.getOps(), event.getOps().emptyMap()).getOrThrow();
        addRecipes(event.getRecipeJsons(), event.getOps(), context);
    }

    public static void addRecipes(Map<Identifier, JsonElement> json, DynamicOps<JsonElement> ops,
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
            sources.add(new RecipeHolder<>(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, entry.getKey()), recipe));
            if (recipe instanceof OverloadProcessingRecipe nativeRecipe) existing.add(new RecipeHolder<>(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, entry.getKey()), nativeRecipe));
        }
        int count = 0;
        for (var holder : InscriberProcessorAdapter.derive(sources, existing, new ReloadIngredientLookup(context, ops))) {
            if (json.containsKey(holder.id().identifier())) continue;
            try {
                json.put(holder.id().identifier(), encode(holder.value(), ops));
                count++;
            } catch (IllegalArgumentException error) {
                LogUtils.getLogger().warn("Cannot encode derived inscriber {}: {}", holder.id(), error.getMessage());
            }
        }
        LogUtils.getLogger().info("Generated {} missing bulk inscriber recipes before recipe scripts", count);
    }
    private static JsonElement encode(OverloadProcessingRecipe recipe, DynamicOps<JsonElement> ops) {
        var json = new com.google.gson.JsonObject();
        json.addProperty("type", "ae2lt:overload_processing");
        json.addProperty("priority", recipe.priority());
        var inputs = new com.google.gson.JsonArray();
        for (var input : recipe.itemInputs()) {
            var entry = new com.google.gson.JsonObject();
            var ingredient = input.ingredient();
            // Plain tag identifiers do not need the global registry owner to have bound yet.
            var tag = ingredient.getCustomIngredient() == null ? ingredient.getValues().unwrapKey() : java.util.Optional.<net.minecraft.tags.TagKey<net.minecraft.world.item.Item>>empty();
            entry.add("ingredient", tag.isPresent() ? new com.google.gson.JsonPrimitive("#" + tag.get().location())
                    : net.minecraft.world.item.crafting.Ingredient.CODEC.encodeStart(ops, ingredient).getOrThrow());
            entry.addProperty("count", input.count());
            inputs.add(entry);
        }
        json.add("inputs", inputs);
        var results = new com.google.gson.JsonArray();
        for (var output : recipe.resultTemplates()) {
            // Bulk outputs may exceed vanilla's template JSON count range.
            var entry = new com.google.gson.JsonObject();
            entry.addProperty("id", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(output.item().value()).toString());
            entry.addProperty("count", output.count());
            if (!output.components().isEmpty()) entry.add("components",
                    net.minecraft.core.component.DataComponentPatch.CODEC.encodeStart(ops, output.components()).getOrThrow());
            results.add(entry);
        }
        json.add("results", results);
        json.addProperty("totalEnergy", recipe.totalEnergy());
        json.addProperty("lightningCost", recipe.lightningCost());
        json.addProperty("lightningTier", recipe.lightningTier().getSerializedName());
        return json;
    }
}
