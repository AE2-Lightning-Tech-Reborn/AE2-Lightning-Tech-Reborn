package com.moakiee.ae2lt.integration.ae2cs;

import com.moakiee.ae2lt.registry.ModItems;
import com.moakiee.ae2lt.machine.lightningassembly.recipe.LightningAssemblyRecipe;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IAEPowerStorage;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.blockentity.ServerTickingBlockEntity;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.util.inv.AppEngInternalInventory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Runs against both the AE2CS 1.2.1 release and the current 1.21.1 snapshot. */
@GameTestHolder("ae2lt_cs_overclock")
@PrefixGameTestTemplate(false)
public final class CrystalScienceOverclockGameTests {
    private static final BlockPos POS = new BlockPos(2, 2, 2);

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    private static AppEngInternalInventory inventory(BlockEntity machine, String getter) {
        try {
            return (AppEngInternalInventory) machine.getClass().getMethod(getter).invoke(machine);
        } catch (ReflectiveOperationException exception) {
            throw new GameTestAssertException("AE2CS inventory API changed: " + getter + ": " + exception);
        }
    }

    private static BlockEntity pulverizer(GameTestHelper helper, ItemStack input, double energy, int cards) {
        check(ModList.get().isLoaded("ae2cs"), "AE2CS is required for this fixture");
        var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2cs:crystal_pulverizer"));
        helper.setBlock(POS, block);
        BlockEntity machine = helper.getBlockEntity(POS);
        check(machine instanceof IUpgradeableObject, "pulverizer has no upgrade inventory");
        if (cards > 0) {
            var upgrades = ((IUpgradeableObject) machine).getUpgrades();
            check(upgrades.getMaxInstalled(ModItems.OVERLOAD_PARALLEL_CARD.get()) == 2,
                    "LT card is not registered for AE2CS pulverizer");
            for (int i = 0; i < cards; i++) {
                check(upgrades.addItems(ModItems.OVERLOAD_PARALLEL_CARD.toStack()).isEmpty(),
                        "LT card " + (i + 1) + " was rejected by AE2CS pulverizer");
            }
        }
        inventory(machine, "getInputInv").setItemDirect(0, input);
        double inserted = ((IAEPowerStorage) machine).injectAEPower(energy, Actionable.MODULATE);
        check(inserted <= 0.001, "AE2CS machine refused the fixture energy");
        return machine;
    }

    private static void tick(BlockEntity machine) {
        ((ServerTickingBlockEntity) machine).serverTick();
    }

    private static IFluidHandler tanks(BlockEntity machine) {
        try {
            return (IFluidHandler) machine.getClass().getMethod("getFluidTanks").invoke(machine);
        } catch (ReflectiveOperationException exception) {
            throw new GameTestAssertException("AE2CS fluid API changed: " + exception);
        }
    }

