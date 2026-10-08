package com.moakiee.ae2lt.machine.largeoverload;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import com.moakiee.ae2lt.blockentity.FirmamentConversionCoreBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Coordinates only this fixed factory; all physical job resources belong to individual hatches. */
public final class LargeFactoryControllerBlockEntity extends BlockEntity {
    private final LargeFactoryBuilder builder = new LargeFactoryBuilder();
    private int[] missing = new int[2];
    private long passiveWorkTick = Long.MIN_VALUE;
    private int passiveVisits;
    private UUID machineId = UUID.randomUUID();
    private LargeFactoryStructureOwnership.Binding binding;
    private LargeFactoryStructure.ScanResult lastScan;
    private LargeFactoryComponent core;
    private LargeFactoryRecipeAccess access;
    private LargeFactoryOperationBudget budget;
    private List<LargeFactoryHatchBlockEntity> hatches = List.of();
    private boolean scanRequested = true;
    private LargeFactoryStructure.Cursor scanCursor;
    private long nextScan;
    private boolean energyReleased;
    private LargeFactoryLedger ledger;
    private LargeFactoryLedger.Account cachedEnergyAccount;
    private boolean allowNetworkEnergy = true;
    private boolean preview;
    private long energyTick = Long.MIN_VALUE;
    private long energyUsed;
    private long energyReceived;
    private long lastOperationTick = Long.MIN_VALUE;
    private boolean executing;
    private int passiveCursor;
    private long nextPassiveWake;
    private boolean completionWake;
    private long capabilityVersion;
    private String status = "unformed";

