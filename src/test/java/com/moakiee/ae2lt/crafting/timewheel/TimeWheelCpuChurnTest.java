package com.moakiee.ae2lt.crafting.timewheel;

import static com.moakiee.ae2lt.crafting.timewheel.TimeWheelDispatchBoundaryTest.field;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.ElapsedTimeTracker;
import appeng.hooks.ticking.TickHandler;
import appeng.me.service.CraftingService;
import com.moakiee.ae2lt.logic.tianshu.maintenance.TianshuInventoryMaintenanceHost;
import com.moakiee.ae2lt.logic.tianshu.maintenance.TianshuInventoryMaintenanceService;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.thunderbolt.api.crafting.cpu.ExtendedCraftingCpuCluster;
import com.moakiee.thunderbolt.core.crafting.cpu.DynamicCraftingCpuClusterIndex;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Real pool/dispatch/link lifecycles. Completed calculations are supplied as initialized jobs;
 * no game server or Mixin-transformed CraftingService is started by this test. */
class TimeWheelCpuChurnTest {
    private static final AEKey INPUT = LightningKey.EXTREME_HIGH_VOLTAGE;
    private static final AEKey OUTPUT = LightningKey.HIGH_VOLTAGE;

    @ParameterizedTest
    @ValueSource(ints = {1, 4, 32})
    void maintenanceChurnRetainsAndTicksExistingPlayerJob(int budget) throws Exception {
        if (net.minecraftforge.fml.loading.LoadingModList.get() == null) {
            net.minecraftforge.fml.loading.LoadingModList.of(List.of(), List.of(), new net.minecraftforge.fml.loading.EarlyLoadingException("test", null, List.of()));
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        var types = field(Class.forName("appeng.api.stacks.AEKeyTypesInternal"), "registry");
        var oldTypes = types.get(null);
        var clock = field(TickHandler.class, "tickCounter");
        long oldTick = clock.getLong(TickHandler.instance());
        if (oldTypes == null) types.set(null, ForgeKeyTypeTestRegistry.create(INPUT.getType()));
        try {
            var fixture = new Fixture(budget);
            var persistentMaintenance = new ArrayList<Job>();
            var background = new ArrayList<Job>();
            for (int i = 0; i < 12; i++) persistentMaintenance.add(fixture.addJob(4096, true));
            var player = fixture.addJob(257, false);
            long lastPlayerProgress = 0;
            int idleTicks = 0;
            for (int tick = 1; tick <= 80; tick++) {
                // Replenishments appear/disappear continuously, sharing a provider and output
                // with the player's job. Cancellation also leaves held inputs to be drained.
                background.add(fixture.addJob(1, true));
                background.add(fixture.addJob(4096, true));
                if (tick % 3 == 0) {
                    for (var job : background) if (!job.link.isDone()) job.cpu.cancelJob();
                    background.clear();
                }
                clock.setLong(TickHandler.instance(), oldTick + tick);
                fixture.tick();
                if (player.cpu.getCraftingLogic().hasJob()) {
                    assertTrue(fixture.pool.getActiveCpus().contains(player.cpu));
                    assertTrue(fixture.service.hasCpu(player.cpu));
                    idleTicks = player.accepted == lastPlayerProgress ? idleTicks + 1 : 0;
                    assertTrue(idleTicks < 20, "Player job starved while maintenance CPUs changed");
                }
                lastPlayerProgress = player.accepted;
                assertTrue(fixture.pool.getActiveCpus().size() <= 17, "Completed CPUs leaked");
            }
            for (var job : background) job.cpu.cancelJob();
            for (var job : persistentMaintenance) job.cpu.cancelJob();
            for (int tick = 81; tick <= 400 && fixture.pool.hasPersistentState(); tick++) {
                clock.setLong(TickHandler.instance(), oldTick + tick);
                fixture.tick();
            }
            assertEquals(257, player.accepted);
            // AE2's standalone CraftingLink has no nexus, so markDone() does not set isDone().
            assertFalse(player.cpu.getCraftingLogic().hasJob());
            assertFalse(player.link.isCanceled());
            assertFalse(fixture.pool.hasPersistentState());
            assertTrue(fixture.pool.getActiveCpus().isEmpty());
            assertEquals(1, fixture.registrations, "CPU churn must not re-register the physical pool");
            assertEquals(fixture.acceptedTotal, fixture.storedOutput);
            assertEquals(fixture.initialInput - fixture.acceptedTotal, fixture.returnedInput);
        } finally {
            types.set(null, oldTypes);
            clock.setLong(TickHandler.instance(), oldTick);
        }
    }

    private static final class Fixture {
        final DynamicCraftingCpuClusterIndex<IGridNode, ExtendedCraftingCpuCluster> index =
                new DynamicCraftingCpuClusterIndex<>();
        final Map<IPatternDetails, Job> jobs = new IdentityHashMap<>();
        final TimeWheelCraftingCpuPoolHost host;
        final TimeWheelCraftingCpuPool pool;
        final CraftingService service;
        final TianshuInventoryMaintenanceService maintenance;
        final IEnergyService energy;
        final IGridNode node;
        final int budget;
        long pendingOutput, acceptedTotal, storedOutput, returnedInput, initialInput;
        int registrations;

        Fixture(int budget) throws Exception {
            this.budget = budget;
            var storage = proxy(MEStorage.class, (name, args) -> {
                if (!name.equals("insert")) return null;
                long amount = (long) args[1];
                if (args[2] == Actionable.MODULATE) {
                    if (OUTPUT.equals(args[0])) storedOutput += amount;
                    if (INPUT.equals(args[0])) returnedInput += amount;
                }
                return amount;
            });
            var storageService = proxy(IStorageService.class,
                    (name, args) -> name.equals("getInventory") ? storage : null);
            var serviceRef = new CraftingService[1];
            var grid = proxy(IGrid.class, (name, args) -> switch (name) {
                case "getCraftingService" -> serviceRef[0];
                case "getStorageService" -> storageService;
                case "getMachineNodes" -> List.of();
                default -> null;
            });
            var level = TimeWheelDispatchBoundaryTest.emptyLevel();
            host = proxy(TimeWheelCraftingCpuPoolHost.class, (name, args) -> switch (name) {
                case "isCpuActive" -> true;
                case "getGrid" -> grid;
                case "getCpuLevel" -> level;
                case "getActionSource" -> IActionSource.empty();
                default -> null;
            });
            pool = new TimeWheelCraftingCpuPool(host, Long.MAX_VALUE, budget - 1, Long.MAX_VALUE, true);
            energy = proxy(IEnergyService.class,
                    (name, args) -> name.equals("extractAEPower") ? args[0] : null);
            var provider = new ICraftingProvider() {
                @Override public List<IPatternDetails> getAvailablePatterns() { return List.copyOf(jobs.keySet()); }
                @Override public boolean isBusy() { return false; }
                @Override public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
                    assertEquals(1, inputs[0].get(INPUT));
                    jobs.get(pattern).accepted++;
                    acceptedTotal++;
                    pendingOutput++;
                    return true;
                }
            };
            service = new CraftingService(grid, storageService, energy) {
                @Override public Iterable<ICraftingProvider> getProviders(IPatternDetails pattern) {
                    return List.of(provider);
                }
                @Override public boolean hasCpu(ICraftingCPU cpu) {
                    return index.clusters().stream().anyMatch(cluster -> cluster.containsCpu(cpu));
                }
            };
            serviceRef[0] = service;
            node = proxy(IGridNode.class, (name, args) -> name.equals("getGrid") ? grid : null);
            var maintenanceHost = proxy(TianshuInventoryMaintenanceHost.class, (name, args) -> switch (name) {
                case "getGrid" -> grid;
                case "getActionableNode" -> node;
                case "getActionSource" -> IActionSource.empty();
                default -> null;
            });
            maintenance = new TianshuInventoryMaintenanceService(maintenanceHost);
            index.addProvider(node);
            refreshIndex();
        }

        Job addJob(long amount, boolean replenishment) throws Exception {
            var cpu = new TimeWheelCraftingCPU(host, Long.MAX_VALUE, budget - 1, Long.MAX_VALUE, true);
            var pattern = new IPatternDetails() {
                @Override public AEItemKey getDefinition() { return null; }
                @Override public IInput[] getInputs() {
                    return new IInput[] {new IInput() {
                        @Override public GenericStack[] getPossibleInputs() {
                            return new GenericStack[] {new GenericStack(INPUT, 1)};
                        }
                        @Override public long getMultiplier() { return 1; }
                        @Override public boolean isValid(AEKey key, Level level) { return INPUT.equals(key); }
                        @Override public AEKey getRemainingKey(AEKey key) { return null; }
                    }};
                }
                @Override public GenericStack[] getOutputs() { return new GenericStack[]{new GenericStack(OUTPUT, 1)}; }
            };
            var plan = proxy(ICraftingPlan.class, (name, args) -> switch (name) {
                case "finalOutput" -> new GenericStack(OUTPUT, amount);
                case "emittedItems" -> new KeyCounter();
                case "patternTimes" -> Map.of(pattern, amount);
                default -> null;
            });
            var tag = new CompoundTag();
            var id = UUID.randomUUID();
            tag.putUUID("craftId", id);
            tag.putBoolean("req", false);
            tag.putBoolean("standalone", !replenishment);
            var link = new CraftingLink(tag, cpu);
            if (replenishment) {
                service.addLink(link);
                tag.putBoolean("req", true);
                service.addLink(new CraftingLink(tag, maintenance));
            }
            var jobType = Class.forName(Ae2LtTimeWheelCraftingCpuLogic.class.getName() + "$TimeWheelJob");
            var ctor = jobType.getDeclaredConstructor(ICraftingPlan.class, Consumer.class,
                    CraftingLink.class, Integer.class, ElapsedTimeTracker.class);
            ctor.setAccessible(true);
            var logic = cpu.getCraftingLogic();
            field(logic.getClass(), "job").set(logic, ctor.newInstance(plan,
                    (Consumer<AEKey>) key -> {}, link, null, new TimeWheelDispatchBoundaryTest.Tracker()));
            logic.getInventory().insert(INPUT, amount, Actionable.MODULATE);
            initialInput += amount;
            var entryType = Class.forName(TimeWheelCraftingCpuPool.class.getName() + "$PoolEntry");
            var entryCtor = entryType.getDeclaredConstructor(UUID.class, long.class, TimeWheelCraftingCPU.class);
            entryCtor.setAccessible(true);
            @SuppressWarnings("unchecked")
            var entries = (Map<UUID, Object>) field(pool.getClass(), "activeCpus").get(pool);
            entries.put(id, entryCtor.newInstance(id, 0L, cpu));
            field(pool.getClass(), "cpuListChanged").setBoolean(pool, true);
            var job = new Job(cpu, link);
            jobs.put(pattern, job);
            return job;
        }

        void refreshIndex() {
            index.refresh(provider -> pool, cluster -> {
                registrations++;
                cluster.prepareForCraftingService();
                cluster.restoreCraftingLinks(link -> service.addLink((CraftingLink) link));
            });
            assertEquals(Set.of(pool), index.clusters());
        }

        void tick() {
            // Rebuild the same provider index AE2/TB rebuilds for cpuListChanged. Selection and
            // tick routing retain the physical pool even when every virtual CPU except one changes.
            if (pool.consumeCpuListChanged()) index.replaceProviders(List.of(node));
            refreshIndex();
            // Exercise AE2's real per-tick CraftingLinkNexus membership/death checks as well.
            service.onServerEndTick();
            long before = acceptedTotal;
            for (var cluster : index.clusters()) cluster.tickCraftingLogic(energy, service);
            assertTrue(acceptedTotal - before <= budget);
            // Standalone jobs count their final result but let it fall through to ME storage.
            long consumed = pool.insert(OUTPUT, pendingOutput, Actionable.MODULATE);
            assertTrue(consumed >= 0 && consumed <= pendingOutput);
            storedOutput += pendingOutput - consumed;
            pendingOutput = 0;
        }
    }

    private static final class Job {
        final TimeWheelCraftingCPU cpu;
        final CraftingLink link;
        long accepted;
        Job(TimeWheelCraftingCPU cpu, CraftingLink link) { this.cpu = cpu; this.link = link; }
    }

    private static <T> T proxy(Class<T> type, java.util.function.BiFunction<String, Object[], Object> call) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (p, method, args) -> {
            var result = call.apply(method.getName(), args);
            if (result != null) return result;
            if (method.getName().equals("getGameTime")) return TickHandler.instance().getCurrentTick();
            if (method.getReturnType() == boolean.class) return false;
            if (method.getReturnType() == int.class) return 0;
            if (method.getReturnType() == long.class) return 0L;
            if (method.getReturnType() == Optional.class) return Optional.empty();
            return null;
        }));
    }
}
