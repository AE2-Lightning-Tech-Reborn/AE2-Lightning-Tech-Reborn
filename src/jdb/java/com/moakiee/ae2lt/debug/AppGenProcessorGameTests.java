package com.moakiee.ae2lt.debug;

import appeng.recipes.handlers.InscriberRecipe;
import com.google.gson.JsonParser;
import com.moakiee.ae2lt.machine.overloadfactory.OverloadProcessingFactoryInventory;
import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingRecipe;
import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingRecipeInput;
import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingRecipeService;
import com.moakiee.ae2lt.me.key.LightningKey;
import io.netty.buffer.Unpooled;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;

/** Uses the actual optional mod's items and recipe manager, not mock registry entries. */


public final class AppGenProcessorGameTests {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static final Identifier BULK = Identifier.parse("ae2lt:overload_processing/appgen_origination_processor");

    public static void originationProcessorLoadsMatchesAndSyncsOnlyWithAppGen(GameTestHelper helper) {
        var level = helper.getLevel();
        var manager = level.recipeAccess();
        var bulk = manager.byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, BULK));
        if (!ModList.get().isLoaded("appgen")) {
            check(bulk.isEmpty(), "AppGen recipe must be excluded when the optional mod is absent");
            helper.succeed();
            return;
        }
        var ember = item("appgen:ember_crystal");
        var emberBlock = item("appgen:ember_block");
        var printed = item("appgen:printed_origination_processor");
        var processor = item("appgen:origination_processor");
        var compression = (ShapedRecipe) manager.byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, Identifier.parse("appgen:crafting/ember_block")))
                .orElseThrow().value();
        check(compression.getWidth() == 2 && compression.getHeight() == 2
                        && compression.getIngredients().stream().allMatch(i -> i.orElseThrow().test(ember))
                        && compression.assemble(net.minecraft.world.item.crafting.CraftingInput.EMPTY).is(emberBlock.getItem()),
                "actual AppGen compression is four crystals per ember block");
        var printing = (InscriberRecipe) manager.byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, Identifier.parse("appgen:inscriber/printed_origination_processor")))
                .orElseThrow().value();
        var finishing = (InscriberRecipe) manager.byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, Identifier.parse("appgen:inscriber/origination_processor")))
                .orElseThrow().value();
        check(printing.getMiddleInput().test(ember) && printing.result().create().is(printed.getItem())
                        && finishing.getTopOptional().orElseThrow().test(printed) && finishing.result().create().is(processor.getItem()),
                "bulk output follows the real inscriber processor chain");
        check(bulk.isPresent() && bulk.get().value() instanceof OverloadProcessingRecipe, "conditional bulk recipe loaded");
        var recipe = (OverloadProcessingRecipe) bulk.orElseThrow().value();
        check(recipe.itemInputs().size() == 3 && recipe.itemInputs().get(0).count() == 9
                        && recipe.itemInputs().get(1).count() == 4 && recipe.itemInputs().get(2).count() == 4,
                "36 processors require 9 ember blocks, 4 redstone blocks, and 4 silicon blocks");
        check(recipe.totalEnergy() == 400_000 && recipe.lightningCost() == 1
                        && recipe.lightningTier() == LightningKey.Tier.HIGH_VOLTAGE,
                "400000 FE and 1 high-voltage lightning match other bulk processors");
        check(recipe.itemResults().size() == 1 && recipe.itemResults().getFirst().is(processor.getItem())
                        && recipe.itemResults().getFirst().getCount() == 36, "exactly 36 actual AppGen processors");
        var siliconOptions = recipe.itemInputs().get(2).ingredient().items().map(ItemStack::new).toArray(ItemStack[]::new);
        check(siliconOptions.length > 0, "silicon block tag resolves with Applied Flux present");
        var inventory = new OverloadProcessingFactoryInventory(null);
        inventory.setStackInSlot(0, emberBlock.copyWithCount(9));
        inventory.setStackInSlot(1, new ItemStack(Items.REDSTONE_BLOCK, 4));
        inventory.setStackInSlot(2, siliconOptions[0].copyWithCount(4));
        var input = OverloadProcessingRecipeInput.fromInventory(inventory, FluidStack.EMPTY);
        check(recipe.matches(input, level), "real tagged inputs match");
        var candidate = OverloadProcessingRecipeService.findFirstProcessable(level, inventory,
                FluidStack.EMPTY, FluidStack.EMPTY, 1, 0);
        check(candidate.isPresent() && candidate.get().recipe().id().identifier().equals(BULK)
                        && candidate.get().parallel() == 1, "factory recipe selection accepts the new processor");
        check(OverloadProcessingRecipeService.findFirstProcessable(level, inventory,
                FluidStack.EMPTY, FluidStack.EMPTY, 0, 0).isEmpty(), "lightning requirement is retained");
        inventory.setStackInSlot(0, emberBlock.copyWithCount(8));
        check(OverloadProcessingRecipeService.findFirstProcessable(level, inventory,
                FluidStack.EMPTY, FluidStack.EMPTY, 1, 0).isEmpty(), "eight ember blocks are insufficient");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        try {
            var codec = OverloadProcessingRecipe.Serializer.INSTANCE.streamCodec();
            codec.encode(buffer, recipe);
            var copy = codec.decode(buffer);
            check(copy.matches(input, level) && copy.totalEnergy() == 400_000
                            && copy.itemResults().getFirst().is(processor.getItem())
                            && copy.itemResults().getFirst().getCount() == 36,
                    "client synchronization preserves the optional recipe, ingredients, energy, and result");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    private static ItemStack item(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).orElseThrow());
    }

    public static void emberSynthesisPreservesUpstreamReaction(GameTestHelper helper) throws Exception {
        assertReactionRecipe(helper, "ember_crystal");
    }

    public static void emberDuplicationPreservesUpstreamReaction(GameTestHelper helper) throws Exception {
        assertReactionRecipe(helper, "ember_crystal_duplicate");
    }

    public static void emberChargingPreservesUpstreamReaction(GameTestHelper helper) throws Exception {
        assertReactionRecipe(helper, "charged_ember_crystal");
    }

    private static void assertReactionRecipe(GameTestHelper helper, String name) throws Exception {
        var level = helper.getLevel();
        var id = Identifier.parse("ae2lt:overload_processing/appgen_" + name);
        var holder = level.recipeAccess().byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, id));
        if (!ModList.get().isLoaded("appgen")) {
            check(holder.isEmpty(), name + " must be excluded without AppGen");
            helper.succeed();
            return;
        }
        check(holder.isPresent() && holder.get().value() instanceof OverloadProcessingRecipe,
                name + " must load as an overload recipe");
        var recipe = (OverloadProcessingRecipe) holder.orElseThrow().value();
        // Compare against the actual optional mod's resource, independently of our converted JSON.
        var source = level.getServer().getResourceManager().getResourceOrThrow(
                Identifier.parse("appgen:recipe/reaction/" + name + ".json"));
        try (var reader = source.openAsReader()) {
            var upstream = JsonParser.parseReader(reader).getAsJsonObject();
            var inputs = upstream.getAsJsonArray("input_items");
            check(recipe.itemInputs().size() == inputs.size(), "upstream ingredient count retained");
            var inventory = new OverloadProcessingFactoryInventory(null);
            for (int slot = 0; slot < inputs.size(); slot++) {
                var entry = inputs.get(slot).getAsJsonObject();
                var stack = item(entry.get("ingredient").getAsString())
                        .copyWithCount(entry.get("amount").getAsInt());
                var converted = recipe.itemInputs().get(slot);
                check(converted.count() == stack.getCount() && converted.ingredient().test(stack),
                        "upstream item and amount retained in slot " + slot);
                inventory.setStackInSlot(slot, stack);
            }
            var upstreamFluid = upstream.getAsJsonObject("input_fluid");
            var fluid = new FluidStack(BuiltInRegistries.FLUID.getOptional(Identifier.parse(
                    upstreamFluid.get("ingredient").getAsString())).orElseThrow(),
                    upstreamFluid.get("amount").getAsInt());
            check(FluidStack.matches(recipe.fluidInput(), fluid), "upstream lava amount retained");
            var output = upstream.getAsJsonObject("itemOutput");
            var result = item(output.get("id").getAsString()).copyWithCount(output.get("count").getAsInt());
            check(recipe.itemResults().size() == 1
                            && ItemStack.matches(recipe.itemResults().getFirst(), result) && recipe.fluidResult().isEmpty(),
                    "upstream output and yield retained");
            check(recipe.totalEnergy() == upstream.get("input_energy").getAsLong()
                            && recipe.lightningCost() == 1 && recipe.lightningTier() == LightningKey.Tier.HIGH_VOLTAGE,
                    "upstream FE retained with the standard one high-voltage lightning cost");
            var candidate = OverloadProcessingRecipeService.findFirstProcessable(level, inventory,
                    fluid, FluidStack.EMPTY, 1, 0);
            check(candidate.isPresent() && candidate.get().recipe().id().identifier().equals(id)
                            && candidate.get().parallel() == 1, "factory selects the reaction with actual registered inputs");
            check(OverloadProcessingRecipeService.findFirstProcessable(level, inventory,
                    fluid, FluidStack.EMPTY, 0, 0).isEmpty(), "lightning remains required");
            check(OverloadProcessingRecipeService.findFirstProcessable(level, inventory,
                    new FluidStack(Fluids.WATER, fluid.getAmount()), FluidStack.EMPTY, 1, 0).isEmpty(),
                    "water cannot replace lava");
            check(OverloadProcessingRecipeService.findFirstProcessable(level, inventory,
                    fluid.copyWithAmount(fluid.getAmount() - 1), FluidStack.EMPTY, 1, 0).isEmpty(),
                    "insufficient lava cannot start a reaction");
            var input = OverloadProcessingRecipeInput.fromInventory(inventory, fluid);
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
            try {
                var codec = OverloadProcessingRecipe.Serializer.INSTANCE.streamCodec();
                codec.encode(buffer, recipe);
                var copy = codec.decode(buffer);
                check(copy.matches(input, level) && FluidStack.matches(copy.fluidInput(), fluid)
                                && ItemStack.matches(copy.itemResults().getFirst(), result)
                                && copy.totalEnergy() == recipe.totalEnergy()
                                && copy.lightningCost() == 1 && copy.lightningTier() == recipe.lightningTier(),
                        "client synchronization retains ingredients, lava, yield, FE, and lightning");
            } finally {
                buffer.release();
            }
            var first = inventory.getStackInSlot(0);
            inventory.setStackInSlot(0, first.copyWithCount(first.getCount() - 1));
            check(OverloadProcessingRecipeService.findFirstProcessable(level, inventory,
                    fluid, FluidStack.EMPTY, 1, 0).isEmpty(), "insufficient items cannot start a reaction");
        }
        helper.succeed();
    }
}
