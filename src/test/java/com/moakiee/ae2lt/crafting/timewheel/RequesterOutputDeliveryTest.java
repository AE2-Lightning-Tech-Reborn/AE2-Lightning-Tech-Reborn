package com.moakiee.ae2lt.crafting.timewheel;

import com.moakiee.thunderbolt.core.crafting.plan.PlannedInputAssignments;
import com.moakiee.thunderbolt.core.crafting.pattern.PlannedInputPattern;
import com.moakiee.thunderbolt.core.crafting.planner.CraftPattern;
import com.moakiee.thunderbolt.core.crafting.planner.CraftInput;
import com.moakiee.thunderbolt.core.crafting.planner.CraftPlan;
import appeng.api.crafting.IPatternDetails;
import net.minecraft.world.level.Level;
import appeng.crafting.CraftingPlan;
import appeng.api.stacks.AEItemKey;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;

import static appeng.api.config.Actionable.MODULATE;
import static appeng.api.config.Actionable.SIMULATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.ElapsedTimeTracker;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.storage.NetworkStorage;
import com.moakiee.ae2lt.logic.tianshu.maintenance.TianshuInventoryMaintenanceHost;
import com.moakiee.ae2lt.logic.tianshu.maintenance.TianshuInventoryMaintenanceService;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.mixin.thunderbolt.accessor.ElapsedTimeTrackerAccessor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Runs real CPU delivery and maintenance callbacks against AE2's network recursion guard. */
class RequesterOutputDeliveryTest {
    private static final AEKey OUTPUT = LightningKey.EXTREME_HIGH_VOLTAGE;
    private static final AEKey OTHER = LightningKey.HIGH_VOLTAGE;
    private static Field allTypes;
    private static Object previousTypes;

    @BeforeAll
    static void initializeTrackerKeyTypes() throws Exception {
        // Plain JUnit does not run NeoForge's registry bake. The tracker only needs this set
        // to size its counters; restore it afterwards so other tests retain their own setup.
        allTypes = Class.forName("appeng.api.stacks.AEKeyTypesInternal").getDeclaredField("allTypes");
        allTypes.setAccessible(true);
        previousTypes = allTypes.get(null);
        if (previousTypes == null) allTypes.set(null, Set.of(OUTPUT.getType()));
    }

    @AfterAll
    static void restoreTrackerKeyTypes() throws Exception {
        allTypes.set(null, previousTypes);
    }

    @ParameterizedTest
    @CsvSource({"1024,1,1", "1500,1,1", "1024,1,2", "1024,2,1"})
    void maintenanceRequiresEveryRequestedUnit(long demand, int period, int batch) throws Exception {
        var fixture = new Fixture(demand, false, false);
        long produced = 0;
        for (int tick = 0; tick < demand * period + 1 && !fixture.link.completed; tick++) {
            if (tick % period == 0) {
                long amount = Math.min(batch, demand - produced);
                assertEquals(amount, fixture.produce(amount));
                produced += amount;
            }
            fixture.flush();
        }

        assertTrue(fixture.link.completed);
        assertEquals(demand, produced);
        assertEquals(demand, fixture.disk.stored);
        assertEquals(0, fixture.remaining());
        assertEquals(0, fixture.held());
    }