    private static boolean hasLatestMachine(String path) {
        return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath("ae2cs", path)).isPresent();
    }

    private static void checkCraftingInput(LightningAssemblyRecipe recipe, String itemId, int count) {
        ItemStack item = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId)));
        check(recipe.inputs().stream().anyMatch(input -> input.count() == count
                        && input.ingredient().test(item)),
                "parallel card recipe is missing " + count + " x " + itemId);
    }

    private static BlockEntity latestMachine(GameTestHelper helper, String path, ItemStack input, double energy) {
        helper.setBlock(POS, BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("ae2cs", path)));
        BlockEntity machine = helper.getBlockEntity(POS);
        var upgrades = ((IUpgradeableObject) machine).getUpgrades();
        check(upgrades.getMaxInstalled(ModItems.OVERLOAD_PARALLEL_CARD.get()) == 2,
                "LT card is not registered for " + path);
        check(upgrades.addItems(ModItems.OVERLOAD_PARALLEL_CARD.toStack()).isEmpty(),
                "LT card was rejected by " + path);
        inventory(machine, "getInputInv").setItemDirect(0, input);
        check(((IAEPowerStorage) machine).injectAEPower(energy, Actionable.MODULATE) <= 0.001,
                path + " refused fixture energy");
        check(tanks(machine).fill(new FluidStack(Fluids.WATER, 8_000), IFluidHandler.FluidAction.EXECUTE) >= 4_000,
                path + " refused water");
        return machine;
    }

    @GameTest(template = "empty")
    public static void oneCardCompletesEightPaidRecipesPerTick(GameTestHelper helper) {
        var cardRecipe = helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse(
                "ae2lt:lightning_assembly/overload_parallel_card"));
        check(cardRecipe.isPresent(),
                "LT and AE2CS conditional crafting recipe did not load");
        check(cardRecipe.get().value() instanceof LightningAssemblyRecipe,
                "parallel card does not use lightning assembly");
        var assembly = (LightningAssemblyRecipe) cardRecipe.get().value();
        check(assembly.inputs().size() == 3, "parallel card recipe has unexpected extra inputs");
        checkCraftingInput(assembly, "ae2cs:overload_card", 2);
        checkCraftingInput(assembly, "ae2lt:lightning_collapse_matrix", 1);
        checkCraftingInput(assembly, "ae2cs:resonating_processor", 2);
        BlockEntity machine = pulverizer(helper, new ItemStack(Items.STONE, 16), 80_000, 1);
        tick(machine);
        check(inventory(machine, "getInputInv").getStackInSlot(0).getCount() == 8,
                "eight paid pulverizer inputs were not consumed");
        check(inventory(machine, "getOutputInv").getStackInSlot(0).is(Items.COBBLESTONE)
                        && inventory(machine, "getOutputInv").getStackInSlot(0).getCount() == 8,
                "eight pulverizer outputs were not produced");
        check(Math.abs(((IAEPowerStorage) machine).getAECurrentPower() - 16_000) < 0.001,
                "eight complete recipe energy costs were not charged");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void twoCardsCompleteSixtyFourPaidRecipesPerTick(GameTestHelper helper) {
        check(helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse(
                "ae2lt_cs_overclock:pulverizer_dirt")).isPresent(), "64x fixture recipe did not load");
        BlockEntity machine = pulverizer(helper, new ItemStack(Items.DIRT, 64), 80_000, 2);
        tick(machine);
        check(inventory(machine, "getInputInv").getStackInSlot(0).isEmpty(),
                "64 paid pulverizer inputs were not consumed");
        check(inventory(machine, "getOutputInv").getStackInSlot(0).is(Items.COBBLESTONE)
                        && inventory(machine, "getOutputInv").getStackInSlot(0).getCount() == 64,
                "64 pulverizer outputs were not produced");
        check(Math.abs(((IAEPowerStorage) machine).getAECurrentPower() - 16_000) < 0.001,
                "64 complete recipe energy costs were not charged");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void expandedCapacitySurvivesBlockEntityReload(GameTestHelper helper) {
        BlockEntity machine = pulverizer(helper, ItemStack.EMPTY, 0, 2);
        helper.runAfterDelay(10, () -> {
            var power = (IAEPowerStorage) machine;
            check(Math.abs(power.getAEMaxPower() - 5_120_000) < 0.001,
                    "two cards did not expand the 80k buffer to 5.12M AE: " + power.getAEMaxPower());
            check(power.injectAEPower(400_000, Actionable.MODULATE) < 0.001,
                    "expanded buffer rejected 400k AE");
            var saved = machine.saveWithoutMetadata(helper.getLevel().registryAccess());
            helper.setBlock(POS, net.minecraft.world.level.block.Blocks.AIR);
            helper.setBlock(POS, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2cs:crystal_pulverizer")));
            BlockEntity restored = helper.getBlockEntity(POS);
            ((AENetworkedBlockEntity) restored).loadTag(saved, helper.getLevel().registryAccess());
            check(Math.abs(((IAEPowerStorage) restored).getAECurrentPower() - 400_000) < 0.001,
                    "expanded buffer energy was truncated while CS loaded the block entity");
            helper.runAfterDelay(10, () -> {
                check(Math.abs(((IAEPowerStorage) restored).getAEMaxPower() - 5_120_000) < 0.001,
                        "reloaded card count did not restore expanded capacity");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 50)
    public static void oneCardExpandsEnergyCapacityEightfold(GameTestHelper helper) {
        BlockEntity machine = pulverizer(helper, ItemStack.EMPTY, 0, 1);
        helper.runAfterDelay(10, () -> {
            check(Math.abs(((IAEPowerStorage) machine).getAEMaxPower() - 640_000) < 0.001,
                    "one card did not expand the 80k buffer to 640k AE: "
                            + ((IAEPowerStorage) machine).getAEMaxPower());
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void twoCardsRefillFromNetworkAcrossSixtyFourRecipes(GameTestHelper helper) {
        helper.setBlock(POS.east(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        BlockEntity machine = pulverizer(helper, ItemStack.EMPTY, 0, 2);
        helper.runAfterDelay(40, () -> {
            var grid = ((AENetworkedBlockEntity) machine).getMainNode().getGrid();
            check(grid != null && grid.getEnergyService().isNetworkPowered(),
                    "AE2CS machine did not join the powered AE network");
            check(Math.abs(((IAEPowerStorage) machine).getAEMaxPower() - 5_120_000) < 0.001,
                    "networked machine did not expand capacity before charging");
            inventory(machine, "getInputInv").setItemDirect(0, new ItemStack(Items.STONE, 64));
            tick(machine);
            check(inventory(machine, "getInputInv").getStackInSlot(0).isEmpty(),
                    "64 stone inputs were not consumed with network refills");
            check(inventory(machine, "getOutputInv").getStackInSlot(0).getCount() == 64,
                    "64 network-powered outputs were not produced");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void cardStopsAtOutputCapacity(GameTestHelper helper) {
        BlockEntity machine = pulverizer(helper, new ItemStack(Items.STONE, 8), 64_000, 1);
        var output = inventory(machine, "getOutputInv");
        for (int slot = 0; slot < output.size(); slot++) {
            output.setItemDirect(slot, new ItemStack(Items.DIAMOND, 64));
        }
        tick(machine);
        check(inventory(machine, "getInputInv").getStackInSlot(0).getCount() == 8,
                "full output consumed inputs: input=" + inventory(machine, "getInputInv").getStackInSlot(0)
                        + " output=" + inventory(machine, "getOutputInv").getStackInSlot(0));
        for (int slot = 0; slot < output.size(); slot++) {
            check(output.getStackInSlot(slot).is(Items.DIAMOND) && output.getStackInSlot(slot).getCount() == 64,
                    "full output was modified at slot " + slot);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void noCardRetainsNativeRate(GameTestHelper helper) {
        BlockEntity machine = pulverizer(helper, new ItemStack(Items.STONE, 8), 64_000, 0);
        tick(machine);
        check(inventory(machine, "getInputInv").getStackInSlot(0).getCount() == 8,
                "unupgraded pulverizer unexpectedly completed a recipe");
        check(inventory(machine, "getOutputInv").getStackInSlot(0).isEmpty(),
                "unupgraded pulverizer unexpectedly produced output");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void latestInfuserConsumesEnergyAndWaterPerOperation(GameTestHelper helper) {
        if (!hasLatestMachine("crystal_infuser")) {
            helper.succeed(); // AE2CS 1.2.1 predates this machine.
            return;
        }
        var seed = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2cs:certus_quartz_seed"));
        BlockEntity machine = latestMachine(helper, "crystal_infuser", new ItemStack(seed, 4), 128_000);
        tick(machine);
        check(inventory(machine, "getInputInv").getStackInSlot(0).getCount() == 2,
                "infuser did not process two 64k-AE recipes from its 128k buffer");
        check(inventory(machine, "getOutputInv").getStackInSlot(0).getCount() == 2,
                "infuser output count differs from completed recipes");
        check(((IAEPowerStorage) machine).getAECurrentPower() < 0.001,
                "infuser did not charge two complete recipe energy costs");
        check(tanks(machine).getFluidInTank(0).getAmount() == 2_000,
                "infuser did not consume 1 B water per operation");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void latestCentrifugeExecutesFourWaterLimitedRecipes(GameTestHelper helper) {
        if (!hasLatestMachine("pulse_centrifuge")) {
            helper.succeed(); // AE2CS 1.2.1 predates this machine.
            return;
        }
        check(helper.getLevel().getRecipeManager().byKey(
                ResourceLocation.parse("ae2lt_cs_overclock:pulse_stone")).isPresent(),
                "focused pulse centrifuge recipe did not load");
        BlockEntity machine = latestMachine(helper, "pulse_centrifuge", new ItemStack(Items.STONE, 8), 64_000);
        tick(machine);
        check(inventory(machine, "getInputInv").getStackInSlot(0).isEmpty(),
                "centrifuge did not consume eight inputs");
        check(inventory(machine, "getOutputInv").getStackInSlot(0).getCount() == 8,
                "centrifuge did not produce eight outputs");
        check(((IAEPowerStorage) machine).getAECurrentPower() < 0.001,
                "centrifuge did not charge eight complete recipe energy costs");
        check(tanks(machine).getFluidInTank(0).getAmount() == 0,
                "centrifuge did not consume 1 B water per operation");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void growthChamberRunsFourPaidGrowthPasses(GameTestHelper helper) {
        var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2cs:crystal_growth_chamber"));
        helper.setBlock(POS, block);
        BlockEntity machine = helper.getBlockEntity(POS);
        var upgrades = ((IUpgradeableObject) machine).getUpgrades();
        check(upgrades.getMaxInstalled(ModItems.OVERLOAD_PARALLEL_CARD.get()) == 2,
                "LT card is not registered for crystal growth chamber");
        check(upgrades.addItems(ModItems.OVERLOAD_PARALLEL_CARD.toStack()).isEmpty(),
                "growth chamber rejected LT card");
        var seed = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2cs:certus_quartz_seed"));
        var inventory = inventory(machine, "getInternalInventory");
        inventory.setItemDirect(0, new ItemStack(seed));
        check(((IAEPowerStorage) machine).injectAEPower(1_000, Actionable.MODULATE) <= 0.001,
                "growth chamber refused fixture energy");
        tick(machine);
        try {
            int growth = (int) seed.getClass().getMethod("getGrowTicks", ItemStack.class)
                    .invoke(seed, inventory.getStackInSlot(0));
            check(growth == 80, "growth chamber did not apply eight native 10-tick growth passes: " + growth);
        } catch (ReflectiveOperationException exception) {
            throw new GameTestAssertException("AE2CS seed growth API changed: " + exception);
        }
        check(Math.abs(((IAEPowerStorage) machine).getAECurrentPower() - 200) < 0.001,
                "growth chamber did not charge eight 100-AE passes");
        helper.succeed();
    }
}
