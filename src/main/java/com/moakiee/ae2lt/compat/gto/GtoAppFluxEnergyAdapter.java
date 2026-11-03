package com.moakiee.ae2lt.compat.gto;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import com.glodblock.github.appflux.common.me.energy.EnergyHandler;
import com.glodblock.github.appflux.config.AFConfig;
import com.moakiee.ae2lt.logic.energy.AppFluxBridge;
import com.moakiee.ae2lt.logic.energy.TargetAccess;
import com.moakiee.ae2lt.logic.management.PowerTelemetry;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/** AppFlux's Forge GTM integration predates GTO's class capabilities and EU-amount receipts. */
public final class GtoAppFluxEnergyAdapter {
    private GtoAppFluxEnergyAdapter() {}

    @Nullable
    public static EnergyHandler.SendAction getHandler(BlockEntity target, Direction side) {
        if (!AFConfig.gteuSupport()) return null;
        // The native helper creates a new EU wrapper for ordinary FE blocks; avoid it on this hot path.
        if (!NativeHolder.BLOCK_TYPE.isInstance(target)) return null;
        NativeApi api = NativeHolder.API;
        Object container = api.find(target, side);
        // GTO also adapts ordinary FE blocks to EU. Leave those to AppFlux's FE path.
        if (container == null || api.forgeWrapper.isInstance(container)) return null;
        var location = target.getLevel() == null ? null : new PowerTelemetry.Target(
                target.getLevel().dimension().location().toString(), target.getBlockPos().immutable(), side.ordinal());
        var access = new NativeTarget(api, container, side, location);
        return (storage, source) -> AppFluxBridge.sendToTarget(access, storage, source, AppFluxBridge.TRANSFER_RATE);
    }

    private static final class NativeHolder {
        private static final Class<?> BLOCK_TYPE = type("com.gregtechceu.gtceu.api.blockentity.GTBlockEntity");
        private static final NativeApi API = load();

        private static NativeApi load() {
            try {
                return new NativeApi(
                        type("com.gregtechceu.gtceu.api.capability.IEnergyContainer"),
                        type("com.gregtechceu.gtceu.api.capability.GTCapabilityHelper"),
                        type("com.gregtechceu.gtceu.api.capability.compat.EUToFEProvider$GTEnergyWrapper"),
                        type("com.gregtechceu.gtceu.api.capability.compat.FeCompat"));
            } catch (ReflectiveOperationException failure) {
                throw new ExceptionInInitializerError(failure);
            }
        }

        private static Class<?> type(String name) {
            try {
                return Class.forName(name, false, GtoAppFluxEnergyAdapter.class.getClassLoader());
            } catch (ClassNotFoundException failure) {
                throw new ExceptionInInitializerError(failure);
            }
        }
    }

    /** Resolve the exact supported GTO ABI once; dispatch only invokes cached handles. */
    static final class NativeApi {
        final Class<?> forgeWrapper;
        final MethodHandle find;
        final MethodHandle voltage;
        final MethodHandle amperage;
        final MethodHandle room;
        final MethodHandle inputs;
        final MethodHandle accept;
        final MethodHandle ratio;

