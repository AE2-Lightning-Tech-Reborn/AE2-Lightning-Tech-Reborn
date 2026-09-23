package com.moakiee.ae2lt.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.stacks.GenericStack;
import appeng.api.crafting.PatternDetailsHelper;

import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.WirelessConnection;
import com.moakiee.ae2lt.logic.WirelessOverflowQueue.Bucket;

/** Stable NBT codec and deferred pattern decoding for wireless overflow. */
final class WirelessOverflowPersistence {
    private static final String TAG_W_SEND_LIST = "WirelessSendList";
    private static final String TAG_W_SEND_CONN = "WirelessSendConn";
    private static final String TAG_WIRELESS_OVERFLOW = "ae2lt:wireless_overflow";
    private static final String TAG_OVERFLOW_PATTERNS = "patterns";
    private static final String TAG_OVERFLOW_PATTERN_ID = "id";
    private static final String TAG_OVERFLOW_PATTERN = "pattern";
    private static final String TAG_OVERFLOW_BUCKETS = "buckets";
    private static final String TAG_OVERFLOW_CONN = "conn";
    private static final String TAG_OVERFLOW_PID = "pid";
    private static final String TAG_OVERFLOW_IDX = "idx";
    private static final String TAG_OVERFLOW_REMAINING = "remaining";
    private static final String TAG_OVERFLOW_FALLBACK = "fallback";
    private static final String TAG_OVERFLOW_FACE = "ae2lt:face";
    private static final String TAG_OVERFLOW_COMPACT = "compact";

    private final Map<Integer, ItemStack> pendingPatternDefinitions =
            new HashMap<>();
    private final List<PendingBucketLoad> pendingBuckets = new ArrayList<>();

    void write(
            CompoundTag tag,
            HolderLookup.Provider registries,
            WirelessOverflowQueue overflow) {
        if (overflow.isEmpty()) {
            return;
        }

        var overflowTag = new CompoundTag();
        var patternList = new ListTag();
        var remappedIds = new HashMap<Integer, Short>();
        short nextWriteId = 0;

        for (var bucket : overflow.buckets()) {
            if (!bucket.compactMode) {
                continue;
            }
            int runtimeId = Short.toUnsignedInt(bucket.patternId);
            if (remappedIds.containsKey(runtimeId)) {
                continue;
            }
            var pattern = overflow.pattern(runtimeId);
            if (pattern == null) {
                continue;
            }

            short writeId = nextWriteId++;
            remappedIds.put(runtimeId, writeId);
            var patternTag = new CompoundTag();
            patternTag.putShort(TAG_OVERFLOW_PATTERN_ID, writeId);
            patternTag.put(
                    TAG_OVERFLOW_PATTERN,
                    com.moakiee.ae2lt.recipe.compat.LegacyItemStackNbt.save(pattern.getDefinition().toStack(), registries));
            patternList.add(patternTag);
        }
        overflowTag.put(TAG_OVERFLOW_PATTERNS, patternList);

        var bucketList = new ListTag();
        for (var connection : overflow.connections()) {
            var bucket = overflow.get(connection);
            if (bucket == null) {
                continue;
            }
            var bucketTag = new CompoundTag();
            bucketTag.put(TAG_OVERFLOW_CONN, connection.toTag());
            bucketTag.putBoolean(TAG_OVERFLOW_COMPACT, bucket.compactMode);
            if (bucket.compactMode) {
                var remapped = remappedIds.get(
                        Short.toUnsignedInt(bucket.patternId));
                if (remapped == null) {
                    continue;
                }
                bucketTag.putShort(TAG_OVERFLOW_PID, remapped);
                bucketTag.putShort(TAG_OVERFLOW_IDX, bucket.stuckIndex);
                bucketTag.putLong(TAG_OVERFLOW_REMAINING, bucket.remaining);
            } else {
                bucketTag.put(
                        TAG_OVERFLOW_FALLBACK,
                        writeRoutedOverflow(bucket.fallback, registries));
            }
            bucketList.add(bucketTag);
        }
        overflowTag.put(TAG_OVERFLOW_BUCKETS, bucketList);
        tag.put(TAG_WIRELESS_OVERFLOW, overflowTag);
    }

