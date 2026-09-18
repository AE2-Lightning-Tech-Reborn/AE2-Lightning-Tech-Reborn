package com.moakiee.ae2lt.crafting.timewheel.allocation;

import com.moakiee.thunderbolt.core.crafting.batch.SharedBatchInputPattern;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.fluids.FluidStack;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;

import com.moakiee.thunderbolt.core.crafting.pattern.FuzzyPatternInputs;
import com.moakiee.thunderbolt.core.crafting.overload.OverloadedPatternDetails;

/** Uses actual AE2 component-bearing keys and native fuzzy enumeration, not an id-only test key. */
class ExecutionFuzzyInputAllocatorTest {
    static {
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final AEKey GLASS = AEItemKey.of(Items.GLASS);
    private static final AEKey MARKED = glass("marked");
    private static final AEKey THIRD = glass("third");
    private static final AEKey IRON = AEItemKey.of(Items.IRON_INGOT);
    private static final AEKey GOLD = AEItemKey.of(Items.GOLD_INGOT);

    @Test
    void fuzzyAndExactComponentsCanBothDispatchBeforeEitherOutputReturns() {
        var fuzzy = pattern(IRON, matching(GLASS, 1, 1, samePrimary(GLASS)));
        var exact = pattern(GOLD, matching(MARKED, 1, 1, MARKED::equals));
        var fixture = new Fixture(tasks(fuzzy, 4, exact, 4));
        fixture.add(GLASS, 4);
        fixture.add(MARKED, 4);
        assertNotEquals(GLASS, MARKED);
        assertEquals(GLASS.dropSecondary(), MARKED.dropSecondary());
        assertFalse(fuzzy instanceof FuzzyPatternInputs, "ordinary IInput semantics must be sufficient");

        var fuzzyBatch = fixture.bulk(fuzzy, 4);
        assertNotNull(fuzzyBatch);
        assertEquals(4, fuzzyBatch.actualCopies);
        assertEquals(4, fuzzyBatch.scaledInputs[0].get(GLASS));
        assertEquals(0, fuzzyBatch.scaledInputs[0].get(MARKED));
        fixture.accept(fuzzy, 4);
        var exactBatch = fixture.bulk(exact, 4);
        assertNotNull(exactBatch);
        assertEquals(4, exactBatch.scaledInputs[0].get(MARKED));
        assertTrue(fixture.inventory.list.isEmpty(), "both recipes launch with no intermediate output arrival");
    }

    @Test
    void exactAndFuzzyPositionsOfTheSamePatternSharePhysicalCapacity() {
        var combined = pattern(IRON, matching(GLASS, 1, 1, samePrimary(GLASS)),
                matching(MARKED, 1, 1, MARKED::equals));
        var fixture = new Fixture(tasks(combined, 3));
        fixture.add(GLASS, 3);
        fixture.add(MARKED, 3);
        var batch = fixture.bulk(combined, 3);
        assertNotNull(batch);
        assertEquals(3, batch.actualCopies);
        assertEquals(3, batch.scaledInputs[0].get(GLASS));
        assertEquals(3, batch.scaledInputs[1].get(MARKED));
        assertTrue(fixture.inventory.list.isEmpty());
    }

    @Test
    void partiallyOverlappingComponentRulesRemainDistinctDespiteTheSamePrimaryKey() {
        var left = pattern(IRON, matching(GLASS, 1, 1, Set.of(GLASS, MARKED)::contains));
        var right = pattern(GOLD, matching(MARKED, 1, 2, Set.of(MARKED, THIRD)::contains));
        var fixture = new Fixture(tasks(left, 1, right, 1));
        fixture.add(GLASS, 1);
        fixture.add(MARKED, 1);
        fixture.add(THIRD, 1);
        var first = fixture.take(left);
        assertNotNull(first);
        assertEquals(1, first[0].get(GLASS));
        fixture.accept(left, 1);
        var second = fixture.take(right);
        assertNotNull(second);
        assertEquals(1, second[0].get(MARKED));
        assertEquals(1, second[0].get(THIRD));
        assertTrue(fixture.inventory.list.isEmpty());
    }

    @Test
    void damagePredicatesDoNotCollapseIntoUnrestrictedSameIdMatching() {
        var full = tool(0);
        var light = tool(5);
        var worn = tool(20);
        var broad = pattern(IRON, matching(full, 1, 1, samePrimary(full)));
        var limited = pattern(GOLD, matching(full, 1, 1,
                key -> key instanceof AEItemKey item && samePrimary(full).test(key)
                        && item.toStack().getDamageValue() <= 10));
        var exact = pattern(AEItemKey.of(Items.DIAMOND), matching(full, 1, 1, full::equals));
        var fixture = new Fixture(tasks(broad, 1, limited, 1, exact, 1));
        fixture.add(full, 1);
        fixture.add(light, 1);
        fixture.add(worn, 1);
        var first = fixture.take(broad);
        assertNotNull(first);
        assertEquals(1, first[0].get(worn));
        fixture.accept(broad, 1);
        var second = fixture.take(limited);
        assertNotNull(second);
        assertEquals(1, second[0].get(light));
        fixture.accept(limited, 1);
        var third = fixture.take(exact);
        assertNotNull(third);
        assertEquals(1, third[0].get(full));
        assertTrue(fixture.inventory.list.isEmpty());
    }

    @Test
    void aNewAcceptedComponentWakesMissingStockWithoutRepeatedMembershipQueries() {
        var checks = new AtomicInteger();
        var fuzzy = pattern(IRON, matching(GLASS, 1, 1, key -> {
            checks.incrementAndGet();
            return samePrimary(GLASS).test(key) && !key.equals(THIRD);
        }));
        var exact = pattern(GOLD, matching(MARKED, 1, 1, MARKED::equals));
        var fixture = new Fixture(tasks(fuzzy, 1, exact, 1));
        fixture.add(MARKED, 1);
        assertFalse(fixture.allocator.allocate(fuzzy, fixture.inventory, null, 1).allowed());
        fixture.add(THIRD, 1);
        assertFalse(fixture.allocator.allocate(fuzzy, fixture.inventory, null, 1).allowed(),
                "a stocked same-id candidate still has to pass this position's predicate");
        var accepted = glass("arrived");
        fixture.add(accepted, 1);
        var allocation = fixture.allocator.allocate(fuzzy, fixture.inventory, null, 1);
        assertTrue(allocation.allowed());
        assertEquals(1, allocation.openChoices().available(0, accepted, 1));
        assertEquals(0, allocation.openChoices().available(0, THIRD, 1));
        int warmChecks = checks.get();
        for (int i = 0; i < 100; i++) {
            fixture.add(accepted, 1);
            assertTrue(fixture.allocator.allocate(fuzzy, fixture.inventory, null, 1).allowed());
        }
        fixture.add(AEItemKey.of(Items.DIRT), 1);
        assertTrue(fixture.allocator.allocate(fuzzy, fixture.inventory, null, 1).allowed());
        assertEquals(warmChecks, checks.get(), "quantity changes and unrelated keys do not invalidate candidate membership");
        assertEquals(1, fixture.inventory.list.get(MARKED));
    }

    @Test
    void fuzzyOutputPromisesNeverReplaceExactComponentReservesBeforeArrival() {
        var fuzzy = pattern(IRON, matching(GLASS, 1, 1, samePrimary(GLASS)));
        var exact = pattern(GOLD, matching(MARKED, 1, 1, MARKED::equals));
        IPatternDetails producer = new LateBoundOutput(MARKED,
                new IPatternDetails.IInput[] {matching(AEItemKey.of(Items.SAND), 1, 1, AEItemKey.of(Items.SAND)::equals)});
        var fixture = new Fixture(tasks(fuzzy, 2, exact, 2, producer, 1));
        fixture.add(GLASS, 2);
        fixture.add(MARKED, 2);
        var before = fixture.allocator.allocate(fuzzy, fixture.inventory, null, 2);
        assertEquals(0, before.openChoices().available(0, MARKED, 1),
                "same-id future output is not a promise of the marked component");
        fixture.accept(producer, 1);
        fixture.add(THIRD, 1); // The fuzzy producer actually returned a different component.
        var after = fixture.allocator.allocate(fuzzy, fixture.inventory, null, 2);
        assertEquals(1, after.openChoices().available(0, THIRD, 1));
        assertEquals(0, after.openChoices().available(0, MARKED, 1));
        assertEquals(2, fixture.inventory.list.get(MARKED));
    }

    @Test
    void duplicateFuzzyAnchorsNeverCountOnePhysicalVariantTwice() {
        var duplicateAnchors = new IPatternDetails.IInput() {
            @Override public GenericStack[] getPossibleInputs() {
                return new GenericStack[] {new GenericStack(GLASS, 1), new GenericStack(MARKED, 1)};
            }
            @Override public long getMultiplier() { return 1; }
            @Override public boolean isValid(AEKey key, Level level) { return samePrimary(GLASS).test(key); }
            @Override public AEKey getRemainingKey(AEKey key) { return null; }
        };
        var left = pattern(IRON, duplicateAnchors);
        var right = pattern(GOLD, matching(GLASS, 1, 1, samePrimary(GLASS)));
        var fixture = new Fixture(tasks(left, 1, right, 1));
        fixture.add(MARKED, 1);
        assertNull(fixture.take(left), "two anchors and two fuzzy slots still share one concrete stock unit");
        assertEquals(1, fixture.inventory.list.get(MARKED));
        fixture.add(THIRD, 1);
        assertNotNull(fixture.take(left));
        fixture.accept(left, 1);
        assertNotNull(fixture.take(right));
        assertTrue(fixture.inventory.list.isEmpty());
    }

    @Test
    void declaredSameIdInputStillRespectsExactConsumersWhenOutputsAreLateBound() {
        IPatternDetails fuzzy = new DeclaredSameIdInput(IRON,
                new IPatternDetails.IInput[] {matching(GLASS, 1, 1, samePrimary(GLASS))});
        var exact = pattern(GOLD, matching(MARKED, 1, 1, MARKED::equals));
        IPatternDetails producer = new LateBoundOutput(MARKED, new IPatternDetails.IInput[] {
                matching(AEItemKey.of(Items.SAND), 1, 1, AEItemKey.of(Items.SAND)::equals)});
        var fixture = new Fixture(tasks(fuzzy, 4, exact, 4, producer, 1));
        fixture.add(GLASS, 4);
        fixture.add(MARKED, 4);
        var batch = fixture.bulk(fuzzy, 4);
        assertNotNull(batch);
        assertEquals(4, batch.scaledInputs[0].get(GLASS));
        assertEquals(4, fixture.inventory.list.get(MARKED));
        fixture.accept(fuzzy, 4);
        assertNotNull(fixture.bulk(exact, 4), "fuzzy production does not impose a wait on exact stock already present");
    }

    @Test
    void fluidComponentsUseExactPhysicalUnitsForFuzzyAndExactDemands() {
        var plain = AEFluidKey.of(Fluids.WATER);
        var stack = new FluidStack(Fluids.WATER, 1_000);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("reserved"));
        var marked = AEFluidKey.of(stack);
        assertNotEquals(plain, marked);
        assertEquals(plain.dropSecondary(), marked.dropSecondary());
        var fuzzy = pattern(IRON, matching(plain, 1_000, 1, samePrimary(plain)));
        var exact = pattern(GOLD, matching(marked, 1_000, 1, marked::equals));
        var fixture = new Fixture(tasks(fuzzy, 2, exact, 2));
        fixture.add(plain, 2_000);
        fixture.add(marked, 2_000);
        var first = fixture.bulk(fuzzy, 2);
        assertNotNull(first);
        assertEquals(2, first.actualCopies);
        assertEquals(2_000, first.scaledInputs[0].get(plain));
        fixture.accept(fuzzy, 2);
        var second = fixture.bulk(exact, 2);
        assertNotNull(second);
        assertEquals(2_000, second.scaledInputs[0].get(marked));
        assertTrue(fixture.inventory.list.isEmpty());
    }

