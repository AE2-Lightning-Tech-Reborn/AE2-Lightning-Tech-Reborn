package com.moakiee.ae2lt.logic;

import com.moakiee.ae2lt.logic.transfer.AdjacentItemAutoExportHelper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.orientation.BlockOrientation;
import appeng.api.orientation.RelativeSide;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.MEStorage;
import appeng.me.storage.CompositeStorage;
import com.moakiee.ae2lt.machine.overloadfactory.NotifyingFluidTank;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AdjacentFluidAutoExportTest {
    @BeforeAll static void bootstrap() {
        if (LoadingModList.get() == null) LoadingModList.of(List.of(), List.of(),
                new net.minecraftforge.fml.loading.EarlyLoadingException("test bootstrap", null, List.of()));
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
    }

    private static boolean push(NotifyingFluidTank tank, CompositeStorage target) {
        return AdjacentItemAutoExportHelper.pushOutFluid(() -> null, BlockOrientation.NORTH_UP,
                EnumSet.allOf(RelativeSide.class), tank::getFluid,
                tank::exportFluid, direction -> target);
    }

    private static CompositeStorage target(java.util.function.LongUnaryOperator insertion) {
        return new CompositeStorage(Map.of(AEKeyType.fluids(), new MEStorage() {
            public Component getDescription() { return Component.literal("fluid export regression"); }
            public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
                return insertion.applyAsLong(amount);
            }
        }));
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3, 4})
    void faultyReceiptAfterCreditIsNeverReplayed(int fault) {
        var tank = new NotifyingFluidTank(4000, () -> {});
        tank.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        long[] credited = {0}; int[] calls = {0};
        var target = target(amount -> {
            credited[0] += amount; calls[0]++;
            return switch (fault) {
                case 0 -> -1;
                case 1 -> amount + 1;
                case 2 -> Long.MAX_VALUE;
                case 3 -> throw new IllegalStateException("failure after credit");
                default -> throw new NoSuchMethodError("ABI failure after credit");
            };
        });
        assertDoesNotThrow(() -> { for (int retry = 0; retry < 1000; retry++) push(tank, target); });
        assertEquals(1000, credited[0]); assertEquals(1, calls[0]); assertTrue(tank.isEmpty());
        var restored = new NotifyingFluidTank(4000, () -> {});
        restored.readFromNBT(tank.writeToNBT(new CompoundTag())); assertTrue(restored.isEmpty());
        assertEquals(3, tank.fill(new FluidStack(Fluids.LAVA, 3), FluidAction.EXECUTE));
    }

    @Test void sourceIsDetachedBeforeForeignCallbackAndCapabilityDrain() {
        var tank = new NotifyingFluidTank(4000, () -> {});
        tank.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        assertTrue(push(tank, target(amount -> {
            assertTrue(tank.isEmpty(), "the receiver must not observe a second owner");
            assertTrue(tank.drain(1000, FluidAction.EXECUTE).isEmpty());
            var restored = new NotifyingFluidTank(4000, () -> {});
            restored.readFromNBT(tank.writeToNBT(new CompoundTag())); assertTrue(restored.isEmpty());
            return amount;
        })));
        assertTrue(tank.isEmpty());
    }

    @Test void partialReceiptPreservesFluidTagsAndAmountAcrossSides() {
        var tank = new NotifyingFluidTank(4000, () -> {});
        var fluid = new FluidStack(Fluids.WATER, 1000);
        var tag = new CompoundTag(); tag.putString("Owner", "regression"); fluid.setTag(tag);
        tank.fill(fluid, FluidAction.EXECUTE);
        long[] credited = {0};
        assertTrue(push(tank, target(amount -> { credited[0] += 100; return 100; })));
        assertEquals(600, credited[0]); assertEquals(400, tank.getFluidAmount());
        assertEquals(tag, tank.getFluid().getTag());
    }

    @Test void ordinaryRejectionDoesNotMarkDirtyOnEveryRetry() {
        var notices = new AtomicInteger(); var tank = new NotifyingFluidTank(4000, notices::incrementAndGet);
        tank.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE); notices.set(0);
        var reject = target(amount -> 0);
        for (int retry = 0; retry < 1000; retry++) assertFalse(push(tank, reject));
        assertEquals(1000, tank.getFluidAmount()); assertEquals(0, notices.get());
    }
    @Test void emptySourceNeverResolvesForeignTargets() {
        var tank = new NotifyingFluidTank(4000, () -> {});
        assertFalse(AdjacentItemAutoExportHelper.pushOutFluid(() -> null, BlockOrientation.NORTH_UP,
                EnumSet.allOf(RelativeSide.class), tank::getFluid, tank::exportFluid,
                direction -> { fail("empty source resolved a foreign capability"); return null; }));
    }

    @Test void disabledOrUnconfiguredSidesNeverDetachFluid() {
        var tank = new NotifyingFluidTank(4000, () -> {});
        tank.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        assertFalse(AdjacentItemAutoExportHelper.pushOutFluid(() -> null, null,
                EnumSet.allOf(RelativeSide.class), tank::getFluid, tank::exportFluid,
                direction -> { fail(); return null; }));
        assertFalse(AdjacentItemAutoExportHelper.pushOutFluid(() -> null, BlockOrientation.NORTH_UP,
                EnumSet.noneOf(RelativeSide.class), tank::getFluid, tank::exportFluid,
                direction -> { fail(); return null; }));
        assertEquals(1000, tank.getFluidAmount());
    }

    @Test void configuredRotationAndMissingReceiverKeepSourceUnchanged() {
        var tank = new NotifyingFluidTank(4000, () -> {});
        tank.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        var side = RelativeSide.values()[0]; int[] calls = {0};
        assertFalse(AdjacentItemAutoExportHelper.pushOutFluid(() -> null, BlockOrientation.EAST_UP,
                EnumSet.of(side), tank::getFluid, tank::exportFluid, direction -> {
                    calls[0]++; assertEquals(BlockOrientation.EAST_UP.getSide(side), direction); return null;
                }));
        assertEquals(1, calls[0]); assertEquals(1000, tank.getFluidAmount());
    }

}
