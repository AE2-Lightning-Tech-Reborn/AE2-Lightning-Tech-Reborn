package com.moakiee.ae2lt.machine.overloadfactory;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NotifyingFluidTankExportTest {
    @BeforeAll static void bootstrap() {
        if (LoadingModList.get() == null) LoadingModList.of(List.of(), List.of(),
                new net.minecraftforge.fml.loading.EarlyLoadingException("test bootstrap", null, List.of()));
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
    }

    @ParameterizedTest @ValueSource(ints = {0, 17, 1000})
    void snapshotDuringCallbackIsResavedAfterRestoringConfirmedRemainder(int receipt) {
        var persisted = new CompoundTag(); var notices = new AtomicInteger();
        NotifyingFluidTank[] holder = {null};
        var tank = new NotifyingFluidTank(4000, () -> {
            notices.incrementAndGet(); holder[0].writeToNBT(persisted);
        }); holder[0] = tank;
        var fluid = new FluidStack(Fluids.WATER, 1000); var tag = new CompoundTag();
        tag.putString("Owner", "exact tail"); fluid.setTag(tag);
        tank.fill(fluid, FluidAction.EXECUTE); notices.set(0);
        assertEquals(receipt, tank.exportFluid(offer -> { tank.writeToNBT(persisted); return receipt; }));
        var restored = new NotifyingFluidTank(4000, () -> {}); restored.readFromNBT(persisted);
        assertEquals(1000 - receipt, restored.getFluidAmount());
        if (receipt < 1000) assertEquals(tag, restored.getFluid().getTag());
        assertEquals(1, notices.get());
    }

    @Test void rejectsReentrantFillDrainReloadAndNestedExportWithoutLosingTail() {
        var tank = new NotifyingFluidTank(4000, () -> {});
        tank.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        var handler = new OverloadProcessingFactoryFluidHandler(new NotifyingFluidTank(4000, () -> {}), tank);
        assertEquals(17, tank.exportFluid(offer -> {
            assertTrue(handler.getFluidInTank(1).isEmpty());
            for (var mode : FluidAction.values()) {
                assertEquals(0, tank.fill(new FluidStack(Fluids.LAVA, 4000), mode));
                assertTrue(handler.drain(1000, mode).isEmpty());
                assertTrue(handler.drain(offer, mode).isEmpty());
            }
            assertEquals(0, tank.exportFluid(nested -> { fail("nested credit"); return 0; }));
            assertThrows(IllegalStateException.class, () -> tank.setFluid(offer));
            assertThrows(IllegalStateException.class, () -> tank.readFromNBT(new CompoundTag()));
            assertThrows(IllegalStateException.class, () -> tank.setCapacity(1));
            assertThrows(IllegalStateException.class, () -> tank.setValidator(stack -> false));
            offer.setAmount(Integer.MAX_VALUE); // Foreign offer must not alias the reservation.
            return 17;
        }));
        assertEquals(983, tank.getFluidAmount()); assertEquals(4000, tank.getCapacity());
        assertEquals(1, tank.fill(new FluidStack(Fluids.WATER, 1), FluidAction.EXECUTE));
    }

    @Test void liveAliasAcquiredBeforeReservationCannotChangeTheSavedTail() {
        var tank = new NotifyingFluidTank(4000, () -> {});
        tank.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        var stale = tank.getFluid();
        assertEquals(17, tank.exportFluid(offer -> { stale.setAmount(1); return 17; }));
        assertEquals(983, tank.getFluidAmount());
    }

    @Test void nullReceiverIsRejectedBeforeDetachingFluid() {
        var tank = new NotifyingFluidTank(4000, () -> {});
        tank.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        assertThrows(NullPointerException.class, () -> tank.exportFluid(null));
        assertEquals(1000, tank.getFluidAmount());
    }

    @Test void fatalErrorPropagatesButCannotRestoreAnUncertainCredit() {
        var tank = new NotifyingFluidTank(4000, () -> {});
        tank.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        long[] credited = {0};
        assertThrows(AssertionError.class, () -> tank.exportFluid(offer -> {
            credited[0] += offer.getAmount(); throw new AssertionError("fatal after credit");
        }));
        assertEquals(1000, credited[0]); assertTrue(tank.isEmpty());
        assertEquals(1, tank.fill(new FluidStack(Fluids.LAVA, 1), FluidAction.EXECUTE));
    }

    @Test void seededRandomPartialReceiptsConserveFluidThroughSaveReload() {
        var random = new Random(0x1201F10DL);
        for (int trial = 0; trial < 1000; trial++) {
            var tank = new NotifyingFluidTank(Integer.MAX_VALUE, () -> {});
            int initial = 1 + random.nextInt(Integer.MAX_VALUE - 1);
            tank.fill(new FluidStack(Fluids.WATER, initial), FluidAction.EXECUTE);
            long credited = 0;
            for (int step = 0; step < 20 && !tank.isEmpty(); step++) {
                int receipt = random.nextInt(tank.getFluidAmount() + 1);
                credited += tank.exportFluid(offer -> receipt);
                var snapshot = tank.writeToNBT(new CompoundTag()); tank.readFromNBT(snapshot);
                assertEquals(initial, credited + tank.getFluidAmount());
            }
            credited += tank.exportFluid(FluidStack::getAmount);
            assertEquals(initial, credited); assertTrue(tank.isEmpty());
        }
    }
}
