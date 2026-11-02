package com.moakiee.ae2lt.gametest;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageCells;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.integration.ae2wtlib.TianshuWirelessIngredientSource;
import com.moakiee.ae2lt.logic.tianshu.terminal.WirelessJeiSupply;
import com.moakiee.ae2lt.network.jei.WirelessJeiSupplyPacket;
import com.moakiee.ae2lt.registry.ModItems;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AE2LightningTech.MODID)
@PrefixGameTestTemplate(false)
public final class WirelessJeiSupplyGameTests {
    private WirelessJeiSupplyGameTests() {}

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void authorizedSupplyPreservesItemsAndRejectsReplay(GameTestHelper helper) {
        if (!ModList.get().isLoaded("ae2wtlib")) {
            helper.succeed();
            return;
        }
        var level = helper.getLevel();
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "JeiSupplyPort"));
        player.getInventory().clearContent();
        var base = helper.absolutePos(new BlockPos(3, 3, 3));
        var accessPoint = base.north();
        var table = base.south();
        level.setBlockAndUpdate(base, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        level.setBlockAndUpdate(base.east(), AEBlocks.DRIVE.block().defaultBlockState());
        level.setBlockAndUpdate(accessPoint, AEBlocks.WIRELESS_ACCESS_POINT.block().defaultBlockState());
        level.setBlockAndUpdate(table, net.minecraft.world.level.block.Blocks.CRAFTING_TABLE.defaultBlockState());
        var cell = AEItems.ITEM_CELL_4K.stack();
        var cellStorage = StorageCells.getCellInventory(cell, null);
        cellStorage.insert(AEItemKey.of(Items.OAK_PLANKS), 264, Actionable.MODULATE, IActionSource.ofPlayer(player));
        cellStorage.persist();
        ((DriveBlockEntity) level.getBlockEntity(base.east())).getInternalInventory().setItemDirect(0, cell);
        var terminal = new ItemStack(ModItems.TIANSHU_WIRELESS_PATTERN_ENCODING_TERMINAL.get());
        terminal.getOrCreateTag().putDouble("internalCurrentPower", 1000000.0);
        terminal.getOrCreateTag().put("accessPoint", GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE,
                GlobalPos.of(level.dimension(), accessPoint)).result().orElseThrow());
        player.getInventory().setItem(0, terminal);
        player.setPos(table.getX() + 0.5, table.getY() + 1, table.getZ() + 0.5);
        var menu = new CraftingMenu(17, player.getInventory(), ContainerLevelAccess.create(level, table));
        player.containerMenu = menu;
        helper.runAtTickTime(50, () -> {
            var locators = TianshuWirelessIngredientSource.locate(player);
            helper.assertTrue(!locators.isEmpty() && TianshuWirelessIngredientSource.open(player, locators.get(0)) != null,
                    "Wireless Tianshu must resolve a powered AE2 network");
            var storage = ((DriveBlockEntity) level.getBlockEntity(base.east()))
                    .getMainNode().getGrid().getStorageService().getInventory();
            var key = AEItemKey.of(Items.OAK_PLANKS);
            var materials = new ItemStack(Items.OAK_PLANKS, 4);
            var offered = new WirelessJeiSupplyPacket(menu.containerId, 1, false, List.of(materials));
            var taken = new WirelessJeiSupplyPacket(menu.containerId, 1, true, List.of(materials));
            helper.assertTrue(WirelessJeiSupply.handle(player, offered).status() == 1,
                    "Offer must list only accessible real ME items");
            helper.assertTrue(storage.extract(key, 264, Actionable.SIMULATE, IActionSource.ofPlayer(player)) == 264,
                    "Offer must not consume storage");
            helper.assertTrue(WirelessJeiSupply.handle(player, taken).status() == 2,
                    "Authorized take must succeed");
            helper.assertTrue(player.getInventory().countItem(Items.OAK_PLANKS) == 4
                    && storage.extract(key, 260, Actionable.SIMULATE, IActionSource.ofPlayer(player)) == 260,
                    "ME and player inventory must conserve transferred items");
            helper.assertTrue(WirelessJeiSupply.handle(player, taken).status() == 0
                    && player.getInventory().countItem(Items.OAK_PLANKS) == 4,
                    "An already consumed ticket cannot be replayed");
            var large = new ItemStack(Items.OAK_PLANKS, 200);
            var largeOffer = new WirelessJeiSupplyPacket(menu.containerId, 3, false, List.of(large));
            var largeTake = new WirelessJeiSupplyPacket(menu.containerId, 3, true, List.of(large));
            helper.assertTrue(WirelessJeiSupply.handle(player, largeOffer).items().get(0).getCount() == 200,
                    "Offer must preserve counts above the vanilla packet limit");
            helper.assertTrue(WirelessJeiSupply.handle(player, largeTake).status() == 2
                    && player.getInventory().countItem(Items.OAK_PLANKS) == 204
                    && storage.extract(key, 64, Actionable.SIMULATE, IActionSource.ofPlayer(player)) == 60,
                    "Large wireless JEI take must conserve items between ME and inventory");
            var universal = new ItemStack(de.mari_023.ae2wtlib.AE2wtlib.UNIVERSAL_TERMINAL);
            universal.getOrCreateTag().putBoolean(
                    com.moakiee.ae2lt.integration.ae2wtlib.Ae2wtlibIntegration.TIANSHU_TERMINAL_NAME, true);
            universal.getOrCreateTag().putDouble("internalCurrentPower", 1000000.0);
            universal.getOrCreateTag().put("accessPoint", terminal.getOrCreateTag().get("accessPoint").copy());
            player.getInventory().setItem(0, universal);
            helper.assertTrue(WirelessJeiSupply.handle(player, new WirelessJeiSupplyPacket(
                    menu.containerId, 2, false, List.of(materials))).status() == 1,
                    "Universal terminal must expose its Tianshu module");
            helper.assertTrue(WirelessJeiSupply.handle(player, new WirelessJeiSupplyPacket(
                    menu.containerId, 2, true, List.of(materials))).status() == 2
                    && player.getInventory().countItem(Items.OAK_PLANKS) == 208,
                    "Universal terminal must supply the same real ME inventory");
            player.containerMenu = player.inventoryMenu;
            helper.succeed();
        });
    }
}
