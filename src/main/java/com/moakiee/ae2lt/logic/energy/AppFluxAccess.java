package com.moakiee.ae2lt.logic.energy;

import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageCells;

import com.glodblock.github.appflux.api.IFluxCell;
import com.glodblock.github.appflux.common.me.cell.FluxCellInventory;
import com.glodblock.github.appflux.common.me.key.FluxKey;
import com.glodblock.github.appflux.common.me.key.type.EnergyType;
import com.glodblock.github.appflux.config.AFConfig;
import com.moakiee.ae2lt.util.MixinReflectionSupport;

import mekanism.api.Action;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.api.math.FloatingLong;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.config.MekanismConfig;
import mekanism.common.util.UnitDisplayUtils;
import sonar.fluxnetworks.api.FluxCapabilities;
import sonar.fluxnetworks.api.energy.IFNEnergyStorage;

/**
 * Direct access to Applied Flux APIs. This class is ONLY loaded when AppFlux
 * is present at runtime — the JVM's lazy class loading ensures that importing
 * AppFlux types here never triggers a {@link NoClassDefFoundError} as long as
 * callers guard access behind the {@code LOADED} check in
 * {@link AppFluxBridge}.
 */
final class AppFluxAccess {

    static final AEKey FE_KEY = FluxKey.of(EnergyType.FE);

    static final long TRANSFER_RATE;
    private static final boolean FLUX_NETWORKS_LOADED;
    private static final boolean MEKANISM_LOADED;

    static {
        long rate = AFConfig.getFluxAccessorIO();
        TRANSFER_RATE = rate == 0L ? Long.MAX_VALUE : Math.max(0L, rate);
        FLUX_NETWORKS_LOADED = isClassPresent("sonar.fluxnetworks.api.energy.IFNEnergyStorage");
        MEKANISM_LOADED = isClassPresent("mekanism.common.capabilities.Capabilities");
    }

    static boolean isFluxCell(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof IFluxCell;
    }

    @Nullable
    static Object createCapCache(ServerLevel level, BlockPos pos,
                                 Supplier<IGrid> gridSupplier) {
        return new CapabilityTargetCache(level, pos);
    }

    @Nullable
    static TargetAccess resolveEnergyTarget(Object energyCapCache, Direction side) {
        if (!(energyCapCache instanceof CapabilityTargetCache cache)) {
            return null;
        }

        TargetAccess target = resolveFluxNetworkTarget(cache, side);
        if (target == null) target = resolveMekanismTarget(cache, side);
        if (target == null) target = ForgeEnergyTarget.resolve(cache, side);
        if (target == null) return null;
        TargetAccess resolved = target;
        var location = new com.moakiee.ae2lt.logic.management.PowerTelemetry.Target(
                cache.level().dimension().location().toString(), cache.pos().relative(side), side.getOpposite().ordinal());
        return new TargetAccess() {
            public long simulateReceive(long amount) { return resolved.simulateReceive(amount); }
            public long receive(long amount) { return resolved.receive(amount); }
            public com.moakiee.ae2lt.logic.management.PowerTelemetry.Target telemetryTarget() { return location; }
            public java.util.OptionalLong storedEnergyFe() { return resolved.storedEnergyFe(); }
        };
    }

    static long simulateTarget(@Nullable TargetAccess access, long maxFe) {
        if (access == null || maxFe <= 0L) return 0L;
        try {
            return Math.min(maxFe, Math.max(0L, access.simulateReceive(maxFe)));
        } catch (RuntimeException | LinkageError failure) {
            MixinReflectionSupport.logReflectionFailure("simulate FE target " + access.getClass().getName(), failure);
            return 0L;
        }
    }

    static long sendToTarget(@Nullable TargetAccess access, IStorageService storage,
                             IActionSource source, long maxFe) {
        if (access == null || maxFe <= 0L) {
            return 0L;
        }

        if (storage.getInventory() instanceof BufferedMEStorage buffer) {
            if (!buffer.beginTransfer()) return 0L;
            try {
                return sendToTargetKnownDemandPinned(access, buffer, source, simulateTarget(access, maxFe));
            } finally {
                buffer.endTransfer();
            }
        }

        long requested = simulateTarget(access, maxFe);
        if (requested <= 0L) {
            return 0L;
        }

        long extracted = storage.getInventory().extract(
                FE_KEY, Math.min(requested, maxFe), Actionable.MODULATE, source);
        if (extracted <= 0L) {
            return 0L;
        }

        long accepted;
        try {
            accepted = Math.min(extracted, Math.max(0L, access.receive(extracted)));
        } catch (RuntimeException | LinkageError uncertain) {
            logUncertainTransfer(access, uncertain);
            // The target may have accepted before throwing. Never refund this call.
            return 0L;
        }
        long leftover = extracted - accepted;
        if (leftover > 0L) {
            storage.getInventory().insert(FE_KEY, leftover, Actionable.MODULATE, source);
        }
        return accepted;
    }

