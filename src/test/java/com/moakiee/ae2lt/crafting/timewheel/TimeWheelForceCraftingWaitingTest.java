package com.moakiee.ae2lt.crafting.timewheel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypesInternal;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.ElapsedTimeTracker;
import com.moakiee.ae2lt.me.key.LightningKey;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class TimeWheelForceCraftingWaitingTest {
    @Test
    void manualInputsParticipateInWaitingRoutingAndSimulationDoesNotConsumeThem() throws Exception {
        try (var fixture = new Fixture()) {
            var missing = new KeyCounter();
            missing.add(LightningKey.HIGH_VOLTAGE, 3);
            var initialize = fixture.job.getClass().getDeclaredMethod("addManualWaiting", KeyCounter.class);
            initialize.setAccessible(true);
            initialize.invoke(fixture.job, missing);

            assertEquals(3, fixture.logic.getWaitingFor(LightningKey.HIGH_VOLTAGE));
            var waiting = new HashSet<AEKey>();
            fixture.logic.getAllWaitingFor(waiting);
            assertTrue(waiting.contains(LightningKey.HIGH_VOLTAGE));
            assertEquals(3, fixture.logic.insert(LightningKey.HIGH_VOLTAGE, 9, Actionable.SIMULATE));
            assertEquals(3, fixture.logic.getWaitingFor(LightningKey.HIGH_VOLTAGE));
            assertEquals(0, fixture.logic.getInventory().list.get(LightningKey.HIGH_VOLTAGE));

            assertEquals(2, fixture.logic.insert(LightningKey.HIGH_VOLTAGE, 2, Actionable.MODULATE));
            assertEquals(1, fixture.logic.getWaitingFor(LightningKey.HIGH_VOLTAGE));
            assertEquals(2, fixture.logic.getInventory().list.get(LightningKey.HIGH_VOLTAGE));
            assertEquals(1, fixture.logic.insert(LightningKey.HIGH_VOLTAGE, 9, Actionable.MODULATE));
            assertEquals(0, fixture.logic.getWaitingFor(LightningKey.HIGH_VOLTAGE));
            assertEquals(0, fixture.logic.insert(LightningKey.HIGH_VOLTAGE, 1, Actionable.MODULATE));
            assertEquals(3, fixture.logic.getInventory().list.get(LightningKey.HIGH_VOLTAGE));
        }
    }

    @Test
    void manualWaitingSurvivesJobSerializationAndRebuildsItsRoutingKeys() throws Exception {
        var registryField = TimeWheelDispatchBoundaryTest.field(AEKeyTypesInternal.class, "registry");
        var previousRegistry = registryField.get(null);
        var typeCodec = ResourceLocation.CODEC.xmap(
                id -> AEKeyTypesInternal.getRegistry().get(id), AEKeyType::getId);
        var registry = Proxy.newProxyInstance(Registry.class.getClassLoader(),
                new Class<?>[] {Registry.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "byNameCodec" -> typeCodec;
                    case "get" -> LightningKey.HIGH_VOLTAGE.getType();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        registryField.set(null, registry);
        try (var fixture = new Fixture()) {
            var jobType = fixture.job.getClass();
            var missing = new KeyCounter();
            missing.add(LightningKey.HIGH_VOLTAGE, 7);
            var initialize = jobType.getDeclaredMethod("addManualWaiting", KeyCounter.class);
            initialize.setAccessible(true);
            initialize.invoke(fixture.job, missing);
            assertEquals(2, fixture.logic.insert(LightningKey.HIGH_VOLTAGE, 2, Actionable.MODULATE));

            var write = jobType.getDeclaredMethod("writeToNBT", HolderLookup.Provider.class);
            write.setAccessible(true);
            var saved = (CompoundTag) write.invoke(fixture.job, RegistryAccess.EMPTY);
            var read = jobType.getDeclaredMethod("readFromNBT", CompoundTag.class,
                    HolderLookup.Provider.class, Consumer.class, TimeWheelCraftingCPU.class);
            read.setAccessible(true);
            var restored = read.invoke(null, saved, RegistryAccess.EMPTY, (Consumer<AEKey>) key -> {}, fixture.cpu);
            TimeWheelDispatchBoundaryTest.field(fixture.logic.getClass(), "job").set(fixture.logic, restored);

            assertEquals(5, fixture.logic.getWaitingFor(LightningKey.HIGH_VOLTAGE));
            var waiting = new HashSet<AEKey>();
            fixture.logic.getAllWaitingFor(waiting);
            assertTrue(waiting.contains(LightningKey.HIGH_VOLTAGE));
            assertEquals(5, fixture.logic.insert(LightningKey.HIGH_VOLTAGE, 9, Actionable.SIMULATE));
            assertEquals(5, fixture.logic.getWaitingFor(LightningKey.HIGH_VOLTAGE));
        } finally {
            registryField.set(null, previousRegistry);
        }
    }

    @Test
    void submissionKeepsLoopMetadataAndManualWaitingUsesThePersistedInventory() throws Exception {
        String logic = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/crafting/timewheel/Ae2LtTimeWheelCraftingCpuLogic.java"));
        String pool = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/crafting/timewheel/TimeWheelCraftingCpuPool.java"));
        assertTrue(logic.contains("forced.original() instanceof LoopCraftingPlan"));
        assertTrue(logic.contains("new TimeWheelJob(forced.original()"));
        assertTrue(logic.contains("candidateJob.addManualWaiting(forced.manualMissing())"));
        assertTrue(logic.contains("insertWaitingFor(entry.getKey(), entry.getLongValue())"));
        assertTrue(logic.contains("data.put(NBT_WAITING_FOR, waitingFor.writeToNBT(registries))"));
        assertTrue(logic.contains("job.waitingFor.readFromNBT(data.getList(NBT_WAITING_FOR"));
        assertTrue(pool.contains("plan = forced.original()"));
    }

    private static final class Fixture implements AutoCloseable {
        final Ae2LtTimeWheelCraftingCpuLogic logic;
        final Object job;
        final TimeWheelCraftingCPU cpu;
        final Field keyTypes;
        final Object previousTypes;

        Fixture() throws Exception {
            if (net.neoforged.fml.loading.LoadingModList.get() == null) {
                net.neoforged.fml.loading.LoadingModList.of(java.util.List.of(), java.util.List.of(),
                        java.util.List.of(), java.util.List.of(), Map.of());
            }
            net.minecraft.SharedConstants.tryDetectVersion();
            net.minecraft.server.Bootstrap.bootStrap();
            keyTypes = TimeWheelDispatchBoundaryTest.field(
                    Class.forName("appeng.api.stacks.AEKeyTypesInternal"), "allTypes");
            previousTypes = keyTypes.get(null);
            if (previousTypes == null) {
                keyTypes.set(null, Set.of(LightningKey.HIGH_VOLTAGE.getType()));
            }
            var host = (TimeWheelCraftingCpuHost) Proxy.newProxyInstance(
                    TimeWheelCraftingCpuHost.class.getClassLoader(), new Class<?>[] {TimeWheelCraftingCpuHost.class},
                    (proxy, method, arguments) -> method.getName().equals("isCpuActive") ? true : null);
            cpu = new TimeWheelCraftingCPU(host, 1024, 0, 1, false);
            logic = cpu.getCraftingLogic();
            var plan = (ICraftingPlan) Proxy.newProxyInstance(ICraftingPlan.class.getClassLoader(),
                    new Class<?>[] {ICraftingPlan.class}, (proxy, method, arguments) -> switch (method.getName()) {
                        case "finalOutput" -> new GenericStack(LightningKey.EXTREME_HIGH_VOLTAGE, 1);
                        case "emittedItems" -> new KeyCounter();
                        case "patternTimes" -> Map.of();
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
            var link = new CraftingLink(CraftingCpuHelper.generateLinkData(UUID.randomUUID(), true, false), cpu);
            var jobType = Class.forName(Ae2LtTimeWheelCraftingCpuLogic.class.getName() + "$TimeWheelJob");
            var constructor = jobType.getDeclaredConstructor(ICraftingPlan.class, Consumer.class,
                    CraftingLink.class, Integer.class, ElapsedTimeTracker.class);
            constructor.setAccessible(true);
            job = constructor.newInstance(plan, (Consumer<AEKey>) key -> {}, link, null,
                    new TimeWheelDispatchBoundaryTest.Tracker());
            TimeWheelDispatchBoundaryTest.field(logic.getClass(), "job").set(logic, job);
        }

        @Override
        public void close() throws IllegalAccessException {
            keyTypes.set(null, previousTypes);
        }
    }
}
