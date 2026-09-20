package com.moakiee.ae2lt.crafting.timewheel;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ICraftingInventory;
import appeng.crafting.inv.ListCraftingInventory;
import com.moakiee.ae2lt.crafting.runtime.ExecuteLoopPattern;
import com.moakiee.ae2lt.overload.runtime.pattern.OverloadPatternDetails;
import com.moakiee.ae2lt.overload.runtime.pattern.OverloadedProviderOnlyPatternDetails;
import com.moakiee.ae2lt.overload.runtime.pattern.PatternExecutionHostKind;
import com.moakiee.ae2lt.overload.runtime.pattern.SourcePatternSnapshot;
import com.moakiee.ae2lt.overload.runtime.cpu.OverloadCpuOwner;
import com.moakiee.ae2lt.overload.runtime.cpu.OverloadCpuState;
import com.moakiee.ae2lt.overload.runtime.cpu.OverloadPatternReference;
import com.moakiee.ae2lt.overload.runtime.cpu.OverloadReusableSeedMetadata;
import com.moakiee.ae2lt.crafting.timewheel.allocation.ExecutionInputAllocator;
import com.moakiee.ae2lt.crafting.timewheel.allocation.TimeWheelInputExtractor;
import com.moakiee.ae2lt.crafting.timewheel.allocation.TimeWheelBatchInputAllocation;
import com.moakiee.thunderbolt.core.crafting.loop.CraftingTaskPersistenceDefinition;
import com.moakiee.thunderbolt.core.crafting.loop.ISeedPreservingCraftingTask;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

/** A bounded, fair event driver around the real extraction, allocation and seed-accounting code.
 * Machines are represented by delayed unit outputs; this is not a Minecraft world/CPU scheduler test. */
class LoopSeedAllocationLivenessTest {
    private enum Scenario { PUBLIC_INTERMEDIATE, SUBSTITUTE, TWO_LOOPS, SHARED_SEED, LATE_FUZZY_VARIANTS }

    static Stream<Arguments> schedules() {
        return Arrays.stream(Scenario.values()).flatMap(scenario -> Stream.of(false, true)
                .flatMap(batch -> IntStream.range(0, 18)
                        .mapToObj(seed -> Arguments.of(scenario, batch, seed))));
    }

    @ParameterizedTest(name = "{0}, batch={1}, schedule={2}")
    @MethodSource("schedules")
    void ordinaryCompetitionDoesNotStrandTheLoop(Scenario scenario, boolean batch, int seed)
            throws Exception {
        new Run(scenario, batch, seed).complete();
    }

    @Test
    void disablingTheSeparateSeedReservationFixReproducesARealWaitCycle() throws Exception {
        var run = new Run(Scenario.SUBSTITUTE, false, 0, false);
        var failure = assertThrows(AssertionError.class, run::complete);
        assertTrue(failure.getMessage().startsWith("Deadlock:"), failure::getMessage);
        assertEquals(0L, run.tasks.entrySet().stream()
                .filter(e -> run.names.get(e.getKey()).equals("a->B")).findFirst().orElseThrow().getValue());
    }

    private static final class Run {
        private final Scenario scenario;
        private final boolean batch;
        private final Random random;
        private final Map<IPatternDetails, Long> tasks = new LinkedHashMap<>();
        private final Map<IPatternDetails, String> names = new LinkedHashMap<>();
        private final Map<IPatternDetails, Integer> attempts = new LinkedHashMap<>();
        private final Map<AEKey, Long> finalSeeds = new LinkedHashMap<>();
        private final List<OutputEvent> events = new ArrayList<>();
        private final List<String> trace = new ArrayList<>();
        private final LoopSeedLedgerBook ledgers = new LoopSeedLedgerBook();
        private final OverloadCpuState overload = new OverloadCpuState(OverloadCpuOwner.from(id(90), this));
        private final ExecutionInputAllocator allocator;
        private final ListCraftingInventory inventory;
        private final Constructor<?> guardConstructor;
        private final Method registerOutput;
        private final TestKey b = key("intermediate", "planned");
        private final TestKey strict = key("intermediate", "exact-only");
        private final TestKey glass = key("glass", "");
        private final TestKey x = key("ordinary_x", "");
        private final TestKey trigger = key("ordinary_trigger", "");
        private final TestKey finished = key("ordinary_finished", "");
        private final int copies = 12;
        private final int width;
        private int tick;
        private int serial;
        private int rejected;
        private int partial;
        private int maxOutstanding;
        private int maxActivePatterns;
        private long consumedPublicB;
        private long consumedGlass;

