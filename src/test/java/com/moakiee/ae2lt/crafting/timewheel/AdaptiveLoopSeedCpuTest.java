package com.moakiee.ae2lt.crafting.timewheel;

import static org.junit.jupiter.api.Assertions.*;
import static com.moakiee.ae2lt.crafting.timewheel.TimeWheelDispatchBoundaryTest.field;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.ElapsedTimeTracker;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.hooks.ticking.TickHandler;
import appeng.me.service.CraftingService;
import com.moakiee.ae2lt.crafting.runtime.ExecuteLoopPattern;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.thunderbolt.api.crafting.batch.IBatchCraftingProvider;
import com.moakiee.thunderbolt.core.crafting.batch.SharedBatchInputPattern;
import com.moakiee.thunderbolt.core.crafting.loop.CraftingTaskPersistenceDefinition;
import com.moakiee.thunderbolt.core.crafting.loop.ISeedPreservingCraftingTask;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

/** Native loan sizing, task bookkeeping and CPU dispatch/return with a delayed provider. */
class AdaptiveLoopSeedCpuTest {
    private static AEKey POWDER;
    private static AEKey FUEL;

    @Test
    void configuredThirtyTwoBorrowsOnlyAvailableSetsAndReturnsTheActualLoan() throws Exception {
        for (long spare : new long[] {0, 16, 248, 1000}) {
            try (var run = new Run(spare)) {
                long expectedLoan = 8 * Math.min(32, 1 + spare / 8);
                assertEquals(expectedLoan, run.logic.getStored(POWDER));
                assertEquals(expectedLoan, run.quota());
                assertEquals(expectedLoan, run.initialSeed());
                for (int i = 0; i < 5; i++) run.tick(2);
                assertEquals(0, run.remaining(), run::state);
                assertEquals(0, run.taskCount(), run::state);
                assertEquals(0, run.logic.getWaitingFor(POWDER), run::state);
                assertEquals(80, run.networkPowder, run::state);
                assertEquals(expectedLoan, run.hostSeed, run::state);
                assertEquals(spare + 8, run.spareSeed, "all borrowed seeds return without creating extras");
            }
        }
    }

    private static final class Run implements AutoCloseable {
        final Ae2LtTimeWheelCraftingCpuLogic logic;
        final ListCraftingInventory inventory;
        final Object job;
        final Class<?> jobClass;
        final IEnergyService energy;
        final CraftingService service;
        final long priorTick;
        final List<Long> outputs = new ArrayList<>();
        long capacity;
        long networkPowder;
        long hostSeed;
        long spareSeed;
        long returnPacketSize = Long.MAX_VALUE;

        Run(long spare) throws Exception {
            spareSeed = spare;
            var fixture = com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopAdaptiveSeedPatternTest.fixture(32);
            POWDER = fixture.seed();
            FUEL = fixture.fuel();
            if (net.minecraftforge.fml.loading.LoadingModList.get() == null) {
                net.minecraftforge.fml.loading.LoadingModList.of(List.of(), List.of(), new net.minecraftforge.fml.loading.EarlyLoadingException("test", null, List.of()));
            }
            net.minecraft.SharedConstants.tryDetectVersion();
            net.minecraft.server.Bootstrap.bootStrap();
            priorTick = field(TickHandler.class, "tickCounter").getLong(TickHandler.instance());
            var level = fixture.level();
            var source = proxy(IActionSource.class, Map.of());
            var host = (TimeWheelCraftingCpuHost) Proxy.newProxyInstance(
                    TimeWheelCraftingCpuHost.class.getClassLoader(), new Class<?>[] {TimeWheelCraftingCpuHost.class},
                    (p, method, args) -> switch (method.getName()) {
                        case "isCpuActive" -> true;
                        case "getCpuLevel" -> level;
                        case "getActionSource" -> source;
                        case "extractReusableSeed" -> {
                            long taken = Math.min(spareSeed, (long) args[1]);
                            if (args[2] == Actionable.MODULATE) spareSeed -= taken;
                            yield taken;
                        }
                        case "insertReusableSeed" -> {
                            if (args[2] == Actionable.MODULATE) {
                                hostSeed += (long) args[1];
                                spareSeed += (long) args[1];
                            }
                            yield args[1];
                        }
                        default -> defaultValue(method.getReturnType());
                    });
            var cpu = new TimeWheelCraftingCPU(host, Long.MAX_VALUE, 0, Long.MAX_VALUE, true);
            logic = cpu.getCraftingLogic();
            var raw = fixture.raw();
            var macro = fixture.macro();
            var plan = proxy(ICraftingPlan.class, Map.of("finalOutput", new GenericStack(POWDER, 80),
                    "emittedItems", new KeyCounter(), "patternTimes", Map.of(macro, 10L)));
            var tag = new CompoundTag(); tag.putUUID("craftId", UUID.randomUUID());
            tag.putBoolean("req", false); tag.putBoolean("standalone", true);
            var link = new CraftingLink(tag, cpu);
            jobClass = Class.forName(logic.getClass().getName() + "$TimeWheelJob");
            var ctor = jobClass.getDeclaredConstructor(ICraftingPlan.class, Consumer.class,
                    CraftingLink.class, Integer.class, ElapsedTimeTracker.class);
            ctor.setAccessible(true);
            job = ctor.newInstance(plan, (Consumer<AEKey>) k -> {}, link, null,
                    new TimeWheelDispatchBoundaryTest.Tracker());
            field(logic.getClass(), "job").set(logic, job);
            ((LoopSeedLedgerBook) field(logic.getClass(), "loopSeedLedgers").get(logic)).initialize(macro.expandPatternFirings(10).keySet().stream()
                    .map(ExecuteLoopPattern.class::cast).toList());
            var requirements = new KeyCounter(); requirements.add(POWDER, 8);
            inventory = (ListCraftingInventory) field(logic.getClass(), "inventory").get(logic);
            inventory.insert(POWDER, 8, Actionable.MODULATE); inventory.insert(FUEL, 10, Actionable.MODULATE);
            var borrow = logic.getClass().getDeclaredMethod("borrowOptionalLoopSeeds", ICraftingPlan.class,
                    jobClass, KeyCounter.class);
            borrow.setAccessible(true);
            borrow.invoke(logic, plan, job, requirements);
            ((KeyCounter) field(logic.getClass(), "seedReturnQuota").get(logic)).addAll(requirements);
            var provider = new IBatchCraftingProvider() {
                @Override public List<IPatternDetails> getAvailablePatterns() { return List.of(raw); }
                @Override public boolean isBusy() { return false; }
                @Override public boolean supportsSharedBatchInputs() { return true; }
                @Override public long getBatchCapacity(IPatternDetails details) { return capacity; }
                @Override public long pushBatch(IPatternDetails details, KeyCounter[] input, long copies) {
                    assertEquals(8, input[0].get(POWDER)); assertEquals(1, input[1].get(FUEL));
                    outputs.add(8 * (copies + 1));
                    return 0;
                }
            };
            energy = (IEnergyService) Proxy.newProxyInstance(IEnergyService.class.getClassLoader(),
                    new Class<?>[] {IEnergyService.class}, (p, m, args) ->
                            m.getName().equals("extractAEPower") ? args[0] : defaultValue(m.getReturnType()));
            service = new CraftingService(proxy(IGrid.class, Map.of()), proxy(IStorageService.class, Map.of()), energy) {
                @Override public Iterable<ICraftingProvider> getProviders(IPatternDetails details) { return List.of(provider); }
            };
        }