    @Test
    void rejectedFuzzyBatchRestoresTheActualComponentKeys() {
        var fuzzy = pattern(IRON, matching(GLASS, 1, 1, samePrimary(GLASS)));
        var exact = pattern(GOLD, matching(MARKED, 1, 1, MARKED::equals));
        var fixture = new Fixture(tasks(fuzzy, 4, exact, 4));
        fixture.add(THIRD, 4);
        fixture.add(MARKED, 4);
        var batch = fixture.bulk(fuzzy, 4);
        assertNotNull(batch);
        assertEquals(4, batch.scaledInputs[0].get(THIRD));
        TimeWheelInputExtractor.reinject(batch, 3, fixture.inventory);
        fixture.accept(fuzzy, 1);
        assertEquals(3, fixture.inventory.list.get(THIRD));
        assertEquals(4, fixture.inventory.list.get(MARKED));
        assertEquals(0, fixture.inventory.list.get(GLASS), "rollback must not insert the fuzzy anchor instead of the concrete key");
        var retry = fixture.bulk(fuzzy, 3);
        assertNotNull(retry);
        assertEquals(3, retry.scaledInputs[0].get(THIRD));
        fixture.accept(fuzzy, 3);
        assertNotNull(fixture.bulk(exact, 4));
        assertTrue(fixture.inventory.list.isEmpty());
    }

