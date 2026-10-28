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
import com.moakiee.thunderbolt.compat.extendedaeplus.ExtendedAePlusBatchAdapter;
import com.moakiee.thunderbolt.compat.neoeco.NeoEcoFastPathCompat;
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
    public static void optionalAdaptersRegisterAndNeoEcoAcceptsPartialBatch(GameTestHelper helper) throws Exception {
        var mods = net.minecraftforge.fml.ModList.get();
        helper.assertTrue(!Boolean.getBoolean("ae2lt.neoecoBatchTestRequired")
                || mods.isLoaded("neoecoae"), "required NeoECO runtime loaded");
        boolean eaepApi = hasClass("com.extendedae_plus.api.crafting.ScaledMolecularAssemblerPattern")
                && hasClass("com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixBlockEntity");
        assertOptionalRegistration(helper, "extendedae_plus", mods.isLoaded("extendedae_plus") && eaepApi,
                ExtendedAePlusBatchAdapter.createAdapter());
        if (mods.isLoaded("extendedae_plus") && !eaepApi) verifyOrdinaryEaepCore(helper);

        boolean forgeApi = hasClass("cn.dancingsnow.neoecoae.api.me.ECOBatchCapacityProvider")
                && hasClass("cn.dancingsnow.neoecoae.api.me.ECOBatchDispatchContext")
                && hasClass("cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOExtractedPatternExecution");
        boolean facadeApi = hasClass("cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider")
                && hasClass("cn.dancingsnow.neoecoae.api.me.ECOFastPathFacade");
        var neoAdapter = NeoEcoFastPathCompat.createAdapter();
        assertOptionalRegistration(helper, "neoeco", mods.isLoaded("neoecoae") && (forgeApi || facadeApi), neoAdapter);

        // The global Core contract must execute even when an optional version has no batch API.
        run(helper, BatchCpuAccounting.Mode.LINEAR, false, 3, false);
        run(helper, BatchCpuAccounting.Mode.SUCCESSFUL_DISPATCH, false, 8, true);
        if (neoAdapter == null) {
            verifyOrdinaryNeoEcoFallback(helper);
            System.out.println("GLOBAL_BATCH_NEOECO_NATIVE_PARTIAL_UNCOVERED reason="
                    + (mods.isLoaded("neoecoae") ? "released_api_absent" : "mod_absent")
                    + " global_partial=3/8 global_uncertain_ownership_verified=true ordinary_fallback_verified=true");
        } else if (forgeApi) {
            verifyNeoEcoForgePartialBatch(helper);
            verifyNeoEcoNativeAccounting(helper, false);
            verifyNeoEcoNativeAccounting(helper, true);
        } else {
            verifyNeoEcoFacadePartialBatch(helper);
        }
        System.out.println("GLOBAL_BATCH_OPTIONAL_CAPABILITIES eaep_super_api=" + eaepApi
                + " neoeco_forge_api=" + forgeApi + " neoeco_facade_api=" + facadeApi);
        helper.succeed();
    }

    private static void verifyNeoEcoNativeAccounting(GameTestHelper helper, boolean uncertain) throws Exception {
        var type = Class.forName("cn.dancingsnow.neoecoae.api.me.ECOBatchCapacityProvider");
        Class<?> admission;
        try { admission = Class.forName("cn.dancingsnow.neoecoae.crafting.execution.batch.ECOBatchAdmission"); }
        catch (ClassNotFoundException atomic) { admission = null; }
        final var receiptType = admission;
        var pattern = new Pattern();
        var job = new Job(helper.getLevel(), pattern);
        int[] calls = {0}, ordinary = {0};
        var provider = (ICraftingProvider) Proxy.newProxyInstance(type.getClassLoader(),
                new Class<?>[] {ICraftingProvider.class, type}, (p, method, args) -> switch (method.getName()) {
                    case "isBusy" -> false;
                    case "getAvailablePatterns" -> List.of(pattern);
                    case "hashCode" -> System.identityHashCode(p);
                    case "equals" -> p == args[0];
                    case "eco$getBatchCapacity" -> uncertain || receiptType != null ? 8L : 3L;
                    case "eco$pushBatch", "eco$pushBatchAdmission" -> {
                        calls[0]++;
                        var context = args[0];
                        var contextType = context.getClass();
                        helper.assertTrue(job.craftingId().equals(contextType.getMethod("craftingJobId").invoke(context)), "NeoECO job identity");
                        var inputs = (KeyCounter[]) contextType.getMethod("inputCounters").invoke(context);
                        helper.assertTrue(inputs[0].get(AEItemKey.of(Items.STONE)) == 1, "NeoECO context contains one copy");
                        if (uncertain) {
                            if (receiptType == null) throw new IllegalStateException("uncertain native batch");
                            yield receiptType.getMethod("indeterminate").invoke(null);
                        }
                        yield receiptType == null ? true
                                : receiptType.getMethod("accepted", long.class, boolean.class).invoke(null, 3L, false);
                    }
                    case "pushPattern" -> { ordinary[0]++; yield false; }
                    default -> throw new AssertionError(method);
                });
        var energy = (IEnergyService) Proxy.newProxyInstance(IEnergyService.class.getClassLoader(),
                new Class<?>[] {IEnergyService.class}, (p, m, a) -> m.getName().equals("extractAEPower") ? a[0] : null);
        var service = new CraftingService(proxy(IGrid.class), proxy(IStorageService.class), energy) {
            @Override public Iterable<ICraftingProvider> getProviders(IPatternDetails details) { return List.of(provider); }
        };
        var inventory = new ListCraftingInventory(key -> {});
        inventory.insert(AEItemKey.of(Items.STONE), 8, Actionable.MODULATE);
        var schedule = new TickProviderDispatchSchedule(); schedule.beginTick(1);
        var result = BatchExecutor.runBatchOnly(8, BatchCpuAccounting.Mode.LINEAR, service, energy, job,
                inventory, new HashMap<>(), () -> {}, Map.of(), 8, 8, false, schedule);
        helper.assertTrue(calls[0] == 1 && ordinary[0] == 0, "one native submit without ordinary replay");
        if (uncertain) {
            helper.assertTrue("AMBIGUOUS_BATCH_PROVIDER_OWNERSHIP".equals(job.failure), "uncertain NeoECO ownership halts CPU");
            helper.assertTrue(inventory.list.get(AEItemKey.of(Items.STONE)) == 0, "uncertain inputs are not refunded");
            helper.assertTrue(job.waitingFor.list.isEmpty(), "uncertain output is not confirmed");
        } else {
            helper.assertTrue(job.failure == null && result.dispatchedCopies() == 3, "three native copies accepted");
            helper.assertTrue(inventory.list.get(AEItemKey.of(Items.STONE)) == 5, "only three inputs owned by NeoECO");
            helper.assertTrue(job.task.count == 5 && job.waitingFor.list.get(AEItemKey.of(Items.SAND)) == 6, "partial task/output settlement");
        }
    }

    private static boolean hasClass(String name) {
        try { Class.forName(name, false, GlobalBatchAdaptersGameTests.class.getClassLoader()); return true; }
        catch (ClassNotFoundException absent) { return false; }
    }

    private static void assertOptionalRegistration(GameTestHelper helper, String name,
            boolean expected, BatchProviderAdapter factory) {
        var registered = BatchProviderAdapters.entries().stream()
                .filter(e -> e.id().equals(new ResourceLocation("thunderbolt", name))).findFirst();
        helper.assertTrue((factory != null) == expected, name + " factory matches the actual optional API");
        helper.assertTrue(registered.isPresent() == expected, name + " registration matches the actual optional API");
        if (expected) helper.assertTrue(registered.orElseThrow().priority() == -100
                && registered.orElseThrow().adapter().getClass() == factory.getClass(), name + " uses the native Core adapter");
    }

    private static void verifyOrdinaryEaepCore(GameTestHelper helper) {
        var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                new ResourceLocation("extendedae_plus", "assembler_matrix_pattern_plus"));
        helper.assertTrue(block != net.minecraft.world.level.block.Blocks.AIR, "real Forge EAEP pattern core exists");
        var pos = helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1));
        helper.getLevel().setBlockAndUpdate(pos, block.defaultBlockState());
        var owner = helper.getLevel().getBlockEntity(pos);
        helper.assertTrue(owner instanceof ICraftingProvider, "ordinary EAEP core retains its native provider");
        var provider = (ICraftingProvider) owner;
        var pattern = new Pattern();
        var job = new Job(helper.getLevel(), pattern);
        for (var entry : BatchProviderAdapters.entries()) helper.assertTrue(
                entry.adapter().adapt(provider, pattern, job) == null, "ordinary EAEP core is not claimed by " + entry.id());
        var input = new KeyCounter(); input.add(AEItemKey.of(Items.STONE), 1);
        helper.assertTrue(!provider.pushPattern(pattern, new KeyCounter[] {input}), "unformed native EAEP core rejects ordinary work");
        helper.assertTrue(input.get(AEItemKey.of(Items.STONE)) == 1, "native ordinary rejection preserves the unowned input");
    }

    private static void verifyOrdinaryNeoEcoFallback(GameTestHelper helper) {
        var pattern = new Pattern(); var provider = new Provider(pattern);
        var input = new KeyCounter(); input.add(AEItemKey.of(Items.STONE), 1);
        helper.assertTrue(!NeoEcoFastPathCompat.pushPattern(provider, pattern, new KeyCounter[] {input}, UUID.randomUUID()),
                "absent NeoECO batch API preserves ordinary provider rejection");
        helper.assertTrue(provider.ordinaryCalls == 1 && input.get(AEItemKey.of(Items.STONE)) == 1,
                "ordinary fallback calls once without taking rejected input ownership");
    }

    private static void verifyNeoEcoForgePartialBatch(GameTestHelper helper) throws Exception {
        var type = Class.forName("cn.dancingsnow.neoecoae.api.me.ECOBatchCapacityProvider");
        var pattern = craftingPattern(helper);
        long[] accepted = new long[1]; int[] ordinary = new int[1]; int[] pushes = new int[1];
        boolean[] fail = new boolean[1]; var uncertain = new IllegalStateException("uncertain native NeoECO submission");
        var provider = (ICraftingProvider) Proxy.newProxyInstance(type.getClassLoader(),
                new Class<?>[] {ICraftingProvider.class, type}, (p, method, args) -> switch (method.getName()) {
                    case "equals" -> p == args[0];
                    case "hashCode" -> System.identityHashCode(p);
                    case "toString" -> "native-neoeco-capacity-provider";
                    case "isBusy" -> false;
                    case "getAvailablePatterns" -> List.of(pattern);
                    case "eco$getBatchCapacity" -> 3L;
                    case "pushPattern" -> { ordinary[0]++; yield false; }
                    case "eco$pushBatch", "eco$pushBatchAdmission" -> {
                        pushes[0]++;
                        if (fail[0]) throw uncertain;
                        long offered = (long) args[1];
                        helper.assertTrue(offered == 3, "native NeoECO admission bounds the offer");
                        accepted[0] += offered;
                        yield method.getReturnType() == boolean.class ? true
                                : method.getReturnType().getMethod("accepted", long.class, boolean.class).invoke(null, offered, true);
                    }
                    default -> throw new AssertionError(method);
                });
        var entry = BatchProviderAdapters.entries().stream().filter(e -> e.id().toString().equals("thunderbolt:neoeco")).findFirst().orElseThrow();
        var endpoint = entry.adapter().adapt(provider, pattern, null);
        helper.assertTrue(endpoint != null, "real Forge NeoECO capacity interface is recognized");
        var template = new KeyCounter(); template.add(AEItemKey.of(Items.OAK_LOG), 1);
        var job = new Job(helper.getLevel(), pattern);
        helper.assertTrue(endpoint.pushBatch(pattern, new KeyCounter[] {template}, 8, job) == 5, "native NeoECO returns five unaccepted copies");
        helper.assertTrue(accepted[0] == 3 && pushes[0] == 1 && ordinary[0] == 0, "native NeoECO accepts three once without ordinary replay");
        helper.assertTrue(template.get(AEItemKey.of(Items.OAK_LOG)) == 1, "native NeoECO preserves the borrowed unit template");
        fail[0] = true;
        try {
            endpoint.pushBatch(pattern, new KeyCounter[] {template}, 8, job);
            throw new AssertionError("uncertain native submit must propagate");
        } catch (RuntimeException expected) {
            helper.assertTrue(expected == uncertain || expected.getCause() == uncertain, "native NeoECO preserves the uncertain ownership cause");
        }
        helper.assertTrue(pushes[0] == 2 && ordinary[0] == 0 && accepted[0] == 3, "uncertain native work is never replayed");
        helper.assertTrue(template.get(AEItemKey.of(Items.OAK_LOG)) == 1, "uncertain native submit preserves the caller template");
        System.out.println("GLOBAL_BATCH_NEOECO_NATIVE_CAPACITY_PASS accepted=3 offered=8");
    }

    private static void verifyNeoEcoFacadePartialBatch(GameTestHelper helper) throws Exception {
        var type = Class.forName("cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider");
        var prepared = Class.forName(type.getName() + "$Preparation");
        var preparationConstructor = Arrays.stream(prepared.getConstructors())
                .filter(c -> c.getParameterCount() == 4).findFirst().orElseThrow();
        long[] accepted = new long[1];
        int[] ordinary = new int[1];
        boolean[] fail = new boolean[1];
        var uncertain = (RuntimeException) Class.forName("cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException")
                .getConstructor(String.class, Throwable.class).newInstance("uncertain test submit", new IllegalStateException());
        java.util.function.Predicate<Object> submit = batch -> {
            if (fail[0]) throw uncertain;
            try { accepted[0] += (long) batch.getClass().getMethod("craftCount").invoke(batch); }
            catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            return true;
        };
        var provider = (ICraftingProvider) Proxy.newProxyInstance(type.getClassLoader(),
                new Class<?>[] {ICraftingProvider.class, type}, (p, method, args) -> switch (method.getName()) {
                    case "isBusy" -> false;
                    case "getAvailablePatterns" -> List.of();
                    case "eco$prepareFastPath" -> preparationConstructor.newInstance(3L, null, false, submit);
                    case "pushPattern" -> { ordinary[0]++; yield false; }
                    default -> null;
                });
        var pattern = craftingPattern(helper);
        var entry = BatchProviderAdapters.entries().stream().filter(e -> e.id().toString().equals("thunderbolt:neoeco")).findFirst().orElseThrow();
        var endpoint = entry.adapter().adapt(provider, pattern, null);
        helper.assertTrue(endpoint != null, "native NeoECO protocol recognized");
        var template = new KeyCounter(); template.add(AEItemKey.of(Items.OAK_LOG), 1);
        var job = new Job(helper.getLevel(), pattern);
        helper.assertTrue(endpoint.pushBatch(pattern, new KeyCounter[] {template}, 8, job) == 5, "NeoECO partial capacity returned five copies");
        helper.assertTrue(accepted[0] == 3 && ordinary[0] == 0, "three accepted via real NeoECO FastPath facade");
        helper.assertTrue(template.get(AEItemKey.of(Items.OAK_LOG)) == 1, "NeoECO did not mutate template");
        fail[0] = true;
        try {
            endpoint.pushBatch(pattern, new KeyCounter[] {template}, 8, job);
            throw new AssertionError("indeterminate submit must propagate");
        } catch (RuntimeException expected) {
            helper.assertTrue(expected == uncertain, "preserved NeoECO uncertain ownership signal");
        }
        helper.assertTrue(ordinary[0] == 0, "uncertain NeoECO submission was not replayed");
        System.out.println("GLOBAL_BATCH_NEOECO_NATIVE_FACADE_PASS accepted=3 offered=8");
    }

    private static IPatternDetails craftingPattern(GameTestHelper helper) {
        var raw = helper.getLevel().getRecipeManager().byKey(new ResourceLocation("oak_planks")).orElseThrow();
        var holder = (net.minecraft.world.item.crafting.CraftingRecipe) raw;
        var inputs = new net.minecraft.world.item.ItemStack[9]; Arrays.fill(inputs, net.minecraft.world.item.ItemStack.EMPTY);
        inputs[0] = new net.minecraft.world.item.ItemStack(Items.OAK_LOG);
        var pattern = appeng.api.crafting.PatternDetailsHelper.decodePattern(
                appeng.api.crafting.PatternDetailsHelper.encodeCraftingPattern(holder, inputs,
                        new net.minecraft.world.item.ItemStack(Items.OAK_PLANKS, 4), false, false), helper.getLevel());
        helper.assertTrue(pattern != null, "native crafting recipe decodes for optional dispatch");
        return pattern;
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
                helper.assertTrue(job.failureCause == endpoint.uncertain, "original uncertain ownership cause is preserved");
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
        final RuntimeException uncertain = new IllegalStateException("submission ownership unknown");
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
            if (fail) throw uncertain;
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
        final ListCraftingInventory waitingFor = new ListCraftingInventory(key -> {}); String failure; Throwable failureCause;
        final UUID craftingId = UUID.randomUUID();
        Job(Level level, IPatternDetails pattern) { this.level = level; task = new Task(pattern); tasks.add(task); }
        @Override public Level level() { return level; }
        @Override public Iterator<BatchTaskHandle> taskIterator() { return tasks.iterator(); }
        @Override public ListCraftingInventory waitingFor() { return waitingFor; }
        @Override public UUID craftingId() { return craftingId; }
        @Override public void addContainerMaxItems(long count, AEKeyType type) {}
        @Override public void failDispatch(String reason, Throwable cause) { failure = reason; failureCause = cause; }
    }
}
