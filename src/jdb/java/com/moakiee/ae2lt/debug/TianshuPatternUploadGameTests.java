package com.moakiee.ae2lt.debug;

import java.util.UUID;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.ids.AEComponents;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.util.AEColor;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.menu.SlotSemantics;
import appeng.menu.locator.MenuLocators;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuPatternUploadRouting;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWirelessPatternEncodingTermMenuHost;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.moakiee.ae2lt.menu.TianshuWirelessPatternEncodingTermMenu;
import com.moakiee.ae2lt.registry.ModItems;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** Real terminal encoding and upload against a powered ME network and full provider. */
@net.neoforged.neoforge.gametest.GameTestHolder("ae2lt_upload_retention")
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
public final class TianshuPatternUploadGameTests {
    @net.minecraft.gametest.framework.GameTest(templateNamespace = "ae2lt", template = "workstation_test", timeoutTicks = 150)
    public static void wiredFullTargetRetainsEncodedPattern(GameTestHelper helper) {
        fullTarget(helper, false);
    }

    @net.minecraft.gametest.framework.GameTest(templateNamespace = "ae2lt", template = "workstation_test", timeoutTicks = 150)
    public static void wirelessFullTargetRetainsEncodedPattern(GameTestHelper helper) {
        fullTarget(helper, true);
    }

