package com.moakiee.ae2lt.blockentity;

import com.moakiee.ae2lt.machine.miningfactory.MiningLoot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.silentchaos512.gear.core.BuiltinMaterials;
import net.silentchaos512.gear.core.component.GearPropertiesData;
import net.silentchaos512.gear.api.property.GearProperty;
import net.silentchaos512.gear.api.property.GearPropertyValue;
import net.silentchaos512.gear.api.property.NumberPropertyValue;
import net.silentchaos512.gear.api.property.TraitListPropertyValue;
import net.silentchaos512.gear.setup.GearItemSets;
import net.silentchaos512.gear.setup.SgDataComponents;
import net.silentchaos512.gear.setup.gear.GearProperties;
import net.silentchaos512.gear.util.GearData;
import net.silentchaos512.gear.util.GearHelper;

@GameTestHolder("ae2lt_mining")
@PrefixGameTestTemplate(false)
public final class MiningFactorySilentGearGameTests {
    private static final BlockPos POS = new BlockPos(2, 2, 2);

    private static ItemStack pickaxe(GameTestHelper h) {
        var stack = GearItemSets.PICKAXE.constructBasicItem(BuiltinMaterials.IRON);
        // Remove random Flexible durability saves for deterministic break-boundary assertions.
        java.util.Map<GearProperty<?, ?>, GearPropertyValue<?>> properties =
                new java.util.LinkedHashMap<>(GearData.getProperties(stack).properties());
        properties.put(GearProperties.TRAITS.get(), TraitListPropertyValue.empty());
        stack.set(SgDataComponents.GEAR_PROPERTIES, new GearPropertiesData(properties));
        h.assertTrue(GearHelper.isValidGear(stack) && stack.has(DataComponents.TOOL)
                && stack.getMaxDamage() > 8 && !GearHelper.isBroken(stack), "Invalid native gear fixture");
        return stack;
    }

    private static long output(MiningFactoryBlockEntity be) {
        long count = be.getPendingDrops().stream().filter(d -> d.stack().is(Items.RAW_IRON))
                .mapToLong(MiningLoot.Drop::count).sum();
        for (int slot = 2; slot < 11; slot++) {
            var stack = be.getInventory().getStackInSlot(slot);
            if (stack.is(Items.RAW_IRON)) count += stack.getCount();
        }
        return count;
    }

