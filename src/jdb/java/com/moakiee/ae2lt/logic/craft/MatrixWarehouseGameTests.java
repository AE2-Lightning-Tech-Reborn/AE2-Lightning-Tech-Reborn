package com.moakiee.ae2lt.logic.craft;

import appeng.api.networking.crafting.ICraftingProvider;
import com.moakiee.ae2lt.blockentity.MatrixPatternStorageBlockEntity;
import com.moakiee.ae2lt.logic.craft.migration.PatternMigrationGameTests;
import com.moakiee.ae2lt.menu.MatrixPortMenu;
import com.moakiee.ae2lt.network.MatrixControllerActionPacket;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2lt_matrix_warehouse")
@PrefixGameTestTemplate(false)
public final class MatrixWarehouseGameTests {
    @GameTest(templateNamespace = "ae2lt_matrix_warehouse", template = "migration_test", timeoutTicks = 200)
    public static void warehousesAdvertiseTheirOwnPatternsAndMenuSwitchesPhysicalInventories(GameTestHelper h) {
        var fixture = PatternMigrationGameTests.build(h.getLevel(), h.absolutePos(new BlockPos(2, 1, 2)));
        h.runAfterDelay(50, () -> {
            fixture.controller().scanAndForm(fixture.player());
            h.assertTrue(fixture.controller().isFormed(), "matrix formed");
            var storages = fixture.port().getPatternStorages();
            h.assertTrue(storages.size() == 2, "two physical warehouses");
            storages.get(0).getInventory().setStackInSlot(0, PatternMigrationGameTests.pattern(h, false));
            storages.get(1).getInventory().setStackInSlot(0, PatternMigrationGameTests.pattern(h, true));
        });
        h.runAfterDelay(60, () -> {
            try {
                var storages = fixture.port().getPatternStorages();
                var grid = fixture.port().getGrid();
                h.assertTrue(grid != null && !(fixture.port() instanceof ICraftingProvider),
                        "port connects the grid without registering as a provider");
                for (var warehouse : storages) {
                    h.assertTrue(warehouse.getAvailablePatterns().size() == 1, "one pattern per warehouse");
                    var pattern = warehouse.getAvailablePatterns().get(0);
                    boolean found = false;
                    for (var provider : ((appeng.me.service.CraftingService) grid.getCraftingService()).getProviders(pattern)) {
                        h.assertTrue(provider != fixture.port(), "port must not duplicate warehouse registrations");
                        if (provider == warehouse) found = true;
                    }
                    h.assertTrue(found && warehouse.hasPattern(pattern), "physical warehouse owns its registration");
                }
                var pos = fixture.port().getBlockPos();
                fixture.player().setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                var menu = new MatrixPortMenu(1, fixture.player().getInventory(), fixture.port());
                h.assertTrue(menu.getWarehouseCount() == 2 && menu.slots.size() == 324,
                        "menu synchronizes 288 warehouse slots and 36 player slots");
                h.assertTrue(ItemStack.matches(menu.slots.get(0).getItem(),
                        storages.get(0).getInventory().getStackInSlot(0)), "first warehouse selected");
                h.assertTrue(menu.clickMenuButton(fixture.player(), 1), "server accepts second warehouse");
                h.assertTrue(ItemStack.matches(menu.slots.get(0).getItem(),
                        storages.get(1).getInventory().getStackInSlot(0)), "second physical warehouse selected");
                h.assertTrue(!menu.clickMenuButton(fixture.player(), 2), "out-of-range selection rejected");
                h.succeed();
            } finally {
                PatternMigrationGameTests.unregister(fixture.player());
            }
        });
    }

    @GameTest(templateNamespace = "ae2lt_matrix_warehouse", template = "migration_test", timeoutTicks = 200)
    public static void upgradeToT3PreservesPatternsAndSavesTheLastPhysicalSlot(GameTestHelper h) {
        var fixture = PatternMigrationGameTests.build(h.getLevel(), h.absolutePos(new BlockPos(2, 1, 2)));
        h.runAfterDelay(50, () -> {
            try {
                fixture.controller().scanAndForm(fixture.player());
                var storage = fixture.port().getPatternStorages().get(0);
                var pos = storage.getBlockPos();
                var pattern = PatternMigrationGameTests.pattern(h, false);
                storage.getInventory().setStackInSlot(0, pattern.copy());
                fixture.player().getInventory().clearContent();
                fixture.player().getInventory().setItem(0,
                        new ItemStack(ModItems.MATTER_WARPING_MATRIX_PATTERN_STORAGE_UPGRADE.get()));
                fixture.controller().performAction(MatrixControllerActionPacket.Action.UPGRADE_PATTERN_STORAGE, fixture.player());
                h.assertTrue(h.getLevel().getBlockState(pos).is(ModBlocks.MATTER_WARPING_MATRIX_PATTERN_STORAGE_T2.get()),
                        "one T1 upgrade produces T2");
                fixture.player().getInventory().setItem(0, new ItemStack(ModItems.DENSE_TOPOLOGICAL_LATTICE.get()));
                fixture.controller().performAction(MatrixControllerActionPacket.Action.UPGRADE_PATTERN_STORAGE, fixture.player());
                var upgraded = (MatrixPatternStorageBlockEntity) h.getLevel().getBlockEntity(pos);
                h.assertTrue(upgraded.capacity() == 288 && upgraded.tier() == MatrixPatternStorageTier.T3,
                        "dense topological lattice upgrades T2 to 288-slot T3");
                h.assertTrue(ItemStack.matches(upgraded.getInventory().getStackInSlot(0), pattern)
                        && fixture.player().getInventory().getItem(0).isEmpty(), "pattern retained and material consumed once");
                upgraded.getInventory().setStackInSlot(287, PatternMigrationGameTests.pattern(h, true));
                var copy = new MatrixPatternStorageBlockEntity(pos, upgraded.getBlockState());
                copy.load(upgraded.saveWithoutMetadata());
                h.assertTrue(ItemStack.matches(copy.getInventory().getStackInSlot(287),
                        upgraded.getInventory().getStackInSlot(287)), "last T3 slot survives NBT round trip");
                h.succeed();
            } finally {
                PatternMigrationGameTests.unregister(fixture.player());
            }
        });
    }
}
