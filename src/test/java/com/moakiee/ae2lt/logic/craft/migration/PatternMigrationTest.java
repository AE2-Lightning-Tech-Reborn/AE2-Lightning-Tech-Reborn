package com.moakiee.ae2lt.logic.craft.migration;

import static org.junit.jupiter.api.Assertions.*;
import appeng.api.networking.IGridNode;
import appeng.api.stacks.AEItemKey;
import com.moakiee.ae2lt.network.MatrixPatternMigrationStatusPacket;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PatternMigrationTest {
    @BeforeAll static void bootstrap() {
        if (LoadingModList.get() == null) LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
    }

    private static ItemStack pattern(int count, boolean substitute) {
        var stack = new ItemStack(Items.PAPER, count);
        var tag = new CompoundTag(); tag.putBoolean("substitute", substitute);
        tag.putString("recipe", "same-output"); tag.putLong("other-settings", 100000000000L);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("original encoded pattern"));
        return stack;
    }

    @Test void exactDefinitionsIgnoreCountButKeepAllSettings() {
        assertEquals(AEItemKey.of(pattern(1, false)), AEItemKey.of(pattern(32, false)));
        assertNotEquals(AEItemKey.of(pattern(1, false)), AEItemKey.of(pattern(1, true)));
    }

    @Test void transferPreservesFullDataAndOnePhysicalItem() {
        var source = new Slot(pattern(12, true)); var target = new Destination(); var custody = new Custody();
        assertEquals(PatternMigrationTransfer.Result.MOVED, move(source, target, custody));
        assertEquals(11, source.stack.getCount()); assertEquals(1, target.stack.getCount());
        assertTrue(ItemStack.isSameItemSameComponents(source.stack, target.stack));
        assertTrue(custody.stack.isEmpty()); assertEquals(12, total(source, target, custody));
    }

    @Test void fullDestinationDoesNotExtract() {
        var source = new Slot(pattern(4, false)); var target = new Destination(); target.full = true;
        var custody = new Custody();
        assertEquals(PatternMigrationTransfer.Result.BLOCKED, move(source, target, custody));
        assertEquals(4, source.stack.getCount()); assertEquals(0, source.extractions);
    }

    @Test void destinationRejectingAfterSimulationRestoresOriginalSlot() {
        var source = new Slot(pattern(7, false)); var target = new Destination(); target.rejectCommit = true;
        var custody = new Custody();
        assertEquals(PatternMigrationTransfer.Result.RESTORED, move(source, target, custody));
        assertEquals(7, total(source, target, custody)); assertTrue(custody.stack.isEmpty());
    }

    @Test void failedRollbackRetainsExactlyOneItemInRecovery() {
        var source = new Slot(pattern(7, false)); source.restoreBlocked = true;
        var target = new Destination(); target.rejectCommit = true; var custody = new Custody();
        assertEquals(PatternMigrationTransfer.Result.RECOVERY_REQUIRED, move(source, target, custody));
        assertEquals(6, source.stack.getCount()); assertEquals(1, custody.stack.getCount());
        assertEquals(7, total(source, target, custody));
    }

    @Test void rollbackCallbackThrowAfterStackedRestoreDoesNotDuplicateRecovery() {
        var source = new Slot(pattern(7, false)); source.throwRestoreAfter = true;
        var target = new Destination(); target.rejectCommit = true; var custody = new Custody();
        assertEquals(PatternMigrationTransfer.Result.RESTORED, move(source, target, custody));
        assertTrue(custody.stack.isEmpty()); assertEquals(7, total(source, target, custody));
    }

    @Test void extractionCallbackThrowIsAuditedAndRestored() {
        var source = new Slot(pattern(7, false)); source.throwExtractAfter = true;
        var target = new Destination(); var custody = new Custody();
        assertEquals(PatternMigrationTransfer.Result.RESTORED, move(source, target, custody));
        assertTrue(target.stack.isEmpty()); assertEquals(7, total(source, target, custody));
    }

    @Test void insertionCallbackThrowAfterReceiptDoesNotRestoreSource() {
        var source = new Slot(pattern(7, false)); var target = new Destination(); target.throwInsertAfter = true;
        var custody = new Custody();
        assertEquals(PatternMigrationTransfer.Result.MOVED, move(source, target, custody));
        assertEquals(6, source.stack.getCount()); assertTrue(custody.stack.isEmpty());
        assertEquals(7, total(source, target, custody));
    }

    @Test void changedSlotFailsBeforeExtraction() {
        var source = new Slot(pattern(7, false)); var captured = source.stack.copy();
        source.stack = pattern(7, true); var target = new Destination(); var custody = new Custody();
        assertEquals(PatternMigrationTransfer.Result.SOURCE_CHANGED,
                PatternMigrationTransfer.move(source, captured, target, custody::hold));
        assertEquals(0, source.extractions);
    }

    @Test void mutationDuringSimulationFailsBeforeExtraction() {
        var source = new Slot(pattern(7, false)); source.mutateSimulation = true;
        assertEquals(PatternMigrationTransfer.Result.SOURCE_CHANGED, move(source, new Destination(), new Custody()));
        assertEquals(0, source.extractions);
    }

    @Test void activityBeginsAfterSimulationWithoutExtractingAUnit() {
        var source = new Slot(pattern(7, false)) {
            @Override public boolean canExtract() { return false; }
        };
        var target = new Destination(); var custody = new Custody();
        assertEquals(PatternMigrationTransfer.Result.SOURCE_CHANGED, move(source, target, custody));
        assertEquals(0, source.extractions);
        assertEquals(7, total(source, target, custody));
    }

    @Test void priorityAndRoundRobinCoverOneHundredThousandSlots() {
        var queue = new PatternMigrationQueue<Integer>(i -> i);
        queue.add(2, 100); queue.add(0, 99900); queue.add(0, 32);
        long began = System.nanoTime();
        for (int i = 0; i < 32; i++) assertEquals(99900, queue.next().source());
        for (int i = 0; i < 32; i++) assertEquals(32, queue.next().source());
        int visited = 64;
        PatternMigrationQueue.Step<Integer> step;
        while ((step = queue.next()) != null) { visited++; if (step.tier() == 3) assertTrue(visited > 99932); }
        assertEquals(100032, visited);
        System.out.println("MIGRATION_100K_QUEUE_NS=" + (System.nanoTime() - began));
        queue.add(3, 1); assertEquals(4, queue.next().tier()); assertNull(queue.next());
    }

    @Test void perGridLeaseAndServerBudgetRotateWithoutStarvation() {
        var clock = new AtomicLong(100); var budget = new PatternMigrationBudget(clock::get);
        var grids = List.of(new Object(), new Object(), new Object());
        var owners = List.of(new Object(), new Object(), new Object());
        for (int i = 0; i < 3; i++) assertTrue(budget.acquire(grids.get(i), owners.get(i)));
        assertFalse(budget.acquire(grids.get(0), new Object()));
        assertEquals(2000100, budget.begin(1, owners.get(0))); budget.record(2000000);
        assertEquals(2000100, budget.begin(1, owners.get(1))); budget.record(2000000);
        assertEquals(0, budget.begin(1, owners.get(2)));
        assertNotEquals(0, budget.begin(2, owners.get(2)));
        assertEquals(0, budget.begin(2, owners.get(1)));
        budget.release(grids.get(0), owners.get(0)); assertFalse(budget.isOwner(grids.get(0), owners.get(0)));
        assertTrue(budget.acquire(grids.get(0), new Object()));
    }

    @Test void thirdPartyOverrunConsumesSharedBudget() {
        var budget = new PatternMigrationBudget(() -> 100L);
        var a = new Object(); var b = new Object();
        budget.acquire(new Object(), a); budget.acquire(new Object(), b);
        assertNotEquals(0, budget.begin(1, a)); budget.record(5000000);
        assertEquals(0, budget.begin(1, b)); assertNotEquals(0, budget.begin(2, b));
    }

    @Test void rosterUsesConcreteClassesAndFiniteEpochAcrossRemovalAndRejoin() {
        var roster = new PatternMigrationGridService();
        var a = node(new Parent()); var b = node(new Child());
        roster.addNode(a, new CompoundTag()); roster.addNode(b, new CompoundTag()); long epoch = roster.epoch();
        var c = node(new Child()); roster.addNode(c, new CompoundTag());
        assertSame(a, roster.next(Parent.class, 0, epoch).node());
        assertSame(b, roster.next(Child.class, 0, epoch).node());
        assertNull(roster.next(Child.class, roster.next(Child.class, 0, epoch).id(), epoch));
        roster.removeNode(b); roster.addNode(b, new CompoundTag());
        assertNull(roster.next(Child.class, 0, epoch));
    }

    @Test void batchFlushesEachIdentityOnceAndRestoresScopeAfterFailure() {
        var owner = new Object(); var count = new AtomicLong();
        try (var scope = PatternMigrationMutationScope.open()) {
            for (int i = 0; i < 100; i++) assertTrue(PatternMigrationMutationScope.defer(owner, count::incrementAndGet));
            assertEquals(0, count.get());
        }
        assertEquals(1, count.get()); assertFalse(PatternMigrationMutationScope.defer(owner, count::incrementAndGet));
        assertThrows(IllegalStateException.class, () -> {
            try (var scope = PatternMigrationMutationScope.open()) {
                PatternMigrationMutationScope.defer(owner, () -> { throw new IllegalStateException(); });
                PatternMigrationMutationScope.defer(new Object(), count::incrementAndGet);
            }
        });
        assertEquals(2, count.get()); assertFalse(PatternMigrationMutationScope.defer(owner, count::incrementAndGet));
    }

    @Test void statusCodecPreservesLargeCounters() {
        var snapshot = new PatternMigrationSnapshot(PatternMigrationSnapshot.Stage.WAITING,
                PatternMigrationSnapshot.Reason.BUSY, 4, 100000, 1000000, 65536, 99999,
                80000, 70000, 60000, 50000, 40000, 30000, 5000000, 9999999);
        var packet = new MatrixPatternMigrationStatusPacket(91, snapshot);
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            MatrixPatternMigrationStatusPacket.STREAM_CODEC.encode(buf, packet);
            assertEquals(packet, MatrixPatternMigrationStatusPacket.STREAM_CODEC.decode(buf));
        } finally { buf.release(); }
    }

    private static IGridNode node(Object owner) {
        return (IGridNode) Proxy.newProxyInstance(IGridNode.class.getClassLoader(), new Class<?>[]{IGridNode.class},
                (proxy, method, args) -> method.getName().equals("getOwner") ? owner : null);
    }
    static class Parent {} static class Child extends Parent {}
    static class Custody { ItemStack stack = ItemStack.EMPTY; void hold(ItemStack item) { stack = item.copy(); } }
    private static PatternMigrationTransfer.Result move(Slot source, Destination target, Custody custody) {
        return PatternMigrationTransfer.move(source, source.stack.copy(), target, custody::hold);
    }
    private static int total(Slot source, Destination target, Custody custody) {
        return source.stack.getCount() + target.stack.getCount() + custody.stack.getCount();
    }
    static class Slot implements PatternMigrationTransfer.Slot {
        ItemStack stack; int extractions;
        boolean restoreBlocked, throwRestoreAfter, throwExtractAfter, mutateSimulation;
        Slot(ItemStack stack) { this.stack = stack; }
        public ItemStack read() { return stack; }
        public ItemStack extract(boolean simulate) {
            var unit = stack.copyWithCount(1);
            if (simulate && mutateSimulation) stack = pattern(stack.getCount(), true);
            if (!simulate) { stack.shrink(1); extractions++; if (throwExtractAfter) throw new IllegalStateException(); }
            return unit;
        }
        public ItemStack restore(ItemStack held) {
            if (restoreBlocked) return held;
            if (stack.isEmpty()) stack = held.copy(); else stack.grow(held.getCount());
            if (throwRestoreAfter) throw new IllegalStateException();
            return ItemStack.EMPTY;
        }
    }
    static class Destination implements PatternMigrationTransfer.Destination {
        ItemStack stack = ItemStack.EMPTY; boolean full, rejectCommit, throwInsertAfter;
        public boolean canAccept(ItemStack held) { return !full; }
        public ItemStack insert(ItemStack held) {
            if (rejectCommit) return held;
            stack = held.copy(); if (throwInsertAfter) throw new IllegalStateException();
            return ItemStack.EMPTY;
        }
        public boolean received(ItemStack held) { return PatternMigrationTransfer.same(held, stack); }
    }
}
