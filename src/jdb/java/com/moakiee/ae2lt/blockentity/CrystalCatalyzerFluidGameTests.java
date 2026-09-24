package com.moakiee.ae2lt.blockentity;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.StorageCells;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.moakiee.ae2lt.machine.crystalcatalyzer.CrystalCatalyzerInventory;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.*;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

/** Native recipes, codecs, AE lightning storage, machine ticks and persistence. Not shipped. */


public final class CrystalCatalyzerFluidGameTests {
    private static final BlockPos POS = new BlockPos(2, 2, 2);
    private static final int CATALYST = CrystalCatalyzerInventory.SLOT_CATALYST;
    private static final int OUTPUT = CrystalCatalyzerInventory.SLOT_OUTPUT;
    private static final String WATER = "quartz_block";
    private record Route(String path, String mod, String fluid, String catalyst, String output) {}
    private static final List<Route> SPECIAL = List.of(
            new Route("fluxite_block", "oritech", "oritech:still_strange_matter",
                    "oritech:fluxite_block", "oritech:fluxite"),
            new Route("uranium_crystal", "oritech", "oritech:still_mineral_slurry",
                    "oritech:uranium_gem", "oritech:uranium_gem"),
            new Route("time_crystal_block", "justdirethings", "justdirethings:time_fluid_source",
                    "justdirethings:time_crystal_block", "justdirethings:time_crystal"));

