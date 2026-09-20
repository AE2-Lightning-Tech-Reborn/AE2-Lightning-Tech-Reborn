package com.moakiee.ae2lt.crafting.timewheel.allocation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;
import com.moakiee.thunderbolt.api.crafting.batch.BatchJobView;
import com.moakiee.thunderbolt.api.crafting.batch.BatchTaskHandle;
import com.moakiee.thunderbolt.core.crafting.batch.BatchExecutor;
import com.moakiee.thunderbolt.core.crafting.batch.ParallelBatchCpuHelper;
import com.moakiee.thunderbolt.core.crafting.batch.SharedBatchInputPattern;
import com.moakiee.thunderbolt.core.crafting.pattern.PlannedInputPattern;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class TimeWheelBatchInputAllocationTest {
    private static final AEKey G = key("glass"), Q = key("quartz"), TOOL = key("tool"),
            OUT = key("output"), CONTAINER = key("container");

    @Test
    void nativeResultKeepsAllocationAcrossPartialRejectionAndRetry() {
        assertTrue(TimeWheelInputExtractor.canExportNativeBatch());
        var flexible = new Pattern(new IPatternDetails.IInput[] {input(null, Q, G)}, List.of(stack(OUT)), false);
        var exact = new Pattern(new IPatternDetails.IInput[] {input(null, Q)}, List.of(stack(OUT)), false);
        var tasks = new LinkedHashMap<IPatternDetails, Long>();
        tasks.put(flexible, 4L); tasks.put(exact, 4L);
        var allocator = new ExecutionInputAllocator(tasks, n -> n, true, true);
        var stock = new ListCraftingInventory(allocator::onInventoryChange);
        stock.insert(G, 4, Actionable.MODULATE);
        stock.insert(Q, 4, Actionable.MODULATE);
        var first = scoped(flexible, stock, allocator, 4, false);
        assertNotNull(first);
        assertEquals(4, first.actualCopies);
        assertEquals(4, first.scaledInputs[0].get(G));
        assertEquals(0, first.scaledInputs[0].get(Q));
        ParallelBatchCpuHelper.markDispatched(first, 1);
        ParallelBatchCpuHelper.reinject(first, 3, stock);
        tasks.put(flexible, 3L); allocator.onTaskChange(flexible);
        assertEquals(3, stock.list.get(G));
        assertEquals(4, stock.list.get(Q));
        var retry = scoped(flexible, stock, allocator, 3, false);
        assertNotNull(retry);
        assertEquals(3, retry.actualCopies);
        assertEquals(3, retry.scaledInputs[0].get(G));
        ParallelBatchCpuHelper.reinject(retry, 3, stock);
        assertEquals(3, stock.list.get(G));
        assertEquals(4, stock.list.get(Q));
    }

    @Test
    void convertedSharedBatchRetainsSeedAndConcreteRemainderAccounting() {
        var pattern = new Pattern(new IPatternDetails.IInput[] {input(null, TOOL), input(CONTAINER, G)},
                List.of(stack(TOOL), stack(OUT)), true);
        var tasks = new LinkedHashMap<IPatternDetails, Long>(); tasks.put(pattern, 4L);
        var allocator = new ExecutionInputAllocator(tasks, n -> n, true, true);
        var stock = new ListCraftingInventory(allocator::onInventoryChange);
        stock.insert(TOOL, 1, Actionable.MODULATE);
        stock.insert(G, 4, Actionable.MODULATE);
        var result = scoped(pattern, stock, allocator, 4, true);
        assertNotNull(result);
        assertEquals(4, result.actualCopies);
        assertTrue(result.hasSharedInputs());
        assertEquals(1, result.scaledInputs[0].get(TOOL));
        assertEquals(4, result.scaledInputs[1].get(G));
        var job = new Job();
        ParallelBatchCpuHelper.markDispatched(result, 2);
        ParallelBatchCpuHelper.registerExpectedOutputs(job, pattern, result, 2);
        ParallelBatchCpuHelper.reinject(result, 2, stock);
        assertEquals(0, stock.list.get(TOOL), "an accepted batch owns the single seed until return");
        assertEquals(2, stock.list.get(G));
        assertEquals(1, job.waiting.list.get(TOOL));
        assertEquals(2, job.waiting.list.get(OUT));
        assertEquals(2, job.waiting.list.get(CONTAINER));
        assertEquals(2, job.remainders);
    }

    @Test
    void scopeExcludesOtherCpusAndRestoresOuterScopeAfterFailure() {
        var pattern = new Pattern(new IPatternDetails.IInput[] {input(null, G)}, List.of(stack(OUT)), false);
        var other = new Pattern(new IPatternDetails.IInput[] {input(null, G)}, List.of(stack(OUT)), false);
        var allocator = new ExecutionInputAllocator(Map.of(pattern, 1L, other, 1L), n -> n);
        var stock = new ListCraftingInventory(key -> { });
        var foreignStock = new ListCraftingInventory(key -> { });
        var originalCalls = new AtomicInteger();
        java.util.function.Supplier<ParallelBatchCpuHelper.BulkResult> original = () -> {
            originalCalls.incrementAndGet(); return null;
        };
        TimeWheelBatchInputAllocation.extract(pattern, stock, 1, false, Map.of(), null, original);
        assertEquals(1, originalCalls.get());
        TimeWheelBatchInputAllocation.withAllocator(pattern, stock, allocator, () -> {
            TimeWheelBatchInputAllocation.extract(other, stock, 1, false, Map.of(), null, original);
            TimeWheelBatchInputAllocation.extract(pattern, foreignStock, 1, false, Map.of(), null, original);
            assertEquals(3, originalCalls.get());
            assertThrows(IllegalStateException.class, () -> TimeWheelBatchInputAllocation.withAllocator(
                    other, foreignStock, allocator, () -> { throw new IllegalStateException("provider failed"); }));
            TimeWheelBatchInputAllocation.extract(pattern, stock, 1, false, Map.of(), null, original);
            assertEquals(3, originalCalls.get(), "outer LT scope must still use allocation after nested failure");
            return null;
        });
        TimeWheelBatchInputAllocation.extract(pattern, stock, 1, false, Map.of(), null, original);
        assertEquals(4, originalCalls.get(), "completed scope must not retain CPU/job state on the thread");
    }

    @Test
    void removingInputBindingsKeepsTheOriginalExecutionWrapper() {
        var source = new Pattern(new IPatternDetails.IInput[] {input(null, Q, G)}, List.of(stack(OUT)), false);
        var inner = new PlannedInputPattern(source, List.of(Map.of(Q, 1L)));
        var outer = new PlannedInputPattern(inner, List.of(Map.of(Q, 1L)));
        assertSame(source, ExecutionTaskInputs.unbound(outer));
        assertSame(source, ExecutionTaskInputs.unbound(source));
        assertEquals(Map.of(Q, 1L), inner.allocations().get(0), "normalization does not mutate metadata");
    }

    @Test
    void supportedNativeDispatcherContainsExactlyOneScopedExtractionHook() throws Exception {
        var matches = new AtomicInteger();
        try (var stream = BatchExecutor.class.getResourceAsStream("BatchExecutor.class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                        String signature, String[] exceptions) {
                    if (!name.equals("runBatchOnly")) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String owner, String name,
                                String descriptor, boolean isInterface) {
                            if (owner.equals("com/moakiee/thunderbolt/core/crafting/batch/ParallelBatchCpuHelper")
                                    && name.equals("bulkExtract") && descriptor.equals(
                                    "(Lappeng/api/crafting/IPatternDetails;Lappeng/crafting/inv/ListCraftingInventory;"
                                    + "JZLjava/util/Map;Lnet/minecraft/world/level/Level;)"
                                    + "Lcom/moakiee/thunderbolt/core/crafting/batch/ParallelBatchCpuHelper$BulkResult;")) {
                                matches.incrementAndGet();
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
        }
        assertEquals(1, matches.get(), "The LT-only Mixin must match the unmodified dependency");
    }

    private static ParallelBatchCpuHelper.BulkResult scoped(IPatternDetails pattern,
            ListCraftingInventory stock, ExecutionInputAllocator allocator, long copies, boolean shared) {
        return TimeWheelBatchInputAllocation.withAllocator(pattern, stock, allocator,
                () -> TimeWheelBatchInputAllocation.extract(pattern, stock, copies, shared, Map.of(), null,
                        () -> fail("LT scope must not execute unallocated native extraction")));
    }

    private static AEKey key(String name) { return new ExecutionInputAllocatorTest.TestKey(name); }
    private static GenericStack stack(AEKey key) { return new GenericStack(key, 1); }
    private static IPatternDetails.IInput input(AEKey remainder, AEKey... keys) {
        return new IPatternDetails.IInput() {
            @Override public GenericStack[] getPossibleInputs() {
                return java.util.Arrays.stream(keys).map(TimeWheelBatchInputAllocationTest::stack).toArray(GenericStack[]::new);
            }
            @Override public long getMultiplier() { return 1; }
            @Override public boolean isValid(AEKey key, Level level) { return List.of(keys).contains(key); }
            @Override public AEKey getRemainingKey(AEKey key) { return remainder; }
        };
    }

    private record Pattern(IPatternDetails.IInput[] inputs, List<GenericStack> outputs, boolean shared)
            implements IPatternDetails, SharedBatchInputPattern {
        @Override public AEItemKey getDefinition() { return null; }
        @Override public IInput[] getInputs() { return inputs.clone(); }
        @Override public GenericStack[] getOutputs() { return outputs.toArray(GenericStack[]::new); }
        @Override public boolean isSharedBatchInput(int slot, AEKey key) { return shared && slot == 0; }
        @Override public long sharedBatchOutputAmount(AEKey key) { return shared && key.equals(TOOL) ? 1 : 0; }
    }

    private static final class Job implements BatchJobView {
        private final ListCraftingInventory waiting = new ListCraftingInventory(key -> { });
        private long remainders;
        @Override public Level level() { return null; }
        @Override public Iterator<BatchTaskHandle> taskIterator() { return java.util.Collections.emptyIterator(); }
        @Override public ListCraftingInventory waitingFor() { return waiting; }
        @Override public UUID craftingId() { return null; }
        @Override public void addContainerMaxItems(long count, AEKeyType type) { remainders += count; }
    }
}
