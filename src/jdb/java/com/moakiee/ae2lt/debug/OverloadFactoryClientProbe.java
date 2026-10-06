package com.moakiee.ae2lt.debug;

import java.nio.file.Files;

import com.moakiee.ae2lt.integration.jei.category.OverloadProcessingCategory;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingRecipeCatalog;
import dev.emi.emi.api.EmiApi;
import mezz.jei.common.Internal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.pedroksl.advanced_ae.recipes.ReactionChamberRecipe;
import net.pedroksl.advanced_ae.xmod.emi.recipes.EMIReactionChamberRecipe;
import net.pedroksl.advanced_ae.xmod.jei.ReactionChamberCategory;

/** Opt-in verification in a disposable world; never shipped. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class OverloadFactoryClientProbe {
    private static int ticks;
    private static int screenshotDelay;
    private static int levelTicks;
    private static boolean done;
    private static String screenshotPrefix = "reaction-category-";

    private static String viewer() {
        return ModList.get().isLoaded("emi") ? "emi" : "jei";
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("ae2lt.overloadFactoryClientProbe") || done) return;
        var mc = Minecraft.getInstance();
        try {
            if (++ticks > 6000) throw new AssertionError("recipe viewers did not finish loading");
            if (screenshotDelay > 0) {
                if (--screenshotDelay == 0) {
                    try (var pixels = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                        pixels.writeToFile(mc.gameDirectory.toPath().resolve(screenshotPrefix + viewer() + ".png"));
                    }
                    done = true;
                    mc.stop();
                }
                return;
            }
            if (mc.level == null || mc.player == null) return;
            if (++levelTicks < 100) return;
            var nativeRecipes = OverloadProcessingRecipeCatalog.displayRecipes(mc.level.getRecipeManager());
            int reactionCount = mc.level.getRecipeManager().getAllRecipesFor(ReactionChamberRecipe.TYPE).size();
            if (ModList.get().isLoaded("emi")) {
                EmiProbe.check(mc, nativeRecipes.size(), reactionCount);
            } else {
                if (!checkJei(mc, reactionCount)) return;
            }
            Files.writeString(mc.gameDirectory.toPath().resolve("viewer-result-" + viewer() + ".txt"),
                    "PASS: " + viewer() + " factory workstation reuses the original reaction category; "
                            + reactionCount + " upstream recipes; " + nativeRecipes.size()
                            + " native or derived factory recipes; no reaction recipe copies.");
            screenshotDelay = 40;
        } catch (Throwable failure) {
            failure.printStackTrace();
            try { Files.writeString(mc.gameDirectory.toPath().resolve("viewer-result-" + viewer() + ".txt"), "FAIL: " + failure); }
            catch (Exception ignored) { }
            done = true;
            mc.stop();
        }
    }

    private static boolean checkJei(Minecraft mc, int reactionCount) {
        mezz.jei.api.runtime.IJeiRuntime runtime;
        try {
            runtime = Internal.getJeiRuntime();
        } catch (IllegalStateException notReady) {
            return false;
        }
        var jei = runtime.getRecipeManager();
        boolean jeiFactory = jei.createRecipeCatalystLookup(ReactionChamberCategory.RECIPE_TYPE).includeHidden()
                .getItemStack().anyMatch(stack -> stack.is(ModBlocks.OVERLOAD_PROCESSING_FACTORY.asItem()));
        check(jeiFactory, "JEI reaction category has no overload factory catalyst");
        var nativeRecipes = OverloadProcessingRecipeCatalog.displayRecipes(mc.level.getRecipeManager());
        var visibleNative = jei.createRecipeLookup(OverloadProcessingCategory.TYPE).includeHidden().get().toList();
        check(visibleNative.size() == nativeRecipes.size(), "JEI factory category contains extra recipes");
        check(visibleNative.stream().allMatch(recipe -> nativeRecipes.stream().anyMatch(holder -> holder.value() == recipe)),
                "reaction execution view leaked into JEI factory category");
        long jeiCount = jei.createRecipeLookup(ReactionChamberCategory.RECIPE_TYPE).includeHidden().get().count();
        check(jeiCount == reactionCount, "JEI reactions were duplicated or lost");
        var fixture = fixture(mc);
        if (fixture != null) {
            screenshotPrefix = "factory-inscriber-";
            checkFixture(fixture.value());
            check(visibleNative.contains(fixture.value()), "JEI omitted the derived wrapper");
            runtime.getRecipesGui().showRecipes(jei.getRecipeCategory(OverloadProcessingCategory.TYPE),
                    java.util.List.of(fixture.value()), java.util.List.of());
        } else {
            runtime.getRecipesGui().showTypes(java.util.List.of(ReactionChamberCategory.RECIPE_TYPE));
        }
        return true;
    }

    private static final class EmiProbe {
        static void check(Minecraft mc, int nativeCount, int reactionCount) {
            var emi = EmiApi.getRecipeManager();
            boolean factory = emi.getWorkstations(EMIReactionChamberRecipe.CATEGORY).stream()
                    .flatMap(ingredient -> ingredient.getEmiStacks().stream())
                    .anyMatch(stack -> stack.getItemStack().is(ModBlocks.OVERLOAD_PROCESSING_FACTORY.asItem()));
            OverloadFactoryClientProbe.check(factory, "EMI reaction category has no overload factory workstation");
            var ownCategory = emi.getCategories().stream()
                    .filter(category -> category.id.toString().equals("ae2lt:overload_processing")).findFirst().orElseThrow();
            OverloadFactoryClientProbe.check(emi.getRecipes(ownCategory).size() == nativeCount, "EMI factory category contains extra recipes");
            OverloadFactoryClientProbe.check(emi.getRecipes(EMIReactionChamberRecipe.CATEGORY).size() == reactionCount, "EMI reactions were duplicated or lost");
            var fixture = fixture(mc);
            if (fixture != null) {
                screenshotPrefix = "factory-inscriber-";
                checkFixture(fixture.value());
                var wrapped = emi.getRecipe(fixture.id());
                OverloadFactoryClientProbe.check(wrapped != null && wrapped.getCategory() == ownCategory, "EMI omitted the derived wrapper");
                EmiApi.displayRecipe(wrapped);
            } else {
                EmiApi.displayRecipeCategory(EMIReactionChamberRecipe.CATEGORY);
            }
        }

    }

    private static net.minecraft.world.item.crafting.RecipeHolder<com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingRecipe>
            fixture(Minecraft mc) {
        var routes = OverloadProcessingRecipeCatalog.displayRecipes(mc.level.getRecipeManager()).stream()
                .filter(h -> h.id().getPath().startsWith("derived/inscriber/ae2lt_overload/inscriber/processor_fixture/"))
                .toList();
        check(routes.size() <= 1, "compressed fixture has duplicate loose-item routes");
        return routes.isEmpty() ? null : routes.getFirst();
    }

    private static void checkFixture(com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingRecipe recipe) {
        long expectedEnergy = Boolean.getBoolean("ae2lt.inscriberScriptTest") ? 123456 : 400000;
        check(recipe.totalEnergy() == expectedEnergy, "client did not receive the final wrapper energy");
        check(recipe.itemInputs().size() == 3 && recipe.itemInputs().stream().allMatch(input -> input.count() == 4),
                "client did not receive the three compressed input routes");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