    @GameTest(template = "empty")
    public static void gearBreakingInsideSampleStopsAtLastUsableHit(GameTestHelper h) {
        var tool = pickaxe(h);
        tool.setDamageValue(tool.getMaxDamage() - 4);
        var snapshot = tool.copy();
        var result = MiningLoot.roll(h.getLevel(), h.absolutePos(POS), Blocks.IRON_ORE.defaultBlockState(), tool, 64, 8);
        h.assertTrue(result.processed() == 3 && result.samples() == 1
                && result.drops().stream().mapToLong(MiningLoot.Drop::count).sum() == 3,
                "Three usable hits: processed=" + result.processed() + ", samples=" + result.samples()
                        + ", drops=" + result.drops());
        h.assertTrue(!result.tool().isEmpty() && GearHelper.isBroken(result.tool()), "Broken gear must be retained");
        h.assertTrue(ItemStack.matches(tool, snapshot), "Prospective damage changed the installed gear");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void brokenGearWithStaleToolComponentCannotMineSoftOrZeroHardnessBlocks(GameTestHelper h) {
        var tool = pickaxe(h);
        var component = tool.get(DataComponents.TOOL);
        tool.setDamageValue(tool.getMaxDamage() - 1);
        h.assertTrue(GearHelper.isBroken(tool), "Fixture must be broken");
        // Simulate an old saved tool or another mod retaining the pre-break component.
        tool.set(DataComponents.TOOL, component);
        h.assertTrue(!MiningLoot.isTool(tool), "Broken Silent Gear was accepted as a usable tool");
        for (var block : new net.minecraft.world.level.block.Block[]{Blocks.DIRT, Blocks.TORCH, Blocks.IRON_ORE}) {
            var result = MiningLoot.roll(h.getLevel(), h.absolutePos(POS), block.defaultBlockState(), tool, 64, 8);
            h.assertTrue(result.processed() == 0 && result.samples() == 0 && result.drops().isEmpty()
                    && ItemStack.matches(tool, result.tool()), "Broken gear generated loot for " + block);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeBrokenGearRemainsIdleAfterReload(GameTestHelper h) {
        var tool = pickaxe(h);
        tool.setDamageValue(tool.getMaxDamage() - 1);
        var be = MiningFactoryGameTests.fixture(h, ItemStack.EMPTY, 64, 1_000_000);
        be.getInventory().setItemDirect(1, tool);
        var tag = be.saveWithFullMetadata(h.getLevel().registryAccess());
        be.clearContent();
        be.loadTag(tag, h.getLevel().registryAccess());
        MiningFactoryGameTests.completeCycle(h, be, () -> {
            h.assertTrue(be.getStatus() == MiningFactoryBlockEntity.Status.TOOL && output(be) == 0
                    && be.getInventory().getStackInSlot(0).getCount() == 64
                    && be.getAvailableLightning() == 1000 && be.getEnergyStorage().getEnergyStored() == 1_000_000,
                    "Broken gear after reload spent input, energy or lightning");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void partialGearBatchCommitsCostsOnceAndStops(GameTestHelper h) {
        var tool = pickaxe(h);
        tool.setDamageValue(tool.getMaxDamage() - 4);
        var be = MiningFactoryGameTests.fixture(h, tool, 64, 1_000_000);
        MiningFactoryGameTests.completeCycle(h, be, () -> {
            h.assertTrue(output(be) == 3 && be.getInventory().getStackInSlot(0).getCount() == 61
                    && GearHelper.isBroken(be.getInventory().getStackInSlot(1))
                    && be.getAvailableLightning() == 999 && be.getEnergyStorage().getEnergyStored() == 1_000_000 - 3 * 256,
                    "Partial gear batch failed to commit exactly three blocks and their costs");
            var saved = be.saveWithFullMetadata(h.getLevel().registryAccess());
            be.clearContent();
            be.loadTag(saved, h.getLevel().registryAccess());
            h.runAfterDelay(8, () -> {
                be.processTick();
                h.assertTrue(be.getStatus() == MiningFactoryBlockEntity.Status.TOOL && output(be) == 3
                        && be.getInventory().getStackInSlot(0).getCount() == 61 && be.getAvailableLightning() == 999
                        && be.getEnergyStorage().getEnergyStored() == 1_000_000 - 3 * 256,
                        "Retained broken gear restarted a batch after reload");
                h.succeed();
            });
        });
    }

    @GameTest(template = "empty")
    public static void unbreakableGearStillWorks(GameTestHelper h) {
        var tool = pickaxe(h);
        tool.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
        var result = MiningLoot.roll(h.getLevel(), h.absolutePos(POS), Blocks.IRON_ORE.defaultBlockState(), tool, 64, 8);
        h.assertTrue(result.processed() == 64 && result.tool().getDamageValue() == 0
                && result.drops().stream().mapToLong(MiningLoot.Drop::count).sum() == 64,
                "Legitimate unbreakable gear must still work");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 1000)
    public static void optionalFullBatchBenchmark(GameTestHelper h) {
        if (Boolean.getBoolean("ae2lt.miningBenchmark")) {
            var vanilla = new ItemStack(Items.NETHERITE_PICKAXE);
            var gear = GearItemSets.PICKAXE.constructBasicItem(BuiltinMaterials.IRON);
            // Extend durability only, so each timed run exercises all 2048 damage hooks.
            vanilla.set(DataComponents.MAX_DAMAGE, 1_000_000);
            java.util.Map<GearProperty<?, ?>, GearPropertyValue<?>> properties =
                    new java.util.LinkedHashMap<>(GearData.getProperties(gear).properties());
            properties.put(GearProperties.DURABILITY.get(), NumberPropertyValue.average(1_000_000));
            gear.set(SgDataComponents.GEAR_PROPERTIES, new GearPropertiesData(properties));
            h.assertTrue(gear.getMaxDamage() == 1_000_000, "Benchmark native durability override failed");
            benchmark(h, "vanilla", vanilla);
            benchmark(h, "silentgear-iron", gear);
            benchmark(h, "plane", new ItemStack(appeng.core.definitions.AEParts.ANNIHILATION_PLANE.asItem()));
        }
        h.succeed();
    }

    private static void benchmark(GameTestHelper h, String name, ItemStack tool) {
        var state = Blocks.IRON_ORE.defaultBlockState();
        var pos = h.absolutePos(POS);
        // Warm the real runtime; record percentiles without making speed a flaky assertion.
        for (int i = 0; i < 40; i++) MiningLoot.roll(h.getLevel(), pos, state, tool, 2048, 8);
        long[] nanos = new long[100];
        for (int i = 0; i < nanos.length; i++) {
            long start = System.nanoTime();
            var result = MiningLoot.roll(h.getLevel(), pos, state, tool, 2048, 8);
            nanos[i] = System.nanoTime() - start;
            h.assertTrue(result.processed() == 2048 && result.samples() == 8, "Benchmark did not process a full batch");
        }
        java.util.Arrays.sort(nanos);
        com.mojang.logging.LogUtils.getLogger().info(
                "MINING_BENCH tool={} blocks=2048 samples=8 runs=100 median_ms={} p95_ms={} max_ms={}",
                name, nanos[50] / 1_000_000.0, nanos[94] / 1_000_000.0, nanos[99] / 1_000_000.0);
    }
}