    private static void fullTarget(GameTestHelper helper, boolean wireless) {
        var level = helper.getLevel();
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "UploadRetention"));
        var cable = helper.absolutePos(new BlockPos(3, 2, 3));
        PartHelper.setPart(level, cable, null, player, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        var terminal = PartHelper.setPart(level, cable, Direction.SOUTH, player,
                ModItems.TIANSHU_PATTERN_ENCODING_TERMINAL.get());
        level.setBlockAndUpdate(cable.below(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        level.setBlockAndUpdate(cable.west(), AEBlocks.DRIVE.block().defaultBlockState());
        ((DriveBlockEntity) level.getBlockEntity(cable.west())).getInternalInventory()
                .setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
        level.setBlockAndUpdate(cable.east(), AEBlocks.PATTERN_PROVIDER.block().defaultBlockState());
        level.setBlockAndUpdate(cable.east(2), AEBlocks.MOLECULAR_ASSEMBLER.block().defaultBlockState());
        level.setBlockAndUpdate(cable.north(), AEBlocks.WIRELESS_ACCESS_POINT.block().defaultBlockState());
        player.setPos(cable.getX() + .5, cable.getY() + 1, cable.getZ() + .5);

        helper.runAfterDelay(40, () -> {
            var grid = terminal.getActionableNode().getGrid();
            check(helper, grid.getEnergyService().isNetworkPowered(), "network is powered");
            var storage = grid.getStorageService().getInventory();
            var source = IActionSource.ofPlayer(player);
            var blank = AEItemKey.of(AEItems.BLANK_PATTERN);
            check(helper, storage.insert(blank, 8, Actionable.MODULATE, source) == 8, "store eight blank patterns");
            var provider = (PatternProviderBlockEntity) level.getBlockEntity(cable.east());
            var target = provider.getLogic().getPatternInv();
            check(helper, TianshuPatternUploadRouting.isCraftingUploadGroup(provider.getLogic().getTerminalGroup()),
                    "provider adjacent to assembler is a crafting upload target");

            var item = ModItems.TIANSHU_WIRELESS_PATTERN_ENCODING_TERMINAL.get();
            var stack = new ItemStack(item);
            stack.set(AEComponents.WIRELESS_LINK_TARGET, GlobalPos.of(level.dimension(), cable.north()));
            stack.set(AEComponents.STORED_ENERGY, 1000000.0);
            player.getInventory().setItem(0, stack);
            var locator = MenuLocators.forInventorySlot(0);
            var wirelessHost = wireless ? new TianshuWirelessPatternEncodingTermMenuHost(
                    item, player, locator, (p, m) -> {}) : null;
            TianshuPatternEncodingTermMenu menu = wireless
                    ? new TianshuWirelessPatternEncodingTermMenu(17, player.getInventory(), wirelessHost)
                    : new TianshuPatternEncodingTermMenu(17, player.getInventory(), terminal);
            player.containerMenu = menu;
            menu.getSlots(SlotSemantics.CRAFTING_GRID).getFirst().set(new ItemStack(Items.OAK_LOG));
            menu.broadcastChanges();
            menu.encode();
            var encodedInventory = (wireless ? wirelessHost.getLogic() : terminal.getLogic()).getEncodedPatternInv();
            var encoded = encodedInventory.getStackInSlot(0).copy();
            check(helper, PatternDetailsHelper.decodePattern(encoded, level) != null, "native crafting encoding succeeds");
            check(helper, storage.extract(blank, 99, Actionable.SIMULATE, source) == 7, "encoding charges one blank");
            for (int i = 0; i < target.size(); i++) target.setItemDirect(i, encoded.copy());

            for (int attempt = 0; attempt < 3; attempt++) {
                menu.uploadEncodedPattern();
                check(helper, menu.uploadState == 3, "full target reports upload failure");
                check(helper, ItemStack.matches(encoded, encodedInventory.getStackInSlot(0)),
                        "failed upload retains the exact paid encoded pattern in its slot");
                check(helper, storage.extract(blank, 99, Actionable.SIMULATE, source) == 7,
                        "failed upload neither refunds nor consumes another blank");
            }

            menu.removed(player);
            player.containerMenu = player.inventoryMenu;
            var reopenedHost = wireless ? new TianshuWirelessPatternEncodingTermMenuHost(
                    item, player, locator, (p, m) -> {}) : null;
            TianshuPatternEncodingTermMenu reopened = wireless
                    ? new TianshuWirelessPatternEncodingTermMenu(18, player.getInventory(), reopenedHost)
                    : new TianshuPatternEncodingTermMenu(18, player.getInventory(), terminal);
            player.containerMenu = reopened;
            var retained = (wireless ? reopenedHost.getLogic() : terminal.getLogic()).getEncodedPatternInv();
            check(helper, ItemStack.matches(encoded, retained.getStackInSlot(0)), "close and reopen retain the encoded item");
            target.setItemDirect(0, ItemStack.EMPTY);
            helper.runAfterDelay(5, () -> {
                reopened.broadcastChanges();
                check(helper, target.getStackInSlot(0).isEmpty() && ItemStack.matches(encoded, retained.getStackInSlot(0)),
                        "freeing target space waits for a user action");
                reopened.uploadEncodedPattern();
                check(helper, reopened.uploadState == 1 && retained.getStackInSlot(0).isEmpty(), "manual retry succeeds");
                check(helper, ItemStack.matches(encoded, target.getStackInSlot(0)), "target receives the retained pattern intact");
                check(helper, storage.extract(blank, 99, Actionable.SIMULATE, source) == 7, "retry consumes no new blank");
                reopened.uploadEncodedPattern();
                check(helper, storage.extract(blank, 99, Actionable.SIMULATE, source) == 7, "empty retry cannot refund a blank");
                try {
                    var encode = TianshuPatternEncodingTermMenu.class
                            .getDeclaredMethod("encodeServerWithOptions", Boolean.class);
                    encode.setAccessible(true);
                    encode.invoke(reopened, true);
                } catch (ReflectiveOperationException error) {
                    throw new AssertionError(error);
                }
                check(helper, retained.getStackInSlot(0).isEmpty()
                                && storage.extract(blank, 99, Actionable.SIMULATE, source) == 7,
                        "duplicate interception still refunds only a newly rejected encoding");
                reopened.removed(player);
                player.containerMenu = player.inventoryMenu;
                System.out.println("TIANSHU_UPLOAD_RETENTION_PASS " + (wireless ? "wireless" : "wired"));
                helper.succeed();
            });
        });
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, message);
    }
}
