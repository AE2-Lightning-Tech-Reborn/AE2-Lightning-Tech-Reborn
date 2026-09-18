package com.moakiee.ae2lt.crafting.timewheel;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
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
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.ElapsedTimeTracker;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.hooks.ticking.TickHandler;
import appeng.me.service.CraftingService;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.mixin.thunderbolt.accessor.ElapsedTimeTrackerAccessor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.WritableLevelData;
import org.junit.jupiter.api.Test;

/** Real pool, CPU scheduler and extraction across the physical CPU's 16384-dispatch boundary. */
class TimeWheelDispatchBoundaryTest {
    private static final AEKey INPUT = LightningKey.EXTREME_HIGH_VOLTAGE;
    private static final AEKey OUTPUT = LightningKey.HIGH_VOLTAGE;
    private static final int BUDGET = 16_384;

    @Test
    void ordinaryJobContinuesAfterExhaustingTwoFullPhysicalTicks() throws Exception {
        runBoundary(false);
    }

    @Test
    void providerRejectionDoesNotPoisonFollowingPhysicalTicks() throws Exception {
        runBoundary(true);
    }

    private void runBoundary(boolean rejectSecondTick) throws Exception {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        var types = field(Class.forName("appeng.api.stacks.AEKeyTypesInternal"), "allTypes");
        var previousTypes = types.get(null);
        var clock = field(TickHandler.class, "tickCounter");
        long previousTick = clock.getLong(TickHandler.instance());
        if (previousTypes == null) types.set(null, Set.of(INPUT.getType()));
        try {
            var level = emptyLevel();
            var source = proxy(IActionSource.class, Map.of());
            var host = proxy(TimeWheelCraftingCpuPoolHost.class, Map.of(
                    "isCpuActive", true, "getActionSource", source, "getLevel", level));
            var pool = new TimeWheelCraftingCpuPool(host, Long.MAX_VALUE,
                    BUDGET - 1, Long.MAX_VALUE, true);
            var cpu = new TimeWheelCraftingCPU(host, Long.MAX_VALUE,
                    BUDGET - 1, Long.MAX_VALUE, true);
            var logic = cpu.getCraftingLogic();
            long amount = 2L * BUDGET + 7;
            var pattern = new IPatternDetails() {
                @Override public AEItemKey getDefinition() { return null; }
                @Override public IInput[] getInputs() {
                    return new IInput[] {new IInput() {
                        @Override public GenericStack[] getPossibleInputs() {
                            return new GenericStack[] {new GenericStack(INPUT, 1)};
                        }
                        @Override public long getMultiplier() { return 1; }
                        @Override public boolean isValid(AEKey key, Level world) { return INPUT.equals(key); }
                        @Override public AEKey getRemainingKey(AEKey key) { return null; }
                    }};
                }
                @Override public List<GenericStack> getOutputs() {
                    return List.of(new GenericStack(OUTPUT, 1));
                }
            };
            var plan = proxy(ICraftingPlan.class, Map.of(
                    "finalOutput", new GenericStack(OUTPUT, amount),
                    "emittedItems", new KeyCounter(), "patternTimes", Map.of(pattern, amount)));
            var linkData = new CompoundTag();
            linkData.putUUID("craftId", UUID.randomUUID());
            linkData.putBoolean("req", false);
            linkData.putBoolean("standalone", true);
            var link = new CraftingLink(linkData, cpu);
            var jobClass = Class.forName(Ae2LtTimeWheelCraftingCpuLogic.class.getName() + "$TimeWheelJob");
            var ctor = jobClass.getDeclaredConstructor(ICraftingPlan.class, Consumer.class,
                    CraftingLink.class, Integer.class, ElapsedTimeTracker.class);
            ctor.setAccessible(true);
            var job = ctor.newInstance(plan, (Consumer<AEKey>) key -> {}, link, null, new Tracker());
            field(logic.getClass(), "job").set(logic, job);
            var inventory = (ListCraftingInventory) field(logic.getClass(), "inventory").get(logic);
            inventory.insert(INPUT, amount, Actionable.MODULATE);
            var entryClass = Class.forName(TimeWheelCraftingCpuPool.class.getName() + "$PoolEntry");
            var entryCtor = entryClass.getDeclaredConstructor(UUID.class, long.class, TimeWheelCraftingCPU.class);
            entryCtor.setAccessible(true);
            var id = UUID.randomUUID();
            @SuppressWarnings("unchecked")
            var entries = (Map<UUID, Object>) field(pool.getClass(), "activeCpus").get(pool);
            entries.put(id, entryCtor.newInstance(id, 0L, cpu));
            long[] accepted = {0};
            boolean[] reject = {false};
            var provider = new ICraftingProvider() {
                @Override public List<IPatternDetails> getAvailablePatterns() { return List.of(pattern); }
                @Override public boolean isBusy() { return false; }
                @Override public boolean pushPattern(IPatternDetails details, KeyCounter[] inputs) {
                    if (reject[0]) return false;
                    assertEquals(1, inputs[0].get(INPUT));
                    accepted[0]++;
                    return true;
                }
            };
            var energy = (IEnergyService) Proxy.newProxyInstance(IEnergyService.class.getClassLoader(),
                    new Class<?>[] {IEnergyService.class}, (p, method, args) ->
                            method.getName().equals("extractAEPower") ? args[0] : null);
            var service = new CraftingService(proxy(IGrid.class, Map.of()),
                    proxy(IStorageService.class, Map.of()), energy) {
                @Override public Iterable<ICraftingProvider> getProviders(IPatternDetails details) {
                    return List.of(provider);
                }
            };
            for (int tick = 1; tick <= (rejectSecondTick ? 4 : 3); tick++) {
                clock.setLong(TickHandler.instance(), previousTick + tick);
                reject[0] = rejectSecondTick && tick == 2;
                long before = accepted[0];
                pool.tickCraftingLogic(energy, service);
                long expected = reject[0] ? 0 : Math.min(BUDGET, amount - before);
                assertEquals(expected, accepted[0] - before, "physical tick " + tick);
            }
            assertEquals(amount, accepted[0]);
            assertEquals(0, inventory.list.get(INPUT));
            assertTrue(((Map<?, ?>) field(jobClass, "tasks").get(job)).isEmpty());
            assertEquals(amount, logic.getWaitingFor(OUTPUT));
        } finally {
            types.set(null, previousTypes);
            clock.setLong(TickHandler.instance(), previousTick);
        }
    }

