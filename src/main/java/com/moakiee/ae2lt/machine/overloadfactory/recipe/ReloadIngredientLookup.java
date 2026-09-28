package com.moakiee.ae2lt.machine.overloadfactory.recipe;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.conditions.ICondition;

/** Matches against this reload's pending tags, without binding or changing the global registries. */
final class ReloadIngredientLookup implements UnaryOperator<Ingredient> {
    private final ICondition.IContext context;
    private final DynamicOps<JsonElement> ops;
    private final Map<Ingredient, Ingredient> cache = new IdentityHashMap<>();

    ReloadIngredientLookup(ICondition.IContext context, DynamicOps<JsonElement> ops) {
        this.context = context;
        this.ops = ops;
    }

    @Override
    public Ingredient apply(Ingredient ingredient) {
        if (cache.containsKey(ingredient)) return cache.get(ingredient);
        Ingredient resolved;
        if (ingredient.getCustomIngredient() == null) {
            var key = ingredient.getValues().unwrapKey();
            if (key.isEmpty()) resolved = ingredient;
            else {
                var items = context.getTag(key.get()).stream().map(net.minecraft.core.Holder::value).toList();
                resolved = items.isEmpty() ? net.neoforged.neoforge.common.crafting.DifferenceIngredient.of(
                        Ingredient.of(net.minecraft.world.item.Items.STONE), Ingredient.of(net.minecraft.world.item.Items.STONE))
                        : Ingredient.of(items.stream());
            }
        } else {
            var json = Ingredient.CODEC.encodeStart(ops, ingredient).result().orElse(null);
            // Preserve unknown predicates without dereferencing possibly stale tag holders.
            resolved = json == null || hasTagReference(json) ? null : ingredient;
        }
        cache.put(ingredient, resolved);
        return resolved;
    }

    private static boolean hasTagReference(JsonElement json) {
        if (json.isJsonArray()) {
            for (var entry : json.getAsJsonArray()) if (hasTagReference(entry)) return true;
        } else if (json.isJsonObject()) {
            for (var entry : json.getAsJsonObject().entrySet()) {
                if (entry.getKey().equals("tag") || hasTagReference(entry.getValue())) return true;
            }
        } else if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
            return json.getAsString().startsWith("#");
        }
        return false;
    }
}