        Run(Scenario scenario, boolean batch, int seed) throws Exception {
            this(scenario, batch, seed, true);
        }

        Run(Scenario scenario, boolean batch, int seed, boolean separatelyReserved) throws Exception {
            this.scenario = scenario;
            this.batch = batch;
            allocator = new ExecutionInputAllocator(tasks, n -> n, true, true,
                    (pattern, slot) -> separatelyReserved
                            && pattern instanceof ExecuteLoopPattern loop && loop.isInputSeedSlot(slot));
            inventory = new ListCraftingInventory(allocator::onInventoryChange);
            random = new Random(0x51eedL + seed);
            width = 1 + seed / 6;
            var type = Class.forName(Ae2LtTimeWheelCraftingCpuLogic.class.getName() + "$ReservedCraftingInventory");
            guardConstructor = type.getDeclaredConstructor(ICraftingInventory.class, Map.class);
            guardConstructor.setAccessible(true);
            // Slot metadata has already been resolved by this fixture. Exercise the actual
            // pending-output queue rather than assigning credits to simulated physical units.
            registerOutput = OverloadCpuState.class.getDeclaredMethod("registerExpectedOutput",
                    OverloadPatternReference.class, int.class, ResourceLocation.class, AEKey.class,
                    long.class, boolean.class, OverloadReusableSeedMetadata.class);
            registerOutput.setAccessible(true);
            addLoop("a");
            if (scenario == Scenario.TWO_LOOPS) addLoop("c");
            boolean fuzzy = scenario == Scenario.LATE_FUZZY_VARIANTS;
            IPatternDetails.IInput[] ordinaryInputs = scenario == Scenario.SUBSTITUTE
                    ? new IPatternDetails.IInput[] {new Input(false, b, glass), new Input(false, trigger)}
                    : new IPatternDetails.IInput[] {new Input(fuzzy, b, glass)};
            add("ordinary", pattern(ordinaryInputs, List.of(stack(x, 1)), id(80), fuzzy, false, false),
                    copies * (scenario == Scenario.TWO_LOOPS ? 2 : 1));
            if (scenario == Scenario.SUBSTITUTE) inventory.insert(glass, copies, Actionable.MODULATE);
            if (fuzzy) {
                add("exact", pattern(new IPatternDetails.IInput[] {new Input(false, strict)},
                        List.of(stack(finished, 1)), id(81), false, false, false), copies);
                inventory.insert(strict, copies, Actionable.MODULATE);
            }
            ledgers.initialize(tasks.keySet().stream().filter(ExecuteLoopPattern.class::isInstance)
                    .map(ExecuteLoopPattern.class::cast).toList());
            // Six permutations cover every initial order of the basic three-member dependency.
            var initial = new ArrayList<>(tasks.keySet());
            int[][] orders = {{0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}};
            var counts = new LinkedHashMap<>(tasks);
            tasks.clear();
            for (int index : orders[seed % 6]) tasks.put(initial.get(index), counts.get(initial.get(index)));
            for (int index = 3; index < initial.size(); index++) {
                tasks.put(initial.get(index), counts.get(initial.get(index)));
            }
        }

