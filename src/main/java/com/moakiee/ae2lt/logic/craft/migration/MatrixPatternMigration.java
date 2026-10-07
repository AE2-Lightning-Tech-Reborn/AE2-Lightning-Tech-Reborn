package com.moakiee.ae2lt.logic.craft.migration;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.parts.storagebus.StorageBusPart;
import com.moakiee.ae2lt.blockentity.MatrixControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.MatrixPatternStorageBlockEntity;
import com.moakiee.ae2lt.blockentity.MatrixPortBlockEntity;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.ConcurrentModificationException;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Controller-owned, finite migration. Closing a menu never owns or cancels its server-thread task. */
public final class MatrixPatternMigration {
    private static final String RECOVERY_TAG = "PatternMigrationRecovery";
    private final MatrixControllerBlockEntity controller;
    private ItemStack recovery = ItemStack.EMPTY;
    private Task task;
    private PatternMigrationSnapshot report = PatternMigrationSnapshot.IDLE;

    public MatrixPatternMigration(MatrixControllerBlockEntity controller) { this.controller = controller; }

    public PatternMigrationSnapshot snapshot() { return report; }

    public void start(ServerPlayer player) {
        if (task != null) return; // Idempotent start packets cannot turn into a stop.
        recover(player);
        if (!recovery.isEmpty()) { reject(PatternMigrationSnapshot.Reason.RECOVERY); return; }
        MatrixPortBlockEntity port = port();
        if (port == null || !port.isLinkConnected()) { reject(PatternMigrationSnapshot.Reason.NO_NETWORK); return; }
        IGrid grid = port.getGrid();
        if (cpuBusy(grid) || controller.hasPendingMigrationWork()) { reject(PatternMigrationSnapshot.Reason.BUSY); return; }
        Task next;
        try { next = new Task(port, grid, player.getUUID()); }
        catch (RuntimeException e) { reject(PatternMigrationSnapshot.Reason.ADAPTER_FAILED); return; }
        if (!next.budget.acquire(grid, next)) { reject(PatternMigrationSnapshot.Reason.LEASE_BUSY); return; }
        try {
            next.ecoLease = EcoMigrationLease.acquire(grid, next);
            if (next.ecoLease == null) {
                next.release();
                reject(PatternMigrationSnapshot.Reason.LEASE_BUSY);
                return;
            }
            task = next;
            report = next.snapshot();
        } catch (RuntimeException e) {
            next.release();
            reject(PatternMigrationSnapshot.Reason.ADAPTER_FAILED);
        }
    }

