package com.moakiee.ae2lt.integration.industrialforegoing;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import appeng.api.storage.MEStorage;
import appeng.me.storage.ExternalStorageFacade;

import com.hrznstudio.titanium.block.tile.ActiveTile;
import com.hrznstudio.titanium.component.fluid.FluidTankComponent;
import com.hrznstudio.titanium.component.fluid.MultiTankComponent;
import com.hrznstudio.titanium.component.sideness.IFacingComponent;
import com.hrznstudio.titanium.util.FacingUtil;

/** AUTO-only view of Industrial Foregoing's product tanks, not its extractable inputs. */
public final class IndustrialForegoingOutputFluidHandler implements IFluidHandler {
    private final Supplier<List<FluidTankComponent<?>>> outputs;

    IndustrialForegoingOutputFluidHandler(Supplier<List<FluidTankComponent<?>>> outputs) {
        this.outputs = outputs;
    }

    /** Loaded only behind the Industrial Foregoing + Titanium mod-presence gates. */
    public static MEStorage restrict(BlockEntity blockEntity, Direction face, MEStorage fallback) {
        var id = BuiltInRegistries.BLOCK.getKey(blockEntity.getBlockState().getBlock());
        if (!id.getNamespace().equals("industrialforegoing") || !(blockEntity instanceof ActiveTile<?> tile)
                || tile.getMultiTankComponent() == null) {
            // No reliable role metadata: retain the ordinary capability behavior.
            return fallback;
        }
        boolean stockAccess = usesStockFluidAccess(tile);
        return ExternalStorageFacade.of(new IndustrialForegoingOutputFluidHandler(
                () -> stockAccess ? exposedOutputs(tile, face, id) : List.of()));
    }

    private static boolean usesStockFluidAccess(ActiveTile<?> tile) {
        try {
            // An override can return a stock-shaped handler with a restricted subset.
            // Its role/permission mapping is not proven by the handler class alone.
            return tile.getClass().getMethod("getFluidHandler", Direction.class).getDeclaringClass() == ActiveTile.class;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private static List<FluidTankComponent<?>> exposedOutputs(
            ActiveTile<?> tile, Direction face, ResourceLocation id) {
        var level = tile.getLevel();
        if (level == null || tile.isRemoved()) return List.of();
        var exposed = level.getCapability(Capabilities.FluidHandler.BLOCK,
                tile.getBlockPos(), tile.getBlockState(), tile, face);
        // Titanium's exact stock handler delegates straight to these components.
        // Never bypass a missing capability, security wrapper, or custom handler.
        // For a recognized machine with an unverifiable view, fail closed for fluids.
        if (exposed == null || exposed.getClass() != MultiTankComponent.MultiTankCapabilityHandler.class
                || exposed != tile.getFluidHandler(face)) return List.of();

        var side = FacingUtil.getFacingRelative(tile.getFacingDirection(), face);
        return tile.getMultiTankComponent().getTanks().stream()
                .filter(tank -> isProduct(id, tank))
                .filter(tank -> !(tank instanceof IFacingComponent sided)
                        || sided.getFacingModes().get(side) != null
                        && sided.getFacingModes().get(side).allowsConnection())
                .<FluidTankComponent<?>>map(tank -> tank)
                .toList();
    }

    static boolean isProduct(ResourceLocation machine, FluidTankComponent<?> tank) {
        // Type is only a GUI size in Titanium. Action.DRAIN is explicit output intent;
        // BOTH is ambiguous (e.g. dissolution input, latex/water, enchantment essence).
        // Animal Rancher's milk output is the audited exception that defaults to BOTH.
        return tank.getTankAction() == FluidTankComponent.Action.DRAIN
                || machine.equals(ResourceLocation.parse("industrialforegoing:animal_rancher"))
                && tank.getName().equals("fluid_output")
                && tank.getTankAction() == FluidTankComponent.Action.BOTH;
    }

    @Override
    public int getTanks() {
        return outputs.get().size();
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        var current = outputs.get();
        return tank >= 0 && tank < current.size() ? current.get(tank).getFluid().copy() : FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        var current = outputs.get();
        return tank >= 0 && tank < current.size() ? current.get(tank).getCapacity() : 0;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        var current = outputs.get();
        return tank >= 0 && tank < current.size() && current.get(tank).isFluidValid(stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        // Compensation only. Obey each output tank's normal fill permission;
        // never restore into an input tank or use fillForced on a DRAIN-only tank.
        int filled = 0;
        for (var tank : outputs.get()) {
            filled += tank.fill(resource.copyWithAmount(resource.getAmount() - filled), action);
            if (filled >= resource.getAmount()) break;
        }
        return filled;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) return FluidStack.EMPTY;
        int drained = 0;
        for (var tank : outputs.get()) {
            drained += tank.drain(resource.copyWithAmount(resource.getAmount() - drained), action).getAmount();
            if (drained >= resource.getAmount()) break;
        }
        return drained == 0 ? FluidStack.EMPTY : resource.copyWithAmount(drained);
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        if (maxDrain <= 0) return FluidStack.EMPTY;
        for (var tank : outputs.get()) {
            var available = tank.drain(maxDrain, FluidAction.SIMULATE);
            if (!available.isEmpty()) return drain(available.copyWithAmount(maxDrain), action);
        }
        return FluidStack.EMPTY;
    }
}
