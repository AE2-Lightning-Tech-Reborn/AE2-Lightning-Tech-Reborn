package com.moakiee.ae2lt.overload.runtime.pattern;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.AEKeyFilter;
import appeng.crafting.inv.ChildCraftingSimulationState;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.service.helpers.NetworkCraftingProviders;
import com.moakiee.ae2lt.api.crafting.Ae2LtCraftingIntegration;
import com.moakiee.ae2lt.overload.pattern.PatternConversionService;
import com.moakiee.ae2lt.overload.runtime.cpu.OverloadCpuStateManager;
import com.moakiee.ae2lt.overload.runtime.model.EncodedOverloadPattern;
import com.moakiee.ae2lt.overload.runtime.model.MatchMode;
import com.moakiee.thunderbolt.core.crafting.batch.ParallelBatchCpuHelper;
import com.moakiee.thunderbolt.core.crafting.planner.FastCraftingPlanner;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Exercises the LT payload/runtime boundary against TB and AE2's real primary-only catalog. */
class OverloadPatternPipelineTest {
    @BeforeAll
    static void bootstrap() {
        if (LoadingModList.get() == null)
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void differentCapturedAndActualComponentsConnectThroughPlanningAndSettlement(
            boolean secondary, boolean reload) throws Exception {
        var raw = AEItemKey.of(Items.STONE);
        var target = AEItemKey.of(Items.DIAMOND);
        var primary = named(Items.PAPER, "producer-primary");
        var side = named(Items.STICK, "producer-secondary");
        var producerOutputs = new ArrayList<AEItemKey>(List.of(primary));
        var consumerInputs = new ArrayList<AEItemKey>(List.of(named(Items.PAPER, "consumer-primary")));
        if (secondary) {
            producerOutputs.add(side);
            consumerInputs.add(named(Items.STICK, "consumer-secondary"));
        }
        var slots = secondary ? Set.of(0, 1) : Set.of(0);
        var producer = pattern("producer", List.of(raw), producerOutputs, Set.of(), slots);
        var consumer = pattern("consumer", consumerInputs, List.of(target), slots, Set.of());
        var catalog = new NetworkCraftingProviders();
        ICraftingProvider provider = (ICraftingProvider) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {ICraftingProvider.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getAvailablePatterns" -> List.of(producer, consumer);
                    case "getEmitableItems" -> Set.of();
                    case "getPatternPriority" -> 0;
                    case "isBusy", "pushPattern" -> false;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        catalog.addProvider(provider);
        assertFalse(catalog.getCraftables(ignored -> true).contains(side));
        assertTrue(catalog.getCraftingFor(side).isEmpty(), "native AE2 does not index byproducts");
        ICraftingService service = (ICraftingService) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {ICraftingService.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getCraftables" -> catalog.getCraftables((AEKeyFilter) args[0]);
                    case "getCraftingFor" -> catalog.getCraftingFor((AEKey) args[0]);
                    case "canEmitFor" -> false;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        var network = new ListCraftingInventory(ignored -> {});
        network.insert(raw, 3, Actionable.MODULATE);
        var attempt = FastCraftingPlanner.tryAttempt(service,
                new ChildCraftingSimulationState(network), null, target, 3, false);
        assertNotNull(attempt.plan(), "both NBT-insensitive slots must connect before any output exists");
        assertEquals(Map.of(producer, 3L, consumer, 3L), attempt.plan().patternTimes());
        assertEquals(3, attempt.plan().usedItems().get(raw));
        assertTrue(attempt.plan().missingItems().isEmpty());

        Object owner = new Object();
        UUID job = UUID.randomUUID();
        var waiting = new KeyCounter();
        var cpu = new ListCraftingInventory(ignored -> {});
        cpu.insert(raw, 3, Actionable.MODULATE);
        try {
            var extracted = ParallelBatchCpuHelper.bulkExtract(producer, cpu, 3, false, Map.of(), null);
            assertNotNull(extracted);
            assertEquals(3, extracted.actualCopies);
            assertEquals(3, extracted.scaledInputs[0].get(raw));
            var registration = Ae2LtCraftingIntegration.prepareOverload(owner, producer, job, target, waiting);
            assertTrue(registration.canDispatch());
            assertTrue(registration.registerAccepted(3));
            producerOutputs.forEach(key -> waiting.add(key, 3));
            if (reload) reload(owner, job);
            for (var advertised : producerOutputs) {
                var actual = named(advertised.getItem(), "actual-machine-output");
                assertNotEquals(advertised, actual);
                long accepted = Ae2LtCraftingIntegration.claimOverloadOutput(owner, job, actual, 3,
                        Actionable.MODULATE, (expected, mode) -> {
                            long amount = Math.min(expected.amount(), waiting.get(expected.what()));
                            if (mode == Actionable.MODULATE) {
                                waiting.remove(expected.what(), amount);
                                cpu.insert(actual, amount, mode);
                            }
                            return amount;
                        });
                assertEquals(3, accepted);
            }
            producerOutputs.forEach(key -> assertEquals(0, waiting.get(key)));
            assertFalse(OverloadCpuStateManager.INSTANCE.hasAnyPending(owner));
            var finalInputs = ParallelBatchCpuHelper.bulkExtract(consumer, cpu, 3, false, Map.of(), null);
            assertNotNull(finalInputs);
            assertEquals(3, finalInputs.actualCopies);
            for (int slot = 0; slot < consumerInputs.size(); slot++) {
                assertEquals(3, finalInputs.scaledInputs[slot].get(
                        named(consumerInputs.get(slot).getItem(), "actual-machine-output")));
            }
        } finally {
            Ae2LtCraftingIntegration.clearOverloadState(owner);
        }
    }

    private static void reload(Object owner, UUID job) throws Exception {
        // Plain JUnit has no AE2 registry event. Restore the previous registry after the round trip,
        // and keep the memoized codec bound to the current registry for subsequent tests.
        var field = appeng.api.stacks.AEKeyTypesInternal.class.getDeclaredField("registry");
        field.setAccessible(true);
        var previous = field.get(null);
        var itemField = appeng.core.definitions.ItemDefinition.class.getDeclaredField("item");
        itemField.setAccessible(true);
        var missingItem = itemField.get(appeng.core.definitions.AEItems.MISSING_CONTENT);
        var holderField = net.neoforged.neoforge.registries.DeferredHolder.class.getDeclaredField("holder");
        holderField.setAccessible(true);
        var previousHolder = holderField.get(missingItem);
        var codec = ResourceLocation.CODEC.xmap(
                id -> appeng.api.stacks.AEKeyTypesInternal.getRegistry().get(id),
                appeng.api.stacks.AEKeyType::getId);
        var registry = Proxy.newProxyInstance(OverloadPatternPipelineTest.class.getClassLoader(),
                new Class<?>[] {net.minecraft.core.Registry.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "byNameCodec" -> codec;
                    case "get" -> appeng.api.stacks.AEKeyType.items().getId().equals(args[0])
                            ? appeng.api.stacks.AEKeyType.items() : null;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        field.set(null, registry);
        holderField.set(missingItem, Items.BARRIER.builtInRegistryHolder());
        try {
            var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            var saved = Ae2LtCraftingIntegration.saveOverloadState(owner, job, registries);
            Ae2LtCraftingIntegration.clearOverloadState(owner);
            Ae2LtCraftingIntegration.loadOverloadState(owner, job, saved, registries);
        } finally {
            field.set(null, previous);
            holderField.set(missingItem, previousHolder);
        }
    }

    private static Ae2OverloadPatternDetails pattern(String name, List<AEItemKey> inputKeys,
            List<AEItemKey> outputKeys, Set<Integer> fuzzyInputs, Set<Integer> fuzzyOutputs) {
        var definition = named(Items.PAPER, name + "-definition");
        var inputs = new ArrayList<ParsedPatternInput>();
        var outputs = new ArrayList<ParsedPatternOutput>();
        var rules = EncodedOverloadPattern.builder();
        for (int slot = 0; slot < inputKeys.size(); slot++) {
            inputs.add(new ParsedPatternInput(slot, inputKeys.get(slot).toStack()));
            rules.input(slot, fuzzyInputs.contains(slot) ? MatchMode.ID_ONLY : MatchMode.STRICT);
        }
        for (int slot = 0; slot < outputKeys.size(); slot++) {
            outputs.add(new ParsedPatternOutput(slot, outputKeys.get(slot).toStack(), slot == 0));
            rules.output(slot, fuzzyOutputs.contains(slot) ? MatchMode.ID_ONLY : MatchMode.STRICT);
        }
        var parsed = new ParsedPatternDefinition(new SourcePatternSnapshot(
                ResourceLocation.fromNamespaceAndPath("test", name), null, null), inputs, outputs);
        var payload = new PatternConversionService().createPayload(parsed, rules.build());
        var restored = OverloadPatternPayloadTagCodec.readPayload(OverloadPatternPayloadTagCodec.writePayload(payload));
        IPatternDetails source = new IPatternDetails() {
            @Override public AEItemKey getDefinition() { return definition; }
            @Override public IInput[] getInputs() {
                return inputKeys.stream().map(key -> new IInput() {
                    public GenericStack[] getPossibleInputs() { return new GenericStack[] {new GenericStack(key, 1)}; }
                    public long getMultiplier() { return 1; }
                    public boolean isValid(AEKey candidate, Level level) { return key.equals(candidate); }
                    public AEKey getRemainingKey(AEKey template) { return null; }
                }).toArray(IInput[]::new);
            }
            @Override public List<GenericStack> getOutputs() {
                return outputKeys.stream().map(key -> new GenericStack(key, 1)).toList();
            }
        };
        return new Ae2OverloadPatternDetails(definition,
                new OverloadPatternDetails(parsed, restored.encodedPattern()), source);
    }

    private static AEItemKey named(Item item, String name) {
        var stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return AEItemKey.of(stack);
    }
}