    void read(CompoundTag tag, HolderLookup.Provider registries) {
        clear();
        if (!com.moakiee.ae2lt.recipe.compat.LegacyNbtTypes.contains(tag, TAG_WIRELESS_OVERFLOW, Tag.TAG_COMPOUND)) {
            readLegacy(tag, registries);
            return;
        }

        var overflowTag = tag.getCompoundOrEmpty(TAG_WIRELESS_OVERFLOW);
        var patterns = overflowTag.getListOrEmpty(
                TAG_OVERFLOW_PATTERNS);
        for (int i = 0; i < patterns.size(); i++) {
            var patternTag = patterns.getCompoundOrEmpty(i);
            int id = Short.toUnsignedInt(
                    patternTag.getShortOr(TAG_OVERFLOW_PATTERN_ID, (short) 0));
            var stack = com.moakiee.ae2lt.recipe.compat.LegacyItemStackNbt.parseOptional(
                    registries, patternTag.getCompoundOrEmpty(TAG_OVERFLOW_PATTERN));
            if (!stack.isEmpty()) {
                pendingPatternDefinitions.put(id, stack);
            }
        }

        var buckets = overflowTag.getListOrEmpty(
                TAG_OVERFLOW_BUCKETS);
        for (int i = 0; i < buckets.size(); i++) {
            var bucketTag = buckets.getCompoundOrEmpty(i);
            if (!com.moakiee.ae2lt.recipe.compat.LegacyNbtTypes.contains(bucketTag, TAG_OVERFLOW_CONN, Tag.TAG_COMPOUND)) {
                continue;
            }
            var connection = WirelessConnection.fromTag(
                    bucketTag.getCompoundOrEmpty(TAG_OVERFLOW_CONN));
            if (bucketTag.getBooleanOr(TAG_OVERFLOW_COMPACT, false)) {
                pendingBuckets.add(new PendingBucketLoad(
                        connection,
                        bucketTag.getShortOr(TAG_OVERFLOW_PID, (short) 0),
                        bucketTag.getShortOr(TAG_OVERFLOW_IDX, (short) 0),
                        bucketTag.getLongOr(TAG_OVERFLOW_REMAINING, 0L),
                        List.of(),
                        true));
                continue;
            }

            var fallback = readRoutedOverflow(
                    registries,
                    bucketTag.getListOrEmpty(
                            TAG_OVERFLOW_FALLBACK));
            if (!fallback.isEmpty()) {
                pendingBuckets.add(new PendingBucketLoad(
                        connection, (short) 0, (short) 0, 0L,
                        fallback, false));
            }
        }
    }

    boolean finishLoad(
            Level level,
            long gameTick,
            WirelessOverflowQueue overflow,
            Function<WirelessConnection, WirelessConnection> connectionResolver,
            Consumer<WirelessConnection> restoredConnection) {
        if (pendingBuckets.isEmpty()) {
            return false;
        }
        if (!pendingPatternDefinitions.isEmpty() && level == null) {
            return false;
        }

        for (var entry : pendingPatternDefinitions.entrySet()) {
            var details = PatternDetailsHelper.decodePattern(
                    entry.getValue(), level);
            if (details != null) {
                overflow.restorePattern(entry.getKey(), details);
            }
        }

        for (var pending : pendingBuckets) {
            Bucket bucket;
            if (pending.compactMode()) {
                var pattern = overflow.pattern(
                        Short.toUnsignedInt(pending.patternId()));
                if (pattern == null || pending.remaining() <= 0L) {
                    continue;
                }
                var inputs = pattern.getInputs();
                if (pending.stuckIndex() < 0
                        || pending.stuckIndex() >= inputs.length) {
                    continue;
                }
                bucket = Bucket.compact(
                        pending.patternId(),
                        pending.stuckIndex(),
                        pending.remaining());
            } else {
                if (pending.fallback().isEmpty()) {
                    continue;
                }
                bucket = Bucket.routedFallback(
                        pending.patternId(), pending.fallback());
            }
            var connection = connectionResolver.apply(pending.connection());
            overflow.restoreBucket(connection, bucket, gameTick);
            restoredConnection.accept(connection);
        }

        clear();
        return true;
    }

