package com.moakiee.ae2lt.machine.largeoverload;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionSource;
import appeng.api.orientation.BlockOrientation;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.me.helpers.MachineSource;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import appeng.util.inv.filter.IAEItemFilter;
import com.moakiee.ae2lt.crafting.runtime.api.DeferredCraftingProvider;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerRecipe;
import com.moakiee.thunderbolt.api.crafting.batch.BatchJobView;
import com.moakiee.thunderbolt.api.crafting.batch.IBatchCraftingProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** One independently channelled network endpoint with its own mode, inventory and resource account. */
public final class LargeFactoryHatchBlockEntity extends AENetworkedBlockEntity
        implements InternalInventoryHost, IBatchCraftingProvider, DeferredCraftingProvider {
    public static final int MAX_VIRTUAL_PATTERNS = 256;
    private AppEngInternalInventory inventory;
    private final IActionSource actionSource = new MachineSource(getMainNode()::getNode);
    private UUID accountId = UUID.randomUUID();
    private BlockPos controllerPos;
    private UUID machine;
    private boolean linked;
    private boolean passive;
    private boolean dirtyPatterns = true;
    private long catalogGeneration = -1;
    private long accessGeneration = -1;
    private List<Entry> entries = List.of();
    private final BitSet disabledSlots = new BitSet();
    private final Set<ResourceLocation> disabledRecipes = new HashSet<>();
    private int passiveCursor;
    private long deliveryTick;
    private boolean processing;
    private boolean resourcesReleased;
    private boolean releaseRequested;
    private String status = "unformed";

    public static final class Entry {
        public final IPatternDetails pattern;
        public final int slot;
        public final ResourceLocation virtualRecipe;
        public final Map<AEKey, Long> outputs;
        private LargeFactoryRecipe bound;
        private Map<AEKey, Long> signature;
        private long operations;
        private LargeFactoryRecipeAccess.Process knownProcess;
        private int searchCursor;
        public String status = "unbound";
        Entry(IPatternDetails pattern, int slot, ResourceLocation virtualRecipe) {
            this.pattern = pattern; this.slot = slot; this.virtualRecipe = virtualRecipe;
            this.outputs = LargeFactoryAmounts.of(pattern.getOutputs());
        }
        public LargeFactoryRecipe bound() { return bound; }
        public long operations() { return operations; }
    }

    public LargeFactoryHatchBlockEntity(BlockPos pos, BlockState state) {
        super(LargeFactoryRegistration.HATCH.get(), pos, state);
        var component = LargeFactoryRegistration.component(state);
        inventory = createInventory(component == LargeFactoryComponent.CRYSTAL_HATCH ? LargeFactoryConfig.crystalSlots() : component.patternSlots());
    }
    private AppEngInternalInventory createInventory(int slots) {
        return new AppEngInternalInventory(this, slots, crystal() ? 64 : 1, new IAEItemFilter() {
                    @Override public boolean allowInsert(InternalInventory inventory, int slot, ItemStack stack) {
                        return crystal() || PatternDetailsHelper.isEncodedPattern(stack);
                    }
                });
    }
    @Override protected IManagedGridNode createMainNode() {
        return super.createMainNode().setTagName("largeFactoryNode").setIdlePowerUsage(4)
                .setFlags(GridFlags.REQUIRE_CHANNEL).addService(ICraftingProvider.class, this);
    }
    public boolean crystal() { return LargeFactoryRegistration.component(getBlockState()) == LargeFactoryComponent.CRYSTAL_HATCH; }
    public AppEngInternalInventory inventory() { return inventory; }
    public IActionSource actionSource() { return actionSource; }
    public boolean passive() { return passive; }
    public boolean processing() { return processing; }
    void processing(boolean processing) {
        this.processing = processing;
        if (!processing && releaseRequested) { releaseRequested = false; releaseResources(); }
    }
    public UUID accountId() { return accountId; }
    public String status() { return status; }
    void status(String status) { this.status = status; }
    public void togglePassive() { passive = !passive; patternsChanged(); saveChanges(); }

    public void bind(BlockPos controller, UUID id) {
        boolean next = controller != null && id != null;
        boolean changed = linked != next || !java.util.Objects.equals(controllerPos, controller) || !java.util.Objects.equals(machine, id);
        controllerPos = controller; machine = id; linked = next;
        if (changed) { onGridConnectableSidesChanged(); patternsChanged(); }
    }
    public LargeFactoryControllerBlockEntity controller() {
        if (!linked || level == null || controllerPos == null || !level.isLoaded(controllerPos)) return null;
        return level.getBlockEntity(controllerPos) instanceof LargeFactoryControllerBlockEntity c && c.owns(worldPosition, machine) ? c : null;
    }
    public boolean ready() { return !isRemoved() && level != null && level.getBlockEntity(worldPosition) == this && controller() != null && getMainNode().isActive() && getMainNode().getGrid() != null && account() != null; }
    @Override public AECableType getCableConnectionType(Direction side) {
        var controller = controller();
        return controller != null && side == controller.facing().getOpposite() ? AECableType.DENSE_SMART : AECableType.NONE;
    }
    @Override public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        var controller = controller();
        return controller == null ? Collections.emptySet() : EnumSet.of(controller.facing().getOpposite());
    }
    @Override public void saveChangedInventory(AppEngInternalInventory inventory) { saveChanges(); patternsChanged(); }
    @Override public void onChangeInventory(AppEngInternalInventory inventory, int slot) { patternsChanged(); }
    public void patternsChanged() { dirtyPatterns = true; }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LargeFactoryHatchBlockEntity hatch) {
        if (level.isClientSide) return;
        if (hatch.linked && hatch.controller() == null) hatch.bind(null, null);
        hatch.refreshPatterns();
        hatch.flushRetained();
    }
    private void refreshPatterns() {
        if (level == null || level.isClientSide) return;
        var controller = controller();
        var catalog = LargeFactoryRecipes.get(level.getRecipeManager());
        long access = controller == null ? -1 : controller.capabilityVersion();
        if (!dirtyPatterns && catalogGeneration == catalog.generation() && accessGeneration == access) return;
        dirtyPatterns = false;
        boolean sameCatalog = catalogGeneration == catalog.generation();
        var previousEntries = entries;
        catalogGeneration = catalog.generation(); accessGeneration = access;
        var next = new ArrayList<Entry>();
        if (crystal()) {
            if (controller != null && controller.access().allows(LargeFactoryRecipeAccess.Process.CATALYZER)) {
                for (var recipe : catalog.recipes()) {
                    if (recipe.catalyst() == null || !hasCatalyst(recipe)) continue;
                    var source = level.getRecipeManager().byKey(recipe.id()).orElse(null);
                    if (source == null || !(source.value() instanceof CrystalCatalyzerRecipe catalyst)) continue;
                    var fluid = catalyst.fluidInput();
                    if (fluid.isEmpty()) continue;
                    var patternItem = PatternDetailsHelper.encodeProcessingPattern(
                            List.of(new GenericStack(AEFluidKey.of(fluid), fluid.getAmount())), LargeFactoryAmounts.stacks(recipe.outputs()));
                    int materialSlot = -1;
                    for (int slot = 0; slot < inventory.size(); slot++) if (recipe.catalyst().test(inventory.getStackInSlot(slot))) { materialSlot = slot; break; }
                    final int identitySlot = materialSlot;
                    net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, patternItem, tag -> {
                        tag.putUUID("FactoryHatch", accountId);
                        tag.putString("FactoryRecipe", recipe.id().toString());
                        tag.putInt("MaterialSlot", identitySlot);
                        tag.putLong("RecipeGeneration", catalog.generation());
                    });
                    var details = PatternDetailsHelper.decodePattern(patternItem, level);
                    if (details != null) next.add(new Entry(details, -1, recipe.id()));
                    if (next.size() >= MAX_VIRTUAL_PATTERNS) { status = "virtual_limit"; break; }
                }
            }
        } else {
            for (int slot = 0; slot < inventory.size(); slot++) {
                var stack = inventory.getStackInSlot(slot);
                if (stack.isEmpty()) continue;
                try {
                    var pattern = PatternDetailsHelper.decodePattern(stack, level);
                    if (pattern != null && pattern.supportsPushInputsToExternalInventory()
                            && !(pattern instanceof appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern)) {
                        next.add(new Entry(pattern, slot, null));
                    }
                } catch (RuntimeException invalid) { status = "invalid_pattern"; }
            }
        }
        if (sameCatalog) for (var entry : next) for (var previous : previousEntries) {
            if (entry.slot == previous.slot && java.util.Objects.equals(entry.virtualRecipe, previous.virtualRecipe)
                    && entry.pattern.getDefinition().equals(previous.pattern.getDefinition())) {
                entry.knownProcess = previous.knownProcess;
                if (controller != null && entry.knownProcess != null && !controller.access().allows(entry.knownProcess)) entry.status = "process_locked";
                break;
            }
        }
        entries = List.copyOf(next);
        ICraftingProvider.requestUpdate(getMainNode());
    }
    public List<Entry> entries() { refreshPatterns(); return entries; }
    public boolean enabled(Entry entry) { return entry.virtualRecipe == null ? !disabledSlots.get(entry.slot) : !disabledRecipes.contains(entry.virtualRecipe); }
    public void toggleEntry(int index) {
        var entries = entries();
        if (index < 0 || index >= entries.size()) return;
        var entry = entries.get(index);
        if (entry.virtualRecipe == null) disabledSlots.flip(entry.slot);
        else if (!disabledRecipes.remove(entry.virtualRecipe)) disabledRecipes.add(entry.virtualRecipe);
        ICraftingProvider.requestUpdate(getMainNode()); saveChanges();
    }
    boolean hasCatalyst(LargeFactoryRecipe recipe) {
        if (recipe.catalyst() == null) return !crystal();
        if (!crystal()) return false;
        if (recipe.catalyst().test(ItemStack.EMPTY)) return false;
        for (int slot = 0; slot < inventory.size(); slot++) if (recipe.catalyst().test(inventory.getStackInSlot(slot))) return true;
        return false;
    }
    public Entry find(IPatternDetails pattern) {
        for (var entry : entries()) if (enabled(entry) && entry.pattern.getDefinition().equals(pattern.getDefinition())) return entry;
        return null;
    }
    LargeFactoryRecipe bindRecipe(Entry entry, Map<AEKey, Long> actual) {
        var controller = controller();
        if (controller == null || !enabled(entry) || !entries().contains(entry)) return null;
        if (entry.bound != null && actual.equals(entry.signature) && controller.access().allows(entry.bound.process()) && hasCatalyst(entry.bound)) return entry.bound;
        if (!actual.equals(entry.signature)) { entry.signature = Map.copyOf(actual); entry.searchCursor = 0; entry.bound = null; entry.operations = 0; }
        var candidates = LargeFactoryRecipes.get(level.getRecipeManager()).recipes();
        while (entry.searchCursor < candidates.size()) {
            if (!LargeFactoryWorkBudget.take(this, LargeFactoryWorkBudget.Work.MATCH)) { entry.status = "binding_pending"; controller.wakeNextTick(); return null; }
            var recipe = candidates.get(entry.searchCursor++);
            if (!controller.access().allows(recipe.process()) || !hasCatalyst(recipe)
                    || (entry.virtualRecipe != null && !entry.virtualRecipe.equals(recipe.id()))) continue;
            long operations = recipe.match(actual, entry.outputs);
            if (operations <= 0) continue;
            entry.bound = recipe; entry.knownProcess = recipe.process(); entry.operations = operations; entry.signature = Map.copyOf(actual); entry.status = "bound";
            return recipe;
        }
        entry.bound = null; entry.operations = 0; entry.status = "recipe_mismatch";
        status = "recipe_mismatch";
        return null;
    }
    @Override public List<IPatternDetails> getAvailablePatterns() {
        if (passive || controller() == null) return List.of();
        return entries().stream().filter(this::enabled)
                .filter(e -> e.knownProcess == null || controller().access().allows(e.knownProcess))
                .map(e -> e.pattern).distinct().toList();
    }
    @Override public boolean isBusy() {
        var controller = controller();
        var account = account();
        return passive || processing || !ready() || account == null || !account.resources.isEmpty()
                || controller.budget().remainingOperations(level.getGameTime()) <= 0;
    }
    @Override public long getBatchCapacity(IPatternDetails details) {
        if (isBusy()) return 0;
        var entry = find(details);
        return entry == null ? 0 : controller().budget().remainingOperations(level.getGameTime()) / Math.max(1, entry.operations);
    }
    @Override public long pushBatch(IPatternDetails details, KeyCounter[] template, long requested) {
        return pushBatchWithReturns(details, template, requested, null);
    }
    @Override public long pushBatch(IPatternDetails details, KeyCounter[] template, long requested, BatchJobView job) {
        return pushBatchWithReturns(details, template, requested, job instanceof DeferredCraftingProvider.Job deferred ? deferred.deferredOutputSink() : null);
    }
    private long pushBatchWithReturns(IPatternDetails details, KeyCounter[] template, long requested, OutputSink returns) {
        if (requested <= 0 || isBusy()) return requested;
        var entry = find(details);
        if (entry == null) return requested;
        Map<AEKey, Long> actual;
        try { actual = LargeFactoryAmounts.flatten(template); } catch (IllegalArgumentException | ArithmeticException invalid) { return requested; }
        return requested - LargeFactoryExecutor.execute(this, entry, actual, requested, returns, false);
    }
    @Override public boolean pushPattern(IPatternDetails details, KeyCounter[] inputs) { return pushBatchWithReturns(details, inputs, 1, null) == 0; }
    @Override public boolean pushPattern(IPatternDetails details, KeyCounter[] inputs, OutputSink returns) { return pushBatchWithReturns(details, inputs, 1, returns) == 0; }

    public LargeFactoryLedger.Account account() {
        if (resourcesReleased || !(level instanceof ServerLevel server)) return null;
        return LargeFactoryLedger.get(server).claim(accountId, server, worldPosition);
    }
    long extract(IGrid grid, AEKey key, long amount, Actionable mode) {
        if (amount <= 0 || !LargeFactoryWorkBudget.take(this, LargeFactoryWorkBudget.Work.NETWORK)) return 0;
        return grid.getStorageService().getInventory().extract(key, amount, mode, actionSource);
    }
    double extractEnergy(IGrid grid, double amount, Actionable mode) {
        if (amount <= 0 || !LargeFactoryWorkBudget.take(this, LargeFactoryWorkBudget.Work.NETWORK)) return 0;
        return grid.getEnergyService().extractAEPower(amount, mode, appeng.api.config.PowerMultiplier.ONE);
    }
    void ledgerChanged() { if (level instanceof ServerLevel server) LargeFactoryLedger.get(server).setDirty(); saveChanges(); }
    UUID origin(IGrid grid) {
        var account = account();
        var identity = grid.getService(LargeFactoryNetworkIdentity.class);
        if (account == null) return null;
        if (!account.empty()) return identity.contains(account.origin) ? account.origin : null;
        return identity.externalAnchor(getMainNode().getNode());
    }
    void deliverAfter(long tick) { deliveryTick = tick; }
    public void flushRetained() {
        if (processing || level == null || level.isClientSide || level.getGameTime() < deliveryTick) return;
        var account = account();
        var grid = getMainNode().getGrid();
        if (account == null || account.empty() || grid == null || !getMainNode().isActive()) return;
        if (!grid.getService(LargeFactoryNetworkIdentity.class).contains(account.origin)) { status = "origin_missing"; return; }
        processing = true;
        try {
            transferAccount(account, grid);
        } finally { processing(false); }
    }
    private void transferAccount(LargeFactoryLedger.Account account, IGrid grid) {
        var storage = grid.getStorageService().getInventory();
        for (var entry : List.copyOf(account.resources.entrySet())) {
            if (getMainNode().getGrid() != grid) break;
            if (!LargeFactoryWorkBudget.take(this, LargeFactoryWorkBudget.Work.NETWORK)) break;
            long inserted = storage.insert(entry.getKey(), entry.getValue(), Actionable.MODULATE, actionSource);
            if (inserted < 0 || inserted > entry.getValue()) throw new IllegalStateException("Invalid ME insertion receipt");
            if (inserted == entry.getValue()) account.resources.remove(entry.getKey());
            else account.resources.put(entry.getKey(), entry.getValue() - inserted);
            ledgerChanged();
        }
        if (getMainNode().getGrid() == grid && account.energyCreditAE > 0
                && LargeFactoryWorkBudget.take(this, LargeFactoryWorkBudget.Work.NETWORK)) {
            account.energyCreditAE = grid.getEnergyService().injectPower(account.energyCreditAE, Actionable.MODULATE);
            ledgerChanged();
        }
        status = account.empty() ? "ready" : "return_blocked";
    }
    public boolean recoverParcel(UUID id) {
        if (!(level instanceof ServerLevel server) || !getMainNode().isActive() || processing) return false;
        var ledger = LargeFactoryLedger.get(server);
        var parcel = ledger.parcel(id);
        var grid = getMainNode().getGrid();
        if (parcel == null || grid == null) return false;
        // An explicit capsule use authorizes transferring its resources to this network.
        processing = true;
        try { transferAccount(parcel, grid); ledger.discardEmptyParcel(parcel); }
        finally { processing(false); }
        return parcel.empty();
    }
    public void exportRecovery(net.minecraft.world.entity.player.Player player) {
        if (!(level instanceof ServerLevel server) || processing) return;
        var account = account();
        if (account == null || account.empty()) return;
        var capsule = LargeFactoryRecoveryItem.create(accountId);
        LargeFactoryLedger.get(server).release(account);
        accountId = UUID.randomUUID();
        if (!player.addItem(capsule)) player.drop(capsule, false);
        saveChanges();
    }

    public void releaseResources() {
        if (!(level instanceof ServerLevel server) || resourcesReleased) return;
        if (processing) { releaseRequested = true; return; }
        var ledger = LargeFactoryLedger.get(server);
        var account = account();
        if (account != null) {
            boolean hasResources = !account.empty();
            ledger.release(account);
            if (hasResources) Block.popResource(level, worldPosition, LargeFactoryRecoveryItem.create(accountId));
        }
        resourcesReleased = true;
    }
    @Override public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        for (int i = 0; i < inventory.size(); i++) if (!inventory.getStackInSlot(i).isEmpty()) drops.add(inventory.getStackInSlot(i).copy());
    }
    @Override public void clearContent() { for (int i = 0; i < inventory.size(); i++) inventory.setItemDirect(i, ItemStack.EMPTY); }

    public boolean passiveStep() { return LargeFactoryPassive.step(this); }
    Entry nextPassiveEntry() {
        var entries = entries();
        if (entries.isEmpty()) return null;
        return entries.get(Math.floorMod(passiveCursor++, entries.size()));
    }
    @Override public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putUUID("Account", accountId);
        tag.putInt("SlotCount", inventory.size());
        inventory.writeToNBT(tag, "Patterns", registries);
        tag.putBoolean("Passive", passive);
        tag.putLongArray("DisabledSlots", disabledSlots.toLongArray());
        var disabled = new ListTag();
        disabledRecipes.forEach(id -> disabled.add(StringTag.valueOf(id.toString())));
        tag.put("DisabledRecipes", disabled);
    }
    @Override public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        accountId = tag.hasUUID("Account") ? tag.getUUID("Account") : UUID.randomUUID();
        if (crystal() && tag.contains("SlotCount")) inventory = createInventory(Math.clamp(tag.getInt("SlotCount"), 1, 36));
        inventory.readFromNBT(tag, "Patterns", registries);
        passive = tag.getBoolean("Passive");
        disabledSlots.clear(); disabledSlots.or(BitSet.valueOf(tag.getLongArray("DisabledSlots")));
        disabledRecipes.clear();
        for (var value : tag.getList("DisabledRecipes", Tag.TAG_STRING)) {
            var id = ResourceLocation.tryParse(value.getAsString());
            if (id != null) disabledRecipes.add(id);
        }
        linked = false; controllerPos = null; machine = null; dirtyPatterns = true; resourcesReleased = false;
    }
}
