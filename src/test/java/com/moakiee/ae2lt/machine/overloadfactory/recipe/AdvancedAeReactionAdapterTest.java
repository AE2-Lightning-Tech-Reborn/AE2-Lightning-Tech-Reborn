package com.moakiee.ae2lt.machine.overloadfactory.recipe;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStackTemplate;
import net.neoforged.neoforge.fluids.FluidStackTemplate;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.me.key.LightningKey;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.pedroksl.advanced_ae.recipes.ReactionChamberRecipe;
import net.pedroksl.ae2addonlib.recipes.IngredientStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AdvancedAeReactionAdapterTest extends com.moakiee.ae2lt.test.MinecraftComponentsTestBase {
    @BeforeAll
    static void bootstrap() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void keepsSourceIdentityCountsComponentsAndEnergyButChargesPerOperation() {
        var ingredient = Ingredient.of(Items.GOLD_INGOT, Items.IRON_INGOT);
        var output = new ItemStack(Items.DIAMOND, 64);
        output.set(DataComponents.CUSTOM_NAME, Component.literal("Source output"));
        var source = holder("third_party:custom", reaction(
                new GenericStack(AEItemKey.of(output), 64),
                List.of(new IngredientStack.Item(ingredient, 32)), null, 123456));
        var converted = AdvancedAeReactionAdapter.convert(source);
        var recipe = converted.value();
        assertEquals(source.id(), converted.id());
        assertSame(ingredient, recipe.itemInputs().getFirst().ingredient());
        assertEquals(32, recipe.itemInputs().getFirst().count());
        assertEquals(123456, recipe.totalEnergy());
        assertEquals(1, recipe.lightningCost());
        assertEquals(LightningKey.Tier.HIGH_VOLTAGE, recipe.lightningTier());
        assertEquals(64, recipe.itemResults().getFirst().getCount());
        assertEquals(output.get(DataComponents.CUSTOM_NAME),
                recipe.itemResults().getFirst().get(DataComponents.CUSTOM_NAME));
        assertEquals(3, OverloadProcessingRecipeService.maxLightningParallel(
                recipe.lightningTier(), recipe.lightningCost(), false, 3, 0));
        var input = new OverloadProcessingRecipeInput(
                List.of(new OverloadProcessingRecipeInput.SlotStack(0, new ItemStack(Items.IRON_INGOT, 96))),
                FluidStack.EMPTY);
        assertEquals(96, recipe.planMatch(input, 3).orElseThrow().getConsumptionForSlot(0));
        assertEquals(192, recipe.getScaledItemResults(3).getFirst().getCount());
        // The view never replaced or changed the original recipe.
        assertEquals(123456, source.value().getEnergy());
        assertEquals(64, source.value().getResultItem().getCount());
    }

    @Test
    void keepsItemTagsInsteadOfResolvingOneExampleStack() {
        var ingredient = Ingredient.of(HolderSet.emptyNamed(BuiltInRegistries.ITEM, ItemTags.LOGS));
        var source = itemRecipe(ingredient, 2, 1, 100);
        assertSame(ingredient, AdvancedAeReactionAdapter.convert(source).value().itemInputs().getFirst().ingredient());
    }

    @Test
    void fluidAlternativesAndParallelAmountAreBothRequired() {
        var source = reaction(new GenericStack(AEItemKey.of(Items.DIAMOND), 1),
                List.of(new IngredientStack.Item(Ingredient.of(Items.IRON_INGOT), 1)),
                new IngredientStack.Fluid(FluidIngredient.of(Fluids.WATER, Fluids.LAVA), 500), 200);
        var recipe = AdvancedAeReactionAdapter.convert(holder("test:fluid", source)).value();
        assertEquals(500, recipe.inputFluidAmount());
        assertTrue(recipe.hasRequiredFluid(new FluidStack(Fluids.WATER, 1000), 2));
        assertTrue(recipe.hasRequiredFluid(new FluidStack(Fluids.LAVA, 1000), 2));
        assertFalse(recipe.hasRequiredFluid(new FluidStack(Fluids.LAVA, 999), 2));
        assertFalse(recipe.hasRequiredFluid(FluidStack.EMPTY, 1));
        assertFalse(recipe.hasRequiredFluid(new FluidStack(Fluids.WATER, Integer.MAX_VALUE), Integer.MAX_VALUE));
    }

    @Test
    void keepsFluidOutputAndAcceptsFluidOnlyInput() {
        var source = reaction(new GenericStack(AEFluidKey.of(Fluids.LAVA), 1000),
                List.of(), new IngredientStack.Fluid(FluidIngredient.of(Fluids.WATER), 4000), 20000);
        var recipe = AdvancedAeReactionAdapter.convert(holder("test:fluid_output", source)).value();
        assertTrue(recipe.itemResults().isEmpty());
        assertFalse(recipe.isIncomplete());
        assertEquals(2000, recipe.getScaledFluidResult(2).getAmount());
        assertEquals(Fluids.LAVA, recipe.getScaledFluidResult(2).getFluid());
        assertTrue(recipe.matches(new OverloadProcessingRecipeInput(List.of(), new FluidStack(Fluids.WATER, 4000)), null));
    }

    @Test
    void reloadWithSameIdRebuildsViewAndRemovalLeavesNoFallbackCopy() {
        var manager = new TestRecipeManager();
        var original = itemRecipe(Ingredient.of(Items.IRON_INGOT), 1, 1, 100);
        manager.replaceRecipes(List.of(original));
        var first = catalog(manager);
        assertSame(first, catalog(manager));

        var changed = itemRecipe(Ingredient.of(Items.GOLD_INGOT), 3, 7, 900);
        manager.replaceRecipes(List.of(changed));
        var reloaded = catalog(manager);
        assertNotSame(first, reloaded);
        assertEquals(900, reloaded.getFirst().value().totalEnergy());
        assertEquals(7, reloaded.getFirst().value().itemResults().getFirst().getCount());
        assertEquals(3, reloaded.getFirst().value().itemInputs().getFirst().count());
        assertFalse(reloaded.getFirst().value().itemInputs().getFirst().ingredient().test(new ItemStack(Items.IRON_INGOT)));
        assertSame(changed.value(), manager.byKey(changed.id()).orElseThrow().value());

        manager.replaceRecipes(List.of());
        assertTrue(catalog(manager).isEmpty());
    }

    @Test
    void managersStayIndependentAndNativeRecipesWorkWithoutReactionSources() {
        var one = new TestRecipeManager();
        var two = new TestRecipeManager();
        var recipe = itemRecipe(Ingredient.of(Items.IRON_INGOT), 1, 1, 100);
        one.replaceRecipes(List.of(recipe));
        var first = catalog(one);
        assertTrue(catalog(two).isEmpty());
        assertSame(first, catalog(one));

        var nativeRecipe = new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, Identifier.parse("ae2lt:test")),
                new OverloadProcessingRecipe(0, List.of(new OverloadProcessingIngredient(Ingredient.of(Items.GOLD_INGOT), 1)),
                        FluidStack.EMPTY, List.of(new ItemStack(Items.DIAMOND)), FluidStack.EMPTY,
                        200, 8, LightningKey.Tier.EXTREME_HIGH_VOLTAGE));
        var nativeOnly = OverloadProcessingRecipeCatalog.recipes(two, List.of(nativeRecipe), List.of());
        assertEquals(List.of(nativeRecipe), nativeOnly);
        assertEquals(8, nativeOnly.getFirst().value().lightningCost());
    }

    @Test
    void unsupportedRecipeIsSkippedWithoutHidingOtherRecipes() {
        var invalid = holder("test:too_many_inputs", reaction(
                new GenericStack(AEItemKey.of(Items.DIAMOND), 1),
                Collections.nCopies(10, new IngredientStack.Item(Ingredient.of(Items.IRON_INGOT), 1)), null, 100));
        var valid = itemRecipe(Ingredient.of(Items.IRON_INGOT), 1, 1, 100);
        var manager = new TestRecipeManager();
        manager.replaceRecipes(List.of(invalid, valid));
        assertEquals(List.of(valid.id()), catalog(manager).stream().map(RecipeHolder::id).toList());
    }

    private static ReactionChamberRecipe reaction(GenericStack output, List<IngredientStack.Item> inputs,
            IngredientStack.Fluid fluid, int energy) {
        ItemStackTemplate item = output.what() instanceof AEItemKey key
                ? ItemStackTemplate.fromNonEmptyStack(key.toStack((int) output.amount())) : null;
        FluidStackTemplate resultFluid = output.what() instanceof AEFluidKey key
                ? FluidStackTemplate.fromNonEmptyStack(key.toStack((int) output.amount())) : null;
        return new ReactionChamberRecipe(item, resultFluid, inputs, fluid, energy);
    }

    private static List<RecipeHolder<OverloadProcessingRecipe>> catalog(RecipeManager manager) {
        return OverloadProcessingRecipeCatalog.recipes(manager, List.of(), AdvancedAeReactionAdapter.sourceRecipes(manager));
    }

    private static RecipeHolder<ReactionChamberRecipe> itemRecipe(Ingredient ingredient, int count, int output, int energy) {
        return holder("test:recipe", reaction(new GenericStack(AEItemKey.of(Items.DIAMOND), output),
                List.of(new IngredientStack.Item(ingredient, count)), null, energy));
    }

    private static RecipeHolder<ReactionChamberRecipe> holder(String id, ReactionChamberRecipe recipe) {
        return new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, Identifier.parse(id)), recipe);
    }
}