    static Level emptyLevel() throws Exception {
        // No world blocks are needed: extraction only reads game time. Avoid starting a server.
        var unsafeType = Class.forName("sun.misc.Unsafe");
        var unsafe = field(unsafeType, "theUnsafe").get(null);
        var level = (Level) unsafeType.getMethod("allocateInstance", Class.class).invoke(unsafe, ServerLevel.class);
        field(Level.class, "levelData").set(level, proxy(WritableLevelData.class, Map.of()));
        return level;
    }

    static Field field(Class<?> type, String name) throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static <T> T proxy(Class<T> type, Map<String, Object> values) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (p, m, args) -> {
            if (m.getName().equals("getGameTime")) return TickHandler.instance().getCurrentTick();
            if (values.containsKey(m.getName())) return values.get(m.getName());
            if (m.getReturnType() == boolean.class) return false;
            if (m.getReturnType() == long.class) return 0L;
            if (m.getReturnType() == int.class) return 0;
            if (m.getReturnType() == Optional.class) return Optional.empty();
            return null;
        }));
    }

    static final class Tracker extends ElapsedTimeTracker implements ElapsedTimeTrackerAccessor {
        @Override public void ae2lt$addMaxItems(long amount, AEKeyType type) { forward("addMaxItems", amount, type); }
        @Override public void ae2lt$decrementItems(long amount, AEKeyType type) { forward("decrementItems", amount, type); }
        private void forward(String name, long amount, AEKeyType type) {
            try {
                var method = ElapsedTimeTracker.class.getDeclaredMethod(name, long.class, AEKeyType.class);
                method.setAccessible(true);
                method.invoke(this, amount, type);
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        }
    }
}
