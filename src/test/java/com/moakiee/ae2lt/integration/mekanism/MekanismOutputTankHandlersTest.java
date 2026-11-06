package com.moakiee.ae2lt.integration.mekanism;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.me.storage.ExternalStorageFacade;

import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.fluid.IMekanismFluidHandler;
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.capabilities.proxy.ProxyFluidHandler;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.component.config.slot.FluidSlotInfo;

class MekanismOutputTankHandlersTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), new net.minecraftforge.fml.loading.EarlyLoadingException("test", null, List.of()));
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** Mirrors TileComponentConfig.setupIOConfig(type, tank, side): one tank in every group. */
    @Test
    void secondaryTankRegisteredAsOutputIsNotAProduct() {
        var secondary = BasicFluidTank.create(1000, null);
        var config = new ConfigInfo(() -> Direction.NORTH);
        config.addSlotInfo(DataType.INPUT, new FluidSlotInfo(true, false, secondary));
        config.addSlotInfo(DataType.OUTPUT, new FluidSlotInfo(false, true, secondary));
        config.addSlotInfo(DataType.INPUT_OUTPUT, new FluidSlotInfo(true, true, secondary));

        assertFalse(MekanismOutputRoles.isProductTank(config, secondary));
    }

    /** Mirrors setupIOConfig(type, input, output, side): separate input and product tanks. */
    @Test
    void separateOutputTankIsAProduct() {
        var input = BasicFluidTank.create(1000, null);
        var output = BasicFluidTank.create(1000, null);
        var config = new ConfigInfo(() -> Direction.NORTH);
        config.addSlotInfo(DataType.INPUT, new FluidSlotInfo(true, false, input));
        config.addSlotInfo(DataType.OUTPUT, new FluidSlotInfo(false, true, output));
        config.addSlotInfo(DataType.INPUT_OUTPUT, new FluidSlotInfo(true, true, input, output));

        assertFalse(MekanismOutputRoles.isProductTank(config, input));
        assertTrue(MekanismOutputRoles.isProductTank(config, output));
    }

    @Test
    void extraAndUnlistedTanksAreNeverProducts() {
        var extra = BasicFluidTank.create(1000, null);
        var unlisted = BasicFluidTank.create(1000, null);
        var config = new ConfigInfo(() -> Direction.NORTH);
        config.addSlotInfo(DataType.OUTPUT, new FluidSlotInfo(false, true, extra));
        config.addSlotInfo(DataType.EXTRA, new FluidSlotInfo(true, true, extra));

        assertFalse(MekanismOutputRoles.isProductTank(config, extra));
        assertFalse(MekanismOutputRoles.isProductTank(config, unlisted));
        assertFalse(MekanismOutputRoles.isProductTank(null, extra));
    }

    /** Bidirectional machines (setupIOConfig with alwaysAllow) keep their extractable tank. */
    @Test
    void alwaysExtractableBidirectionalTankRemainsReturnable() {
        var tank = BasicFluidTank.create(1000, null);
        var config = new ConfigInfo(() -> Direction.NORTH);
        config.addSlotInfo(DataType.INPUT, new FluidSlotInfo(true, true, tank));
        config.addSlotInfo(DataType.OUTPUT, new FluidSlotInfo(true, true, tank));

        assertTrue(MekanismOutputRoles.isProductTank(config, tank));
    }

    @Test
    void fluidViewOnlyDrainsProductTankEvenWithTheSameFluid() {
        var fixture = new FluidFixture();
        var storage = ExternalStorageFacade.of(fixture.handler);
        var water = AEFluidKey.of(Fluids.WATER);

        assertEquals(300, storage.getAvailableStacks().get(water));
        assertEquals(300, storage.extract(water, 10_000, Actionable.MODULATE, IActionSource.empty()));
        assertEquals(700, fixture.input.getFluidAmount());
        assertEquals(0, fixture.output.getFluidAmount());
    }

    @Test
    void fluidViewHidesInputTankAndRefusesCompensationIntoIt() {
        var fixture = new FluidFixture();
        assertEquals(1, fixture.handler.getTanks());
        assertEquals(300, fixture.handler.getFluidInTank(0).getAmount());

        fixture.output.setStack(FluidStack.EMPTY);
        assertTrue(fixture.handler.drain(1000, net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE)
                .isEmpty());
        // The output tank refuses external inserts, so compensation must not leak into the input.
        assertEquals(0, fixture.handler.fill(new FluidStack(Fluids.WATER, 50),
                net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE));
        assertEquals(700, fixture.input.getFluidAmount());
    }

    @Test
    void changingExposedTanksCannotReuseAnOldProductIndex() {
        var fixture = new FluidFixture();
        fixture.machine.tanks = List.of(fixture.input);
        assertEquals(0, fixture.handler.getTanks());
        assertTrue(fixture.handler.drain(new FluidStack(Fluids.WATER, 1000),
                net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE).isEmpty());
        assertEquals(700, fixture.input.getFluidAmount());
    }

    private static final class FluidFixture {
        private final BasicFluidTank input = BasicFluidTank.create(1000, null);
        private final BasicFluidTank output = BasicFluidTank.output(1000, null);
        private final Machine machine = new Machine(List.of(input, output));
        private final ConfigInfo config = new ConfigInfo(() -> Direction.NORTH);
        private final MekanismOutputFluidHandler handler;

        private FluidFixture() {
            input.setStack(new FluidStack(Fluids.WATER, 700));
            output.setStack(new FluidStack(Fluids.WATER, 300));
            config.addSlotInfo(DataType.INPUT, new FluidSlotInfo(true, false, input));
            config.addSlotInfo(DataType.OUTPUT, new FluidSlotInfo(false, true, output));
            config.addSlotInfo(DataType.INPUT_OUTPUT, new FluidSlotInfo(true, true, input, output));
            handler = new MekanismOutputFluidHandler(
                    new ProxyFluidHandler(machine, Direction.NORTH, null), machine, () -> config, Direction.NORTH);
        }
    }

    private static final class Machine implements IMekanismFluidHandler {
        private List<IExtendedFluidTank> tanks;

        private Machine(List<IExtendedFluidTank> tanks) {
            this.tanks = tanks;
        }

        @Override
        public List<IExtendedFluidTank> getFluidTanks(Direction side) {
            return tanks;
        }

        @Override
        public void onContentsChanged() {}
    }
}
