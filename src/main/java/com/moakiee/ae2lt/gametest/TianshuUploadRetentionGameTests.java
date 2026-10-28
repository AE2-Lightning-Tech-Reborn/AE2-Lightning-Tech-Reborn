package com.moakiee.ae2lt.gametest;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
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
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.integration.ae2wtlib.TianshuWTMenuHost;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.moakiee.ae2lt.menu.TianshuWirelessPatternEncodingTermMenu;
import com.moakiee.ae2lt.part.TianshuPatternEncodingTerminalPart;
import com.moakiee.ae2lt.registry.ModItems;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AE2LightningTech.MODID)
@PrefixGameTestTemplate(false)
public final class TianshuUploadRetentionGameTests {
    private TianshuUploadRetentionGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void failedUploadRetainsPatternAndRetriesWhenProviderHasSpace(GameTestHelper helper) {
        BlockPos cable = new BlockPos(3, 3, 3);
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "UploadRetention"));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(cable), null, player,
                AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        TianshuPatternEncodingTerminalPart terminal = (TianshuPatternEncodingTerminalPart) PartHelper.setPart(
                helper.getLevel(), helper.absolutePos(cable), Direction.SOUTH, player,
                ModItems.TIANSHU_PATTERN_ENCODING_TERMINAL.get());
        helper.setBlock(cable.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(cable.west(), AEBlocks.DRIVE.block());
        var drive = (DriveBlockEntity) helper.getBlockEntity(cable.west());
        drive.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
        player.setPos(helper.absolutePos(cable).getX() + 0.5, helper.absolutePos(cable).getY() + 1,
                helper.absolutePos(cable).getZ() + 0.5);

        helper.runAtTickTime(40, () -> {
            var grid = terminal.getActionableNode().getGrid();
            helper.assertTrue(grid != null, "Terminal must join the powered grid");
            helper.assertTrue(drive.getMainNode().getGrid() == grid, "Drive must share the terminal grid");
            helper.assertTrue(grid.getEnergyService().isNetworkPowered(), "Grid must have power");
            var storage = grid.getStorageService().getInventory();
            var source = IActionSource.empty();
            var blank = AEItemKey.of(AEItems.BLANK_PATTERN.asItem());
            helper.assertTrue(storage.insert(blank, 2, Actionable.MODULATE, source) == 2,
                    "Blank patterns must be stored in the grid");
            var menu = new TianshuPatternEncodingTermMenu(17, player.getInventory(), terminal);
            player.containerMenu = menu;
            menu.getSlots(SlotSemantics.CRAFTING_GRID).get(0).set(new ItemStack(Items.OAK_LOG));
            menu.broadcastChanges();
            menu.encode();
            var encodedInventory = terminal.getLogic().getEncodedPatternInv();
            var encoded = encodedInventory.getStackInSlot(0).copy();
            helper.assertTrue(PatternDetailsHelper.decodePattern(encoded, helper.getLevel()) != null,
                    "Network blank must become a crafting pattern");
            helper.assertTrue(storage.extract(blank, 99, Actionable.SIMULATE, source) == 1,
                    "Encoding must charge exactly one blank pattern");

            for (int attempt = 0; attempt < 2; attempt++) {
                menu.uploadEncodedPattern();
                helper.assertTrue(menu.uploadState == 3, "Without a target, upload must fail");
                helper.assertTrue(ItemStack.matches(encoded, encodedInventory.getStackInSlot(0)),
                        "Failed upload must leave the paid encoded pattern intact");
                helper.assertTrue(storage.extract(blank, 99, Actionable.SIMULATE, source) == 1,
                        "Retrying must neither refund nor charge another blank pattern");
            }
            menu.removed(player);
            player.containerMenu = player.inventoryMenu;
            var reopened = new TianshuPatternEncodingTermMenu(18, player.getInventory(), terminal);
            player.containerMenu = reopened;
            reopened.broadcastChanges();
            helper.assertTrue(ItemStack.matches(encoded, encodedInventory.getStackInSlot(0)),
                    "Closing and reopening must retain the paid encoded pattern");
            helper.assertTrue(storage.extract(blank, 99, Actionable.SIMULATE, source) == 1,
                    "Reopening must not change the blank pattern balance");
            helper.setBlock(cable.east(), AEBlocks.PATTERN_PROVIDER.block());
            helper.setBlock(cable.east(2), AEBlocks.MOLECULAR_ASSEMBLER.block());
            helper.runAtTickTime(60, () -> {
                var provider = (PatternProviderBlockEntity) helper.getBlockEntity(cable.east());
                helper.assertTrue(provider.getMainNode().getGrid() == grid,
                        "Pattern provider must join the terminal grid");
                var patterns = provider.getLogic().getPatternInv();
                for (int slot = 0; slot < patterns.size(); slot++) {
                    patterns.setItemDirect(slot, encoded.copy());
                }
                for (int attempt = 0; attempt < 2; attempt++) {
                    reopened.uploadEncodedPattern();
                    helper.assertTrue(reopened.uploadState == 3, "Full provider must reject the upload");
                    helper.assertTrue(ItemStack.matches(encoded, encodedInventory.getStackInSlot(0)),
                            "Full provider must not consume the encoded pattern");
                    helper.assertTrue(storage.extract(blank, 99, Actionable.SIMULATE, source) == 1,
                            "Full provider must not change the paid blank balance");
                }

                patterns.setItemDirect(0, ItemStack.EMPTY);
                reopened.uploadEncodedPattern();
                helper.assertTrue(reopened.uploadState == 1, "Retry into a free provider slot must succeed");
                helper.assertTrue(encodedInventory.getStackInSlot(0).isEmpty(),
                        "Successful upload must consume the source pattern");
                helper.assertTrue(ItemStack.matches(encoded, patterns.getStackInSlot(0)),
                        "Successful upload must place exactly one encoded pattern in the provider");
                helper.assertTrue(storage.extract(blank, 99, Actionable.SIMULATE, source) == 1,
                        "Successful retry must not charge or refund another blank");
                reopened.removed(player);
                player.containerMenu = player.inventoryMenu;
                helper.succeed();
            });
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void wirelessUploadRetainsPaidPatternAfterReopen(GameTestHelper helper) {
        if (!ModList.get().isLoaded("ae2wtlib")) {
            helper.succeed();
            return;
        }
        BlockPos cable = new BlockPos(3, 3, 3);
        var player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "WirelessUploadRetention"));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(cable), null, player,
                AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(cable), Direction.SOUTH, player,
                ModItems.TIANSHU_PATTERN_ENCODING_TERMINAL.get());
        helper.setBlock(cable.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(cable.west(), AEBlocks.DRIVE.block());
        helper.setBlock(cable.north(), AEBlocks.WIRELESS_ACCESS_POINT.block());
        helper.setBlock(cable.east(), AEBlocks.PATTERN_PROVIDER.block());
        helper.setBlock(cable.east(2), AEBlocks.MOLECULAR_ASSEMBLER.block());
        var drive = (DriveBlockEntity) helper.getBlockEntity(cable.west());
        drive.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
        var stack = new ItemStack(ModItems.TIANSHU_WIRELESS_PATTERN_ENCODING_TERMINAL.get());
        stack.getOrCreateTag().putDouble("internalCurrentPower", 1_000_000.0);
        stack.getOrCreateTag().put("accessPoint", GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE,
                GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(cable.north())))
                .result().orElseThrow());
        player.getInventory().setItem(0, stack);
        BlockPos accessPoint = helper.absolutePos(cable.north());
        player.setPos(accessPoint.getX() + 0.5, accessPoint.getY() + 1, accessPoint.getZ() + 0.5);

        helper.runAtTickTime(40, () -> {
            var grid = drive.getMainNode().getGrid();
            helper.assertTrue(grid != null && grid.getEnergyService().isNetworkPowered(),
                    "Wireless access point must share a powered grid with the drive");
            var storage = grid.getStorageService().getInventory();
            var source = IActionSource.empty();
            var blank = AEItemKey.of(AEItems.BLANK_PATTERN.asItem());
            helper.assertTrue(storage.insert(blank, 2, Actionable.MODULATE, source) == 2,
                    "Store two network blank patterns");
            var host = new TianshuWTMenuHost(player, 0, stack, (owner, menu) -> { });
            helper.assertTrue(host.rangeCheck() && host.getActionableNode() != null,
                    "Wireless terminal must resolve the bound access point");
            var menu = new TianshuWirelessPatternEncodingTermMenu(19, player.getInventory(), host);
            player.containerMenu = menu;
            menu.getSlots(SlotSemantics.CRAFTING_GRID).get(0).set(new ItemStack(Items.OAK_LOG));
            menu.broadcastChanges();
            menu.encode();
            var encodedInventory = host.getLogic().getEncodedPatternInv();
            var encoded = encodedInventory.getStackInSlot(0).copy();
            helper.assertTrue(PatternDetailsHelper.decodePattern(encoded, helper.getLevel()) != null,
                    "Wireless encoding must create a valid pattern");
            helper.assertTrue(storage.extract(blank, 99, Actionable.SIMULATE, source) == 1,
                    "Wireless encoding must charge exactly one blank");
            var provider = (PatternProviderBlockEntity) helper.getBlockEntity(cable.east());
            var target = provider.getLogic().getPatternInv();
            for (int slot = 0; slot < target.size(); slot++) target.setItemDirect(slot, encoded.copy());
            for (int attempt = 0; attempt < 2; attempt++) {
                menu.uploadEncodedPattern();
                helper.assertTrue(menu.uploadState == 3, "A full provider must reject wireless uploads");
                helper.assertTrue(ItemStack.matches(encoded, encodedInventory.getStackInSlot(0)),
                        "Wireless upload failure must retain the encoded pattern");
                helper.assertTrue(storage.extract(blank, 99, Actionable.SIMULATE, source) == 1,
                        "Failed wireless upload must not refund or consume another blank");
            }
            menu.removed(player);
            player.containerMenu = player.inventoryMenu;
            var reopenedHost = new TianshuWTMenuHost(player, 0, stack, (owner, submenu) -> { });
            var reopened = new TianshuWirelessPatternEncodingTermMenu(20, player.getInventory(), reopenedHost);
            player.containerMenu = reopened;
            reopened.broadcastChanges();
            var retained = reopenedHost.getLogic().getEncodedPatternInv();
            helper.assertTrue(ItemStack.matches(encoded, retained.getStackInSlot(0)),
                    "Closing and reopening must preserve the paid wireless pattern");
            target.setItemDirect(0, ItemStack.EMPTY);
            helper.runAtTickTime(45, () -> {
                reopened.broadcastChanges();
                helper.assertTrue(target.getStackInSlot(0).isEmpty()
                                && ItemStack.matches(encoded, retained.getStackInSlot(0)),
                        "Freeing a provider slot must not trigger an automatic upload");
                reopened.uploadEncodedPattern();
                helper.assertTrue(reopened.uploadState == 1 && retained.getStackInSlot(0).isEmpty(),
                        "Manual retry must upload the retained wireless pattern");
                helper.assertTrue(ItemStack.matches(encoded, target.getStackInSlot(0)),
                        "Provider must receive the exact retained pattern");
                helper.assertTrue(storage.extract(blank, 99, Actionable.SIMULATE, source) == 1,
                        "Retry must not consume or refund another blank");
                reopened.removed(player);
                player.containerMenu = player.inventoryMenu;
                helper.succeed();
            });
        });
    }
}
