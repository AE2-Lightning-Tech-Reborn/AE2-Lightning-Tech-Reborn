package com.moakiee.ae2lt.integration.ae2ct;

import appeng.api.stacks.GenericStack;
import appeng.menu.me.crafting.CraftingPlanSummary;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Supplies Crafting Tree's required payload when an exact plan builds its own native summary. */
public final class CraftingTreeSummaryCompat {
    private static final ClassValue<Optional<Access>> ACCESS = new ClassValue<>() {
        @Override
        protected Optional<Access> computeValue(Class<?> summaryType) {
            Class<?> api;
            try {
                api = Class.forName("com.neuvillette.ae2ct.api.ICraftingPlanSummary", false,
                        summaryType.getClassLoader());
            } catch (ClassNotFoundException absent) {
                return Optional.empty();
            }
            if (!api.isAssignableFrom(summaryType)) return Optional.empty();
            try {
                var tree = Class.forName("com.neuvillette.ae2ct.api.RecipeHelper", false,
                        summaryType.getClassLoader());
                var recipe = Class.forName("com.neuvillette.ae2ct.api.RecipeHelper$Recipe", false,
                        summaryType.getClassLoader());
                return Optional.of(new Access(api.getMethod("getJob"), api.getMethod("setJob", tree),
                        tree.getConstructor(GenericStack.class, List.class),
                        recipe.getConstructor(List.class, List.class, Long.class)));
            } catch (ReflectiveOperationException incompatible) {
                throw new IllegalStateException("Unsupported AE2 Crafting Tree summary API", incompatible);
            }
        }
    };

    private CraftingTreeSummaryCompat() {}

    public static void attach(CraftingPlanSummary summary, GenericStack output, List<Recipe> recipes) {
        var access = ACCESS.get(summary.getClass()).orElse(null);
        if (access == null) return;
        try {
            // Never replace a payload already populated by another summary adapter.
            if (access.get().invoke(summary) != null) return;
            var treeRecipes = new ArrayList<>(recipes.size());
            for (var recipe : recipes) {
                treeRecipes.add(access.recipe().newInstance(recipe.inputs(), recipe.outputs(), recipe.times()));
            }
            access.set().invoke(summary, access.tree().newInstance(output, treeRecipes));
        } catch (ReflectiveOperationException incompatible) {
            throw new IllegalStateException("Could not initialize AE2 Crafting Tree exact-plan summary", incompatible);
        }
    }

    /** The optional tree protocol uses long values; these projections are never used for execution. */
    public record Recipe(List<GenericStack> inputs, List<GenericStack> outputs, long times) {
        public Recipe {
            inputs = List.copyOf(inputs);
            outputs = List.copyOf(outputs);
            if (times <= 0) throw new IllegalArgumentException("Recipe execution count must be positive");
        }
    }

    private record Access(Method get, Method set, Constructor<?> tree, Constructor<?> recipe) {}
}
