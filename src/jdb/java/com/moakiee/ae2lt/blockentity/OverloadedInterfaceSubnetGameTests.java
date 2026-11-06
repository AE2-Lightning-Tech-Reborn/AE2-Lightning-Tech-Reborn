package com.moakiee.ae2lt.blockentity;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.api.util.AEColor;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.capabilities.Capabilities;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import com.moakiee.ae2lt.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2lt_interface_input")
@PrefixGameTestTemplate(false)
public final class OverloadedInterfaceSubnetGameTests {
    private static final BlockPos INTERFACE_POS = new BlockPos(2, 2, 2);
    private static final AEItemKey STONE = AEItemKey.of(Items.STONE);
    private static final AEItemKey DIRT = AEItemKey.of(Items.DIRT);
    private static final IActionSource SOURCE = IActionSource.empty();

    private OverloadedInterfaceSubnetGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void storageBusTracksNetworkAndConfiguredInventory(GameTestHelper helper) {
        helper.setBlock(INTERFACE_POS, ModBlocks.OVERLOADED_INTERFACE.get());
        helper.setBlock(INTERFACE_POS.east(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(INTERFACE_POS.west(), AEBlocks.DRIVE.block());
        var owner = (OverloadedInterfaceBlockEntity) helper.getBlockEntity(INTERFACE_POS);
        owner.setImportMode(OverloadedInterfaceBlockEntity.ImportMode.OFF);
        owner.setExportMode(OverloadedInterfaceBlockEntity.ExportMode.OFF);
        var drive = (DriveBlockEntity) helper.getBlockEntity(INTERFACE_POS.west());
        drive.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_1K.stack());

        var busPos = INTERFACE_POS.north();
        var level = helper.getLevel();
        PartHelper.setPart(level, helper.absolutePos(busPos), null, null,
                AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        var bus = PartHelper.setPart(level, helper.absolutePos(busPos), Direction.SOUTH, null,
                AEParts.STORAGE_BUS.asItem());
        helper.assertTrue(bus != null, "Could not place storage bus");
        helper.setBlock(busPos.north(), AEBlocks.CREATIVE_ENERGY_CELL.block());

        helper.runAfterDelay(50, () -> {
            var sourceGrid = owner.getMainNode().getGrid();
            var subnet = bus.getMainNode().getGrid();
            helper.assertTrue(owner.getMainNode().isActive() && bus.getMainNode().isActive(), "Grids are not active");
            helper.assertTrue(sourceGrid != null && subnet != null && sourceGrid != subnet, "Grids must be separate");
            var sourceStorage = sourceGrid.getStorageService().getInventory();
            var subnetStorage = subnet.getStorageService().getInventory();
            helper.assertTrue(sourceStorage.insert(STONE, 100, Actionable.MODULATE, SOURCE) == 100,
                    "Source cell rejected stone");
            helper.assertTrue(sourceStorage.insert(DIRT, 25, Actionable.MODULATE, SOURCE) == 25,
                    "Source cell rejected dirt");
            assertCapability(helper, owner, sourceStorage);
            helper.assertTrue(bus.getInternalHandler() == sourceStorage, "Bus did not select network storage");
            helper.assertTrue(subnetStorage.extract(STONE, 13, Actionable.MODULATE, SOURCE) == 13,
                    "Subnet could not extract from source grid");
            helper.assertTrue(amount(sourceStorage, STONE) == 87, "Subnet extraction did not debit the source grid");
            owner.getInterfaceLogic().getConfig().setStack(0, new GenericStack(STONE, 32));
        });

        helper.runAfterDelay(100, () -> {
            var sourceStorage = owner.getMainNode().getGrid().getStorageService().getInventory();
            var subnetStorage = bus.getMainNode().getGrid().getStorageService().getInventory();
            var configuredStorage = owner.getInterfaceLogic().getInventory();
            helper.assertTrue(configuredStorage != sourceStorage, "Configuration did not select the interface inventory");
            assertCapability(helper, owner, configuredStorage);
            helper.assertTrue(bus.getInternalHandler() == configuredStorage, "Bus did not switch to interface inventory");
            helper.assertTrue(amount(subnetStorage, STONE) == 32 && amount(subnetStorage, DIRT) == 0,
                    "Subnet did not respect configured inventory contents");
            helper.assertTrue(subnetStorage.extract(STONE, 7, Actionable.MODULATE, SOURCE) == 7,
                    "Subnet could not extract configured stone");
            helper.assertTrue(amount(sourceStorage, STONE) == 80, "Configured extraction did not debit the source grid");
            owner.getInterfaceLogic().getConfig().setStack(0, null);
        });

        helper.runAfterDelay(150, () -> {
            var sourceStorage = owner.getMainNode().getGrid().getStorageService().getInventory();
            var subnetStorage = bus.getMainNode().getGrid().getStorageService().getInventory();
            assertCapability(helper, owner, sourceStorage);
            helper.assertTrue(bus.getInternalHandler() == sourceStorage, "Bus did not restore network storage");
            helper.assertTrue(amount(subnetStorage, STONE) == 80 && amount(subnetStorage, DIRT) == 25,
                    "Subnet did not restore the full source inventory");
            helper.succeed();
        });
    }

    private static void assertCapability(GameTestHelper helper, OverloadedInterfaceBlockEntity owner,
                                         MEStorage expected) {
        helper.assertTrue(owner.getCapability(Capabilities.STORAGE, null).orElse(null) == expected,
                "Unsided storage capability returned the wrong inventory");
        for (var side : Direction.values()) {
            helper.assertTrue(owner.getCapability(Capabilities.STORAGE, side).orElse(null) == expected,
                    "Storage capability returned the wrong inventory on " + side);
        }
    }

    private static long amount(MEStorage storage, AEItemKey key) {
        return storage.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, SOURCE);
    }
}