        private void addLoop(String name) {
            var a = key("seed_" + name, "");
            var group = id(name.charAt(0));
            var firstId = id(name.charAt(0) * 10L);
            var secondId = id(name.charAt(0) * 10L + 1);
            boolean fuzzy = scenario == Scenario.LATE_FUZZY_VARIANTS;
            boolean shared = scenario == Scenario.SHARED_SEED;
            long bAmount = scenario == Scenario.SUBSTITUTE ? 1 : 2;
            var firstOutputs = scenario == Scenario.SUBSTITUTE
                    ? List.of(stack(b, bAmount), stack(trigger, 1)) : List.of(stack(b, bAmount));
            var first = new ExecuteLoopPattern(pattern(new IPatternDetails.IInput[] {new Input(false, a)},
                    firstOutputs, group, false, fuzzy, shared), firstId,
                    counter(a, width), counter(a, 1), Map.of(secondId, counter(b, 1)));
            var second = new ExecuteLoopPattern(pattern(new IPatternDetails.IInput[] {
                    new Input(fuzzy, b), new Input(false, x)}, List.of(stack(a, 1)), group, fuzzy, false, shared),
                    secondId, new KeyCounter(), counter(b, 1),
                    shared ? Map.of() : Map.of(firstId, counter(a, 1)),
                    shared ? Map.of(firstId, counter(a, 1)) : Map.of());
            add(name + "->B", first, copies);
            add("B+X->" + name, second, copies);
            inventory.insert(a, width, Actionable.MODULATE);
            finalSeeds.put(a, (long) width);
        }

        private void add(String name, IPatternDetails pattern, long count) {
            names.put(pattern, name);
            tasks.put(pattern, count);
        }

        void complete() throws Exception {
            for (tick = 0; tick < 4000; tick++) {
                Collections.shuffle(events, random);
                for (var iterator = events.iterator(); iterator.hasNext();) {
                    var event = iterator.next();
                    if (event.due > tick) continue;
                    if (event.fuzzy) {
                        var claims = overload.commitPreview(overload.claimByItemId(event.actual.getId(), 1, false));
                        assertEquals(1, claims.claimedForInventory());
                        for (var claim : claims.claims()) for (var credit : claim.consumerCredits()) {
                            ledgers.rekeyAvailable(credit.consumerId(), claim.exactExpectedKey(),
                                    event.actual, credit.amount());
                        }
                    }
                    inventory.insert(event.actual, 1, Actionable.MODULATE);
                    iterator.remove();
                }
                if (tasks.values().stream().allMatch(n -> n == 0) && events.isEmpty()) break;
                var order = new ArrayList<>(tasks.keySet());
                if (tick > 0) Collections.shuffle(order, random);
                boolean progressOrRetry = false;
                for (var pattern : order) {
                    if (tasks.get(pattern) > 0) progressOrRetry |= dispatch(pattern);
                }
                maxOutstanding = Math.max(maxOutstanding, events.size());
                maxActivePatterns = Math.max(maxActivePatterns,
                        (int) events.stream().map(OutputEvent::producer).distinct().count());
                assertTrue(progressOrRetry || !events.isEmpty(),
                        () -> "Deadlock: a complete fair pass has no dispatch, rejection or pending output. " + state());
            }
            assertTrue(tick < 4000, () -> "Bounded retry/livelock limit reached. " + state());
            assertTrue(tasks.values().stream().allMatch(n -> n == 0), this::state);
            assertTrue(overload.isEmpty(), "All fuzzy output claims must have been returned");
            assertEquals(finalSeeds, ledgers.positiveSnapshot(), this::state);
            var expected = new LinkedHashMap<AEKey, Long>(finalSeeds);
            if (scenario == Scenario.LATE_FUZZY_VARIANTS) expected.put(finished, (long) copies);
            var physical = new LinkedHashMap<AEKey, Long>();
            for (var entry : inventory.list) {
                assertTrue(entry.getLongValue() >= 0, this::state);
                if (entry.getLongValue() > 0) physical.put(entry.getKey(), entry.getLongValue());
            }
            assertEquals(expected, physical, "Only restored seeds and the exact recipe's final output may remain");
            assertTrue(rejected > 0, "The run must exercise rollback/retry");
            if (batch && width > 1) assertTrue(partial > 0, "The run must exercise partial batch acceptance");
            assertTrue(maxOutstanding > 1, "Independent outputs/recipes must be allowed in flight together");
            if (scenario == Scenario.TWO_LOOPS) {
                assertTrue(maxActivePatterns >= 2, "Disjoint loops must dispatch without waiting for each other");
            }
            if (scenario == Scenario.SUBSTITUTE) {
                assertEquals(copies, consumedGlass);
                assertEquals(0, consumedPublicB, "The ordinary task must never consume the private intermediate");
            } else {
                assertEquals(copies * (scenario == Scenario.TWO_LOOPS ? 2 : 1), consumedPublicB);
            }
        }

