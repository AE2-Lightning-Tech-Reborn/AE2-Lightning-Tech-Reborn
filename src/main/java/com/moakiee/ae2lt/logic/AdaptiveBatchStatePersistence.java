package com.moakiee.ae2lt.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import appeng.util.inv.AppEngInternalInventory;

import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.WirelessConnection;

/**
 * Persists only the adaptive batch facts that remain valid across a provider
 * reconstruction. Transient ready queues, same-tick growth proofs and retry
 * cursors deliberately restart from a safe state.
 */
final class AdaptiveBatchStatePersistence {
    private static final String TAG_ROOT = "ae2lt:adaptive_batch_state";
    private static final String TAG_VERSION = "version";
    private static final int VERSION = 1;

    private static final String TAG_PATTERNS = "patterns";
    private static final String TAG_TARGETS = "targets";
    private static final String TAG_SLOT = "slot";
    private static final String TAG_STACK = "stack";
    private static final String TAG_KIND = "kind";
    private static final String TAG_DIRECTION = "direction";
    private static final String TAG_ADDRESS = "address";
    private static final String TAG_STATES = "states";
    private static final byte KIND_NORMAL = 0;
    private static final byte KIND_WIRELESS = 1;

    private static final String TAG_REMEMBERED_CHUNK = "remembered_chunk";
    private static final String TAG_STEP = "step";
    private static final String TAG_NEXT_CHUNK = "next_chunk";
    private static final String TAG_PROVEN_CHUNK = "proven_chunk";
    private static final String TAG_PROVEN_SUCCESSES = "proven_successes";
    private static final String TAG_REPEAT_CURRENT = "repeat_current";
    private static final String TAG_GROWTH_CAPPED = "growth_capped";
    private static final String TAG_BACKING_OFF = "backing_off";
    private static final String TAG_LAST_SUCCESSFUL_TICK = "last_successful_tick";
    private static final String TAG_LAST_ATTEMPT_TICK = "last_attempt_tick";
    private static final String TAG_RESERVOIR_MODE = "reservoir_mode";
    private static final String TAG_RESERVOIR_TAIL_LOWER = "reservoir_tail_lower";
    private static final String TAG_RESERVOIR_TAIL_UPPER = "reservoir_tail_upper";
    private static final String TAG_RESERVOIR_TAIL_SUPPRESSED = "reservoir_tail_suppressed";
    private static final String TAG_LAST_TAIL_ATTEMPT = "last_tail_attempt_tick";

    @Nullable
    private PendingState pending;

    void write(
            CompoundTag ownerTag,
            HolderLookup.Provider registries,
            AppEngInternalInventory patternInventory,
            OverloadedProviderPatternCatalog patternCatalog,
            ProviderNormalDispatch normalDispatch,
            List<WirelessConnection> wirelessConnections) {
        var targetTags = new ListTag();
        var usedPatternSlots = new HashSet<Integer>();

        for (var entry : normalDispatch.targets().entrySet()) {
            var targetTag = writeTarget(
                    entry.getValue(), patternCatalog, usedPatternSlots);
            if (targetTag == null) {
                continue;
            }
            targetTag.putByte(TAG_KIND, KIND_NORMAL);
            targetTag.putByte(
                    TAG_DIRECTION,
                    (byte) entry.getKey().get3DDataValue());
            targetTags.add(targetTag);
        }

        for (var connection : wirelessConnections) {
            var targetTag = writeTarget(
                    connection, patternCatalog, usedPatternSlots);
            if (targetTag == null) {
                continue;
            }
            targetTag.putByte(TAG_KIND, KIND_WIRELESS);
            targetTag.put(TAG_ADDRESS, connection.toTag());
            targetTags.add(targetTag);
        }

        if (targetTags.isEmpty()) {
            ownerTag.remove(TAG_ROOT);
            return;
        }

        var patternTags = new ListTag();
        usedPatternSlots.stream().sorted().forEach(slot -> {
            if (slot < 0 || slot >= patternInventory.size()) {
                return;
            }
            var stack = patternInventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                return;
            }
            var patternTag = new CompoundTag();
            patternTag.putInt(TAG_SLOT, slot);
            patternTag.put(TAG_STACK, com.moakiee.ae2lt.recipe.compat.LegacyItemStackNbt.save(stack, registries));
            patternTags.add(patternTag);
        });

