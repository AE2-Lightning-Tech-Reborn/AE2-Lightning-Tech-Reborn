package com.moakiee.ae2lt.integration.industrialforegoing;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import com.hrznstudio.titanium.component.IComponentHarness;
import com.hrznstudio.titanium.component.fluid.FluidTankComponent;
import com.hrznstudio.titanium.component.inventory.MultiInventoryComponent.MultiInvCapabilityHandler;
import com.hrznstudio.titanium.component.inventory.SidedInventoryComponent;
import com.hrznstudio.titanium.component.sideness.IFacingComponent.FaceMode;
import com.hrznstudio.titanium.util.FacingUtil.Sideness;

class IndustrialForegoingOutputHandlersTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        if (LoadingModList.get() == null) LoadingModList.of(List.of(), List.of(), null);
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void tankGuiTypeAndExtractabilityDoNotIdentifyProducts() {
        var both = tank("input_fluid", FluidTankComponent.Action.BOTH, 1000);
        var fill = tank("water", FluidTankComponent.Action.FILL, 1000);
        var product = tank("output_fluid", FluidTankComponent.Action.DRAIN, 2000);
        var dissolution = new ResourceLocation("industrialforegoing:dissolution_chamber");
        assertEquals(1000, both.drain(1000, FluidAction.SIMULATE).getAmount());
        assertFalse(IndustrialForegoingOutputFluidHandler.isProduct(dissolution, both));
        assertFalse(IndustrialForegoingOutputFluidHandler.isProduct(dissolution, fill));
        assertTrue(IndustrialForegoingOutputFluidHandler.isProduct(dissolution, product));
        var rancherOutput = tank("fluid_output", FluidTankComponent.Action.BOTH, 1000);
        assertTrue(IndustrialForegoingOutputFluidHandler.isProduct(
                new ResourceLocation("industrialforegoing:animal_rancher"), rancherOutput));
        assertFalse(IndustrialForegoingOutputFluidHandler.isProduct(dissolution, rancherOutput));
    }

    @Test
    void sameFluidInputsCannotBeDrainedOrFilledThroughProductView() {
        var input = tank("input_fluid", FluidTankComponent.Action.BOTH, 1000);
        var first = tank("output_fluid", FluidTankComponent.Action.DRAIN, 1200);
        var second = tank("output_fluid", FluidTankComponent.Action.DRAIN, 800);
        var handler = new IndustrialForegoingOutputFluidHandler(() -> List.of(first, second));
        assertEquals(2000, handler.drain(new FluidStack(Fluids.WATER, 5000), FluidAction.SIMULATE).getAmount());
        assertEquals(1200, first.getFluidAmount());
        assertEquals(2000, handler.drain(5000, FluidAction.EXECUTE).getAmount());
        assertEquals(1000, input.getFluidAmount());
        assertEquals(0, handler.fill(new FluidStack(Fluids.WATER, 2000), FluidAction.EXECUTE));
        assertEquals(1000, input.getFluidAmount());
    }

    @Test
    void fluidComponentsDifferentFluidsAndSimulationArePreserved() {
        var water = tank("output", FluidTankComponent.Action.DRAIN, 1500);
        var lava = tank("output", FluidTankComponent.Action.DRAIN, 800);
        lava.setFluidStack(new FluidStack(Fluids.LAVA, 800));
        var handler = new IndustrialForegoingOutputFluidHandler(() -> List.of(water, lava));
        assertTrue(handler.drain(new FluidStack(Fluids.EMPTY, 0), FluidAction.EXECUTE).isEmpty());
        assertEquals(800, handler.drain(new FluidStack(Fluids.LAVA, 2000), FluidAction.SIMULATE).getAmount());
        assertEquals(800, lava.getFluidAmount());
        assertEquals(800, handler.drain(new FluidStack(Fluids.LAVA, 2000), FluidAction.EXECUTE).getAmount());
        assertEquals(1500, water.getFluidAmount());
    }

    @Test
    void revokedFluidViewFailsClosedAndBothOutputCanRestore() {
        var product = tank("fluid_output", FluidTankComponent.Action.BOTH, 1000);
        var visible = new AtomicReference<List<FluidTankComponent<?>>>(List.of(product));
        var handler = new IndustrialForegoingOutputFluidHandler(visible::get);
        assertEquals(700, handler.drain(700, FluidAction.EXECUTE).getAmount());
        assertEquals(700, handler.fill(new FluidStack(Fluids.WATER, 700), FluidAction.EXECUTE));
        visible.set(List.of());
        assertEquals(0, handler.getTanks());
        assertTrue(handler.getFluidInTank(0).isEmpty());
        assertTrue(handler.drain(5000, FluidAction.EXECUTE).isEmpty());
        assertEquals(0, handler.fill(new FluidStack(Fluids.WATER, 1), FluidAction.EXECUTE));
        assertEquals(1000, product.getFluidAmount());
    }

    @Test
    void itemRolesAreMachineSpecificAndUnknownMachinesFallBack() {
        assertEquals(Set.of("fertilizer"), IndustrialForegoingOutputItemHandler.outputNames("sewage_composter"));
        assertEquals(Set.of(), IndustrialForegoingOutputItemHandler.outputNames("plant_fertilizer"));
        assertEquals(Set.of("noEnchanted", "enchanted"), IndustrialForegoingOutputItemHandler.outputNames("enchantment_sorter"));
        assertNull(IndustrialForegoingOutputItemHandler.outputNames("material_stonework_factory"));
        assertNull(IndustrialForegoingOutputItemHandler.outputNames("unknown_future_machine"));
    }

    @Test
    void itemSlotsPreservePredicatesAndCannotCompensateIntoMatchingInputs() {
        var input = inventory("input", 11);
        var output = inventory("output", 23);
        var raw = new MultiInvCapabilityHandler<IComponentHarness>(List.of(input, output));
        var handler = new IndustrialForegoingOutputItemHandler(() -> raw, () -> Sideness.FRONT, Set.of("output"));
        assertEquals(11, raw.extractItem(0, 64, true).getCount());
        assertTrue(handler.extractItem(0, 64, false).isEmpty());
        assertEquals(23, handler.extractItem(1, 64, true).getCount());
        output.setOutputFilter((stack, slot) -> false);
        assertTrue(handler.extractItem(1, 64, false).isEmpty());
        output.setOutputFilter((stack, slot) -> true);
        assertEquals(23, handler.extractItem(1, 64, false).getCount());
        assertEquals(4, handler.insertItem(0, new ItemStack(Items.STONE, 4), false).getCount());
        output.setInputFilter((stack, slot) -> false);
        assertEquals(4, handler.insertItem(1, new ItemStack(Items.STONE, 4), false).getCount());
        assertEquals(11, input.getStackInSlot(0).getCount());
    }

    @Test
    void itemCapabilityReplacementAndSideChangeRevokeOldIndices() {
        var output = inventory("output", 23);
        var input = inventory("input", 11);
        var view = new AtomicReference<MultiInvCapabilityHandler<?>>(new MultiInvCapabilityHandler<>(List.of(output)));
        var handler = new IndustrialForegoingOutputItemHandler(view::get, () -> Sideness.FRONT, Set.of("output"));
        assertEquals(23, handler.getStackInSlot(0).getCount());
        output.getFacingModes().put(Sideness.FRONT, FaceMode.NONE);
        assertTrue(handler.extractItem(0, 64, false).isEmpty());
        view.set(new MultiInvCapabilityHandler<>(List.of(input)));
        assertTrue(handler.getStackInSlot(0).isEmpty());
        view.set(null);
        assertEquals(0, handler.getSlots());
        assertTrue(handler.extractItem(0, 64, false).isEmpty());
        assertEquals(11, input.getStackInSlot(0).getCount());
    }

    private static FluidTankComponent<IComponentHarness> tank(String name, FluidTankComponent.Action action, int amount) {
        var tank = new FluidTankComponent<IComponentHarness>(name, 10000, 0, 0).setTankAction(action);
        tank.setFluidStack(new FluidStack(Fluids.WATER, amount));
        return tank;
    }

    private static SidedInventoryComponent<IComponentHarness> inventory(String name, int amount) {
        var inventory = new SidedInventoryComponent<IComponentHarness>(name, 0, 0, 1, 0);
        inventory.setStackInSlot(0, new ItemStack(Items.STONE, amount));
        return inventory;
    }
}