    static long sendToTargetKnownDemand(@Nullable TargetAccess access, IStorageService storage,
                                        IActionSource source, long requested) {
        if (access == null || requested <= 0L) {
            return 0L;
        }

        if (storage.getInventory() instanceof BufferedMEStorage buffer) {
            return sendToTargetKnownDemand(access, buffer, source, requested);
        }

        long extracted = storage.getInventory().extract(
                FE_KEY, requested, Actionable.MODULATE, source);
        if (extracted <= 0L) {
            return 0L;
        }

        long accepted;
        try {
            accepted = Math.min(extracted, Math.max(0L, access.receive(extracted)));
        } catch (RuntimeException | LinkageError uncertain) {
            logUncertainTransfer(access, uncertain);
            return 0L;
        }
        long leftover = extracted - accepted;
        if (leftover > 0L) {
            storage.getInventory().insert(FE_KEY, leftover, Actionable.MODULATE, source);
        }
        return accepted;
    }

    /**
     * Direct-buffer variant of {@link #sendToTargetKnownDemand(TargetAccess, IStorageService, IActionSource, long)}
     * for callers that already hold a {@link BufferedMEStorage} reference (the
     * NORMAL-mode tickNormal hot path). Skips the {@code IStorageService.getInventory()}
     * interface dispatch — JIT cannot always devirt that across modules and
     * each NORMAL modulate-pass iteration would otherwise spend ~5 ns there.
     */
    static long sendToTargetKnownDemand(@Nullable TargetAccess access, BufferedMEStorage buffer,
                                        IActionSource source, long requested) {
        if (!buffer.beginTransfer()) return 0L;
        try {
            return sendToTargetKnownDemandPinned(access, buffer, source, requested);
        } finally {
            buffer.endTransfer();
        }
    }

    private static long sendToTargetKnownDemandPinned(@Nullable TargetAccess access, BufferedMEStorage buffer,
                                                     IActionSource source, long requested) {
        if (access == null || requested <= 0L || !buffer.isOnline()) {
            return 0L;
        }

        long extracted = buffer.extract(FE_KEY, requested, Actionable.MODULATE, source);
        if (extracted <= 0L) {
            return 0L;
        }

        // Withdrawal can run storage callbacks. Funds have not reached a
        // target yet, so an intervening disconnect authorizes a full refund.
        if (!buffer.isOnline()) {
            buffer.returnUndelivered(extracted, source);
            return 0L;
        }

        buffer.quarantineDelivery(extracted);
        long accepted;
        try {
            accepted = access.receive(extracted);
            if (accepted < 0 || accepted > extracted) return 0;
        } catch (RuntimeException | LinkageError uncertain) {
            if (buffer.isLedgerOwned()) throw new IllegalStateException("Unknown FE delivery receipt", uncertain);
            logUncertainTransfer(access, uncertain);
            return 0L;
        }
        buffer.settleDelivery(extracted, accepted);
        return com.moakiee.ae2lt.logic.management.PowerTelemetry.record(source, access, accepted);
    }

    static long sendToTargetRepeatedOptimistic(@Nullable TargetAccess access, BufferedMEStorage buffer,
                                              IActionSource source, long maxFe, int maxCalls) {
        if (!buffer.beginTransfer()) return 0L;
        try {
            return sendToTargetRepeatedPinned(access, buffer, source, maxFe, maxCalls);
        } finally {
            buffer.endTransfer();
        }
    }

