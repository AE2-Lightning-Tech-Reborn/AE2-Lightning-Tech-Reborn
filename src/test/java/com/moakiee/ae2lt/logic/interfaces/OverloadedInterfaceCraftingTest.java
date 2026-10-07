package com.moakiee.ae2lt.logic.interfaces;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableSet;
import appeng.api.networking.crafting.CraftingJobStatus;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.helpers.MultiCraftingTracker;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCPU;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.loading.LoadingModList;

class OverloadedInterfaceCraftingTest {
    private static Field keyTypeRegistry;
    private static Object previousRegistry;

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        if (LoadingModList.get() == null) LoadingModList.of(List.of(), List.of(), null);
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        keyTypeRegistry = Class.forName("appeng.api.stacks.AEKeyTypesInternal").getDeclaredField("registry");
        keyTypeRegistry.setAccessible(true);
        previousRegistry = keyTypeRegistry.get(null);
        var name = new net.minecraft.resources.ResourceLocation("ae2lt", "interface_crafting_test_keys");
        var create = net.minecraftforge.registries.RegistryManager.class.getDeclaredMethod(
                "createRegistry", net.minecraft.resources.ResourceLocation.class,
                net.minecraftforge.registries.RegistryBuilder.class);
        create.setAccessible(true);
        @SuppressWarnings("unchecked")
        var registry = (net.minecraftforge.registries.IForgeRegistry<appeng.api.stacks.AEKeyType>) create.invoke(
                new net.minecraftforge.registries.RegistryManager("interface-crafting-test"), name,
                new net.minecraftforge.registries.RegistryBuilder<appeng.api.stacks.AEKeyType>()
                        .setName(name).disableSaving().disableSync());
        registry.register(appeng.api.stacks.AEKeyType.items().getId(), appeng.api.stacks.AEKeyType.items());
        registry.register(appeng.api.stacks.AEKeyType.fluids().getId(), appeng.api.stacks.AEKeyType.fluids());
        keyTypeRegistry.set(null, (java.util.function.Supplier<net.minecraftforge.registries.IForgeRegistry<appeng.api.stacks.AEKeyType>>) () -> registry);
    }

    @AfterAll
    static void restoreKeyTypes() throws Exception {
        keyTypeRegistry.set(null, previousRegistry);
    }

    @Test
    void networkJobsAndStockCoverTheTargetWithoutAnotherCalculation() {
        var h = new Harness();
        var key = AEItemKey.of(Items.DIAMOND);
        h.stock.put(key, 20L);
        h.cpus.add(cpu(key, 30));
        h.cpus.add(cpu(key, 50));
        h.slots = List.of(slot(0, key, 100));
        for (int tick = 0; tick < 200; tick++) h.tick(tick);
        assertTrue(h.calculations.isEmpty());
        assertEquals(0, h.submissions);
    }

    @Test
    void sameKeySlotsAggregateAndShareOnePendingCalculation() {
        var h = new Harness();
        var key = AEItemKey.of(Items.DIAMOND);
        h.slots = List.of(slot(0, key, 64), slot(1, key, 128));
        h.stock.put(key, 32L);
        h.cpus.add(cpu(key, 40));
        h.tick(0);
        for (int tick = 1; tick < 200; tick++) h.tick(tick);
        assertEquals(List.of(new GenericStack(key, 120)), h.requests);
        h.complete(0);
        assertTrue(h.tick(200));
        h.cpus.clear(); // Own persisted link still prevents a duplicate.
        for (int tick = 201; tick < 250; tick++) h.tick(tick);
        assertEquals(1, h.submissions);
        assertEquals(1, h.tracker.getRequestedJobs().size());
        assertEquals(1, h.calculations.size());
    }

    @Test
    void extractionReplenishesOnlyTheActualGap() {
        var h = new Harness();
        var key = AEItemKey.of(Items.DIAMOND);
        h.slots = List.of(slot(0, key, 100));
        h.stock.put(key, 95L);
        h.tick(0);
        assertEquals(List.of(new GenericStack(key, 5)), h.requests);
    }

    @Test
    void completedPlanIsDiscardedWhenAnotherCpuCoveredItsGap() {
        var h = new Harness();
        var key = AEItemKey.of(Items.DIAMOND);
        h.slots = List.of(slot(0, key, 100));
        h.tick(0);
        h.complete(0);
        h.cpus.add(cpu(key, 100));
        assertFalse(h.tick(1));
        assertEquals(0, h.submissions);
        h.tick(2);
        assertEquals(1, h.calculations.size());
    }

    @Test
    void completedPlanRecalculatesWhenOnlyPartOfItsGapRemains() {
        var h = new Harness();
        var key = AEItemKey.of(Items.DIAMOND);
        h.slots = List.of(slot(0, key, 100));
        h.tick(0);
        h.complete(0);
        h.stock.put(key, 60L);
        assertFalse(h.tick(1));
        assertEquals(0, h.submissions);
        h.tick(2);
        assertEquals(List.of(new GenericStack(key, 100), new GenericStack(key, 40)), h.requests);
    }

    @Test
    void twoInterfacesRecheckTheNetworkBeforeSubmittingTheirPlans() {
        var first = new Harness();
        var second = new Harness();
        second.cpus = first.cpus;
        var key = AEItemKey.of(Items.DIAMOND);
        first.slots = second.slots = List.of(slot(0, key, 64));
        first.tick(0);
        second.tick(0);
        first.complete(0);
        second.complete(0);
        assertTrue(first.tick(1));
        assertFalse(second.tick(1));
        assertEquals(1, first.submissions);
        assertEquals(0, second.submissions);
    }

    @Test
    void submissionFailureCoolsDownDespiteEveryTickPolling() {
        var h = new Harness();
        var key = AEItemKey.of(Items.DIAMOND);
        h.slots = List.of(slot(0, key, 64));
        h.failSubmission = true;
        h.tick(0);
        h.complete(0);
        h.tick(1);
        for (int tick = 2; tick <= 100; tick++) h.tick(tick);
        assertEquals(1, h.calculations.size());
        assertEquals(1, h.submissions);
        h.tick(101);
        assertEquals(2, h.calculations.size());
        h.failSubmission = false;
        h.complete(1);
        assertTrue(h.tick(102));
        assertEquals(2, h.submissions);
    }

    @Test
    void failedCancelledAndSimulationCalculationsAreClearedAndCooledDown() {
        for (int failure = 0; failure < 3; failure++) {
            var h = new Harness();
            var key = AEItemKey.of(Items.DIAMOND);
            h.slots = List.of(slot(0, key, 64));
            h.tick(0);
            var future = h.calculations.get(0);
            switch (failure) {
                case 0 -> future.completeExceptionally(new IllegalStateException("no recipe"));
                case 1 -> future.cancel(true);
                default -> future.complete(plan(new GenericStack(key, 64), true));
            }
            h.tick(1);
            for (int tick = 2; tick <= 100; tick++) h.tick(tick);
            assertEquals(1, h.calculations.size());
            assertEquals(0, h.submissions);
            h.tick(101);
            assertEquals(2, h.calculations.size());
        }
    }

    @Test
    void unlimitedSlotsOnlyRequestAnEmptyBufferOnce() {
        var h = new Harness();
        var key = AEItemKey.of(Items.DIAMOND);
        h.slots = List.of(new OverloadedInterfaceCrafting.Slot(0, key, 1, true),
                new OverloadedInterfaceCrafting.Slot(1, key, 1, true));
        h.stock.put(key, 1L);
        h.tick(0);
        assertTrue(h.requests.isEmpty());
        h.stock.clear();
        h.cpus.add(cpu(key, 1));
        h.tick(1);
        assertTrue(h.requests.isEmpty());
        h.cpus.clear();
        h.tick(2);
        assertEquals(List.of(new GenericStack(key, 1024L * key.getType().getAmountPerByte())), h.requests);
        h.complete(0);
        h.tick(3);
        for (int tick = 4; tick < 200; tick++) h.tick(tick);
        assertEquals(1, h.submissions);
        var link = h.tracker.getRequestedJobs().iterator().next();
        h.tracker.jobStateChange(link);
        h.cpus.clear();
        h.stock.put(key, 1L);
        h.tick(200);
        assertEquals(1, h.requests.size());
    }

    @Test
    void distinctKeysRemainIndependentAndLargeAmountsDoNotOverflow() {
        var h = new Harness();
        var diamond = AEItemKey.of(Items.DIAMOND);
        var emerald = AEItemKey.of(Items.EMERALD);
        h.slots = List.of(slot(0, diamond, Long.MAX_VALUE), slot(1, diamond, 64), slot(2, emerald, 64));
        h.tick(0);
        assertEquals(List.of(new GenericStack(diamond, Long.MAX_VALUE), new GenericStack(emerald, 64)), h.requests);
        h.cpus.add(cpu(diamond, Long.MAX_VALUE));
        h.cpus.add(cpu(diamond, Long.MAX_VALUE));
        h.tick(1);
        assertTrue(h.calculations.get(0).isCancelled());
        assertFalse(h.calculations.get(1).isCancelled());
        assertEquals(Long.MAX_VALUE, OverloadedInterfaceCrafting.inFlightAmounts(h.service).get(diamond));
    }

    @Test
    void changingAConfiguredKeyCancelsItsPendingPlan() {
        var h = new Harness();
        var diamond = AEItemKey.of(Items.DIAMOND);
        var emerald = AEItemKey.of(Items.EMERALD);
        h.slots = List.of(slot(0, diamond, 64));
        h.tick(0);
        h.slots = List.of(slot(0, emerald, 32));
        h.tick(1);
        assertTrue(h.calculations.get(0).isCancelled());
        assertEquals(List.of(new GenericStack(diamond, 64), new GenericStack(emerald, 32)), h.requests);
    }

    @Test
    void persistedLinksSuppressDuplicatesEvenIfTheFirstMatchingSlotMoves() {
        var h = new Harness();
        var key = AEItemKey.of(Items.DIAMOND);
        h.slots = List.of(slot(1, key, 64));
        h.tick(0);
        h.complete(0);
        h.tick(1);
        var tag = new CompoundTag();
        h.tracker.writeToNBT(tag);
        assertTrue(tag.contains("links-1"));
        var restored = new Harness();
        restored.tracker.readFromNBT(tag);
        restored.slots = List.of(slot(0, key, 64), slot(1, key, 64));
        for (int tick = 2; tick < 200; tick++) restored.tick(tick);
        assertTrue(restored.requests.isEmpty());
        assertEquals(1, restored.tracker.getRequestedJobs().size());
    }

    @Test
    void originalTrackerStillCancelsCalculationsOnCraftingCardRemoval() throws Exception {
        var h = new Harness();
        h.slots = List.of(slot(0, AEItemKey.of(Items.DIAMOND), 64));
        h.tick(0);
        var cancel = MultiCraftingTracker.class.getDeclaredMethod("cancel");
        cancel.setAccessible(true);
        cancel.invoke(h.tracker);
        h.policy.reset();
        assertTrue(h.calculations.get(0).isCancelled());
        h.tick(1);
        assertEquals(2, h.calculations.size());
    }

    @Test
    void nativeCpuCountsRemainingOutputInsteadOfItsOriginalJobTotal() throws Exception {
        var h = new Harness();
        var key = AEItemKey.of(Items.DIAMOND);
        var cpu = new CraftingCPUCluster(BlockPos.ZERO, BlockPos.ZERO);
        var constructor = Arrays.stream(ExecutingCraftingJob.class.getDeclaredConstructors())
                .filter(c -> c.getParameterCount() == 4).findFirst().orElseThrow();
        constructor.setAccessible(true);
        var listenerType = constructor.getParameterTypes()[1];
        var listener = Proxy.newProxyInstance(listenerType.getClassLoader(), new Class<?>[] {listenerType},
                (proxy, method, args) -> null);
        var job = constructor.newInstance(plan(new GenericStack(key, 100), false), listener, link(h.requester), null);
        var jobField = CraftingCpuLogic.class.getDeclaredField("job");
        jobField.setAccessible(true);
        jobField.set(cpu.craftingLogic, job);
        var remaining = ExecutingCraftingJob.class.getDeclaredField("remainingAmount");
        remaining.setAccessible(true);
        remaining.setLong(job, 30L);
        h.cpus.add(cpu);
        assertEquals(100, cpu.getJobStatus().crafting().amount());
        assertEquals(30, OverloadedInterfaceCrafting.inFlightAmounts(h.service).get(key));
        h.stock.put(key, 20L);
        h.slots = List.of(slot(0, key, 100));
        h.tick(0);
        assertEquals(List.of(new GenericStack(key, 50)), h.requests);
    }

    @Test
    void timeWheelCpuAlsoReportsTheRemainingOutputForStocking() throws Exception {
        var h = new Harness();
        var key = AEItemKey.of(Items.DIAMOND);
        var cpu = new TimeWheelCraftingCPU(null, 1024, 0, 1, false);
        var logic = cpu.getCraftingLogic();
        var jobField = logic.getClass().getDeclaredField("job");
        jobField.setAccessible(true);
        var constructor = Arrays.stream(jobField.getType().getDeclaredConstructors())
                .filter(c -> c.getParameterCount() == 4).findFirst().orElseThrow();
        constructor.setAccessible(true);
        java.util.function.Consumer<AEKey> listener = ignored -> {};
        var job = constructor.newInstance(plan(new GenericStack(key, 100), false), listener, link(h.requester), null);
        jobField.set(logic, job);
        var remaining = job.getClass().getDeclaredField("remainingAmount");
        remaining.setAccessible(true);
        remaining.setLong(job, 30L);
        h.cpus.add(cpu);
        assertEquals(100, cpu.getJobStatus().crafting().amount());
        assertEquals(30, OverloadedInterfaceCrafting.inFlightAmounts(h.service).get(key));
    }

    private static OverloadedInterfaceCrafting.Slot slot(int index, AEKey key, long amount) {
        return new OverloadedInterfaceCrafting.Slot(index, key, amount, false);
    }

    private static ICraftingPlan plan(GenericStack output, boolean simulation) {
        return (ICraftingPlan) Proxy.newProxyInstance(ICraftingPlan.class.getClassLoader(),
                new Class<?>[] {ICraftingPlan.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "finalOutput" -> output;
                    case "simulation" -> simulation;
                    case "emittedItems", "usedItems", "missingItems" -> new KeyCounter();
                    case "patternTimes" -> Map.of();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static CraftingLink link(ICraftingRequester requester) {
        var tag = new CompoundTag();
        tag.putUUID("craftId", UUID.randomUUID());
        tag.putBoolean("req", true);
        return new CraftingLink(tag, requester);
    }

    private static ICraftingCPU cpu(AEKey key, long amount) {
        return (ICraftingCPU) Proxy.newProxyInstance(ICraftingCPU.class.getClassLoader(),
                new Class<?>[] {ICraftingCPU.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "isBusy" -> true;
                    case "getJobStatus" -> new CraftingJobStatus(new GenericStack(key, amount), amount, 0, 0);
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static final class Harness {
        final ICraftingRequester requester = (ICraftingRequester) Proxy.newProxyInstance(
                ICraftingRequester.class.getClassLoader(), new Class<?>[] {ICraftingRequester.class},
                (proxy, method, args) -> { throw new UnsupportedOperationException(method.getName()); });
        final MultiCraftingTracker tracker = new MultiCraftingTracker(requester, 36);
        final OverloadedInterfaceCrafting policy = new OverloadedInterfaceCrafting(tracker, requester, 36);
        final Map<AEKey, Long> stock = new HashMap<>();
        final List<GenericStack> requests = new ArrayList<>();
        final List<CompletableFuture<ICraftingPlan>> calculations = new ArrayList<>();
        List<ICraftingCPU> cpus = new ArrayList<>();
        List<OverloadedInterfaceCrafting.Slot> slots = List.of();
        boolean failSubmission;
        int submissions;
        final ICraftingService service = (ICraftingService) Proxy.newProxyInstance(
                ICraftingService.class.getClassLoader(), new Class<?>[] {ICraftingService.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getCpus" -> ImmutableSet.copyOf(cpus);
                    case "beginCraftingCalculation" -> {
                        requests.add(new GenericStack((AEKey) args[2], (long) args[3]));
                        var future = new CompletableFuture<ICraftingPlan>();
                        calculations.add(future);
                        yield future;
                    }
                    case "submitJob" -> {
                        submissions++;
                        if (failSubmission) yield CraftingSubmitResult.CPU_BUSY;
                        var output = ((ICraftingPlan) args[0]).finalOutput();
                        cpus.add(cpu(output.what(), output.amount()));
                        yield CraftingSubmitResult.successful(link((ICraftingRequester) args[1]));
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });

        boolean tick(long now) {
            return policy.tick(slots, now, null, service, null, key -> stock.getOrDefault(key, 0L));
        }

        void complete(int index) {
            calculations.get(index).complete(plan(requests.get(index), false));
        }
    }
}