    @Test
    void plannedInputTasksKeepSeparateCopiesWithoutDuplicatingPendingOutput() throws Exception {
        var input = new IPatternDetails.IInput() {
            @Override public GenericStack[] getPossibleInputs() {
                return new GenericStack[] {new GenericStack(OUTPUT, 1), new GenericStack(OTHER, 1)};
            }
            @Override public long getMultiplier() { return 1; }
            @Override public boolean isValid(AEKey key, Level level) { return true; }
            @Override public AEKey getRemainingKey(AEKey key) { return null; }
        };
        var source = new IPatternDetails() {
            @Override public AEItemKey getDefinition() { return null; }
            @Override public IInput[] getInputs() { return new IInput[] {input}; }
            @Override public List<GenericStack> getOutputs() { return List.of(new GenericStack(OUTPUT, 1)); }
        };
        var firstInputs = List.of(CraftInput.of(OTHER, 1));
        var secondInputs = List.of(CraftInput.of(OUTPUT, 1));
        var first = new CraftPattern<>(OUTPUT, BigInteger.ONE,
                firstInputs, List.of(), source, List.of(firstInputs));
        var second = new CraftPattern<>(OUTPUT, BigInteger.ONE,
                secondInputs, List.of(), source, List.of(secondInputs));
        var internal = new CraftPlan<>(true, true,
                Map.of(first, 2L, second, 3L), Map.of(), Map.of(), Map.of(), Map.of(), 0, false);
        var plan = new CraftingPlan(new GenericStack(OUTPUT, 5), 100L, false, false,
                new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of(source, 5L));
        PlannedInputAssignments.record(plan, internal);
        var jobClass = Class.forName(Ae2LtTimeWheelCraftingCpuLogic.class.getName() + "$TimeWheelJob");
        var constructor = jobClass.getDeclaredConstructor(ICraftingPlan.class, Consumer.class,
                CraftingLink.class, Integer.class, ElapsedTimeTracker.class);
        constructor.setAccessible(true);
        var job = constructor.newInstance(plan, (Consumer<AEKey>) key -> {}, null, null, new Tracker());
        var tasks = (Map<?, ?>) field(job, "tasks").get(job);
        assertEquals(2, tasks.size());
        var copiesByKey = new HashMap<AEKey, Long>();
        for (var entry : tasks.entrySet()) {
            var planned = (PlannedInputPattern) entry.getKey();
            copiesByKey.put(planned.allocations().getFirst().keySet().iterator().next(),
                    field(entry.getValue(), "value").getLong(entry.getValue()));
            org.junit.jupiter.api.Assertions.assertSame(source, planned.providerLookupPattern());
        }
        assertEquals(Map.of(OTHER, 2L, OUTPUT, 3L), copiesByKey);
        assertEquals(5L, ((KeyCounter) field(job, "pendingOutputs").get(job)).get(OUTPUT));
    }

    @Test
    void oneProducedUnitCannotCompleteTheRestOfTheJob() throws Exception {
        var fixture = new Fixture(1024, false, false);
        assertEquals(1, fixture.produce(1));

        for (int tick = 0; tick < 2048; tick++) fixture.flush();

        assertFalse(fixture.link.completed);
        assertEquals(1023, fixture.remaining());
        assertEquals(1023, fixture.waiting(OUTPUT));
        assertEquals(1, fixture.disk.stored);
        assertEquals(0, fixture.pending());
    }

    @Test
    void blockedAndPartialDeliveryRetainsPhysicalOutputWithoutNewProductionCredit() throws Exception {
        var fixture = new Fixture(64, false, false);
        fixture.disk.capacity = 0;
        assertEquals(16, fixture.produce(16));
        for (int tick = 0; tick < 100; tick++) fixture.flush();

        assertFalse(fixture.link.completed);
        assertEquals(64, fixture.remaining());
        assertEquals(48, fixture.waiting(OUTPUT));
        assertEquals(16, fixture.pending());
        assertEquals(16, fixture.held());
        assertEquals(0, fixture.disk.stored);

        fixture.disk.capacity = 8;
        fixture.flush();
        assertEquals(56, fixture.remaining());
        assertEquals(48, fixture.waiting(OUTPUT));
        assertEquals(8, fixture.pending());
        assertEquals(8, fixture.held());
        assertEquals(8, fixture.disk.stored);

        fixture.disk.capacity = 64;
        fixture.flush();
        assertEquals(48, fixture.remaining());
        assertEquals(48, fixture.waiting(OUTPUT));
        assertEquals(16, fixture.disk.stored);
        assertEquals(0, fixture.pending());
        assertEquals(48, fixture.produce(48));
        fixture.flush();
        assertTrue(fixture.link.completed);
        assertEquals(64, fixture.disk.stored);
        assertEquals(0, fixture.held());
    }