        private boolean dispatch(IPatternDetails pattern) throws Exception {
            var loop = pattern instanceof ExecuteLoopPattern value ? value : null;
            var reserve = loop == null ? ledgers.reservationView(null, ignored -> false)
                    : ledgers.reservationView(loop.seedConsumerId(), loop::isInputSeedKey,
                            loop.hasSingleSeedInputPerMember());
            // Real CPU reservation maps intentionally cannot be enumerated.
            Map<AEKey, Long> opaque = new AbstractMap<>() {
                @Override public Long get(Object key) { return reserve.get(key); }
                @Override public Set<Entry<AEKey, Long>> entrySet() { return Set.of(); }
            };
            var visible = (ICraftingInventory) guardConstructor.newInstance(inventory, opaque);
            var before = new LinkedHashMap<AEKey, Long>();
            for (var entry : inventory.list) before.put(entry.getKey(), entry.getLongValue());
            KeyCounter[] inputs;
            long extracted = 1;
            var providerPattern = loop == null ? pattern : loop.providerLookupPattern();
            if (batch && !(providerPattern instanceof OverloadedProviderOnlyPatternDetails)
                    && (loop == null || !loop.requiresActualSeedKeyTracking())) {
                var result = TimeWheelBatchInputAllocation.withAllocator(pattern, inventory, allocator,
                        () -> TimeWheelBatchInputAllocation.extract(pattern, inventory,
                                Math.min(4, tasks.get(pattern)), false, opaque, null,
                                () -> fail("LT loop dispatch must use its scoped allocation")));
                if (result == null) return false;
                inputs = result.scaledInputs;
                extracted = result.actualCopies;
            } else {
                inputs = TimeWheelInputExtractor.extractPatternInputs(pattern, visible, null,
                        new KeyCounter(), new KeyCounter(), allocator.allocate(pattern, visible, null, 1));
                if (inputs == null) return false;
            }
            var taken = new KeyCounter();
            for (var slot : inputs) for (var entry : slot) taken.add(entry.getKey(), entry.getLongValue());
            for (var entry : taken) {
                long available = Math.max(0, before.getOrDefault(entry.getKey(), 0L)
                        - reserve.getOrDefault(entry.getKey(), 0L));
                assertTrue(entry.getLongValue() <= available, () -> "Stolen seed: " + state());
                if (!names.get(pattern).equals("exact")) {
                    assertNotEquals(strict, entry.getKey(), "Both fuzzy ordinary and loop tasks must preserve exact stock");
                }
            }
            List<ExecuteLoopPattern.ActualSeedUse> uses = null;
            if (loop != null && loop.requiresActualSeedKeyTracking()) {
                assertEquals(1, extracted, "Fuzzy loop seed routing runs through the single-copy CPU path");
                var resolution = loop.resolveActualInputSeedUses(inputs);
                assertTrue(resolution.complete(), this::state);
                uses = resolution.uses();
                assertTrue(ledgers.canRouteActualSeedUses(loop, uses), this::state);
            }
            int attempt = attempts.merge(pattern, 1, Integer::sum);
            long accepted = extracted;
            if (attempt == 1 || attempt % 7 == 0) { accepted = 0; rejected++; }
            else if (extracted > 1) { accepted = extracted - 1; partial++; }
            for (var entry : taken) {
                long returned = entry.getLongValue() / extracted * (extracted - accepted);
                if (returned > 0) inventory.insert(entry.getKey(), returned, Actionable.MODULATE);
            }
            if (accepted == 0) return true; // Retryable rejection is not an inventory deadlock.
            if (loop != null) ledgers.recordDispatch(loop, accepted, false, uses);
            tasks.put(pattern, tasks.get(pattern) - accepted);
            allocator.onTaskChange(pattern);
            if (names.get(pattern).equals("ordinary")) {
                consumedGlass += taken.get(glass) / extracted * accepted;
                for (var entry : taken) if (entry.getKey().dropSecondary().equals(b.dropSecondary())) {
                    assertNotEquals(strict, entry.getKey(), "Fuzzy task must preserve the exact-only material");
                    consumedPublicB += entry.getLongValue() / extracted * accepted;
                }
            }
            note(names.get(pattern) + " accepted=" + accepted + " remaining=" + tasks.get(pattern));
            for (long copy = 0; copy < accepted; copy++) scheduleOutputs(pattern, loop);
            return true;
        }

