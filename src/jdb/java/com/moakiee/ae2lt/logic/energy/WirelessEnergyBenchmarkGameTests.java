package com.moakiee.ae2lt.logic.energy;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import com.google.gson.GsonBuilder;
import com.moakiee.ae2lt.blockentity.AdvancedWirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.grid.FrequencySecurityLevel;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.logic.management.ManagementQuery;
import com.moakiee.ae2lt.logic.management.ManagementSnapshot;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.mojang.authlib.GameProfile;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Opt-in real server workload. Only compiled by wireless-energy-benchmark.init.gradle. */
@GameTestHolder("ae2lt")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "ae2lt")
public final class WirelessEnergyBenchmarkGameTests {
    private static Run active;
    private static final com.sun.management.ThreadMXBean ALLOCATIONS = allocationCounter();
    private static com.sun.management.ThreadMXBean allocationCounter() {
        var bean = ManagementFactory.getThreadMXBean();
        if (!(bean instanceof com.sun.management.ThreadMXBean counter)
                || !counter.isThreadAllocatedMemorySupported()) return null;
        if (!counter.isThreadAllocatedMemoryEnabled()) counter.setThreadAllocatedMemoryEnabled(true);
        return counter;
    }
    private static long allocatedBytes() {
        return ALLOCATIONS == null ? 0 : ALLOCATIONS.getThreadAllocatedBytes(Thread.currentThread().threadId());
    }
    private static long configureAffinity() {
        long mask = Long.getLong("ae2lt.feBench.affinity", 0);
        if (mask == 0) return 0;
        if (!com.sun.jna.Platform.isWindows() || mask < 0) throw new IllegalArgumentException("Invalid Windows affinity mask");
        var kernel = com.sun.jna.platform.win32.Kernel32.INSTANCE;
        var process = kernel.GetCurrentProcess();
        var actual = new com.sun.jna.platform.win32.BaseTSD.ULONG_PTRByReference();
        var system = new com.sun.jna.platform.win32.BaseTSD.ULONG_PTRByReference();
        if (!kernel.SetProcessAffinityMask(process, new com.sun.jna.platform.win32.BaseTSD.ULONG_PTR(mask))
                || !kernel.GetProcessAffinityMask(process, actual, system) || actual.getValue().longValue() != mask)
            throw new IllegalStateException("Cannot apply benchmark process affinity");
        return mask;
    }
    private static final class Run {
        final int targets = Integer.getInteger("ae2lt.feBench.targets", 256);
        final int grids = Math.max(1, (targets + 255) / 256);
        final int warmup = Integer.getInteger("ae2lt.feBench.warmup", 300);
        final int samples = Integer.getInteger("ae2lt.feBench.samples", 1200);
        final int queries = Integer.getInteger("ae2lt.feBench.queries", 0);
        final boolean bind = Boolean.parseBoolean(System.getProperty("ae2lt.feBench.bind", "true"));
        final boolean saturated = Boolean.getBoolean("ae2lt.feBench.saturated");
        final boolean highBudget = Boolean.getBoolean("ae2lt.feBench.highBudget");
        final List<AdvancedWirelessOverloadedControllerBlockEntity> controllers = new ArrayList<>();
        final List<ServerPlayer> players = new ArrayList<>();
        final List<WirelessEnergyTestMachine> machines = new ArrayList<>();
        final long[] nanos = new long[samples], offered = new long[grids];
        final long[] postNanos = new long[samples], tickBytes = new long[samples], postBytes = new long[samples];
        long postStarted, tickAllocated, postAllocated;
        long affinityMask;
        long initialFe, totalReceived, sampleReceived, sampleDelivered, gcCount, gcMs, maxHeap, started;
        int ticks, simulations, receives, maxSimulations, maxReceives, queryCount;
        String failure;
    }
    @GameTest(template = "wireless_io_empty", timeoutTicks = 24000)
    public static void sustainedRealFeEndpointsWithServerTickMeasurements(GameTestHelper h) {
        var r = new Run();
        r.affinityMask = configureAffinity();
        h.assertTrue(r.targets >= 0 && r.targets <= 8192 && r.warmup >= 1 && r.samples >= 128
                && r.warmup + r.samples < 23000 && r.queries >= 0 && r.queries <= 128, "invalid benchmark parameters");
        if (r.highBudget) {
            WirelessEnergySettings.globalVisits.set(8192);
            WirelessEnergySettings.networkTargets.set(1024);
            WirelessEnergySettings.networkFe.set(Integer.MAX_VALUE);
        }
        var level = h.getLevel();
        for (int grid = 0; grid < r.grids; grid++) {
            var base = new BlockPos(3200 + grid % 8 * 64, 64, 3200 + grid / 8 * 64);
            // One target chunk and one controller chunk, identical for active/control runs.
            for (int z = 0; z < 2; z++) {
                level.setChunkForced(base.getX() >> 4, (base.getZ() >> 4) + z, true);
                level.getChunk(base.getX() >> 4, (base.getZ() >> 4) + z);
            }
            var cpos = base.offset(2, 0, 18);
            level.setBlockAndUpdate(cpos, ModBlocks.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER.get().defaultBlockState());
            level.setBlockAndUpdate(cpos.east(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
            level.setBlockAndUpdate(cpos.west(), AEBlocks.DRIVE.block().defaultBlockState());
            var drive = (DriveBlockEntity) level.getBlockEntity(cpos.west());
            drive.getInternalInventory().setItemDirect(0, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("appflux:fe_256m_cell"))));
            var controller = (AdvancedWirelessOverloadedControllerBlockEntity) level.getBlockEntity(cpos);
            var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "FE_Bench_" + grid));
            int frequency = WirelessFrequencyManager.get().createFrequency(player, "FE benchmark " + grid,
                    0xffffff, FrequencySecurityLevel.PRIVATE, "").getId();
            controller.setFrequency(frequency); r.controllers.add(controller); r.players.add(player);
            for (int i = 0; i < Math.min(256, r.targets - grid * 256); i++) {
                var pos = base.offset(i % 16, 0, i / 16);
                level.setBlock(pos, WirelessEnergyTestMachine.BLOCK.defaultBlockState(), 18);
                var machine = (WirelessEnergyTestMachine) level.getBlockEntity(pos);
                machine.drain = !r.saturated;
                if (r.saturated) machine.energy.prime(100000);
                final int index = grid;
                machine.duringSimulation = () -> r.simulations++;
                machine.afterReceive = () -> {
                    r.receives++; r.offered[index] += machine.energy.getEnergyStored();
                    // Each machine is drained before the production post-tick sender.
                    r.totalReceived += machine.energy.getEnergyStored();
                };
                r.machines.add(machine);
            }
        }
        h.runAfterDelay(80, () -> {
            var registry = WirelessEnergyEndpoints.get(level.getServer());
            for (int grid = 0; grid < r.grids; grid++) {
                var c = r.controllers.get(grid); var p = r.players.get(grid);
                h.assertTrue(c.getMainNode().isActive(), "benchmark grid inactive");
                var inventory = c.getMainNode().getGrid().getStorageService().getInventory();
                long inserted = inventory.insert(AppFluxBridge.FE_KEY, 1_000_000_000_000L, Actionable.MODULATE, IActionSource.empty());
                long maxPerTick = Math.min(WirelessEnergySettings.networkFe.get(), 256L * WirelessEnergySettings.targetFe.get());
                h.assertTrue(inserted > (r.warmup + r.samples + 10L) * maxPerTick, "insufficient real FE cell capacity");
                r.initialFe += inserted;
                if (r.bind) for (int i = grid * 256; i < Math.min(r.targets, (grid + 1) * 256); i++) {
                    var machine = r.machines.get(i); var pos = machine.getBlockPos();
                    p.setPos(pos.getX(), pos.getY(), pos.getZ() - 1);
                    h.assertTrue(registry.setBound(p, c.getFrequencyId(), level, pos, Direction.NORTH, true).equals("connected"), "benchmark bind failed at " + i);
                }
            }
            active = r;
            h.runAfterDelay(r.warmup + r.samples + 2, () -> finish(h, r));
        });
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beforeTick(ServerTickEvent.Pre event) {
        var r = active; if (r == null || r.ticks >= r.warmup + r.samples) return;
        r.started = System.nanoTime(); r.simulations = 0; r.receives = 0; Arrays.fill(r.offered, 0);
        r.tickAllocated = allocatedBytes();
        if (r.ticks == r.warmup) { r.gcCount = gc(false); r.gcMs = gc(true); r.sampleReceived = r.totalReceived; }
        if (r.ticks % 20 == 0) for (int q = 0; q < r.queries; q++) {
            int i = q % r.grids;
            var snapshot = ManagementSnapshot.build(r.players.get(i), r.controllers.get(i), ManagementQuery.initial(true));
            if (snapshot.rows().size() > ManagementQuery.PAGE_SIZE) r.failure = "unbounded management page";
            r.queryCount++;
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beforePostTick(ServerTickEvent.Post event) {
        var r = active; if (r == null || r.started == 0 || r.ticks >= r.warmup + r.samples) return;
        r.postStarted = System.nanoTime(); r.postAllocated = allocatedBytes();
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void afterTick(ServerTickEvent.Post event) {
        var r = active; if (r == null || r.started == 0 || r.ticks >= r.warmup + r.samples) return;
        long elapsed = System.nanoTime() - r.started;
        if (r.ticks >= r.warmup) {
            int sample = r.ticks - r.warmup;
            r.nanos[sample] = elapsed;
            r.postNanos[sample] = System.nanoTime() - r.postStarted;
            r.tickBytes[sample] = allocatedBytes() - r.tickAllocated;
            r.postBytes[sample] = allocatedBytes() - r.postAllocated;
        }
        r.maxHeap = Math.max(r.maxHeap, Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
        r.maxSimulations = Math.max(r.maxSimulations, r.simulations); r.maxReceives = Math.max(r.maxReceives, r.receives);
        if (r.simulations > WirelessEnergySettings.globalVisits.get() || r.receives > WirelessEnergySettings.globalVisits.get())
            r.failure = "global visit budget exceeded";
        for (long amount : r.offered) if (amount > WirelessEnergySettings.networkFe.get()) r.failure = "grid FE budget exceeded";
        r.ticks++; r.started = 0;
        if (r.ticks == r.warmup + r.samples) r.sampleDelivered = r.totalReceived - r.sampleReceived;
    }
    private static void finish(GameTestHelper h, Run r) {
        active = null;
        h.assertTrue(r.ticks == r.warmup + r.samples, "incomplete timing samples");
        h.assertTrue(r.failure == null, String.valueOf(r.failure));
        long currentFe = 0, uncertain = 0, escrowFe = 0;
        for (var c : r.controllers) {
            currentFe += c.getMainNode().getGrid().getStorageService().getInventory().extract(AppFluxBridge.FE_KEY, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
            var escrow = c.getMainNode().getGrid().getService(WirelessEnergyGridService.class).escrow(c.getMainNode().getNode());
            escrowFe += escrow.available(); uncertain += escrow.uncertain();
        }
        h.assertTrue(r.initialFe == currentFe + escrowFe + uncertain + r.totalReceived, "real cell/ledger/receiver conservation failed");
        long minReceived = r.machines.stream().mapToLong(m -> m.received).min().orElse(0);
        if (r.bind && !r.saturated && r.targets > 0) h.assertTrue(minReceived > 0, "endpoint starvation during sustained workload");
        if (!r.bind || r.saturated) h.assertTrue(r.totalReceived == 0, "idle or full machine received FE");
        var report = new LinkedHashMap<String, Object>();
        report.put("scenario", System.getProperty("ae2lt.feBench.scenario", "custom"));
        report.put("gitHead", System.getProperty("ae2lt.feBench.gitHead", "unknown"));
        report.put("java", System.getProperty("java.runtime.version"));
        report.put("processors", Runtime.getRuntime().availableProcessors());
        report.put("affinityMask", r.affinityMask);
        report.put("maxHeapBytes", Runtime.getRuntime().maxMemory());
        report.put("targets", r.targets); report.put("grids", r.grids); report.put("bound", r.bind); report.put("saturated", r.saturated);
        report.put("highBudget", r.highBudget);
        report.put("warmupTicks", r.warmup); report.put("sampleTicks", r.samples);
        report.put("tickEventMs", stats(r.nanos)); report.put("rawTickEventNanos", r.nanos);
        report.put("measurement", "HIGHEST ServerTickEvent.Pre through LOWEST ServerTickEvent.Post; excludes sleep, setup and final assertions; includes production post-tick FE and scheduled snapshot builds");
        report.put("postTickMs", stats(r.postNanos)); report.put("rawPostTickNanos", r.postNanos);
        report.put("postMeasurement", "HIGHEST through LOWEST ServerTickEvent.Post; includes FE endpoint/admission dispatch and other post-tick subscribers, excludes world ticking and management query builds");
        report.put("allocationCounterSupported", ALLOCATIONS != null);
        report.put("meanTickAllocatedBytes", Arrays.stream(r.tickBytes).average().orElseThrow());
        report.put("meanPostTickAllocatedBytes", Arrays.stream(r.postBytes).average().orElseThrow());
        report.put("gcCollections", gc(false) - r.gcCount); report.put("gcMillis", gc(true) - r.gcMs); report.put("peakUsedHeapBytes", r.maxHeap);
        report.put("sampleDeliveredFePerTick", r.sampleDelivered / (double) r.samples);
        report.put("totalDeliveredFe", r.totalReceived); report.put("minimumEndpointDeliveredFe", minReceived);
        report.put("maxSimulationCallsPerTick", r.maxSimulations); report.put("maxReceiveCallsPerTick", r.maxReceives);
        report.put("managementSnapshotBuilds", r.queryCount); report.put("networkFePerTick", WirelessEnergySettings.networkFe.get());
        report.put("globalVisitsPerTick", WirelessEnergySettings.globalVisits.get()); report.put("conservationAndBudgetsPassed", true);
        try {
            var path = Path.of(System.getProperty("ae2lt.feBench.report")); Files.createDirectories(path.toAbsolutePath().getParent());
            Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch (Exception e) { throw new IllegalStateException("Cannot write benchmark report", e); }
        System.out.println("FE_BENCHMARK_PASS " + report.get("scenario") + " " + report.get("tickEventMs"));
        h.succeed();
    }
    private static Map<String, Object> stats(long[] data) {
        var sorted = data.clone(); Arrays.sort(sorted);
        var result = new LinkedHashMap<String, Object>();
        result.put("mean", Arrays.stream(data).average().orElseThrow() / 1e6);
        for (int p : new int[] {50, 95, 99}) result.put("p" + p, sorted[(int) Math.ceil(sorted.length * p / 100.0) - 1] / 1e6);
        result.put("max", sorted[sorted.length - 1] / 1e6);
        result.put("over50ms", Arrays.stream(data).filter(n -> n > 50_000_000L).count());
        return result;
    }
    private static long gc(boolean millis) {
        return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b -> Math.max(0, millis ? b.getCollectionTime() : b.getCollectionCount())).sum();
    }
}
