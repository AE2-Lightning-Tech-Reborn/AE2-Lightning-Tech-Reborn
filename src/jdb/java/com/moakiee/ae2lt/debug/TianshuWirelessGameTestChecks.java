package com.moakiee.ae2lt.debug;

import appeng.api.config.IncludeExclude;
import appeng.api.stacks.AEItemKey;
import appeng.menu.locator.MenuLocators;
import com.moakiee.ae2lt.integration.ae2wtlib.*;
import com.moakiee.ae2lt.logic.tianshu.terminal.*;
import com.moakiee.ae2lt.menu.Ae2ltSlotSemantics;
import com.moakiee.ae2lt.mixin.ae2wtlib.CraftingTerminalHandlerAccessor;
import com.moakiee.ae2lt.registry.ModItems;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Unit;
import de.mari_023.ae2wtlib.AE2wtlib;
import de.mari_023.ae2wtlib.AE2wtlibSlotSemantics;
import de.mari_023.ae2wtlib.wut.WTDefinition;
import de.mari_023.ae2wtlib.wut.WUTHandler;
import de.mari_023.ae2wtlib.wct.CraftingTerminalHandler;
import de.mari_023.ae2wtlib.wct.magnet_card.MagnetHandler;
import de.mari_023.ae2wtlib.wct.magnet_card.MagnetMode;
import de.mari_023.ae2wtlib.wut.recipe.Common;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayerFactory;

