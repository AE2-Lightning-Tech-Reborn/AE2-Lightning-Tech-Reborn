package com.moakiee.ae2lt.compat.gto;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.Test;

public class GtoAppFluxEnergyAdapterTest {
    private static GtoAppFluxEnergyAdapter.NativeApi api() throws ReflectiveOperationException {
        return new GtoAppFluxEnergyAdapter.NativeApi(ContainerApi.class, NativeHelper.class,
                ForgeWrapper.class, Conversion.class);
    }

    private static GtoAppFluxEnergyAdapter.NativeTarget target(Container receiver) throws ReflectiveOperationException {
        return new GtoAppFluxEnergyAdapter.NativeTarget(api(), receiver, Direction.NORTH, null);
    }

    @Test
    void simulationReadsNativeDemandWithoutMutatingEnergy() throws Exception {
        var receiver = new Container();
        var target = target(receiver);
        assertEquals(256, target.simulateReceive(10_000));
        assertEquals(0, receiver.energy);
        assertEquals(0, receiver.calls);
    }

    @Test
    void nativeBudgetAndReceiptAreEuAmountsRatherThanAmperage() throws Exception {
        var receiver = new Container();
        var target = target(receiver);
        assertEquals(40, target.simulateReceive(43));
        assertEquals(40, target.receive(43));
        assertEquals(10, receiver.offeredEu);
        assertEquals(10, receiver.energy);
        assertEquals(32, receiver.offeredVoltage);
        assertSame(target, receiver.source);
        assertEquals(1, receiver.calls);
    }

    @Test
    void partialReceiptChargesOnlyTheActualAcceptedEu() throws Exception {
        var receiver = new Container();
        receiver.maxReceipt = 7;
        var target = target(receiver);
        assertEquals(28, target.receive(255));
        assertEquals(63, receiver.offeredEu);
        assertEquals(7, receiver.energy);
        assertEquals(1, receiver.calls);
    }

    @Test
    void capacityAndInputSideLimitBothSimulationAndRealOffer() throws Exception {
        var receiver = new Container();
        receiver.capacity = 3;
        var target = target(receiver);
        assertEquals(12, target.simulateReceive(10_000));
        assertEquals(12, target.receive(10_000));
        assertEquals(0, target.receive(10_000));
        receiver.energy = 0;
        receiver.input = false;
        assertEquals(0, target.simulateReceive(10_000));
        assertEquals(0, target.receive(10_000));
        assertEquals(1, receiver.calls);
    }

    @Test
    void subEuRemainderIsNeverDeliveredForFree() throws Exception {
        var receiver = new Container();
        var target = target(receiver);
        assertEquals(0, target.simulateReceive(3));
        assertEquals(0, target.receive(3));
        assertEquals(0, receiver.energy);
        assertEquals(0, receiver.calls);
    }

    @Test
    void longBoundaryDoesNotOverflowVoltageAmperageOrFeReceipt() throws Exception {
        var receiver = new Container();
        receiver.voltage = Long.MAX_VALUE;
        receiver.amperage = Long.MAX_VALUE;
        receiver.capacity = Long.MAX_VALUE;
        var target = target(receiver);
        long representedFe = Long.MAX_VALUE - Long.MAX_VALUE % 4;
        assertEquals(representedFe, target.simulateReceive(Long.MAX_VALUE));
        assertEquals(representedFe, target.receive(Long.MAX_VALUE));
        assertEquals(Long.MAX_VALUE / 4, receiver.energy);
    }

    @Test
    void invalidNativeReceiptIsVisibleAndIsNotRetried() throws Exception {
        var receiver = new Container();
        receiver.invalidReceipt = true;
        var target = target(receiver);
        assertThrows(IllegalStateException.class, () -> target.receive(40));
        assertEquals(1, receiver.calls);
    }

    @Test
    void coldBindingUsesTheClassCapabilityHelperSignature() throws Exception {
        var receiver = new Container();
        NativeHelper.receiver = receiver;
        var api = api();
        assertSame(receiver, api.find(null, Direction.NORTH));
        assertTrue(api.forgeWrapper.isInstance(new ForgeWrapper()));
        assertFalse(api.forgeWrapper.isInstance(receiver));
    }

    public interface ContainerApi {
        long getInputVoltage();
        long getInputAmperage();
        long getEnergyCanBeInserted();
        boolean inputsEnergy(Direction side);
        long acceptEnergyFromNetwork(Object source, Direction side, long voltage, long amountEu);
    }

    public static class Container implements ContainerApi {
        long voltage = 32;
        long amperage = 2;
        long capacity = 10_000;
        long energy;
        long maxReceipt = Long.MAX_VALUE;
        long offeredEu;
        long offeredVoltage;
        Object source;
        int calls;
        boolean input = true;
        boolean invalidReceipt;

        @Override public long getInputVoltage() { return voltage; }
        @Override public long getInputAmperage() { return amperage; }
        @Override public long getEnergyCanBeInserted() { return capacity - energy; }
        @Override public boolean inputsEnergy(Direction side) { return input; }
        @Override public long acceptEnergyFromNetwork(Object source, Direction side, long voltage, long amountEu) {
            calls++;
            this.source = source;
            offeredVoltage = voltage;
            offeredEu = amountEu;
            if (invalidReceipt) return amountEu + 1;
            long accepted = Math.min(amountEu, Math.min(capacity - energy, maxReceipt));
            energy += accepted;
            return accepted;
        }
    }

    public static final class ForgeWrapper extends Container {}

    public static final class NativeHelper {
        static ContainerApi receiver;
        public static ContainerApi getEnergyContainer(BlockEntity blockEntity, Direction side) { return receiver; }
    }

    public static final class Conversion {
        public static int ratio(boolean feToEu) {
            // Deliberately asymmetric: receipt cost must use the FE-to-EU conversion, too.
            return feToEu ? 4 : 2;
        }
    }
}