    @Test
    void oneShotOverloadedVariantsAreRetiredAndCanBeDiscoveredAgain() {
        var checks = new AtomicInteger();
        IPatternDetails fuzzy = new OverloadedInput(IRON, new IPatternDetails.IInput[] {
                matching(GLASS, 1, 1, key -> { checks.incrementAndGet(); return samePrimary(GLASS).test(key); })});
        var exact = pattern(GOLD, matching(MARKED, 1, 1, MARKED::equals));
        var fixture = new Fixture(tasks(fuzzy, 1, exact, 1));
        fixture.add(MARKED, 1);
        assertFalse(fixture.allocator.allocate(fuzzy, fixture.inventory, null, 1).allowed());
        var baseline = fixture.allocator.cacheStats();
        for (int i = 0; i < 6_000; i++) {
            var transientKey = glass("one-shot-" + i);
            fixture.add(transientKey, 1);
            var allocation = fixture.allocator.allocate(fuzzy, fixture.inventory, null, 1);
            assertTrue(allocation.allowed());
            assertEquals(1, allocation.openChoices().available(0, transientKey, 1));
            assertEquals(0, allocation.openChoices().available(0, MARKED, 1));
            assertNotNull(fixture.take(fuzzy), "exercise the positive-key index and publish the extraction proof");
            assertFalse(fixture.allocator.allocate(fuzzy, fixture.inventory, null, 1).allowed());
            assertEquals(baseline, fixture.allocator.cacheStats(), "expired candidates cannot accumulate in any cache");
        }
        int beforeReappearance = checks.get();
        var old = glass("one-shot-0");
        fixture.add(old, 1);
        assertNotNull(fixture.take(fuzzy));
        assertTrue(checks.get() > beforeReappearance, "a retired key must be validated again");
        assertEquals(1, fixture.inventory.list.get(MARKED));
    }

