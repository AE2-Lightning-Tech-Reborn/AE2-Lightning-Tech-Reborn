package com.moakiee.ae2lt.logic.craft.migration;

import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.blockentity.crafting.MolecularAssemblerBlockEntity;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.util.inv.AppEngInternalInventory;
import com.moakiee.ae2lt.blockentity.MatrixPatternStorageBlockEntity;
import com.moakiee.ae2lt.blockentity.MatrixPortBlockEntity;
import com.moakiee.ae2lt.mixin.PatternProviderLogicAccessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Explicit optional APIs take precedence over the terminal-view fallback. No optional type is linked here. */
final class PatternMigrationSources {
    static final String EAE_PATTERN = "com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixPattern";
    static final String EAEP_MATRIX = "com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixBlockEntity";
    static final String EAEP_CORE = "com.extendedae_plus.content.matrix.PatternCorePlusBlockEntity";
    static final String ECO_BUS = "cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity";
    static final String EAE_ASSEMBLER = "com.glodblock.github.extendedae.common.tileentities.TileExMolecularAssembler";
    private final Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());

    static int priority(Class<?> type) {
        if (MigrationReflection.isType(type, EAE_PATTERN) || MigrationReflection.isType(type, EAEP_MATRIX)
                || MigrationReflection.isType(type, ECO_BUS)) return 0;
        if (MatrixPatternStorageBlockEntity.class.isAssignableFrom(type)
                || MolecularAssemblerBlockEntity.class.isAssignableFrom(type)) return 1;
        if (PatternProviderLogicHost.class.isAssignableFrom(type)) return 2;
        if (PatternContainer.class.isAssignableFrom(type)) return 3;
        return -1;
    }

    /** Activity-only pass: no pattern catalog, inventory view or decoding is needed to certify idle. */
    static BooleanSupplier executionProbe(Object owner) {
        Class<?> type = owner.getClass();
        if (MigrationReflection.isType(type, EAEP_MATRIX)) {
            Object cluster = MigrationReflection.call(owner, "eap$getSuperMatrixCluster");
            return cluster == null ? null : () -> MigrationReflection.call(owner, "eap$getSuperMatrixCluster") == cluster
                    && Boolean.TRUE.equals(MigrationReflection.call(cluster, "hasPendingWork"));
        }
        if (MigrationReflection.isType(type, EAE_PATTERN)) {
            Object superCluster = MigrationReflection.hasMethod(owner, "eap$getSuperMatrixCluster")
                    ? MigrationReflection.call(owner, "eap$getSuperMatrixCluster") : null;
            Object cluster = superCluster != null ? superCluster : MigrationReflection.call(owner, "getCluster");
            return cluster == null ? null : superCluster != null
                    ? () -> MigrationReflection.call(owner, "eap$getSuperMatrixCluster") == cluster
                            && Boolean.TRUE.equals(MigrationReflection.call(cluster, "hasPendingWork"))
                    : () -> MigrationReflection.call(owner, "getCluster") == cluster && eaeBusy(cluster);
        }
        if (MigrationReflection.isType(type, ECO_BUS)) {
            Object controller = MigrationReflection.call(owner, "getCraftingController");
            return controller == null ? null : () -> MigrationReflection.call(owner, "getCraftingController") == controller
                    && ((Number) MigrationReflection.call(controller, "getRunningThreadCount")).longValue() > 0;
        }
        if (MigrationReflection.isType(type, EAE_ASSEMBLER)) return () -> eaeCrafterBusy(owner);
        if (owner instanceof MatrixPatternStorageBlockEntity storage) {
            var pos = storage.getControllerPos();
            var level = storage.getLevel();
            if (pos == null || level == null || !level.isLoaded(pos)
                    || !(level.getBlockEntity(pos) instanceof com.moakiee.ae2lt.blockentity.MatrixControllerBlockEntity matrix)) return null;
            return matrix::hasPendingMigrationWork;
        }
        if (owner instanceof MolecularAssemblerBlockEntity assembler) {
            return () -> {
                var inventory = assembler.getInternalInventory();
                for (int slot = 0; slot < 10; slot++) if (!inventory.getStackInSlot(slot).isEmpty()) return true;
                return assembler.getCraftingProgress() > 0;
            };
        }
        if (owner instanceof PatternProviderLogicHost host) {
            return () -> host.getLogic().isBusy() || !host.getLogic().getReturnInv().isEmpty();
        }
        return null;
    }

    List<PatternMigrationSource> discover(IGridNode node, MatrixPortBlockEntity target) {
        Object owner = node.getOwner();
        if (owner == target || owner instanceof MatrixPatternStorageBlockEntity storage
                && storage.getLevel() == target.getLevel()
                && java.util.Objects.equals(storage.getControllerPos(), target.getControllerPos())) return List.of();
        var be = PatternMigrationSource.blockEntity(owner);
        if (be == null || be.getLevel() == null) return List.of();
        Class<?> type = owner.getClass();

        if (MigrationReflection.isType(type, EAEP_MATRIX)) {
            Object cluster = MigrationReflection.call(owner, "eap$getSuperMatrixCluster");
            if (cluster == null) return List.of();
            var result = new ArrayList<PatternMigrationSource>();
            for (Object host : (List<?>) MigrationReflection.call(cluster, "getPatternCores")) {
                addEaePattern(host, node, result);
            }
            return result;
        }
        if (MigrationReflection.isType(type, EAE_PATTERN)) {
            var result = new ArrayList<PatternMigrationSource>();
            addEaePattern(owner, node, result);
            return result;
        }
        if (!seen.add(owner)) return List.of();

        if (MigrationReflection.isType(type, ECO_BUS)) {
            var inventory = (InternalInventory) MigrationReflection.call(owner, "getTerminalPatternInventory");
            var physical = MigrationReflection.field(owner, "inventory");
            if (!(physical instanceof AppEngInternalInventory)
                    || !inventory.getClass().getName().equals(ECO_BUS + "$EffectivePatternInventory")) {
                throw new IllegalStateException("No certified ECO physical inventory adapter");
            }
            // NeoECO 20.x batches catalog refreshes with its own PatternBusUpdateScheduler.
            // Resolve the exact methods now; a mismatched version fails before any extraction.
            Object originalController = MigrationReflection.call(owner, "getCraftingController");
            if (originalController == null) return List.of();
            var source = source(owner, node, inventory,
                    () -> MigrationReflection.call(owner, "getCraftingController") == originalController
                            && MigrationReflection.field(owner, "inventory") == physical
                            ? (InternalInventory) MigrationReflection.call(owner, "getTerminalPatternInventory") : null,
                    () -> {
                        Object controller = MigrationReflection.call(owner, "getCraftingController");
                        return controller == null || ((Number) MigrationReflection.call(controller, "getRunningThreadCount")).longValue() > 0;
                    }, 0, 0, inventory.size(), false,
                    () -> {}, () -> {});
            return List.of(source);
        }
        if (owner instanceof MatrixPatternStorageBlockEntity storage) {
            var inventory = storage.getTerminalPatternInventory();
            return List.of(source(owner, node, inventory, storage::getTerminalPatternInventory,
                    () -> {
                        var pos = storage.getControllerPos();
                        if (pos == null || !storage.getLevel().isLoaded(pos)) return true;
                        var controller = storage.getLevel().getBlockEntity(pos);
                        return !(controller instanceof com.moakiee.ae2lt.blockentity.MatrixControllerBlockEntity matrix)
                                || matrix.hasPendingMigrationWork();
                    }, 1, 0, inventory.size(), false, storage::beginPatternBatch, storage::endPatternBatch));
        }
        if (owner instanceof MolecularAssemblerBlockEntity assembler) {
            var inventory = assembler.getInternalInventory();
            if (inventory.size() != 11) throw new IllegalStateException("Unsupported assembler slot layout");
            return List.of(source(owner, node, inventory, assembler::getInternalInventory,
                    () -> {
                        for (int slot = 0; slot < 10; slot++) if (!inventory.getStackInSlot(slot).isEmpty()) return true;
                        return assembler.getCraftingProgress() > 0;
                    }, 1, 10, 1, false, () -> {}, () -> {}));
        }
        if (owner instanceof PatternProviderLogicHost host) {
            var logic = host.getLogic();
            var inventory = logic.getPatternInv();
            var cached = ((PatternProviderLogicAccessor) logic).getPatterns();
            boolean hint = cached.stream().limit(32).anyMatch(pattern -> pattern instanceof IMolecularAssemblerSupportedPattern);
            return List.of(source(owner, node, inventory, () -> host.getLogic().getPatternInv(),
                    () -> host.getLogic().isBusy() || !host.getLogic().getReturnInv().isEmpty(),
                    2, 0, inventory.size(), hint, () -> {}, () -> {}));
        }
        if (owner instanceof PatternContainer container) {
            var inventory = container.getTerminalPatternInventory();
            // Subclasses and custom views can synthesize recipe rows, even when extraction simulates successfully.
            if (inventory.getClass() != AppEngInternalInventory.class) {
                throw new IllegalStateException("No certified physical inventory adapter");
            }
            if (node.getService(appeng.api.networking.crafting.ICraftingProvider.class) != null
                    || owner instanceof appeng.api.networking.crafting.ICraftingProvider) {
                throw new IllegalStateException("No certified execution-state adapter");
            }
            return List.of(source(owner, node, inventory, container::getTerminalPatternInventory,
                    () -> false, 3, 0, inventory.size(), false, () -> {}, () -> {}));
        }
        return List.of();
    }

    private void addEaePattern(Object owner, IGridNode anchor, List<PatternMigrationSource> result) {
        if (seen.contains(owner)) return;
        var inventory = (InternalInventory) MigrationReflection.call(owner, "getPatternInventory");
        Object superCluster = MigrationReflection.hasMethod(owner, "eap$getSuperMatrixCluster")
                ? MigrationReflection.call(owner, "eap$getSuperMatrixCluster") : null;
        int limit = inventory.size();
        BooleanSupplier busy;
        if (superCluster != null) {
            // Forge EAEP exposes the physical hybrid cores directly, with their full inventories.
            if (!((List<?>) MigrationReflection.call(superCluster, "getPatternCores")).contains(owner)) return;
            busy = () -> Boolean.TRUE.equals(MigrationReflection.call(superCluster, "hasPendingWork"));
        } else {
            Object cluster = MigrationReflection.call(owner, "getCluster");
            busy = () -> cluster == null || eaeBusy(cluster);
        }
        busy.getAsBoolean(); // Validate the optional activity API before accepting this source.
        seen.add(owner);
        Object originalCluster = superCluster != null ? superCluster : MigrationReflection.call(owner, "getCluster");
        if (originalCluster == null) return;
        boolean hint = false;
        if (MigrationReflection.hasField(owner, "patterns")) {
            var cached = (List<?>) MigrationReflection.field(owner, "patterns");
            hint = cached.stream().limit(32).anyMatch(pattern -> pattern instanceof IMolecularAssemblerSupportedPattern);
        }
        result.add(source(owner, anchor, inventory,
                () -> {
                    Object now = superCluster != null ? MigrationReflection.call(owner, "eap$getSuperMatrixCluster")
                            : MigrationReflection.call(owner, "getCluster");
                    return now == originalCluster ? (InternalInventory) MigrationReflection.call(owner, "getPatternInventory") : null;
                },
                busy, 0, 0, Math.min(limit, inventory.size()), hint, () -> {}, () -> {}));
    }

    private static boolean eaeBusy(Object cluster) {
        if (((Number) MigrationReflection.call(cluster, "getBusyCrafterAmount")).intValue() > 0) return true;
        // EAE only marks a crafter full when its output buffer exceeds a threshold. Even one pending
        // output must prevent migration. These sets already exist; no pattern inventory is decoded here.
        for (String name : List.of("availableCrafters", "busyCrafters")) {
            for (Object crafter : (Iterable<?>) MigrationReflection.field(cluster, name)) {
                if (eaeCrafterBusy(crafter)) return true;
            }
        }
        return false;
    }

    static boolean eaeCrafterBusy(Object crafter) {
        if (((Number) MigrationReflection.field(crafter, "states")).intValue() != 0) return true;
        // Older EAE keeps undelivered outputs in the thread inventory; newer versions add a buffer.
        var inventory = (InternalInventory) MigrationReflection.field(crafter, "internalInv");
        for (int slot = 0; slot < inventory.size(); slot++) if (!inventory.getStackInSlot(slot).isEmpty()) return true;
        return MigrationReflection.hasField(crafter, "outputBuffer")
                && ((Number) MigrationReflection.field(MigrationReflection.field(crafter, "outputBuffer"), "size")).longValue() > 0;
    }

    private static PatternMigrationSource source(Object owner, IGridNode anchor, InternalInventory inventory,
            Supplier<InternalInventory> current, BooleanSupplier busy, int priority, int first, int count,
            boolean hint, Runnable begin, Runnable end) {
        return new PatternMigrationSource(owner, anchor, inventory, current, busy, priority, first, count, hint, begin, end);
    }
}