    private void reject(PatternMigrationSnapshot.Reason reason) {
        report = new PatternMigrationSnapshot(PatternMigrationSnapshot.Stage.REJECTED, reason,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    public void stop(PatternMigrationSnapshot.Reason reason) {
        if (task == null) return;
        task.stage = PatternMigrationSnapshot.Stage.STOPPED;
        task.reason = reason;
        report = task.snapshot();
        task.release();
        task = null;
    }

    public void tick() {
        if (task == null || !(controller.getLevel() instanceof ServerLevel level)) return;
        Task current = task;
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(current.playerId);
        if (player == null) { stop(PatternMigrationSnapshot.Reason.OFFLINE); return; }
        if (!controller.isPersistentStateOwner() || port() != current.port || !current.port.isLinkConnected()
                || current.port.getGrid() != current.grid
                || !current.storages.equals(current.port.getPatternStorages())
                || current.storages.stream().anyMatch(storage -> storage.isRemoved() || storage.getLevel() == null
                        || !storage.getLevel().isLoaded(storage.getBlockPos())
                        || storage.getLevel().getBlockEntity(storage.getBlockPos()) != storage)) {
            stop(PatternMigrationSnapshot.Reason.TARGET_CHANGED);
            return;
        }
        if (!current.budget.isOwner(current.grid, current) || !current.ecoLease.owned()) {
            stop(PatternMigrationSnapshot.Reason.LEASE_BUSY);
            return;
        }
        long started = System.nanoTime();
        long deadline = current.budget.begin(level.getServer().getTickCount(), current);
        if (deadline == 0) return;
        try {
            current.slice(player, deadline);
        } catch (ConcurrentModificationException e) {
            stop(PatternMigrationSnapshot.Reason.TARGET_CHANGED);
        } catch (RuntimeException e) {
            stop(recovery.isEmpty() ? PatternMigrationSnapshot.Reason.ADAPTER_FAILED : PatternMigrationSnapshot.Reason.RECOVERY);
        } finally {
            current.lastSliceNanos = System.nanoTime() - started;
            current.budget.record(current.lastSliceNanos);
            if (current.lastSliceNanos >= deadline - started) current.budgetHits++;
            report = current.snapshot();
            if (task == current && current.stage == PatternMigrationSnapshot.Stage.COMPLETE) {
                current.release();
                task = null;
                player.displayClientMessage(Component.translatable("ae2lt.matrix.migration.finished",
                        current.moved, current.recovered), true);
            }
        }
    }

    private MatrixPortBlockEntity port() {
        var level = controller.getLevel();
        var pos = controller.getPortPos();
        if (!controller.isFormed() || level == null || pos == null || !level.isLoaded(pos)) return null;
        return level.getBlockEntity(pos) instanceof MatrixPortBlockEntity port && port.getController() == controller ? port : null;
    }

    private static boolean cpuBusy(IGrid grid) {
        return grid.getCraftingService().getCpus().stream().anyMatch(cpu -> cpu.isBusy());
    }

    private static appeng.api.crafting.IPatternDetails decode(ItemStack stack, net.minecraft.world.level.Level level) {
        try { return PatternDetailsHelper.decodePattern(stack, level); }
        catch (RuntimeException | LinkageError malformed) { return null; }
    }

    /** Opening either authorized menu first attempts to hand back the exceptional in-flight item. */
    public void recover(ServerPlayer player) {
        if (task != null || recovery.isEmpty()) return;
        var port = port();
        if (port != null && port.isLinkConnected()) {
            // Until discovery certifies a bus destination, all bus handlers are excluded from a recovery refund.
            recovery = refund(player, port.getGrid(), new PatternMigrationRefundScope.Policy(), recovery);
        } else {
            var remainder = recovery.copy();
            insertBackpack(player, remainder);
            recovery = remainder;
        }
        controller.setChanged();
        if (recovery.isEmpty()) report = PatternMigrationSnapshot.IDLE;
    }

    public ItemStack recoveryStack() { return recovery.copy(); }

    public void dropRecovery() {
        if (recovery.isEmpty() || controller.getLevel() == null || controller.getLevel().isClientSide) return;
        var stack = recovery;
        custody(ItemStack.EMPTY);
        com.moakiee.ae2lt.util.NativeStackDropHelper.popResource(controller.getLevel(), controller.getBlockPos(), stack);
    }

    private void custody(ItemStack stack) {
        recovery = stack.copy();
        controller.setChanged();
    }

    public void writeTo(CompoundTag tag, HolderLookup.Provider registries) {
        if (!recovery.isEmpty()) tag.put(RECOVERY_TAG, recovery.save(registries));
    }

    public void readFrom(CompoundTag tag, HolderLookup.Provider registries) {
        stop(PatternMigrationSnapshot.Reason.TARGET_CHANGED);
        recovery = ItemStack.parseOptional(registries, tag.getCompound(RECOVERY_TAG));
        report = recovery.isEmpty() ? PatternMigrationSnapshot.IDLE : new PatternMigrationSnapshot(
                PatternMigrationSnapshot.Stage.STOPPED, PatternMigrationSnapshot.Reason.RECOVERY,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private static ItemStack refund(ServerPlayer player, IGrid grid, PatternMigrationRefundScope.Policy policy, ItemStack stack) {
        var remainder = stack.copy();
        try (var scope = PatternMigrationRefundScope.open(policy)) {
            long accepted = grid.getStorageService().getInventory().insert(AEItemKey.of(remainder), remainder.getCount(),
                    Actionable.MODULATE, IActionSource.ofPlayer(player));
            remainder.shrink((int) Math.max(0, Math.min(accepted, remainder.getCount())));
        }
        if (!remainder.isEmpty()) insertBackpack(player, remainder);
        return remainder;
    }

    /** Inventory.add discards a full-inventory remainder in creative mode. Migration must retain it. */
    private static void insertBackpack(ServerPlayer player, ItemStack remainder) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.items.size() && !remainder.isEmpty(); i++) {
            var existing = inventory.items.get(i);
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, remainder)) {
                int accepted = Math.min(remainder.getCount(), Math.max(0,
                        Math.min(existing.getMaxStackSize(), inventory.getMaxStackSize()) - existing.getCount()));
                existing.grow(accepted);
                remainder.shrink(accepted);
            }
        }
        for (int i = 0; i < inventory.items.size() && !remainder.isEmpty(); i++) {
            if (!inventory.items.get(i).isEmpty()) continue;
            int accepted = Math.min(remainder.getCount(), Math.min(remainder.getMaxStackSize(), inventory.getMaxStackSize()));
            inventory.items.set(i, remainder.copyWithCount(accepted));
            remainder.shrink(accepted);
        }
        inventory.setChanged();
    }