    @Test
    void durabilityRemainderIndexDoesNotKeepEveryPastToolVariant() {
        var anchor = tool(0);
        var cyclingInput = new IPatternDetails.IInput() {
            @Override public GenericStack[] getPossibleInputs() { return new GenericStack[] {new GenericStack(anchor, 1)}; }
            @Override public long getMultiplier() { return 1; }
            @Override public boolean isValid(AEKey key, Level level) { return samePrimary(anchor).test(key); }
            @Override public AEKey getRemainingKey(AEKey key) {
                return tool(((AEItemKey) key).toStack().getDamageValue() + 1);
            }
        };
        var cycling = pattern(IRON, cyclingInput);
        var exact = pattern(GOLD, matching(anchor, 1, 1, anchor::equals));
        var fixture = new Fixture(tasks(cycling, 2_000, exact, 1));
        fixture.add(anchor, 1);
        for (int damage = 2; damage < 2_002; damage++) {
            var worn = tool(damage);
            fixture.add(worn, 1);
            var taken = fixture.take(cycling);
            assertNotNull(taken);
            assertEquals(1, taken[0].get(worn));
            assertEquals(1, fixture.inventory.list.get(anchor));
            fixture.accept(cycling, 1);
            fixture.allocator.allocate(cycling, fixture.inventory, null, 1);
            var stats = fixture.allocator.cacheStats();
            assertEquals(0, stats.liveVariants());
            assertTrue(stats.validityEntries() <= 2);
            assertTrue(stats.returnEntries() <= 2);
            assertTrue(stats.outputEntries() <= 3, "derived damaged outputs must be removed with their source variant");
        }
    }

