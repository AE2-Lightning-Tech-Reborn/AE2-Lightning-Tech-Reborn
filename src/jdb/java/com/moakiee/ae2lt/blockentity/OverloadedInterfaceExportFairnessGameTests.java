package com.moakiee.ae2lt.blockentity;

import java.util.Arrays;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.ExportMode;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.IOSpeedMode;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.InterfaceMode;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.WirelessConnection;
import com.moakiee.ae2lt.machine.miningfactory.MiningFactoryInventory;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.LoggerFactory;

/** Real ME storage and mining-factory capabilities, with a fixed input-consumption schedule. */
@GameTestHolder("ae2lt_io")
@PrefixGameTestTemplate(false)
public final class OverloadedInterfaceExportFairnessGameTests {
    private static final BlockPos OWNER = new BlockPos(1, 1, 1);
    private static final AEItemKey[] KEYS = {
            AEItemKey.of(Items.IRON_ORE), AEItemKey.of(Items.GOLD_ORE), AEItemKey.of(Items.COPPER_ORE)};

    @GameTest(template = "wireless_io_empty", timeoutTicks = 1500)
    public static void competingUnlimitedExportBenchmark(GameTestHelper helper) {
        if (!Boolean.getBoolean("ae2lt.exportFairnessBenchmark")) {
            helper.succeed();
            return;
        }
        int count = Integer.getInteger("ae2lt.exportFairnessBenchmark.targets", 256);
        int keyCount = Integer.getInteger("ae2lt.exportFairnessBenchmark.keys", 27);
        int batch = Integer.getInteger("ae2lt.exportFairnessBenchmark.batch", 512);
        int warmup = Integer.getInteger("ae2lt.wirelessIoBenchmark.warmupTicks", 200);
        int samples = Integer.getInteger("ae2lt.wirelessIoBenchmark.sampleTicks", 1200);
        int startSample = 40 + warmup;
        int finish = startSample + samples;
        require(count > 0 && count <= 256 && keyCount >= 2 && keyCount <= 36
                && batch > 0 && batch <= 4096 && finish < 1480, "invalid fairness benchmark parameters");
        var fixture = fixture(helper, false, IOSpeedMode.FAST, count);
        var owner = fixture.owner();
        var keys = new AEItemKey[keyCount];
        for (int key = 0; key < keyCount; key++) {
            var stack = KEYS[key % KEYS.length].toStack(1);
            stack.setHoverName(net.minecraft.network.chat.Component.literal("ore-" + key));
            keys[key] = AEItemKey.of(stack);
        }
        long supplyPerKey = (long) count * batch * (finish + 1);
        long[][] consumed = new long[count][keyCount];
        int[][] lastConsumed = new int[count][keyCount];
        int[] completed = new int[count];
        int[] byTick = new int[samples];
        int[] maxWait = {0};
        helper.onEachTick(() -> {
            int tick = Math.toIntExact(helper.getTick());
            if (tick < 40) return;
            require(owner.getMainNode().isActive(), "benchmark grid lost power");
            var storage = owner.getMainNode().getGrid().getStorageService().getInventory();
            if (tick == 40) {
                for (int key = 0; key < keyCount; key++) {
                    owner.getInterfaceLogic().getConfig().setStack(key, new GenericStack(keys[key], 1));
                    owner.setSlotUnlimited(key, true);
                    require(storage.insert(keys[key], supplyPerKey, Actionable.MODULATE, IActionSource.empty())
                            == supplyPerKey, "benchmark supply rejected");
                }
                owner.setExportMode(ExportMode.AUTO);
            }
            if (tick < finish) {
                for (int target = 0; target < count; target++) {
                    var inventory = fixture.factories()[target].getInventory();
                    var stack = inventory.getStackInSlot(MiningFactoryInventory.INPUT);
                    if (stack.getCount() < batch) continue;
                    int matched = -1;
                    for (int key = 0; key < keyCount; key++) {
                        if (keys[key].matches(stack)) { matched = key; break; }
                    }
                    require(matched >= 0, "unexpected input key");
                    require(inventory.extractItem(MiningFactoryInventory.INPUT, batch, false).getCount() == batch,
                            "benchmark consumption changed");
                    consumed[target][matched] += batch;
                    maxWait[0] = Math.max(maxWait[0], tick - Math.max(40, lastConsumed[target][matched]));
                    lastConsumed[target][matched] = tick;
                    if (tick >= startSample) {
                        completed[target]++;
                        byTick[tick - startSample]++;
                    }
                }
            }
            if (tick != finish) return;
            long totalConsumed = 0;
            for (int key = 0; key < keyCount; key++) {
                long accounted = storage.extract(keys[key], Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
                for (int target = 0; target < count; target++) {
                    var stack = fixture.factories()[target].getInventory().getStackInSlot(MiningFactoryInventory.INPUT);
                    accounted += consumed[target][key] + (keys[key].matches(stack) ? stack.getCount() : 0);
                    totalConsumed += consumed[target][key];
                    maxWait[0] = Math.max(maxWait[0], tick - Math.max(40, lastConsumed[target][key]));
                }
                require(accounted == supplyPerKey, "benchmark ownership changed");
            }
            double minimumTarget = Arrays.stream(completed).min().orElseThrow() / (double) samples;
            double minimumWindow = 1;
            int window = 0;
            for (int sample = 0; sample < samples; sample++) {
                window += byTick[sample];
                if (sample >= 100) window -= byTick[sample - 100];
                if (sample >= 99) minimumWindow = Math.min(minimumWindow, window / (100.0 * count));
            }
            long totalCompleted = Arrays.stream(completed).asLongStream().sum();
            var report = new java.util.LinkedHashMap<String, Object>();
            report.put("scenario", System.getProperty("ae2lt.wirelessIoBenchmark.scenario"));
            report.put("targets", count);
            report.put("keys", keyCount);
            report.put("batch", batch);
            report.put("samples", samples);
            report.put("plannedEvents", (long) samples * count);
            report.put("completedEvents", totalCompleted);
            report.put("minimumTargetThroughput", minimumTarget);
            report.put("minimumWindowThroughput", minimumWindow);
            report.put("maxKeyWait", maxWait[0]);
            report.put("consumedItems", totalConsumed);
            report.put("ownershipVerified", true);
            report.put("bufferedItems", owner.benchmarkBufferedImportAmount());
            try {
                var directory = java.nio.file.Path.of("benchmark-reports");
                java.nio.file.Files.createDirectories(directory);
                java.nio.file.Files.writeString(directory.resolve("fairness-workload.json"),
                        new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
            } catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
            LoggerFactory.getLogger("ae2lt-export-fairness").info("Competing export workload={}", report);
            require(owner.benchmarkBufferedImportAmount() == 0, "benchmark retained overflow");
            require(minimumTarget >= 0.99 && minimumWindow >= 0.99, "benchmark throughput fell below 99%");
            require(maxWait[0] <= keyCount * (4096 / batch + 10), "benchmark key starvation");
            helper.succeed();
        });
    }

    @GameTest(template = "wireless_io_empty", timeoutTicks = 700)
    public static void fastLocalSparseRefillDoesNotStarveBacklog(GameTestHelper helper) {
        run(helper, true, IOSpeedMode.FAST, 1, true);
    }

    @GameTest(template = "wireless_io_empty", timeoutTicks = 700)
    public static void normalLocalSparseRefillDoesNotStarveBacklog(GameTestHelper helper) {
        run(helper, true, IOSpeedMode.NORMAL, 1, true);
    }

    @GameTest(template = "wireless_io_empty", timeoutTicks = 700)
    public static void fastWireless256FactoriesShareContinuouslyRefilledOres(GameTestHelper helper) {
        run(helper, false, IOSpeedMode.FAST, 256, false);
    }

    @GameTest(template = "wireless_io_empty", timeoutTicks = 700)
    public static void normalWireless64FactoriesShareSparseRefills(GameTestHelper helper) {
        run(helper, false, IOSpeedMode.NORMAL, 64, true);
    }

    @GameTest(template = "wireless_io_empty", timeoutTicks = 700)
    public static void normalWireless64FactoriesShareContinuouslyRefilledOres(GameTestHelper helper) {
        run(helper, false, IOSpeedMode.NORMAL, 64, false);
    }

    @GameTest(template = "wireless_io_empty", timeoutTicks = 700)
    public static void miningFactoryProcessesEveryOreWithLiveRefills(GameTestHelper helper) {
        if (Boolean.getBoolean("ae2lt.wirelessIoBenchmark")) {
            helper.succeed();
            return;
        }
        var fixture = fixture(helper, true, IOSpeedMode.FAST, 1);
        var owner = fixture.owner();
        var factory = fixture.factories()[0];
        var inventory = factory.getInventory();
        var tool = new ItemStack(Items.DIAMOND_PICKAXE);
        tool.getOrCreateTag().putBoolean("Unbreakable", true);
        inventory.setStackInSlot(MiningFactoryInventory.TOOL, tool);
        inventory.setStackInSlot(MiningFactoryInventory.MATRIX, new ItemStack(ModItems.LIGHTNING_COLLAPSE_MATRIX.get()));
        var outputs = new net.minecraft.world.item.Item[] {Items.RAW_IRON, Items.RAW_GOLD, Items.RAW_COPPER};
        long[] produced = new long[3];
        long[] lastProduced = new long[3];
        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (tick < 40) return;
            require(owner.getMainNode().isActive(), "ME grid lost power");
            var storage = owner.getMainNode().getGrid().getStorageService().getInventory();
            if (tick == 40) {
                require(factory.getMainNode().getGrid() == owner.getMainNode().getGrid(), "factory is not on the input grid");
                var lightning = appeng.api.storage.StorageCells.getCellInventory(
                        new ItemStack(ModItems.LIGHTNING_STORAGE_COMPONENT_II.get()), null);
                require(lightning != null && lightning.insert(LightningKey.HIGH_VOLTAGE, 1000,
                        Actionable.MODULATE, IActionSource.empty()) == 1000, "lightning setup failed");
                owner.getMainNode().getGrid().getStorageService().addGlobalStorageProvider(mounts -> mounts.mount(lightning, 0));
                for (int key = 0; key < KEYS.length; key++) {
                    owner.getInterfaceLogic().getConfig().setStack(key, new GenericStack(KEYS[key], 1));
                    owner.setSlotUnlimited(key, true);
                    long amount = key == 0 ? 8 : 200_000;
                    require(storage.insert(KEYS[key], amount, Actionable.MODULATE, IActionSource.empty()) == amount,
                            "ore setup failed");
                }
                owner.setExportMode(ExportMode.AUTO);
            }
            require(storage.insert(KEYS[0], 8, Actionable.MODULATE, IActionSource.empty()) == 8, "refill failed");
            factory.getEnergyStorage().receiveEnergy(1_000_000, false);
            for (int slot = MiningFactoryInventory.OUTPUT; slot < MiningFactoryInventory.OUTPUT + MiningFactoryInventory.OUTPUT_COUNT; slot++) {
                var output = inventory.extractItem(slot, Integer.MAX_VALUE, false);
                for (int key = 0; key < outputs.length; key++) {
                    if (output.is(outputs[key])) {
                        produced[key] += output.getCount();
                        lastProduced[key] = tick;
                    }
                }
            }
            if (tick >= 360) {
                for (int key = 0; key < KEYS.length; key++) {
                    require(lastProduced[key] > 0 && tick - lastProduced[key] <= 240,
                            "live mining starved an ore: " + Arrays.toString(produced) + ", status=" + factory.getStatus());
                }
            }
            if (tick == 640) {
                LoggerFactory.getLogger("ae2lt-export-fairness").info("Live mining output={}", Arrays.toString(produced));
                helper.succeed();
            }
        });
    }

