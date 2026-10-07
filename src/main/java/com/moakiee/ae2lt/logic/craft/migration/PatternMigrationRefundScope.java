package com.moakiee.ae2lt.logic.craft.migration;

import appeng.api.storage.MEStorage;
import appeng.api.storage.IStorageProvider;
import appeng.api.implementations.blockentities.IChestOrDrive;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.parts.storagebus.StorageBusPart;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

/** A refund cannot re-enter a pattern warehouse through a storage bus. Applies to simulation and commit. */
public final class PatternMigrationRefundScope implements AutoCloseable {
    private static final ThreadLocal<Policy> CURRENT = new ThreadLocal<>();
    private final Policy previous;

    private PatternMigrationRefundScope(Policy policy) {
        previous = CURRENT.get();
        CURRENT.set(policy);
    }

    public static PatternMigrationRefundScope open(Policy policy) { return new PatternMigrationRefundScope(policy); }

    public static boolean permits(MEStorage inventory, IStorageProvider provider) {
        Policy policy = CURRENT.get();
        if (policy == null) return true;
        var known = policy.storageBuses.get(inventory);
        if (known != null) {
            try { return known.getAsBoolean(); } catch (RuntimeException e) { return false; }
        }
        if (provider instanceof StorageBusPart bus) return Policy.safeTarget(bus);
        // A provider certificate also catches third-party buses whose handler name contains no "StorageBus".
        // Only cell hosts are certified here. Unknown/global providers use the backpack fallback.
        return provider instanceof IChestOrDrive;
    }

    /** An exception in a storage notification can occur after the physical item was accepted. */
    public static long insert(MEStorage inventory, IStorageProvider provider, AEKey key, long amount, Actionable mode,
            IActionSource source, LongSupplier insertion) {
        if (!permits(inventory, provider)) return 0;
        if (CURRENT.get() == null || mode == Actionable.SIMULATE) return insertion.getAsLong();
        long before = inventory.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, source);
        try { return insertion.getAsLong(); }
        catch (RuntimeException failure) {
            long after = inventory.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, source);
            if (after >= before && after - before == amount) return amount;
            throw failure;
        }
    }

    @Override public void close() {
        if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
    }

    public static final class Policy {
        private final Map<MEStorage, BooleanSupplier> storageBuses = new IdentityHashMap<>();

        public void add(StorageBusPart bus) {
            bus.mountInventories((inventory, priority) -> storageBuses.put(inventory, () -> safeTarget(bus)));
        }

        private static boolean safeTarget(StorageBusPart bus) {
            var be = bus.getBlockEntity();
            if (be == null || be.isRemoved()) return false;
            var level = be.getLevel();
            var pos = be.getBlockPos().relative(bus.getSide());
            if (level == null || !level.isLoaded(pos)) return false;
            var target = level.getBlockEntity(pos);
            // Other endpoints may aggregate inventories or expose virtual slots. They require their own adapter.
            return target != null && (target.getClass() == ChestBlockEntity.class
                    || target.getClass() == BarrelBlockEntity.class || target.getClass() == ShulkerBoxBlockEntity.class);
        }
    }
}