    private final class Task {
        final MatrixPortBlockEntity port;
        final IGrid grid;
        final UUID playerId;
        final List<MatrixPatternStorageBlockEntity> storages;
        final PatternMigrationBudget budget;
        final PatternMigrationSources adapters = new PatternMigrationSources();
        final List<PatternMigrationSource> sources = new ArrayList<>();
        final Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        final List<ActivityProbe> activities = new ArrayList<>();
        final Set<Object> activitySeen = Collections.newSetFromMap(new IdentityHashMap<>());
        int activityClass;
        Class<?> activityNodeClass;
        long activityNodeCursor;
        boolean activityDiscovered;
        final PatternMigrationRefundScope.Policy refundPolicy = new PatternMigrationRefundScope.Policy();
        final List<Class<?>> classes;
        int classIndex;
        int discoveryTier;
        int queuedSources;
        boolean discoveryFinished;
        int activityCursor;
        final PatternMigrationGridService roster;
        final long rosterEpoch;
        Class<?> nodeClass;
        long nodeCursor;
        final PatternMigrationQueue<PatternMigrationSource> queue = new PatternMigrationQueue<>(source -> source.count);
        final Map<AEItemKey, Integer> definitions = new HashMap<>();
        final ArrayDeque<TargetSlot> emptySlots = new ArrayDeque<>();
        final Map<MatrixPatternStorageBlockEntity, Long> revisions = new IdentityHashMap<>();
        EcoMigrationLease ecoLease;
        PatternMigrationSnapshot.Stage stage = PatternMigrationSnapshot.Stage.CHECKING;
        PatternMigrationSnapshot.Reason reason = PatternMigrationSnapshot.Reason.NONE;
        PatternMigrationSnapshot.Stage resumeStage;
        PatternMigrationQueue.Step<PatternMigrationSource> pending;
        boolean pendingCounted;
        boolean discovered;
        boolean indexed;
        int targetStorage;
        int targetSlot;
        int tier;
        long scanned, total, moved, recovered, incompatible, noSpace, refundBlocked, unavailable, unsupported, disks;
        long lastSliceNanos, budgetHits;

        Task(MatrixPortBlockEntity port, IGrid grid, UUID playerId) {
            this.port = port;
            this.grid = grid;
            this.playerId = playerId;
            storages = List.copyOf(port.getPatternStorages());
            budget = PatternMigrationBudget.forServer(controller.getLevel().getServer());
            roster = grid.getService(PatternMigrationGridService.class);
            if (roster == null) throw new IllegalStateException("Migration roster not registered");
            rosterEpoch = roster.epoch();
            var machineClasses = new ArrayList<Class<?>>();
            grid.getMachineClasses().forEach(machineClasses::add);
            machineClasses.sort(Comparator.comparingInt(type -> {
                int priority = PatternMigrationSources.priority(type);
                return priority < 0 ? 4 : priority;
            }));
            classes = List.copyOf(machineClasses);
        }

