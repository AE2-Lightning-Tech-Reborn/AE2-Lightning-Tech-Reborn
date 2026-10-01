package com.moakiee.ae2lt.logic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.orientation.BlockOrientation;
import appeng.api.orientation.RelativeSide;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.MEStorage;
import appeng.me.storage.CompositeStorage;
import com.moakiee.ae2lt.machine.common.ManualInputTransfer;
import com.moakiee.ae2lt.machine.miningfactory.MiningFactoryInventory;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ManualItemExportTest {
    @BeforeAll static void bootstrap() {
        if (LoadingModList.get() == null) LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void disabledOrUnconfiguredExportNeverResolvesTargets() {
        var inv = new MiningFactoryInventory(null);
        inv.setItemDirect(2, new ItemStack(Items.STONE, 100));
        assertEquals(0, ManualItemExport.push(() -> null, false, BlockOrientation.NORTH_UP,
                EnumSet.allOf(RelativeSide.class), inv, 2, 9, direction -> { fail(); return null; }, new ManualInputTransfer.Budget()));
        assertEquals(0, ManualItemExport.push(() -> null, true, BlockOrientation.NORTH_UP,
                EnumSet.noneOf(RelativeSide.class), inv, 2, 9, direction -> { fail(); return null; }, new ManualInputTransfer.Budget()));
        assertEquals(100, inv.getStackInSlot(2).getCount());
    }

    @Test void multipleOutputsAndDirectionsShareOneBudget() {
        var inv = new MiningFactoryInventory(null);
        for (int slot = 2; slot < 11; slot++) inv.setItemDirect(slot, new ItemStack(Items.STONE, 4096));
        AtomicInteger calls = new AtomicInteger();
        var receiver = new CompositeStorage(Map.of(AEKeyType.items(), new MEStorage() {
            @Override public Component getDescription() { return Component.literal("Partial receiver"); }
            @Override public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
                assertInstanceOf(AEItemKey.class, what);
                assertEquals(Actionable.MODULATE, mode);
                assertTrue(inv.extractItem(2, 4096, false).isEmpty());
                calls.incrementAndGet();
                return 1;
            }
        }));
        var budget = new ManualInputTransfer.Budget();
        long sent = ManualItemExport.push(() -> null, true, BlockOrientation.NORTH_UP,
                EnumSet.allOf(RelativeSide.class), inv, 2, 9, direction -> receiver, budget);
        assertEquals(12, sent);
        assertEquals(12, calls.get());
        assertEquals(0, ManualItemExport.push(() -> null, true, BlockOrientation.NORTH_UP,
                EnumSet.allOf(RelativeSide.class), inv, 2, 9, direction -> { fail(); return null; }, budget));
        long left = 0;
        for (int slot = 2; slot < 11; slot++) left += inv.getStackInSlot(slot).getCount();
        assertEquals(4096 * 9, sent + left);
    }

    @Test void followsRotatedConfiguredSideAndKeepsRejectedOutput() {
        var inv = new MiningFactoryInventory(null);
        inv.setItemDirect(2, new ItemStack(Items.STONE, 123));
        var orientation = BlockOrientation.EAST_UP;
        var side = RelativeSide.values()[0];
        AtomicInteger calls = new AtomicInteger();
        assertEquals(0, ManualItemExport.push(() -> null, true, orientation, EnumSet.of(side), inv, 2, 9,
                direction -> {
                    assertEquals(orientation.getSide(side), direction);
                    calls.incrementAndGet();
                    return null;
                }, new ManualInputTransfer.Budget()));
        assertEquals(1, calls.get());
        assertEquals(123, inv.getStackInSlot(2).getCount());
    }
}
