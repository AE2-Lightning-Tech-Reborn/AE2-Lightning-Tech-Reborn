package com.moakiee.ae2lt.blockentity;

import java.util.EnumSet;
import java.util.Map;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.orientation.BlockOrientation;
import appeng.api.orientation.RelativeSide;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.MEStorage;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.me.storage.CompositeStorage;
import com.moakiee.ae2lt.logic.transfer.ManualItemExport;
import com.moakiee.ae2lt.machine.common.ManualInputTransfer;
import com.moakiee.ae2lt.machine.lightningchamber.LargeStackItemHandler;
import com.moakiee.ae2lt.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.ItemHandlerHelper;

/** Actual machine inventories, chest credits and block-entity save/reload after faulty receipts. */
@GameTestHolder("ae2lt_interface_input")
@PrefixGameTestTemplate(false)
public final class ManualItemExportReceiptGameTests {
    @GameTest(template = "empty")
    public static void simulationChamberDoesNotReplayUnknownExportReceipt(GameTestHelper helper) {
        checkExportRecovery(helper, 0);
    }

    @GameTest(template = "empty")
    public static void assemblyChamberDoesNotReplayUnknownExportReceipt(GameTestHelper helper) {
        checkExportRecovery(helper, 1);
    }

    @GameTest(template = "empty")
    public static void processingFactoryDoesNotReplayUnknownExportReceipt(GameTestHelper helper) {
        checkExportRecovery(helper, 2);
    }

    @GameTest(template = "empty")
    public static void miningFactoryDoesNotReplayUnknownExportReceipt(GameTestHelper helper) {
        checkExportRecovery(helper, 3);
    }

    private static void checkExportRecovery(GameTestHelper helper, int type) {
        var pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos.east(), Blocks.CHEST);
        var chest = helper.getBlockEntity(pos.east()).getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(
                () -> new IllegalStateException("chest capability missing"));
        helper.setBlock(pos, switch (type) {
            case 0 -> ModBlocks.LIGHTNING_SIMULATION_CHAMBER.get();
            case 1 -> ModBlocks.LIGHTNING_ASSEMBLY_CHAMBER.get();
            case 2 -> ModBlocks.OVERLOAD_PROCESSING_FACTORY.get();
            default -> ModBlocks.MINING_FACTORY.get();
        });
        var host = (AEBaseBlockEntity) helper.getBlockEntity(pos);
        LargeStackItemHandler inventory;
        int output;
        if (host instanceof LightningSimulationChamberBlockEntity machine) {
            machine.setAutoExportEnabled(false);
            inventory = machine.getInventory(); output = 4;
        } else if (host instanceof LightningAssemblyChamberBlockEntity machine) {
            machine.setAutoExportEnabled(false);
            inventory = machine.getInventory(); output = 10;
        } else if (host instanceof OverloadProcessingFactoryBlockEntity machine) {
            machine.setAutoExportEnabled(false);
            inventory = machine.getInventory(); output = 10;
        } else {
            var machine = (MiningFactoryBlockEntity) host;
            machine.setAutoExportEnabled(false);
            inventory = machine.getInventory(); output = 2;
        }
        for (int fault = 0; fault < 5; fault++) {
            inventory.setItemDirect(output, new ItemStack(Items.STONE, 64));
            int selected = fault;
            int[] calls = {0};
            boolean[] valid = {true};
            var receiver = new CompositeStorage(Map.of(AEKeyType.items(), new MEStorage() {
                public Component getDescription() { return Component.literal("chest credit then bad receipt"); }
                public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
                    calls[0]++;
                    valid[0] &= inventory.getStackInSlot(output).isEmpty();
                    host.saveWithoutMetadata();
                    var offered = ((AEItemKey) what).toStack((int) amount);
                    var remainder = ItemHandlerHelper.insertItemStacked(chest, offered, false);
                    valid[0] &= remainder.isEmpty();
                    return switch (selected) {
                        case 0 -> -1;
                        case 1 -> amount + 1;
                        case 2 -> Long.MAX_VALUE;
                        case 3 -> throw new IllegalStateException("failure after chest credit");
                        default -> throw new NoSuchMethodError("ABI failure after chest credit");
                    };
                }
            }));
            for (int retry = 0; retry < 1000; retry++) {
                ManualItemExport.push((IActionHost) host, true, BlockOrientation.NORTH_UP,
                        EnumSet.allOf(RelativeSide.class), inventory, output, 1, direction -> receiver,
                        new ManualInputTransfer.Budget());
            }
            helper.assertTrue(valid[0] && calls[0] == 1 && inventory.getStackInSlot(output).isEmpty(),
                    "faulty receipt replayed output or failed to detach its snapshot");
            var snapshot = host.saveWithoutMetadata();
            host.loadTag(snapshot);
            helper.assertTrue(inventory.getStackInSlot(output).isEmpty(), "reload restored an uncertain credit");
            long received = 0;
            for (int slot = 0; slot < chest.getSlots(); slot++) received += chest.getStackInSlot(slot).getCount();
            helper.assertTrue(received == (fault + 1L) * 64, "chest received duplicated or missing input");
        }
        inventory.setItemDirect(output, new ItemStack(Items.DIRT, 3));
        helper.assertTrue(inventory.extractItem(output, 3, false).getCount() == 3,
                "export reservation was not released after fault recovery");
        helper.succeed();
    }
}