    @Test
    void unqueriedInventoryChurnHasABoundedQueueAndRescansCurrentStock() {
        var fuzzy = pattern(IRON, matching(GLASS, 1, 1, samePrimary(GLASS)));
        var exact = pattern(GOLD, matching(MARKED, 1, 1, MARKED::equals));
        var fixture = new Fixture(tasks(fuzzy, 1, exact, 1));
        fixture.add(MARKED, 1);
        fixture.allocator.allocate(fuzzy, fixture.inventory, null, 1);
        for (int i = 0; i < 12_000; i++) {
            var key = glass("unqueried-" + i);
            fixture.add(key, 1);
            fixture.inventory.extract(key, 1, Actionable.MODULATE);
        }
        assertTrue(fixture.allocator.cacheStats().pendingKeys() <= 4_096);
        var current = glass("still-present");
        fixture.add(current, 1);
        var taken = fixture.take(fuzzy);
        assertNotNull(taken);
        assertEquals(1, taken[0].get(current));
        fixture.allocator.allocate(fuzzy, fixture.inventory, null, 1);
        assertEquals(0, fixture.allocator.cacheStats().liveVariants());
    }

    @Test
    void variantLimitFailsClosedAndRecoversAfterInventoryShrinks() {
        var fuzzy = pattern(IRON, matching(GLASS, 1, 1, samePrimary(GLASS)));
        var exact = pattern(GOLD, matching(MARKED, 1, 1, MARKED::equals));
        var fixture = new Fixture(tasks(fuzzy, 1, exact, 1));
        fixture.add(MARKED, 1);
        var variants = new java.util.ArrayList<AEKey>();
        for (int i = 0; i < 4_200; i++) { var key = glass("crowded-" + i); variants.add(key); fixture.add(key, 1); }
        assertFalse(fixture.allocator.allocate(fuzzy, fixture.inventory, null, 1).allowed());
        assertTrue(fixture.allocator.cacheStats().liveVariants() <= 4_096);
        for (var key : variants) fixture.inventory.extract(key, 1, Actionable.MODULATE);
        var current = glass("after-shrink");
        fixture.add(current, 1);
        assertNotNull(fixture.take(fuzzy));
        assertEquals(1, fixture.inventory.list.get(MARKED));
    }

    private record OverloadedInput(AEKey output, IInput[] inputs) implements IPatternDetails, OverloadedPatternDetails {
        @Override public AEItemKey getDefinition() { return null; }
        @Override public IInput[] getInputs() { return inputs; }
        @Override public List<GenericStack> getOutputs() { return List.of(new GenericStack(output, 1)); }
        @Override public String overloadPatternIdentity() { return "execution-bucket-test"; }
        @Override public boolean hasFuzzyInputs() { return true; }
        @Override public boolean isFuzzyInput(int slot) { return true; }
        @Override public boolean isFuzzyOutput(int slot) { return false; }
    }

    @Test
    void retiringOneVariantKeepsARemainderStillReferencedByAnotherVariant() {
        var input = new IPatternDetails.IInput() {
            @Override public GenericStack[] getPossibleInputs() { return new GenericStack[] {new GenericStack(GLASS, 1)}; }
            @Override public long getMultiplier() { return 1; }
            @Override public boolean isValid(AEKey key, Level level) { return samePrimary(GLASS).test(key); }
            @Override public AEKey getRemainingKey(AEKey key) {
                return key.equals(GLASS) || key.equals(MARKED) ? null : IRON;
            }
        };
        var fuzzy = pattern(GOLD, input);
        var exact = pattern(GOLD, matching(MARKED, 1, 1, MARKED::equals));
        var fixture = new Fixture(tasks(fuzzy, 1, exact, 1));
        fixture.add(MARKED, 1);
        fixture.allocator.allocate(fuzzy, fixture.inventory, null, 1);
        int baseline = fixture.allocator.cacheStats().outputEntries();
        var first = glass("first-source");
        var second = glass("second-source");
        fixture.add(first, 1);
        fixture.add(second, 1);
        fixture.allocator.allocate(fuzzy, fixture.inventory, null, 1);
        assertEquals(baseline + 1, fixture.allocator.cacheStats().outputEntries());
        fixture.inventory.extract(first, 1, Actionable.MODULATE);
        fixture.allocator.allocate(fuzzy, fixture.inventory, null, 1);
        assertEquals(baseline + 1, fixture.allocator.cacheStats().outputEntries());
        fixture.inventory.extract(second, 1, Actionable.MODULATE);
        fixture.allocator.allocate(fuzzy, fixture.inventory, null, 1);
        assertEquals(baseline, fixture.allocator.cacheStats().outputEntries());
    }