        NativeApi(Class<?> energyType, Class<?> helperType, Class<?> wrapperType, Class<?> compatType)
                throws ReflectiveOperationException {
            var lookup = MethodHandles.publicLookup();
            forgeWrapper = wrapperType;
            find = lookup.findStatic(helperType, "getEnergyContainer",
                    MethodType.methodType(energyType, BlockEntity.class, Direction.class))
                    .asType(MethodType.methodType(Object.class, BlockEntity.class, Direction.class));
            voltage = lookup.findVirtual(energyType, "getInputVoltage", MethodType.methodType(long.class))
                    .asType(MethodType.methodType(long.class, Object.class));
            amperage = lookup.findVirtual(energyType, "getInputAmperage", MethodType.methodType(long.class))
                    .asType(MethodType.methodType(long.class, Object.class));
            room = lookup.findVirtual(energyType, "getEnergyCanBeInserted", MethodType.methodType(long.class))
                    .asType(MethodType.methodType(long.class, Object.class));
            inputs = lookup.findVirtual(energyType, "inputsEnergy", MethodType.methodType(boolean.class, Direction.class))
                    .asType(MethodType.methodType(boolean.class, Object.class, Direction.class));
            accept = lookup.findVirtual(energyType, "acceptEnergyFromNetwork",
                    MethodType.methodType(long.class, Object.class, Direction.class, long.class, long.class))
                    .asType(MethodType.methodType(long.class, Object.class, Object.class, Direction.class, long.class, long.class));
            ratio = lookup.findStatic(compatType, "ratio", MethodType.methodType(int.class, boolean.class));
        }

        Object find(BlockEntity target, Direction side) {
            try {
                return (Object) find.invokeExact(target, side);
            } catch (Throwable failure) {
                throw callFailure(failure);
            }
        }
    }

    static final class NativeTarget implements TargetAccess {
        private final NativeApi api;
        private final Object container;
        private final Direction side;
        private final PowerTelemetry.Target location;

        NativeTarget(NativeApi api, Object container, Direction side, @Nullable PowerTelemetry.Target location) {
            this.api = api;
            this.container = container;
            this.side = side;
            this.location = location;
        }

        @Override
        public PowerTelemetry.Target telemetryTarget() { return location; }

        @Override
        public long simulateReceive(long maxFe) {
            if (maxFe <= 0) return 0;
            try {
                int ratio = ratio();
                long voltage = (long) api.voltage.invokeExact(container);
                return demandEu(maxFe, ratio, voltage) * ratio;
            } catch (Throwable failure) {
                throw callFailure(failure);
            }
        }

        @Override
        public long receive(long amountFe) {
            if (amountFe <= 0) return 0;
            try {
                int ratio = ratio();
                long voltage = (long) api.voltage.invokeExact(container);
                long offeredEu = demandEu(amountFe, ratio, voltage);
                if (offeredEu == 0) return 0;
                // GTO accepts an EU amount and returns EU, rather than the old GTM amp count.
                // The bound handler is the stable identity of this native injection source.
                long acceptedEu = (long) api.accept.invokeExact(container, (Object) this, side, voltage, offeredEu);
                if (acceptedEu < 0 || acceptedEu > offeredEu) {
                    throw new IllegalStateException("Invalid GTO native EU receipt: " + acceptedEu + "/" + offeredEu);
                }
                // Use the FE-to-EU cost in both directions of this single transfer.
                // Multiplication is bounded by the original FE offer, even near Long.MAX_VALUE.
                return acceptedEu * ratio;
            } catch (Throwable failure) {
                throw callFailure(failure);
            }
        }

        private int ratio() throws Throwable {
            int ratio = (int) api.ratio.invokeExact(true);
            if (ratio <= 0) throw new IllegalStateException("GTO FE-to-EU ratio must be positive");
            return ratio;
        }

        private long demandEu(long maxFe, int ratio, long voltage) throws Throwable {
            if (voltage <= 0 || !(boolean) api.inputs.invokeExact(container, side)) return 0;
            long amperage = (long) api.amperage.invokeExact(container);
            long room = (long) api.room.invokeExact(container);
            if (amperage <= 0 || room <= 0) return 0;
            long tickLimit = voltage > Long.MAX_VALUE / amperage ? Long.MAX_VALUE : voltage * amperage;
            return Math.min(maxFe / ratio, Math.min(room, tickLimit));
        }
    }

    private static RuntimeException callFailure(Throwable failure) {
        if (failure instanceof Error error) throw error;
        if (failure instanceof RuntimeException runtime) return runtime;
        return new IllegalStateException("GTO native energy API failed", failure);
    }
}