/** Optional full-WT checks are isolated from API-only class loading. */
final class TianshuWirelessGameTestChecks {
    private static void require(boolean result, String message) { if (!result) throw new AssertionError(message); }
    private static void clientAction(TianshuEnhancedWirelessCraftingMenu menu, String action) {
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buffer.writeVarInt(menu.containerId);
        buffer.writeUtf(action);
        menu.receiveClientAction(new appeng.core.sync.packets.GuiDataSyncPacket(buffer));
        buffer.release();
    }
    private static void setCrafting(ItemStack stack, ItemStack ingredient) {
        var inventory = new appeng.util.inv.AppEngInternalInventory(9);
        inventory.setItemDirect(0, ingredient.copy());
        inventory.writeToNBT(stack.getOrCreateTag(), "craftingGrid");
    }
    private static ItemStack getCrafting(ItemStack stack) {
        var inventory = new appeng.util.inv.AppEngInternalInventory(9);
        inventory.readFromNBT(stack.getOrCreateTag(), "craftingGrid");
        return inventory.getStackInSlot(0);
    }
    static void run(GameTestHelper helper) {
        var definition = Ae2wtlibIntegration.TIANSHU_CRAFTING_NAME;
        var item = (com.moakiee.ae2lt.item.TianshuWirelessCraftingTerminalItem) ModItems.TIANSHU_WIRELESS_CRAFTING_TERMINAL.get();
        var manualInputs = new net.minecraft.nbt.ListTag();
        manualInputs.add(new ItemStack(Items.NETHERITE_INGOT, 2).save(new net.minecraft.nbt.CompoundTag()));
        var manualData = new net.minecraft.nbt.CompoundTag();
        manualData.put("inputs", manualInputs);
        var manualComponent = com.moakiee.ae2lt.registry.ModDataComponents.TIANSHU_WORKSTATIONS;
        var occupiedSource = new ItemStack(item);
        manualComponent.set(occupiedSource, manualData);
        var emptyTarget = new ItemStack(AE2wtlib.UNIVERSAL_TERMINAL);
        emptyTarget.getOrCreateTag().putBoolean("crafting", true);
        var manualMerged = Common.mergeTerminal(emptyTarget, occupiedSource, definition);
        require(!manualMerged.isEmpty() && manualComponent.get(manualMerged).equals(manualData),
                "native WUT merge retains manual workstation contents");
        var occupiedTarget = emptyTarget.copy();
        manualComponent.set(occupiedTarget, manualData);
        require(Common.mergeTerminal(occupiedTarget, occupiedSource, definition).isEmpty(),
                "merging two equal occupied workstations must reject, not discard one copy of the inputs");
        require(manualComponent.get(occupiedSource).equals(manualData)
                        && manualComponent.get(occupiedTarget).equals(manualData),
                "rejected WUT merge leaves both workstation inventories unchanged");
        var source = new ItemStack(item);
        var target = new ItemStack(AE2wtlib.UNIVERSAL_TERMINAL);
        target.getOrCreateTag().putBoolean("crafting", true);
        setCrafting(target, new ItemStack(Items.DIAMOND, 3));
        setCrafting(source, ItemStack.EMPTY);
        source.getOrCreateTag().putDouble("internalCurrentPower", 20.0);
        target.getOrCreateTag().putDouble("internalCurrentPower", 10.0);
        var result = Common.mergeTerminal(target, source, definition);
        require(!result.isEmpty() && getCrafting(result).getCount() == 3
                && result.getOrCreateTag().getDouble("internalCurrentPower") == 30, "merge must preserve real grid and sum energy");
        require(!target.getOrCreateTag().getBoolean(definition) && target.getOrCreateTag().getDouble("internalCurrentPower") == 10,
                "merge planning must not mutate inputs");
        setCrafting(source, getCrafting(target));
        require(Common.mergeTerminal(target, source, definition).isEmpty(), "identical occupied crafting grids must not be deduplicated");
        source.getOrCreateTag().remove("craftingGrid");
        source.getOrCreateTag().putBoolean("restock", true);
        target.getOrCreateTag().putBoolean("restock", false);
        require(Common.mergeTerminal(target, source, definition).isEmpty(), "conflicting settings must reject loss");
        target.getOrCreateTag().remove("restock");
        source.getOrCreateTag().remove("restock");
        require(item.getUpgrades(source).addItems(new ItemStack(AE2wtlib.MAGNET_CARD)).isEmpty(), "Ti magnet upgrade registration");
        require(AE2wtlib.UNIVERSAL_TERMINAL.getUpgrades(target).addItems(new ItemStack(AE2wtlib.MAGNET_CARD)).isEmpty(), "WUT magnet upgrade registration");
        require(Common.mergeTerminal(target, source, definition).isEmpty(), "duplicate limited cards must reject loss");
        System.out.println("TIANSHU_WORKSTATION_PASS WUT merge keeps grids/energy, rejects duplicate inventory/cards/settings, leaves inputs unchanged");

        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.fromString("20000000-0000-0000-0000-000000000006"), "TianshuWireless"));
        player.getInventory().clearContent();
        var pos = helper.absolutePos(net.minecraft.core.BlockPos.ZERO);
        player.setPos(pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5);
        var ti = new ItemStack(item);
        player.getInventory().setItem(0, ti);
        var handler = CraftingTerminalHandler.getCraftingTerminalHandler(player);
        require(handler.getCraftingTerminal() == ti, "native locator must find standalone Ti crafting terminal");
        require(!WUTHandler.hasTerminal(ti, "crafting"), "native crafting definition must not be spoofed outside locator scope");
        var tiWut = new ItemStack(AE2wtlib.UNIVERSAL_TERMINAL);
        tiWut.getOrCreateTag().putBoolean(definition, true);
        player.getInventory().setItem(0, tiWut);
        ((CraftingTerminalHandlerAccessor) handler).ae2lt$invalidateCache();
        require(handler.getCraftingTerminal() == tiWut && !WUTHandler.hasTerminal(tiWut, "crafting"), "Ti-only WUT locator");
        tiWut.getOrCreateTag().putBoolean("crafting", true);
        require(handler.getCraftingTerminal() == tiWut, "dual-definition WUT remains one selected terminal");

        // The global locator points at slot 0, while the user explicitly edits the Ti item in slot 1.
        player.getInventory().setItem(1, ti);
        item.getUpgrades(ti).addItems(new ItemStack(AE2wtlib.MAGNET_CARD));
        var host = new TianshuWirelessCraftingTermMenuHost(player, 1, ti, (p, m) -> {});
        var menu = new TianshuEnhancedWirelessCraftingMenu(9, player.getInventory(), host);
        player.containerMenu = menu;
        try {
            var helmet = menu.getSlots(AE2wtlibSlotSemantics.HELMET).get(0);
            menu.setCarried(new ItemStack(Items.DIAMOND_HELMET));
            menu.clicked(helmet.index, 0, ClickType.PICKUP, player);
            require(helmet.getItem().is(Items.DIAMOND_HELMET) && menu.getCarried().isEmpty(),
                    "native WCT crafting-page equipment slot accepts the real carried helmet");
            menu.setWorkPage(TianshuWorkPage.ANVIL);
            menu.clicked(helmet.index, 0, ClickType.PICKUP, player);
            require(!helmet.hasItem() && menu.getCarried().is(Items.DIAMOND_HELMET),
                    "equipment remains interactive while the right-hand anvil pane is selected");
            menu.clicked(helmet.index, 0, ClickType.PICKUP, player);
            menu.setWirelessPage(TianshuEnhancedWirelessCraftingMenu.WirelessPage.SETTINGS);
            menu.clicked(helmet.index, 0, ClickType.PICKUP, player);
            require(helmet.getItem().is(Items.DIAMOND_HELMET) && menu.getCarried().isEmpty(),
                    "hidden equipment cannot be taken from a settings subwindow");
            menu.setWirelessPage(TianshuEnhancedWirelessCraftingMenu.WirelessPage.MAIN);
            menu.setWorkPage(TianshuWorkPage.CRAFTING);
            menu.clicked(helmet.index, 0, ClickType.PICKUP, player);
            require(!helmet.hasItem() && menu.getCarried().is(Items.DIAMOND_HELMET),
                    "returning to crafting restores equipment interaction without duplicating the helmet");
            menu.setCarried(ItemStack.EMPTY);
            menu.setWorkPage(TianshuWorkPage.CELL);
            menu.getSlots(Ae2ltSlotSemantics.TIANSHU_CELL).get(0).set(TianshuCellScrollFixture.stack());
            var marks = menu.getSlots(Ae2ltSlotSemantics.TIANSHU_CELL_CONFIG);
            var upgrades = menu.getSlots(Ae2ltSlotSemantics.TIANSHU_CELL_UPGRADE);
            menu.setFilter(marks.get(0).index, new ItemStack(Items.DIAMOND));
            menu.setCarried(appeng.core.definitions.AEItems.FUZZY_CARD.stack());
            menu.clicked(upgrades.get(0).index, 0, ClickType.PICKUP, player);
            require(menu.getCarried().isEmpty() && upgrades.get(0).hasItem(),
                    "marks and real upgrade cards are simultaneously editable");
            menu.setCellConfigRow(1);
            require(menu.isCellMarkVisible(3) && menu.isCellMarkVisible(11) && !menu.isCellMarkVisible(0),
                    "mark wheel moves one three-slot row while keeping a 3x3 viewport");
            menu.setFilter(marks.get(0).index, new ItemStack(Items.DIRT));
            require(marks.get(0).getItem().is(Items.DIAMOND), "scrolled-out marks reject stale writes");
            menu.setCellUpgradeRow(99);
            require(menu.cellUpgradeRow == 5 && menu.cellConfigRow == 1 && menu.isCellUpgradeVisible(7),
                    "eight upgrade slots scroll independently and clamp at the last three");
            menu.clicked(upgrades.get(0).index, 0, ClickType.PICKUP, player);
            require(menu.getCarried().isEmpty(), "scrolled-out real upgrade cannot be taken");
            menu.setCarried(appeng.core.definitions.AEItems.SPEED_CARD.stack());
            menu.clicked(upgrades.get(7).index, 0, ClickType.PICKUP, player);
            require(menu.getCarried().isEmpty() && upgrades.get(7).hasItem(), "last upgrade slot accepts the real card");
            menu.setCellConfigRow(999);
            require(menu.cellConfigRow == 18 && menu.isCellMarkVisible(62), "last mark row stays reachable");
            menu.setWirelessPage(TianshuEnhancedWirelessCraftingMenu.WirelessPage.SETTINGS);
            menu.setCellConfigRow(0);
            menu.setCellUpgradeRow(0);
            menu.setCellCopyMode(appeng.api.config.CopyMode.KEEP_ON_REMOVE);
            require(menu.cellCopyMode == appeng.api.config.CopyMode.CLEAR_ON_REMOVE,
                    "subwindows reject stale keep-configuration changes");
            require(menu.cellConfigRow == 18 && menu.cellUpgradeRow == 5, "subwindows reject stale scroll actions");
            menu.setWirelessPage(TianshuEnhancedWirelessCraftingMenu.WirelessPage.MAIN);
            menu.setCellConfigRow(0);
            menu.setCellUpgradeRow(0);
            require(marks.get(0).getItem().is(Items.DIAMOND) && upgrades.get(0).hasItem() && upgrades.get(7).hasItem(),
                    "scrolling preserves both off-screen inventories");
            menu.getSlots(Ae2ltSlotSemantics.TIANSHU_CELL).get(0).set(appeng.core.definitions.AEItems.ITEM_CELL_1K.stack());
            require(menu.cellConfigRow == 0 && menu.cellUpgradeRow == 0, "changing the real cell resets both scroll offsets");
            menu.setWorkPage(TianshuWorkPage.CRAFTING);
            System.out.println("TIANSHU_WORKSTATION_PASS persistent WCT equipment, simultaneous 3x3 marks/1x3 upgrades, independent scrolling and stale-slot guards");
            menu.setWirelessPage(TianshuEnhancedWirelessCraftingMenu.WirelessPage.MAGNET);
            menu.setFilter(menu.getSlots(AE2wtlibSlotSemantics.PICKUP_CONFIG).get(0).index, new ItemStack(Items.DIAMOND));
            require(ti.getOrCreateTag().contains("pickupConfig") && !tiWut.getOrCreateTag().contains("pickupConfig"), "filter editor must update exact open item");
            clientAction(menu, "togglepickupmode");
            require(ti.getOrCreateTag().getBoolean("pickupMode"), "native filter mode update");
            var pickup = menu.getSlots(AE2wtlibSlotSemantics.PICKUP_CONFIG).get(0);
            var insert = menu.getSlots(AE2wtlibSlotSemantics.INSERT_CONFIG).get(0);
            require(pickup == menu.getMagnetMenu().getSlots(AE2wtlibSlotSemantics.PICKUP_CONFIG).get(0),
                    "terminal must use native MagnetMenu slot instances");
            menu.setFilter(insert.index, new ItemStack(Items.GOLD_INGOT));
            clientAction(menu, "copy_down");
            require(insert.getItem().is(Items.DIAMOND), "native copy down");
            menu.setFilter(insert.index, new ItemStack(Items.GOLD_INGOT));
            clientAction(menu, "copy_up");
            require(pickup.getItem().is(Items.GOLD_INGOT), "native copy up");
            menu.setFilter(pickup.index, new ItemStack(Items.DIAMOND));
            clientAction(menu, "switch");
            require(pickup.getItem().is(Items.GOLD_INGOT) && insert.getItem().is(Items.DIAMOND), "native swap filters");
            clientAction(menu, "toggleinsertmode");
            require(ti.getOrCreateTag().getBoolean("insertMode"), "native insert mode update");
            menu.setFilter(pickup.index, new ItemStack(Items.DIAMOND));
            require(!tiWut.getOrCreateTag().contains("pickupConfig") && !tiWut.getOrCreateTag().contains("insertConfig"),
                    "all native actions must leave the globally selected different terminal unchanged");
            menu.setWirelessPage(TianshuEnhancedWirelessCraftingMenu.WirelessPage.MAIN);
            menu.setFilter(pickup.index, new ItemStack(Items.DIRT));
            clientAction(menu, "togglepickupmode");
            require(pickup.getItem().is(Items.DIAMOND) && ti.getOrCreateTag().getBoolean("pickupMode"),
                    "stale native filter writes and actions rejected outside magnet page");
            var ordinaryHost = new de.mari_023.ae2wtlib.wct.WCTMenuHost(player, 1, ti, (p, m) -> {});
            var ordinaryMenu = new de.mari_023.ae2wtlib.wct.magnet_card.config.MagnetMenu(10, player.getInventory(), ordinaryHost);
            require(ordinaryMenu.getMagnetHost() == handler.getMagnetHost(), "unadapted WT MagnetMenu retains native host selection");
            menu.setWorkPage(TianshuWorkPage.ANVIL);
            menu.getSlots(Ae2ltSlotSemantics.TIANSHU_ANVIL).get(0).set(new ItemStack(Items.IRON_INGOT));
            menu.setWirelessPage(TianshuEnhancedWirelessCraftingMenu.WirelessPage.TRASH);
            menu.setWorkPage(TianshuWorkPage.SMITHING);
            menu.clearWorkInputs(true);
            require(menu.workPage == TianshuWorkPage.ANVIL
                    && menu.getSlots(Ae2ltSlotSemantics.TIANSHU_ANVIL).get(0).hasItem(),
                    "WT subwindows reject stale work-page and clear-input actions");
            menu.clicked(menu.getSlots(Ae2ltSlotSemantics.TIANSHU_ANVIL).get(0).index, 0, ClickType.PICKUP, player);
            require(menu.getCarried().isEmpty(), "work inputs inaccessible in WT subpages");
            var trash = menu.getSlots(AE2wtlibSlotSemantics.TRASH).get(0);
            trash.set(new ItemStack(Items.DIRT, 3));
            menu.setWirelessPage(TianshuEnhancedWirelessCraftingMenu.WirelessPage.MAIN);
            require(!trash.hasItem() && menu.getSlots(Ae2ltSlotSemantics.TIANSHU_ANVIL).get(0).hasItem(), "trash clears and work inputs survive WT page changes");
            System.out.println("TIANSHU_WORKSTATION_PASS native locator, Ti-only/dual WUT, exact-item filter settings and shared-menu WT pages");
        } finally { menu.removed(player); player.containerMenu = player.inventoryMenu; }

        player.getInventory().setItem(0, ItemStack.EMPTY);
        ((CraftingTerminalHandlerAccessor) handler).ae2lt$invalidateCache();
        MagnetHandler.saveMagnetMode(ti, MagnetMode.PICKUP_INVENTORY);
        var diamond = new ItemEntity(helper.getLevel(), player.getX(), player.getY(), player.getZ(), new ItemStack(Items.DIAMOND));
        diamond.setNoPickUpDelay(); helper.getLevel().addFreshEntity(diamond);
        // Passing another terminal exercises the first-ticked-item adaptation.
        MagnetHandler.handle(player, tiWut);
        require(diamond.isRemoved(), "native magnet must use selected Ti settings and filter");
        var later = new ItemEntity(helper.getLevel(), player.getX(), player.getY(), player.getZ(), new ItemStack(Items.DIAMOND));
        later.setNoPickUpDelay(); helper.getLevel().addFreshEntity(later);
        TianshuWctIntegration.tick(player);
        require(!later.isRemoved(), "native magnet executor must deduplicate the same player/tick");
        later.discard();
        System.out.println("TIANSHU_WORKSTATION_PASS native filtered pickup and per-player/tick deduplication");
    }
}