    @Test
    void renewableQueriesDoNotRetainAnUnboundedNumberOfBatchSnapshots() {
        var input = new IPatternDetails.IInput() {
            @Override public GenericStack[] getPossibleInputs() { return new GenericStack[] {new GenericStack(GLASS, 1)}; }
            @Override public long getMultiplier() { return 1; }
            @Override public boolean isValid(AEKey key, Level level) { return samePrimary(GLASS).test(key); }
            @Override public AEKey getRemainingKey(AEKey key) { return GLASS; }
        };
        var fuzzy = pattern(IRON, input);
        var exact = pattern(GOLD, matching(MARKED, 1, 1, MARKED::equals));
        var fixture = new Fixture(tasks(fuzzy, 1_000, exact, 1));
        fixture.add(GLASS, 100);
        fixture.add(MARKED, 1);
        for (int copies = 1; copies <= 100; copies++) {
            assertTrue(fixture.allocator.allocate(fuzzy, fixture.inventory, null, copies).allowed());
            assertTrue(fixture.allocator.cacheStats().allocationSnapshots() <= 8);
        }
    }

    @Test
    void atCapacityArrivalBeforeDepartureStillDiscoversTheReplacement() {
        var fuzzy = pattern(IRON, matching(GLASS, 1, 1, samePrimary(GLASS)));
        var exact = pattern(GOLD, matching(MARKED, 1, 1, MARKED::equals));
        var fixture = new Fixture(tasks(fuzzy, 1, exact, 1));
        fixture.add(MARKED, 1);
        var variants = new java.util.ArrayList<AEKey>();
        for (int i = 0; i < 4_094; i++) { var key = glass("full-" + i); variants.add(key); fixture.add(key, 1); }
        assertTrue(fixture.allocator.allocate(fuzzy, fixture.inventory, null, 1).allowed());
        var first = glass("replacement-first");
        var second = glass("replacement-second");
        fixture.add(first, 1);
        fixture.add(second, 1);
        fixture.inventory.extract(variants.get(0), 1, Actionable.MODULATE);
        fixture.inventory.extract(variants.get(1), 1, Actionable.MODULATE);
        var allocation = fixture.allocator.allocate(fuzzy, fixture.inventory, null, 1);
        assertTrue(allocation.allowed());
        assertEquals(1, allocation.openChoices().available(0, first, 1));
        assertEquals(1, allocation.openChoices().available(0, second, 1));
        assertEquals(4_094, fixture.allocator.cacheStats().liveVariants());
    }

    @Test
    void aLateOnlyPredicateCannotDisableOtherExactReservations() {
        var broad = pattern(IRON, matching(MARKED, 1, 1, samePrimary(GLASS)));
        var late = pattern(GOLD, matching(GLASS, 1, 1, THIRD::equals));
        var exact = pattern(AEItemKey.of(Items.DIAMOND), matching(MARKED, 1, 1, MARKED::equals));
        var fixture = new Fixture(tasks(broad, 1, late, 1, exact, 1));
        fixture.add(GLASS, 1);
        fixture.add(MARKED, 1);
        assertFalse(fixture.allocator.allocate(broad, fixture.inventory, null, 1).allowed());
        fixture.add(THIRD, 1);
        var allocation = fixture.allocator.allocate(broad, fixture.inventory, null, 1);
        assertTrue(allocation.allowed());
        assertEquals(1, allocation.openChoices().available(0, GLASS, 1));
        assertEquals(0, allocation.openChoices().available(0, MARKED, 1));
        assertEquals(0, allocation.openChoices().available(0, THIRD, 1));
    }