        private void scheduleOutputs(IPatternDetails pattern, ExecuteLoopPattern loop) throws Exception {
            var credits = loop == null ? Map.<UUID, KeyCounter>of() : loop.outputSeedCredits();
            for (var output : pattern.getOutputs()) {
                AEKey actual = output.what();
                boolean fuzzy = scenario == Scenario.LATE_FUZZY_VARIANTS && actual.equals(b);
                if (fuzzy) {
                    actual = key("intermediate", "late-" + serial++);
                }
                UUID consumer = credits.entrySet().stream().filter(e -> e.getValue().get(output.what()) > 0)
                        .map(Map.Entry::getKey).findFirst().orElse(null);
                if (fuzzy) {
                    var reference = new OverloadPatternReference(names.get(pattern), new SourcePatternSnapshot(
                            new ResourceLocation("ae2lt_test", "pattern"), null, null));
                    registerOutput.invoke(overload, reference, 0, actual.getId(), output.what(), output.amount(),
                            false, consumer == null ? null : new OverloadReusableSeedMetadata(consumer, false, 1));
                }
                for (long unit = 0; unit < output.amount(); unit++) {
                    events.add(new OutputEvent(tick + 1 + random.nextInt(5), actual, fuzzy, pattern));
                }
            }
        }

        private void note(String event) {
            if (trace.size() == 24) trace.remove(0);
            trace.add(tick + ":" + event);
        }

        private String state() {
            var remaining = new LinkedHashMap<String, Long>();
            tasks.forEach((pattern, n) -> remaining.put(names.get(pattern), n));
            var stock = new LinkedHashMap<AEKey, Long>();
            for (var entry : inventory.list) if (entry.getLongValue() != 0) stock.put(entry.getKey(), entry.getLongValue());
            return "tick=" + tick + ", tasks=" + remaining + ", stock=" + stock
                    + ", reserved=" + ledgers.positiveSnapshot() + ", trace=" + trace;
        }
    }

    private record OutputEvent(int due, AEKey actual, boolean fuzzy, IPatternDetails producer) { }

    private record Input(boolean fuzzy, GenericStack[] possible) implements IPatternDetails.IInput {
        Input(boolean fuzzy, AEKey... keys) {
            this(fuzzy, Arrays.stream(keys).map(key -> stack(key, 1)).toArray(GenericStack[]::new));
        }
        @Override public GenericStack[] getPossibleInputs() { return possible.clone(); }
        @Override public long getMultiplier() { return 1; }
        @Override public boolean isValid(AEKey key, Level level) {
            return Arrays.stream(possible).anyMatch(candidate -> fuzzy
                    ? candidate.what().dropSecondary().equals(key.dropSecondary()) : candidate.what().equals(key));
        }
        @Override public AEKey getRemainingKey(AEKey key) { return null; }
    }

    private static Pattern pattern(IPatternDetails.IInput[] inputs, List<GenericStack> outputs,
            UUID group, boolean fuzzyInput, boolean fuzzyOutput, boolean singleSeed) {
        return fuzzyInput || fuzzyOutput
                ? new FuzzyPattern(inputs, outputs, group, singleSeed, fuzzyInput, fuzzyOutput)
                : new Pattern(inputs, outputs, group, singleSeed);
    }