    private static Fixture fixture(GameTestHelper helper, boolean local, IOSpeedMode speed, int count) {
        helper.setBlock(OWNER.east(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(OWNER, ModBlocks.OVERLOADED_INTERFACE.get());
        helper.setBlock(OWNER.west(), AEBlocks.DRIVE.block());
        var owner = (OverloadedInterfaceBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(OWNER));
        var drive = (DriveBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(OWNER.west()));
        drive.getInternalInventory().setItemDirect(0, new ItemStack(ModItems.INFINITE_STORAGE_CELL.get()));
        owner.setInterfaceMode(local ? InterfaceMode.NORMAL : InterfaceMode.WIRELESS);
        owner.setIOSpeedMode(speed);
        var factories = new MiningFactoryBlockEntity[count];
        for (int index = 0; index < count; index++) {
            var pos = local ? OWNER.south() : new BlockPos(4 + index % 16, 1, 4 + index / 16);
            helper.setBlock(pos, ModBlocks.MINING_FACTORY.get());
            var factory = (MiningFactoryBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
            factories[index] = factory;
            if (!local) require(owner.addOrUpdateConnection(new WirelessConnection(helper.getLevel().dimension(),
                    helper.absolutePos(pos), Direction.UP)), "wireless binding failed");
        }
        return new Fixture(owner, factories);
    }

    private static void run(GameTestHelper helper, boolean local, IOSpeedMode speed, int count, boolean sparse) {
        if (Boolean.getBoolean("ae2lt.wirelessIoBenchmark")) {
            helper.succeed();
            return;
        }
        var fixture = fixture(helper, local, speed, count);
        var owner = fixture.owner();
        var targets = Arrays.stream(fixture.factories()).map(MiningFactoryBlockEntity::getInventory)
                .toArray(MiningFactoryInventory[]::new);
        long[] supplied = new long[KEYS.length];
        long[][] consumed = new long[count][KEYS.length];
        long[][] lastConsumed = new long[count][KEYS.length];
        long[] maximumWait = {0};
        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (tick < 40) return;
            require(owner.getMainNode().isActive(), "ME grid lost power");
            var storage = owner.getMainNode().getGrid().getStorageService().getInventory();
            if (tick == 40) {
                for (int key = 0; key < KEYS.length; key++) {
                    owner.getInterfaceLogic().getConfig().setStack(key, new GenericStack(KEYS[key], 1));
                    owner.setSlotUnlimited(key, true);
                    long initial = (long) count * (sparse && key == 0 ? 8 : 200_000);
                    require(storage.insert(KEYS[key], initial, Actionable.MODULATE, IActionSource.empty()) == initial,
                            "initial supply rejected");
                    supplied[key] += initial;
                }
                owner.setExportMode(ExportMode.AUTO);
            }
            // Replenish the first ore every tick, including while its previous batch is being consumed.
            long refill = (long) count * (sparse ? 8 : 512);
            require(storage.insert(KEYS[0], refill, Actionable.MODULATE, IActionSource.empty()) == refill,
                    "refill rejected");
            supplied[0] += refill;
            for (int target = 0; target < count; target++) {
                var stack = targets[target].getStackInSlot(MiningFactoryInventory.INPUT);
                if (tick % 5 == 0 && !stack.isEmpty()) {
                    for (int key = 0; key < KEYS.length; key++) {
                        if (!KEYS[key].matches(stack)) continue;
                        long taken = targets[target].extractItem(MiningFactoryInventory.INPUT, 512, false).getCount();
                        consumed[target][key] += taken;
                        maximumWait[0] = Math.max(maximumWait[0], tick - Math.max(40, lastConsumed[target][key]));
                        lastConsumed[target][key] = tick;
                        break;
                    }
                }
                if (tick >= 240) {
                    for (int key = 0; key < KEYS.length; key++) {
                        // Scarce shared ME supply is not a per-target reservation. The two
                        // fully stocked backlogs must progress at every target regardless.
                        if (sparse && count > 1 && key == 0) continue;
                        require(lastConsumed[target][key] > 0 && tick - lastConsumed[target][key] <= 160,
                                "ore starved: local=" + local + ", speed=" + speed + ", target=" + target
                                        + ", key=" + key + ", consumed=" + Arrays.toString(consumed[target]));
                    }
                }
            }
            if (tick != 640) return;
            for (int key = 0; key < KEYS.length; key++) {
                long accounted = storage.extract(KEYS[key], Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
                long totalConsumed = 0;
                for (int target = 0; target < count; target++) {
                    var stack = targets[target].getStackInSlot(MiningFactoryInventory.INPUT);
                    accounted += consumed[target][key] + (KEYS[key].matches(stack) ? stack.getCount() : 0);
                    totalConsumed += consumed[target][key];
                }
                require(accounted == supplied[key], "per-key ownership changed for " + KEYS[key]);
                require(totalConsumed > 0, "shared supply was never consumed for " + KEYS[key]);
            }
            require(owner.benchmarkBufferedImportAmount() == 0, "unexpected export overflow");
            LoggerFactory.getLogger("ae2lt-export-fairness").info(
                    "Single-input fairness local={} speed={} targets={} sparse={} maxWait={} firstTargetConsumed={}",
                    local, speed, count, sparse, maximumWait[0], Arrays.toString(consumed[0]));
            helper.succeed();
        });
    }

    private record Fixture(OverloadedInterfaceBlockEntity owner, MiningFactoryBlockEntity[] factories) {}

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
