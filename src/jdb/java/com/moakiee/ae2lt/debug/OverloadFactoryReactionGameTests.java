package com.moakiee.ae2lt.debug;

import java.util.ArrayList;
import java.util.List;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.StorageCells;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.blockentity.OverloadProcessingFactoryBlockEntity;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.machine.overloadfactory.OverloadProcessingFactoryInventory;
import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingLockedRecipe;
import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingRecipeCatalog;
import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingRecipeService;
import com.moakiee.ae2lt.registry.ModItems;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModRecipeTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.pedroksl.advanced_ae.recipes.ReactionChamberRecipe;
import net.pedroksl.ae2addonlib.recipes.IngredientStack;


public final class OverloadFactoryReactionGameTests {

    public static void completesDerivedProcessorBatchWithOriginalPrintingAndBlockRecipes(GameTestHelper helper) {
        var manager = com.moakiee.ae2lt.debug.NativeSyncTestAccess.manager(helper.getLevel());
        var sourceId = Identifier.parse("ae2lt_overload:inscriber/processor_fixture");
        helper.assertTrue(manager.byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, sourceId)).isPresent(), "enable -Pae2ltInscriberFixture=true for this isolated test");
        helper.assertTrue(manager.byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, Identifier.parse("ae2lt_overload:inscriber/unrelated_fixture"))).isPresent(),
                "unrelated three-input fixture was not loaded");
        helper.assertTrue(OverloadProcessingRecipeCatalog.recipes(manager).stream().noneMatch(h ->
                h.value().itemResults().stream().anyMatch(s -> s.is(Items.RECOVERY_COMPASS))),
                "a three-input recipe without processor in its ID must not be wrapped");
        var derived = OverloadProcessingRecipeCatalog.recipes(manager).stream()
                .filter(h -> h.id().identifier().getPath().startsWith("derived/inscriber/")
                        && h.value().itemResults().getFirst().is(Items.CLOCK)).findFirst().orElseThrow();
        helper.assertTrue(manager.byKey(derived.id()).isPresent(), "pre-script derived recipe was not loaded");
        var recipe = derived.value();
        helper.assertTrue(recipe.itemInputs().stream().allMatch(i -> i.count() == 4), "real 3x3 blocks should use four each: " + describe(recipe));
        helper.assertTrue(recipe.itemInputs().getFirst().ingredient().test(new ItemStack(Items.GOLD_BLOCK)), "logic print did not resolve to gold block");
        helper.assertTrue(recipe.itemInputs().get(1).ingredient().test(new ItemStack(Items.EMERALD_BLOCK)), "emerald did not resolve to block");
        var pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ModBlocks.OVERLOAD_PROCESSING_FACTORY.get());
        OverloadProcessingFactoryBlockEntity host = (OverloadProcessingFactoryBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        helper.setBlock(pos.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(pos.east(), AEBlocks.DRIVE.block());
        var cell = new ItemStack(ModItems.LIGHTNING_STORAGE_COMPONENT_I.get());
        var storage = StorageCells.getCellInventory(cell, null);
        helper.assertTrue(storage != null && storage.insert(LightningKey.HIGH_VOLTAGE, 2, Actionable.MODULATE,
                IActionSource.ofMachine(host)) == 2, "could not seed lightning");
        storage.persist();
        DriveBlockEntity drive = (DriveBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(pos.east()));
        drive.getInternalInventory().setItemDirect(0, cell);
        helper.runAfterDelay(20, () -> {
            for (int i = 0; i < recipe.itemInputs().size(); i++) {
                var input = recipe.itemInputs().get(i);
                host.getInventory().setStackInSlot(i, new ItemStack(input.ingredient().items().findFirst().orElseThrow(), input.count()));
            }
            host.getEnergyStorage().receiveEnergy((int) recipe.totalEnergy(), false);
        });
        helper.succeedWhen(() -> {
            var output = host.getInventory().getStackInSlot(OverloadProcessingFactoryInventory.SLOT_OUTPUT_0);
            helper.assertTrue(output.is(Items.CLOCK) && output.getCount() == 36, "waiting for 36 derived outputs");
            for (int i = 0; i < 3; i++) helper.assertTrue(host.getInventory().getStackInSlot(i).isEmpty(), "derived input not consumed exactly once");
            helper.assertTrue(host.getAvailableHighVoltage() == 1, "expected one high-voltage lightning per bulk batch");
            helper.assertTrue(host.getEnergyStorage().getStoredEnergyLong() == 0, "expected the final recipe FE per bulk batch");
        });
    }

    public static void derivesRealAppGenProcessorWhenManualCompatibilityIsMissing(GameTestHelper helper) {
        if (!net.neoforged.fml.ModList.get().isLoaded("appgen")) { helper.succeed(); return; }
        var manager = new net.minecraft.world.item.crafting.RecipeManager(helper.getLevel().registryAccess());
        var original = com.moakiee.ae2lt.debug.NativeSyncTestAccess.manager(helper.getLevel()).getRecipes();
        var manual = Identifier.parse("ae2lt:overload_processing/appgen_origination_processor");
        var json = new java.util.HashMap<Identifier, com.google.gson.JsonElement>();
        var ops = helper.getLevel().registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        for (var holder : original) {
            var value = holder.value();
            if (holder.id().identifier().equals(manual)) continue;
            if (value.getClass() == appeng.recipes.handlers.InscriberRecipe.class
                    || value.getClass() == net.minecraft.world.item.crafting.ShapedRecipe.class
                    || value.getClass() == net.minecraft.world.item.crafting.ShapelessRecipe.class
                    || value instanceof com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingRecipe) {
                json.put(holder.id().identifier(), net.minecraft.world.item.crafting.Recipe.CODEC.encodeStart(ops, value).getOrThrow());
            }
        }
        var context = new net.neoforged.neoforge.common.conditions.ICondition.IContext() {
            @Override public <T> boolean isTagLoaded(net.minecraft.tags.TagKey<T> key) {
                return helper.getLevel().registryAccess().lookupOrThrow(key.registry()).get(key).isPresent();
            }
            @Override public <T> java.util.Collection<net.minecraft.core.Holder<T>> getTag(net.minecraft.tags.TagKey<T> key) {
                return helper.getLevel().registryAccess().lookupOrThrow(key.registry()).get(key)
                        .map(tag -> tag.stream().toList()).orElse(java.util.List.of());
            }
        };
        com.moakiee.ae2lt.machine.overloadfactory.recipe.InscriberProcessorRecipeLoader.addRecipes(json, ops, context);
        var loaded = new ArrayList<RecipeHolder<?>>();
        json.forEach((id, value) -> loaded.add(new RecipeHolder<>(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, id),
                net.minecraft.world.item.crafting.Recipe.CODEC.parse(ops, value).getOrThrow())));
        com.moakiee.ae2lt.debug.NativeSyncTestAccess.replaceRecipes(manager, loaded);
        var processor = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(Identifier.parse("appgen:origination_processor"));
        var block = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(Identifier.parse("appgen:ember_block"));
        var derived = OverloadProcessingRecipeCatalog.displayRecipes(manager).stream()
                .filter(h -> h.value().itemResults().stream().anyMatch(stack -> stack.is(processor))).findFirst().orElseThrow();
        var recipe = derived.value();
        helper.assertTrue(derived.id().identifier().getPath().startsWith("derived/inscriber/"), "missing AppGen recipe was not derived");
        helper.assertTrue(recipe.itemInputs().getFirst().ingredient().test(new ItemStack(block))
                        && recipe.itemInputs().getFirst().count() == 9
                        && recipe.itemInputs().get(1).count() == 4 && recipe.itemInputs().get(2).count() == 4,
                "real AppGen chain must derive 9 ember blocks, 4 redstone blocks and 4 silicon blocks: " + describe(recipe));
        helper.assertTrue(recipe.itemResults().getFirst().getCount() == 36, "wrong processor output quantity");
        helper.assertTrue(OverloadProcessingRecipeCatalog.find(manager, derived.id().identifier()).isPresent(), "derived ID cannot resume");
        // Script-like removal after generation must remain authoritative, even with its sources intact.
        com.moakiee.ae2lt.debug.NativeSyncTestAccess.replaceRecipes(manager, loaded.stream().filter(h -> !h.id().equals(derived.id())).toList());
        helper.assertTrue(OverloadProcessingRecipeCatalog.find(manager, derived.id().identifier()).isEmpty(), "deleted fallback was regenerated");
        helper.succeed();
    }

    private static String describe(com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingRecipe recipe) {
        return recipe.itemInputs().stream().map(i -> i.count() + " x " + i.ingredient().items().toList().toString()).toList().toString();
    }

    public static void kubeJsEditsRemainAuthoritativeAfterReload(GameTestHelper helper) {
        if (!Boolean.getBoolean("ae2lt.inscriberScriptTest")) { helper.succeed(); return; }
        assertScriptResults(helper);
        helper.runAfterDelay(80, () -> {
            var server = helper.getLevel().getServer();
            server.reloadResources(new ArrayList<>(server.getPackRepository().getSelectedIds())).whenComplete((unused, error) ->
                    server.execute(() -> {
                        if (error != null) { helper.fail("reload failed: " + error); return; }
                        assertScriptResults(helper);
                        helper.succeed();
                    }));
        });
    }

    private static void assertScriptResults(GameTestHelper helper) {
        var manager = com.moakiee.ae2lt.debug.NativeSyncTestAccess.manager(helper.getLevel());
        helper.assertTrue(manager.byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, Identifier.parse("ae2lt_overload:kjs_test_marker"))).isPresent(), "KJS fixture did not run");
        var catalog = OverloadProcessingRecipeCatalog.recipes(manager);
        var changed = catalog.stream().filter(h -> h.id().identifier().getPath().startsWith(
                "derived/inscriber/ae2lt_overload/inscriber/processor_fixture/")).findFirst().orElseThrow();
        helper.assertTrue(changed.value().totalEnergy() == 123456, "KJS energy edit was overwritten");
        helper.assertTrue(catalog.stream().noneMatch(h -> h.id().identifier().getPath().startsWith(
                "derived/inscriber/ae2lt_overload/inscriber/removed_processor_fixture/")), "deleted wrapper was regenerated");
        var calculation = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(Identifier.parse("ae2:calculation_processor"));
        helper.assertTrue(catalog.stream().noneMatch(h -> h.value().itemResults().stream().anyMatch(s -> s.is(calculation))),
                "deleted manual calculation processor recipe was replaced with a fallback");
    }

    public static void completesBorrowedFluidRecipeAndSettlesEveryResourceOnce(GameTestHelper helper) {
        var pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ModBlocks.OVERLOAD_PROCESSING_FACTORY.get());
        OverloadProcessingFactoryBlockEntity host = (OverloadProcessingFactoryBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        helper.setBlock(pos.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(pos.east(), AEBlocks.DRIVE.block());
        var cell = new ItemStack(ModItems.LIGHTNING_STORAGE_COMPONENT_I.get());
        var storage = StorageCells.getCellInventory(cell, null);
        helper.assertTrue(storage != null && storage.insert(LightningKey.HIGH_VOLTAGE, 8, Actionable.MODULATE,
                IActionSource.ofMachine(host)) == 8, "could not seed lightning storage");
        storage.persist();
        DriveBlockEntity drive = (DriveBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(pos.east()));
        drive.getInternalInventory().setItemDirect(0, cell);
        var source = (ReactionChamberRecipe) com.moakiee.ae2lt.debug.NativeSyncTestAccess.manager(helper.getLevel())
                .byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, Identifier.parse("advanced_ae:quantum_infusion"))).orElseThrow().value();
        helper.runAfterDelay(20, () -> {
            var input = source.getInputs().getFirst();
            host.getInventory().setStackInSlot(0, new ItemStack(input.getIngredient().items().findFirst().orElseThrow(), input.getAmount() * 2));
            host.getInventory().setStackInSlot(OverloadProcessingFactoryInventory.SLOT_MATRIX,
                    new ItemStack(ModItems.LIGHTNING_COLLAPSE_MATRIX.get()));
            host.getFluidHandlerCapability(null).fill(new FluidStack(Fluids.WATER, 8000), FluidAction.EXECUTE);
            host.getEnergyStorage().receiveEnergy((int) OverloadProcessingRecipeService.computeTotalEnergy(source.getEnergy(), 2), false);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getTick() > 20 && host.getOutputFluid().getAmount() == 2000, "waiting for fluid output");
            helper.assertTrue(host.getInputFluid().isEmpty() && host.getInventory().getStackInSlot(0).isEmpty(),
                    "inputs were not consumed exactly twice");
            helper.assertTrue(host.getAvailableHighVoltage() == 6, "lightning did not settle once per operation");
            helper.assertTrue(host.getEnergyStorage().getStoredEnergyLong() == 0, "parallel FE was not settled exactly");
            helper.assertTrue(!host.hasLockedRecipe(), "finished recipe remained locked");
        });
    }

    public static void borrowsEveryLoadedReactionWithoutRegisteringCopies(GameTestHelper helper) {
        var manager = com.moakiee.ae2lt.debug.NativeSyncTestAccess.manager(helper.getLevel());
        var reactions = com.moakiee.ae2lt.recipe.compat.LegacyRecipeAccess.recipesOfType(manager, net.pedroksl.advanced_ae.recipes.AAERecipeTypes.REACTION_CHAMBER);
        helper.assertTrue(!reactions.isEmpty(), "AdvancedAE reactions were not loaded");
        var catalog = OverloadProcessingRecipeCatalog.recipes(manager);
        helper.assertTrue(catalog.size() == reactions.size()
                + OverloadProcessingRecipeCatalog.displayRecipes(manager).size(),
                "factory did not borrow every loaded reaction");
        for (var source : reactions) {
            var converted = catalog.stream().filter(holder -> holder.id().equals(source.id())).findFirst().orElseThrow();
            helper.assertTrue(converted.value().totalEnergy() == source.value().getEnergy(), "source energy changed");
            helper.assertTrue(converted.value().lightningCost() == 1, "lightning is not charged per operation");
            helper.assertTrue(manager.byKey(source.id()).orElseThrow().value() == source.value(), "source was overwritten");
        }
        helper.assertTrue(manager.byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, Identifier.parse("ae2lt:overload_processing/aae_quantum_alloy"))).isEmpty(),
                "old duplicate recipe still exists");
        helper.succeed();
    }

    public static void selectsFluidOutputAndResumesUsingOriginalRecipeId(GameTestHelper helper) {
        var level = helper.getLevel();
        var sourceId = Identifier.parse("advanced_ae:quantum_infusion");
        var source = (ReactionChamberRecipe) com.moakiee.ae2lt.debug.NativeSyncTestAccess.manager(level).byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, sourceId)).orElseThrow().value();
        var inventory = new OverloadProcessingFactoryInventory(null);
        for (int i = 0; i < source.getInputs().size(); i++) {
            var input = source.getInputs().get(i);
            inventory.setStackInSlot(i, new ItemStack(input.getIngredient().items().findFirst().orElseThrow(), input.getAmount() * 2));
        }
        inventory.setStackInSlot(OverloadProcessingFactoryInventory.SLOT_MATRIX,
                new ItemStack(ModItems.LIGHTNING_COLLAPSE_MATRIX.get()));
        var water = new FluidStack(Fluids.WATER, source.getFluid().getAmount() * 2);
        var candidate = OverloadProcessingRecipeService.findFirstProcessable(level, inventory, water, FluidStack.EMPTY, 2, 0)
                .orElseThrow();
        helper.assertTrue(candidate.recipe().id().identifier().equals(sourceId), "factory selected a different recipe");
        helper.assertTrue(candidate.parallel() == 2 && candidate.totalLightningCost() == 2, "parallel lightning cost changed");
        helper.assertTrue(candidate.recipe().value().getScaledFluidResult(2).getAmount() == 2000, "fluid yield changed");
        var locked = OverloadProcessingLockedRecipe.fromCandidate(candidate);
        var restored = OverloadProcessingLockedRecipe.fromTag(locked.toTag(level.registryAccess()), level.registryAccess());
        helper.assertTrue(restored != null && restored.recipeId().equals(sourceId), "saved recipe lost its upstream ID");
        helper.assertTrue(OverloadProcessingRecipeService.findLockedRecipeMatch(level, inventory, water, FluidStack.EMPTY,
                restored, 2, 0).isPresent(), "borrowed recipe cannot resume");
        helper.assertTrue(OverloadProcessingRecipeService.findLockedRecipeMatch(level, inventory,
                water.copyWithAmount(water.getAmount() - 1), FluidStack.EMPTY, restored, 2, 0).isEmpty(),
                "locked recipe accepted insufficient fluid");
        helper.succeed();
    }

    public static void sourceReloadAndFluidTagsRemainAuthoritative(GameTestHelper helper) {
        var level = helper.getLevel();
        var manager = com.moakiee.ae2lt.debug.NativeSyncTestAccess.manager(level);
        var original = List.copyOf(manager.getRecipes());
        var id = Identifier.parse("third_party:overload_tag_probe");
        var inventory = new OverloadProcessingFactoryInventory(null);
        inventory.setStackInSlot(0, new ItemStack(Items.DRAGON_BREATH, 3));
        inventory.setStackInSlot(OverloadProcessingFactoryInventory.SLOT_MATRIX,
                new ItemStack(ModItems.LIGHTNING_COLLAPSE_MATRIX.get()));
        var flowingWater = new FluidStack(Fluids.FLOWING_WATER, 1500);
        try {
            var withSource = new ArrayList<>(original);
            withSource.add(new RecipeHolder<>(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, id), tagRecipe(1, 100)));
            com.moakiee.ae2lt.debug.NativeSyncTestAccess.replaceRecipes(manager, withSource);
            var candidate = OverloadProcessingRecipeService.findFirstProcessable(level, inventory,
                    flowingWater, FluidStack.EMPTY, 2, 0).orElseThrow();
            helper.assertTrue(candidate.recipe().id().identifier().equals(id) && candidate.parallel() == 2
                    && candidate.totalLightningCost() == 2, "tagged fluid or lightning limit was flattened");
            helper.assertTrue(OverloadProcessingRecipeService.findFirstProcessable(level, inventory,
                    new FluidStack(Fluids.LAVA, 1500), FluidStack.EMPTY, 2, 0).isEmpty(), "fluid tag accepted lava");
            var locked = OverloadProcessingLockedRecipe.fromCandidate(candidate);

            withSource.set(withSource.size() - 1, new RecipeHolder<>(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, id), tagRecipe(7, 900)));
            com.moakiee.ae2lt.debug.NativeSyncTestAccess.replaceRecipes(manager, withSource);
            var changed = OverloadProcessingRecipeService.findRecipeById(level, id).orElseThrow().value();
            helper.assertTrue(changed.totalEnergy() == 900 && changed.itemResults().getFirst().getCount() == 7,
                    "same-ID recipe replacement reused stale data");
            helper.assertTrue(OverloadProcessingRecipeService.findLockedRecipeMatch(level, inventory,
                    flowingWater, FluidStack.EMPTY, locked, 2, 0).isEmpty(), "old energy budget survived source edit");

            com.moakiee.ae2lt.debug.NativeSyncTestAccess.replaceRecipes(manager, original);
            helper.assertTrue(OverloadProcessingRecipeService.findRecipeById(level, id).isEmpty(), "deleted source survived in cache");
            helper.assertTrue(OverloadProcessingRecipeService.findFirstProcessable(level, inventory,
                    flowingWater, FluidStack.EMPTY, 2, 0).isEmpty(), "deleted source still executes");
        } finally {
            com.moakiee.ae2lt.debug.NativeSyncTestAccess.replaceRecipes(manager, original);
        }
        helper.succeed();
    }

    private static ReactionChamberRecipe tagRecipe(int output, int energy) {
        return new ReactionChamberRecipe(new net.minecraft.world.item.ItemStackTemplate(Items.EMERALD, output), null,
                List.of(new IngredientStack.Item(Ingredient.of(Items.DRAGON_BREATH), 1)),
                new IngredientStack.Fluid(FluidIngredient.of(net.minecraft.core.registries.BuiltInRegistries.FLUID.getOrThrow(FluidTags.WATER)), 500), energy);
    }
}