    private static class Pattern implements IPatternDetails,
            ISeedPreservingCraftingTask, CraftingTaskPersistenceDefinition {
        private final IPatternDetails.IInput[] inputs;
        private final List<GenericStack> outputs;
        private final UUID group;
        private final boolean singleSeed;
        Pattern(IPatternDetails.IInput[] inputs, List<GenericStack> outputs,
                UUID group, boolean singleSeed) {
            this.inputs = inputs.clone();
            this.outputs = List.copyOf(outputs);
            this.group = group;
            this.singleSeed = singleSeed;
        }
        @Override public AEItemKey getDefinition() { return null; }
        @Override public IPatternDetails.IInput[] getInputs() { return inputs.clone(); }
        @Override public GenericStack[] getOutputs() { return outputs.toArray(GenericStack[]::new); }
        @Override public UUID reusableSeedGroupId() { return group; }
        @Override public Set<AEKey> reusableSeedCycleKeys() { return Set.of(); }
        @Override public boolean hasSingleSeedInputPerMember() { return singleSeed; }
        @Override public AEItemKey craftingTaskPersistenceDefinition() { return null; }
    }

    private static final class FuzzyPattern extends Pattern implements OverloadedProviderOnlyPatternDetails {
        private final boolean fuzzyInput;
        private final boolean fuzzyOutput;
        FuzzyPattern(IPatternDetails.IInput[] inputs, List<GenericStack> outputs, UUID group,
                boolean singleSeed, boolean fuzzyInput, boolean fuzzyOutput) {
            super(inputs, outputs, group, singleSeed);
            this.fuzzyInput = fuzzyInput;
            this.fuzzyOutput = fuzzyOutput;
        }
        @Override public PatternExecutionHostKind requiredHostKind() {
            return PatternExecutionHostKind.OVERLOADED_PATTERN_PROVIDER;
        }
        @Override public String overloadPatternIdentity() { return "test:liveness:" + reusableSeedGroupId(); }
        @Override public OverloadPatternDetails overloadPatternDetailsView() { return null; }
        @Override public boolean isFuzzyInput(int slot) { return fuzzyInput && slot == 0; }
        @Override public boolean isFuzzyOutput(int slot) { return fuzzyOutput && slot == 0; }
    }

    private static UUID id(long n) { return new UUID(0x51eedL, n); }
    private static GenericStack stack(AEKey key, long n) { return new GenericStack(key, n); }
    private static KeyCounter counter(AEKey key, long n) {
        var counter = new KeyCounter();
        counter.add(key, n);
        return counter;
    }
    private static TestKey key(String id, String variant) { return new TestKey(id, variant); }

    private static final class TestKey extends AEKey {
        private static final AEKeyType TYPE = new AEKeyType(
                new ResourceLocation("ae2lt_test", "liveness"),
                TestKey.class, Component.literal("liveness key")) {
            @Override public AEKey loadKeyFromTag(CompoundTag tag) { return null; }
            @Override public AEKey readFromPacket(FriendlyByteBuf input) { return null; }
        };
        private final String id;
        private final String variant;
        TestKey(String id, String variant) { this.id = id; this.variant = variant; }
        @Override public AEKeyType getType() { return TYPE; }
        @Override public AEKey dropSecondary() { return variant.isEmpty() ? this : key(id, ""); }
        @Override public Object getPrimaryKey() { return id; }
        @Override public ResourceLocation getId() { return new ResourceLocation("ae2lt_test", id); }
        @Override public CompoundTag toTag() {
            var tag = new CompoundTag();
            tag.putString("id", id); tag.putString("variant", variant);
            return tag;
        }
        @Override public void writeToPacket(FriendlyByteBuf output) { }
        @Override protected Component computeDisplayName() { return Component.literal(id + ":" + variant); }
        @Override public void addDrops(long n, List<ItemStack> drops, Level level, BlockPos pos) { }
         public boolean hasComponents() { return !variant.isEmpty(); }
        @Override public boolean equals(Object other) {
            return other instanceof TestKey key && id.equals(key.id) && variant.equals(key.variant);
        }
        @Override public int hashCode() { return java.util.Objects.hash(id, variant); }
        @Override public String toString() { return id + ":" + variant; }
    }
}
