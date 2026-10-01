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
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Uses real transformed CraftingService/BatchExecutor with an ordinary provider and a global adapter. */
@GameTestHolder("ae2lt_global_batch")
@PrefixGameTestTemplate(false)
public final class GlobalBatchAdaptersGameTests {
    @GameTest(template = "empty", timeoutTicks = 150)
    public static void realUselessFurnaceAcceptsEightCraftingCopiesFromTianshu(GameTestHelper helper) {
        if (!hasUselessBigIntegerApi()) { helper.succeed(); return; }
        UselessFurnaceProbe.run(helper, false);
    }

    @GameTest(template = "empty", timeoutTicks = 150)
    public static void realUselessFurnaceAcceptsEightOmniversalCopiesFromTianshu(GameTestHelper helper) {
        if (!hasUselessBigIntegerApi()) { helper.succeed(); return; }
        UselessFurnaceProbe.run(helper, true);
    }

    private static boolean hasUselessBigIntegerApi() {
        if (!net.neoforged.fml.ModList.get().isLoaded("useless_mod")) return false;
        try {
            Class.forName("com.sorrowmist.useless.api.crafting.bigint.AlloyFurnaceBigIntegerProvider", false,
                    GlobalBatchAdaptersGameTests.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException unavailable) { return false; }
    }

    /** Optional classes resolve only when an actual Useless jar is enabled for the probe. */
    private static final class UselessFurnaceProbe {
        static void run(GameTestHelper helper, boolean omniversal) {
            var corePos = new net.minecraft.core.BlockPos(5, 2, 3);
            for (var entry : com.sorrowmist.useless.content.blocks.multiblock.OmniversalAlloyFurnaceStructure.entries()) {
                var block = switch (entry.part()) {
                    case CORE -> com.sorrowmist.useless.init.ModBlocks.MULTIBLOCK_ALLOY_FURNACE_CORE.get();
                    case CASING -> com.sorrowmist.useless.init.ModBlocks.OMNIVERSAL_FURNACE_CASING.get();
                    case COIL -> com.sorrowmist.useless.init.ModBlocks.USELESS_COILS.get(1).get();
                    case AIR -> net.minecraft.world.level.block.Blocks.AIR;
                };
                helper.setBlock(entry.worldPos(corePos, net.minecraft.core.Direction.NORTH), block);
            }
            helper.setBlock(corePos.west(), com.sorrowmist.useless.init.ModBlocks.ME_PATTERN_ASSEMBLY.get());
            helper.setBlock(corePos.east(), com.sorrowmist.useless.init.ModBlocks.OMNIVERSAL_MOLD_HUB.get());
            helper.setBlock(corePos.west(2), appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block());
            helper.runAfterDelay(50, () -> {
                var assembly = (com.sorrowmist.useless.content.blockentities.multiblock.MePatternAssemblyBlockEntity)
                        helper.getBlockEntity(corePos.west());
                var core = assembly.getController();
                helper.assertTrue(core != null && core.isFormed(), "real furnace structure formed");
                core.getEnergyManager().setEnergyStored(1_000_000L);
                net.minecraft.world.item.ItemStack encoded;
                if (omniversal) {
                    var hub = (com.sorrowmist.useless.content.blockentities.multiblock.OmniversalMoldHubBlockEntity)
                            helper.getBlockEntity(corePos.east());
                    hub.getMolds().setStackInSlot(0, new net.minecraft.world.item.ItemStack(Items.STICK));
                    var recipe = com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog.entries(helper.getLevel())
                            .stream().filter(e -> e.identity().recipeId().toString().equals("ae2lt_omniversal:one_mold"))
                            .findFirst().orElseThrow();
                    encoded = com.moakiee.ae2lt.integration.useless.UselessModCompat.encodeViewerRecipe(recipe, helper.getLevel());
                } else {
                    var recipe = helper.getLevel().getRecipeManager().byKey(ResourceLocation.withDefaultNamespace("oak_planks"))
                            .orElseThrow();
                    var holder = new net.minecraft.world.item.crafting.RecipeHolder<>(recipe.id(),
                            (net.minecraft.world.item.crafting.CraftingRecipe) recipe.value());
                    var inputs = new net.minecraft.world.item.ItemStack[9];
                    Arrays.fill(inputs, net.minecraft.world.item.ItemStack.EMPTY);
                    inputs[0] = new net.minecraft.world.item.ItemStack(Items.OAK_LOG);
                    encoded = appeng.api.crafting.PatternDetailsHelper.encodeCraftingPattern(holder, inputs,
                            new net.minecraft.world.item.ItemStack(Items.OAK_PLANKS, 4), false, false);
                }
                helper.assertTrue(!encoded.isEmpty(), "native pattern encoded");
                assembly.getTerminalPatternInventory().setItemDirect(0, encoded);
                helper.runAfterDelay(2, () -> {
                    helper.assertTrue(assembly.getAvailablePatterns().size() == 1, "real furnace published its pattern");
                    var pattern = assembly.getAvailablePatterns().getFirst();
                    var inputKey = AEItemKey.of(omniversal ? Items.IRON_INGOT : Items.OAK_LOG);
                    var outputKey = AEItemKey.of(omniversal ? Items.GOLD_INGOT : Items.OAK_PLANKS);
                    long inputAmount = omniversal ? 16 : 8;
                    long outputAmount = omniversal ? 8 : 32;
                    var fixture = new FurnaceCpu(helper.getLevel(), assembly);
                    fixture.stock.add(inputKey, inputAmount);
                    var used = new KeyCounter(); used.add(inputKey, inputAmount);
                    var plan = new appeng.crafting.CraftingPlan(new GenericStack(outputKey, outputAmount), 100,
                            false, false, used, new KeyCounter(), new KeyCounter(), Map.of(pattern, 8L));
                    var submitted = fixture.cpu.getCraftingLogic().trySubmitJob(fixture.grid, plan,
                            appeng.api.networking.security.IActionSource.empty(), null);
                    helper.assertTrue(submitted.successful(), "Tianshu submitted real furnace plan: " + submitted);
                    // Cold recipe/class loading can exhaust the optional mod's global budget.
                    // Isolate it here so the baseline checks the real dispatch protocol itself.
                    var budget = new FurnaceBudget();
                    try {
                        budget.fullSpeed();
                        var usage = fixture.cpu.getCraftingLogic().tickCraftingLogic(fixture.energy, fixture.service, 1, 8);
                        helper.assertTrue(usage.successfulDispatches() == 1 && usage.dispatchedCopies() == 8,
                                "unthrottled Tianshu must accept eight copies: " + usage);
                        com.mojang.logging.LogUtils.getLogger().info("Unthrottled real furnace: omniversal={}, usage={}", omniversal, usage);
                    } finally { budget.restore(); }
                    var tasks = core.saveWithoutMetadata(helper.getLevel().registryAccess()).getCompound("AeTasks");
                    var pending = tasks.getList("QueuedCraftingOutputs", net.minecraft.nbt.Tag.TAG_COMPOUND);
                    helper.assertTrue(pending.size() == 1, "one real folded furnace output batch: " + tasks);
                    var outputs = pending.getCompound(0).getList("Outputs", net.minecraft.nbt.Tag.TAG_COMPOUND);
                    helper.assertTrue(outputs.size() == 1 && java.math.BigInteger.valueOf(outputAmount)
                                    .equals(new java.math.BigInteger(outputs.getCompound(0).getByteArray("Amount"))),
                            "real furnace owns exactly the expected output amount: " + tasks);
                    if (omniversal) verifySingleThrottle(helper, assembly, core, pattern, inputKey, budget);
                    helper.succeed();
                });
            });
        }

        private static void verifySingleThrottle(GameTestHelper helper, ICraftingProvider assembly,
                com.sorrowmist.useless.content.blockentities.multiblock.MultiblockAlloyFurnaceCoreBlockEntity core,
                IPatternDetails pattern, AEItemKey inputKey, FurnaceBudget budget) {
            try {
                var math = Class.forName("com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.AlloyFurnaceBigIntegerCrafting");
                boolean scalesRequested = Arrays.stream(math.getMethods()).anyMatch(m ->
                        m.getName().equals("maximumCraftingPatternCount") && m.getParameterCount() == 5);
                core.getEnergyManager().setEnergyStored(core.getEnergyManager().getMaxEnergyStoredLong());
                var target = assembly.getClass().getMethod("bigIntegerTarget").invoke(assembly);
                var prototype = new KeyCounter(); prototype.add(inputKey, 2);
                var inputs = new KeyCounter[] {prototype};
                budget.minimumSpeed();
                var capacity = target.getClass().getMethod("capacity", IPatternDetails.class, KeyCounter[].class,
                        java.math.BigInteger.class).invoke(target, pattern, inputs, java.math.BigInteger.valueOf(256));
                var accepted = (java.math.BigInteger) capacity.getClass().getMethod("accepted").invoke(capacity);
                long expected = scalesRequested ? 5 : 8;
                helper.assertTrue(accepted.longValueExact() == expected,
                        "capacity distinguishes request scaling from intrinsic-capacity scaling: " + accepted);
                // The actual CPU must accept the machine's once-throttled amount.
                var fixture = new FurnaceCpu(helper.getLevel(), assembly);
                fixture.stock.add(inputKey, 512);
                var used = new KeyCounter(); used.add(inputKey, 512);
                var plan = new appeng.crafting.CraftingPlan(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 256), 100,
                        false, false, used, new KeyCounter(), new KeyCounter(), Map.of(pattern, 256L));
                helper.assertTrue(fixture.cpu.getCraftingLogic().trySubmitJob(fixture.grid, plan,
                        appeng.api.networking.security.IActionSource.empty(), null).successful(), "throttled plan submitted");
                budget.minimumSpeed();
                var usage = fixture.cpu.getCraftingLogic().tickCraftingLogic(fixture.energy, fixture.service, 1, 256);
                helper.assertTrue(usage.successfulDispatches() == 1 && usage.dispatchedCopies() == expected,
                        "Tianshu admission must apply the 2% throttle only once: " + usage);
                helper.assertTrue(fixture.cpu.getCraftingLogic().getInventory().list.get(inputKey) == 512 - 2 * expected,
                        "only accepted copies consumed; rejected copies refunded");
                var pending = core.saveWithoutMetadata(helper.getLevel().registryAccess()).getCompound("AeTasks")
                        .getList("QueuedCraftingOutputs", net.minecraft.nbt.Tag.TAG_COMPOUND);
                helper.assertTrue(pending.size() == 2, "two real accepted furnace batches");
                var outputs = pending.getCompound(1).getList("Outputs", net.minecraft.nbt.Tag.TAG_COMPOUND);
                helper.assertTrue(outputs.size() == 1 && java.math.BigInteger.valueOf(expected)
                                .equals(new java.math.BigInteger(outputs.getCompound(0).getByteArray("Amount"))),
                        "real furnace owns exactly the once-throttled output amount");
                com.mojang.logging.LogUtils.getLogger().info("Single furnace throttle: scalesRequested={}, Tianshu 256 -> capacity={} -> usage={}",
                        scalesRequested, accepted, usage);
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            finally { budget.restore(); }
        }
    }

    /** Deterministic optional-mod budget fixture; no production state survives the test. */
    private static final class FurnaceBudget {
        final Class<?> type;
        final java.lang.reflect.Field window, spent, smoothed;
        final long savedWindow, savedSpent;
        final double savedSmoothed;
        FurnaceBudget() {
            try {
                type = Class.forName("com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.AlloyFurnaceTickBudget");
                window = field("windowStartNanos"); spent = field("spentThisWindow"); smoothed = field("smoothedNanos");
                savedWindow = window.getLong(null); savedSpent = spent.getLong(null); savedSmoothed = smoothed.getDouble(null);
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        }
        private java.lang.reflect.Field field(String name) throws ReflectiveOperationException {
            var result = type.getDeclaredField(name); result.setAccessible(true); return result;
        }
        void fullSpeed() { set(0); }
        void minimumSpeed() { set(10_000_000_000L); }
        private void set(long nanos) {
            try {
                // Freeze the wall-clock window during the short synchronous probe.
                window.setLong(null, System.nanoTime() + 1_000_000_000L);
                spent.setLong(null, nanos); smoothed.setDouble(null, 0D);
            } catch (IllegalAccessException e) { throw new AssertionError(e); }
        }
        void restore() {
            try {
                window.setLong(null, savedWindow); spent.setLong(null, savedSpent); smoothed.setDouble(null, savedSmoothed);
            } catch (IllegalAccessException e) { throw new AssertionError(e); }
        }
    }

    private static final class FurnaceCpu {
        final KeyCounter stock = new KeyCounter();
        final IEnergyService energy;
        final IGrid grid;
        final CraftingService service;
        final com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCPU cpu;

        FurnaceCpu(net.minecraft.server.level.ServerLevel level, ICraftingProvider provider) {
            var disk = new appeng.api.storage.MEStorage() {
                @Override public long insert(AEKey key, long amount, Actionable mode,
                        appeng.api.networking.security.IActionSource source) {
                    if (mode == Actionable.MODULATE) stock.add(key, amount);
                    return amount;
                }
                @Override public long extract(AEKey key, long amount, Actionable mode,
                        appeng.api.networking.security.IActionSource source) {
                    long taken = Math.min(amount, stock.get(key));
                    if (mode == Actionable.MODULATE) stock.remove(key, taken);
                    return taken;
                }
                @Override public void getAvailableStacks(KeyCounter out) { out.addAll(stock); }
                @Override public net.minecraft.network.chat.Component getDescription() {
                    return net.minecraft.network.chat.Component.literal("Furnace probe storage");
                }
            };
            energy = valuesProxy(IEnergyService.class, Map.of());
            var storage = valuesProxy(IStorageService.class, Map.of("getInventory", disk, "getCachedInventory", stock));
            var values = new HashMap<String, Object>();
            values.put("getStorageService", storage); values.put("getEnergyService", energy);
            grid = valuesProxy(IGrid.class, values);
            service = new CraftingService(grid, storage, energy);
            service.addGlobalCraftingProvider(provider);
            values.put("getCraftingService", service);
            var host = new com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCpuHost() {
                @Override public boolean isCpuActive() { return true; }
                @Override public IGrid getGrid() { return grid; }
                @Override public appeng.api.networking.security.IActionSource getActionSource() {
                    return appeng.api.networking.security.IActionSource.empty();
                }
                @Override public Level getLevel() { return level; }
                @Override public void markCpuDirty() { }
                @Override public net.minecraft.network.chat.Component getDisplayName() {
                    return net.minecraft.network.chat.Component.literal("Real furnace Tianshu probe");
                }
            };
            cpu = new com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCPU(host, Long.MAX_VALUE, 31, 256, false);
        }
    }

    private static <T> T valuesProxy(Class<T> type, Map<String, Object> values) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (p, m, a) -> {
            if (m.getName().equals("extractAEPower")) return a[0];
            if (values.containsKey(m.getName())) return values.get(m.getName());
            var returned = m.getReturnType();
            if (returned == boolean.class) return false;
            if (returned == int.class) return 0;
            if (returned == long.class) return 0L;
            if (returned == double.class) return 0D;
            if (returned == Optional.class) return Optional.empty();
            return null;
        }));
    }

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
    public static void throwingPushStopsWithoutRefundOrOrdinaryReplay(GameTestHelper helper) {
        run(helper, BatchCpuAccounting.Mode.SUCCESSFUL_DISPATCH, false, 8, true);
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void releasedUselessProtocolUsesGlobalRegistration(GameTestHelper helper) throws Exception {
        // The normal suite also runs without optional mods; an enabled probe must resolve globally.
        if (!net.neoforged.fml.ModList.get().isLoaded("useless_mod")) { helper.succeed(); return; }
        var type = Class.forName("com.sorrowmist.useless.api.crafting.SmartDoublingCraftingProvider");
        var patterns = Class.forName("com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.SmartDoublingPatterns");
        IPatternDetails[] received = new IPatternDetails[1];
        long[] input = new long[1];
        var provider = (ICraftingProvider) Proxy.newProxyInstance(type.getClassLoader(),
                new Class<?>[] {ICraftingProvider.class, type}, (p, method, args) -> switch (method.getName()) {
                    case "isBusy" -> false;
                    case "getAvailablePatterns" -> List.of();
                    case "pushPattern" -> {
                        received[0] = (IPatternDetails) args[0];
                        var counters = (KeyCounter[]) args[1];
                        input[0] = counters[0].get(AEItemKey.of(Items.STONE));
                        counters[0].reset();
                        yield true;
                    }
                    default -> null;
                });
        var entry = BatchProviderAdapters.entries().stream().filter(e -> e.id().toString().equals("thunderbolt:useless")).findFirst().orElseThrow();
        var pattern = new Pattern();
        var endpoint = entry.adapter().adapt(provider, pattern, null);
        helper.assertTrue(endpoint != null, "released Useless API registered by TB");
        var template = new KeyCounter(); template.add(AEItemKey.of(Items.STONE), 1);
        helper.assertTrue(endpoint.pushBatch(pattern, new KeyCounter[] {template}, 8) == 0, "accept eight logical copies");
        helper.assertTrue((long) patterns.getMethod("operationsPerPush", IPatternDetails.class).invoke(null, received[0]) == 8, "real Useless pattern multiplier");
        helper.assertTrue(input[0] == 8 && template.get(AEItemKey.of(Items.STONE)) == 1, "owned scaled inputs preserve borrowed template");
        var nested = received[0];
        var nestedInput = new KeyCounter(); nestedInput.add(AEItemKey.of(Items.STONE), 8);
        helper.assertTrue(endpoint.pushBatch(nested, new KeyCounter[] {nestedInput}, 4) == 0, "nested batch accepted");
        helper.assertTrue((long) patterns.getMethod("operationsPerPush", IPatternDetails.class).invoke(null, received[0]) == 32, "nested multiplier must compose");
        helper.assertTrue(input[0] == 32, "nested input amount");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void optionalAdaptersRegisterAndNeoEcoAcceptsPartialBatch(GameTestHelper helper) throws Exception {
        for (var mapping : Map.of("neoecoae", "neoeco", "useless_mod", "useless", "extendedae_plus", "extendedae_plus").entrySet()) {
            if (net.neoforged.fml.ModList.get().isLoaded(mapping.getKey())) {
                helper.assertTrue(BatchProviderAdapters.entries().stream().anyMatch(e -> e.id().toString().equals("thunderbolt:" + mapping.getValue())),
                        "TB registered adapter for " + mapping.getKey());
            }
        }
        if (!net.neoforged.fml.ModList.get().isLoaded("neoecoae")) { helper.succeed(); return; }
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
        var raw = helper.getLevel().getRecipeManager().byKey(ResourceLocation.withDefaultNamespace("oak_planks")).orElseThrow();
        var holder = new net.minecraft.world.item.crafting.RecipeHolder<>(raw.id(), (net.minecraft.world.item.crafting.CraftingRecipe) raw.value());
        var inputs = new net.minecraft.world.item.ItemStack[9]; Arrays.fill(inputs, net.minecraft.world.item.ItemStack.EMPTY);
        inputs[0] = new net.minecraft.world.item.ItemStack(Items.OAK_LOG);
        var pattern = appeng.api.crafting.PatternDetailsHelper.decodePattern(
                appeng.api.crafting.PatternDetailsHelper.encodeCraftingPattern(holder, inputs, new net.minecraft.world.item.ItemStack(Items.OAK_PLANKS, 4), false, false), helper.getLevel());
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
        helper.succeed();
    }

    private static void run(GameTestHelper helper, BatchCpuAccounting.Mode mode, boolean explicitNull,
                            int accepted, boolean fail) {
        var pattern = new Pattern();
        var provider = new Provider(pattern);
        var endpoint = new Endpoint(provider, accepted, fail);
        var id = ResourceLocation.fromNamespaceAndPath("ae2lt_global_batch", "test");
        BatchProviderAdapters.register(id, 1000, (candidate, details, job) -> candidate == provider ? endpoint : null);
        try {
            var energy = (IEnergyService) Proxy.newProxyInstance(IEnergyService.class.getClassLoader(),
                    new Class<?>[] {IEnergyService.class}, (p, m, a) -> m.getName().equals("extractAEPower") ? a[0] : null);
            var service = new CraftingService(proxy(IGrid.class), proxy(IStorageService.class), energy);
            service.addGlobalCraftingProvider(provider);
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
            helper.assertTrue(endpoint.calls == 1, "exactly one real batch call");
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
        @Override public List<GenericStack> getOutputs() { return List.of(new GenericStack(AEItemKey.of(Items.SAND), 2)); }
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
        Endpoint(Provider provider, int accepted, boolean fail) { this.provider = provider; this.accepted = accepted; this.fail = fail; }
        @Override public List<IPatternDetails> getAvailablePatterns() { return provider.getAvailablePatterns(); }
        @Override public boolean isBusy() { return false; }
        @Override public long pushBatch(IPatternDetails p, KeyCounter[] inputs, long count) {
            calls++;
            if (count != 8 || inputs[0].get(AEItemKey.of(Items.STONE)) != 1) throw new AssertionError("single-copy template contract");
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
        Job(Level level, IPatternDetails pattern) { this.level = level; task = new Task(pattern); tasks.add(task); }
        @Override public Level level() { return level; }
        @Override public Iterator<BatchTaskHandle> taskIterator() { return tasks.iterator(); }
        @Override public ListCraftingInventory waitingFor() { return waitingFor; }
        @Override public UUID craftingId() { return null; }
        @Override public void addContainerMaxItems(long count, AEKeyType type) {}
        @Override public void failDispatch(String reason, Throwable cause) { failure = reason; }
    }
}
