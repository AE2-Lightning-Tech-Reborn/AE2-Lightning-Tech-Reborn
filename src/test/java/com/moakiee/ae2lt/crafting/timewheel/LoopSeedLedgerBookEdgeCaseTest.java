package com.moakiee.ae2lt.crafting.timewheel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.crafting.IPatternDetails;
import appeng.api.config.Actionable;
import appeng.crafting.inv.ICraftingInventory;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.crafting.execution.CraftingCpuHelper;
import com.moakiee.ae2lt.crafting.timewheel.allocation.ExecutionInputAllocator;
import com.moakiee.ae2lt.crafting.timewheel.allocation.TimeWheelInputExtractor;
import com.moakiee.thunderbolt.core.crafting.loop.ISeedPreservingCraftingTask;
import com.moakiee.ae2lt.crafting.runtime.ExecuteLoopPattern;
import com.moakiee.ae2lt.overload.runtime.pattern.OverloadPatternDetails;
import com.moakiee.ae2lt.overload.runtime.pattern.OverloadedProviderOnlyPatternDetails;
import com.moakiee.ae2lt.overload.runtime.pattern.PatternExecutionHostKind;
import com.moakiee.thunderbolt.core.crafting.loop.CraftingTaskPersistenceDefinition;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.moakiee.thunderbolt.core.crafting.planner.Sat;

class LoopSeedLedgerBookEdgeCaseTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void lastLoopMemberDoesNotBlockTheOrdinaryProducerItIsWaitingFor(boolean batch) throws Exception {
        var a = key("cycle_a", "");
        var b = key("cycle_b", "");
        var glass = key("ordinary_glass", "");
        var intermediate = key("ordinary_intermediate", "");
        var group = UUID.randomUUID();
        var firstId = UUID.randomUUID();
        var secondId = UUID.randomUUID();
        var first = new ExecuteLoopPattern(new FakeSeedPattern(
                new IPatternDetails.IInput[] {new FakeInput(new GenericStack[] {stack(a, 1)})},
                List.of(stack(b, 1)), group, false), firstId, counter(a, 1), counter(a, 1),
                Map.of(secondId, counter(b, 1)));
        var second = new ExecuteLoopPattern(new FakeSeedPattern(
                new IPatternDetails.IInput[] {new FakeInput(new GenericStack[] {stack(b, 1)}),
                        new FakeInput(new GenericStack[] {stack(intermediate, 1)})},
                List.of(stack(a, 1)), group, false), secondId, new KeyCounter(), counter(b, 1),
                Map.of(firstId, counter(a, 1)));
        var ordinary = new FakeSeedPattern(new IPatternDetails.IInput[] {
                new FakeInput(new GenericStack[] {stack(b, 1), stack(glass, 1)})},
                List.of(stack(intermediate, 1)), UUID.randomUUID(), false);
        var tasks = new java.util.LinkedHashMap<IPatternDetails, Long>();
        tasks.put(first, 1L); tasks.put(second, 1L); tasks.put(ordinary, 1L);
        var allocator = new ExecutionInputAllocator(tasks, value -> value, true, true,
                (pattern, slot) -> pattern instanceof ExecuteLoopPattern loop && loop.isInputSeedSlot(slot));
        var inventory = new ListCraftingInventory(allocator::onInventoryChange);
        inventory.insert(a, 1, Actionable.MODULATE);
        inventory.insert(glass, 1, Actionable.MODULATE);
        var ledgers = new LoopSeedLedgerBook();
        ledgers.initialize(List.of(first, second));

        var firstView = guarded(inventory, ledgers.reservationView(firstId, first::isInputSeedKey));
        var taken = TimeWheelInputExtractor.extractPatternInputs(first, firstView, null,
                new KeyCounter(), new KeyCounter(), allocator.allocate(first, firstView, null, 1));
        assertNotNull(taken);
        ledgers.recordDispatch(first, 1, false, null);
        tasks.put(first, 0L); allocator.onTaskChange(first);
        inventory.insert(b, 1, Actionable.MODULATE); // A's last in-flight output is now physical.

