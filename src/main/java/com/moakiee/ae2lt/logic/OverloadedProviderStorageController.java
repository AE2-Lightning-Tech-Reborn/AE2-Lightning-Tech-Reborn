package com.moakiee.ae2lt.logic;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;

import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.mixin.PatternProviderLogicAccessor;

/** Large-provider SavedData lifecycle and lossless capacity-shrink recovery. */
final class OverloadedProviderStorageController {
    private static final String TAG_RESTORE_OVERFLOW =
            "ae2lt:restore_overflow";
    private final OverloadedPatternProviderBlockEntity host;
    private final PatternProviderLogicAccessor logic;
    private final int totalCapacity;
    private final List<GenericStack> pendingRestore = new ArrayList<>();
    private boolean needsSavedDataLoad;

    OverloadedProviderStorageController(
            OverloadedPatternProviderBlockEntity host,
            PatternProviderLogicAccessor logic,
            int totalCapacity) {
        this.host = host;
        this.logic = logic;
        this.totalCapacity = totalCapacity;
    }

    boolean hasPendingRestore() {
        return !pendingRestore.isEmpty();
    }

    void writeToNBT(
            CompoundTag tag, HolderLookup.Provider registries) {
        if (pendingRestore.isEmpty()) {
            return;
        }
        var list = new ListTag();
        for (var stack : pendingRestore) {
            list.add(com.moakiee.ae2lt.recipe.compat.LegacyAeStackTags.writeGeneric(registries, stack));
        }
        tag.put(TAG_RESTORE_OVERFLOW, list);
    }

    void readFromNBT(
            CompoundTag tag,
            HolderLookup.Provider registries,
            PatternProviderReturnInventory returnInventory) {
        needsSavedDataLoad = totalCapacity > 36
                && !hasPatternInventoryContents();
        pendingRestore.clear();
        salvageTruncatedNbtSlots(tag, registries, returnInventory);
        if (com.moakiee.ae2lt.recipe.compat.LegacyNbtTypes.contains(tag, TAG_RESTORE_OVERFLOW, Tag.TAG_LIST)) {
            pendingRestore.addAll(readGenericStackList(
                    registries,
                    tag.getListOrEmpty(TAG_RESTORE_OVERFLOW)));
        }
    }

    void drainPendingRestore(
            NetworkInserter networkInserter, Runnable saveChanges) {
        if (pendingRestore.isEmpty()) {
            return;
        }
        boolean changed = false;
        for (int i = 0; i < pendingRestore.size(); ) {
            var stack = pendingRestore.get(i);
            long remaining = networkInserter.insert(
                    stack.what(), stack.amount());
            if (remaining <= 0L) {
                pendingRestore.remove(i);
                changed = true;
                continue;
            }
            if (remaining != stack.amount()) {
                pendingRestore.set(
                        i, new GenericStack(stack.what(), remaining));
                changed = true;
            }
            i++;
        }
        if (changed) {
            saveChanges.run();
        }
    }

    void addDrops(List<ItemStack> drops) {
        for (var stack : pendingRestore) {
            stack.what().addDrops(
                    stack.amount(), drops, host.getLevel(), host.getBlockPos());
        }
        if (totalCapacity > 36) {
            removeSavedData();
        }
    }

    void clear() {
        if (totalCapacity > 36) {
            removeSavedData();
        }
        pendingRestore.clear();
    }

    boolean loadOnReady() {
        if (!needsSavedDataLoad) {
            return false;
        }
        needsSavedDataLoad = false;
        return loadFromSavedData();
    }

    void removeSavedData() {
        var level = host.getLevel();
        if (level instanceof ServerLevel serverLevel) {
            PatternStorageSavedData.get(serverLevel)
                    .remove(host.getBlockPos().asLong());
        }
    }

    private boolean loadFromSavedData() {
        var level = host.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            org.slf4j.LoggerFactory.getLogger("ae2lt").warn(
                    "[SavedData] loadFromSavedData skipped: level={} pos={}",
                    level, host.getBlockPos());
            return false;
        }
        var savedData = PatternStorageSavedData.get(serverLevel);
        var stored = savedData.get(host.getBlockPos().asLong());
        if (stored == null) {
            org.slf4j.LoggerFactory.getLogger("ae2lt").info(
                    "[SavedData] No stored data for pos={}", host.getBlockPos());
            return false;
        }
        org.slf4j.LoggerFactory.getLogger("ae2lt").info(
                "[SavedData] Loaded {} patterns for pos={}",
                stored.length, host.getBlockPos());

        var inventory = logic.getPatternInventory();
        int limit = Math.min(stored.length, inventory.size());
        for (int i = 0; i < limit; i++) {
            inventory.setItemDirect(
                    i, stored[i] != null ? stored[i] : ItemStack.EMPTY);
        }

        int salvaged = 0;
        for (int i = limit; i < stored.length; i++) {
            var stack = stored[i];
            var key = stack != null ? AEItemKey.of(stack) : null;
            if (key != null && !stack.isEmpty()) {
                pendingRestore.add(
                        new GenericStack(key, stack.getCount()));
                salvaged++;
            }
        }
        if (salvaged > 0) {
            org.slf4j.LoggerFactory.getLogger("ae2lt").warn(
                    "[SavedData] Capacity at {} smaller than stored data; salvaged {} patterns into the restore queue",
                    host.getBlockPos(), salvaged);
        }
        savedData.remove(host.getBlockPos().asLong());
        return true;
    }

    private boolean hasPatternInventoryContents() {
        var inventory = logic.getPatternInventory();
        for (int i = 0; i < inventory.size(); i++) {
            if (!inventory.getStackInSlot(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void salvageTruncatedNbtSlots(
            CompoundTag tag,
            HolderLookup.Provider registries,
            PatternProviderReturnInventory returnInventory) {
        int salvaged = 0;

        int patternInventorySize = logic.getPatternInventory().size();
        var patternsTag = tag.getListOrEmpty(
                PatternProviderLogic.NBT_MEMORY_CARD_PATTERNS);
        for (int i = 0; i < patternsTag.size(); i++) {
            var itemTag = patternsTag.getCompoundOrEmpty(i);
            if (itemTag.getIntOr("Slot", 0) < patternInventorySize) {
                continue;
            }
            var stack = com.moakiee.ae2lt.recipe.compat.LegacyItemStackNbt.parseOptional(registries, itemTag);
            var key = AEItemKey.of(stack);
            if (key != null && !stack.isEmpty()) {
                pendingRestore.add(
                        new GenericStack(key, stack.getCount()));
                salvaged++;
            }
        }

        var returnTag = tag.getListOrEmpty(
                PatternProviderLogic.NBT_RETURN_INV);
        for (int i = returnInventory.size(); i < returnTag.size(); i++) {
            var stack = com.moakiee.ae2lt.recipe.compat.LegacyAeStackTags.readGeneric(
                    registries, returnTag.getCompoundOrEmpty(i));
            if (stack != null && stack.amount() > 0L) {
                pendingRestore.add(stack);
                salvaged++;
            }
        }

        if (salvaged > 0) {
            org.slf4j.LoggerFactory.getLogger("ae2lt").warn(
                    "Pattern provider at {} shrank below its saved size; salvaged {} stacks into the restore queue",
                    host.getBlockPos(), salvaged);
        }
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

    @FunctionalInterface
    interface NetworkInserter {
        long insert(AEKey what, long amount);
    }
}
