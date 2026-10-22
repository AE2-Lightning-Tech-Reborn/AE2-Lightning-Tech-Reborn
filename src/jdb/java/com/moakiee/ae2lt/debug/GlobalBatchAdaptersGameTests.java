package com.moakiee.ae2lt.debug;

import java.lang.reflect.Proxy;
import java.util.*;
import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.*;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.service.CraftingService;
import com.moakiee.thunderbolt.api.crafting.batch.*;
import com.moakiee.thunderbolt.core.crafting.batch.*;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Uses real transformed CraftingService/BatchExecutor with an ordinary provider and a global adapter. */
@GameTestHolder("ae2lt_global_batch")
@PrefixGameTestTemplate(false)
public final class GlobalBatchAdaptersGameTests {
    @GameTest(template = "empty")
    public static void overloadsShareGlobalDispatchAndRefundOnlyUnacceptedInputs(GameTestHelper helper) {
        for (var mode : List.of(BatchCpuAccounting.Mode.LINEAR, BatchCpuAccounting.Mode.SUCCESSFUL_DISPATCH)) {
            for (boolean explicitNull : List.of(false, true)) run(helper, mode, explicitNull, 3, false);
        }
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void rejectionRefundsTheWholeBatchAndChargesNothing(GameTestHelper helper) {
        run(helper, BatchCpuAccounting.Mode.LINEAR, false, 0, false);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void concreteAdmissionBoundsNativeExtractionAndRefundsRejection(GameTestHelper helper) {
        run(helper, BatchCpuAccounting.Mode.LINEAR, false, 2, false, 3);
        run(helper, BatchCpuAccounting.Mode.SUCCESSFUL_DISPATCH, false, 0, false, 0);
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void throwingPushStopsWithoutRefundOrOrdinaryReplay(GameTestHelper helper) {
        run(helper, BatchCpuAccounting.Mode.SUCCESSFUL_DISPATCH, false, 8, true);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void optionalAdaptersRegister(GameTestHelper helper) {
        boolean installed = net.minecraftforge.fml.ModList.get().isLoaded("extendedae_plus");
        boolean registered = BatchProviderAdapters.entries().stream()
                .anyMatch(e -> e.id().toString().equals("thunderbolt:extendedae_plus"));
        helper.assertTrue(installed == registered, "optional ExtendedAE Plus adapter follows mod presence");
        helper.succeed();
    }

    private static void run(GameTestHelper helper, BatchCpuAccounting.Mode mode, boolean explicitNull,
                            int accepted, boolean fail) {
        run(helper, mode, explicitNull, accepted, fail, -1);
    }

    private static void run(GameTestHelper helper, BatchCpuAccounting.Mode mode, boolean explicitNull,
                            int accepted, boolean fail, int admission) {
        var pattern = new Pattern();
        var provider = new Provider(pattern);
        var endpoint = new Endpoint(provider, accepted, fail);
        endpoint.admission = admission;
        var id = new ResourceLocation("ae2lt_global_batch", "test");
        BatchProviderAdapters.register(id, 1000, (candidate, details, job) -> candidate == provider ? endpoint : null);
        try {
            var energy = (IEnergyService) Proxy.newProxyInstance(IEnergyService.class.getClassLoader(),
                    new Class<?>[] {IEnergyService.class}, (p, m, a) -> m.getName().equals("extractAEPower") ? a[0] : null);
            var service = new CraftingService(proxy(IGrid.class), proxy(IStorageService.class), energy) {
                @Override public Iterable<ICraftingProvider> getProviders(IPatternDetails details) { return List.of(provider); }
            };
            var inventory = new ListCraftingInventory(key -> {});
            inventory.insert(AEItemKey.of(Items.STONE), 8, Actionable.MODULATE);
            var job = new Job(helper.getLevel(), pattern);
            var schedule = new TickProviderDispatchSchedule(); schedule.beginTick(1);
            var batched = new HashMap<IPatternDetails, IdentityHashMap<ICraftingProvider, Boolean>>();
            var result = explicitNull
                    ? BatchExecutor.runBatchOnly(8, mode, service, energy, job, inventory, batched,
                            () -> {}, Map.of(), 8, 8, false, schedule, null)
                    : BatchExecutor.runBatchOnly(8, mode, service, energy, job, inventory, batched,
                            () -> {}, Map.of(), 8, 8, false, schedule);
            helper.assertTrue(endpoint.calls == (admission == 0 ? 0 : 1), "admission controls batch submission");
            helper.assertTrue(provider.ordinaryCalls == 0, "no ordinary replay");
            helper.assertTrue(inventory.list.get(AEItemKey.of(Items.STONE)) == 8 - accepted, "refund only unowned copies");
            if (fail) {
                helper.assertTrue("AMBIGUOUS_BATCH_PROVIDER_OWNERSHIP".equals(job.failure), "job stopped on uncertain ownership");
                helper.assertTrue(job.waitingFor.list.isEmpty(), "uncertain work is not confirmed output");
            } else {
                helper.assertTrue(job.failure == null, "normal dispatch must not fail");
                helper.assertTrue(result.dispatchedCopies() == accepted, "accepted copy accounting");
                helper.assertTrue(job.waitingFor.list.get(AEItemKey.of(Items.SAND)) == accepted * 2, "expected output amount");
                helper.assertTrue(job.task.count == 8 - accepted, "remaining task amount");
                helper.assertTrue(result.consumedCpuOps() == (accepted == 0 ? 0 : mode == BatchCpuAccounting.Mode.LINEAR ? accepted : 1), "CPU-specific operation accounting");
            }
        } finally { BatchProviderAdapters.unregister(id); }
    }
    private static <T> T proxy(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (p, m, a) -> null));
    }
    private static final class Pattern implements IPatternDetails {
        @Override public AEItemKey getDefinition() { return AEItemKey.of(Items.STICK); }
        @Override public IInput[] getInputs() { return new IInput[] {new IInput() {
            @Override public GenericStack[] getPossibleInputs() { return new GenericStack[] {new GenericStack(AEItemKey.of(Items.STONE), 1)}; }
            @Override public long getMultiplier() { return 1; }
            @Override public boolean isValid(AEKey key, Level level) { return key.equals(AEItemKey.of(Items.STONE)); }
            @Override public AEKey getRemainingKey(AEKey key) { return null; }
        }}; }
        @Override public GenericStack[] getOutputs() { return new GenericStack[] {new GenericStack(AEItemKey.of(Items.SAND), 2)}; }
    }
    private static final class Provider implements ICraftingProvider {
        final IPatternDetails pattern; int ordinaryCalls;
        Provider(IPatternDetails pattern) { this.pattern = pattern; }
        @Override public List<IPatternDetails> getAvailablePatterns() { return List.of(pattern); }
        @Override public boolean isBusy() { return false; }
        @Override public boolean pushPattern(IPatternDetails p, KeyCounter[] inputs) { ordinaryCalls++; return false; }
    }
    private static final class Endpoint implements IBatchCraftingProvider {
        final Provider provider; final int accepted; final boolean fail; int calls;
        int admission = -1;
        Endpoint(Provider provider, int accepted, boolean fail) { this.provider = provider; this.accepted = accepted; this.fail = fail; }
        @Override public List<IPatternDetails> getAvailablePatterns() { return provider.getAvailablePatterns(); }
        @Override public boolean isBusy() { return false; }
        @Override public PreparedBatch prepareBatch(IPatternDetails pattern, KeyCounter[] inputs,
                                                   long count, BatchJobView job) {
            if (admission < 0) return null;
            var template = new KeyCounter[]{new KeyCounter()};
            template[0].addAll(inputs[0]);
            return new PreparedBatch() {
                @Override public long capacity() { return admission; }
                @Override public long push(long offered) { return pushBatch(pattern, template, offered); }
            };
        }
        @Override public long pushBatch(IPatternDetails p, KeyCounter[] inputs, long count) {
            calls++;
            if (count != (admission < 0 ? 8 : admission) || inputs[0].get(AEItemKey.of(Items.STONE)) != 1) throw new AssertionError("single-copy template contract");
            if (fail) throw new IllegalStateException("submission ownership unknown");
            return count - accepted;
        }
    }
    private static final class Task implements BatchTaskHandle {
        final IPatternDetails details; long count = 8;
        Task(IPatternDetails details) { this.details = details; }
        @Override public IPatternDetails details() { return details; }
        @Override public long getValue() { return count; }
        @Override public void setValue(long value) { count = value; }
    }
    private static final class Job implements BatchJobView {
        final Level level; final Task task; final ArrayList<BatchTaskHandle> tasks = new ArrayList<>();
        final ListCraftingInventory waitingFor = new ListCraftingInventory(key -> {}); String failure;
        final UUID craftingId = UUID.randomUUID();
        Job(Level level, IPatternDetails pattern) { this.level = level; task = new Task(pattern); tasks.add(task); }
        @Override public Level level() { return level; }
        @Override public Iterator<BatchTaskHandle> taskIterator() { return tasks.iterator(); }
        @Override public ListCraftingInventory waitingFor() { return waitingFor; }
        @Override public UUID craftingId() { return craftingId; }
        @Override public void addContainerMaxItems(long count, AEKeyType type) {}
        @Override public void failDispatch(String reason, Throwable cause) { failure = reason; }
    }
}
