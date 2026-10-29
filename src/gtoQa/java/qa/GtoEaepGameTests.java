package qa;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCpuPool;
import com.moakiee.ae2lt.integration.eaep.EaepForcedCraftingPlanAccess;
import com.moakiee.thunderbolt.ae2.crafting.ExactPlanReport;
import com.moakiee.thunderbolt.ae2.crafting.ExactPlanReports;
import com.moakiee.thunderbolt.core.crafting.plan.LoopCraftingPlan;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2lt_qa")
@PrefixGameTestTemplate(false)
public final class GtoEaepGameTests {
    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void forcedCpuDispatchesThroughTheRealGtoProvider(GameTestHelper h) {
        GtoCpuGameTests.checkNativeProvider(h, false);
    }
    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void forcedPhysicalCpuPreservesManualWaitingThroughSimulationAndReload(GameTestHelper h) {
        GtoCpuGameTests.checkEaepManualState(h);
    }
    @GameTest(template = "empty", templateNamespace = "ae2lt_global_batch", timeoutTicks = 20)
    public static void advancedBlockingKeepsItsSettingAndOnlyUnblocksCompleteInputs(GameTestHelper h) throws Exception {
        var pos = h.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1));
        h.getLevel().setBlockAndUpdate(pos, appeng.core.definitions.AEBlocks.PATTERN_PROVIDER.block().defaultBlockState());
        var provider = ((appeng.blockentity.crafting.PatternProviderBlockEntity) h.getLevel().getBlockEntity(pos)).getLogic();
        var flag = Class.forName("com.extendedae_plus.api.advancedBlocking.IAdvancedBlocking");
        h.assertTrue(flag.isInstance(provider), "advanced blocking interface did not load");
        provider.getConfigManager().putSetting(appeng.api.config.Settings.BLOCKING_MODE, appeng.api.config.YesNo.YES);
        var first = AEItemKey.of(Items.COBBLESTONE);
        var second = AEItemKey.of(Items.IRON_INGOT);
        var pattern = new appeng.crafting.pattern.AEProcessingPattern(AEItemKey.of(
                appeng.api.crafting.PatternDetailsHelper.encodeProcessingPattern(
                        new GenericStack[] {new GenericStack(first, 1), new GenericStack(second, 1)},
                        new GenericStack[] {new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1)})));
        var held = new java.util.HashSet<appeng.api.stacks.AEKey>();
        var target = new appeng.helpers.patternprovider.PatternProviderTarget() {
            public long insert(appeng.api.stacks.AEKey key, long amount, appeng.api.config.Actionable mode) { return 0; }
            public boolean containsPatternInput(java.util.Set<appeng.api.stacks.AEKey> keys) { return keys.stream().anyMatch(held::contains); }
        };
        var inputs = java.util.Set.<appeng.api.stacks.AEKey>of(first, second);
        flag.getMethod("eap$setAdvancedBlocking", boolean.class).invoke(provider, true);
        held.add(first);
        h.assertTrue(com.moakiee.ae2lt.compat.gto.GtoEaepProviderSupport.containsBlockingInput(target, inputs, provider, pattern),
                "advanced blocking accepted an incomplete input set");
        held.add(second);
        h.assertTrue(!com.moakiee.ae2lt.compat.gto.GtoEaepProviderSupport.containsBlockingInput(target, inputs, provider, pattern),
                "advanced blocking rejected complete matching inputs");
        var saved = new net.minecraft.nbt.CompoundTag();
        provider.writeToNBT(saved);
        flag.getMethod("eap$setAdvancedBlocking", boolean.class).invoke(provider, false);
        h.assertTrue(com.moakiee.ae2lt.compat.gto.GtoEaepProviderSupport.containsBlockingInput(target, inputs, provider, pattern),
                "ordinary blocking was changed");
        provider.readFromNBT(saved);
        h.assertTrue((boolean) flag.getMethod("eap$getAdvancedBlocking").invoke(provider), "advanced setting was not persisted");
        h.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ae2lt_global_batch", timeoutTicks = 20)
    public static void encodingConsumesFromNetworkAndRefundsWithoutAnInventedSlot(GameTestHelper h) throws Exception {
        var counts = new KeyCounter();
        var blank = AEItemKey.of(appeng.core.definitions.AEItems.BLANK_PATTERN);
        counts.add(blank, 7);
        var storage = new appeng.api.storage.MEStorage() {
            public long insert(appeng.api.stacks.AEKey key, long amount, appeng.api.config.Actionable mode,
                    appeng.api.networking.security.IActionSource source) { if (mode == appeng.api.config.Actionable.MODULATE) counts.add(key, amount); return amount; }
            public long extract(appeng.api.stacks.AEKey key, long amount, appeng.api.config.Actionable mode,
                    appeng.api.networking.security.IActionSource source) {
                long extracted = Math.min(amount, counts.get(key));
                if (mode == appeng.api.config.Actionable.MODULATE) counts.remove(key, extracted);
                return extracted;
            }
            public net.minecraft.network.chat.Component getDescription() { return net.minecraft.network.chat.Component.literal("QA encoding storage"); }
        };
        var host = new EncodingHost(h.getLevel(), storage);
        var logic = host.getLogic();
        logic.getBlankPatternInv().setItemDirect(0, appeng.core.definitions.AEItems.BLANK_PATTERN.stack(3));
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "EaepEncodingQA"));
        var menu = new appeng.menu.me.items.PatternEncodingTermMenu(1, player.getInventory(), host);
        h.assertTrue(logic.getBlankPatternInv().getStackInSlot(0).isEmpty() && counts.get(blank) == 10,
                "legacy blanks were lost or left in an invisible slot");
        var accessor = Class.forName("com.extendedae_plus.mixin.ae2.accessor.PatternEncodingTermMenuAccessor");
        h.assertTrue(accessor.isInstance(menu), "encoding accessors did not load");
        h.assertTrue(accessor.getMethod("eap$getBlankPatternSlot").invoke(menu) == null, "GTO was given a fake blank slot");
        logic.setMode(appeng.parts.encoding.EncodingMode.PROCESSING);
        menu.setMode(appeng.parts.encoding.EncodingMode.PROCESSING);
        logic.getEncodedInputInv().setStack(0, new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1));
        logic.getEncodedOutputInv().setStack(0, new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1));
        menu.encode();
        h.assertTrue(appeng.api.crafting.PatternDetailsHelper.isEncodedPattern(logic.getEncodedPatternInv().getStackInSlot(0))
                && counts.get(blank) == 9, "encoding failed to consume exactly one network blank");
        var upload = Class.forName("com.extendedae_plus.util.uploadPattern.MatrixUploadUtil");
        var refund = upload.getDeclaredMethod("refundBlankPattern", net.minecraft.server.level.ServerPlayer.class,
                appeng.menu.me.items.PatternEncodingTermMenu.class, int.class);
        refund.setAccessible(true);
        refund.invoke(null, player, menu, 1);
        h.assertTrue(counts.get(blank) == 10 && player.getInventory().countItem(appeng.core.definitions.AEItems.BLANK_PATTERN.asItem()) == 0,
                "matrix upload refund was lost, duplicated or placed outside network storage");
        h.succeed();
    }

    private static final class EncodingHost extends appeng.parts.encoding.PatternEncodingTerminalPart {
        private final appeng.api.storage.MEStorage storage;
        private final net.minecraft.world.level.Level fixtureLevel;
        private EncodingHost(net.minecraft.world.level.Level level, appeng.api.storage.MEStorage storage) {
            super(appeng.core.definitions.AEParts.PATTERN_ENCODING_TERMINAL.asItem());
            this.fixtureLevel = level;
            this.storage = storage;
        }
        public net.minecraft.world.level.Level getLevel() { return fixtureLevel; }
        public appeng.api.storage.MEStorage getInventory() { return storage; }
        public void markForSave() { }
    }

    @GameTest(template = "empty", templateNamespace = "ae2lt_global_batch", timeoutTicks = 20)
    public static void actualProviderKeepsUpgradesWirelessAndScaledPatterns(GameTestHelper h) throws Exception {
        var provider = appeng.helpers.patternprovider.PatternProviderLogic.class;
        for (var contract : List.of("com.extendedae_plus.api.bridge.IInterfaceWirelessLinkBridge",
                "com.extendedae_plus.compat.PatternProviderLogicVirtualCompatBridge")) {
            h.assertTrue(Class.forName(contract).isAssignableFrom(provider), "lost EAEP provider contract " + contract);
        }
        h.assertTrue(provider.getMethod("getUpgrades") != null, "lost EAEP upgrades");
        var original = processing(Items.COBBLESTONE, Items.IRON_INGOT);
        var other = processing(Items.COBBLESTONE, Items.GOLD_INGOT);
        var scaled = Class.forName("com.extendedae_plus.api.crafting.ScaledProcessingPattern")
                .getConstructor(appeng.crafting.pattern.AEProcessingPattern.class, long.class).newInstance(original, 37L);
        h.assertTrue(com.moakiee.ae2lt.compat.gto.GtoEaepProviderSupport.contains(List.of(original), scaled),
                "scaled pattern did not use its original provider");
        h.assertTrue(!com.moakiee.ae2lt.compat.gto.GtoEaepProviderSupport.contains(List.of(other), scaled),
                "scaled pattern matched the wrong provider");
        h.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ae2lt_global_batch", timeoutTicks = 20)
    public static void nativeVirtualCompletionWaitsForTheLastRealTask(GameTestHelper h) throws Exception {
        var cluster = new appeng.me.cluster.implementations.CraftingCPUCluster(
                net.minecraft.core.BlockPos.ZERO, net.minecraft.core.BlockPos.ZERO);
        var cpu = cluster.craftingLogic;
        while (true) {
            if (cpu instanceof com.moakiee.ae2lt.compat.gto.GtoCompatibleCraftingCpuLogic compatible) {
                cpu = compatible.nativeDelegate();
                continue;
            }
            java.lang.reflect.Field delegate = null;
            for (var field : cpu.getClass().getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())
                        && field.getType() == appeng.crafting.execution.CraftingCpuLogic.class) delegate = field;
            }
            if (delegate == null) break;
            delegate.setAccessible(true);
            cpu = (appeng.crafting.execution.CraftingCpuLogic) delegate.get(cpu);
        }
        java.lang.reflect.Field jobField = null;
        for (var field : cpu.getClass().getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
            if (java.util.Arrays.stream(field.getType().getDeclaredFields())
                    .anyMatch(member -> member.getType() == appeng.crafting.CraftingLink.class)) jobField = field;
        }
        h.assertTrue(jobField != null, "native job field absent");
        var first = processing(Items.COBBLESTONE, Items.IRON_INGOT);
        var second = processing(Items.COBBLESTONE, Items.GOLD_INGOT);
        var counts = new Object2LongOpenHashMap<appeng.api.crafting.IPatternDetails>();
        counts.put(first, 1);
        counts.put(second, 1);
        var plan = new CraftingPlan(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1), 8, false, false,
                new KeyCounter(), new KeyCounter(), new KeyCounter(), counts);
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putUUID("craftId", java.util.UUID.randomUUID());
        tag.putBoolean("req", false);
        tag.putBoolean("standalone", true);
        var link = new appeng.crafting.CraftingLink(tag, cluster);
        link.setNexus(new appeng.crafting.CraftingLinkNexus(tag.getUUID("craftId")));
        var ctor = java.util.Arrays.stream(jobField.getType().getDeclaredConstructors())
                .filter(candidate -> candidate.getParameterCount() == 4).findFirst().orElseThrow();
        ctor.setAccessible(true);
        var job = ctor.newInstance(plan, (appeng.crafting.inv.ListCraftingInventory.ChangeListener) key -> {}, link, null);
        jobField.setAccessible(true);
        jobField.set(cpu, job);
        h.assertTrue(!com.moakiee.ae2lt.compat.gto.GtoEaepVirtualCpu.completeLastTask(cluster.craftingLogic, first),
                "virtual completion discarded another pending task");
        java.lang.reflect.Field tasksField = java.util.Arrays.stream(job.getClass().getDeclaredFields())
                .filter(field -> Map.class.isAssignableFrom(field.getType())).findFirst().orElseThrow();
        tasksField.setAccessible(true);
        var tasks = (Map<?, ?>) tasksField.get(job);
        var progress = tasks.get(second);
        var value = java.util.Arrays.stream(progress.getClass().getDeclaredFields())
                .filter(field -> field.getType() == long.class).findFirst().orElseThrow();
        value.setAccessible(true);
        value.setLong(progress, 0);
        h.assertTrue(com.moakiee.ae2lt.compat.gto.GtoEaepVirtualCpu.completeLastTask(cluster.craftingLogic, first),
                "last virtual task did not finish");
        h.assertTrue(link.isDone(), "virtual completion did not complete the crafting link");
        h.assertTrue(!link.isCanceled() && !cpu.hasJob(), "virtual completion canceled or retained the job");
        h.succeed();
    }

    private static appeng.crafting.pattern.AEProcessingPattern processing(
            net.minecraft.world.item.Item input, net.minecraft.world.item.Item output) {
        return new appeng.crafting.pattern.AEProcessingPattern(AEItemKey.of(
                appeng.api.crafting.PatternDetailsHelper.encodeProcessingPattern(
                        new GenericStack[] {new GenericStack(AEItemKey.of(input), 1)},
                        new GenericStack[] {new GenericStack(AEItemKey.of(output), 1)})));
    }

    @GameTest(template = "empty", templateNamespace = "ae2lt_global_batch", timeoutTicks = 20)
    public static void actualForcedPlanCopiesExactMissingAndRetainsDetachedSnapshots(GameTestHelper h) throws Exception {
        var missing = new KeyCounter();
        var key = AEItemKey.of(Items.COBBLESTONE);
        missing.add(key, Long.MAX_VALUE);
        var original = plan(missing);
        var wrapped = wrap(original);
        var data = EaepForcedCraftingPlanAccess.read(wrapped);
        h.assertTrue(data != null && data.original() == original, "forced plan lost its original");
        h.assertTrue(!wrapped.simulation() && wrapped.missingItems().isEmpty(), "force flag not honored");
        h.assertTrue(data.manualMissing().get(key) == Long.MAX_VALUE, "missing amount truncated");
        data.manualMissing().set(key, 37);
        h.assertTrue(EaepForcedCraftingPlanAccess.read(wrapped).manualMissing().get(key) == Long.MAX_VALUE,
                "manual missing snapshot leaked mutations");
        h.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ae2lt_global_batch", timeoutTicks = 20)
    public static void actualForcedPlanPreservesLoopSeedsAndExactAdmission(GameTestHelper h) throws Exception {
        var key = AEItemKey.of(Items.COBBLESTONE);
        var missing = new KeyCounter();
        missing.add(key, 3);
        var allowed = new LoopCraftingPlan(plan(missing), List.of(host -> true), Map.of(key, 3L), Map.of(), List.of());
        var denied = new LoopCraftingPlan(plan(missing), List.of(host -> false), Map.of(key, 3L), Map.of(), List.of());
        var pool = new TimeWheelCraftingCpuPool(null, 1024, 0, 1, false);
        h.assertTrue(pool.canHandle(wrap(allowed)) && !pool.canHandle(wrap(denied)), "CPU predicate lost");
        var exact = plan(new KeyCounter());
        ExactPlanReports.attach(exact, new ExactPlanReport(BigInteger.ZERO, Map.of(), false));
        h.assertTrue(EaepForcedCraftingPlanAccess.read(wrap(exact)) == null, "force wrapper hid exact preview");
        h.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "ae2lt_global_batch", timeoutTicks = 20)
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void nativeAndLegacyConsumersReadTheSameRealCounter(GameTestHelper h) {
        var key = AEItemKey.of(Items.COBBLESTONE);
        var counter = new KeyCounter();
        counter.add(key, Long.MAX_VALUE);
        var legacy = (Object2LongMap.Entry<?>) counter.iterator().next();
        h.assertTrue(legacy.getKey() == key && legacy.getLongValue() == Long.MAX_VALUE, "legacy iterator failed");
        Consumer raw = value -> {
            var entry = (Object2LongMap.Entry<?>) value;
            h.assertTrue(entry.getKey() == key && entry.getLongValue() == Long.MAX_VALUE, "legacy forEach failed");
        };
        counter.forEach(raw);
        counter.forEach(entry -> h.assertTrue(entry.getLongValue() == Long.MAX_VALUE, "native forEach failed"));
        h.succeed();
    }

    private static ICraftingPlan wrap(ICraftingPlan original) throws ReflectiveOperationException {
        return (ICraftingPlan) Class.forName("com.extendedae_plus.crafting.ForcedCraftingPlan")
                .getConstructor(ICraftingPlan.class).newInstance(original);
    }

    private static CraftingPlan plan(KeyCounter missing) {
        return new CraftingPlan(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1), 8, true, false,
                new KeyCounter(), new KeyCounter(), missing, new Object2LongOpenHashMap<>());
    }
}
