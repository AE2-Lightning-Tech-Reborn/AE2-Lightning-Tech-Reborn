package com.moakiee.ae2lt.util;

import com.moakiee.ae2lt.util.LegacyTransferBridge;
import com.moakiee.ae2lt.machine.overloadfactory.OverloadProcessingFactoryFluidHandler;
import com.moakiee.ae2lt.machine.overloadfactory.NotifyingFluidTank;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class LegacyFluidTransferTest extends com.moakiee.ae2lt.test.MinecraftComponentsTestBase {
    @Test
    void actualAe2ExternalStorageMustNotDuplicateFactoryOutput() {
        var input = new NotifyingFluidTank(2000, () -> {});
        var output = new NotifyingFluidTank(2000, () -> {});
        input.setFluid(new FluidStack(Fluids.WATER, 1000));
        output.setFluid(new FluidStack(Fluids.WATER, 1000));
        var view = LegacyTransferBridge.fluids(new OverloadProcessingFactoryFluidHandler(input, output));
        var facade = appeng.me.storage.ExternalStorageFacade.ofFluidHandler(view);
        long exported = facade.extract(appeng.api.stacks.AEFluidKey.of(Fluids.WATER), 2000,
                appeng.api.config.Actionable.MODULATE, appeng.api.networking.security.IActionSource.empty());
        int removed = 2000 - input.getFluidAmount() - output.getFluidAmount();
        System.out.println("REVIEW_AE2_FLUID_EXTRACT exported=" + exported + " actualDelta=" + removed);
        assertEquals(exported, removed, "AE2 external storage extraction must conserve fluid");
    }
    @Test
    void inputAndOutputOfTheSameFluidMustNotExposeTheOutputTwice() {
        var input = new NotifyingFluidTank(2000, () -> {});
        var output = new NotifyingFluidTank(2000, () -> {});
        input.setFluid(new FluidStack(Fluids.WATER, 1000));
        output.setFluid(new FluidStack(Fluids.WATER, 1000));
        var view = LegacyTransferBridge.fluids(new OverloadProcessingFactoryFluidHandler(input, output));
        var water = FluidResource.of(new FluidStack(Fluids.WATER, 1000));
        int first, second;
        try (var tx = Transaction.openRoot()) {
            first = view.extract(0, water, 1000, tx);
            second = view.extract(1, water, 1000, tx);
            tx.commit();
        }
        int removed = 2000 - input.getFluidAmount() - output.getFluidAmount();
        System.out.println("REVIEW_FLUID_SLOTS inputReceipt=" + first + " outputReceipt=" + second + " actualDelta=" + removed);
        assertEquals(first + second, removed, "input slot cannot claim the output tank's fluid");
    }
    @Test
    void outputTankMustNotBeExtractedTwiceThroughTwoViews() {
        var input = new NotifyingFluidTank(2000, () -> {});
        var output = new NotifyingFluidTank(2000, () -> {});
        output.setFluid(new FluidStack(Fluids.WATER, 1000));
        var actual = new OverloadProcessingFactoryFluidHandler(input, output);
        var north = LegacyTransferBridge.fluids(actual);
        var south = LegacyTransferBridge.fluids(actual);
        var water = FluidResource.of(new FluidStack(Fluids.WATER, 1000));
        int extracted;
        try (var tx = Transaction.openRoot()) {
            extracted = north.extract(1, water, 1000, tx) + south.extract(1, water, 1000, tx);
            tx.commit();
        }
        int removed = 1000 - output.getFluidAmount();
        System.out.println("REVIEW_FLUID_EXTRACT acknowledged=" + extracted + " actualDelta=" + removed);
        assertEquals(extracted, removed, "acknowledged extraction must equal actual fluid removed");
    }

    @Test
    void rollbackPreservesTankAccessAndMakesTheOutputAvailableAgain() {
        var input = new NotifyingFluidTank(2000, () -> {});
        var output = new NotifyingFluidTank(2000, () -> {});
        input.setFluid(new FluidStack(Fluids.WATER, 1000));
        output.setFluid(new FluidStack(Fluids.WATER, 1000));
        var source = new OverloadProcessingFactoryFluidHandler(input, output);
        var a = LegacyTransferBridge.fluids(source);
        var b = LegacyTransferBridge.fluids(source);
        var water = FluidResource.of(new FluidStack(Fluids.WATER, 1));
        try (var tx = Transaction.openRoot()) {
            assertEquals(0, a.extract(0, water, 1000, tx));
            assertEquals(0, a.insert(1, water, 1000, tx));
            assertEquals(400, a.extract(1, water, 400, tx));
            try (var child = Transaction.open(tx)) {
                assertEquals(600, b.extract(1, water, 1000, child));
            }
            assertEquals(600, b.getAmountAsLong(1));
        }
        assertEquals(1000, input.getFluidAmount());
        assertEquals(1000, output.getFluidAmount());
        var facade = appeng.me.storage.ExternalStorageFacade.ofFluidHandler(b);
        assertEquals(1000, facade.extract(appeng.api.stacks.AEFluidKey.of(Fluids.WATER), 2000,
                appeng.api.config.Actionable.MODULATE, appeng.api.networking.security.IActionSource.empty()));
        assertEquals(1000, input.getFluidAmount());
        assertEquals(0, output.getFluidAmount());
    }

    @Test
    void repeatedViewsCannotOverfillTheInputTank() {
        var input = new NotifyingFluidTank(1000, () -> {});
        var output = new NotifyingFluidTank(1000, () -> {});
        var source = new OverloadProcessingFactoryFluidHandler(input, output);
        var a = LegacyTransferBridge.fluids(source);
        var b = LegacyTransferBridge.fluids(source);
        var water = FluidResource.of(new FluidStack(Fluids.WATER, 1));
        try (var tx = Transaction.openRoot()) {
            assertEquals(700, a.insert(0, water, 700, tx));
            assertEquals(300, b.insert(0, water, 700, tx));
            tx.commit();
        }
        assertEquals(1000, input.getFluidAmount());
        assertEquals(0, output.getFluidAmount());
    }

    @Test
    void singleTankCatalyzerStillRejectsExternalExtraction() {
        var tank = new NotifyingFluidTank(1000, () -> {});
        var source = new com.moakiee.ae2lt.machine.crystalcatalyzer.CrystalCatalyzerFluidHandler(tank);
        var view = LegacyTransferBridge.fluids(source);
        var water = FluidResource.of(new FluidStack(Fluids.WATER, 1));
        try (var tx = Transaction.openRoot()) {
            assertEquals(1000, view.insert(0, water, 1000, tx));
            tx.commit();
        }
        try (var tx = Transaction.openRoot()) {
            assertEquals(0, view.extract(0, water, 1000, tx));
            tx.commit();
        }
        assertEquals(1000, tank.getFluidAmount());
    }
}