        void slice(ServerPlayer player, long deadline) {
            if (cpuBusy(grid) || controller.hasPendingMigrationWork()) {
                waitForIdle();
                return;
            }
            if (stage == PatternMigrationSnapshot.Stage.WAITING) { stage = resumeStage; reason = PatternMigrationSnapshot.Reason.NONE; }
            int checks = 0, decodes = 0;
            if (!activityDiscovered) {
                while (checks < PatternMigrationBudget.MAX_SLOTS && System.nanoTime() < deadline) {
                    if (activityNodeClass == null) {
                        if (activityClass >= classes.size()) { activityDiscovered = true; break; }
                        Class<?> type = classes.get(activityClass++);
                        if (PatternMigrationSources.priority(type) < 0
                                && !MigrationReflection.isType(type, PatternMigrationSources.EAE_ASSEMBLER)) continue;
                        activityNodeClass = type;
                        activityNodeCursor = 0;
                        continue;
                    }
                    var entry = roster.next(activityNodeClass, activityNodeCursor, rosterEpoch);
                    if (entry == null) { activityNodeClass = null; continue; }
                    activityNodeCursor = entry.id();
                    checks++;
                    var node = entry.node();
                    if (!node.isActive() || !activitySeen.add(node.getOwner())) continue;
                    try {
                        var probe = PatternMigrationSources.executionProbe(node.getOwner());
                        if (probe != null) {
                            probe.getAsBoolean(); // Validate optional APIs while failure can still exclude this owner.
                            activities.add(new ActivityProbe(node, probe));
                        }
                    } catch (RuntimeException | LinkageError unavailableApi) {
                        unsupported++;
                        visited.add(node.getOwner()); // An unknown execution contract cannot be transferred safely.
                    }
                }
                if (!activityDiscovered) return;
            }
            // Include low-priority autonomous assemblers before any high-priority inventory transfer.
            while (activityCursor < activities.size() && checks < PatternMigrationBudget.MAX_SLOTS
                    && System.nanoTime() < deadline) {
                checks++;
                if (activities.get(activityCursor++).busy(grid)) { activityCursor = 0; waitForIdle(); return; }
            }
            if (activityCursor < activities.size()) return;
            if (!discovered) {
                while (checks < PatternMigrationBudget.MAX_SLOTS && System.nanoTime() < deadline) {
                    if (nodeClass == null) {
                        if (classIndex >= classes.size()) { discoveryFinished = true; finishDiscovery(); break; }
                        Class<?> type = classes.get(classIndex);
                        int priority = PatternMigrationSources.priority(type);
                        if (priority >= 0 && priority > discoveryTier && queuedSources < sources.size()) {
                            finishDiscovery(); break;
                        }
                        if (priority >= 0) discoveryTier = priority;
                        classIndex++;
                        if (PatternMigrationSources.priority(type) < 0 && !StorageBusPart.class.isAssignableFrom(type)
                                && !MigrationReflection.isType(type, PatternMigrationSources.EAE_ASSEMBLER)) continue;
                        nodeClass = type;
                        nodeCursor = 0;
                        continue;
                    }
                    var entry = roster.next(nodeClass, nodeCursor, rosterEpoch);
                    if (entry == null) { nodeClass = null; continue; }
                    nodeCursor = entry.id();
                    IGridNode node = entry.node();
                    checks++;
                    if (!node.isActive() || !visited.add(node.getOwner())) continue;
                    Object owner = node.getOwner();
                    if (owner instanceof StorageBusPart bus) { refundPolicy.add(bus); continue; }
                    if (MigrationReflection.isType(owner.getClass(), PatternMigrationSources.EAE_ASSEMBLER)) continue;
                    try {
                        for (var source : adapters.discover(node, port)) {
                            if (!roster.wasPresent(source.owner, rosterEpoch)) continue;
                            if (source.count == 0) continue;
                            sources.add(source);
                            total += source.count;
                            if (source.busy.getAsBoolean()) waitForIdle();
                        }
                        if (stage == PatternMigrationSnapshot.Stage.WAITING) return;
                    } catch (RuntimeException | LinkageError e) { unsupported++; }
                }
                if (!discovered || task != this) { activityCursor = 0; return; }
            }
            if (targetsChanged()) resetIndex();
            if (!indexed) {
                stage = PatternMigrationSnapshot.Stage.INDEXING;
                while (targetStorage < storages.size() && checks < PatternMigrationBudget.MAX_SLOTS
                        && decodes < PatternMigrationBudget.MAX_DECODES && System.nanoTime() < deadline) {
                    var storage = storages.get(targetStorage);
                    if (targetSlot >= storage.capacity()) { targetStorage++; targetSlot = 0; continue; }
                    int slot = targetSlot++;
                    checks++;
                    var stack = storage.getInventory().getStackInSlot(slot);
                    if (stack.isEmpty()) { emptySlots.addLast(new TargetSlot(storage, slot)); continue; }
                    decodes++;
                    if (decode(stack, storage.getLevel()) instanceof IMolecularAssemblerSupportedPattern) {
                        definitions.merge(AEItemKey.of(stack), 1, Integer::sum);
                    }
                }
                if (targetStorage < storages.size()) { activityCursor = 0; return; }
                indexed = true;
                stage = PatternMigrationSnapshot.Stage.SCANNING;
            }
            Set<PatternMigrationSource> batched = Collections.newSetFromMap(new IdentityHashMap<>());
            var targetBatches = new ArrayList<MatrixPatternStorageBlockEntity>();
            try (var mutations = PatternMigrationMutationScope.open()) {
                try {
                    for (var storage : storages) { storage.beginPatternBatch(); targetBatches.add(storage); }
                    while (checks < PatternMigrationBudget.MAX_SLOTS && decodes < PatternMigrationBudget.MAX_DECODES
                            && System.nanoTime() < deadline && task == this) {
                        if (cpuBusy(grid) || controller.hasPendingMigrationWork()) { waitForIdle(); break; }
                        if (pending == null) { pending = queue.next(); pendingCounted = false; }
                        if (pending == null) {
                            if (discoveryFinished) stage = PatternMigrationSnapshot.Stage.COMPLETE;
                            else { discovered = false; stage = PatternMigrationSnapshot.Stage.SCANNING; }
                            break;
                        }
                        if (targetsChanged()) { resetIndex(); break; }
                        var source = pending.source();
                        tier = pending.tier();
                        checks++;
                        if (!pendingCounted) { scanned++; pendingCounted = true; }
                        if (!source.valid(grid)) { unavailable++; clearPending(); continue; }
                        if (source.busy.getAsBoolean()) { waitForIdle(); break; }
                        var sourceSlot = source.slot(pending.slot(), () -> source.valid(grid) && !cpuBusy(grid)
                                && !controller.hasPendingMigrationWork() && !targetsChanged());
                        var stack = sourceSlot.read().copy();
                        if (stack.isEmpty()) { clearPending(); continue; }
                        if (!PatternDetailsHelper.isEncodedPattern(stack)) { disks++; clearPending(); continue; }
                        decodes++;
                        var details = decode(stack, PatternMigrationSource.blockEntity(source.owner).getLevel());
                        if (!(details instanceof IMolecularAssemblerSupportedPattern)) { incompatible++; clearPending(); continue; }
                        AEItemKey key = AEItemKey.of(stack);
                        boolean duplicate = definitions.containsKey(key);
                        TargetSlot target = duplicate ? null : findEmptySlot();
                        if (!duplicate && target == null) { noSpace++; clearPending(); continue; }
                        PatternMigrationTransfer.Destination destination = duplicate ? refundDestination(player) : target.destination(details);
                        if (!destination.canAccept(stack.copyWithCount(1))) {
                            if (duplicate) refundBlocked++; else noSpace++;
                            clearPending();
                            continue;
                        }
                        if (!source.valid(grid) || source.busy.getAsBoolean() || cpuBusy(grid) || controller.hasPendingMigrationWork()) { waitForIdle(); break; }
                        if (batched.add(source)) source.beginBatch.run();
                        var result = PatternMigrationTransfer.move(sourceSlot, stack, destination, MatrixPatternMigration.this::custody);
                        switch (result) {
                            case MOVED -> {
                                stage = PatternMigrationSnapshot.Stage.MIGRATING;
                                if (duplicate) recovered++;
                                else {
                                    moved++;
                                    definitions.merge(key, 1, Integer::sum);
                                    emptySlots.removeFirst();
                                    revisions.put(target.storage, target.storage.getPatternContentRevision());
                                }
                                if (sourceSlot.read().isEmpty()) clearPending();
                            }
                            case RECOVERY_REQUIRED -> { stop(PatternMigrationSnapshot.Reason.RECOVERY); return; }
                            case SOURCE_CHANGED -> { unavailable++; clearPending(); }
                            case BLOCKED, RESTORED -> { if (duplicate) refundBlocked++; else noSpace++; clearPending(); }
                        }
                    }
                } finally {
                    for (var source : batched) {
                        try { source.endBatch.run(); } catch (RuntimeException e) { source.disabled = true; unsupported++; }
                    }
                    RuntimeException failed = null;
                    for (var storage : targetBatches) {
                        try { storage.endPatternBatch(); }
                        catch (RuntimeException e) { if (failed == null) failed = e; else failed.addSuppressed(e); }
                    }
                    activityCursor = 0;
                    if (failed != null) throw failed;
                }
            }
        }