        void tick(long nextCapacity) throws Exception {
            capacity = nextCapacity;
            var clock = field(TickHandler.class, "tickCounter");
            clock.setLong(TickHandler.instance(), clock.getLong(TickHandler.instance()) + 1);
            logic.tickCraftingLogic(energy, service, 1, Long.MAX_VALUE);
            for (long amount : outputs) {
                while (amount > 0) {
                    long offered = Math.min(returnPacketSize, amount);
                    long simulated = logic.insert(POWDER, offered, Actionable.SIMULATE);
                    long accepted = logic.insert(POWDER, offered, Actionable.MODULATE);
                    assertEquals(simulated, accepted, state());
                    networkPowder += offered - accepted;
                    amount -= offered;
                }
            }
            outputs.clear();
            if (taskCount() > 0 && logic.getWaitingFor(POWDER) == 0) {
                assertTrue(logic.getStored(POWDER) >= 8, "Next loop seed must remain in CPU: " + state());
            }
        }

        long quota() throws Exception {
            return ((KeyCounter) field(logic.getClass(), "seedReturnQuota").get(logic)).get(POWDER);
        }
        long initialSeed() throws Exception {
            return ((Map<?, ?>) field(jobClass, "tasks").get(job)).keySet().stream()
                    .filter(ExecuteLoopPattern.class::isInstance).map(ExecuteLoopPattern.class::cast)
                    .mapToLong(pattern -> pattern.initialSeed().get(POWDER)).max().orElse(0);
        }
        long remaining() throws Exception { return field(jobClass, "remainingAmount").getLong(job); }
        int taskCount() throws Exception { return ((Map<?, ?>) field(jobClass, "tasks").get(job)).size(); }
        long retained() throws Exception { return ((KeyCounter) field(logic.getClass(), "retainedFinalOutputs").get(logic)).get(POWDER); }
        String state() {
            try { return "capacity=" + capacity + " remaining=" + remaining() + " tasks=" + taskCount()
                    + " waiting=" + logic.getWaitingFor(POWDER) + " cpuPowder=" + logic.getStored(POWDER)
                    + " retained=" + retained() + " network=" + networkPowder + " hostSeed=" + hostSeed; }
            catch (Exception e) { throw new AssertionError(e); }
        }
        @Override public void close() throws Exception {
            field(TickHandler.class, "tickCounter").setLong(TickHandler.instance(), priorTick);
        }
    }

    private static <T> T proxy(Class<T> type, Map<String, Object> values) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (p, method, args) -> values.containsKey(method.getName()) ? values.get(method.getName())
                        : defaultValue(method.getReturnType())));
    }
    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == Optional.class) return Optional.empty();
        return null;
    }
}