    @Test
    void simulationDoesNotConsumeWaitingOrPendingOutput() throws Exception {
        var fixture = new Fixture(128, false, false);
        assertEquals(8, fixture.produce(8));
        assertEquals(32, fixture.network.insert(OUTPUT, 32, SIMULATE, fixture.source));

        assertEquals(128, fixture.remaining());
        assertEquals(120, fixture.waiting(OUTPUT));
        assertEquals(8, fixture.pending());
        assertEquals(8, fixture.held());
        assertEquals(0, fixture.disk.stored);
        fixture.flush();
        assertEquals(120, fixture.remaining());
        assertEquals(8, fixture.disk.stored);
    }

    @Test
    void directCpuDeliveryAlsoCannotReenterItsOwnWaitingDemand() throws Exception {
        var fixture = new Fixture(128, false, false);
        assertEquals(8, fixture.logic.insert(OUTPUT, 8, MODULATE));

        assertEquals(120, fixture.remaining());
        assertEquals(120, fixture.waiting(OUTPUT));
        assertEquals(8, fixture.disk.stored);
        assertEquals(0, fixture.pending());
        assertEquals(0, fixture.held());
        assertFalse(fixture.link.completed);
    }

    @Test
    void requesterFailureDoesNotLeaveTheCpuUnableToAcceptNewProduction() throws Exception {
        var fixture = new Fixture(16, false, false);
        assertEquals(1, fixture.produce(1));
        fixture.link.beforeDelivery = () -> { throw new IllegalStateException("requester failed"); };
        assertThrows(IllegalStateException.class, fixture::flush);
        fixture.link.beforeDelivery = () -> {};

        assertEquals(1, fixture.produce(1));
        assertEquals(14, fixture.waiting(OUTPUT));
        assertEquals(2, fixture.pending());
        fixture.flush();
        assertEquals(14, fixture.remaining());
        assertEquals(2, fixture.disk.stored);
    }

    @Test
    void deliveryDoesNotBlockAnUnrelatedReturnedIngredient() throws Exception {
        var fixture = new Fixture(16, false, true);
        fixture.link.beforeDelivery = () -> {
            fixture.link.beforeDelivery = () -> {};
            assertEquals(1, fixture.logic.insert(OTHER, 1, MODULATE));
        };
        assertEquals(1, fixture.logic.insert(OUTPUT, 1, MODULATE));

        assertEquals(0, fixture.waiting(OTHER));
        assertEquals(15, fixture.waiting(OUTPUT));
        assertEquals(15, fixture.remaining());
        assertEquals(1, fixture.disk.stored);
    }

    @Test
    void standaloneOutputStillFallsThroughToNetworkStorage() throws Exception {
        var fixture = new Fixture(16, true, false);
        assertEquals(8, fixture.produce(8));
        assertEquals(8, fixture.remaining());
        assertEquals(8, fixture.waiting(OUTPUT));
        assertEquals(8, fixture.disk.stored);
        assertEquals(0, fixture.pending());
        assertEquals(8, fixture.produce(8));
        assertTrue(fixture.link.completed);
        assertEquals(16, fixture.disk.stored);
    }