        private void finishDiscovery() {
            var newlyDiscovered = sources.subList(queuedSources, sources.size());
            newlyDiscovered.sort(Comparator.comparingInt((PatternMigrationSource source) -> source.priority)
                    .thenComparing(source -> !source.craftingHint).thenComparing(source -> source.sortKey));
            for (var source : newlyDiscovered) queue.add(source.priority, source);
            queuedSources = sources.size();
            discovered = true;
            stage = PatternMigrationSnapshot.Stage.SCANNING;
            if (!indexed) resetIndex();
        }

        private void waitForIdle() {
            if (stage != PatternMigrationSnapshot.Stage.WAITING) resumeStage = stage;
            stage = PatternMigrationSnapshot.Stage.WAITING;
            reason = PatternMigrationSnapshot.Reason.BUSY;
        }

        private void clearPending() { pending = null; pendingCounted = false; }

        private boolean targetsChanged() {
            for (var storage : storages) if (revisions.getOrDefault(storage, -1L) != storage.getPatternContentRevision()) return true;
            return false;
        }

        private void resetIndex() {
            definitions.clear(); emptySlots.clear(); revisions.clear();
            for (var storage : storages) revisions.put(storage, storage.getPatternContentRevision());
            targetStorage = 0; targetSlot = 0; indexed = false;
        }

