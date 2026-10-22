package com.moakiee.ae2lt.crafting.big;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.me.service.CraftingService;
import com.moakiee.ae2lt.blockentity.TianshuSupercomputerControllerBlockEntity;
import com.moakiee.ae2lt.logic.tianshu.CpuInternalCoreProfile;
import com.moakiee.ae2lt.logic.tianshu.CpuInternalCoreCalculator;
import com.moakiee.ae2lt.logic.tianshu.CpuMainCoreTier;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.thunderbolt.api.storage.BigMEStorage;
import com.moakiee.thunderbolt.core.storage.big.BigAmounts;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.*;

/** Real CPU executor/AE2 service with deterministic network and structure discovery. */
class BigServiceTestFixture {
    final Map<String, Object> gridValues = new HashMap<>();
    final IGrid grid;
    final Disk disk = new Disk();
    final CraftingService crafting;
    final BigCraftingService service;
    final ServerLevel level;

    BigServiceTestFixture(ServerLevel level) {
        this(level, CpuInternalCoreCalculator.calculate(CpuMainCoreTier.MULTIDIMENSIONAL, 0, 0));
    }

    BigServiceTestFixture(ServerLevel level, CpuInternalCoreProfile profile) {
        this.level = level;
        var storage = proxy(IStorageService.class, Map.of("getInventory", disk));
        var power = (IEnergyService) Proxy.newProxyInstance(IEnergyService.class.getClassLoader(),
                new Class<?>[]{IEnergyService.class}, (p, m, a) ->
                        m.getName().equals("extractAEPower") ? a[0] : defaultValue(m.getReturnType()));
        gridValues.put("getStorageService", storage);
        gridValues.put("getEnergyService", power);
        gridValues.put("getActiveMachines", Set.of());
        gridValues.put("getNodes", Set.of());
        grid = proxy(IGrid.class, gridValues);
        crafting = new CraftingService(grid, storage, power);
        gridValues.put("getCraftingService", crafting);
        service = serviceFor(level, grid, profile);
    }

    private final Map<appeng.api.networking.crafting.ICraftingProvider, appeng.api.networking.IGridNode> providerNodes = new IdentityHashMap<>();

    void addProvider(appeng.api.networking.crafting.ICraftingProvider provider) {
        var node = proxy(appeng.api.networking.IGridNode.class, Map.of("getService", provider));
        providerNodes.put(provider, node);
        crafting.getCraftingProviders().addProvider(node);
    }

    void removeProvider(appeng.api.networking.crafting.ICraftingProvider provider) {
        crafting.getCraftingProviders().removeProvider(providerNodes.remove(provider));
    }

    static BigCraftingService serviceFor(ServerLevel level, IGrid grid) {
        return serviceFor(level, grid,
                CpuInternalCoreCalculator.calculate(CpuMainCoreTier.MULTIDIMENSIONAL, 0, 0));
    }

    static BigCraftingService serviceFor(ServerLevel level, IGrid grid, CpuInternalCoreProfile profile) {
        var host = new TianshuSupercomputerControllerBlockEntity(BlockPos.ZERO,
                ModBlocks.TIANSHU_SUPERCOMPUTER_CONTROLLER.get().defaultBlockState()) {
            public boolean isCpuActive() { return true; }
            public IGrid getGrid() { return grid; }
            public IActionSource getActionSource() { return IActionSource.empty(); }
            public CpuInternalCoreProfile getCoreProfile() {
                return profile;
            }
            public void markCpuDirty() {}
        };
        host.setLevel(level);
        return host.bigCrafting();
    }

    void load(BigCraftingJob job) {
        var tag = new CompoundTag();
        tag.put("job", job.save(level.registryAccess()));
        service.load(tag, level.registryAccess());
    }

    static final class Disk implements MEStorage, BigMEStorage {
        final Map<AEKey, BigInteger> contents = new LinkedHashMap<>();
        boolean blocked;
        Runnable beforeInsert = () -> {};
        public BigInteger insertBig(AEKey key, BigInteger n, Actionable mode, IActionSource source) {
            if (blocked) return BigInteger.ZERO;
            if (mode == Actionable.MODULATE) {
                beforeInsert.run();
                contents.merge(key, n, BigInteger::add);
            }
            return n;
        }
        public BigInteger extractBig(AEKey key, BigInteger n, Actionable mode, IActionSource source) {
            var taken = contents.getOrDefault(key, BigInteger.ZERO).min(n);
            if (mode == Actionable.MODULATE) contents.put(key, contents.getOrDefault(key, BigInteger.ZERO).subtract(taken));
            return taken;
        }
        public Map<AEKey, BigInteger> snapshotBig(IActionSource source) { return Map.copyOf(contents); }
        public long insert(AEKey key, long n, Actionable mode, IActionSource source) {
            return insertBig(key, BigInteger.valueOf(n), mode, source).longValueExact();
        }
        public long extract(AEKey key, long n, Actionable mode, IActionSource source) {
            return extractBig(key, BigInteger.valueOf(n), mode, source).longValueExact();
        }
        public Component getDescription() { return Component.literal("Exact test disk"); }
    }

    static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0D;
        if (type == Optional.class) return Optional.empty();
        return null;
    }

    static <T> T proxy(Class<T> type, Map<String, Object> values) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, m, a) ->
                values.containsKey(m.getName()) ? values.get(m.getName()) : defaultValue(m.getReturnType())));
    }
}
