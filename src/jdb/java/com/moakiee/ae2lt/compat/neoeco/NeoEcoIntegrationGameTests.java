package com.moakiee.ae2lt.compat.neoeco;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.*;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.service.CraftingService;
import com.moakiee.ae2lt.compat.OptionalBatchProviders;
import com.moakiee.thunderbolt.api.crafting.batch.*;
import com.moakiee.thunderbolt.core.crafting.batch.*;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real NeoECO facade plus transformed BatchExecutor; provider fixtures isolate the ownership contract. */
@GameTestHolder("ae2lt_neoeco")
@PrefixGameTestTemplate(false)
public final class NeoEcoIntegrationGameTests {
    @GameTest(templateNamespace = "ae2lt_machine_recharge", template = "empty")
    public static void acceptedSliceSettlesInputsOutputsAndEnergyOnce(GameTestHelper h) throws Exception { run(h, 0); }
    @GameTest(templateNamespace = "ae2lt_machine_recharge", template = "empty")
    public static void rejectedBatchAndOrdinaryRejectionRefundEverything(GameTestHelper h) throws Exception { run(h, 1); }
    @GameTest(templateNamespace = "ae2lt_machine_recharge", template = "empty")
    public static void indeterminateSubmissionIsQuarantinedWithoutRefundOrReplay(GameTestHelper h) throws Exception { run(h, 2); }
    @GameTest(templateNamespace = "ae2lt_machine_recharge", template = "empty")
    public static void slowFallbackPreservesJobIdentityAndConsumesOneCopy(GameTestHelper h) throws Exception { run(h, 3); }