    private static void replaceRecipes(net.minecraft.world.item.crafting.RecipeManager manager,
                                       java.util.Collection<RecipeHolder<?>> recipes) {
        try {
            var field = net.minecraft.world.item.crafting.RecipeManager.class.getDeclaredField("recipes");
            field.setAccessible(true);
            field.set(manager, net.minecraft.world.item.crafting.RecipeMap.create(recipes));
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new net.minecraft.gametest.framework.GameTestAssertException(net.minecraft.network.chat.Component.literal(message), 0);
    }

    private static Optional<CrystalCatalyzerRecipeCandidate> find(GameTestHelper h, String path) {
        return CrystalCatalyzerRecipeService.findRecipeById(h.getLevel(),
                Identifier.parse("ae2lt:crystal_catalyzer/" + path));
    }

    private static CrystalCatalyzerBlockEntity machine(GameTestHelper h, boolean pigmee) {
        h.setBlock(POS, pigmee ? ModBlocks.PIGMEE_CRYSTAL_CATALYZER.get() : ModBlocks.CRYSTAL_CATALYZER.get());
        return h.getBlockEntity(POS, CrystalCatalyzerBlockEntity.class);
    }

    private static CrystalCatalyzerBlockEntity powered(GameTestHelper h, long lightning) {
        var host = machine(h, false);
        h.setBlock(POS.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        h.setBlock(POS.east(), AEBlocks.DRIVE.block());
        var cell = new ItemStack(ModItems.LIGHTNING_STORAGE_COMPONENT_I.get());
        var storage = StorageCells.getCellInventory(cell, null);
        check(storage != null, "lightning cell is not registered");
        check(storage.insert(LightningKey.HIGH_VOLTAGE, lightning, Actionable.MODULATE,
                IActionSource.ofMachine(host)) == lightning, "could not seed lightning storage");
        storage.persist();
        DriveBlockEntity drive = h.getBlockEntity(POS.east(), DriveBlockEntity.class);
        drive.getInternalInventory().setItemDirect(0, cell);
        return host;
    }

    private static long lightning(CrystalCatalyzerBlockEntity host) {
        return host.getMainNode().getGrid().getStorageService().getInventory().extract(
                LightningKey.HIGH_VOLTAGE, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.ofMachine(host));
    }

    private static void supply(CrystalCatalyzerBlockEntity host, CrystalCatalyzerRecipe recipe) {
        host.getInventory().setItemDirect(CATALYST, recipe.catalyst().orElseThrow().items().map(ItemStack::new).toArray(ItemStack[]::new)[0].copyWithCount(256));
        host.getInventory().setItemDirect(CrystalCatalyzerInventory.SLOT_MATRIX,
                new ItemStack(ModItems.LIGHTNING_COLLAPSE_MATRIX.get()));
        check(host.getFluidHandlerCapability(Direction.UP).fill(recipe.fluidInput(), FluidAction.EXECUTE) == 1000,
                "pipe rejected the required fluid");
        host.getEnergyStorage().receiveEnergy(100_000, false);
    }

    public static void optionalRecipeDataAndCodecs(GameTestHelper h) {
        var serializer = CrystalCatalyzerRecipe.Serializer.INSTANCE;
        var ops = h.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var legacyJson = JsonParser.parseString("""
                {"catalyst":"ae2:quartz_block","catalystCount":1,
                 "output":{"id":"ae2:certus_quartz_crystal","count":1},
                 "energyPerCycle":100000,"lightningCost":1}
                """);
        var legacy = serializer.codec().codec().parse(ops, legacyJson).getOrThrow();
        check(legacy.isWaterRecipe() && legacy.fluidInput().getAmount() == 1000, "legacy JSON lost water default");
        var recipes = new ArrayList<CrystalCatalyzerRecipe>();
        recipes.add(legacy);
        for (var route : SPECIAL) {
            var candidate = find(h, route.path());
            check(candidate.isPresent() == ModList.get().isLoaded(route.mod()), "optional recipe boundary " + route.path());
            if (candidate.isEmpty()) continue;
            var recipe = candidate.get().recipe().value();
            check(BuiltInRegistries.FLUID.getKey(recipe.fluidInput().getFluid()).toString().equals(route.fluid()),
                    "wrong fluid " + route.path());
            check(recipe.fluidInput().getAmount() == 1000 && recipe.catalystCount() == 1
                    && recipe.getOutputTemplate().getCount() == 1 && recipe.energyPerCycle() == 100_000
                    && recipe.lightningCost() == 1 && recipe.lightningTier() == LightningKey.Tier.HIGH_VOLTAGE,
                    "per-cycle costs changed " + route.path());
            check(recipe.catalystMatches(new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(route.catalyst())))),
                    "wrong catalyst " + route.path());
            check(BuiltInRegistries.ITEM.getKey(recipe.getOutputTemplate().getItem()).toString().equals(route.output()),
                    "wrong output " + route.path());
            recipes.add(recipe);
        }
        for (var recipe : recipes) {
            var encoded = serializer.codec().codec().encodeStart(ops, recipe).getOrThrow();
            var decoded = serializer.codec().codec().parse(ops, encoded).getOrThrow();
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
            try {
                serializer.streamCodec().encode(buffer, decoded);
                var wire = serializer.streamCodec().decode(buffer);
                check(FluidStack.matches(wire.fluidInput(), recipe.fluidInput()), "recipe sync changed fluid");
            } finally { buffer.release(); }
            var lock = new CrystalCatalyzerLockedRecipe(Identifier.parse("ae2lt:test"),
                    recipe.getOutputTemplate(), recipe.energyPerCycle(), 1024, 1,
                    LightningKey.Tier.HIGH_VOLTAGE, recipe.fluidInput());
            var saved = lock.toTag(h.getLevel().registryAccess());
            var restored = CrystalCatalyzerLockedRecipe.fromTag(saved, h.getLevel().registryAccess());
            check(restored != null && restored.matchesFluidInput(recipe) && restored.outputMultiplier() == 1024,
                    "locked fluid or output multiplier lost on save");
            var copy = restored.fluidInput();
            copy.shrink(999);
            check(restored.fluidInput().getAmount() == 1000, "fluid accessor leaked mutable snapshot");
            saved.remove("InputFluid");
            check(CrystalCatalyzerLockedRecipe.fromTag(saved, h.getLevel().registryAccess()).matchesFluidInput(legacy),
                    "legacy in-flight NBT lost default water");
            saved.put("InputFluid", new CompoundTag());
            check(CrystalCatalyzerLockedRecipe.fromTag(saved, h.getLevel().registryAccess()) == null,
                    "invalid saved fluid was silently changed to water");
        }
        h.succeed();
    }

