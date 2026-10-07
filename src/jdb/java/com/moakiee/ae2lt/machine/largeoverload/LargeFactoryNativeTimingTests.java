package com.moakiee.ae2lt.machine.largeoverload;

import java.util.*;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.*;
import com.moakiee.ae2lt.me.key.LightningKey;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;

/** Measures actual automatic ticks; no direct passive calls or scheduler overrides. */
@GameTestHolder("ae2lt_large_factory")
@PrefixGameTestTemplate(false)
public final class LargeFactoryNativeTimingTests {
    @GameTest(template = "wide", batch = "large_factory_native_timing", timeoutTicks = 2200)
    public static void completeAutomaticTicksIncludeColdStartAndRecovery(GameTestHelper h) {
        new Run(h).start();
    }

    private record Sample(long tick, int factory) { }
    private static final class Run {
        final GameTestHelper h;
        final List<LargeFactoryGameTests.Fixture> factories = new ArrayList<>();
        final Map<BlockPos, Integer> owners = new HashMap<>();
        final Map<Sample, Long> samples = new HashMap<>();
        final Map<String, Long> sections = new TreeMap<>();
        final com.google.gson.JsonArray results = new com.google.gson.JsonArray();
        final String[] stages = {"missing_inputs", "continuous_completions", "missing_lightning", "missing_fe", "full_output", "recovered"};
        String phase = "cold_formation";
        long since, calls, enumerations, products, gc;
        int stage = -1;
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
            reset();
            LargeFactoryTiming.setReceiver((host, section, nanos) -> {
                Integer owner = owners.get(host.getBlockPos());
                if (owner == null) return;
                samples.merge(new Sample(now(), owner), nanos, Long::sum);
                sections.merge(section, nanos, Long::sum);
            });
            h.runAfterDelay(1, this::poll);
        }
        void reset() {
            since = now(); samples.clear(); sections.clear();
            calls = factories.stream().mapToLong(f -> f.store.calls).sum();
            enumerations = factories.stream().mapToLong(f -> f.store.enumerations).sum();
            products = factories.stream().mapToLong(f -> f.store.get(LargeFactoryGameTests.DIAMOND)).sum();
            gc = gc();
        }
        static long gc() { return java.lang.management.ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b -> Math.max(0, b.getCollectionTime())).sum(); }
        void poll() {
            if (phase.equals("cold_formation")) {
                if (factories.stream().allMatch(f -> f.expanded.ready())) {
                    report(0);
                    var irrelevant = new ArrayList<AEKey>();
                    for (int i = 0; i < 2048; i++) {
                        var item = new ItemStack(Items.PAPER); final int key = i;
                        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, item, tag -> tag.putInt("NativeTimingKey", key));
                        irrelevant.add(AEItemKey.of(item));
                    }
                    for (var f : factories) {
                        irrelevant.forEach(key -> f.store.put(key, 64));
                        f.expanded.getMainNode().getGrid().getStorageService().addGlobalStorageProvider(m -> m.mount(f.store, 0));
                        for (int slot = 0; slot < 144; slot++) f.expanded.inventory().setItemDirect(slot, PatternDetailsHelper.encodeProcessingPattern(
                                List.of(new GenericStack(LargeFactoryGameTests.STONE, slot + 1)), List.of(new GenericStack(LargeFactoryGameTests.DIAMOND, 2L * (slot + 1)))));
                    }
                    phase = "cold_pattern_publication"; reset();
                }
            } else if (phase.equals("cold_pattern_publication")) {
                if (factories.stream().noneMatch(f -> f.expanded.patternIndexing())) {
                    report(0);
                    for (var f : factories) {
                        h.assertTrue(f.expanded.entries().size() == 144, "all indexed patterns remain available");
                        f.expanded.togglePassive();
                    }
                    next();
                }
            } else if (now() - since >= 160) {
                report(96);
                if (stage == stages.length - 1) {
                    LargeFactoryTiming.setReceiver(null);
                    try { java.nio.file.Files.writeString(java.nio.file.Path.of("large-factory-native-timing.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(results) + "\n"); }
                    catch (java.io.IOException e) { throw new AssertionError(e); }
                    h.succeed(); return;
                }
                next();
            }
            h.runAfterDelay(1, this::poll);
        }
        void next() {
            phase = stages[++stage];
            for (var f : factories) {
                f.store.accept = !phase.equals("full_output");
                f.store.put(LargeFactoryGameTests.STONE, phase.equals("missing_inputs") ? 0 : 100_000_000_000L);
                f.store.put(LightningKey.HIGH_VOLTAGE, phase.equals("missing_lightning") ? 0 : 100_000_000_000L);
                if (f.controller.allowNetworkEnergy() == phase.equals("missing_fe")) f.controller.toggleNetworkEnergy();
                f.controller.wakeNextTick();
            }
            reset();
        }
        void report(int warmup) {
            long output = factories.stream().mapToLong(f -> f.store.get(LargeFactoryGameTests.DIAMOND)).sum() - products;
            long enumerated = factories.stream().mapToLong(f -> f.store.enumerations).sum() - enumerations;
            h.assertTrue(enumerated == 0, "automatic factory work never enumerates full network inventory");
            if (phase.equals("continuous_completions") || phase.equals("recovered")) h.assertTrue(output > 0, "normal scheduling must actually produce after recovery");
            if (phase.equals("continuous_completions")) for (int i = 0; i < 8; i++)
                h.assertTrue(factories.get(i).store.get(LargeFactoryGameTests.DIAMOND) >= 128_000,
                        "completion wake must sustain production across ticks, not fall back to idle polling");
            for (int group = 0; group < 2; group++) {
                final int tier = group;
                var all = samples.entrySet().stream().filter(e -> e.getKey().factory() / 8 == tier && e.getKey().tick() < now()).map(Map.Entry::getValue).sorted().toList();
                var warm = samples.entrySet().stream().filter(e -> e.getKey().factory() / 8 == tier && e.getKey().tick() >= since + warmup && e.getKey().tick() < now()).map(Map.Entry::getValue).sorted().toList();
                h.assertTrue(!warm.isEmpty(), "native complete tick samples");
                var result = new com.google.gson.JsonObject();
                result.addProperty("scenario", phase); result.addProperty("tier", group == 0 ? "T1" : "T4_unlimited");
                result.addProperty("factories_in_tier", 8); result.addProperty("total_factories", 16); result.addProperty("total_patterns", phase.equals("cold_formation") ? 0 : 2304);
                result.addProperty("total_network_keys", phase.equals("cold_formation") ? 0 : 32768); result.addProperty("warmup_ticks", warmup);
                result.addProperty("sample_factory_ticks", warm.size()); result.addProperty("mean_us", warm.stream().mapToLong(Long::longValue).average().orElseThrow() / 1000);
                result.addProperty("p95_us", warm.get(Math.min(warm.size() - 1, (int) (warm.size() * .95))) / 1000.0);
                result.addProperty("p99_us", warm.get(Math.min(warm.size() - 1, (int) (warm.size() * .99))) / 1000.0);
                result.addProperty("max_us", warm.getLast() / 1000.0); result.addProperty("ticks_over_50us", warm.stream().filter(n -> n > 50_000).count());
                result.addProperty("including_transition_max_us", all.getLast() / 1000.0);
                result.addProperty("all_factories_phase_storage_calls", factories.stream().mapToLong(f -> f.store.calls).sum() - calls);
                result.addProperty("all_factories_phase_produced_items", output); result.addProperty("full_inventory_enumerations", enumerated);
                result.addProperty("process_gc_ms", gc() - gc); result.add("all_factories_sections_ns", new com.google.gson.Gson().toJsonTree(sections));
                results.add(result);
            }
        }
    }
}