    private static long sendToTargetRepeatedPinned(@Nullable TargetAccess access, BufferedMEStorage buffer,
                                                  IActionSource source, long maxFe, int maxCalls) {
        if (access == null || maxFe <= 0L || maxCalls <= 0) {
            return 0L;
        }

        long totalAccepted = 0L;
        long remaining = 0L;
        try {
            for (int i = 0; i < maxCalls; i++) {
                // A successful receipt can still disconnect the host. Stop
                // before another delivery and refund the unused batch budget.
                if (!buffer.isOnline()) break;
                if (remaining <= 0L) {
                    try {
                        remaining = extractRepeatedBudget(access, buffer, source, maxFe, maxCalls - i);
                    } catch (RuntimeException | LinkageError failure) {
                        MixinReflectionSupport.logReflectionFailure("prepare direct FE budget "
                                + access.getClass().getName(), failure);
                        break;
                    }
                    if (remaining <= 0L) {
                        break;
                    }
                }

                if (!buffer.isOnline()) break;

                long attempt = remaining < maxFe ? remaining : maxFe;
                // Separate in-flight ownership BEFORE entering third-party code.
                remaining -= attempt;
                buffer.quarantineDelivery(attempt);
                long accepted;
                try {
                    accepted = access.receive(attempt);
                } catch (RuntimeException | LinkageError uncertain) {
                    if (buffer.isLedgerOwned()) throw new IllegalStateException("Unknown FE delivery receipt", uncertain);
                    logUncertainTransfer(access, uncertain);
                    break;
                }
                if (accepted < 0 || accepted > attempt) break;
                buffer.settleDelivery(attempt, accepted);
                if (accepted == 0) break;
                com.moakiee.ae2lt.logic.management.PowerTelemetry.record(source, access, accepted);
                totalAccepted = saturatingAdd(totalAccepted, accepted);
            }
        } finally {
            if (remaining > 0L) {
                buffer.returnFromDirectSend(remaining, source);
            }
        }
        return totalAccepted;
    }

    private static void logUncertainTransfer(TargetAccess access, Throwable cause) {
        MixinReflectionSupport.logReflectionFailure(
                "send FE to " + access.getClass().getName()
                        + " (receipt unknown; in-flight FE will not be refunded or retried in this batch)", cause);
    }

    private static long extractRepeatedBudget(TargetAccess access, BufferedMEStorage buffer,
                                             IActionSource source, long maxFe, int remainingCalls) {
        long budget = saturatingMul(maxFe, remainingCalls);
        long extracted = buffer.extractForDirectSend(budget, source);
        if (extracted > 0L) {
            return extracted;
        }

        long simulated = simulateTarget(access, maxFe);
        if (!buffer.isOnline()) return 0L;
        long currentDemand = simulated < maxFe ? simulated : maxFe;
        if (currentDemand <= 0L) {
            return 0L;
        }
        long refillDemand = buffer.refillBudgetForDirectSend(currentDemand);
        if (refillDemand <= 0L) {
            return 0L;
        }
        buffer.refillForDirectSend(refillDemand, source);
        return buffer.extractForDirectSend(budget, source);
    }

    static long getFluxCellCapacity(ItemStack stack) {
        var inventory = StorageCells.getCellInventory(stack, null);
        if (inventory instanceof FluxCellInventory fluxInv) {
            return Math.max(0L, fluxInv.getMaxEnergy());
        }
        if (stack.getItem() instanceof IFluxCell fluxCell) {
            return Math.max(0L, fluxCell.getBytes(stack));
        }
        return 0L;
    }

    static void persistCellStorage(@Nullable MEStorage storage) {
        if (storage instanceof FluxCellInventory fluxInv) {
            fluxInv.persist();
        }
    }

    private AppFluxAccess() {}

    @Nullable
    private static TargetAccess resolveFluxNetworkTarget(CapabilityTargetCache cache, Direction side) {
        if (!FLUX_NETWORKS_LOADED) {
            return null;
        }
        try {
            IFNEnergyStorage target = getTargetCapability(cache, side, FluxCapabilities.FN_ENERGY_STORAGE);
            return target != null ? new FluxNetworkTarget(target) : null;
        } catch (LinkageError | RuntimeException ignored) {
            return null;
        }
    }

    @Nullable
    private static TargetAccess resolveMekanismTarget(CapabilityTargetCache cache, Direction side) {
        if (!MEKANISM_LOADED) {
            return null;
        }
        try {
            IStrictEnergyHandler target = getTargetCapability(cache, side, Capabilities.STRICT_ENERGY);
            return target != null ? MekanismStrictTarget.create(target) : null;
        } catch (LinkageError | RuntimeException ignored) {
            return null;
        }
    }

    private record FluxNetworkTarget(IFNEnergyStorage target) implements TargetAccess {
        @Override
        public long simulateReceive(long maxFe) {
            return Math.max(0L, target.receiveEnergyL(maxFe, true));
        }

        @Override
        public long receive(long amountFe) {
            return target.receiveEnergyL(amountFe, false);
        }
    }

    private static final class MekanismStrictTarget implements TargetAccess {
        private final IStrictEnergyHandler target;
        private final FloatingLong maxJoulesPerCall;
        private final long maxForgeEnergyPerCall;

