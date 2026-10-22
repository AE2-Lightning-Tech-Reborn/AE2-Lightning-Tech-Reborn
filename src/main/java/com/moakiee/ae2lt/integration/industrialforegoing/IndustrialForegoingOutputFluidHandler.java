package com.moakiee.ae2lt.integration.industrialforegoing;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import appeng.api.storage.MEStorage;
import appeng.me.storage.ExternalStorageFacade;

import com.hrznstudio.titanium.block.tile.ActiveTile;
import com.hrznstudio.titanium.block.tile.PoweredTile;
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
            var owner = tile.getClass().getMethod("getCapability", Capability.class, Direction.class)
                    .getDeclaringClass();
            // Forge Titanium's PoweredTile only intercepts energy; all other
            // capabilities delegate unchanged to ActiveTile, including fluids.
            return owner == ActiveTile.class || owner == PoweredTile.class;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private static List<FluidTankComponent<?>> exposedOutputs(
            ActiveTile<?> tile, Direction face, ResourceLocation id) {
        var level = tile.getLevel();
        if (level == null || tile.isRemoved()) return List.of();
        if (level.getBlockEntity(tile.getBlockPos()) != tile) return List.of();
        var exposed = tile.getCapability(ForgeCapabilities.FLUID_HANDLER, face).orElse(null);
        var side = FacingUtil.getFacingRelative(tile.getFacingDirection(), face);
        // Titanium's exact stock handler delegates straight to these components.
        // Never bypass a missing capability, security wrapper, or custom handler.
        // For a recognized machine with an unverifiable view, fail closed for fluids.
        if (exposed == null || exposed.getClass() != MultiTankComponent.MultiTankCapabilityHandler.class
                || exposed != tile.getMultiTankComponent().getCapabilityForSide(side).orElse(null)) return List.of();

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
                || machine.equals(new ResourceLocation("industrialforegoing:animal_rancher"))
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
            filled += tank.fill(copyAmount(resource, resource.getAmount() - filled), action);
            if (filled >= resource.getAmount()) break;
        }
        return filled;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) return FluidStack.EMPTY;
        int drained = 0;
        for (var tank : outputs.get()) {
            drained += tank.drain(copyAmount(resource, resource.getAmount() - drained), action).getAmount();
            if (drained >= resource.getAmount()) break;
        }
        return drained == 0 ? FluidStack.EMPTY : copyAmount(resource, drained);
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        if (maxDrain <= 0) return FluidStack.EMPTY;
        for (var tank : outputs.get()) {
            var available = tank.drain(maxDrain, FluidAction.SIMULATE);
            if (!available.isEmpty()) return drain(copyAmount(available, maxDrain), action);
        }
        return FluidStack.EMPTY;
    }

    private static FluidStack copyAmount(FluidStack stack, int amount) {
        var copy = stack.copy();
        copy.setAmount(amount);
        return copy;
    }
}