        var secondView = guarded(inventory, ledgers.reservationView(secondId, second::isInputSeedKey));
        assertNull(TimeWheelInputExtractor.extractPatternInputs(second, secondView, null,
                new KeyCounter(), new KeyCounter(), allocator.allocate(second, secondView, null, 1)),
                "the last loop member legitimately waits for the ordinary intermediate");
        var ordinaryView = guarded(inventory, ledgers.reservationView(null, ignored -> false));
        assertEquals(0, ordinaryView.extract(b, 1, Actionable.SIMULATE));
        assertEquals(1, ordinaryView.extract(glass, 1, Actionable.SIMULATE));
        var allocation = allocator.allocate(ordinary, ordinaryView, null, 1);
        assertTrue(allocation.allowed(), "do not demand the loop's already hidden B reserve again from public stock");
        var rejected = extract(ordinary, inventory, ledgers.reservationView(null, ignored -> false), allocator, batch);
        assertNotNull(rejected);
        assertEquals(1, rejected[0].get(glass));
        CraftingCpuHelper.reinjectPatternInputs(ordinaryView, rejected);
        var accepted = extract(ordinary, inventory, ledgers.reservationView(null, ignored -> false), allocator, batch);
        assertNotNull(accepted);
        assertEquals(1, inventory.list.get(b), "the ordinary recipe cannot steal the loop seed");
        tasks.put(ordinary, 0L); allocator.onTaskChange(ordinary);
        inventory.insert(intermediate, 1, Actionable.MODULATE);
        assertNotNull(TimeWheelInputExtractor.extractPatternInputs(second, secondView, null,
                new KeyCounter(), new KeyCounter(), allocator.allocate(second, secondView, null, 1)));
        ledgers.recordDispatch(second, 1, false, null);
        tasks.put(second, 0L); allocator.onTaskChange(second);
        inventory.insert(a, 1, Actionable.MODULATE);
        assertEquals(Map.of(a, 1L), ledgers.positiveSnapshot());
        assertEquals(1, inventory.list.get(a));
        assertEquals(0, inventory.list.get(b));
        assertTrue(tasks.values().stream().allMatch(count -> count == 0));
    }

    @Test
    void loopOrdinaryConsumableSlotsStillKeepTheirFiniteMaterialProtection() throws Exception {
        var seed = key("private_seed", "");
        var exactMaterial = key("loop_consumable", "");
        var substitute = key("ordinary_substitute", "");
        var loopId = UUID.randomUUID();
        var loop = new ExecuteLoopPattern(new FakeSeedPattern(new IPatternDetails.IInput[] {
                new FakeInput(new GenericStack[] {stack(seed, 1)}),
                new FakeInput(new GenericStack[] {stack(exactMaterial, 1)})},
                List.of(stack(key("returned_state", ""), 1)), UUID.randomUUID(), false),
                loopId, counter(seed, 1), counter(seed, 1), Map.of());
        var ordinary = new FakeSeedPattern(new IPatternDetails.IInput[] {
                new FakeInput(new GenericStack[] {stack(exactMaterial, 1), stack(substitute, 1)})},
                List.of(stack(key("ordinary_output", ""), 1)), UUID.randomUUID(), false);
        assertTrue(loop.isInputSeedSlot(0));
        assertFalse(loop.isInputSeedSlot(1));
        var tasks = new java.util.LinkedHashMap<IPatternDetails, Long>();
        tasks.put(loop, 1L); tasks.put(ordinary, 1L);
        var allocator = loopAllocator(tasks);
        var inventory = new ListCraftingInventory(allocator::onInventoryChange);
        inventory.insert(seed, 1, Actionable.MODULATE);
        inventory.insert(exactMaterial, 1, Actionable.MODULATE);
        inventory.insert(substitute, 1, Actionable.MODULATE);
        var ledgers = new LoopSeedLedgerBook();
        ledgers.initialize(List.of(loop));
        var taken = extract(ordinary, inventory, ledgers.reservationView(null, ignored -> false), allocator, true);
        assertNotNull(taken);
        assertEquals(1, taken[0].get(substitute));
        assertEquals(1, inventory.list.get(seed));
        assertEquals(1, inventory.list.get(exactMaterial), "only seed slots are exempt from lifetime reservation");
    }

    @Test
    void selectedFuzzyLoopUsesReadySeedOnlyAndWakesWhenTheNextActualSeedArrives() {
        var seed = key("late_seed", "");
        var strict = key("ordinary_exact", "");
        var returned = key("seed_return", "");
        var loopId = UUID.randomUUID();
        var loop = new ExecuteLoopPattern(new FakeSeedPattern(new IPatternDetails.IInput[] {
                new FakeInput(new GenericStack[] {stack(seed, 1), stack(strict, 1)})},
                List.of(stack(returned, 1)), UUID.randomUUID(), false),
                loopId, counter(seed, 2), counter(seed, 1), Map.of(loopId, counter(returned, 1)));
        var ordinary = new FakeSeedPattern(new IPatternDetails.IInput[] {
                new FakeInput(new GenericStack[] {stack(strict, 1)})},
                List.of(stack(key("exact_output", ""), 1)), UUID.randomUUID(), false);
        var tasks = new java.util.LinkedHashMap<IPatternDetails, Long>();
        tasks.put(loop, 2L); tasks.put(ordinary, 1L);
        var allocator = loopAllocator(tasks);
        var inventory = new ListCraftingInventory(allocator::onInventoryChange);
        inventory.insert(seed, 1, Actionable.MODULATE); // The second precredited seed is still in flight.
        inventory.insert(strict, 1, Actionable.MODULATE);
        var ledgers = new LoopSeedLedgerBook();
        ledgers.initialize(List.of(loop));
        var reserved = ledgers.reservationView(loopId, loop::isInputSeedKey);
        var first = TimeWheelInputExtractor.bulkExtract(loop, inventory, 2, false, reserved, null,
                (visible, copies) -> allocator.allocate(loop, visible, null, copies));
        assertNotNull(first, "a ready seed must not wait for all remaining copies' seeds");
        assertEquals(1, first.actualCopies);
        assertEquals(1, first.scaledInputs[0].get(seed));
        assertEquals(1, inventory.list.get(strict));
        tasks.put(loop, 1L); allocator.onTaskChange(loop);
        ledgers.recordDispatch(loop, 1, false, null);
        assertNull(TimeWheelInputExtractor.bulkExtract(loop, inventory, 1, false, reserved, null,
                (visible, copies) -> allocator.allocate(loop, visible, null, copies)),
                "a future credit is not physical stock, and the other recipe's exact material stays protected");
        inventory.insert(seed, 1, Actionable.MODULATE);
        var last = TimeWheelInputExtractor.bulkExtract(loop, inventory, 1, false, reserved, null,
                (visible, copies) -> allocator.allocate(loop, visible, null, copies));
        assertNotNull(last);
        assertEquals(1, last.scaledInputs[0].get(seed));
        assertEquals(1, inventory.list.get(strict));
    }

    private static ExecutionInputAllocator loopAllocator(Map<IPatternDetails, Long> tasks) {
        return new ExecutionInputAllocator(tasks, value -> value, true, true,
                (pattern, slot) -> pattern instanceof ExecuteLoopPattern loop && loop.isInputSeedSlot(slot));
    }

    private static KeyCounter[] extract(IPatternDetails pattern, ListCraftingInventory inventory,
            Map<AEKey, Long> reservations, ExecutionInputAllocator allocator, boolean batch) throws Exception {
        if (batch) {
            var result = TimeWheelInputExtractor.bulkExtract(pattern, inventory, 1, false, reservations, null,
                    (visible, copies) -> allocator.allocate(pattern, visible, null, copies));
            return result == null ? null : result.scaledInputs;
        }
        var visible = guarded(inventory, reservations);
        return TimeWheelInputExtractor.extractPatternInputs(pattern, visible, null,
                new KeyCounter(), new KeyCounter(), allocator.allocate(pattern, visible, null, 1));
    }

    private static ICraftingInventory guarded(ICraftingInventory inventory, Map<AEKey, Long> reservations)
            throws Exception {
        // Exercise the real CPU adapter, including its non-enumerable reservation view contract.
        var type = Class.forName(Ae2LtTimeWheelCraftingCpuLogic.class.getName() + "$ReservedCraftingInventory");
        var constructor = type.getDeclaredConstructor(ICraftingInventory.class, Map.class);
        constructor.setAccessible(true);
        return (ICraftingInventory) constructor.newInstance(inventory, reservations);
    }

    @Test
    void dispatchPrecreditsOutputButStillProtectsItFromAnotherConsumer() {
        var a = key("a", "");
        var b = key("b", "");
        var firstConsumer = UUID.randomUUID();
        var secondConsumer = UUID.randomUUID();
        var ledgers = new LoopSeedLedgerBook();
        ledgers.initializeAccounts(Map.of(
                firstConsumer, Map.of(a, 1L),
                secondConsumer, Map.of(a, 1L)));

        ledgers.recordDispatch(
                firstConsumer,
                counter(a, 1L),
                Map.of(firstConsumer, counter(b, 1L)),
                1L);

        assertEquals(0L, ledgers.balance(firstConsumer, a));
        assertEquals(1L, ledgers.balance(firstConsumer, b),
                "successful dispatch must credit the fixed consumer immediately");
        assertEquals(1L, ledgers.totalReserved(a));
        assertEquals(1L, ledgers.totalReserved(b));

        var firstTaskView = ledgers.reservationView(firstConsumer, b::equals);
        assertNull(firstTaskView.get(b),
                "a consumer may use its own precredited output once physical");
        var secondTaskView = ledgers.reservationView(secondConsumer, ignored -> false);
        assertEquals(1L, secondTaskView.get(b),
                "another consumer must not borrow that precredited output");
    }

    @Test
    void sameKeyInputAndOutputCollapseToOneStableSeedBalance() {
        var seed = key("stable_seed", "");
        var consumer = UUID.randomUUID();
        var ledgers = new LoopSeedLedgerBook();
        ledgers.initializeAccounts(Map.of(consumer, Map.of(seed, 1L)));

        ledgers.recordDispatch(
                consumer,
                counter(seed, 1L),
                Map.of(consumer, counter(seed, 1L)),
                Long.MAX_VALUE);

        assertEquals(1L, ledgers.balance(consumer, seed));
        assertEquals(Map.of(seed, 1L), ledgers.positiveSnapshot());
    }

    @Test
    void fuzzyOverloadReturnRekeysTheFinalRecoveryQuotaToTheActualVariant() {
        var expected = key("tool", "expected_damage");
        var actual = key("tool", "returned_damage");
        var consumer = UUID.randomUUID();
        var ledgers = new LoopSeedLedgerBook();
        ledgers.initializeAccounts(Map.of(consumer, Map.of(expected, 1L)));

        ledgers.recordDispatch(
                consumer,
                counter(expected, 1L),
                Map.of(consumer, counter(expected, 1L)),
                1L);
        ledgers.rekeyAvailable(consumer, expected, actual, 1L);

        assertEquals(0L, ledgers.balance(consumer, expected));
        assertEquals(1L, ledgers.balance(consumer, actual));
        assertEquals(Map.of(actual, 1L), ledgers.positiveSnapshot());
        assertFalse(ledgers.positiveSnapshot().containsKey(expected));
    }

    @Test
    void fixedForkCreditsCannotBeStolenByTheSiblingConsumer() {
        var a = key("fork_a", "");
        var b = key("fork_b", "");
        var producer = UUID.randomUUID();
        var bToC = UUID.randomUUID();
        var bToD = UUID.randomUUID();
        var ledgers = new LoopSeedLedgerBook();
        ledgers.initializeAccounts(Map.of(producer, Map.of(a, 1L)));

        ledgers.recordDispatch(
                producer,
                counter(a, 1L),
                Map.of(bToC, counter(b, 1L), bToD, counter(b, 1L)),
                1L);

        assertEquals(1L, ledgers.balance(bToC, b));
        assertEquals(1L, ledgers.balance(bToD, b));
        assertEquals(2L, ledgers.totalReserved(b));
        assertEquals(1L, ledgers.reservationView(bToC, b::equals).get(b));
        assertEquals(1L, ledgers.reservationView(bToD, b::equals).get(b));

        ledgers.recordDispatch(bToC, counter(b, 1L), Map.of(), 1L);

        assertEquals(0L, ledgers.balance(bToC, b));
        assertEquals(1L, ledgers.balance(bToD, b));
        assertEquals(1L, ledgers.totalReserved(b));
        assertEquals(1L, ledgers.reservationView(bToC, b::equals).get(b),
                "B-to-C must still leave B-to-D's share protected");
    }

    @Test
    void borrowedStockIsRepaidOnlyByCreditRoutedToThatConsumer() {
        var b = key("borrowed_b", "");
        var producer = UUID.randomUUID();
        var borrower = UUID.randomUUID();
        var sibling = UUID.randomUUID();
        var ledgers = new LoopSeedLedgerBook();
        ledgers.initializeAccounts(Map.of());

        ledgers.recordDispatch(borrower, counter(b, 1L), Map.of(), 1L);
        assertEquals(-1L, ledgers.balance(borrower, b));

        ledgers.recordDispatch(
                producer, new KeyCounter(), Map.of(sibling, counter(b, 1L)), 1L);
        assertEquals(-1L, ledgers.balance(borrower, b),
                "a sibling's fixed credit must not erase this consumer's debt");
        assertEquals(1L, ledgers.balance(sibling, b));

        ledgers.recordDispatch(
                producer, new KeyCounter(), Map.of(borrower, counter(b, 1L)), 1L);
        assertEquals(0L, ledgers.balance(borrower, b));
        assertEquals(1L, ledgers.balance(sibling, b));
        assertEquals(1L, ledgers.totalReserved(b));
    }

    @Test
    void dedicatedReserveAggregateDoesNotWrapAtExtremeAmounts() {
        var seed = key("huge_seed", "");
        var groups = List.of(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID());
        var requirements = new java.util.LinkedHashMap<UUID, Map<AEKey, Long>>();
        for (var consumer : groups) requirements.put(consumer, Map.of(seed, Sat.SAT));
        var ledgers = new LoopSeedLedgerBook();
        ledgers.initializeAccounts(requirements);

        assertEquals(Long.MAX_VALUE, ledgers.totalReserved(seed));
        ledgers.recordDispatch(
                groups.get(0), counter(seed, Sat.SAT), Map.of(), 1L);

        assertEquals(Sat.SAT * 4L, ledgers.totalReserved(seed));
        assertTrue(ledgers.totalReserved(seed) > 0L);
    }

    @Test
    void concreteHostVariantRekeysOnlyTheBorrowedInitialReserve() {
        var expected = key("tool", "planned");
        var actual = key("tool", "stored");
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var ledgers = new LoopSeedLedgerBook();
        ledgers.initializeAccounts(Map.of(
                first, Map.of(expected, 1L),
                second, Map.of(expected, 1L)));

        ledgers.rekeyAvailable(first, expected, actual, 1L);

        assertEquals(1L, ledgers.totalReserved(expected));
        assertEquals(1L, ledgers.totalReserved(actual));
        assertEquals(1L, ledgers.balance(first, actual));
        assertEquals(0L, ledgers.balance(first, expected));
        assertEquals(1L, ledgers.balance(second, expected));
        assertEquals(0L, ledgers.balance(second, actual));
    }

    @Test
    void crossPatternMultiLoopReturnsItsDedicatedLedgerToTheInitialState() {
        var a = key("cross_a", "");
        var b = key("cross_b", "");
        var c = key("cross_c", "");
        var d = key("cross_d", "");
        var aConsumer = UUID.randomUUID();
        var cConsumer = UUID.randomUUID();
        var bToAConsumer = UUID.randomUUID();
        var joinConsumer = UUID.randomUUID();
        var ledgers = new LoopSeedLedgerBook();
        ledgers.initializeAccounts(Map.of(
                aConsumer, Map.of(a, 1L),
                cConsumer, Map.of(c, 1L)));

        ledgers.recordDispatch(aConsumer, counter(a, 1L), Map.of(
                bToAConsumer, counter(b, 1L),
                joinConsumer, counter(b, 1L)), 1L);
        ledgers.recordDispatch(cConsumer, counter(c, 1L),
                Map.of(joinConsumer, counter(d, 1L)), 1L);
        ledgers.recordDispatch(bToAConsumer, counter(b, 1L),
                Map.of(aConsumer, counter(a, 1L)), 1L);
        ledgers.recordDispatch(
                joinConsumer,
                counter(Map.of(b, 1L, d, 1L)),
                Map.of(cConsumer, counter(c, 1L)),
                1L);

        assertEquals(Map.of(a, 1L, c, 1L), ledgers.positiveSnapshot());
        assertEquals(0L, ledgers.balance(bToAConsumer, b));
        assertEquals(0L, ledgers.balance(joinConsumer, b));
        assertEquals(0L, ledgers.balance(joinConsumer, d));
    }

    @Test
    void crossInputMultiLoopCanPrecreditBothDConsumersWithoutExtraSeed() {
        var a = key("cross_input_a", "");
        var b = key("cross_input_b", "");
        var c = key("cross_input_c", "");
        var d = key("cross_input_d", "");
        var cToDConsumer = UUID.randomUUID();
        var crossInputConsumer = UUID.randomUUID();
        var bToAConsumer = UUID.randomUUID();
        var joinConsumer = UUID.randomUUID();
        var ledgers = new LoopSeedLedgerBook();
        ledgers.initializeAccounts(Map.of(
                cToDConsumer, Map.of(c, 1L),
                crossInputConsumer, Map.of(a, 1L)));

        ledgers.recordDispatch(cToDConsumer, counter(c, 1L), Map.of(
                crossInputConsumer, counter(d, 1L),
                joinConsumer, counter(d, 1L)), 1L);
        ledgers.recordDispatch(
                crossInputConsumer,
                counter(Map.of(a, 1L, d, 1L)),
                Map.of(
                        bToAConsumer, counter(b, 1L),
                        joinConsumer, counter(b, 1L)),
                1L);
        ledgers.recordDispatch(bToAConsumer, counter(b, 1L),
                Map.of(crossInputConsumer, counter(a, 1L)), 1L);
        ledgers.recordDispatch(
                joinConsumer,
                counter(Map.of(b, 1L, d, 1L)),
                Map.of(cToDConsumer, counter(c, 1L)),
                1L);

        assertEquals(Map.of(a, 1L, c, 1L), ledgers.positiveSnapshot());
        assertEquals(0L, ledgers.balance(bToAConsumer, b));
        assertEquals(0L, ledgers.balance(joinConsumer, b));
        assertEquals(0L, ledgers.balance(crossInputConsumer, d));
        assertEquals(0L, ledgers.balance(joinConsumer, d));
    }

    @Test
    void fuzzyRemaindersUseResidualMatchingInsteadOfGreedyConsumerOrder() {
        var planned = key("matched_state", "planned");
        var y = key("matched_state", "y");
        var z = key("matched_state", "z");
        var leftInput = key("left_input", "");
        var rightInput = key("right_input", "");
        var group = UUID.randomUUID();
        var flexible = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var constrained = UUID.fromString("00000000-0000-0000-0000-000000000002");
        var producer = UUID.fromString("00000000-0000-0000-0000-000000000003");

        var flexiblePattern = loopPattern(
                flexible, group, new KeyCounter(), counter(planned, 1L),
                stack(planned, 1L), stack(y, 1L), stack(z, 1L));
        var constrainedPattern = loopPattern(
                constrained, group, new KeyCounter(), counter(planned, 1L),
                stack(planned, 1L), stack(y, 1L));
        var producerPattern = new ExecuteLoopPattern(
                new FakeSeedPattern(new IPatternDetails.IInput[] {
                        new FakeInput(new GenericStack[] {stack(leftInput, 1L)}),
                        new FakeInput(new GenericStack[] {stack(rightInput, 1L)})
                }, group),
                producer,
                new KeyCounter(),
                counter(Map.of(leftInput, 1L, rightInput, 1L)),
                Map.of(
                        flexible, counter(planned, 1L),
                        constrained, counter(planned, 1L)));
        var uses = List.of(
                new ExecuteLoopPattern.ActualSeedUse(
                        leftInput, leftInput, planned, y, 1L, 1L),
                new ExecuteLoopPattern.ActualSeedUse(
                        rightInput, rightInput, planned, z, 1L, 1L));

        var ledgers = new LoopSeedLedgerBook();
        ledgers.initializeAccounts(Map.of());
        ledgers.registerConsumers(List.of(flexiblePattern, constrainedPattern));

        assertTrue(ledgers.canRouteActualSeedUses(producerPattern, uses));
        ledgers.recordDispatch(producerPattern, 1L, false, uses);
        assertEquals(1L, ledgers.balance(flexible, z));
        assertEquals(1L, ledgers.balance(constrained, y));
        assertEquals(0L, ledgers.balance(flexible, y));
    }

    @Test
    void fuzzyRemainderSurplusBeyondTheSeedQuotaRemainsPublic() {
        var input = key("surplus_input", "");
        var planned = key("surplus_state", "planned");
        var actual = key("surplus_state", "actual");
        var group = UUID.randomUUID();
        var consumer = UUID.randomUUID();
        var producer = UUID.randomUUID();
        var consumerPattern = loopPattern(
                consumer, group, new KeyCounter(), counter(planned, 1L),
                stack(planned, 1L), stack(actual, 1L));
        var producerPattern = new ExecuteLoopPattern(
                new FakeSeedPattern(new IPatternDetails.IInput[] {
                        new FakeInput(new GenericStack[] {stack(input, 1L)})
                }, group),
                producer,
                new KeyCounter(),
                counter(input, 1L),
                Map.of(consumer, counter(planned, 1L)));
        var uses = List.of(new ExecuteLoopPattern.ActualSeedUse(
                input, input, planned, actual, 1L, 2L));

        var ledgers = new LoopSeedLedgerBook();
        ledgers.initializeAccounts(Map.of());
        ledgers.registerConsumers(List.of(consumerPattern));

        assertTrue(ledgers.canRouteActualSeedUses(producerPattern, uses));
        ledgers.recordDispatch(producerPattern, 1L, false, uses);
        assertEquals(1L, ledgers.balance(consumer, actual),
                "only the seed-owned prefix is protected; the second remainder stays public");
    }

    @Test
    void strictOutputSatisfiesSeedQuotaBeforeChangedRemainder() {
        var input = key("strict_cover_input", "");
        var planned = key("strict_cover_state", "planned");
        var actual = key("strict_cover_state", "actual");
        var group = UUID.randomUUID();
        var consumer = UUID.randomUUID();
        var producer = UUID.randomUUID();
        var consumerPattern = loopPattern(
                consumer, group, new KeyCounter(), counter(planned, 1L),
                stack(planned, 1L));
        var producerPattern = new ExecuteLoopPattern(
                new FakeSeedPattern(
                        new IPatternDetails.IInput[] {
                                new FakeInput(new GenericStack[] {stack(input, 1L)})
                        },
                        List.of(stack(planned, 1L)), group, false),
                producer,
                new KeyCounter(),
                counter(input, 1L),
                Map.of(consumer, counter(planned, 1L)));
        var uses = List.of(new ExecuteLoopPattern.ActualSeedUse(
                input, input, planned, actual, 1L, 1L));

        var ledgers = new LoopSeedLedgerBook();
        ledgers.initializeAccounts(Map.of());
        ledgers.registerConsumers(List.of(consumerPattern));

        assertTrue(ledgers.canRouteActualSeedUses(producerPattern, uses));
        ledgers.recordDispatch(producerPattern, 1L, false, uses);
        assertEquals(1L, ledgers.balance(consumer, planned));
        assertEquals(0L, ledgers.balance(consumer, actual),
                "the incompatible changed remainder stays public when strict P covers the quota");
    }

    @Test
    void unchangedRemainderReportsItsP2CreditBeforeAnIdOnlyOutputIsRegistered() {
        var input = key("unchanged_cover_input", "");
        var planned = key("unchanged_cover_state", "planned");
        var group = UUID.randomUUID();
        var consumer = UUID.randomUUID();
        var consumerPattern = loopPattern(
                consumer, group, new KeyCounter(), counter(planned, 1L),
                stack(planned, 1L));
        var producerPattern = new ExecuteLoopPattern(
                new FakeSeedPattern(
                        new IPatternDetails.IInput[] {
                                new FakeInput(new GenericStack[] {stack(input, 1L)})
                        },
                        List.of(stack(planned, 1L)), group, false, Set.of(0)),
                UUID.randomUUID(), new KeyCounter(), counter(input, 1L),
                Map.of(consumer, counter(planned, 1L)));
        var uses = List.of(new ExecuteLoopPattern.ActualSeedUse(
                input, input, planned, planned, 1L, 1L));

        var ledgers = new LoopSeedLedgerBook();
        ledgers.initializeAccounts(Map.of());
        ledgers.registerConsumers(List.of(consumerPattern));

        var preallocated = ledgers.recordDispatch(producerPattern, 1L, false, uses);

        assertEquals(1L, preallocated.get(consumer).get(planned),
                "the unchanged remainder must consume the P2 quota before ID_ONLY metadata");
        assertEquals(1L, ledgers.balance(consumer, planned));
    }

    @Test
    void idOnlyOutputDoesNotPretendToBeExactSeedCapacity() {
        var input = key("fuzzy_output_input", "");
        var planned = key("fuzzy_output_state", "planned");
        var actual = key("fuzzy_output_state", "actual");
        var group = UUID.randomUUID();
        var consumer = UUID.randomUUID();
        var producerPattern = new ExecuteLoopPattern(
                new FakeSeedPattern(
                        new IPatternDetails.IInput[] {
                                new FakeInput(new GenericStack[] {stack(input, 1L)})
                        },
                        List.of(stack(planned, 1L)), group, false, Set.of(0)),
                UUID.randomUUID(), new KeyCounter(), counter(input, 1L),
                Map.of(consumer, counter(planned, 1L)));
        var consumerPattern = loopPattern(
                consumer, group, new KeyCounter(), counter(planned, 1L),
                stack(planned, 1L));
        var uses = List.of(new ExecuteLoopPattern.ActualSeedUse(
                input, input, planned, actual, 1L, 1L));
        var ledgers = new LoopSeedLedgerBook();
        ledgers.initializeAccounts(Map.of());
        ledgers.registerConsumers(List.of(consumerPattern));

        assertFalse(ledgers.canRouteActualSeedUses(producerPattern, uses),
                "an ID_ONLY output is not guaranteed to return the planned component state");
    }

    @Test
    void unchangedRemainderSatisfiesQuotaBeforeChangedRemainder() {
        var firstInput = key("unchanged_input", "");
        var secondInput = key("changed_input", "");
        var planned = key("mixed_remainder_state", "planned");
        var actual = key("mixed_remainder_state", "actual");
        var group = UUID.randomUUID();
        var consumer = UUID.randomUUID();
        var producerPattern = new ExecuteLoopPattern(
                new FakeSeedPattern(new IPatternDetails.IInput[] {
                        new FakeInput(new GenericStack[] {stack(firstInput, 1L)}),
                        new FakeInput(new GenericStack[] {stack(secondInput, 1L)})
                }, group),
                UUID.randomUUID(), new KeyCounter(),
                counter(Map.of(firstInput, 1L, secondInput, 1L)),
                Map.of(consumer, counter(planned, 1L)));
        var consumerPattern = loopPattern(
                consumer, group, new KeyCounter(), counter(planned, 1L),
                stack(planned, 1L));
        var uses = List.of(
                new ExecuteLoopPattern.ActualSeedUse(
                        firstInput, firstInput, planned, planned, 1L, 1L),
                new ExecuteLoopPattern.ActualSeedUse(
                        secondInput, secondInput, planned, actual, 1L, 1L));
        var ledgers = new LoopSeedLedgerBook();
        ledgers.initializeAccounts(Map.of());
        ledgers.registerConsumers(List.of(consumerPattern));

        assertTrue(ledgers.canRouteActualSeedUses(producerPattern, uses));
        ledgers.recordDispatch(producerPattern, 1L, false, uses);
        assertEquals(1L, ledgers.balance(consumer, planned));
        assertEquals(0L, ledgers.balance(consumer, actual));
    }

    @Test
    void partialChangedBundleCannotBecomeAnUnexecutableP2SeedState() {
        var input = key("partial_bundle_input", "");
        var planned = key("partial_bundle_state", "planned");
        var actual = key("partial_bundle_state", "actual");
        var group = UUID.randomUUID();
        var consumer = UUID.randomUUID();
        var consumerPattern = loopPattern(
                consumer, group, new KeyCounter(), counter(planned, 2L),
                stack(planned, 2L), stack(actual, 2L));
        var producerPattern = new ExecuteLoopPattern(
                new FakeSeedPattern(
                        new IPatternDetails.IInput[] {
                                new FakeInput(new GenericStack[] {stack(input, 2L)})
                        },
                        List.of(stack(planned, 1L)), group, false),
                UUID.randomUUID(), new KeyCounter(), counter(input, 2L),
                Map.of(consumer, counter(planned, 2L)));
        var uses = List.of(new ExecuteLoopPattern.ActualSeedUse(
                input, input, planned, actual, 2L, 1L));
        var ledgers = new LoopSeedLedgerBook();
        ledgers.initializeAccounts(Map.of());
        ledgers.registerConsumers(List.of(consumerPattern));

        assertFalse(ledgers.canRouteActualSeedUses(producerPattern, uses),
                "X + A cannot execute a slot that requires either 2X or 2A");
    }

    @Test
    void completeChangedBundleCanReplaceTheWholeP2SeedState() {
        var input = key("complete_bundle_input", "");
        var planned = key("complete_bundle_state", "planned");
        var actual = key("complete_bundle_state", "actual");
        var group = UUID.randomUUID();
        var consumer = UUID.randomUUID();
        var consumerPattern = loopPattern(
                consumer, group, new KeyCounter(), counter(planned, 2L),
                stack(planned, 2L), stack(actual, 2L));
        var producerPattern = new ExecuteLoopPattern(
                new FakeSeedPattern(new IPatternDetails.IInput[] {
                        new FakeInput(new GenericStack[] {stack(input, 2L)})
                }, group),
                UUID.randomUUID(), new KeyCounter(), counter(input, 2L),
                Map.of(consumer, counter(planned, 2L)));
        var uses = List.of(new ExecuteLoopPattern.ActualSeedUse(
                input, input, planned, actual, 2L, 2L));
        var ledgers = new LoopSeedLedgerBook();
        ledgers.initializeAccounts(Map.of());
        ledgers.registerConsumers(List.of(consumerPattern));

        assertTrue(ledgers.canRouteActualSeedUses(producerPattern, uses));
        ledgers.recordDispatch(producerPattern, 1L, false, uses);
        assertEquals(2L, ledgers.balance(consumer, actual));
        assertEquals(0L, ledgers.balance(consumer, planned));
    }

    @Test
    void oneProducerCannotContributeHalfOfAChangedP2Bundle() {
        var input = key("split_bundle_input", "");
        var planned = key("split_bundle_state", "planned");
        var actual = key("split_bundle_state", "actual");
        var group = UUID.randomUUID();
        var consumer = UUID.randomUUID();
        var consumerPattern = loopPattern(
                consumer, group, new KeyCounter(), counter(planned, 2L),
                stack(planned, 2L), stack(actual, 2L));
        var producerPattern = new ExecuteLoopPattern(
                new FakeSeedPattern(new IPatternDetails.IInput[] {
                        new FakeInput(new GenericStack[] {stack(input, 1L)})
                }, group),
                UUID.randomUUID(), new KeyCounter(), counter(input, 1L),
                Map.of(consumer, counter(planned, 1L)));
        var uses = List.of(new ExecuteLoopPattern.ActualSeedUse(
                input, input, planned, actual, 1L, 1L));
        var ledgers = new LoopSeedLedgerBook();
        ledgers.initializeAccounts(Map.of());
        ledgers.registerConsumers(List.of(consumerPattern));

        assertFalse(ledgers.canRouteActualSeedUses(producerPattern, uses),
                "two independent X1 credits must not combine into an implicit 2X bundle");
    }

    @Test
    void hostVariantMustAlsoArriveInCompleteP2Bundles() {
        var planned = key("host_bundle_state", "planned");
        var actual = key("host_bundle_state", "actual");
        var group = UUID.randomUUID();
        var consumer = UUID.randomUUID();
        var pattern = loopPattern(
                consumer, group, counter(planned, 2L), counter(planned, 2L),
                false, stack(planned, 2L), stack(actual, 2L));
        var ledgers = new LoopSeedLedgerBook();
        ledgers.initialize(List.of(pattern));

        var accepted = ledgers.assignHostVariantsForGroup(
                group, false, planned, counter(Map.of(planned, 1L, actual, 1L)));

        assertEquals(1L, accepted.get(planned));
        assertEquals(0L, accepted.get(actual));
        assertEquals(2L, ledgers.balance(consumer, planned));
        assertEquals(0L, ledgers.balance(consumer, actual));
    }

    @Test
    void hostVariantsAreMatchedToConstrainedBootstrapConsumersAsOneBatch() {
        var planned = key("host_state", "planned");
        var y = key("host_state", "y");
        var z = key("host_state", "z");
        var group = UUID.randomUUID();
        var flexible = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var constrained = UUID.fromString("00000000-0000-0000-0000-000000000002");
        var flexiblePattern = loopPattern(
                flexible, group, counter(planned, 1L), counter(planned, 1L),
                false, stack(planned, 1L), stack(y, 1L), stack(z, 1L));
        var constrainedPattern = loopPattern(
                constrained, group, counter(planned, 1L), counter(planned, 1L),
                false, stack(planned, 1L), stack(y, 1L));
        var offered = counter(Map.of(y, 1L, z, 1L));

        var ledgers = new LoopSeedLedgerBook();
        ledgers.initialize(List.of(flexiblePattern, constrainedPattern));
        var accepted = ledgers.assignHostVariantsForGroup(group, false, planned, offered);

        assertEquals(1L, accepted.get(y));
        assertEquals(1L, accepted.get(z));
        assertEquals(1L, ledgers.balance(flexible, z));
        assertEquals(1L, ledgers.balance(constrained, y));
        assertEquals(0L, ledgers.balance(
                ExecuteLoopPattern.SHARED_SEED_ACCOUNT_ID, planned));
    }

    @Test
    void sharedFuzzyHostVariantBindsToAConsumerInItsAllocatedGroup() {
        var planned = key("shared_host_state", "planned");
        var actual = key("shared_host_state", "actual");
        var group = UUID.randomUUID();
        var consumer = UUID.randomUUID();
        var pattern = loopPattern(
                consumer, group, counter(planned, 1L), counter(planned, 1L),
                stack(planned, 1L), stack(actual, 1L));
        var ledgers = new LoopSeedLedgerBook();
        ledgers.initialize(List.of(pattern));

        var accepted = ledgers.assignHostVariantsForGroup(
                group, true, planned, counter(actual, 1L));

        assertEquals(1L, accepted.get(actual));
        assertEquals(0L, ledgers.balance(
                ExecuteLoopPattern.SHARED_SEED_ACCOUNT_ID, planned));
        assertEquals(1L, ledgers.balance(consumer, actual));
    }

    @Test
    void sharedFuzzyHostVariantCannotBindToACompatibleConsumerInAnotherGroup() {
        var planned = key("group_scoped_host_state", "planned");
        var actual = key("group_scoped_host_state", "actual");
        var firstGroup = UUID.randomUUID();
        var secondGroup = UUID.randomUUID();
        var firstConsumer = UUID.randomUUID();
        var secondConsumer = UUID.randomUUID();
        var firstPattern = loopPattern(
                firstConsumer, firstGroup, counter(planned, 1L), counter(planned, 1L),
                stack(planned, 1L));
        var secondPattern = loopPattern(
                secondConsumer, secondGroup, counter(planned, 1L), counter(planned, 1L),
                stack(planned, 1L), stack(actual, 1L));
        var ledgers = new LoopSeedLedgerBook();
        ledgers.initialize(List.of(firstPattern, secondPattern));

        var accepted = ledgers.assignHostVariantsForGroup(
                firstGroup, true, planned, counter(actual, 1L));

        assertTrue(accepted.isEmpty());
        assertEquals(1L, ledgers.balance(
                ExecuteLoopPattern.SHARED_SEED_ACCOUNT_ID, planned));
        assertEquals(0L, ledgers.balance(firstConsumer, actual));
        assertEquals(0L, ledgers.balance(secondConsumer, actual));
    }

    @Test
    void onePhysicalSharedFuzzyVariantIsAssignedToOnlyOneConsumer() {
        var planned = key("single_physical_host_state", "planned");
        var actual = key("single_physical_host_state", "actual");
        var group = UUID.randomUUID();
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var firstPattern = loopPattern(
                first, group, counter(planned, 1L), counter(planned, 1L),
                stack(planned, 1L), stack(actual, 1L));
        var secondPattern = loopPattern(
                second, group, counter(planned, 1L), counter(planned, 1L),
                stack(planned, 1L), stack(actual, 1L));
        var ledgers = new LoopSeedLedgerBook();
        ledgers.initialize(List.of(firstPattern, secondPattern));

        var accepted = ledgers.assignHostVariantsForGroup(
                group, true, planned, counter(actual, 1L));

        assertEquals(1L, accepted.get(actual));
        assertEquals(1L, ledgers.balance(first, actual) + ledgers.balance(second, actual));
        assertEquals(1L, ledgers.balance(
                ExecuteLoopPattern.SHARED_SEED_ACCOUNT_ID, planned));
    }

    @Test
    void exactHostVariantRemainsInTheGlobalSharedAccount() {
        var planned = key("exact_shared_host_state", "planned");
        var firstGroup = UUID.randomUUID();
        var secondGroup = UUID.randomUUID();
        var firstConsumer = UUID.randomUUID();
        var secondConsumer = UUID.randomUUID();
        var firstPattern = loopPattern(
                firstConsumer, firstGroup, counter(planned, 1L), counter(planned, 1L),
                stack(planned, 1L));
        var secondPattern = loopPattern(
                secondConsumer, secondGroup, counter(planned, 1L), counter(planned, 1L),
                stack(planned, 1L));
        var ledgers = new LoopSeedLedgerBook();
        ledgers.initialize(List.of(firstPattern, secondPattern));

        var accepted = ledgers.assignHostVariantsForGroup(
                firstGroup, true, planned, counter(planned, 1L));

        assertEquals(1L, accepted.get(planned));
        assertEquals(1L, ledgers.balance(
                ExecuteLoopPattern.SHARED_SEED_ACCOUNT_ID, planned));
        assertEquals(0L, ledgers.balance(firstConsumer, planned));
        assertEquals(0L, ledgers.balance(secondConsumer, planned));
    }

    @Test
    void concreteOwnedVariantIsDebitedBeforeAnUnreturnedPlannedCredit() {
        var planned = key("debit_order", "planned");
        var actual = key("debit_order", "actual");
        var consumer = UUID.randomUUID();
        var pattern = loopPattern(
                consumer, UUID.randomUUID(), new KeyCounter(), counter(planned, 1L),
                stack(planned, 1L), stack(actual, 1L));
        var ledgers = new LoopSeedLedgerBook();
        ledgers.initializeAccounts(Map.of(
                consumer, Map.of(planned, 1L, actual, 1L)));
        ledgers.registerConsumers(List.of(pattern));

        ledgers.recordDispatch(pattern, 1L, false, List.of(
                new ExecuteLoopPattern.ActualSeedUse(
                        planned, actual, null, null, 1L, 0L)));

        assertEquals(1L, ledgers.balance(consumer, planned),
                "the pending planned credit must stay attached to its future physical return");
        assertEquals(0L, ledgers.balance(consumer, actual));
    }

    @Test
    void borrowedConcreteVariantDoesNotConsumeAnUnreturnedPlannedCredit() {
        var planned = key("borrow_order", "planned");
        var actual = key("borrow_order", "actual");
        var consumer = UUID.randomUUID();
        var pattern = loopPattern(
                consumer, UUID.randomUUID(), new KeyCounter(), counter(planned, 1L),
                stack(planned, 1L), stack(actual, 1L));
        var ledgers = new LoopSeedLedgerBook();
        ledgers.initializeAccounts(Map.of(consumer, Map.of(planned, 1L)));
        ledgers.registerConsumers(List.of(pattern));

        ledgers.recordDispatch(pattern, 1L, false, List.of(
                new ExecuteLoopPattern.ActualSeedUse(
                        planned, actual, null, null, 1L, 0L)));

        assertEquals(1L, ledgers.balance(consumer, planned));
        assertEquals(-1L, ledgers.balance(consumer, actual),
                "taking public X must be recorded as X debt, not paid with pending P");
        assertEquals(Map.of(planned, 1L), ledgers.positiveSnapshot());
    }

    private static KeyCounter counter(AEKey key, long amount) {
        var result = new KeyCounter();
        result.add(key, amount);
        return result;
    }

    private static KeyCounter counter(Map<AEKey, Long> amounts) {
        var result = new KeyCounter();
        for (var entry : amounts.entrySet()) {
            result.add(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private static GenericStack stack(AEKey key, long amount) {
        return new GenericStack(key, amount);
    }

    private static ExecuteLoopPattern loopPattern(
            UUID consumer,
            UUID group,
            KeyCounter initial,
            KeyCounter input,
            GenericStack... possible) {
        return loopPattern(consumer, group, initial, input, true, possible);
    }

    private static ExecuteLoopPattern loopPattern(
            UUID consumer,
            UUID group,
            KeyCounter initial,
            KeyCounter input,
            boolean singleSeedInputPerMember,
            GenericStack... possible) {
        return new ExecuteLoopPattern(
                new FakeSeedPattern(new IPatternDetails.IInput[] {
                        new FakeInput(possible)
                }, group, singleSeedInputPerMember),
                consumer,
                initial,
                input,
                Map.of());
    }

    private record FakeInput(GenericStack[] possible) implements IPatternDetails.IInput {
        private FakeInput {
            possible = possible.clone();
        }
        @Override public GenericStack[] getPossibleInputs() { return possible.clone(); }
        @Override public long getMultiplier() { return 1L; }
        @Override public boolean isValid(AEKey input, Level level) {
            return java.util.Arrays.stream(possible)
                    .anyMatch(candidate -> candidate.what().equals(input));
        }
        @Override public AEKey getRemainingKey(AEKey template) { return null; }
    }

    private record FakeSeedPattern(
            IPatternDetails.IInput[] inputs,
            List<GenericStack> outputs,
            UUID group,
            boolean singleSeedInputPerMember,
            Set<Integer> fuzzyOutputSlots)
            implements IPatternDetails, ISeedPreservingCraftingTask,
            CraftingTaskPersistenceDefinition, OverloadedProviderOnlyPatternDetails {
        private FakeSeedPattern(IPatternDetails.IInput[] inputs, UUID group) {
            this(inputs, List.of(), group, true, Set.of());
        }
        private FakeSeedPattern(
                IPatternDetails.IInput[] inputs,
                UUID group,
                boolean singleSeedInputPerMember) {
            this(inputs, List.of(), group, singleSeedInputPerMember, Set.of());
        }
        private FakeSeedPattern(
                IPatternDetails.IInput[] inputs,
                List<GenericStack> outputs,
                UUID group,
                boolean singleSeedInputPerMember) {
            this(inputs, outputs, group, singleSeedInputPerMember, Set.of());
        }
        private FakeSeedPattern {
            inputs = inputs.clone();
            outputs = List.copyOf(outputs);
            fuzzyOutputSlots = Set.copyOf(fuzzyOutputSlots);
        }
        @Override public AEItemKey getDefinition() { return null; }
        @Override public IPatternDetails.IInput[] getInputs() { return inputs.clone(); }
        @Override public GenericStack[] getOutputs() { return outputs.toArray(GenericStack[]::new); }
        @Override public UUID reusableSeedGroupId() { return group; }
        @Override public java.util.Set<AEKey> reusableSeedCycleKeys() { return java.util.Set.of(); }
        @Override public boolean hasSingleSeedInputPerMember() {
            return singleSeedInputPerMember;
        }
        @Override public AEItemKey craftingTaskPersistenceDefinition() { return null; }
        @Override public PatternExecutionHostKind requiredHostKind() {
            return PatternExecutionHostKind.OVERLOADED_PATTERN_PROVIDER;
        }
        @Override public String overloadPatternIdentity() { return "test:ledger-pattern"; }
        @Override public OverloadPatternDetails overloadPatternDetailsView() { return null; }
        @Override public boolean isFuzzyOutput(int slot) {
            return fuzzyOutputSlots.contains(slot);
        }
    }

    private static TestKey key(String id, String secondary) {
        return new TestKey(id, secondary);
    }

    private static final class TestKey extends AEKey {
        private static final TestKeyType TYPE = new TestKeyType();
        private final String id;
        private final String secondary;

        private TestKey(String id, String secondary) {
            this.id = id;
            this.secondary = secondary;
        }

        @Override public AEKeyType getType() { return TYPE; }
        @Override public AEKey dropSecondary() {
            return secondary.isEmpty() ? this : new TestKey(id, "");
        }
        @Override public CompoundTag toTag() {
            var tag = new CompoundTag();
            tag.putString("id", id);
            tag.putString("secondary", secondary);
            return tag;
        }
        @Override public Object getPrimaryKey() { return id; }
        @Override public ResourceLocation getId() {
            return new ResourceLocation("ae2lt_test", id);
        }
        @Override public void writeToPacket(FriendlyByteBuf data) { }
        @Override protected Component computeDisplayName() {
            return Component.literal(id + secondary);
        }
        @Override public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) { }
        @Override public boolean equals(Object obj) {
            return obj instanceof TestKey other
                    && id.equals(other.id)
                    && secondary.equals(other.secondary);
        }
        @Override public int hashCode() { return java.util.Objects.hash(id, secondary); }
    }

    private static final class TestKeyType extends AEKeyType {
        private TestKeyType() {
            super(new ResourceLocation("ae2lt_test", "ledger_key"),
                    TestKey.class, Component.literal("ledger key"));
        }
        @Override public AEKey loadKeyFromTag(CompoundTag tag) { return null; }
        @Override public AEKey readFromPacket(FriendlyByteBuf input) { return null; }
    }
}