    public LargeFactoryControllerBlockEntity(BlockPos pos, BlockState state) {
        super(LargeFactoryRegistration.CONTROLLER.get(), pos, state);
    }
    @Override public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) { LargeFactoryWorld.get(level).add(this);
            LargeFactoryWorkBudget.register((net.minecraft.server.level.ServerLevel) level, machineId); }
        scanRequested = true;
    }
    @Override public void setRemoved() {
        if (level != null && !level.isClientSide) { LargeFactoryWorld.get(level).remove(this);
            LargeFactoryWorkBudget.unregister((net.minecraft.server.level.ServerLevel) level, machineId); }
        super.setRemoved();
    }
    public UUID machineId() { return machineId; }
    @Override public void setBlockState(BlockState state) {
        var previous = getBlockState();
        super.setBlockState(state);
        if (level != null && !level.isClientSide && previous.getValue(LargeFactoryControllerBlock.FACING) != state.getValue(LargeFactoryControllerBlock.FACING)) {
            LargeFactoryWorld.get(level).remove(this);
            LargeFactoryWorld.get(level).add(this);
            requestScan();
        }
    }
    public Direction facing() { return getBlockState().getValue(LargeFactoryControllerBlock.FACING); }
    public boolean formed() { return binding != null && binding.isCurrent(); }
    public boolean owns(BlockPos position, UUID machine) {
        return machineId.equals(machine) && binding != null && binding.owns(position);
    }
    LargeFactoryStructureOwnership.Binding membership(BlockPos position, UUID machine) {
        return owns(position, machine) ? binding : null;
    }
    boolean owns(LargeFactoryStructureOwnership.Binding membership) {
        // Membership was checked when the hatch bound. Every member is revoked together;
        // an old token must not inherit a replacement formation on this controller.
        return membership != null && binding == membership && membership.isCurrent();
    }
    public boolean contains(BlockPos position) {
        var a = LargeFactoryStructure.worldPosition(worldPosition, BlockPos.ZERO, facing());
        var b = LargeFactoryStructure.worldPosition(worldPosition, new BlockPos(8, 6, 8), facing());
        return position.getX() >= Math.min(a.getX(), b.getX()) && position.getX() <= Math.max(a.getX(), b.getX())
                && position.getY() >= Math.min(a.getY(), b.getY()) && position.getY() <= Math.max(a.getY(), b.getY())
                && position.getZ() >= Math.min(a.getZ(), b.getZ()) && position.getZ() <= Math.max(a.getZ(), b.getZ());
    }
    public void requestScan() { scanRequested = true; scanCursor = null; }
    public void suspend() {
        if (binding != null) LargeFactoryWorld.get(level).ownership.release(binding);
        binding = null;
        for (var hatch : hatches) hatch.bind(null, null);
        hatches = List.of();
        capabilityVersion++;
        status = "unformed";
    }
    public void tick() {
        long start = LargeFactoryTiming.begin();
        try { tickWork(); } finally { LargeFactoryTiming.end(this, "controller", start); }
    }
    private void tickWork() {
        long tick = level.getGameTime();
        builder.tick(this);
        if (scanRequested && tick >= nextScan && !executing && !builder.active()
                && LargeFactoryWorkBudget.take(this, LargeFactoryWorkBudget.Work.SCAN)) {
            nextScan = tick + 1;
            scan();
        }
        if (preview && tick % 20 == 0) LargeFactoryPreview.show(this);
        if (!formed()) return;
        if (!executing) budget.reconfigure(tick, LargeFactoryConfig.operations(core));
        for (var hatch : hatches) hatch.flushRetained();
        if ((completionWake || tick >= nextPassiveWake) && !executing) {
            completionWake = false;
            nextPassiveWake = tick + 100;
            runPassive();
        }
    }
    private void scan() {
        if (formed()) { scanRequested = false; return; }
        if (scanCursor == null) scanCursor = new LargeFactoryStructure.Cursor(worldPosition, facing());
        var scanned = scanCursor.advance(32, 15_000, level::isLoaded,
                p -> LargeFactoryRegistration.component(level.getBlockState(p)), p -> {
                    if (!(level.getBlockEntity(p) instanceof FirmamentConversionCoreBlockEntity firmament)
                            || !firmament.isInsideFirmamentStarship()) return LargeFactoryStructure.FirmamentReadiness.OUTSIDE_STARSHIP;
                    return firmament.canJoinLargeFactory() ? LargeFactoryStructure.FirmamentReadiness.READY
                            : LargeFactoryStructure.FirmamentReadiness.INVENTORY_OR_JOB_PRESENT;
                });
        if (scanned == null) return;
        missing = scanCursor.missing();
        lastScan = scanned;
        scanCursor = null; scanRequested = false;
        if (lastScan.status() != LargeFactoryStructure.Status.VALID) {
            status = lastScan.status() == LargeFactoryStructure.Status.INCOMPLETE ? "chunk_unloaded" : "invalid_structure";
            return;
        }
        var formation = lastScan.formation();
        if (energyAccount() == null) { status = "identity_conflict"; return; }
        if (core != null && core != formation.core() && lastOperationTick == level.getGameTime()) { requestScan(); return; }
        var claim = LargeFactoryWorld.get(level).ownership.claim(machineId, formation);
        if (claim.isEmpty()) { status = "overlap"; return; }
        binding = claim.get();
        boolean changedCore = core != formation.core();
        core = formation.core();
        access = new LargeFactoryRecipeAccess(core, java.util.Set.of());
        if (budget == null || changedCore) budget = new LargeFactoryOperationBudget(LargeFactoryConfig.operations(core));
        else budget.reconfigure(level.getGameTime(), LargeFactoryConfig.operations(core));
        var newHatches = new ArrayList<LargeFactoryHatchBlockEntity>();
        for (var member : formation.hatches()) {
            var be = level.getBlockEntity(member.position());
            if (be instanceof LargeFactoryHatchBlockEntity hatch) {
                hatch.bind(worldPosition, machineId);
                newHatches.add(hatch);
            } else if (be instanceof LargeFactoryAuxBlockEntity aux) aux.bind(worldPosition, machineId);
        }
        hatches = List.copyOf(newHatches);
        permissionsChanged();
        nextPassiveWake = level.getGameTime() + Math.floorMod(worldPosition.hashCode(), 100);
        status = "ready";
        setChanged();
    }
    public void permissionsChanged() {
        if (!formed()) return;
        var unlocked = EnumSet.noneOf(LargeFactoryRecipeAccess.Process.class);
        for (var member : binding.formation().hatches()) {
            if (member.component() != LargeFactoryComponent.PROCESS_CORE_HATCH) continue;
            if (level.getBlockEntity(member.position()) instanceof LargeFactoryAuxBlockEntity aux) {
                for (int i = 0; i < aux.inventory().getSlots(); i++) {
                    var stack = aux.inventory().getStackInSlot(i);
                    for (var entry : LargeFactoryRegistration.PROCESS_CORES.entrySet()) if (stack.is(entry.getValue().get())) unlocked.add(entry.getKey());
                }
            }
        }
        access = new LargeFactoryRecipeAccess(core, unlocked);
        capabilityVersion++;
        hatches.forEach(LargeFactoryHatchBlockEntity::accessChanged);
        completionWake = true;
        setChanged();
    }
    public LargeFactoryRecipeAccess access() { return access; }
    public long capabilityVersion() { return capabilityVersion; }
    public LargeFactoryOperationBudget budget() { return budget; }
    public LargeFactoryComponent core() { return core; }
    private LargeFactoryLedger.Account energyAccount() {
        if (energyReleased || !(level instanceof net.minecraft.server.level.ServerLevel server)) return null;
        if (ledger == null) ledger = LargeFactoryLedger.get(server);
        if (cachedEnergyAccount == null) cachedEnergyAccount = ledger.claim(machineId, server, worldPosition);
        return cachedEnergyAccount != null && !cachedEnergyAccount.parcel ? cachedEnergyAccount : null;
    }
    public long energyStored() { var account = energyAccount(); return account == null ? 0 : account.externalFE; }
    public void releaseEnergy() {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server) || energyReleased) return;
        var account = energyAccount();
        if (account != null) {
            boolean retained = !account.empty();
            LargeFactoryLedger.get(server).release(account);
            if (retained) net.minecraft.world.level.block.Block.popResource(level, worldPosition, LargeFactoryRecoveryItem.create(machineId));
        }
        energyReleased = true;
    }
    public long energyCapacity() { return core == null ? 0 : LargeFactoryConfig.capacity(core); }
    private void beginEnergyTick() {
        long tick = level.getGameTime();
        if (energyTick != tick) { energyTick = tick; energyUsed = 0; energyReceived = 0; }
    }
    public long remainingEnergyThroughput() {
        beginEnergyTick();
        return core == null ? 0 : Math.max(0, LargeFactoryConfig.throughput(core) - energyUsed);
    }
    public int receiveEnergy(int requested, boolean simulate) {
        if (!formed() || core == LargeFactoryComponent.FIRMAMENT_CORE) return 0;
        var account = energyAccount();
        if (account == null) return 0;
        beginEnergyTick();
        long accepted = Math.min(Math.max(0, requested), Math.min(Math.max(0, energyCapacity() - account.externalFE),
                Math.max(0, LargeFactoryConfig.throughput(core) - energyReceived)));
        if (!simulate && accepted > 0) {
            account.externalFE += accepted;
            ledger.setDirty();
            energyReceived += accepted;
        }
        return (int) accepted;
    }
    public boolean allowNetworkEnergy() { return allowNetworkEnergy; }
    public void toggleNetworkEnergy() { allowNetworkEnergy = !allowNetworkEnergy; setChanged(); }
    public void consumeEnergy(long fromBuffer, long total) {
        var account = energyAccount();
        if (account == null || fromBuffer < 0 || fromBuffer > account.externalFE || total < fromBuffer || total > remainingEnergyThroughput()) throw new IllegalStateException("Invalid factory energy commit");
        account.externalFE -= fromBuffer;
        ledger.setDirty();
        energyUsed += total;
        // FE is saved only by the ledger; throughput is transient. No block NBT changed,
        // so a chunk/comparator notification for each committed copy is unnecessary.
    }
    public boolean enterExecution() {
        if (executing || !formed() || energyAccount() == null) return false;
        executing = true;
        return true;
    }
    public void leaveExecution() { executing = false; }
    public void wakeNextTick() { completionWake = true; }
    public void completed() {
        lastOperationTick = level.getGameTime();
        completionWake = true;
        status = "working";
    }
    private void runPassive() {
        if (hatches.isEmpty()) return;
        // Round-robin hatches, then one bounded entry per visit. No completion callback recurses.
        if (passiveWorkTick != level.getGameTime()) { passiveWorkTick = level.getGameTime(); passiveVisits = 0; }
        int visits = Math.min(64 - passiveVisits, hatches.size() * 4);
        boolean progressed = false;
        for (int i = 0; i < visits && budget.remainingOperations(level.getGameTime()) > 0; i++) {
            passiveVisits++;
            var hatch = hatches.get(Math.floorMod(passiveCursor++, hatches.size()));
            progressed |= hatch.passiveStep();
        }
        if (progressed) completionWake = true;
    }
    public void endTick() {
        long start = LargeFactoryTiming.begin();
        try { endTickWork(); } finally { LargeFactoryTiming.end(this, "completion", start); }
    }
    private void endTickWork() {
        if (!formed() || executing || !completionWake || passiveWorkTick == level.getGameTime() && passiveVisits >= 64) return;
        completionWake = false;
        runPassive();
        // Exhausting this tick's resource/operation quota must not discard the completion wake.
        if (lastOperationTick == level.getGameTime()) completionWake = true;
    }
    public void startBuild(net.minecraft.server.level.ServerPlayer player) { builder.start(player); }
    public int[] missing() { return missing.clone(); }
    public String status() { return builder.active() ? "building" : status; }
    public LargeFactoryStructure.ScanResult lastScan() { return lastScan; }
    public List<LargeFactoryHatchBlockEntity> hatches() { return hatches; }
    int passiveHatchCount() {
        int count = 0;
        for (var hatch : hatches) if (hatch.passive()) count++;
        return Math.max(1, count);
    }
    public void togglePreview() { preview = !preview; if (preview) LargeFactoryPreview.show(this); }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putUUID("Machine", machineId);
        tag.putInt("StructureVersion", 1);
        if (core != null) tag.putString("LastCore", core.name());
        tag.putLong("CapabilityVersion", capabilityVersion);
        tag.putBoolean("ExternalOnly", !allowNetworkEnergy);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        machineId = tag.hasUUID("Machine") ? tag.getUUID("Machine") : UUID.randomUUID();
        ledger = null; cachedEnergyAccount = null;
        core = null;
        try { core = LargeFactoryComponent.valueOf(tag.getString("LastCore")); }
        catch (IllegalArgumentException ignored) { }
        if (core != null && !core.isCore()) core = null;
        capabilityVersion = Math.max(0, tag.getLong("CapabilityVersion"));
        energyReleased = false;
        allowNetworkEnergy = !tag.getBoolean("ExternalOnly");
        binding = null; scanRequested = true; scanCursor = null; hatches = List.of();
    }
}
