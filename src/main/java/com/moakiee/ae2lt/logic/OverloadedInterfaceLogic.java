package com.moakiee.ae2lt.logic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.function.BiPredicate;

import org.jetbrains.annotations.Nullable;

import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity;
import com.moakiee.ae2lt.debug.WirelessIoPerformanceProbe;
import com.moakiee.ae2lt.logic.energy.PowerCostUtil;

import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.config.Settings;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.upgrades.UpgradeInventories;
import appeng.core.definitions.AEItems;
import appeng.helpers.InterfaceLogic;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.util.ConfigInventory;
import appeng.util.ConfigMenuInventory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.List;

public class OverloadedInterfaceLogic extends InterfaceLogic {
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();

    private static final long OVERLOADED_BYTES = 1024L;

    private static final Field F_CONFIG;
    private static final Field F_STORAGE;
    private static final Field F_UPGRADES;
    private static final Field F_CRAFTING_TRACKER;
    private static final Method M_ON_CONFIG_CHANGED;
    private static final Method M_ON_STORAGE_CHANGED;
    private static final Method M_ON_UPGRADES_CHANGED;

    static {
        try {
            F_CONFIG = InterfaceLogic.class.getDeclaredField("config");
            F_CONFIG.setAccessible(true);
            F_STORAGE = InterfaceLogic.class.getDeclaredField("storage");
            F_STORAGE.setAccessible(true);
            F_UPGRADES = InterfaceLogic.class.getDeclaredField("upgrades");
            F_UPGRADES.setAccessible(true);
            F_CRAFTING_TRACKER = InterfaceLogic.class.getDeclaredField("craftingTracker");
            F_CRAFTING_TRACKER.setAccessible(true);
            M_ON_CONFIG_CHANGED = InterfaceLogic.class.getDeclaredMethod("onConfigRowChanged");
            M_ON_CONFIG_CHANGED.setAccessible(true);
            M_ON_STORAGE_CHANGED = InterfaceLogic.class.getDeclaredMethod("onStorageChanged");
            M_ON_STORAGE_CHANGED.setAccessible(true);
            M_ON_UPGRADES_CHANGED = InterfaceLogic.class.getDeclaredMethod("onUpgradesChanged");
            M_ON_UPGRADES_CHANGED.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to init reflection for OverloadedInterfaceLogic", e);
        }
    }

    private final OverloadedInterfaceBlockEntity owner;
    private final ProxiedStorageInv proxiedStorage;
    private final int slotCount;
    private final appeng.api.upgrades.IUpgradeInventory ourUpgrades;
    private final appeng.helpers.MultiCraftingTracker craftingTrackerRef;

    public OverloadedInterfaceLogic(IManagedGridNode gridNode,
                                    OverloadedInterfaceBlockEntity host,
                                    Item is, int slots) {
        super(gridNode, host, is, slots);
        this.owner = host;
        this.slotCount = slots;

        try {
            this.craftingTrackerRef = (appeng.helpers.MultiCraftingTracker) F_CRAFTING_TRACKER.get(this);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to read craftingTracker", e);
        }

        var newConfig = new OverloadedConfigInv(
                com.google.common.collect.Sets.newHashSet(AEKeyTypes.getAll()), null,
                GenericStackInv.Mode.CONFIG_STACKS, slots,
                () -> {
                    invalidateViewCaches();
                    invokeQuietly(M_ON_CONFIG_CHANGED, this);
                });
        newConfig.owner = host;
        setField(F_CONFIG, newConfig);
        newConfig.useRegisteredCapacities();
        for (var type : AEKeyTypes.getAll()) {
            newConfig.setCapacity(type, overloadedCap(type));
        }

        proxiedStorage = new ProxiedStorageInv(
                this, com.google.common.collect.Sets.newHashSet(AEKeyTypes.getAll()),
                null,
                slots,
                () -> {
                    invalidateViewCaches();
                    invokeQuietly(M_ON_STORAGE_CHANGED, this);
                });
        setField(F_STORAGE, proxiedStorage);
        proxiedStorage.useRegisteredCapacities();
        for (var type : AEKeyTypes.getAll()) {
            proxiedStorage.setCapacity(type, overloadedCap(type));
        }

        var newUpgrades = UpgradeInventories.forMachine(is, 4, () -> {
            invalidateViewCaches();
            invokeQuietly(M_ON_UPGRADES_CHANGED, this);
            host.invalidateInductionCardCache();
        });
        setField(F_UPGRADES, newUpgrades);
        this.ourUpgrades = newUpgrades;

        // Relies on ClassToInstanceMap.putInstance last-write-wins to replace
        // the stocking Ticker registered by the InterfaceLogic constructor; if
        // AE2 ever rejects duplicate services, the vanilla ticker would return
        // and ping-pong items through the proxied storage.
        mainNode.addService(IGridTickable.class, new ProxyTicker());
    }

