package com.moakiee.ae2lt.machine.largeoverload;

import java.util.*;
import appeng.api.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.me.service.CraftingService;
import com.moakiee.ae2lt.crafting.timewheel.*;
import com.moakiee.ae2lt.me.key.LightningKey;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;

/** Real time-wheel CPU execution with precomputed plans. Planning time is deliberately separate. */
@GameTestHolder("ae2lt_large_factory")
@PrefixGameTestTemplate(false)
public final class LargeFactoryActiveTimingTests {
    private static final AEKey STONE = LargeFactoryGameTests.STONE, DIAMOND = LargeFactoryGameTests.DIAMOND;
    private static final AEKey EMERALD = AEItemKey.of(Items.EMERALD);
    private record Sample(long tick, int factory) { }

    @GameTest(template = "wide", batch = "large_factory_active_timing", timeoutTicks = 1400)
    public static void activeBatchesChainsAndMultipleRequestsUseActualCpu(GameTestHelper h) { new Run(h).start(); }

    private static final class Run {
        final GameTestHelper h;
        final List<LargeFactoryGameTests.Fixture> factories = new ArrayList<>();
        final List<TimeWheelCraftingCPU> cpus = new ArrayList<>();
        final List<appeng.crafting.CraftingPlan> plans = new ArrayList<>();
        final Map<BlockPos, Integer> owners = new HashMap<>();
        final Map<Sample, Long> factoryTimes = new HashMap<>(), cpuTimes = new HashMap<>(), submitTimes = new HashMap<>();
        final Map<Sample, List<Long>> activeCalls = new HashMap<>();
        final Map<Long, Integer> drivenTicks = new HashMap<>();
        final com.google.gson.JsonArray results = new com.google.gson.JsonArray();
        final IPatternDetails[] first = new IPatternDetails[16], second = new IPatternDetails[16];
        long calls, enumerations, gc;
        int phase, tick;
        Run(GameTestHelper h) {
            this.h = h;
            for (int z = 0; z < 4; z++) for (int x = 0; x < 4; x++) {
                var f = new LargeFactoryGameTests.Fixture(h, false, false, new BlockPos(x * 16, 0, z * 16));
                int index = factories.size(); factories.add(f);
                if (index >= 8) h.setBlock(f.pos(LargeFactoryStructure.CORE), LargeFactoryRegistration.block(LargeFactoryComponent.CORE_T4));
                owners.put(f.controller.getBlockPos(), index);
                owners.put(f.hatch.getBlockPos(), index); owners.put(f.expanded.getBlockPos(), index); owners.put(f.crystal.getBlockPos(), index);
            }
        }
        long now() { return h.getLevel().getGameTime(); }
        void start() {
            h.startSequence().thenWaitUntil(() -> h.assertTrue(factories.stream().allMatch(f -> f.expanded.ready()), "active fixtures formed"))
                    .thenExecute(this::prepare)
                    .thenWaitUntil(() -> {
                        for (int i = 0; i < 16; i++) {
                            var f = factories.get(i);
                            h.assertTrue(!f.expanded.patternIndexing(), "144 patterns indexed");
                            var service = (CraftingService) f.expanded.getMainNode().getGrid().getCraftingService();
                            h.assertTrue(service.getProviders(first[i]).iterator().hasNext() && service.getProviders(second[i]).iterator().hasNext(), "actual AE2 provider publication");
                        }
                    }).thenExecute(() -> {
                        LargeFactoryTiming.setReceiver((host, section, nanos) -> {
                            Integer owner = owners.get(host.getBlockPos());
                            if (owner == null) return;
                            var key = new Sample(now(), owner);
                            factoryTimes.merge(key, nanos, Long::sum);
                            if (section.equals("active")) activeCalls.computeIfAbsent(key, unused -> new ArrayList<>()).add(nanos);
                        });
                        reset(); h.runAfterDelay(1, this::drive);
                    });
        }
        void prepare() {
            var irrelevant = new ArrayList<AEKey>();
            for (int i = 0; i < 2048; i++) {
                var item = new ItemStack(Items.PAPER); final int key = i;
                net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, item, tag -> tag.putInt("ActiveTimingKey", key));
                irrelevant.add(AEItemKey.of(item));
            }
            for (int i = 0; i < 16; i++) {
                var f = factories.get(i);
                irrelevant.forEach(key -> f.store.put(key, 64));
                f.store.put(STONE, 1_000_000_000_000L); f.store.put(LightningKey.HIGH_VOLTAGE, 1_000_000_000_000L);
                var grid = f.expanded.getMainNode().getGrid();
                grid.getStorageService().addGlobalStorageProvider(m -> m.mount(f.store, 0));
                for (int slot = 0; slot < 143; slot++) f.expanded.inventory().setItemDirect(slot, PatternDetailsHelper.encodeProcessingPattern(
                        List.of(new GenericStack(STONE, slot + 1)), List.of(new GenericStack(DIAMOND, 2L * (slot + 1)))));
                f.expanded.inventory().setItemDirect(143, PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(DIAMOND, 1)), List.of(new GenericStack(EMERALD, 3))));
                first[i] = PatternDetailsHelper.decodePattern(f.expanded.inventory().getStackInSlot(0), h.getLevel());
                second[i] = PatternDetailsHelper.decodePattern(f.expanded.inventory().getStackInSlot(143), h.getLevel());
                var host = new TimeWheelCraftingCpuHost() {
                    @Override public boolean isCpuActive() { return true; }
                    @Override public appeng.api.networking.IGrid getGrid() { return grid; }
                    @Override public IActionSource getActionSource() { return IActionSource.empty(); }
                    @Override public net.minecraft.world.level.Level getLevel() { return h.getLevel(); }
                    @Override public void markCpuDirty() { }
                    @Override public Component getDisplayName() { return Component.literal("Active factory timing CPU"); }
                };
                cpus.add(new TimeWheelCraftingCPU(host, Long.MAX_VALUE, 31, Long.MAX_VALUE, false));
            }
        }
        long inputs(int index) { return phase == 2 ? 1 : index < 8 ? phase == 1 ? 341 : 1024 : 1_048_576; }
        void reset() {
            tick = 0; factoryTimes.clear(); cpuTimes.clear(); submitTimes.clear(); activeCalls.clear(); drivenTicks.clear(); plans.clear();
            calls = factories.stream().mapToLong(f -> f.store.calls).sum(); enumerations = factories.stream().mapToLong(f -> f.store.enumerations).sum();
            gc = gc();
            for (int i = 0; i < 16; i++) {
                long count = inputs(i);
                var used = new KeyCounter(); used.add(STONE, count);
                plans.add(new appeng.crafting.CraftingPlan(new GenericStack(phase == 1 ? EMERALD : DIAMOND, count * (phase == 1 ? 6 : 2)),
                        100, false, false, used, new KeyCounter(), new KeyCounter(),
                        phase == 1 ? Map.of(first[i], count, second[i], count * 2) : Map.of(first[i], count)));
            }
        }
        void drive() {
            if (tick == 160) {
                report();
                if (++phase == 3) {
                    LargeFactoryTiming.setReceiver(null);
                    try { java.nio.file.Files.writeString(java.nio.file.Path.of("large-factory-active-timing.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(results) + "\n"); }
                    catch (java.io.IOException e) { throw new AssertionError(e); }
                    h.succeed(); return;
                }
                reset();
            }
            drivenTicks.put(now(), tick++);
            for (int i = 0; i < 16; i++) {
                var f = factories.get(i); var cpu = cpus.get(i); var grid = f.expanded.getMainNode().getGrid();
                long inputBefore = f.store.get(STONE), lightningBefore = f.store.get(LightningKey.HIGH_VOLTAGE);
                AEKey output = phase == 1 ? EMERALD : DIAMOND;
                long outputBefore = f.store.get(output);
                long cpuNanos = 0, submitNanos = 0;
                int requests = phase == 2 ? 32 : 1;
                for (int request = 0; request < requests; request++) {
                    long started = System.nanoTime();
                    var submitted = cpu.getCraftingLogic().trySubmitJob(grid, plans.get(i), IActionSource.empty(), null);
                    long afterSubmit = System.nanoTime();
                    h.assertTrue(submitted.successful(), "real CPU accepted precomputed plan: " + submitted);
                    long tickStart = System.nanoTime();
                    cpu.getCraftingLogic().tickCraftingLogic(grid.getEnergyService(), (CraftingService) grid.getCraftingService(), 32, Long.MAX_VALUE);
                    long afterTick = System.nanoTime();
                    submitNanos += afterSubmit - started; cpuNanos += afterTick - tickStart;
                    h.assertTrue(!cpu.getCraftingLogic().hasJob() && !cpu.isBusy(), "same-tick active batch completes: " + phase + " / " + i + " / " + f.expanded.status());
                }
                var sample = new Sample(now(), i); cpuTimes.put(sample, cpuNanos); submitTimes.put(sample, submitNanos);
                long amount = inputs(i) * requests;
                h.assertTrue(inputBefore - f.store.get(STONE) == amount && lightningBefore - f.store.get(LightningKey.HIGH_VOLTAGE) == amount * (phase == 1 ? 3 : 1), "exact active input and lightning receipts");
                h.assertTrue(f.store.get(output) - outputBefore == amount * (phase == 1 ? 6 : 2) && f.expanded.account().empty(), "exact active products, no retained duplicates");
            }
            h.runAfterDelay(1, this::drive);
        }
        static long gc() { return java.lang.management.ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b -> Math.max(0, b.getCollectionTime())).sum(); }
        void report() {
            h.assertTrue(factories.stream().mapToLong(f -> f.store.enumerations).sum() == enumerations, "active hot path never enumerates the full network");
            for (int tier = 0; tier < 2; tier++) {
                final int group = tier;
                java.util.function.Predicate<Sample> warm = key -> key.factory() / 8 == group && drivenTicks.getOrDefault(key.tick(), -1) >= 96;
                var total = factoryTimes.entrySet().stream().filter(e -> warm.test(e.getKey())).map(Map.Entry::getValue).sorted().toList();
                var cpu = cpuTimes.entrySet().stream().filter(e -> warm.test(e.getKey())).map(Map.Entry::getValue).sorted().toList();
                var submit = submitTimes.entrySet().stream().filter(e -> warm.test(e.getKey())).map(Map.Entry::getValue).sorted().toList();
                var callbacks = activeCalls.entrySet().stream().filter(e -> warm.test(e.getKey())).flatMap(e -> e.getValue().stream()).sorted().toList();
                h.assertTrue(total.size() == 512 && cpu.size() == 512, "512 complete active factory ticks per tier");
                var result = new com.google.gson.JsonObject();
                result.addProperty("scenario", List.of("one_batch_per_tick", "two_stage_0t_chain", "32_small_jobs_per_tick").get(phase));
                result.addProperty("tier", tier == 0 ? "T1" : "T4_unlimited"); result.addProperty("warmup_ticks", 96); result.addProperty("sample_ticks", 64);
                result.addProperty("total_factories", 16); result.addProperty("total_patterns", 2304); result.addProperty("irrelevant_network_keys", 32768);
                result.addProperty("jobs_per_factory_tick", phase == 2 ? 32 : 1);
                result.addProperty("source_operations_per_factory_tick", inputs(tier * 8) * (phase == 1 ? 3 : phase == 2 ? 32 : 1));
                result.add("factory_complete_tick", stats(total)); result.add("provider_callback", stats(callbacks));
                result.add("cpu_execution_tick_including_factory", stats(cpu)); result.add("cpu_submission", stats(submit));
                var all = factoryTimes.entrySet().stream().filter(e -> e.getKey().factory() / 8 == group && drivenTicks.containsKey(e.getKey().tick())).mapToLong(Map.Entry::getValue);
                result.addProperty("including_initial_binding_max_us", all.max().orElseThrow() / 1000.0);
                result.addProperty("all_factories_phase_storage_calls", factories.stream().mapToLong(f -> f.store.calls).sum() - calls);
                result.addProperty("full_inventory_enumerations", 0); result.addProperty("process_gc_ms", gc() - gc);
                results.add(result);
            }
        }
        com.google.gson.JsonObject stats(List<Long> values) {
            h.assertTrue(!values.isEmpty(), "actual active dispatch samples");
            var json = new com.google.gson.JsonObject(); json.addProperty("samples", values.size());
            json.addProperty("mean_us", values.stream().mapToLong(Long::longValue).average().orElseThrow() / 1000);
            json.addProperty("p95_us", values.get((int) (values.size() * .95)) / 1000.0);
            json.addProperty("p99_us", values.get((int) (values.size() * .99)) / 1000.0);
            json.addProperty("max_us", values.getLast() / 1000.0); json.addProperty("samples_over_50us", values.stream().filter(n -> n > 50_000).count());
            return json;
        }
    }
}