        if (patternTags.isEmpty()) {
            ownerTag.remove(TAG_ROOT);
            return;
        }

        var root = new CompoundTag();
        root.putInt(TAG_VERSION, VERSION);
        root.put(TAG_PATTERNS, patternTags);
        root.put(TAG_TARGETS, targetTags);
        ownerTag.put(TAG_ROOT, root);
    }

    @Nullable
    private static CompoundTag writeTarget(
            ProviderTarget target,
            OverloadedProviderPatternCatalog patternCatalog,
            Set<Integer> usedPatternSlots) {
        var stateTags = new ListTag();
        target.adaptiveBatchSnapshots().forEach((pattern, snapshot) -> {
            int slot = patternCatalog.slotOf(pattern);
            if (slot < 0 || !snapshot.isValid()) {
                return;
            }
            var stateTag = writeSnapshot(snapshot);
            stateTag.putInt(TAG_SLOT, slot);
            stateTags.add(stateTag);
            usedPatternSlots.add(slot);
        });
        if (stateTags.isEmpty()) {
            return null;
        }
        var result = new CompoundTag();
        result.put(TAG_STATES, stateTags);
        return result;
    }

    void read(
            CompoundTag ownerTag,
            HolderLookup.Provider registries,
            int patternCapacity,
            int wirelessCapacity) {
        pending = null;
        if (!com.moakiee.ae2lt.recipe.compat.LegacyNbtTypes.contains(ownerTag, TAG_ROOT, Tag.TAG_COMPOUND)) {
            return;
        }
        var root = ownerTag.getCompoundOrEmpty(TAG_ROOT);
        if (root.getIntOr(TAG_VERSION, 0) != VERSION) {
            return;
        }

        int safePatternCapacity = Math.max(0, patternCapacity);
        var patterns = new HashMap<Integer, ItemStack>();
        var patternTags = root.getListOrEmpty(TAG_PATTERNS);
        int patternCount = Math.min(patternTags.size(), safePatternCapacity);
        for (int i = 0; i < patternCount; i++) {
            var patternTag = patternTags.getCompoundOrEmpty(i);
            int slot = patternTag.getIntOr(TAG_SLOT, 0);
            if (slot < 0 || slot >= safePatternCapacity
                    || !com.moakiee.ae2lt.recipe.compat.LegacyNbtTypes.contains(patternTag, TAG_STACK, Tag.TAG_COMPOUND)) {
                continue;
            }
            try {
                var stack = com.moakiee.ae2lt.recipe.compat.LegacyItemStackNbt.parseOptional(
                        registries, patternTag.getCompoundOrEmpty(TAG_STACK));
                if (!stack.isEmpty()) {
                    patterns.putIfAbsent(slot, stack);
                }
            } catch (RuntimeException ignored) {
                // Corrupt or no-longer-decodable pattern identities fail closed.
            }
        }
        if (patterns.isEmpty()) {
            return;
        }

        int safeTargetCapacity = Math.max(0, wirelessCapacity) + 6;
        var targets = new ArrayList<PendingTarget>();
        var targetTags = root.getListOrEmpty(TAG_TARGETS);
        int targetCount = Math.min(targetTags.size(), safeTargetCapacity);
        for (int i = 0; i < targetCount; i++) {
            var targetTag = targetTags.getCompoundOrEmpty(i);
            var states = readStates(
                    targetTag.getListOrEmpty(TAG_STATES),
                    safePatternCapacity,
                    patterns);
            if (states.isEmpty()) {
                continue;
            }
            byte kind = targetTag.getByteOr(TAG_KIND, (byte) 0);
            if (kind == KIND_NORMAL) {
                int directionId = targetTag.getByteOr(TAG_DIRECTION, (byte) 0);
                if (directionId >= 0
                        && directionId < Direction.values().length) {
                    targets.add(PendingTarget.normal(
                            Direction.from3DDataValue(directionId), states));
                }
            } else if (kind == KIND_WIRELESS
                    && com.moakiee.ae2lt.recipe.compat.LegacyNbtTypes.contains(targetTag, TAG_ADDRESS, Tag.TAG_COMPOUND)) {
                try {
                    targets.add(PendingTarget.wireless(
                            WirelessConnection.fromTag(
                                    targetTag.getCompoundOrEmpty(TAG_ADDRESS)),
                            states));
                } catch (RuntimeException ignored) {
                    // Invalid dimensions or addresses are not recoverable.
                }
            }
        }
        if (!targets.isEmpty()) {
            pending = new PendingState(Map.copyOf(patterns), List.copyOf(targets));
        }
    }

    private static Map<Integer, ProviderTarget.AdaptiveBatchSnapshot> readStates(
            ListTag stateTags,
            int patternCapacity,
            Map<Integer, ItemStack> patterns) {
        var states = new HashMap<
                Integer, ProviderTarget.AdaptiveBatchSnapshot>();
        int stateCount = Math.min(stateTags.size(), patternCapacity);
        for (int i = 0; i < stateCount; i++) {
            var stateTag = stateTags.getCompoundOrEmpty(i);
            int slot = stateTag.getIntOr(TAG_SLOT, 0);
            if (!patterns.containsKey(slot)) {
                continue;
            }
            var snapshot = readSnapshot(stateTag);
            if (snapshot != null) {
                states.putIfAbsent(slot, snapshot);
            }
        }
        return states;
    }

    boolean finishLoad(
            @Nullable ServerLevel level,
            BlockPos providerPos,
            AppEngInternalInventory patternInventory,
            OverloadedProviderPatternCatalog patternCatalog,
            ProviderNormalDispatch normalDispatch,
            List<Direction> activeNormalDirections,
            List<WirelessConnection> wirelessConnections) {
        var state = pending;
        if (state == null || level == null) {
            return false;
        }

        var validPatterns = new HashMap<
                Integer, appeng.api.crafting.IPatternDetails>();
        state.patterns().forEach((slot, savedStack) -> {
            if (slot < 0 || slot >= patternInventory.size()) {
                return;
            }
            var currentStack = patternInventory.getStackInSlot(slot);
            var pattern = patternCatalog.patternAtSlot(slot);
            if (pattern != null
                    && ItemStack.isSameItemSameComponents(
                            savedStack, currentStack)) {
                validPatterns.put(slot, pattern);
            }
        });

        for (var targetState : state.targets()) {
            ProviderTarget target;
            if (targetState.normalDirection() != null) {
                if (!activeNormalDirections.contains(
                        targetState.normalDirection())) {
                    continue;
                }
                target = normalDispatch.target(
                        level, providerPos, targetState.normalDirection());
            } else {
                target = findWirelessTarget(
                        wirelessConnections, targetState.wirelessAddress());
                if (target == null) {
                    continue;
                }
            }
            targetState.states().forEach((slot, snapshot) -> {
                var pattern = validPatterns.get(slot);
                if (pattern != null) {
                    target.restoreAdaptiveBatchSnapshot(pattern, snapshot);
                }
            });
        }
        pending = null;
        return true;
    }

    @Nullable
    private static WirelessConnection findWirelessTarget(
            List<WirelessConnection> connections,
            @Nullable WirelessConnection address) {
        if (address == null) {
            return null;
        }
        for (var connection : connections) {
            if (connection.equals(address)) {
                return connection;
            }
        }
        return null;
    }

    void clear() {
        pending = null;
    }

    static CompoundTag writeSnapshot(
            ProviderTarget.AdaptiveBatchSnapshot snapshot) {
        var tag = new CompoundTag();
        if (snapshot.rememberedChunk() > 0) {
            tag.putInt(
                    TAG_REMEMBERED_CHUNK, snapshot.rememberedChunk());
        }
        var step = snapshot.step();
        if (step != null) {
            var stepTag = new CompoundTag();
            stepTag.putInt(TAG_NEXT_CHUNK, step.nextChunk());
            stepTag.putInt(TAG_PROVEN_CHUNK, step.provenChunk());
            stepTag.putInt(
                    TAG_PROVEN_SUCCESSES, step.provenSuccesses());
            stepTag.putBoolean(TAG_REPEAT_CURRENT, step.repeatCurrent());
            stepTag.putBoolean(TAG_GROWTH_CAPPED, step.growthCapped());
            stepTag.putBoolean(TAG_BACKING_OFF, step.backingOff());
            stepTag.putLong(
                    TAG_LAST_SUCCESSFUL_TICK,
                    step.lastSuccessfulTick());
            stepTag.putLong(
                    TAG_LAST_ATTEMPT_TICK, step.lastAttemptTick());
            stepTag.putBoolean(TAG_RESERVOIR_MODE, step.reservoirMode());
            stepTag.putInt(TAG_RESERVOIR_TAIL_LOWER, step.reservoirTailLower());
            stepTag.putInt(TAG_RESERVOIR_TAIL_UPPER, step.reservoirTailUpperExclusive());
            stepTag.putBoolean(TAG_RESERVOIR_TAIL_SUPPRESSED, step.reservoirTailSuppressed());
            stepTag.putLong(TAG_LAST_TAIL_ATTEMPT, step.lastReservoirTailAttemptTick());
            tag.put(TAG_STEP, stepTag);
        }
        return tag;
    }

    @Nullable
    static ProviderTarget.AdaptiveBatchSnapshot readSnapshot(
            CompoundTag tag) {
        int rememberedChunk = com.moakiee.ae2lt.recipe.compat.LegacyNbtTypes.contains(tag, TAG_REMEMBERED_CHUNK, Tag.TAG_INT)
                        ? tag.getIntOr(TAG_REMEMBERED_CHUNK, 0)
                        : 0;
        ProviderTarget.BatchStepSnapshot step = null;
        if (com.moakiee.ae2lt.recipe.compat.LegacyNbtTypes.contains(tag, TAG_STEP, Tag.TAG_COMPOUND)) {
            var stepTag = tag.getCompoundOrEmpty(TAG_STEP);
            step = new ProviderTarget.BatchStepSnapshot(
                    stepTag.getIntOr(TAG_NEXT_CHUNK, 0),
                    stepTag.getIntOr(TAG_PROVEN_CHUNK, 0),
                    stepTag.getIntOr(TAG_PROVEN_SUCCESSES, 0),
                    stepTag.getBooleanOr(TAG_REPEAT_CURRENT, false),
                    stepTag.getBooleanOr(TAG_GROWTH_CAPPED, false),
                    stepTag.getBooleanOr(TAG_BACKING_OFF, false),
                    stepTag.getLongOr(TAG_LAST_SUCCESSFUL_TICK, 0L),
                    stepTag.getLongOr(TAG_LAST_ATTEMPT_TICK, 0L),
                    stepTag.getBooleanOr(TAG_RESERVOIR_MODE, false),
                    stepTag.getIntOr(TAG_RESERVOIR_TAIL_LOWER, 0),
                    stepTag.getIntOr(TAG_RESERVOIR_TAIL_UPPER, 0),
                    stepTag.getBooleanOr(TAG_RESERVOIR_TAIL_SUPPRESSED, false),
                    com.moakiee.ae2lt.recipe.compat.LegacyNbtTypes.contains(stepTag, TAG_LAST_TAIL_ATTEMPT, Tag.TAG_LONG)
                            ? stepTag.getLongOr(TAG_LAST_TAIL_ATTEMPT, 0L) : Long.MIN_VALUE);
        }
        var snapshot = new ProviderTarget.AdaptiveBatchSnapshot(
                rememberedChunk, step);
        return snapshot.isValid() ? snapshot : null;
    }

    private record PendingState(
            Map<Integer, ItemStack> patterns,
            List<PendingTarget> targets) {
    }

    private record PendingTarget(
            @Nullable Direction normalDirection,
            @Nullable WirelessConnection wirelessAddress,
            Map<Integer, ProviderTarget.AdaptiveBatchSnapshot> states) {
        private static PendingTarget normal(
                Direction direction,
                Map<Integer, ProviderTarget.AdaptiveBatchSnapshot> states) {
            return new PendingTarget(direction, null, Map.copyOf(states));
        }

        private static PendingTarget wireless(
                WirelessConnection address,
                Map<Integer, ProviderTarget.AdaptiveBatchSnapshot> states) {
            return new PendingTarget(null, address, Map.copyOf(states));
        }
    }
}