    @ParameterizedTest
    @CsvSource({"true", "false"})
    void finalOutputNeededByOrdinaryTaskStaysAvailableUntilConsumed(boolean standalone) throws Exception {
        var fixture = new Fixture(8, standalone, false);
        var pattern = new IPatternDetails() {
            @Override public AEItemKey getDefinition() { return null; }
            @Override public IInput[] getInputs() {
                return new IInput[]{new IInput() {
                    @Override public GenericStack[] getPossibleInputs() {
                        return new GenericStack[]{new GenericStack(OUTPUT, 1)};
                    }
                    @Override public long getMultiplier() { return 1; }
                    @Override public boolean isValid(AEKey key, Level level) { return OUTPUT.equals(key); }
                    @Override public AEKey getRemainingKey(AEKey key) { return null; }
                }};
            }
            @Override public List<GenericStack> getOutputs() { return List.of(new GenericStack(OTHER, 1)); }
        };
        var progressClass = Class.forName(Ae2LtTimeWheelCraftingCpuLogic.class.getName() + "$TaskProgress");
        var constructor = progressClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        var progress = constructor.newInstance();
        field(progress, "value").setLong(progress, 4L);
        var tasks = (Map<IPatternDetails, Object>) field(fixture.job, "tasks").get(fixture.job);
        tasks.put(pattern, progress);

        assertEquals(4L, fixture.produce(4));
        fixture.flush();
        assertEquals(8L, fixture.remaining());
        assertEquals(4L, fixture.held());
        assertEquals(0L, fixture.disk.stored);
        var reserveMethod = fixture.logic.getClass().getDeclaredMethod("reservedCraftingInventory", IPatternDetails.class);
        reserveMethod.setAccessible(true);
        var available = (appeng.crafting.inv.ICraftingInventory) reserveMethod.invoke(fixture.logic, pattern);
        assertEquals(4L, available.extract(OUTPUT, 4L, SIMULATE));
        assertEquals(4L, available.extract(OUTPUT, 4L, MODULATE));
        var reconcile = fixture.logic.getClass().getDeclaredMethod("reconcileRetainedInventory");
        reconcile.setAccessible(true);
        reconcile.invoke(fixture.logic);
        tasks.clear();
        assertEquals(0L, ((KeyCounter) field(fixture.logic, "retainedFinalOutputs").get(fixture.logic)).get(OUTPUT));
        fixture.flush();
        assertEquals(8L, fixture.remaining(), "consumed intermediate material cannot complete final demand");
    }

    @Test
    void dispatchFailureCannotBeResumedOrMarkedSuccessful() throws Exception {
        var fixture = new Fixture(1, true, false);
        var fail = fixture.logic.getClass().getDeclaredMethod("failExecution",
                fixture.job.getClass(), String.class, Throwable.class);
        fail.setAccessible(true);
        fail.invoke(fixture.logic, fixture.job, "AMBIGUOUS_PROVIDER_OWNERSHIP", new IllegalStateException("provider"));
        fixture.logic.setJobSuspended(false);
        assertTrue(fixture.logic.isJobSuspended());
        assertEquals("AMBIGUOUS_PROVIDER_OWNERSHIP", fixture.logic.getExecutionError());
        field(fixture.job, "remainingAmount").setLong(fixture.job, 0);
        fixture.flush();
        assertFalse(fixture.link.completed);
    }

