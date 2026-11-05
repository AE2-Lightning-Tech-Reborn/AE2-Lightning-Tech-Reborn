package com.moakiee.ae2lt.blockentity;

import java.util.EnumSet;
import java.util.Map;
import java.util.function.LongUnaryOperator;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.orientation.BlockOrientation;
import appeng.api.orientation.RelativeSide;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.MEStorage;
import appeng.me.storage.CompositeStorage;
import com.moakiee.ae2lt.logic.transfer.AdjacentItemAutoExportHelper;
import com.moakiee.ae2lt.machine.overloadfactory.NotifyingFluidTank;
import com.moakiee.ae2lt.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real factory fluid capability, receiver tank credits, and host save/reload. */
@GameTestHolder("ae2lt_interface_input")
@PrefixGameTestTemplate(false)
public class AdjacentFluidExportReceiptGameTests {
    private static OverloadProcessingFactoryBlockEntity factory(GameTestHelper helper) {
        var pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ModBlocks.OVERLOAD_PROCESSING_FACTORY.get());
        var machine = (OverloadProcessingFactoryBlockEntity) helper.getBlockEntity(pos);
        machine.setAutoExportEnabled(false);
        return machine;
    }

    private static NotifyingFluidTank output(OverloadProcessingFactoryBlockEntity machine) throws Exception {
        var field = OverloadProcessingFactoryBlockEntity.class.getDeclaredField("outputTank");
        field.setAccessible(true);
        return (NotifyingFluidTank) field.get(machine);
    }

    private static CompositeStorage target(LongUnaryOperator insertion) {
        return new CompositeStorage(Map.of(AEKeyType.fluids(), new MEStorage() {
            public Component getDescription() { return Component.literal("real fluid receiver"); }
            public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
                return insertion.applyAsLong(amount);
            }
        }));
    }

    private static boolean push(OverloadProcessingFactoryBlockEntity machine, NotifyingFluidTank tank,
                                CompositeStorage target) {
        return AdjacentItemAutoExportHelper.pushOutFluid(machine, BlockOrientation.NORTH_UP,
                EnumSet.allOf(RelativeSide.class), tank::getFluid, tank::exportFluid, direction -> target);
    }

    @GameTest(template = "empty")
    public static void processingFactoryNeverReplaysFaultyFluidReceipt(GameTestHelper helper) throws Exception {
        var machine = factory(helper); var tank = output(machine);
        var receiver = new NotifyingFluidTank(10000, () -> {});
        for (int fault = 0; fault < 5; fault++) {
            tank.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
            int selected = fault; int[] calls = {0};
            var target = target(amount -> {
                calls[0]++;
                helper.assertTrue(machine.getOutputFluid().isEmpty(), "source must be detached before credit");
                var saved = machine.saveWithoutMetadata();
                helper.assertTrue(FluidStack.loadFluidStackFromNBT(saved.getCompound("OutputTank")).isEmpty(),
                        "in-flight host snapshot must not retain reserved fluid");
                helper.assertTrue(receiver.fill(new FluidStack(Fluids.WATER, (int) amount), FluidAction.EXECUTE)
                        == amount, "receiver rejected expected credit");
                return switch (selected) {
                    case 0 -> -1;
                    case 1 -> amount + 1;
                    case 2 -> Long.MAX_VALUE;
                    case 3 -> throw new IllegalStateException("failure after fluid credit");
                    default -> throw new NoSuchMethodError("ABI failure after fluid credit");
                };
            });
            for (int retry = 0; retry < 1000; retry++) push(machine, tank, target);
            helper.assertTrue(calls[0] == 1 && tank.isEmpty(), "uncertain fluid credit was replayed");
            machine.loadTag(machine.saveWithoutMetadata());
            helper.assertTrue(machine.getOutputFluid().isEmpty(), "reload restored uncertain credit");
            helper.assertTrue(receiver.getFluidAmount() == (fault + 1) * 1000, "receiver credit is not conserved");
        }
        helper.assertTrue(tank.fill(new FluidStack(Fluids.LAVA, 3), FluidAction.EXECUTE) == 3,
                "export reservation did not unlock after receipt faults");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void processingFactoryRejectsReentrantDrainAndExport(GameTestHelper helper) throws Exception {
        var machine = factory(helper); var tank = output(machine);
        tank.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        var capability = machine.getFluidHandlerCapability(null);
        int[] credits = {0};
        var nested = target(amount -> { credits[0]++; return amount; });
        push(machine, tank, target(amount -> {
            helper.assertTrue(capability.drain(1000, FluidAction.EXECUTE).isEmpty(), "reentrant drain duplicated offer");
            helper.assertTrue(capability.drain(new FluidStack(Fluids.WATER, 1000), FluidAction.SIMULATE).isEmpty(),
                    "simulation exposed reserved fluid");
            helper.assertTrue(!push(machine, tank, nested) && credits[0] == 0, "nested export credited same source");
            helper.assertTrue(tank.fill(new FluidStack(Fluids.LAVA, 4000), FluidAction.EXECUTE) == 0,
                    "reserved tail space was replaced during callback");
            return 17;
        }));
        helper.assertTrue(tank.getFluidAmount() == 898, "partial receipts across six sides lost source remainder");
        machine.loadTag(machine.saveWithoutMetadata());
        helper.assertTrue(machine.getOutputFluid().getAmount() == 898, "reloaded remainder is incorrect");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void processingFactoryPreservesTaggedPartialAndRejectedFluid(GameTestHelper helper) throws Exception {
        var machine = factory(helper); var tank = output(machine);
        var fluid = new FluidStack(Fluids.WATER, 1000); var tag = new CompoundTag();
        tag.putString("Owner", "native regression"); fluid.setTag(tag);
        tank.fill(fluid, FluidAction.EXECUTE);
        var receiver = new NotifyingFluidTank(4000, () -> {});
        push(machine, tank, target(amount -> {
            machine.saveWithoutMetadata();
            return receiver.fill(new FluidStack(fluid, 100), FluidAction.EXECUTE);
        }));
        helper.assertTrue(receiver.getFluidAmount() == 600 && tank.getFluidAmount() == 400,
                "partial fluid receipts are not conserved");
        for (int retry = 0; retry < 1000; retry++) push(machine, tank, target(amount -> 0));
        machine.loadTag(machine.saveWithoutMetadata());
        helper.assertTrue(machine.getOutputFluid().getAmount() == 400 && tag.equals(machine.getOutputFluid().getTag()),
                "rejection or reload changed tagged tail");
        helper.succeed();
    }
}
