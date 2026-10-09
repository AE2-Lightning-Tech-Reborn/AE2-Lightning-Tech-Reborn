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
import appeng.blockentity.grid.AENetworkBlockEntity;
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
public final class LargeFactoryHatchBlockEntity extends AENetworkBlockEntity
        implements InternalInventoryHost, IBatchCraftingProvider, DeferredCraftingProvider {
    public static final int MAX_VIRTUAL_PATTERNS = 256;
    private AppEngInternalInventory inventory;
    private final IActionSource actionSource = new MachineSource(getMainNode()::getNode);
    private UUID accountId = UUID.randomUUID();
    private BlockPos controllerPos;
    private LargeFactoryControllerBlockEntity boundController;
    private LargeFactoryStructureOwnership.Binding boundMembership;
    private UUID machine;
    private boolean linked;
    private boolean passive;
    private boolean dirtyPatterns = true;
    private long catalogGeneration = -1;
    private long accessGeneration = -1;
    private List<Entry> entries = List.of();
    private ArrayList<Entry> buildingEntries;
    private Entry[] previousSlots;
    private int buildCursor;
    private boolean buildSameCatalog;
    private List<IPatternDetails> publishedPatterns = List.of();
    private Map<AEKey, Entry> publishedEntries = Map.of();
    // Same catalog-lifetime identity front cache as the overloaded provider. Neither side
    // owns transient CPU wrappers, and live capacity/payment state is never cached here.
    private final Map<IPatternDetails, Entry> resolvedEntries = new com.google.common.collect.MapMaker()
            .weakKeys().weakValues().concurrencyLevel(1).makeMap();
    private LargeFactoryLedger ledger;
    private LargeFactoryLedger.Account cachedAccount;
    private final Map<AEKey, Long> availability = new java.util.HashMap<>();
    private IGrid availabilityGrid;
    private long availabilityTick = Long.MIN_VALUE;
    private final com.moakiee.ae2lt.logic.TransferPollSchedule returnSchedule = new com.moakiee.ae2lt.logic.TransferPollSchedule();
    private long nextReturnTick;
    private boolean returnObserving;
    private final BitSet disabledSlots = new BitSet();
    private final Set<ResourceLocation> disabledRecipes = new HashSet<>();
    private int passiveCursor;
    private long deliveryTick;
    private boolean refreshingPatterns;
    private boolean processing;
    private boolean resourcesReleased;
    private boolean releaseRequested;
    private String status = "unformed";

    public static final class Entry {
        public final IPatternDetails pattern;
        public final int slot;
        public final ResourceLocation virtualRecipe;
        public final Map<AEKey, Long> outputs;
        final List<List<GenericStack>> inputOptions;
        final Map<AEKey, Long> exactInputs;
        private LargeFactoryRecipe bound;
        private Map<AEKey, Long> signature;
        private long operations;
        private long minimumOperations = -1;
        private LargeFactoryRecipeAccess.Process knownProcess;
        private int searchCursor;
        private int catalogIndex = -1;
        public String status = "unbound";
        Entry(IPatternDetails pattern, int slot, ResourceLocation virtualRecipe) {
            this.pattern = pattern; this.slot = slot; this.virtualRecipe = virtualRecipe;
            this.outputs = LargeFactoryAmounts.of(pattern.getOutputs());
            var options = new ArrayList<List<GenericStack>>();
            var exact = new LinkedHashMap<AEKey, Long>();
            boolean disjoint = true;
            for (var input : pattern.getInputs()) {
                var choices = new ArrayList<GenericStack>();
                int visited = 0;
                for (var possible : input.getPossibleInputs()) {
                    if (++visited > 64) break;
                    if (possible != null && possible.amount() > 0 && input.getRemainingKey(possible.what()) == null)
                        choices.add(new GenericStack(possible.what(), Math.multiplyExact(possible.amount(), input.getMultiplier())));
                }
                options.add(List.copyOf(choices));
                if (choices.size() == 1) LargeFactoryAmounts.add(exact, choices.get(0).what(), choices.get(0).amount());
                else disjoint = false;
            }
            inputOptions = List.copyOf(options);
            exactInputs = disjoint ? Map.copyOf(exact) : null;
        }
        public LargeFactoryRecipe bound() { return bound; }
        public long operations() { return operations; }
        boolean hasBoundSignature(Map<AEKey, Long> actual) { return bound != null && actual.equals(signature); }
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
    public void togglePassive() { passive = !passive; publishPatterns(); saveChanges(); }

    public void bind(BlockPos controller, UUID id) {
        boolean next = controller != null && id != null;
        boolean changed = linked != next || !java.util.Objects.equals(controllerPos, controller) || !java.util.Objects.equals(machine, id);
        controllerPos = controller; machine = id; linked = next;
        boundController = next && level != null && level.getBlockEntity(controller) instanceof LargeFactoryControllerBlockEntity c ? c : null;
        boundMembership = boundController == null ? null : boundController.membership(worldPosition, id);
        if (changed) {
            onGridConnectableSidesChanged(); accessChanged();
            availability.clear(); availabilityGrid = null;
            returnSchedule.reset(); nextReturnTick = 0; returnObserving = false;
        }
    }
    public LargeFactoryControllerBlockEntity controller() {
        if (!linked || level == null || boundController == null || boundController.isRemoved()) return null;
        // The opaque ownership token is revoked before block-change/chunk-unload callbacks can reenter.
        return boundController.owns(boundMembership) ? boundController : null;
    }
    LargeFactoryControllerBlockEntity readyController() {
        if (!loadedEndpoint()) return null;
        var controller = controller();
        return controller != null && activeEndpoint() ? controller : null;
    }
    public boolean ready() { return readyController() != null; }
    boolean ready(LargeFactoryControllerBlockEntity expected) {
        return expected != null && loadedEndpoint() && controller() == expected && activeEndpoint();
    }
    boolean ready(LargeFactoryControllerBlockEntity expected, IGrid grid) {
        return expected != null && grid != null && loadedEndpoint() && controller() == expected
                && getMainNode().isActive() && getMainNode().getGrid() == grid && account() != null;
    }
    private boolean loadedEndpoint() {
        return !isRemoved() && level != null && level.getBlockEntity(worldPosition) == this;
    }
    private boolean activeEndpoint() {
        return getMainNode().isActive() && getMainNode().getGrid() != null && account() != null;
    }
    @Override public AECableType getCableConnectionType(Direction side) {
        var controller = controller();
        return controller != null && side == controller.facing().getOpposite() ? AECableType.DENSE_SMART : AECableType.NONE;
    }
    @Override public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        var controller = controller();
        return controller == null ? Collections.emptySet() : EnumSet.of(controller.facing().getOpposite());
    }
    public void saveChangedInventory(AppEngInternalInventory inventory) { saveChanges(); patternsChanged(); }
    @Override public void onChangeInventory(InternalInventory inventory, int slot) { patternsChanged(); }
    public void patternsChanged() { dirtyPatterns = true; }
    public void accessChanged() { accessGeneration = Long.MIN_VALUE; }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LargeFactoryHatchBlockEntity hatch) {
        if (level.isClientSide) return;
        long start = LargeFactoryTiming.begin();
        try { hatch.tickWork(); } finally { LargeFactoryTiming.end(hatch, "hatch", start); }
    }
    private void tickWork() {
        var hatch = this;
        if (hatch.linked && hatch.controller() == null) hatch.bind(null, null);
        hatch.refreshPatterns();
        hatch.flushRetained();
    }
    private void refreshPatterns() {
        if (refreshingPatterns) return;
        refreshingPatterns = true;
        try { refreshPatternWork(); } finally { refreshingPatterns = false; }
    }
    private void refreshPatternWork() {
        if (level == null || level.isClientSide) return;
        var catalog = LargeFactoryRecipes.get(level.getRecipeManager());
        boolean sameCatalog = catalogGeneration == catalog.generation();
        boolean accessChanged = accessGeneration == Long.MIN_VALUE;
        if (!dirtyPatterns && sameCatalog && !accessChanged && buildingEntries == null) return;
        var controller = controller();
        if (dirtyPatterns || !sameCatalog || accessChanged) {
            boolean inventoryChanged = dirtyPatterns;
            dirtyPatterns = false;
            catalogGeneration = catalog.generation();
            accessGeneration = controller == null ? -1 : controller.capabilityVersion();
            if (!inventoryChanged && sameCatalog && !crystal() && buildingEntries == null) {
                for (var entry : entries) {
                    entry.bound = null; entry.operations = 0; entry.signature = null; entry.searchCursor = 0; entry.minimumOperations = -1;
                    if (!sameCatalog) entry.knownProcess = null;
                    entry.status = controller != null && entry.knownProcess != null && !controller.access().allows(entry.knownProcess)
                            ? "process_locked" : "unbound";
                }
                publishPatterns();
                return;
            }
            previousSlots = new Entry[inventory.size()];
            for (var entry : entries) if (entry.slot >= 0) previousSlots[entry.slot] = entry;
            buildingEntries = new ArrayList<>(); buildCursor = 0; buildSameCatalog = sameCatalog;
            entries = List.of();
            publishPatterns(); // Withdraw changed/unauthorized definitions before gradually publishing the replacement.
        }
        long started = System.nanoTime();
        int processed = 0;
        int end = crystal() ? catalog.catalysts().size() : inventory.size();
        if (crystal() && (controller == null || !controller.access().allows(LargeFactoryRecipeAccess.Process.CATALYZER))) buildCursor = end;
        while (buildCursor < end) {
            if (!crystal() && inventory.getStackInSlot(buildCursor).isEmpty()) { buildCursor++; continue; }
            if (crystal() && !hasCatalyst(catalog.catalysts().get(buildCursor))) { buildCursor++; continue; }
            if (processed > 0 && System.nanoTime() - started >= 15_000) break;
            if (!crystal() && buildSameCatalog && previousSlots[buildCursor] != null
                    && previousSlots[buildCursor].pattern.getDefinition().equals(appeng.api.stacks.AEItemKey.of(inventory.getStackInSlot(buildCursor)))) {
                buildingEntries.add(previousSlots[buildCursor++]); processed++; continue;
            }
            if (!LargeFactoryWorkBudget.take(this, LargeFactoryWorkBudget.Work.PATTERN)) break;
            processed++;
            Entry entry = crystal() ? decodeCrystal(catalog.catalysts().get(buildCursor++), catalog.generation()) : decodeSlot(buildCursor++);
            if (entry == null) continue;
            buildingEntries.add(entry);
            if (buildingEntries.size() >= MAX_VIRTUAL_PATTERNS) { status = "virtual_limit"; buildCursor = end; break; }
        }
        if (buildCursor == end) {
            // Publish once: repeatedly rebuilding AE2's catalog for each slice turns indexing quadratic.
            entries = List.copyOf(buildingEntries);
            for (int i = 0; i < entries.size(); i++) entries.get(i).catalogIndex = i;
            buildingEntries = null; previousSlots = null;
            publishPatterns();
        }
    }
    private Entry decodeSlot(int slot) {
        try {
            var pattern = PatternDetailsHelper.decodePattern(inventory.getStackInSlot(slot), level);
            if (pattern == null || !pattern.supportsPushInputsToExternalInventory()
                    || pattern instanceof appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern) return null;
            var entry = new Entry(pattern, slot, null);
            var previous = previousSlots[slot];
            if (buildSameCatalog && previous != null && pattern.getDefinition().equals(previous.pattern.getDefinition()))
                entry.knownProcess = previous.knownProcess;
            return entry;
        } catch (RuntimeException invalid) { status = "invalid_pattern"; return null; }
    }
    private Entry decodeCrystal(LargeFactoryRecipe recipe, long generation) {
        if (!hasCatalyst(recipe)) return null;
        var source = level.getRecipeManager().byKey(recipe.id()).orElse(null);
        if (source == null || !(source instanceof CrystalCatalyzerRecipe catalyst)) return null;
        var fluid = catalyst.fluidInput();
        if (fluid.isEmpty()) return null;
        var item = LargeFactoryPatternEncoding.encodeProcessingPattern(List.of(new GenericStack(AEFluidKey.of(fluid), fluid.getAmount())),
                LargeFactoryAmounts.stacks(recipe.outputs()));
        int slot = -1;
        for (int i = 0; i < inventory.size(); i++) if (recipe.catalyst().test(inventory.getStackInSlot(i))) { slot = i; break; }
        final int materialSlot = slot;
        { var tag = item.getOrCreateTag();
            tag.putUUID("FactoryHatch", accountId); tag.putString("FactoryRecipe", recipe.id().toString());
            tag.putInt("MaterialSlot", materialSlot); tag.putLong("RecipeGeneration", generation);
        }
        var pattern = PatternDetailsHelper.decodePattern(item, level);
        return pattern == null ? null : new Entry(pattern, -1, recipe.id());
    }
    private void publishPatterns() {
        resolvedEntries.clear();
        var controller = controller();
        var published = new LinkedHashMap<AEKey, Entry>();
        if (!passive && controller != null) for (var entry : entries)
            if (enabled(entry) && (entry.knownProcess == null || controller.access().allows(entry.knownProcess)))
                published.putIfAbsent(entry.pattern.getDefinition(), entry);
        publishedEntries = Map.copyOf(published);
        publishedPatterns = published.values().stream().map(e -> e.pattern).toList();
        ICraftingProvider.requestUpdate(getMainNode());
    }
    public List<Entry> entries() { refreshPatterns(); return entries; }
    boolean patternIndexing() { return dirtyPatterns || buildingEntries != null; }
    public boolean enabled(Entry entry) { return entry.virtualRecipe == null ? !disabledSlots.get(entry.slot) : !disabledRecipes.contains(entry.virtualRecipe); }
    public void toggleEntry(int index) {
        var entries = entries();
        if (index < 0 || index >= entries.size()) return;
        var entry = entries.get(index);
        if (entry.virtualRecipe == null) disabledSlots.flip(entry.slot);
        else if (!disabledRecipes.remove(entry.virtualRecipe)) disabledRecipes.add(entry.virtualRecipe);
        publishPatterns(); saveChanges();
    }
    boolean hasCatalyst(LargeFactoryRecipe recipe) {
        if (recipe.catalyst() == null) return !crystal();
        if (!crystal()) return false;
        if (recipe.catalyst().test(ItemStack.EMPTY)) return false;
        for (int slot = 0; slot < inventory.size(); slot++) if (recipe.catalyst().test(inventory.getStackInSlot(slot))) return true;
        return false;
    }
    public Entry find(IPatternDetails pattern) {
        refreshPatterns();
        var entry = resolvedEntries.get(pattern);
        if (entry == null) {
            entry = publishedEntries.get(pattern.getDefinition());
            if (entry != null) resolvedEntries.put(pattern, entry);
        }
        return entry;
    }
    LargeFactoryRecipe bindRecipe(Entry entry, Map<AEKey, Long> actual) {
        return bindRecipe(entry, actual, controller());
    }
    LargeFactoryRecipe bindRecipe(Entry entry, Map<AEKey, Long> actual, LargeFactoryControllerBlockEntity controller) {
        if (controller == null || !enabled(entry) || !containsEntry(entry)) return null;
        if (entry.bound != null && actual.equals(entry.signature) && controller.access().allows(entry.bound.process()) && hasCatalyst(entry.bound)) return entry.bound;
        if (!actual.equals(entry.signature)) { entry.signature = Map.copyOf(actual); entry.searchCursor = 0; entry.bound = null; entry.operations = 0; }
        var candidates = LargeFactoryRecipes.get(level.getRecipeManager()).candidates(entry.pattern.getPrimaryOutput().what());
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
    private boolean containsEntry(Entry entry) {
        var current = entries();
        int index = entry.catalogIndex;
        // Rebuilding withdraws the old list immediately; identity also rejects entries from
        // another hatch or a replaced slot. Reused entries get their new index on publication.
        return index >= 0 && index < current.size() && current.get(index) == entry;
    }
    @Override public List<IPatternDetails> getAvailablePatterns() {
        if (passive || controller() == null) return List.of();
        refreshPatterns();
        return publishedPatterns;
    }
    @Override public boolean isBusy() {
        return availableController() == null;
    }
    private LargeFactoryControllerBlockEntity availableController() {
        if (passive || processing) return null;
        var controller = readyController();
        var account = account();
        return controller == null || account == null || !account.resources.isEmpty()
                || controller.budget().remainingOperations(level.getGameTime()) <= 0 ? null : controller;
    }
    @Override public long getBatchCapacity(IPatternDetails details) {
        var controller = availableController();
        if (controller == null) return 0;
        var entry = find(details);
        return entry == null || controller() != controller ? 0
                : controller.budget().remainingOperations(level.getGameTime()) / Math.max(1, entry.operations);
    }
    @Override public long pushBatch(IPatternDetails details, KeyCounter[] template, long requested) {
        return pushBatchWithReturns(details, template, requested, null);
    }
    @Override public long pushBatch(IPatternDetails details, KeyCounter[] template, long requested, BatchJobView job) {
        return pushBatchWithReturns(details, template, requested, job instanceof DeferredCraftingProvider.Job deferred ? deferred.deferredOutputSink() : null);
    }
    private long pushBatchWithReturns(IPatternDetails details, KeyCounter[] template, long requested, OutputSink returns) {
        long start = LargeFactoryTiming.begin();
        try { return pushBatchWork(details, template, requested, returns); }
        finally { LargeFactoryTiming.end(this, "active", start); }
    }
    private long pushBatchWork(IPatternDetails details, KeyCounter[] template, long requested, OutputSink returns) {
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
        if (ledger == null) ledger = LargeFactoryLedger.get(server);
        if (cachedAccount == null) cachedAccount = ledger.claim(accountId, server, worldPosition);
        return cachedAccount != null && !cachedAccount.parcel ? cachedAccount : null;
    }
    long extract(IGrid grid, AEKey key, long amount, Actionable mode) {
        if (amount <= 0) return 0;
        long tick = level.getGameTime();
        if (availabilityTick != tick || availabilityGrid != grid) {
            availabilityTick = tick; availabilityGrid = grid; availability.clear();
        }
        var hint = availability.get(key);
        if (mode == Actionable.SIMULATE && hint != null) return Math.min(amount, hint);
        if (!LargeFactoryWorkBudget.take(this, LargeFactoryWorkBudget.Work.NETWORK)) return 0;
        long actual = grid.getStorageService().getInventory().extract(key, mode == Actionable.SIMULATE ? Long.MAX_VALUE : amount, mode, actionSource);
        if (actual < 0 || mode == Actionable.MODULATE && actual > amount) throw new IllegalStateException("Invalid ME extraction receipt");
        if (mode == Actionable.SIMULATE) {
            availability.put(key, actual);
            return Math.min(amount, actual);
        }
        // Hints only price a batch; actual receipts remain authoritative, including partial failures.
        if (actual < amount || hint == null) availability.remove(key);
        else availability.put(key, Math.max(0, hint - actual));
        return actual;
    }
    boolean cachedMissing(Entry entry) {
        if (entry.exactInputs == null || availabilityTick != level.getGameTime() || availabilityGrid != getMainNode().getGrid()) return false;
        for (var input : entry.exactInputs.entrySet()) {
            var available = availability.get(input.getKey());
            if (available != null && available < input.getValue()) return true;
        }
        return false;
    }
    long minimumOperations(Entry entry) {
        if (entry.operations > 0) return entry.operations;
        if (entry.minimumOperations >= 0) return entry.minimumOperations;
        long minimum = Long.MAX_VALUE;
        for (var recipe : LargeFactoryRecipes.get(level.getRecipeManager()).candidates(entry.pattern.getPrimaryOutput().what())) {
            long count = recipe.outputOperations(entry.outputs);
            if (count > 0) minimum = Math.min(minimum, count);
        }
        return entry.minimumOperations = minimum == Long.MAX_VALUE ? 0 : minimum;
    }
    double extractEnergy(IGrid grid, double amount, Actionable mode) {
        if (amount <= 0 || !LargeFactoryWorkBudget.take(this, LargeFactoryWorkBudget.Work.NETWORK)) return 0;
        return grid.getEnergyService().extractAEPower(amount, mode, appeng.api.config.PowerMultiplier.ONE);
    }
    void ledgerChanged() {
        if (level instanceof ServerLevel server) { if (ledger == null) ledger = LargeFactoryLedger.get(server); ledger.setDirty(); }
        nextReturnTick = 0;
        // Resource balances and commits live only in SavedData. Inventory, mode and account
        // identity changes separately save block NBT at their own mutation sites.
    }
    UUID origin(IGrid grid) {
        var account = account();
        var identity = grid.getService(LargeFactoryNetworkIdentity.class);
        if (account == null) return null;
        if (!account.empty()) return identity.contains(account.origin) ? account.origin : null;
        if (identity.contains(account.origin)) return account.origin;
        return identity.externalAnchor(getMainNode().getNode());
    }
    void deliverAfter(long tick) { deliveryTick = tick; }
    public void flushRetained() {
        if (processing || level == null || level.isClientSide || level.getGameTime() < Math.max(deliveryTick, nextReturnTick)) return;
        var account = account();
        var grid = getMainNode().getGrid();
        if (account == null || account.empty() || grid == null || !getMainNode().isActive()) return;
        if (!grid.getService(LargeFactoryNetworkIdentity.class).contains(account.origin)) { status = "origin_missing"; return; }
        processing = true;
        try {
            long tick = level.getGameTime();
            if (!returnObserving || nextReturnTick > 0 && tick > nextReturnTick) returnSchedule.beginObservation(tick);
            returnObserving = true;
            int result = transferAccount(account, grid);
            if (!account.empty()) nextReturnTick = tick + (result == 0 ? 1
                    : result == 2 ? returnSchedule.success(tick) : returnSchedule.failure(tick, 100));
            else { returnSchedule.success(level.getGameTime()); nextReturnTick = 0; returnObserving = false; }
        } finally { processing(false); }
    }
    /** 0 unavailable/budgeted out; 1 actual blocked receipt; 2 actual progress. */
    private int transferAccount(LargeFactoryLedger.Account account, IGrid grid) {
        var storage = grid.getStorageService().getInventory();
        boolean attempted = false, progressed = false;
        for (var entry : List.copyOf(account.resources.entrySet())) {
            if (getMainNode().getGrid() != grid) break;
            if (!LargeFactoryWorkBudget.take(this, LargeFactoryWorkBudget.Work.NETWORK)) break;
            attempted = true;
            long inserted = storage.insert(entry.getKey(), entry.getValue(), Actionable.MODULATE, actionSource);
            if (inserted < 0 || inserted > entry.getValue()) throw new IllegalStateException("Invalid ME insertion receipt");
            if (inserted > 0) {
                if (inserted == entry.getValue()) account.resources.remove(entry.getKey());
                else account.resources.put(entry.getKey(), entry.getValue() - inserted);
                availability.remove(entry.getKey());
                ledgerChanged(); progressed = true;
            }
        }
        if (getMainNode().getGrid() == grid && account.energyCreditAE > 0
                && LargeFactoryWorkBudget.take(this, LargeFactoryWorkBudget.Work.NETWORK)) {
            attempted = true;
            double before = account.energyCreditAE;
            account.energyCreditAE = grid.getEnergyService().injectPower(before, Actionable.MODULATE);
            if (account.energyCreditAE < before) { ledgerChanged(); progressed = true; }
        }
        status = account.empty() ? "ready" : "return_blocked";
        return progressed ? 2 : attempted ? 1 : 0;
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
        cachedAccount = null;
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

    public boolean passiveStep() {
        long start = LargeFactoryTiming.begin();
        try { return LargeFactoryPassive.step(this); }
        finally { LargeFactoryTiming.end(this, "passive", start); }
    }
    Entry nextPassiveEntry() {
        var entries = entries();
        if (entries.isEmpty()) return null;
        return entries.get(Math.floorMod(passiveCursor++, entries.size()));
    }
    @Override public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putUUID("Account", accountId);
        tag.putInt("SlotCount", inventory.size());
        inventory.writeToNBT(tag, "Patterns");
        tag.putBoolean("Passive", passive);
        tag.putLongArray("DisabledSlots", disabledSlots.toLongArray());
        var disabled = new ListTag();
        disabledRecipes.forEach(id -> disabled.add(StringTag.valueOf(id.toString())));
        tag.put("DisabledRecipes", disabled);
    }
    @Override public void loadTag(CompoundTag tag) {
        super.loadTag(tag);
        accountId = tag.hasUUID("Account") ? tag.getUUID("Account") : UUID.randomUUID();
        ledger = null; cachedAccount = null; availability.clear(); availabilityGrid = null; nextReturnTick = 0; returnObserving = false; returnSchedule.reset();
        // Older crystal inventories expand in place; reading their saved slots preserves every catalyst.
        if (crystal()) inventory = createInventory(LargeFactoryConfig.crystalSlots());
        inventory.readFromNBT(tag, "Patterns");
        passive = tag.getBoolean("Passive");
        disabledSlots.clear(); disabledSlots.or(BitSet.valueOf(tag.getLongArray("DisabledSlots")));
        disabledRecipes.clear();
        for (var value : tag.getList("DisabledRecipes", Tag.TAG_STRING)) {
            var id = ResourceLocation.tryParse(value.getAsString());
            if (id != null) disabledRecipes.add(id);
        }
        linked = false; controllerPos = null; boundController = null; boundMembership = null; machine = null; dirtyPatterns = true; resourcesReleased = false;
    }
}
