package com.moakiee.ae2lt.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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
import com.moakiee.ae2lt.machine.lightningchamber.LightningSimulationChamberInventory;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.loading.EarlyLoadingException;
import net.minecraftforge.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ManualItemExportTest {
    @BeforeAll
    static void bootstrap() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), new EarlyLoadingException("test bootstrap", null, List.of()));
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void disabledOrUnconfiguredExportDoesNotResolveTargets() {
        var inventory = new LightningSimulationChamberInventory(null);
        inventory.setItemDirect(4, new ItemStack(Items.STONE, 100));
        assertEquals(0, ManualItemExport.push(() -> null, false, BlockOrientation.NORTH_UP,
                EnumSet.allOf(RelativeSide.class), inventory, 4, 1,
                direction -> { fail("Disabled"); return null; }, new ManualInputTransfer.Budget()));
        assertEquals(0, ManualItemExport.push(() -> null, true, BlockOrientation.NORTH_UP,
                EnumSet.noneOf(RelativeSide.class), inventory, 4, 1,
                direction -> { fail("No sides"); return null; }, new ManualInputTransfer.Budget()));
        assertEquals(100, inventory.getStackInSlot(4).getCount());
    }

    @Test
    void exportRespectsSharedAttemptBudgetAndKeepsRemainder() {
        var inventory = new LightningSimulationChamberInventory(null);
        inventory.setItemDirect(4, new ItemStack(Items.STONE, 100));
        AtomicInteger calls = new AtomicInteger();
        var receiver = new CompositeStorage(Map.of(AEKeyType.items(), new MEStorage() {
            @Override public Component getDescription() { return Component.literal("Partial receiver"); }
            @Override public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
                assertTrue(key instanceof AEItemKey);
                assertEquals(Actionable.MODULATE, mode);
                assertTrue(inventory.extractItem(4, 100, false).isEmpty());
                calls.incrementAndGet();
                return 1;
            }
        }));
        var budget = new ManualInputTransfer.Budget();
        long sent = ManualItemExport.push(() -> null, true, BlockOrientation.NORTH_UP,
                EnumSet.allOf(RelativeSide.class), inventory, 4, 1, direction -> receiver, budget);
        sent += ManualItemExport.push(() -> null, true, BlockOrientation.NORTH_UP,
                EnumSet.allOf(RelativeSide.class), inventory, 4, 1, direction -> receiver, budget);
        assertEquals(ManualInputTransfer.EXPORT_ATTEMPTS, sent);
        assertEquals(ManualInputTransfer.EXPORT_ATTEMPTS, calls.get());
        assertEquals(100 - sent, inventory.getStackInSlot(4).getCount());
        assertEquals(0, ManualItemExport.push(() -> null, true, BlockOrientation.NORTH_UP,
                EnumSet.allOf(RelativeSide.class), inventory, 4, 1,
                direction -> { fail("Budget spent"); return null; }, budget));
    }
}