    public static void sameCatalystDifferentFluidsSelectCorrectOutput(GameTestHelper h) {
        var host = machine(h, false);
        var water = find(h, WATER).orElseThrow().recipe().value();
        var lava = new CrystalCatalyzerRecipe(water.catalyst(), 1,
                CrystalCatalyzerOutput.ofItem(new ItemStack(net.minecraft.world.item.Items.DIAMOND)),
                100_000, 1, LightningKey.Tier.HIGH_VOLTAGE, Mode.CRYSTAL, new FluidStack(Fluids.LAVA, 1000));
        var manager = h.getLevel().recipeAccess();
        var original = new ArrayList<>(manager.getRecipes());
        var modified = new ArrayList<>(original);
        var lavaId = Identifier.parse("ae2lt_catalyzer:test_lava");
        modified.add(new RecipeHolder<>(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, lavaId), lava));
        try {
            replaceRecipes(manager, modified);
            host.getInventory().setItemDirect(CATALYST, AEBlocks.QUARTZ_BLOCK.stack());
            host.getTank().setFluid(new FluidStack(Fluids.LAVA, 1000));
            check(host.findProcessableRecipe().orElseThrow().recipe().id().identifier().equals(lavaId), "lava selected water recipe");
            host.getTank().setFluid(new FluidStack(Fluids.WATER, 1000));
            check(host.findProcessableRecipe().orElseThrow().recipe().value() == water, "water selected lava recipe");
            host.getTank().setFluid(new FluidStack(Fluids.LAVA, 999));
            check(host.findProcessableRecipe().isEmpty(), "insufficient fluid matched");
        } finally { replaceRecipes(manager, original); }
        h.succeed();
    }

    private static void fullCycle(GameTestHelper h, String path) {
        var candidate = find(h, path);
        if (candidate.isEmpty()) { h.succeed(); return; }
        var recipe = candidate.get().recipe().value();
        var host = powered(h, 10);
        h.runAfterDelay(20, () -> supply(host, recipe));
        h.succeedWhen(() -> {
            var result = host.getInventory().getStackInSlot(OUTPUT);
            check(result.getCount() == 1024, "waiting for 1024 products: " + path);
            check(ItemStack.isSameItemSameComponents(result, recipe.getOutputTemplate()), "wrong product");
            check(host.getFluid().isEmpty() && host.getMachineStoredEnergy() == 0 && lightning(host) == 9,
                    "1024 outputs must cost exactly 1 B, 100000 FE and one lightning");
            check(host.getInventory().getStackInSlot(CATALYST).getCount() == 256, "catalysts were consumed");
        });
    }

    public static void legacyWater1024(GameTestHelper h) { fullCycle(h, WATER); }

    public static void fluxite1024(GameTestHelper h) { fullCycle(h, "fluxite_block"); }

    public static void uranium1024(GameTestHelper h) { fullCycle(h, "uranium_crystal"); }

    public static void timeCrystal1024(GameTestHelper h) { fullCycle(h, "time_crystal_block"); }

    public static void savedCycleWaitsForFluidAndOutput(GameTestHelper h) {
        var recipe = find(h, "time_crystal_block").orElseGet(() -> find(h, WATER).orElseThrow()).recipe().value();
        var host = powered(h, 10);
        long[] paid = {-1};
        h.runAfterDelay(20, () -> supply(host, recipe));
        h.runAfterDelay(28, () -> {
            check(host.hasLockedRecipe() && host.getConsumedEnergy() > 0, "cycle never started");
            var tag = new CompoundTag();
            host.saveAdditional(com.moakiee.ae2lt.api.compat.ValueIO.output(tag, h.getLevel().registryAccess()));
            paid[0] = host.getConsumedEnergy();
            host.clearContent();
            host.loadTag(com.moakiee.ae2lt.api.compat.ValueIO.input(tag, h.getLevel().registryAccess()));
            check(host.getLockedRecipe().orElseThrow().matchesFluidInput(recipe), "restored wrong fluid");
            var insufficient = recipe.fluidInput();
            insufficient.setAmount(999);
            host.getTank().setFluid(insufficient);
        });
        h.runAfterDelay(65, () -> {
            check(host.hasLockedRecipe() && host.getConsumedEnergy() == paid[0], "missing fluid lost/advanced progress");
            check(host.getFluid().getAmount() == 999 && lightning(host) == 10, "missing fluid spent materials");
            host.getInventory().setItemDirect(OUTPUT, recipe.getOutputTemplate().copyWithCount(1024));
            host.getTank().setFluid(recipe.fluidInput());
        });
        h.runAfterDelay(100, () -> {
            check(host.getConsumedEnergy() == paid[0] && host.getFluid().getAmount() == 1000 && lightning(host) == 10,
                    "blocked output advanced or spent resources");
            host.getInventory().extractItem(OUTPUT, 1024, false);
        });
        h.succeedWhen(() -> {
            check(h.getTick() > 100 && host.getInventory().getStackInSlot(OUTPUT).getCount() == 1024, "waiting for recovery");
            check(host.getFluid().isEmpty() && host.getMachineStoredEnergy() == 0 && lightning(host) == 9,
                    "recovered cycle did not settle once");
        });
    }

    public static void pigmeeRejectsSpecialFluidsAndLegacyFluxite(GameTestHelper h) {
        var host = machine(h, true);
        h.runAfterDelay(15, () -> {
            var fluids = new ArrayList<FluidStack>();
            fluids.add(new FluidStack(Fluids.LAVA, 1000));
            for (var route : SPECIAL) find(h, route.path()).ifPresent(c -> fluids.add(c.recipe().value().fluidInput()));
            for (var fluid : fluids) {
                check(!host.getTank().isFluidValid(fluid), "GUI tank accepted non-water");
                check(host.getTank().fill(fluid, FluidAction.EXECUTE) == 0, "direct fill accepted non-water");
                for (var side : Direction.values()) {
                    var handler = host.getFluidHandlerCapability(side);
                    check(!handler.isFluidValid(0, fluid)
                            && handler.fill(fluid, FluidAction.SIMULATE) == 0
                            && handler.fill(fluid, FluidAction.EXECUTE) == 0, "pipe accepted non-water");
                }
            }
            for (var route : SPECIAL) {
                var candidate = find(h, route.path());
                if (candidate.isEmpty()) continue;
                var recipe = candidate.get().recipe().value();
                var catalyst = recipe.catalyst().orElseThrow().items().map(ItemStack::new).toArray(ItemStack[]::new)[0].copyWithCount(64);
                check(!host.getInventory().isItemValid(CATALYST, catalyst), "Pigmee accepted special catalyst");
                // Simulate old NBT bypassing insertion validation.
                host.getInventory().setItemDirect(CATALYST, catalyst);
                host.getTank().setFluid(recipe.fluidInput());
                check(host.findProcessableRecipe().isEmpty(), "NBT bypass enabled non-water recipe");
                var old = new CrystalCatalyzerLockedRecipe(candidate.get().recipe().id().identifier(), recipe.getOutputTemplate(),
                        100_000, 1, 1, LightningKey.Tier.HIGH_VOLTAGE);
                host.getTank().setFluid(new FluidStack(Fluids.WATER, 1000));
                check(!host.completeLockedRecipe(old, candidate.get()) && host.getFluid().getAmount() == 1000,
                        "legacy water snapshot completed special-fluid recipe");
            }
            h.succeed();
        });
    }

    public static void changedRecipeFluidDoesNotStrandSavedCycle(GameTestHelper h) {
        var host = machine(h, false);
        h.runAfterDelay(15, () -> {
            var candidate = find(h, "fluxite_block").orElseGet(() -> find(h, WATER).orElseThrow());
            var recipe = candidate.recipe().value();
            host.getInventory().setItemDirect(CATALYST, recipe.catalyst().orElseThrow().items().map(ItemStack::new).toArray(ItemStack[]::new)[0]);
            var staleFluid = recipe.isWaterRecipe() ? new FluidStack(Fluids.LAVA, 1000)
                    : CrystalCatalyzerRecipe.defaultFluidInput();
            var stale = new CrystalCatalyzerLockedRecipe(candidate.recipe().id().identifier(), recipe.getOutputTemplate(),
                    100_000, 1, 1, LightningKey.Tier.HIGH_VOLTAGE, staleFluid);
            var tag = new CompoundTag();
            host.saveAdditional(com.moakiee.ae2lt.api.compat.ValueIO.output(tag, h.getLevel().registryAccess()));
            tag.put("LockedRecipe", stale.toTag(h.getLevel().registryAccess()));
            tag.putLong("ConsumedEnergy", 5000);
            host.loadTag(com.moakiee.ae2lt.api.compat.ValueIO.input(tag, h.getLevel().registryAccess()));
            host.getTank().setFluid(recipe.fluidInput());
            // No FE/lightning and none of the old fluid: invalidation must precede waiting.
            host.getLogic().tickingRequest(host.getActionableNode(), 1);
            check(!host.hasLockedRecipe(), "stale fluid contract remained locked waiting for unavailable resources");
            check(host.getFluid().getAmount() == 1000 && host.getInventory().getStackInSlot(OUTPUT).isEmpty(),
                    "invalidating the stale cycle spent fluid or produced free output");
            check(host.lockCurrentRecipe().orElseThrow().matchesFluidInput(recipe), "replacement recipe did not relock");
            h.succeed();
        });
    }

    public static void pigmeeStillNeeds100DistinctTicks(GameTestHelper h) {
        com.moakiee.ae2ltcpuselection.PigmeeCrystalCatalyzerGameTests.pigmeeRepeatedTicksDoNotAccelerate(h);
    }

    public static void pigmeeLegacyProgressStillResumes(GameTestHelper h) {
        com.moakiee.ae2ltcpuselection.PigmeeCrystalCatalyzerGameTests.pigmeeSavedProgressAndLegacyRecipeIdResume(h);
    }

    public static void normalStillRequiresEnergyAndLightning(GameTestHelper h) {
        com.moakiee.ae2ltcpuselection.PigmeeCrystalCatalyzerGameTests.normalCatalyzerDoesNotGainFreeProcessing(h);
    }
}
