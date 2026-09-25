package com.moakiee.ae2lt.logic.tianshu.loop;

import static org.junit.jupiter.api.Assertions.*;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import com.moakiee.ae2lt.logic.tianshu.terminal.SeedRefillSync;
import com.moakiee.ae2lt.overload.runtime.pattern.SourcePatternSnapshot;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class TianshuSeedRefillServiceTest {
    private static final AEKey SEED = new TestKey("seed");
    private static final AEKey OLD = new TestKey("obsolete");
    private static final IActionSource SOURCE = IActionSource.empty();

    @Test void deletedPatternsReturnAllPhysicalSeedsAndRepeatedClickIsIdempotent() {
        var seeds = new Storage(100).with(SEED, 8).with(OLD, 5);
        var network = new Storage(100);
        var result = reconcile(Map.of(), seeds, network);
        assertTrue(result.complete());
        assertEquals(Map.of(SEED, 8L, OLD, 5L), result.returned());
        assertEquals(0, seeds.amount(SEED));
        assertEquals(8, network.amount(SEED));
        assertEquals(5, network.amount(OLD));
        var again = reconcile(Map.of(), seeds, network);
        assertTrue(again.complete());
        assertTrue(again.returned().isEmpty());
    }

    @Test void loweredMultiplierKeepsExactlyTheCurrentTarget() {
        var seeds = new Storage(100).with(SEED, 32);
        var network = new Storage(100);
        var result = reconcile(Map.of(SEED, 8L), seeds, network);
        assertEquals(24L, result.returned().get(SEED));
        assertEquals(8, seeds.amount(SEED));
        assertEquals(24, network.amount(SEED));
        assertTrue(result.complete());
    }

    @Test void obsoleteSeedsAreReturnedBeforeNewSeedsNeedTheirCapacity() {
        var seeds = new Storage(8).with(OLD, 8);
        var network = new Storage(100).with(SEED, 8);
        var result = reconcile(Map.of(SEED, 8L), seeds, network);
        assertTrue(result.complete());
        assertEquals(8, seeds.amount(SEED));
        assertEquals(0, seeds.amount(OLD));
        assertEquals(8, network.amount(OLD));
        assertEquals(0, network.amount(SEED));
    }

    @Test void fullNetworkLeavesSeedsInPlaceAndReportsReturnBlocked() {
        var seeds = new Storage(100).with(OLD, 8);
        var network = new Storage(0);
        var result = reconcile(Map.of(), seeds, network);
        assertFalse(result.complete());
        assertEquals(8, seeds.amount(OLD));
        assertEquals(0, seeds.extractionCalls);
        assertEquals(8L, result.returnBlocked().get(OLD));
        var sync = SeedRefillSync.of(result);
        assertEquals(SeedRefillSync.STATE_RETURN_BLOCKED, sync.state());
        assertEquals(8L, sync.problems().get(0).returnBlocked());
    }

    @Test void partlyFullNetworkOnlyTakesWhatItCanStore() {
        var seeds = new Storage(100).with(OLD, 8);
        var network = new Storage(3);
        var result = reconcile(Map.of(), seeds, network);
        assertEquals(3L, result.returned().get(OLD));
        assertEquals(5L, result.returnBlocked().get(OLD));
        assertEquals(5, seeds.amount(OLD));
        assertEquals(3, network.amount(OLD));
    }

    @Test void destinationChangingItsAcceptanceRefundsAllRejectedSeeds() {
        var seeds = new Storage(100).with(OLD, 8);
        var network = new Storage(100);
        network.actualInsertLimit = 3;
        var result = reconcile(Map.of(), seeds, network);
        assertEquals(3, network.amount(OLD));
        assertEquals(5, seeds.amount(OLD));
        assertEquals(5L, result.returnBlocked().get(OLD));
    }

    @Test void shortageAndBlockedReturnRemainDistinctInMixedStatus() {
        var seeds = new Storage(100).with(OLD, 8);
        var network = new Storage(0);
        var result = reconcile(Map.of(SEED, 4L), seeds, network);
        assertEquals(4L, result.networkMissing().get(SEED));
        assertEquals(8L, result.returnBlocked().get(OLD));
        assertEquals(SeedRefillSync.STATE_MIXED, SeedRefillSync.of(result).state());
    }

    @Test void targetAggregatesEnabledPatternsByMaximumAndBothMultipliers() {
        var small = payload(SEED, 1, 2, 3, true);
        var large = payload(SEED, 2, 4, 2, true);
        var disabled = payload(OLD, 1, 100, 100, false);
        assertEquals(Map.of(SEED, 16L), TianshuSeedRefillService.requirements(List.of(small, large, disabled)));
        assertEquals(Map.of(SEED, 6L), TianshuSeedRefillService.requirements(List.of(small, disabled)));
        assertEquals(Map.of(), TianshuSeedRefillService.requirements(List.of(disabled)));
    }

    @Test void exactComponentVariantsAreReturnedWithoutChangingTheirIdentity() {
        var wanted = new TestKey("seed", "wanted");
        var obsolete = new TestKey("seed", "old");
        var seeds = new Storage(100).with(wanted, 4).with(obsolete, 8);
        var network = new Storage(100);
        var result = reconcile(Map.of(wanted, 4L), seeds, network);
        assertTrue(result.complete());
        assertEquals(4, seeds.amount(wanted));
        assertEquals(8, network.amount(obsolete));
        assertEquals(0, network.amount(wanted));
    }

    private static TianshuSeedRefillService.RefillResult reconcile(
            Map<AEKey, Long> required, Storage seeds, Storage network) {
        return TianshuSeedRefillService.reconcile(required, seeds, network, SOURCE);
    }

    private static ClosedLoopPatternPayload payload(AEKey seed, long amount, int execution, int stored, boolean enabled) {
        var member = new SourcePatternSnapshot(new ResourceLocation("ae2", "encoded_processing_pattern"), null, null);
        return new ClosedLoopPatternPayload(List.of(new ClosedLoopMemberPattern(member, 1)),
                List.of(new GenericStack(seed, amount)), List.of(new GenericStack(SEED, 1)),
                List.of(new GenericStack(SEED, 1)), execution, stored, enabled);
    }

    private static final class Storage implements MEStorage {
        private final Map<AEKey, Long> amounts = new HashMap<>();
        private final long capacity;
        long actualInsertLimit = Long.MAX_VALUE;
        int extractionCalls;
        Storage(long capacity) { this.capacity = capacity; }
        Storage with(AEKey key, long amount) { amounts.put(key, amount); return this; }
        long amount(AEKey key) { return amounts.getOrDefault(key, 0L); }
        @Override public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            long used = amounts.values().stream().mapToLong(Long::longValue).sum();
            long accepted = Math.min(amount, Math.max(0, capacity - used));
            if (mode == Actionable.MODULATE) {
                accepted = Math.min(accepted, actualInsertLimit);
                amounts.merge(key, accepted, Long::sum);
            }
            return accepted;
        }
        @Override public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            long extracted = Math.min(amount, amount(key));
            if (mode == Actionable.MODULATE) {
                extractionCalls++;
                amounts.put(key, amount(key) - extracted);
            }
            return extracted;
        }
        @Override public void getAvailableStacks(KeyCounter out) { amounts.forEach(out::add); }
        @Override public Component getDescription() { return Component.literal("test storage"); }
    }
    private static final class TestKey extends AEKey {
        private static final TestKeyType TYPE = new TestKeyType();
        private final String id;
        private final String secondary;

        private TestKey(String id) { this(id, ""); }
        private TestKey(String id, String secondary) {
            this.id = id;
            this.secondary = secondary;
        }
        @Override public AEKeyType getType() { return TYPE; }
        @Override public AEKey dropSecondary() {
            return secondary.isEmpty() ? this : new TestKey(id);
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
        @Override protected Component computeDisplayName() { return Component.literal(id + secondary); }
        @Override public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) { }
        @Override public boolean equals(Object obj) {
            return obj instanceof TestKey other && id.equals(other.id) && secondary.equals(other.secondary);
        }
        @Override public int hashCode() { return java.util.Objects.hash(id, secondary); }
    }

    private static final class TestKeyType extends AEKeyType {
        private TestKeyType() {
            super(new ResourceLocation("ae2lt_test", "key"), TestKey.class,
                    Component.literal("test key"));
        }
        @Override public AEKey loadKeyFromTag(CompoundTag tag) { return null; }
        @Override public AEKey readFromPacket(FriendlyByteBuf input) { return null; }
    }
}
