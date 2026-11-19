package com.moakiee.ae2lt.integration.mekanism;

import java.util.Arrays;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import appeng.api.storage.MEStorage;
import appeng.me.storage.ExternalStorageFacade;

import mekanism.api.Action;
import mekanism.api.fluid.IExtendedFluidHandler;
import mekanism.api.fluid.IMekanismFluidHandler;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.interfaces.ISideConfiguration;

/** AUTO-return view of a Mekanism machine's product fluid tanks; see {@link MekanismOutputRoles}. */
public final class MekanismOutputFluidHandler implements IFluidHandler {
    private final IExtendedFluidHandler exposed;
    private final IMekanismFluidHandler machine;
    private final Supplier<ConfigInfo> config;
    private final Direction face;

    MekanismOutputFluidHandler(IExtendedFluidHandler exposed, IMekanismFluidHandler machine,
                               Supplier<ConfigInfo> config, Direction face) {
        this.exposed = exposed;
        this.machine = machine;
        this.config = config;
        this.face = face;
    }

    /** Called only when Mekanism is installed; non-configurable fluid holders keep the usual view. */
    @Nullable
    public static MEStorage restrict(BlockEntity blockEntity, Direction face, MEStorage fallback) {
        if (!(blockEntity instanceof ISideConfiguration configuration)
                || !(blockEntity instanceof IMekanismFluidHandler machine)
                || !machine.canHandleFluid()
                || configuration.getConfig() == null
                || configuration.getConfig().getConfig(TransmissionType.FLUID) == null) {
            return fallback;
        }
        var level = blockEntity.getLevel();
        var handler = level == null ? null : level.getCapability(
                Capabilities.FluidHandler.BLOCK, blockEntity.getBlockPos(),
                blockEntity.getBlockState(), blockEntity, face);
        if (handler == null) {
            return null;
        }
        // Per-tank access is required to tell inputs from products holding the
        // same fluid. An unexpected wrapper fails closed for this machine's fluids.
        if (!(handler instanceof IExtendedFluidHandler extended)) {
            return ExternalStorageFacade.of(EMPTY);
        }
        return ExternalStorageFacade.of(new MekanismOutputFluidHandler(
                extended, machine,
                () -> configuration.getConfig().getConfig(TransmissionType.FLUID), face));
    }

    private int[] productTanks() {
        var tanks = machine.getFluidTanks(face);
        var fluidConfig = config.get();
        if (fluidConfig == null || exposed.getTanks() != tanks.size()) {
            return new int[0];
        }
        int count = 0;
        var result = new int[tanks.size()];
        for (int i = 0; i < tanks.size(); i++) {
            if (MekanismOutputRoles.isProductTank(fluidConfig, tanks.get(i))) {
                result[count++] = i;
            }
        }
        return Arrays.copyOf(result, count);
    }

    @Override
    public int getTanks() {
        return productTanks().length;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        var products = productTanks();
        return tank >= 0 && tank < products.length
                ? exposed.getFluidInTank(products[tank]).copy()
                : FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        var products = productTanks();
        return tank >= 0 && tank < products.length ? exposed.getTankCapacity(products[tank]) : 0;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        var products = productTanks();
        return tank >= 0 && tank < products.length && exposed.isFluidValid(products[tank], stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        // Compensation only, under each product tank's normal external permission.
        if (resource.isEmpty()) return 0;
        var mekAction = Action.fromFluidAction(action);
        int filled = 0;
        for (int tank : productTanks()) {
            if (filled >= resource.getAmount()) break;
            var offered = resource.copyWithAmount(resource.getAmount() - filled);
            var remainder = exposed.insertFluid(tank, offered, mekAction);
            filled += offered.getAmount() - remainder.getAmount();
        }
        return filled;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) return FluidStack.EMPTY;
        var mekAction = Action.fromFluidAction(action);
        int drained = 0;
        for (int tank : productTanks()) {
            if (drained >= resource.getAmount()) break;
            if (!FluidStack.isSameFluidSameComponents(exposed.getFluidInTank(tank), resource)) continue;
            var taken = exposed.extractFluid(tank, resource.getAmount() - drained, mekAction);
            if (FluidStack.isSameFluidSameComponents(taken, resource)) {
                drained += taken.getAmount();
            }
        }
        return drained == 0 ? FluidStack.EMPTY : resource.copyWithAmount(drained);
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        if (maxDrain <= 0) return FluidStack.EMPTY;
        for (int tank : productTanks()) {
            var stored = exposed.getFluidInTank(tank);
            if (!stored.isEmpty()) return drain(stored.copyWithAmount(maxDrain), action);
        }
        return FluidStack.EMPTY;
    }

    private static final IFluidHandler EMPTY = new IFluidHandler() {
        @Override
        public int getTanks() {
            return 0;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return 0;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return false;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    };
}