    OverloadedInterfaceBlockEntity getOwner() {
        return owner;
    }

    public ProxiedStorageInv getProxiedStorage() {
        return proxiedStorage;
    }

    public void invalidateViewCaches() {
        proxiedStorage.invalidateViewCaches();
    }

    /**
     * Sets config stack while suppressing the automatic unlimited-cancel in OverloadedConfigInv.
     * Used by toggleUnlimited to set amount=1 without cancelling the unlimited flag it just set.
     */
    public void setConfigStackSuppressed(int slot, @Nullable GenericStack stack) {
        var cfg = getConfig();
        if (cfg instanceof OverloadedConfigInv oci) {
            oci.suppressUnlimitedCancel = true;
            try { oci.setStack(slot, stack); }
            finally { oci.suppressUnlimitedCancel = false; }
        } else {
            cfg.setStack(slot, stack);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Drops / clear — storage is virtual, MUST NOT drop or clear ME items
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public void addDrops(List<ItemStack> drops) {
        for (var is : getUpgrades()) {
            if (!is.isEmpty()) drops.add(is);
        }
        var filterStack = owner.getFilterInv().getStackInSlot(0);
        if (!filterStack.isEmpty()) drops.add(filterStack);
        owner.addImportBufferDrops(drops);
    }

    @Override
    public void clearContent() {
        getUpgrades().clear();
        owner.getFilterInv().setItemDirect(0, ItemStack.EMPTY);
        owner.clearImportBuffer();
    }

    private static final long REACTIVE_COOLDOWN_TICKS = 5;
    private long lastReactiveTick = -1;

    private boolean invokeCrafting(int slot, AEKey what, long amount) {
        var grid = mainNode.getGrid();
        if (grid == null || what == null) return false;
        return craftingTrackerRef.handleCrafting(slot, what, amount,
                host.getBlockEntity().getLevel(),
                grid.getCraftingService(), actionSource);
    }

    void onExtractDeficit(AEKey what) {
        if (!ourUpgrades.isInstalled(AEItems.CRAFTING_CARD)) return;
        var level = host.getBlockEntity().getLevel();
        if (level == null) return;
        long now = level.getGameTime();
        if (now - lastReactiveTick < REACTIVE_COOLDOWN_TICKS) return;
        lastReactiveTick = now;

        var cfg = getConfig();
        for (int i = 0; i < slotCount; i++) {
            var key = cfg.getKey(i);
            if (key == null || !key.equals(what)) continue;
            long cap = owner.isSlotUnlimited(i) ? overloadedCap(what) : cfg.getAmount(i);
            invokeCrafting(i, what, cap);
            break;
        }
    }

    private static long overloadedCap(AEKey what) {
        return overloadedCap(what.getType());
    }

    private static long overloadedCap(AEKeyType type) {
        return OVERLOADED_BYTES * type.getAmountPerByte();
    }

    private void setField(Field f, Object value) {
        try { f.set(this, value); } catch (IllegalAccessException e) {
            throw new IllegalStateException("Reflection set failed", e);
        }
    }

    private static void invokeQuietly(Method m, Object target) {
        try {
            m.invoke(target);
        } catch (Exception e) {
            LOG.warn("Reflection invoke failed: {}.{}", m.getDeclaringClass().getSimpleName(), m.getName(), e);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Ticker — no stocking, only handles crafting card deficit requests
    // ══════════════════════════════════════════════════════════════════════

    private class ProxyTicker implements IGridTickable {
        // 自定义 1-5 tick 档:有活 URGENT → 1 tick,空转 SLOWER → 最慢 5 tick
        // (AE2 默认 TickRates.Interface 是 5-120,空转太慢、响应滞后;这里覆盖为响应优先)
        private static final int MIN_TICKS = 1;
        private static final int MAX_TICKS = 5;

        @Override
        public TickingRequest getTickingRequest(IGridNode node) {
            // The interface actively alerts this ticker whenever its mode,
            // connection set or I/O configuration changes. AE2 15 rejects
            // alertDevice calls unless canBeAlerted is enabled on the request.
            return new TickingRequest(MIN_TICKS, MAX_TICKS, false, true);
        }

        @Override
        public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
            // Keep polling at the maximum interval while the node is inactive.
            // Channel/power changes and remote eject targets do not reliably
            // produce an alertDevice callback for this node, so SLEEP can leave
            // the interface stuck after the external condition recovers.
            if (!mainNode.isActive()) return TickRateModulation.IDLE;

            boolean measured = WirelessIoPerformanceProbe.shouldMeasureGridTicker()
                    && owner.getInterfaceMode() == OverloadedInterfaceBlockEntity.InterfaceMode.WIRELESS;
            long started = measured ? System.nanoTime() : 0;
            boolean hasItemIoWork;
            try {
                hasItemIoWork = owner.hasGridItemIoWork();
                if (hasItemIoWork) owner.tickGridItemIo();
            } finally {
                if (measured) {
                    WirelessIoPerformanceProbe.recordWirelessInterfaceIo(
                            System.nanoTime() - started, owner.getConnections().size(),
                            owner.getIOSpeedMode() == OverloadedInterfaceBlockEntity.IOSpeedMode.FAST);
                }
            }

            boolean craftingCardInstalled = ourUpgrades.isInstalled(AEItems.CRAFTING_CARD);
            if (!craftingCardInstalled) {
                return OverloadedInterfaceTickDecider.gridTickModulation(
                        hasItemIoWork, false, false);
            }
            var grid = mainNode.getGrid();
            if (grid == null) return TickRateModulation.IDLE;

            var cache = grid.getStorageService().getCachedInventory();
            var cfg = getConfig();
            boolean didWork = false;
            for (int i = 0; i < slotCount; i++) {
                var cfgStack = cfg.getStack(i);
                if (cfgStack == null) continue;
                long cap = owner.isSlotUnlimited(i) ? Long.MAX_VALUE : cfgStack.amount();
                if (cap == Long.MAX_VALUE) {
                    didWork |= invokeCrafting(i, cfgStack.what(), overloadedCap(cfgStack.what()));
                } else {
                    long deficit = cap - cache.get(cfgStack.what());
                    if (deficit > 0) {
                        didWork |= invokeCrafting(i, cfgStack.what(), deficit);
                    }
                }
            }
            return OverloadedInterfaceTickDecider.gridTickModulation(
                    hasItemIoWork, true, didWork);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  OverloadedConfigInv — bypasses getMaxStackSize() clamp
    // ══════════════════════════════════════════════════════════════════════

    static class OverloadedConfigInv extends ConfigInventory {
        @Nullable OverloadedInterfaceBlockEntity owner;
        boolean suppressUnlimitedCancel;
        private final Set<AEKeyType> supportedTypes;
        @Nullable
        private final BiPredicate<Integer, AEKey> slotFilter;

        OverloadedConfigInv(Set<AEKeyType> supportedTypes,
                            @Nullable BiPredicate<Integer, AEKey> slotFilter,
                            GenericStackInv.Mode mode, int size,
                            @Nullable Runnable listener) {
            super(key -> supportedTypes.contains(key.getType()), mode, size, listener, false);
            this.supportedTypes = supportedTypes;
            this.slotFilter = slotFilter;
        }

        public boolean isSupportedType(AEKeyType type) {
            return supportedTypes.contains(type);
        }

        public boolean isSupportedKey(AEKey what) {
            return what != null && isSupportedType(what.getType());
        }

        public boolean isAllowedIn(int slot, AEKey what) {
            return isSupportedKey(what) && (slotFilter == null || slotFilter.test(slot, what));
        }

        @Override
        public long getMaxAmount(AEKey key) {
            return getCapacity(key.getType());
        }

        @Override
        public long insert(int slot, AEKey what, long amount, Actionable mode) {
            if (!isAllowedIn(slot, what)) {
                return 0;
            }
            return super.insert(slot, what, amount, mode);
        }

        @Override
        public void setStack(int slot, @Nullable GenericStack stack) {
            if (stack != null && !isAllowedIn(slot, stack.what())) {
                return;
            }
            super.setStack(slot, stack);
            if (!suppressUnlimitedCancel && owner != null && owner.isSlotUnlimited(slot)) {
                var level = owner.getLevel();
                if (level != null && !level.isClientSide()) {
                    owner.setSlotUnlimited(slot, false);
                }
            }
            if (owner != null) {
                var level = owner.getLevel();
                if (level != null && !level.isClientSide()) {
                    owner.onGridIoConfigChanged();
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ProxiedStorageInv — ME network proxy
    //
    //  • getStack/getAmount return REAL display values:
    //      unlimited  → actual ME network quantity
    //      limited    → min(configAmount, networkQuantity)
    //  • extract() proxies to the ME network (capped per config)
    //  • insert()  proxies to the ME network (crafted items, pipe input)
    //  • getAvailableStacks() exposes only configured keys to avoid grid cache inflation
    //  • Re-entrancy guard prevents recursion through InterfaceInventory
    // ══════════════════════════════════════════════════════════════════════

    public static class ProxiedStorageInv extends OverloadedConfigInv {
        private final OverloadedInterfaceLogic logic;
        private boolean proxying = false;

        public boolean isNetworkOperationInProgress() {
            return proxying;
        }

        public void runWithNetworkGuard(Runnable action) {
            if (proxying) return;
            proxying = true;
            try {
                action.run();
            } finally {
                proxying = false;
                invalidateViewCaches();
            }
        }
        /** Rebuilt (not reset) each refresh: KeyCounter.reset() keeps zeroed keys, which would leak 0-amount entries to callers. */
        private KeyCounter availableStacksCache = new KeyCounter();
        private long availableStacksCacheTick = Long.MIN_VALUE;
        private @Nullable FuzzyMode availableStacksCacheFuzzyMode;
        /** Per-slot tick cache for the getStack/getAmount display path. External
         *  IItemHandler pollers hit these every tick; without caching each call
         *  re-simulates the whole ME network. -1 = not yet computed this tick. */
        private final long[] displayAmountCache;
        private long displayAmountCacheTick = Long.MIN_VALUE;
        private final Object2LongLinkedOpenHashMap<AEKey> capByKey;
        private @Nullable Object2LongLinkedOpenHashMap<AEKey> capByVariant;
        private @Nullable Object2LongOpenHashMap<AEKey> amountByVariant;
        private @Nullable CacheInvalidatingStorage networkInventory;

        ProxiedStorageInv(OverloadedInterfaceLogic logic,
                          Set<AEKeyType> supportedTypes,
                          @Nullable BiPredicate<Integer, AEKey> slotFilter,
                          int size, @Nullable Runnable listener) {
            super(supportedTypes, slotFilter, GenericStackInv.Mode.STORAGE, size, listener);
            this.logic = logic;
            this.displayAmountCache = new long[size];
            this.capByKey = new Object2LongLinkedOpenHashMap<>(size);
        }

        @Override
        public long getCapacity(AEKeyType keyType) {
            // AE2 adapts this to IItemHandler slot limit; keep unbounded so external
            // automation does not re-impose the configured stack cap on unlimited slots.
            if (keyType == AEKeyType.items()) return Integer.MAX_VALUE;
            return super.getCapacity(keyType);
        }

        private IActionSource src() {
            return IActionSource.ofMachine(logic.owner);
        }

        public ConfigInventory cfg() {
            return logic.getConfig();
        }

        public MEStorage getNetworkInventory(IStorageService storageService) {
            var delegate = storageService.getInventory();
            var inventory = networkInventory;
            if (inventory == null || inventory.storageService != storageService || inventory.delegate != delegate) {
                inventory = new CacheInvalidatingStorage(storageService, delegate);
                networkInventory = inventory;
            }
            return inventory;
        }

        private final class CacheInvalidatingStorage implements MEStorage {
            private final IStorageService storageService;
            private final MEStorage delegate;

            private CacheInvalidatingStorage(IStorageService storageService, MEStorage delegate) {
                this.storageService = storageService;
                this.delegate = delegate;
            }

            @Override
            public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
                try {
                    return delegate.insert(key, amount, mode, source);
                } finally {
                    if (mode == Actionable.MODULATE && key != null && amount > 0) {
                        invalidateViewCaches();
                        storageService.invalidateCache();
                    }
                }
            }

            @Override
            public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
                try {
                    return delegate.extract(key, amount, mode, source);
                } finally {
                    if (mode == Actionable.MODULATE && key != null && amount > 0) {
                        invalidateViewCaches();
                        storageService.invalidateCache();
                    }
                }
            }

            @Override
            public boolean isPreferredStorageFor(AEKey key, IActionSource source) {
                return delegate.isPreferredStorageFor(key, source);
            }

            @Override
            public void getAvailableStacks(KeyCounter out) {
                delegate.getAvailableStacks(out);
            }

            @Override
            public net.minecraft.network.chat.Component getDescription() {
                return delegate.getDescription();
            }
        }

        public long capForSlot(int slot) {
            if (logic.owner.isSlotUnlimited(slot)) return Long.MAX_VALUE;
            long amt = cfg().getAmount(slot);
            if (amt > 0) return amt;
            var key = cfg().getKey(slot);
            return key != null ? key.getType().getAmountPerUnit() : Long.MAX_VALUE;
        }

        private long displayAmount(int slot, AEKey key) {
            var be = logic.host.getBlockEntity();
            var level = be != null ? be.getLevel() : null;
            long now = level != null ? level.getGameTime() : Long.MIN_VALUE;
            boolean cacheable = level != null && slot >= 0 && slot < displayAmountCache.length;
            if (cacheable) {
                if (now != displayAmountCacheTick) {
                    Arrays.fill(displayAmountCache, -1L);
                    displayAmountCacheTick = now;
                }
                long cached = displayAmountCache[slot];
                if (cached >= 0) return cached;
            }
            long amt = visibleNetworkAmount(key, capForSlot(slot));
            if (cacheable) displayAmountCache[slot] = amt;
            return amt;
        }

        private long visibleNetworkAmount(AEKey key, long cap) {
            var grid = logic.mainNode.getGrid();
            if (grid == null || key == null || cap <= 0) return 0;
            boolean wasProxying = proxying;
            proxying = true;
            try {
                var storageService = grid.getStorageService();
                long reported = storageService.getCachedInventory().get(key);
                var network = storageService.getInventory();
                long simulated = network != null ? network.extract(key, cap, Actionable.SIMULATE, src()) : 0;
                return OverloadedAmountMath.mergeReportedAndSimulatedAmount(reported, simulated, cap);
            } finally {
                proxying = wasProxying;
            }
        }

        private boolean matchesConfiguredSlot(int slot, AEKey what) {
            var configured = cfg().getKey(slot);
            if (configured == null) return false;
            if (configured.equals(what)) return true;
            if (!logic.ourUpgrades.isInstalled(AEItems.FUZZY_CARD)) return false;
            if (!configured.supportsFuzzyRangeSearch()) return false;
            var fuzzyMode = logic.getConfigManager().getSetting(Settings.FUZZY_MODE);
            return configured.fuzzyEquals(what, fuzzyMode);
        }

        private long exposedAmount(AEKey what) {
            var grid = logic.mainNode.getGrid();
            if (grid == null) return 0;
            // Aggregate caps first: same-key slots mirror the same network
            // stock, so summing per-slot visible amounts would double-count
            long capSum = 0;
            for (int i = 0; i < size(); i++) {
                if (!matchesConfiguredSlot(i, what)) continue;
                capSum = OverloadedAmountMath.saturatingAdd(capSum, capForSlot(i));
                if (capSum == Long.MAX_VALUE) break;
            }
            return capSum > 0 ? visibleNetworkAmount(what, capSum) : 0;
        }

        // ── Display: server queries ME network & syncs stacks[]; client uses synced stacks[] ─

        @Override
        public @Nullable GenericStack getStack(int slot) {
            if (proxying) return null;
            if (logic.mainNode.getGrid() == null) {
                return super.getStack(slot);
            }
            var key = cfg().getKey(slot);
            if (key == null) {
                stacks[slot] = null;
                return null;
            }
            long amt = displayAmount(slot, key);
            var cached = stacks[slot];
            if (amt > 0 && cached != null && cached.amount() == amt && cached.what().equals(key)) {
                return cached;
            }
            var result = amt > 0 ? new GenericStack(key, amt) : null;
            stacks[slot] = result;
            return result;
        }

        @Override
        public @Nullable AEKey getKey(int slot) {
            if (logic.mainNode.getGrid() == null) {
                return super.getKey(slot);
            }
            return cfg().getKey(slot);
        }

        @Override
        public long getAmount(int slot) {
            if (proxying) return 0;
            if (logic.mainNode.getGrid() == null) {
                return super.getAmount(slot);
            }
            var key = cfg().getKey(slot);
            if (key == null) return 0;
            return displayAmount(slot, key);
        }

        @Override
        public boolean isAllowedIn(int slot, AEKey what) {
            return isSupportedKey(what);
        }

        @Override
        public ConfigMenuInventory createMenuWrapper() {
            return new ProxiedMenuWrapper(this);
        }

        // ── Per-slot insert: proxy to ME (crafted items / InterfaceLogic) ─

        @Override
        public long insert(int slot, AEKey what, long amount, Actionable mode) {
            if (what == null || amount <= 0) return 0;
            return proxyInsert(what, amount, mode);
        }

        // ── Per-slot extract: proxy to ME, cap by config ────────────────

        @Override
        public long extract(int slot, AEKey what, long amount, Actionable mode) {
            if (what == null || amount <= 0) return 0;
            var key = cfg().getKey(slot);
            if (key == null || !key.equals(what)) return 0;
            long capped = Math.min(amount, capForSlot(slot));
            long extracted = proxyExtract(what, capped, mode);
            if (extracted > 0 && mode == Actionable.MODULATE) {
                logic.onExtractDeficit(what);
            }
            return extracted;
        }

        // ── MEStorage extract: direct pass-through to ME network ────────

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            if (proxying) return 0;
            if (what == null || amount <= 0) return 0;
            long exposed = exposedAmount(what);
            if (exposed <= 0) return 0;
            long extracted = proxyExtract(what, Math.min(amount, exposed), mode);
            if (extracted > 0 && mode == Actionable.MODULATE) {
                logic.onExtractDeficit(what);
            }
            return extracted;
        }

        // ── MEStorage insert: proxy to ME ───────────────────────────────

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            if (proxying) return 0;
            if (what == null || amount <= 0) return 0;
            return proxyInsert(what, amount, mode);
        }

        // ── Re-entrant-safe proxies (public for Menu-level click handling) ─

        public long proxyExtract(AEKey what, long amount, Actionable mode) {
            if (proxying) return 0;
            proxying = true;
            boolean mutationAttempted = false;
            try {
                var grid = logic.mainNode.getGrid();
                if (grid == null) return 0;
                var network = getNetworkInventory(grid.getStorageService());
                long affordable = PowerCostUtil.maxAffordable(grid, what, amount);
                if (affordable <= 0) return 0;
                mutationAttempted = mode == Actionable.MODULATE;
                long extracted = network.extract(what, affordable, mode, src());
                if (extracted > 0 && mode == Actionable.MODULATE) {
                    PowerCostUtil.consume(grid, what, extracted);
                }
                return extracted;
            } finally {
                proxying = false;
                if (mutationAttempted) invalidateViewCaches();
            }
        }

        public long proxyInsert(AEKey what, long amount, Actionable mode) {
            if (proxying) return 0;
            proxying = true;
            boolean mutationAttempted = false;
            try {
                var grid = logic.mainNode.getGrid();
                if (grid == null) return 0;
                var network = getNetworkInventory(grid.getStorageService());
                long affordable = PowerCostUtil.maxAffordable(grid, what, amount);
                if (affordable <= 0) return 0;
                mutationAttempted = mode == Actionable.MODULATE;
                long inserted = network.insert(what, affordable, mode, src());
                if (inserted > 0 && mode == Actionable.MODULATE) {
                    PowerCostUtil.consume(grid, what, inserted);
                }
                return inserted;
            } finally {
                proxying = false;
                if (mutationAttempted) invalidateViewCaches();
            }
        }

        public void setDisplayStack(int slot, @Nullable GenericStack stack) {
            stacks[slot] = stack;
        }

        void invalidateViewCaches() {
            availableStacksCacheTick = Long.MIN_VALUE;
            availableStacksCacheFuzzyMode = null;
            displayAmountCacheTick = Long.MIN_VALUE;
        }

        // ── Grid cache: expose a configured view of the ME network ───────
        // InterfaceInventory (registered on our grid) iterates stacks[] via
        // per-slot getStack(), so this override only affects external MEStorage
        // callers (e.g. storage bus from another network). The proxying guard
        // prevents recursion if the same grid re-enters.

        @Override
        public void getAvailableStacks(KeyCounter out) {
            if (proxying) return;
            var grid = logic.mainNode.getGrid();
            if (grid == null) return;
            var be = logic.host.getBlockEntity();
            var level = be != null ? be.getLevel() : null;
            long now = level != null ? level.getGameTime() : Long.MIN_VALUE;
            boolean fuzzy = logic.ourUpgrades.isInstalled(AEItems.FUZZY_CARD);
            var fuzzyMode = fuzzy ? logic.getConfigManager().getSetting(Settings.FUZZY_MODE) : null;
            if (level != null && now == availableStacksCacheTick
                    && fuzzyMode == availableStacksCacheFuzzyMode) {
                for (var entry : availableStacksCache) {
                    out.add(entry.getKey(), entry.getLongValue());
                }
                return;
            }
            proxying = true;
            try {
                var fresh = new KeyCounter();
                // Aggregate caps per key: same-key slots mirror the same
                // network stock and must not double-count
                for (int slot = 0; slot < size(); slot++) {
                    var key = cfg().getKey(slot);
                    if (key == null) continue;
                    capByKey.put(key, OverloadedAmountMath.saturatingAdd(capByKey.getLong(key), capForSlot(slot)));
                }

                // A network variant can match several configured keys. Aggregate
                // every matching slot cap, but retain only one copy of the
                // physical network amount for that variant.
                if (fuzzy && capByVariant == null) {
                    capByVariant = new Object2LongLinkedOpenHashMap<>(size());
                    amountByVariant = new Object2LongOpenHashMap<>(size());
                }
                var caps = capByKey.object2LongEntrySet().fastIterator();
                while (caps.hasNext()) {
                    var capEntry = caps.next();
                    var key = capEntry.getKey();
                    long cap = capEntry.getLongValue();
                    if (!fuzzy) {
                        long amount = visibleNetworkAmount(key, cap);
                        if (amount > 0) fresh.add(key, amount);
                        continue;
                    }
                    long configuredAmount = visibleNetworkAmount(key, Long.MAX_VALUE);
                    OverloadedAmountMath.mergeSharedExposure(
                            capByVariant, amountByVariant, key, cap, configuredAmount);

                    if (key.supportsFuzzyRangeSearch()) {
                        for (var entry : grid.getStorageService().getCachedInventory().findFuzzy(key, fuzzyMode)) {
                            var variant = entry.getKey();
                            // The exact key was already counted for this
                            // configured entry above.
                            if (variant.equals(key)) continue;
                            OverloadedAmountMath.mergeSharedExposure(
                                    capByVariant,
                                    amountByVariant,
                                    variant,
                                    cap,
                                    entry.getLongValue());
                        }
                    }
                }
                if (fuzzy) {
                    var variants = capByVariant.object2LongEntrySet().fastIterator();
                    while (variants.hasNext()) {
                        var exposedEntry = variants.next();
                        var variant = exposedEntry.getKey();
                        long cap = exposedEntry.getLongValue();
                        long networkAmount = amountByVariant.getLong(variant);
                        long amount = OverloadedAmountMath.capVisibleAmount(networkAmount, cap);
                        if (amount > 0) fresh.add(variant, amount);
                    }
                }
                availableStacksCache = fresh;
                availableStacksCacheTick = now;
                availableStacksCacheFuzzyMode = fuzzyMode;
                for (var entry : fresh) {
                    out.add(entry.getKey(), entry.getLongValue());
                }
            } finally {
                capByKey.clear();
                if (capByVariant != null) capByVariant.clear();
                if (amountByVariant != null) amountByVariant.clear();
                proxying = false;
            }
        }

        // ── NBT: no persistent state ────────────────────────────────────

        @Override
        public void writeToChildTag(CompoundTag tag, String name) {
            tag.remove(name);
        }

        @Override
        public void readFromChildTag(CompoundTag tag, String name) {
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ProxiedMenuWrapper — display-only wrapper
    //
    //  All mutation methods are no-ops; actual GUI interaction is handled
    //  entirely by OverloadedInterfaceMenu.clicked() which directly
    //  operates on the ME network (ME Terminal pattern).
    // ══════════════════════════════════════════════════════════════════════

    static class ProxiedMenuWrapper extends ConfigMenuInventory {
        private final ProxiedStorageInv proxy;

        ProxiedMenuWrapper(ProxiedStorageInv proxy) {
            super(proxy);
            this.proxy = proxy;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public void setItemDirect(int slotIndex, ItemStack stack) {
            if (stack.isEmpty()) {
                proxy.setDisplayStack(slotIndex, null);
            } else {
                proxy.setDisplayStack(slotIndex, convertToSuitableStack(stack));
            }
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    }
}