    @Test
    void ordinaryBulkStopsAfterAmbiguousPushOrAcceptedAccountingFailure() throws Exception {
        for (boolean providerThrows : List.of(true, false)) {
            var fixture = new Fixture(2, true, false);
            var source = new IPatternDetails() {
                public AEItemKey getDefinition() { return null; }
                public IInput[] getInputs() {
                    return new IInput[] {new IInput() {
                        public GenericStack[] getPossibleInputs() { return new GenericStack[] {new GenericStack(OTHER, 1)}; }
                        public long getMultiplier() { return 1; }
                        public boolean isValid(AEKey key, Level level) { return key.equals(OTHER); }
                        public AEKey getRemainingKey(AEKey key) { return null; }
                    }};
                }
                public List<GenericStack> getOutputs() { return List.of(new GenericStack(OUTPUT, 1)); }
            };
            var planned = new PlannedInputPattern(source, List.of(Map.of(OTHER, 1L)));
            var progressClass = Class.forName(Ae2LtTimeWheelCraftingCpuLogic.class.getName() + "$TaskProgress");
            var constructor = progressClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            var progress = constructor.newInstance();
            field(progress, "value").setLong(progress, 2);
            ((Map) field(fixture.job, "tasks").get(fixture.job)).put(planned, progress);
            ((Set) field(fixture.logic, "nonBatchTasksThisTick").get(fixture.logic)).add(planned);
            var stock = (ListCraftingInventory) field(fixture.logic, "inventory").get(fixture.logic);
            stock.insert(OTHER, 10, MODULATE);
            int[] pushes = {0};
            var providerType = appeng.api.networking.crafting.ICraftingProvider.class;
            var provider = Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {providerType},
                    (proxy, method, args) -> {
                        if (method.getName().equals("isBusy")) return false;
                        if (method.getName().equals("pushPattern")) {
                            pushes[0]++;
                            if (providerThrows) throw new IllegalStateException("accepted then threw");
                            return true;
                        }
                        return null;
                    });
            var schedule = new com.moakiee.thunderbolt.core.crafting.batch.TickProviderDispatchSchedule();
            var scheduleClass = Class.forName(schedule.getClass().getName() + "$PatternSchedule");
            var scheduleConstructor = scheduleClass.getDeclaredConstructor(List.class);
            scheduleConstructor.setAccessible(true);
            ((Map) field(schedule, "patterns").get(schedule)).put(source,
                    scheduleConstructor.newInstance(List.of(provider)));
            if (!providerThrows) {
                field(fixture.job, "waitingFor").set(fixture.job, new ListCraftingInventory(key -> {
                    throw new IllegalStateException("notification failed after acceptance");
                }));
            }
            var energyType = appeng.api.networking.energy.IEnergyService.class;
            var energy = Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {energyType},
                    (proxy, method, args) -> method.getName().equals("extractAEPower") ? args[0] : null);
            var execute = fixture.logic.getClass().getDeclaredMethod("executeCraftingBudgeted", int.class,
                    long.class, appeng.me.service.CraftingService.class, energyType, Level.class, schedule.getClass());
            execute.setAccessible(true);
            execute.invoke(fixture.logic, 2, 2L, null, energy, null, schedule);
            assertEquals(1, pushes[0]);
            assertEquals(9, stock.list.get(OTHER), "only the submitted copy is withheld");
            assertTrue(fixture.logic.isJobSuspended());
            fixture.logic.setJobSuspended(false);
            execute.invoke(fixture.logic, 2, 2L, null, energy, null, schedule);
            assertEquals(1, pushes[0], "failed work must not replay");
        }
    }

    @Test
    void invalidTaskListIsPreservedAndSuspendedInsteadOfRestoringAPartialJob() throws Exception {
        var fixture = new Fixture(1, true, false);
        var data = new CompoundTag();
        data.putString("tasks", "corrupt task list");
        var restore = fixture.job.getClass().getDeclaredMethod("restoreTasks", CompoundTag.class,
                net.minecraft.core.HolderLookup.Provider.class, Level.class);
        restore.setAccessible(true);
        restore.invoke(fixture.job, data, null, null);
        assertTrue(fixture.logic.isJobSuspended());
        assertEquals("EXECUTION_METADATA_LOST", fixture.logic.getExecutionError());
        assertEquals(data.get("tasks"), field(fixture.job, "failedTaskSnapshot").get(fixture.job));
        assertTrue(((Map<?, ?>) field(fixture.job, "tasks").get(fixture.job)).isEmpty());
        fixture.logic.setJobSuspended(false);
        assertTrue(fixture.logic.isJobSuspended());
    }

    @ParameterizedTest
    @CsvSource({"true", "false"})
    void releasedRetainedOutputIsDeliveredOnlyOnce(boolean standalone) throws Exception {
        var fixture = new Fixture(8, standalone, false);
        var inventory = (ListCraftingInventory) field(fixture.logic, "inventory").get(fixture.logic);
        inventory.insert(OUTPUT, 4L, MODULATE);
        ((KeyCounter) field(fixture.logic, "retainedFinalOutputs").get(fixture.logic)).add(OUTPUT, 4L);
        invoke(fixture.logic, "flushUnusedRetainedFinalOutputs", fixture.job);
        invoke(fixture.logic, "flushUnusedRetainedFinalOutputs", fixture.job);
        assertEquals(4L, fixture.disk.stored);
        assertEquals(4L, fixture.remaining());
        assertEquals(0L, fixture.held());
    }

    @Test
    void intermediateReturnPublishesStockBeforeWaitingNotification() throws Exception {
        var fixture = new Fixture(8, false, true);
        var inventory = (ListCraftingInventory) field(fixture.logic, "inventory").get(fixture.logic);
        var waiting = new ListCraftingInventory(key -> {
            assertEquals(1L, inventory.list.get(OTHER), "waiting retirement must observe physical stock");
        });
        waiting.list.add(OTHER, 1L);
        field(fixture.job, "waitingFor").set(fixture.job, waiting);
        assertEquals(1L, fixture.logic.insert(OTHER, 1L, SIMULATE));
        assertEquals(0L, inventory.list.get(OTHER));
        assertEquals(1L, fixture.waiting(OTHER));
        assertEquals(1L, fixture.logic.insert(OTHER, 1L, MODULATE));
        assertEquals(0L, fixture.waiting(OTHER));
        assertEquals(1L, inventory.list.get(OTHER));
        assertEquals(0L, fixture.logic.insert(OTHER, 1L, MODULATE));
    }

    private static final class Fixture {
        final NetworkStorage network = new NetworkStorage();
        final Disk disk = new Disk();
        final IActionSource source = proxy(IActionSource.class, Map.of());
        final Ae2LtTimeWheelCraftingCpuLogic logic;
        final Link link;
        final Object job;

        Fixture(long demand, boolean standalone, boolean withOtherInput) throws Exception {
            var storage = proxy(IStorageService.class, Map.of("getInventory", network));
            var grid = proxy(IGrid.class, Map.of("getStorageService", storage));
            var host = proxy(TimeWheelCraftingCpuHost.class,
                    Map.of("isCpuActive", true, "getGrid", grid, "getActionSource", source));
            var cpu = new TimeWheelCraftingCPU(host, Long.MAX_VALUE, 0, Long.MAX_VALUE, false);
            logic = cpu.getCraftingLogic();
            var maintenanceHost = proxy(TianshuInventoryMaintenanceHost.class,
                    Map.of("getGrid", grid, "getActionSource", source));
            link = new Link(cpu, new TianshuInventoryMaintenanceService(maintenanceHost), standalone);
            var emitted = new KeyCounter();
            emitted.add(OUTPUT, demand);
            if (withOtherInput) emitted.add(OTHER, 1);
            var plan = proxy(ICraftingPlan.class, Map.of(
                    "finalOutput", new GenericStack(OUTPUT, demand), "emittedItems", emitted,
                    "patternTimes", Map.of()));
            var jobClass = Class.forName(Ae2LtTimeWheelCraftingCpuLogic.class.getName() + "$TimeWheelJob");
            var constructor = jobClass.getDeclaredConstructor(ICraftingPlan.class, Consumer.class,
                    CraftingLink.class, Integer.class, ElapsedTimeTracker.class);
            constructor.setAccessible(true);
            job = constructor.newInstance(plan, (Consumer<AEKey>) key -> {}, link, null, new Tracker());
            field(logic, "job").set(logic, job);
            // Matches CraftingServiceStorage's priority and the extended CPU insertion route.
            network.mount(Integer.MAX_VALUE, new MEStorage() {
                @Override
                public long insert(AEKey key, long amount, Actionable mode, IActionSource src) {
                    return logic.insert(key, amount, mode);
                }

                @Override
                public Component getDescription() { return Component.literal("CPU"); }
            });
            network.mount(0, disk);
        }

        long produce(long amount) {
            return network.insert(OUTPUT, amount, MODULATE, source);
        }

        void flush() throws Exception {
            invoke(logic, "flushPendingRequesterOutputs", job);
            invoke(logic, "flushUnusedRetainedFinalOutputs", job);
            invoke(logic, "recoverTerminalFinalOutputFromInventory", job);
            invoke(logic, "finishSuccessfulIfReady", job);
        }

        long remaining() throws Exception { return field(job, "remainingAmount").getLong(job); }
        long waiting(AEKey key) throws Exception {
            return ((ListCraftingInventory) field(job, "waitingFor").get(job)).list.get(key);
        }
        long pending() throws Exception {
            return ((KeyCounter) field(logic, "pendingRequesterOutputs").get(logic)).get(OUTPUT);
        }
        long held() throws Exception {
            return ((ListCraftingInventory) field(logic, "inventory").get(logic)).list.get(OUTPUT);
        }
    }

    private static final class Disk implements MEStorage {
        long stored;
        long capacity = Long.MAX_VALUE;

        @Override
        public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            long accepted = Math.min(amount, capacity - stored);
            if (mode == MODULATE) stored += accepted;
            return accepted;
        }

        @Override
        public Component getDescription() { return Component.literal("Storage"); }
    }

    /** Keeps the real maintenance callback while replacing only CraftingLinkNexus wiring. */
    private static final class Link extends CraftingLink {
        final TianshuInventoryMaintenanceService maintenance;
        final boolean standalone;
        boolean completed;
        Runnable beforeDelivery = () -> {};

        Link(ICraftingCPU cpu, TianshuInventoryMaintenanceService maintenance, boolean standalone) {
            super(linkTag(standalone), cpu);
            this.maintenance = maintenance;
            this.standalone = standalone;
        }

        @Override public boolean isStandalone() { return standalone; }
        @Override public boolean isCanceled() { return false; }
        @Override public boolean isDone() { return completed; }
        @Override public void markDone() { completed = true; }

        @Override
        public long insert(AEKey what, long amount, Actionable mode) {
            if (standalone) return 0;
            beforeDelivery.run();
            return maintenance.insertCraftedItems(this, what, amount, mode);
        }

        private static CompoundTag linkTag(boolean standalone) {
            var tag = new CompoundTag();
            tag.putUUID("craftId", UUID.randomUUID());
            tag.putBoolean("req", false);
            tag.putBoolean("standalone", standalone);
            return tag;
        }
    }

    /** Supplies the accessors normally installed by Mixin, retaining the real tracker math. */
    private static final class Tracker extends ElapsedTimeTracker implements ElapsedTimeTrackerAccessor {
        @Override public void ae2lt$addMaxItems(long amount, AEKeyType type) {
            forward("addMaxItems", amount, type);
        }
        @Override public void ae2lt$decrementItems(long amount, AEKeyType type) {
            forward("decrementItems", amount, type);
        }

        private void forward(String name, long amount, AEKeyType type) {
            try {
                var method = ElapsedTimeTracker.class.getDeclaredMethod(name, long.class, AEKeyType.class);
                method.setAccessible(true);
                method.invoke(this, amount, type);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }
    }

    private static Field field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void invoke(Object target, String name, Object job) throws Exception {
        var method = target.getClass().getDeclaredMethod(name, job.getClass());
        method.setAccessible(true);
        try {
            method.invoke(target, job);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException failure) throw failure;
            if (e.getCause() instanceof Error failure) throw failure;
            throw e;
        }
    }

    private static <T> T proxy(Class<T> type, Map<String, Object> values) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> {
                    if (values.containsKey(method.getName())) return values.get(method.getName());
                    var result = method.getReturnType();
                    if (result == boolean.class) return false;
                    if (result == int.class) return 0;
                    if (result == long.class) return 0L;
                    if (result == Optional.class) return Optional.empty();
                    return null;
                }));
    }
}
