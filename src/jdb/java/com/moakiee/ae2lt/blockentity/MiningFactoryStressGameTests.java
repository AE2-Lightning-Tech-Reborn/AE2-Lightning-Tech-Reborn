package com.moakiee.ae2lt.blockentity;

import java.util.SplittableRandom;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.StorageCells;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import com.moakiee.ae2lt.machine.miningfactory.MiningFactoryConfig;
import com.moakiee.ae2lt.machine.miningfactory.MiningFactoryInventory;
import com.moakiee.ae2lt.machine.miningfactory.MiningLoot;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Native server stress fixtures; excluded from release artifacts. */
@GameTestHolder("ae2lt_stress")
@PrefixGameTestTemplate(false)
public final class MiningFactoryStressGameTests {
    private static final BlockPos POS = new BlockPos(2, 2, 2);

    private MiningFactoryStressGameTests() {}

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 200)
    public static void controlledMiningThroughputBenchmark(GameTestHelper h) throws Exception {
        var gear = UpstreamFixGameTests.nativePickaxe(h, true);
        var gearUnbreakable = gear.copy();
        gearUnbreakable.getOrCreateTag().putBoolean("Unbreakable", true);
        var vanillaUnbreakable = new ItemStack(Items.NETHERITE_PICKAXE);
        vanillaUnbreakable.getOrCreateTag().putBoolean("Unbreakable", true);
        ItemStack[] tools = {new ItemStack(Items.NETHERITE_PICKAXE), gear,
                vanillaUnbreakable, gearUnbreakable, new ItemStack(AEParts.ANNIHILATION_PLANE.asItem())};
        String[] names = {"vanilla", "silentgear", "vanilla_unbreakable", "silentgear_unbreakable", "plane"};
        int count = Math.min(256, gear.getMaxDamage() - 2);
        int[] index = {0};
        long[][] nanos = new long[tools.length][128];
        h.onEachTick(() -> {
            for (int step = 0; step < 8 && index[0] < tools.length * 160; step++, index[0]++) {
                int type = index[0] / 160;
                int sample = index[0] % 160;
                long start = System.nanoTime();
                var result = MiningLoot.roll(h.getLevel(), h.absolutePos(POS), Blocks.IRON_ORE.defaultBlockState(), tools[type], count, 8);
                long elapsed = System.nanoTime() - start;
                h.assertTrue(result.processed() == count
                        && result.drops().stream().mapToLong(MiningLoot.Drop::count).sum() == count,
                        "Benchmark did not conserve the complete batch for " + names[type]);
                if (sample >= 32) nanos[type][sample - 32] = elapsed;
            }
            if (index[0] == tools.length * 160) {
                for (int type = 0; type < tools.length; type++) {
                    java.util.Arrays.sort(nanos[type]);
                    com.mojang.logging.LogUtils.getLogger().info(
                            "MINING_CONTROLLED_BENCH tool={} blocks={} samples=8 runs=128 median_ms={} p95_ms={} max_ms={}",
                            names[type], count, nanos[type][64] / 1_000_000.0, nanos[type][121] / 1_000_000.0,
                            nanos[type][127] / 1_000_000.0);
                }
                h.succeed();
            }
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 300)
    public static void randomizedNativeLootConservesCountsAndToolCopies(GameTestHelper h) throws Exception {
        var nativeTool = UpstreamFixGameTests.nativePickaxe(h, false);
        var random = new SplittableRandom(20261003L);
        int[] completed = {0};
        long[] blocks = {0};
        long[] nanos = new long[2048];
        h.onEachTick(() -> {
            for (int step = 0; step < 16 && completed[0] < nanos.length; step++) {
                int caseId = completed[0];
                boolean plane = caseId % 8 == 0;
                var tool = plane ? new ItemStack(AEParts.ANNIHILATION_PLANE.asItem())
                        : caseId % 2 == 0 ? new ItemStack(Items.IRON_PICKAXE) : nativeTool.copy();
                if (!plane) {
                    tool.setDamageValue(Math.max(0, tool.getMaxDamage() - random.nextInt(1, 33)));
                    if (caseId % 3 == 0) tool.enchant(Enchantments.UNBREAKING, 3);
                    if (caseId % 17 == 0) tool.getOrCreateTag().putBoolean("Unbreakable", true);
                }
                var snapshot = tool.copy();
                int count = random.nextInt(1, 257);
                int budget = random.nextInt(1, 17);
                boolean usable = MiningLoot.isTool(tool);
                long start = System.nanoTime();
                var result = MiningLoot.roll(h.getLevel(), h.absolutePos(POS), Blocks.IRON_ORE.defaultBlockState(), tool, count, budget);
                nanos[caseId] = System.nanoTime() - start;
                long drops = result.drops().stream().mapToLong(MiningLoot.Drop::count).sum();
                h.assertTrue(result.processed() >= 0 && result.processed() <= count
                        && result.samples() <= Math.min(count, budget) && result.samples() <= result.processed()
                        && drops == result.processed(), "Count or sample conservation failed in case " + caseId);
                h.assertTrue(ItemStack.matches(snapshot, tool), "Installed tool mutated in case " + caseId);
                h.assertTrue(usable || result.processed() == 0, "Broken tool generated loot in case " + caseId);
                if (!MiningLoot.isTool(result.tool())) {
                    var retry = MiningLoot.roll(h.getLevel(), h.absolutePos(POS), Blocks.DIRT.defaultBlockState(), result.tool(), 256, 8);
                    h.assertTrue(retry.processed() == 0 && retry.drops().isEmpty(), "Broken tool restarted in case " + caseId);
                }
                blocks[0] += result.processed();
                completed[0]++;
            }
            if (completed[0] == nanos.length) {
                java.util.Arrays.sort(nanos);
                com.mojang.logging.LogUtils.getLogger().info(
                        "MINING_RANDOM_STRESS cases={} processed={} median_ms={} p95_ms={} max_ms={}",
                        nanos.length, blocks[0], nanos[1024] / 1_000_000.0, nanos[1945] / 1_000_000.0,
                        nanos[2047] / 1_000_000.0);
                h.succeed();
            }
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 400)
    public static void repeatedFullOutputAndReloadsDoNotSpendOrDuplicateResources(GameTestHelper h) {
        h.setBlock(POS, ModBlocks.MINING_FACTORY.get());
        h.setBlock(POS.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        h.setBlock(POS.north(), AEBlocks.DRIVE.block());
        var factory = (MiningFactoryBlockEntity) h.getBlockEntity(POS);
        factory.setAutoExportEnabled(false);
        var inventory = factory.getInventory();
        inventory.setStackInSlot(MiningFactoryInventory.INPUT, new ItemStack(Items.IRON_ORE, 4096));
        inventory.setStackInSlot(MiningFactoryInventory.TOOL, new ItemStack(AEParts.ANNIHILATION_PLANE.asItem()));
        inventory.setStackInSlot(MiningFactoryInventory.MATRIX,
                new ItemStack(ModItems.LIGHTNING_COLLAPSE_MATRIX.get(), inventory.getSlotLimit(MiningFactoryInventory.MATRIX)));
        factory.getEnergyStorage().receiveEnergy(1_000_000, false);
        var cell = new ItemStack(ModItems.BULK_LIGHTNING_STORAGE_COMPONENT.get());
        var storage = StorageCells.getCellInventory(cell, null);
        h.assertTrue(storage != null && storage.insert(LightningKey.HIGH_VOLTAGE, 1000,
                Actionable.MODULATE, IActionSource.empty()) == 1000, "Could not initialize lightning cell");
        storage.persist();
        ((DriveBlockEntity) h.getBlockEntity(POS.north())).getInternalInventory().setItemDirect(0, cell);
        int[] completed = {0};
        long[] previousLightning = {1000};
        long[] expectedEnergy = {1_000_000};
        int[] previousInput = {4096};
        long[] generated = {0};
        long[] drained = {0};
        int[] blockedUntil = {0};
        long[] tick = {0};
        boolean[] started = {false};
        h.onEachTick(() -> {
            int now = (int) tick[0]++;
            if (!started[0]) {
                if (factory.getAvailableLightning() != 1000) return;
                started[0] = true;
            }
            int input = inventory.getStackInSlot(MiningFactoryInventory.INPUT).getCount();
            int processed = previousInput[0] - input;
            h.assertTrue(processed >= 0, "Input grew without a refill");
            if (processed > 0) {
                h.assertTrue(now >= blockedUntil[0], "Factory processed while all output slots were blocked");
                generated[0] += processed;
                expectedEnergy[0] -= (long) processed * MiningFactoryConfig.energyPerBlock(true);
                previousLightning[0]--;
                previousInput[0] = input;
                completed[0]++;
            }
            long output = factory.getPendingDrops().stream().filter(d -> d.stack().is(Items.RAW_IRON))
                    .mapToLong(MiningLoot.Drop::count).sum();
            for (int slot = MiningFactoryInventory.OUTPUT; slot < MiningFactoryInventory.OUTPUT + MiningFactoryInventory.OUTPUT_COUNT; slot++) {
                var stack = inventory.getStackInSlot(slot);
                if (stack.is(Items.RAW_IRON)) output += stack.getCount();
            }
            h.assertTrue(output + drained[0] == generated[0], "Output duplication or loss across block/reload cycle");
            h.assertTrue(factory.getEnergyStorage().getStoredEnergyLong() == expectedEnergy[0]
                    && factory.getAvailableLightning() == previousLightning[0], "Resource costs diverged from actual batches");
            if (processed > 0) {
                drained[0] += output;
                for (int slot = MiningFactoryInventory.OUTPUT; slot < MiningFactoryInventory.OUTPUT + MiningFactoryInventory.OUTPUT_COUNT; slot++) {
                    inventory.setItemDirect(slot, new ItemStack(Items.COBBLESTONE, MiningFactoryInventory.CAPACITY));
                }
                blockedUntil[0] = now + 7;
                var tag = factory.saveWithFullMetadata();
                factory.clearContent();
                factory.load(tag);
                factory.getEnergyStorage().receiveEnergy(500_000, false);
                expectedEnergy[0] = Math.min(factory.getEnergyStorage().getCapacityLong(), expectedEnergy[0] + 500_000);
            }
            if (now == blockedUntil[0]) {
                for (int slot = MiningFactoryInventory.OUTPUT; slot < MiningFactoryInventory.OUTPUT + MiningFactoryInventory.OUTPUT_COUNT; slot++) {
                    inventory.setItemDirect(slot, ItemStack.EMPTY);
                }
            }
            if (input == 0 && completed[0] > 0) {
                h.assertTrue(generated[0] == 4096 && drained[0] == 4096, "Did not process the complete input stream");
                com.mojang.logging.LogUtils.getLogger().info("MINING_MACHINE_STRESS processed={} batches={} ticks={}",
                        generated[0], completed[0], now);
                h.succeed();
            }
        });
    }
}
