package com.moakiee.ae2lt.blockentity;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.ConnectionState;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.ExportConfigEntry;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.ExportPlan;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.IOSpeedMode;
import com.moakiee.ae2lt.logic.energy.PowerCostUtil;
import com.moakiee.ae2lt.machine.lightningchamber.LargeStackItemHandler;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OverloadedInterfaceExportFairnessTest {
    private static final IActionSource SOURCE = IActionSource.empty();
    private static AEItemKey iron;
    private static AEItemKey gold;
    private static AEItemKey copper;

    @BeforeAll static void bootstrap() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        iron = AEItemKey.of(Items.RAW_IRON);
        gold = AEItemKey.of(Items.RAW_GOLD);
        copper = AEItemKey.of(Items.RAW_COPPER);
    }

    @Test void stockedCompetitorYieldsRefillAndWakesEvenAfterLongRejectionBackoff() {
        var model = new Model(List.of(iron, gold, copper), 4096);
        model.tick(0, IOSpeedMode.NORMAL);
        assertEquals(4096, model.handler.getStackInSlot(0).getCount());
        model.plan.transfers()[1].untilTick = 80;
        model.handler.extractItem(0, 512, false);
        model.tick(1, IOSpeedMode.NORMAL);
        assertEquals(3584, model.handler.getStackInSlot(0).getCount(), "incumbent must drain, not top up");
        assertTrue(model.plan.shouldYield(0));
        model.handler.extractItem(0, 4096, false);
        model.tick(2, IOSpeedMode.NORMAL);
        assertTrue(gold.matches(model.handler.getStackInSlot(0)), "first waiting key must win the empty slot");
    }

    @Test void missingAndInvalidKeysDoNotHoldTheOnlyUsableMaterial() {
        var model = new Model(List.of(iron, gold, copper), 4096);
        model.handler.rejected = copper;
        model.supply.put(gold, 0L);
        for (int tick = 0; tick < 500; tick++) {
            model.handler.extractItem(0, 256, false);
            model.tick(tick, IOSpeedMode.FAST);
            assertTrue(iron.matches(model.handler.getStackInSlot(0)));
            assertEquals(4096, model.handler.getStackInSlot(0).getCount());
        }
    }

    @Test void vanishedSupplyReleasesItsClaimAndLaterRefillCanCompeteAgain() {
        var model = new Model(List.of(iron, gold), 4096);
        model.tick(0, IOSpeedMode.FAST);
        model.supply.put(gold, 0L);
        model.handler.extractItem(0, 256, false);
        model.tick(1, IOSpeedMode.FAST);
        assertEquals(4096, model.handler.getStackInSlot(0).getCount());
        model.supply.put(gold, 1_000_000L);
        boolean delivered = false;
        for (int tick = 2; tick < 80; tick++) {
            model.handler.extractItem(0, 256, false);
            model.tick(tick, IOSpeedMode.FAST);
            delivered |= gold.matches(model.handler.getStackInSlot(0));
        }
        assertTrue(delivered);
    }

    @Test void boundedAndSingleKeyPlansDoNotScanOrProbeSupplyForFairness() {
        for (var entries : List.of(List.of(new ExportConfigEntry(iron, Long.MAX_VALUE)),
                List.of(new ExportConfigEntry(iron, 64), new ExportConfigEntry(gold, 64)),
                List.of(new ExportConfigEntry(iron, Long.MAX_VALUE), new ExportConfigEntry(iron, 64)))) {
            var state = new ConnectionState();
            var plan = state.exportPlan(iron.getType(), entries);
            var model = new Model(List.of(iron, gold), 4096);
            for (var transfer : plan.transfers()) transfer.rejected(0, IOSpeedMode.FAST);
            plan.prepare(model.handler, model.storage, SOURCE, 1);
            assertEquals(0, model.handler.validityChecks);
            assertEquals(0, model.simulations);
            plan.transferred(0);
            assertEquals(0, plan.startIndex());
        }
    }

    @Test void independentSlotsAndMultiInputRecipesKeepRefilling() {
        var model = new Model(List.of(iron, gold), 4096);
        model.handler = new Handler(2, 4096) {
            @Override public boolean isItemValid(int slot, ItemStack stack) {
                return slot == 0 ? iron.matches(stack) : gold.matches(stack);
            }
        };
        model.tick(0, IOSpeedMode.FAST);
        model.tick(1, IOSpeedMode.FAST);
        model.plan.prepare(model.handler, model.storage, SOURCE, 2);
        assertFalse(model.plan.shouldYield(0));
        assertFalse(model.plan.shouldYield(1));
        assertEquals(4096, model.handler.getStackInSlot(1).getCount());

        model.handler = new Handler(2, 4096);
        model.handler.setStackInSlot(0, iron.toStack(1));
        model.handler.setStackInSlot(1, iron.toStack(1));
        model.plan.transfers()[1].rejected(2, IOSpeedMode.FAST);
        model.plan.prepare(model.handler, model.storage, SOURCE, 3);
        assertFalse(model.plan.shouldYield(0), "a multi-input recipe may need more of the resident ingredient");
    }

    @Test void capabilityReplacementAndChangingFiltersDiscardObsoleteYield() {
        var model = new Model(List.of(iron, gold), 4096);
        model.tick(0, IOSpeedMode.FAST);
        model.handler.extractItem(0, 512, false);
        model.plan.prepare(model.handler, model.storage, SOURCE, 1);
        assertTrue(model.plan.shouldYield(0));
        model.handler.rejected = gold;
        model.plan.prepare(model.handler, model.storage, SOURCE, 2);
        assertFalse(model.plan.shouldYield(0));
        model.plan.prepare(null, model.storage, SOURCE, 3);
        assertFalse(model.plan.shouldYield(0));
        model.handler = new Handler(1, 4096);
        model.tick(4, IOSpeedMode.FAST);
        assertTrue(gold.matches(model.handler.getStackInSlot(0)));
    }

    @Test void duplicateKeysAndComponentVariantsShareNeitherPriorityNorContentsIncorrectly() {
        var named = iron.toStack(1);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("variant"));
        var variant = AEItemKey.of(named);
        var model = new Model(List.of(iron, iron, variant), 64);
        model.tick(0, IOSpeedMode.FAST);
        model.handler.extractItem(0, 16, false);
        model.plan.prepare(model.handler, model.storage, SOURCE, 1);
        assertTrue(model.plan.shouldYield(0));
        assertTrue(model.plan.shouldYield(1));
        model.handler.extractItem(0, 64, false);
        model.tick(1, IOSpeedMode.FAST);
        assertTrue(variant.matches(model.handler.getStackInSlot(0)));
        assertFalse(iron.matches(model.handler.getStackInSlot(0)));
    }

    @Test void planChangesAndModeResetCannotRetainRemovedWaiters() {
        var model = new Model(List.of(iron, gold), 64);
        model.tick(0, IOSpeedMode.FAST);
        var replacement = model.state.exportPlan(iron.getType(), List.of(new ExportConfigEntry(iron, Long.MAX_VALUE)));
        replacement.prepare(model.handler, model.storage, SOURCE, 1);
        assertFalse(replacement.shouldYield(0));
        model.state.resetWirelessIo(IOSpeedMode.NORMAL);
        var reset = model.state.exportPlan(iron.getType(), model.plan.entries());
        assertEquals(0, reset.startIndex());
        assertFalse(reset.transfers()[1].waitingForSpace);
    }

    @Test void boundedContenderCanDrainAnUnlimitedIncumbentWithoutPausingBoundedRefills() {
        var model = new Model(List.of(iron, gold), 4096);
        model.plan = model.state.exportPlan(iron.getType(), List.of(
                new ExportConfigEntry(iron, Long.MAX_VALUE), new ExportConfigEntry(gold, 64)));
        model.tick(0, IOSpeedMode.FAST);
        model.handler.extractItem(0, 512, false);
        model.tick(1, IOSpeedMode.FAST);
        assertTrue(model.plan.shouldYield(0));
        model.handler.extractItem(0, 4096, false);
        model.tick(2, IOSpeedMode.FAST);
        assertEquals(64, model.handler.getStackInSlot(0).getCount());
        model.tick(3, IOSpeedMode.FAST);
        assertFalse(model.plan.shouldYield(1));
        assertEquals(128, model.handler.getStackInSlot(0).getCount());
    }

    @Test void manyTargetsKeysRatesAndPhasesConserveItemsWithoutStarvation() {
        for (var mode : IOSpeedMode.values()) {
            for (int keyCount : new int[] {2, 3, 9, 27}) {
                var keys = new ArrayList<AEItemKey>();
                for (int key = 0; key < keyCount; key++) {
                    var stack = iron.toStack(1);
                    stack.set(DataComponents.CUSTOM_NAME, Component.literal("ore-" + key));
                    keys.add(AEItemKey.of(stack));
                }
                long totalCalls = 0;
                long maximumWait = 0;
                for (int target = 0; target < 32; target++) {
                    int capacity = target % 2 == 0 ? 4096 : 64;
                    int period = 1 + target % 5;
                    int consumedPerCycle = capacity / 4;
                    var model = new Model(keys, capacity);
                    model.supply.put(keys.getFirst(), 1L);
                    long[] consumed = new long[keyCount];
                    int[] last = new int[keyCount];
                    int deadline = keyCount * (4 * period + 3) + 100;
                    int ticks = Math.max(2000, deadline * 3);
                    for (int tick = 0; tick < ticks; tick++) {
                        model.supply.merge(keys.getFirst(), 1L, Long::sum);
                        if ((tick + target) % period == 0) {
                            var stack = model.handler.extractItem(0, consumedPerCycle, false);
                            for (int key = 0; key < keyCount; key++) {
                                if (keys.get(key).matches(stack)) {
                                    consumed[key] += stack.getCount();
                                    maximumWait = Math.max(maximumWait, tick - last[key]);
                                    last[key] = tick;
                                }
                            }
                        }
                        model.tick(tick, mode);
                        if (tick > deadline) {
                            for (int key = 0; key < keyCount; key++) {
                                assertTrue(tick - last[key] <= deadline,
                                        "starved mode=" + mode + " keys=" + keyCount + " target=" + target + " key=" + key);
                            }
                        }
                    }
                    for (int key = 0; key < keyCount; key++) {
                        var stack = model.handler.getStackInSlot(0);
                        long initial = key == 0 ? 1L + ticks : Model.INITIAL_SUPPLY;
                        assertEquals(initial, model.supply.get(keys.get(key)) + consumed[key]
                                + (keys.get(key).matches(stack) ? stack.getCount() : 0));
                        assertTrue(consumed[key] > 0);
                    }
                    totalCalls += model.simulations;
                    // Includes target simulations and fairness demand probes; no quadratic ME scans.
                    assertTrue(model.simulations <= (long) ticks * keyCount * 2);
                }
                System.out.printf("single-input-stress mode=%s targets=32 keys=%d maxWait=%d supplySimulations=%d%n",
                        mode, keyCount, maximumWait, totalCalls);
            }
        }
    }

    @Test void separatedDuplicatesCannotBuyExtraTurns() {
        var model = new Model(List.of(iron, gold, iron, copper), 64);
        var delivered = new ArrayList<AEItemKey>();
        for (int tick = 0; tick < 12; tick++) {
            model.handler.extractItem(0, 64, false);
            model.tick(tick, IOSpeedMode.FAST);
            delivered.add(AEItemKey.of(model.handler.getStackInSlot(0)));
        }
        assertEquals(List.of(iron, gold, copper, iron, gold, copper, iron, gold, copper, iron, gold, copper), delivered);
    }

    @Test void smallInitialSupplyCanReachTheMinimumRecipeBatch() {
        for (var mode : IOSpeedMode.values()) {
            var model = new Model(List.of(iron, gold), 64);
            model.supply.put(iron, 8L);
            long[] consumed = new long[2];
            for (int tick = 0; tick < 300; tick++) {
                if (tick > 0) model.supply.merge(iron, 2L, Long::sum);
                model.tick(tick, mode);
                var stack = model.handler.getStackInSlot(0);
                if (stack.getCount() >= 16) {
                    int index = iron.matches(stack) ? 0 : 1;
                    consumed[index] += model.handler.extractItem(0, 16, false).getCount();
                }
            }
            assertTrue(consumed[0] > 0 && consumed[1] > 0, "minimum batch deadlocked: " + mode);
        }
    }

    @Test void nonDivisibleCapacityKeepsFullRefillsAndEverySteadyProcessingOpportunity() {
        for (var mode : IOSpeedMode.values()) {
            var model = new Model(List.of(iron, gold, copper), 64);
            int completed = 0;
            var consumed = new long[3];
            for (int tick = 0; tick < 1200; tick++) {
                model.tick(tick, mode);
                var stack = model.handler.getStackInSlot(0);
                if (stack.getCount() >= 24) {
                    for (int key = 0; key < 3; key++) {
                        if (List.of(iron, gold, copper).get(key).matches(stack)) consumed[key] += 24;
                    }
                    model.handler.extractItem(0, 24, false);
                    if (tick >= 200) completed++;
                }
            }
            assertEquals(1000, completed, "a tail smaller than the recipe batch must allow normal full refill: " + mode);
            // Full refills to 64 and exact consumption in 24s cannot empty this
            // slot. Preserve throughput instead of changing transfer quantities.
            assertTrue(consumed[0] > 0);
            assertEquals(0, consumed[1] + consumed[2]);
        }
    }

    @Test void oneOccupiedSlotDoesNotProbeEveryWaitingKeyEveryTick() {
        var keys = new ArrayList<AEItemKey>();
        for (int key = 0; key < 27; key++) {
            var stack = iron.toStack(1);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("waiter-" + key));
            keys.add(AEItemKey.of(stack));
        }
        var model = new Model(keys, 4096);
        model.tick(0, IOSpeedMode.FAST);
        model.handler.extractItem(0, 512, false);
        model.tick(1, IOSpeedMode.FAST);
        long before = model.simulations;
        for (int tick = 2; tick < 102; tick++) model.tick(tick, IOSpeedMode.FAST);
        assertTrue(model.simulations - before <= 300,
                "one occupied slot caused " + (model.simulations - before) + " ME simulations");
    }

    @Test void changingRecipesAndProcessingPausesResumeWithoutPermanentWithholding() {
        for (var mode : IOSpeedMode.values()) {
            var model = new Model(List.of(iron, gold, copper), 64);
            long[] consumed = new long[3];
            int completed = 0;
            for (int tick = 0; tick < 2400; tick++) {
                int batch = tick < 400 ? 16 : tick < 1200 ? 48 : 24;
                boolean paused = tick >= 800 && tick < 1000;
                model.tick(tick, mode);
                var stack = model.handler.getStackInSlot(0);
                if (!paused && stack.getCount() >= batch) {
                    for (int key = 0; key < 3; key++) {
                        if (List.of(iron, gold, copper).get(key).matches(stack)) consumed[key] += batch;
                    }
                    model.handler.extractItem(0, batch, false);
                    if (tick >= 200) completed++;
                }
            }
            assertTrue(completed >= 1980, "recipe-change throughput below 99%: " + mode + ", " + completed + "/2000");
            for (long count : consumed) assertTrue(count > 0);
        }
    }

    @Test void fixedIngredientSlotsCanFinishTheirMinimumBatches() {
        for (var mode : IOSpeedMode.values()) {
            var model = new Model(List.of(iron, gold, copper), 64);
            model.handler = new Handler(2, 64) {
                @Override public boolean isItemValid(int slot, ItemStack stack) {
                    return slot == 0 ? iron.matches(stack) || gold.matches(stack) : copper.matches(stack);
                }
            };
            model.supply.put(iron, 8L);
            long[] consumed = new long[2];
            int[] lastConsumed = new int[2];
            int completed = 0;
            for (int tick = 0; tick < 1400; tick++) {
                if (tick > 0) model.supply.merge(iron, 64L, Long::sum);
                model.tick(tick, mode);
                var primary = model.handler.getStackInSlot(0);
                int batch = tick < 700 ? 16 : 32;
                if (primary.getCount() >= batch && model.handler.getStackInSlot(1).getCount() >= 12) {
                    int key = iron.matches(primary) ? 0 : 1;
                    consumed[key] += batch;
                    lastConsumed[key] = tick;
                    model.handler.extractItem(0, batch, false);
                    model.handler.extractItem(1, 12, false);
                    if (tick >= 200) completed++;
                }
                if (tick >= 200) {
                    for (int last : lastConsumed) assertTrue(tick - last <= 32, "independent ingredient reset fairness");
                }
            }
            assertTrue(completed >= 1188, "coupled recipe throughput below 99%: " + mode + ", " + completed + "/1200");
            assertTrue(consumed[0] > 0 && consumed[1] > 0);
        }
    }

    @Test void fullSupplyKeepsAllSteadyOpportunitiesAcrossBatchesPeriodsAndPhases() {
        for (var mode : IOSpeedMode.values()) {
            for (int capacity : new int[] {64, 4096}) {
                for (int batch : new int[] {1, 3, 16, 24, 63, 64}) {
                    for (int period : new int[] {1, 5, 60}) {
                        for (int phase = 0; phase < Math.min(period, 3); phase++) {
                            var model = new Model(List.of(iron, gold, copper), capacity);
                            int planned = 0;
                            int completed = 0;
                            for (int tick = 0; tick < 2400; tick++) {
                                model.tick(tick, mode);
                                if (tick % period != phase) continue;
                                if (tick >= 200) planned++;
                                if (model.handler.getStackInSlot(0).getCount() >= batch) {
                                    model.handler.extractItem(0, batch, false);
                                    if (tick >= 200) completed++;
                                }
                            }
                            assertEquals(planned, completed, mode + " capacity=" + capacity + " batch=" + batch
                                    + " period=" + period + " phase=" + phase);
                        }
                    }
                }
            }
        }
    }

    private static class Handler extends LargeStackItemHandler {
        private final int capacity;
        private AEItemKey rejected;
        private int validityChecks;

        Handler(int slots, int capacity) { super(slots, null); this.capacity = capacity; }
        @Override public int getSlotLimit(int slot) { return capacity; }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            validityChecks++;
            return rejected == null || !rejected.matches(stack);
        }
    }

    private static final class Model {
        static final long INITIAL_SUPPLY = 10_000_000;
        final Map<AEKey, Long> supply = new HashMap<>();
        final ConnectionState state = new ConnectionState();
        final PowerCostUtil.EnergyAccess energy = new PowerCostUtil.EnergyAccess();
        final IGrid grid;
        ExportPlan plan;
        Handler handler;
        long simulations;
        final MEStorage storage = new MEStorage() {
            @Override public Component getDescription() { return Component.literal("fairness supply"); }
            @Override public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
                long result = Math.min(amount, supply.getOrDefault(key, 0L));
                if (mode == Actionable.SIMULATE) simulations++;
                else supply.merge(key, -result, Long::sum);
                return result;
            }
        };

        Model(List<AEItemKey> keys, int capacity) {
            plan = state.exportPlan(keys.getFirst().getType(), keys.stream()
                    .map(key -> new ExportConfigEntry(key, Long.MAX_VALUE)).toList());
            for (var key : keys) supply.put(key, INITIAL_SUPPLY);
            handler = new Handler(1, capacity);
            var service = (IEnergyService) Proxy.newProxyInstance(IEnergyService.class.getClassLoader(),
                    new Class<?>[] {IEnergyService.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getIdlePowerUsage" -> 0.0;
                        case "extractAEPower" -> args[0];
                        default -> throw new AssertionError(method);
                    });
            grid = (IGrid) Proxy.newProxyInstance(IGrid.class.getClassLoader(), new Class<?>[] {IGrid.class},
                    (proxy, method, args) -> service);
        }

        void tick(long now, IOSpeedMode mode) {
            plan.prepare(handler, storage, SOURCE, now);
            int start = plan.startIndex();
            for (int offset = 0; offset < plan.entries().size(); offset++) {
                int index = plan.indexAt(start, offset);
                var transfer = plan.transfers()[index];
                if (plan.shouldYield(index)) transfer.untilTick = now + 1;
                else if (now >= transfer.untilTick) {
                    long sent = OverloadedInterfaceBlockEntity.exportBoundedItemKey(transfer, plan.entries().get(index),
                            handler, storage, SOURCE, energy, () -> grid, (key, amount) -> fail("unexpected overflow"),
                            mode, now, -1);
                    if (sent > 0) plan.transferred(index, handler, sent, now);
                }
            }
        }
    }
}