    private static Predicate<AEKey> samePrimary(AEKey anchor) {
        return key -> key.dropSecondary().equals(anchor.dropSecondary());
    }

    private static AEItemKey glass(String name) {
        var stack = new ItemStack(Items.GLASS);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return AEItemKey.of(stack);
    }

    private static AEItemKey tool(int damage) {
        var stack = new ItemStack(Items.WOODEN_PICKAXE);
        stack.setDamageValue(damage);
        return AEItemKey.of(stack);
    }

    private static IPatternDetails.IInput matching(AEKey anchor, long amount, long multiplier, Predicate<AEKey> predicate) {
        return new IPatternDetails.IInput() {
            @Override public GenericStack[] getPossibleInputs() { return new GenericStack[] {new GenericStack(anchor, amount)}; }
            @Override public long getMultiplier() { return multiplier; }
            @Override public boolean isValid(AEKey key, Level level) { return predicate.test(key); }
            @Override public AEKey getRemainingKey(AEKey key) { return null; }
        };
    }

    private static IPatternDetails pattern(AEKey output, IPatternDetails.IInput... inputs) {
        return new Pattern(output, inputs);
    }

    private record Pattern(AEKey output, IInput[] inputs) implements IPatternDetails {
        @Override public AEItemKey getDefinition() { return null; }
        @Override public IInput[] getInputs() { return inputs; }
        @Override public List<GenericStack> getOutputs() { return List.of(new GenericStack(output, 1)); }
    }

    private record DeclaredSameIdInput(AEKey output, IInput[] inputs) implements IPatternDetails, FuzzyPatternInputs {
        @Override public AEItemKey getDefinition() { return null; }
        @Override public IInput[] getInputs() { return inputs; }
        @Override public List<GenericStack> getOutputs() { return List.of(new GenericStack(output, 1)); }
        @Override public boolean acceptsSameIdVariants(int slot) { return true; }
    }

    private record LateBoundOutput(AEKey output, IInput[] inputs) implements IPatternDetails, FuzzyPatternInputs {
        @Override public AEItemKey getDefinition() { return null; }
        @Override public IInput[] getInputs() { return inputs; }
        @Override public List<GenericStack> getOutputs() { return List.of(new GenericStack(output, 1)); }
        @Override public boolean acceptsSameIdVariants(int slot) { return false; }
        @Override public boolean producesSameIdVariants(int slot) { return true; }
    }

    private static Map<IPatternDetails, Long> tasks(Object... entries) {
        var result = new LinkedHashMap<IPatternDetails, Long>();
        for (int i = 0; i < entries.length; i += 2) result.put((IPatternDetails) entries[i], ((Number) entries[i + 1]).longValue());
        return result;
    }

    private static final class Fixture {
        final Map<IPatternDetails, Long> tasks;
        final ExecutionInputAllocator allocator;
        final ListCraftingInventory inventory;

        Fixture(Map<IPatternDetails, Long> tasks) {
            this.tasks = tasks;
            allocator = new ExecutionInputAllocator(tasks, value -> value, true, true);
            inventory = new ListCraftingInventory(allocator::onInventoryChange);
        }

        void add(AEKey key, long amount) { inventory.insert(key, amount, Actionable.MODULATE); }
        void accept(IPatternDetails pattern, long copies) {
            tasks.compute(pattern, (key, remaining) -> remaining - copies);
            allocator.onTaskChange(pattern);
        }
        KeyCounter[] take(IPatternDetails pattern) {
            return TimeWheelInputExtractor.extractPatternInputs(pattern, inventory, null, new KeyCounter(), new KeyCounter(),
                    allocator.allocate(pattern, inventory, null, 1));
        }
        TimeWheelInputExtractor.BulkResult bulk(IPatternDetails pattern, long copies) {
            return TimeWheelInputExtractor.bulkExtract(pattern, inventory, copies, false, Map.of(), null,
                    (visible, maximum) -> allocator.allocate(pattern, visible, null, maximum));
        }
    }
}