        private MekanismStrictTarget(IStrictEnergyHandler target, FloatingLong maxJoulesPerCall,
                                     long maxForgeEnergyPerCall) {
            this.target = target;
            this.maxJoulesPerCall = maxJoulesPerCall;
            this.maxForgeEnergyPerCall = maxForgeEnergyPerCall;
        }

        static MekanismStrictTarget create(IStrictEnergyHandler target) {
            double rate = MekanismConfig.general.forgeConversionRate.get().doubleValue();
            long maxFe = maxForgeEnergyFor(rate);
            long cappedTransfer = TRANSFER_RATE > 0L ? Math.min(TRANSFER_RATE, maxFe) : 0L;
            FloatingLong maxJ = cappedTransfer > 0L
                    ? UnitDisplayUtils.EnergyUnit.FORGE_ENERGY.convertFrom(cappedTransfer)
                    : FloatingLong.ZERO;
            return new MekanismStrictTarget(target, maxJ, maxFe);
        }

        @Override
        public long simulateReceive(long maxFe) {
            return insert(maxFe, Action.SIMULATE);
        }

        @Override
        public long receive(long amountFe) {
            return insert(amountFe, Action.EXECUTE);
        }

        private long insert(long amountFe, Action action) {
            if (amountFe <= 0L) {
                return 0L;
            }
            long effectiveAmountFe = Math.min(amountFe, maxForgeEnergyPerCall);
            if (effectiveAmountFe <= 0L) {
                return 0L;
            }
            FloatingLong mekanismAmount = amountFe == TRANSFER_RATE
                    ? maxJoulesPerCall
                    : UnitDisplayUtils.EnergyUnit.FORGE_ENERGY.convertFrom(effectiveAmountFe);
            if (mekanismAmount.isZero()) {
                return 0L;
            }
            FloatingLong remainder = target.insertEnergy(mekanismAmount, action);
            if (remainder.isZero()) {
                return effectiveAmountFe;
            }
            if (remainder.greaterOrEqual(mekanismAmount)) {
                return 0L;
            }
            FloatingLong acceptedJ = mekanismAmount.subtract(remainder);
            return Math.min(
                    effectiveAmountFe,
                    UnitDisplayUtils.EnergyUnit.FORGE_ENERGY.convertToAsLong(acceptedJ));
        }
    }

    private record ForgeEnergyTarget(IEnergyStorage target) implements TargetAccess {
        @Override public java.util.OptionalLong storedEnergyFe() { return new NativeFeTarget(target).storedEnergyFe(); }
        @Nullable
        static TargetAccess resolve(CapabilityTargetCache cache, Direction side) {
            IEnergyStorage target = getTargetCapability(cache, side, ForgeCapabilities.ENERGY);
            return target != null ? new ForgeEnergyTarget(target) : null;
        }

        @Override
        public long simulateReceive(long maxFe) {
            return Math.max(0, target.receiveEnergy(clampToInt(maxFe), true));
        }

        @Override
        public long receive(long amountFe) {
            return target.receiveEnergy(clampToInt(amountFe), false);
        }
    }

    private record CapabilityTargetCache(ServerLevel level, BlockPos pos) {
        private CapabilityTargetCache {
            pos = pos.immutable();
        }

        @Nullable
        private BlockEntity resolveTarget(Direction side) {
            BlockPos targetPos = pos.relative(side);
            return level.isLoaded(targetPos) ? level.getBlockEntity(targetPos) : null;
        }
    }

    @Nullable
    private static <T> T getTargetCapability(CapabilityTargetCache cache, Direction side, Capability<T> capability) {
        BlockEntity target = cache.resolveTarget(side);
        if (target == null) {
            return null;
        }
        return target.getCapability(capability, side.getOpposite()).resolve().orElse(null);
    }

    private static int clampToInt(long value) {
        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0L, value);
    }

    private static long maxForgeEnergyFor(double feToJoules) {
        if (Double.isNaN(feToJoules) || feToJoules <= 0.0) {
            return Long.MAX_VALUE;
        }
        double max = 9.223372036854776E18 / feToJoules;
        if (max >= 9.223372036854776E18) {
            return Long.MAX_VALUE;
        }
        return Math.max(1L, (long) max);
    }

    private static long saturatingAdd(long a, long b) {
        long r = a + b;
        return ((a ^ r) & (b ^ r)) < 0L ? Long.MAX_VALUE : r;
    }

    private static long saturatingMul(long a, long b) {
        if (a <= 0L || b <= 0L) {
            return 0L;
        }
        return a > Long.MAX_VALUE / b ? Long.MAX_VALUE : a * b;
    }

    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, AppFluxAccess.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError | SecurityException ignored) {
            return false;
        }
    }
}