        private TargetSlot findEmptySlot() {
            while (!emptySlots.isEmpty()) {
                var slot = emptySlots.peekFirst();
                if (slot.storage.getInventory().getStackInSlot(slot.slot).isEmpty()) return slot;
                emptySlots.removeFirst();
            }
            return null;
        }

        private PatternMigrationTransfer.Destination refundDestination(ServerPlayer player) {
            return new PatternMigrationTransfer.Destination() {
                @Override public boolean canAccept(ItemStack stack) {
                    try (var scope = PatternMigrationRefundScope.open(refundPolicy)) {
                        if (grid.getStorageService().getInventory().insert(AEItemKey.of(stack), 1,
                                Actionable.SIMULATE, IActionSource.ofPlayer(player)) == 1) return true;
                    }
                    var inventory = player.getInventory();
                    for (int i = 0; i < inventory.items.size(); i++) {
                        var existing = inventory.items.get(i);
                        if (existing.isEmpty() || ItemStack.isSameItemSameComponents(existing, stack)
                                && existing.getCount() < Math.min(existing.getMaxStackSize(), inventory.getMaxStackSize())) return true;
                    }
                    return false;
                }
                @Override public ItemStack insert(ItemStack stack) { return refund(player, grid, refundPolicy, stack); }
            };
        }

        void release() {
            if (ecoLease != null) ecoLease.close();
            budget.release(grid, this);
        }

        PatternMigrationSnapshot snapshot() {
            return new PatternMigrationSnapshot(stage, reason, tier, scanned, total, moved, recovered,
                    incompatible, noSpace, refundBlocked, unavailable, unsupported, disks, lastSliceNanos, budgetHits);
        }
    }

    private record ActivityProbe(IGridNode node, java.util.function.BooleanSupplier probe) {
        boolean busy(IGrid grid) {
            var be = PatternMigrationSource.blockEntity(node.getOwner());
            return node.isActive() && node.getGrid() == grid && be != null && !be.isRemoved()
                    && be.getLevel() != null && be.getLevel().isLoaded(be.getBlockPos())
                    && be.getLevel().getBlockEntity(be.getBlockPos()) == be
                    && (!(node.getOwner() instanceof appeng.parts.AEBasePart part)
                            || part.getHost().getPart(part.getSide()) == part)
                    && probe.getAsBoolean();
        }
    }

    private record TargetSlot(MatrixPatternStorageBlockEntity storage, int slot) {
        PatternMigrationTransfer.Destination destination(appeng.api.crafting.IPatternDetails details) {
            return new PatternMigrationTransfer.Destination() {
                @Override public boolean canAccept(ItemStack stack) { return storage.insertMigrationPattern(slot, stack, details, true).isEmpty(); }
                @Override public ItemStack insert(ItemStack stack) { return storage.insertMigrationPattern(slot, stack, details, false); }
                @Override public boolean received(ItemStack stack) {
                    return PatternMigrationTransfer.same(storage.getInventory().getStackInSlot(slot), stack);
                }
            };
        }
    }
}