    private static void run(GameTestHelper h, int mode) throws Exception {
        var recipe = (CraftingRecipe) h.getLevel().getRecipeManager().byKey(new ResourceLocation("minecraft:oak_planks")).orElseThrow();
        var grid = new ItemStack[9]; Arrays.fill(grid, ItemStack.EMPTY); grid[0] = new ItemStack(Items.OAK_LOG);
        var pattern = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeCraftingPattern(
                recipe, grid, new ItemStack(Items.OAK_PLANKS, 4), false, false), h.getLevel());
        h.assertTrue(pattern != null, "real AE2 crafting pattern decoded");
        var job = new Job(h.getLevel(), pattern);
        var api = new NeoEcoBatchApi(NeoEcoIntegrationGameTests.class.getClassLoader());
        var preparation = Class.forName("cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider$Preparation");
        int[] ordinary = {0}, submissions = {0}; long[] custody = {0};
        Predicate<Object> dispatch = batch -> {
            submissions[0]++;
            try {
                long copies = (long) batch.getClass().getMethod("craftCount").invoke(batch);
                h.assertTrue(copies == 3, "provider capacity bounds real facade");
                if (mode == 2) {
                    custody[0] = copies;
                    throw (RuntimeException) Class.forName("cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException")
                            .getConstructor(String.class, Throwable.class).newInstance("test uncertain custody", null);
                }
                if (mode == 1) return false;
                custody[0] = copies; return true;
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        };
        Class<?> aware = Class.forName("cn.dancingsnow.neoecoae.api.me.provider.ECOJobAwareCraftingProvider");
        var provider = (ICraftingProvider) Proxy.newProxyInstance(api.providerType.getClassLoader(),
                new Class<?>[]{ICraftingProvider.class, api.providerType, aware}, (p, m, a) -> switch (m.getName()) {
                    case "getAvailablePatterns" -> List.of(pattern);
                    case "isBusy" -> false;
                    case "eco$prepareFastPath" -> mode == 3 ? null : preparation.getConstructor(long.class,
                                    Class.forName("cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOStatefulBatchCalculator"),
                                    boolean.class, Predicate.class).newInstance(3L, null, false, dispatch);
                    case "pushPattern" -> {
                        ordinary[0]++;
                        h.assertTrue(a.length == 3 && job.id.equals(a[2]), "ordinary fallback retains task UUID");
                        if (mode == 3) { custody[0]++; yield true; }
                        yield false;
                    }
                    case "hashCode" -> System.identityHashCode(p);
                    case "equals" -> p == a[0];
                    case "toString" -> "NeoECO protocol provider";
                    default -> null;
                });
        double[] charged = {0};
        var energy = (IEnergyService) Proxy.newProxyInstance(IEnergyService.class.getClassLoader(),
                new Class<?>[]{IEnergyService.class}, (p, m, a) -> {
                    if (!m.getName().equals("extractAEPower")) return null;
                    if (a[1] == Actionable.MODULATE) charged[0] += (double) a[0];
                    return a[0];
                });
        var service = new CraftingService(proxy(IGrid.class), proxy(IStorageService.class), energy) {
            @Override public Iterable<ICraftingProvider> getProviders(IPatternDetails details) { return List.of(provider); }
        };
        var inventory = new ListCraftingInventory(ignored -> {});
        inventory.list.add(AEItemKey.of(Items.OAK_LOG), 8);
        var adapter = OptionalBatchProviders.createAdapter();
        h.assertTrue(adapter != null, "actual mod discovery enables the optional adapter");
        var schedule = new TickProviderDispatchSchedule(); schedule.beginTick(1);
        var result = BatchExecutor.runBatchOnly(8, BatchCpuAccounting.Mode.LINEAR, service, energy,
                job, inventory, new HashMap<IPatternDetails, IdentityHashMap<ICraftingProvider, Boolean>>(),
                () -> {}, Map.of(), 8, 8, false, schedule, adapter);
        if (mode == 2) {
            h.assertTrue("AMBIGUOUS_BATCH_PROVIDER_OWNERSHIP".equals(job.failure), "uncertain submission stops the job");
            h.assertTrue(ordinary[0] == 0 && job.waiting.list.isEmpty(), "no replay or confirmed output");
            h.assertTrue(inventory.list.get(AEItemKey.of(Items.OAK_LOG)) == 5, "only the admitted uncertain slice is quarantined");
        } else {
            long accepted = mode == 0 ? 3 : mode == 3 ? 1 : 0;
            h.assertTrue(result.dispatchedCopies() == accepted && custody[0] == accepted, "single ownership receipt");
            h.assertTrue(inventory.list.get(AEItemKey.of(Items.OAK_LOG)) == 8 - accepted, "refund unaccepted copies only");
            h.assertTrue(job.waiting.list.get(AEItemKey.of(Items.OAK_PLANKS)) == accepted * 4, "output ledger settles once");
            h.assertTrue(job.task.left == 8 - accepted, "task count settles once");
            h.assertTrue(charged[0] == accepted, "CPU energy settles once");
        }
        h.assertTrue(submissions[0] == (mode == 3 ? 0 : 1), "one batch submission");
        h.succeed();
    }

    private static <T> T proxy(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p,m,a) -> null));
    }
    private static final class Task implements BatchTaskHandle {
        final IPatternDetails pattern; long left = 8;
        Task(IPatternDetails pattern) { this.pattern = pattern; }
        public IPatternDetails details() { return pattern; }
        public long getValue() { return left; }
        public void setValue(long value) { left = value; }
    }
    private static final class Job implements BatchJobView {
        final Level level; final UUID id = UUID.randomUUID(); final Task task;
        final ListCraftingInventory waiting = new ListCraftingInventory(ignored -> {}); String failure;
        Job(Level level, IPatternDetails pattern) { this.level = level; task = new Task(pattern); }
        public Level level() { return level; }
        public UUID craftingId() { return id; }
        public Iterator<BatchTaskHandle> taskIterator() { return List.<BatchTaskHandle>of(task).iterator(); }
        public ListCraftingInventory waitingFor() { return waiting; }
        public void addContainerMaxItems(long amount, AEKeyType type) {}
        public void failDispatch(String reason, Throwable cause) { failure = reason; }
    }
}