    void clear() {
        pendingPatternDefinitions.clear();
        pendingBuckets.clear();
    }

    static ListTag writeRoutedOverflow(
            RoutedPatternOverflow overflow,
            HolderLookup.Provider registries) {
        var list = new ListTag();
        for (var entry : overflow.snapshot()) {
            var stackTag = com.moakiee.ae2lt.recipe.compat.LegacyAeStackTags.writeGeneric(registries, entry.stack());
            if (entry.face() != null) {
                stackTag.putByte(
                        TAG_OVERFLOW_FACE,
                        (byte) entry.face().get3DDataValue());
            }
            list.add(stackTag);
        }
        return list;
    }

    static List<RoutedPatternOverflow.Entry> readRoutedOverflow(
            HolderLookup.Provider registries,
            ListTag list) {
        var entries = new ArrayList<RoutedPatternOverflow.Entry>(list.size());
        for (int i = 0; i < list.size(); i++) {
            var stackTag = list.getCompoundOrEmpty(i);
            var stack = com.moakiee.ae2lt.recipe.compat.LegacyAeStackTags.readGeneric(registries, stackTag);
            if (stack == null || stack.amount() <= 0L) {
                continue;
            }

            Direction face = null;
            if (com.moakiee.ae2lt.recipe.compat.LegacyNbtTypes.contains(stackTag, TAG_OVERFLOW_FACE, Tag.TAG_BYTE)) {
                int faceId = stackTag.getByteOr(TAG_OVERFLOW_FACE, (byte) 0);
                if (faceId >= 0 && faceId < Direction.values().length) {
                    face = Direction.from3DDataValue(faceId);
                }
            }
            entries.add(new RoutedPatternOverflow.Entry(face, stack));
        }
        return entries;
    }

    private void readLegacy(
            CompoundTag tag, HolderLookup.Provider registries) {
        if (!com.moakiee.ae2lt.recipe.compat.LegacyNbtTypes.contains(tag, TAG_W_SEND_LIST, Tag.TAG_LIST)
                || !com.moakiee.ae2lt.recipe.compat.LegacyNbtTypes.contains(tag, TAG_W_SEND_CONN, Tag.TAG_COMPOUND)) {
            return;
        }
        var fallback = readGenericStackList(
                registries,
                tag.getListOrEmpty(TAG_W_SEND_LIST));
        if (!fallback.isEmpty()) {
            pendingBuckets.add(new PendingBucketLoad(
                    WirelessConnection.fromTag(
                            tag.getCompoundOrEmpty(TAG_W_SEND_CONN)),
                    (short) 0,
                    (short) 0,
                    0L,
                    toUnroutedOverflow(fallback),
                    false));
        }
    }

    private static List<RoutedPatternOverflow.Entry> toUnroutedOverflow(
            List<GenericStack> stacks) {
        var entries = new ArrayList<RoutedPatternOverflow.Entry>(stacks.size());
        for (var stack : stacks) {
            entries.add(new RoutedPatternOverflow.Entry(null, stack));
        }
        return entries;
    }

    private static List<GenericStack> readGenericStackList(
            HolderLookup.Provider registries, ListTag list) {
        var stacks = new ArrayList<GenericStack>(list.size());
        for (int i = 0; i < list.size(); i++) {
            var stack = com.moakiee.ae2lt.recipe.compat.LegacyAeStackTags.readGeneric(registries, list.getCompoundOrEmpty(i));
            if (stack != null && stack.amount() > 0L) {
                stacks.add(stack);
            }
        }
        return stacks;
    }

    private record PendingBucketLoad(
            WirelessConnection connection,
            short patternId,
            short stuckIndex,
            long remaining,
            List<RoutedPatternOverflow.Entry> fallback,
            boolean compactMode) {
    }
}
