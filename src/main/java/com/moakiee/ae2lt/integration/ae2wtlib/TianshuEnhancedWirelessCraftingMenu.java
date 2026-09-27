package com.moakiee.ae2lt.integration.ae2wtlib;

import appeng.api.config.IncludeExclude;
import appeng.helpers.InventoryAction;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.slot.AppEngSlot;
import appeng.util.inv.AppEngInternalInventory;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWirelessCraftingTermMenuHost;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWorkPage;
import com.moakiee.ae2lt.menu.TianshuWirelessCraftingTermMenu;
import com.moakiee.ae2lt.mixin.ae2wtlib.CraftingTerminalHandlerAccessor;
import de.mari_023.ae2wtlib.AE2wtlibSlotSemantics;
import de.mari_023.ae2wtlib.terminal.ArmorSlot;
import de.mari_023.ae2wtlib.wct.CraftingTerminalHandler;
import de.mari_023.ae2wtlib.wct.magnet_card.MagnetHandler;
import de.mari_023.ae2wtlib.wct.magnet_card.MagnetMode;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;

/** WCT enhancement adapter. Sub-pages retain this menu; manual inputs belong to its terminal item. */
public final class TianshuEnhancedWirelessCraftingMenu extends TianshuWirelessCraftingTermMenu {
    public enum WirelessPage { MAIN, EQUIPMENT, SETTINGS, MAGNET, TRASH }
    public static final Set<SlotSemantic> ARMOR_SLOTS = Set.of(AE2wtlibSlotSemantics.HELMET,
            AE2wtlibSlotSemantics.CHESTPLATE, AE2wtlibSlotSemantics.LEGGINGS,
            AE2wtlibSlotSemantics.BOOTS, AE2wtlibSlotSemantics.OFFHAND);
    @GuiSync(170) public WirelessPage wirelessPage = WirelessPage.MAIN;
    @GuiSync(171) public IncludeExclude pickupMode = IncludeExclude.BLACKLIST;
    @GuiSync(172) public IncludeExclude insertMode = IncludeExclude.BLACKLIST;
    private final TianshuMagnetMenu magnetMenu;
    private final AppEngInternalInventory trash = new AppEngInternalInventory(null, 27);

    public TianshuEnhancedWirelessCraftingMenu(int id, Inventory inventory, TianshuWirelessCraftingTermMenuHost host) {
        super(id, inventory, host);
        addSlot(new ArmorSlot(inventory, ArmorSlot.Armor.HEAD) {
            @Override public boolean mayPlace(ItemStack stack) {
                return (stack.getItem() instanceof BlockItem block && block.getBlock() instanceof Equipable) || super.mayPlace(stack);
            }
        }, AE2wtlibSlotSemantics.HELMET);
        addSlot(new ArmorSlot(inventory, ArmorSlot.Armor.CHEST), AE2wtlibSlotSemantics.CHESTPLATE);
        addSlot(new ArmorSlot(inventory, ArmorSlot.Armor.LEGS), AE2wtlibSlotSemantics.LEGGINGS);
        addSlot(new ArmorSlot(inventory, ArmorSlot.Armor.FEET), AE2wtlibSlotSemantics.BOOTS);
        addSlot(Integer.valueOf(Inventory.SLOT_OFFHAND).equals(host.getSlot())
                ? new ArmorSlot.DisabledOffhandSlot(inventory) : new ArmorSlot(inventory, ArmorSlot.Armor.OFFHAND),
                AE2wtlibSlotSemantics.OFFHAND);

        magnetMenu = new TianshuMagnetMenu(this);
        for (var semantic : java.util.List.of(AE2wtlibSlotSemantics.PICKUP_CONFIG, AE2wtlibSlotSemantics.INSERT_CONFIG)) {
            for (var slot : magnetMenu.getSlots(semantic)) addSlot(slot, semantic);
        }
        for (int i = 0; i < trash.size(); i++) addSlot(new AppEngSlot(trash, i), AE2wtlibSlotSemantics.TRASH);
        registerClientAction("wirelessPage", WirelessPage.class, this::setWirelessPage);
        registerClientAction("wirelessSettings", WirelessSettings.class, this::setWirelessSettings);
        for (var action : TianshuMagnetMenu.ACTIONS) {
            registerClientAction(action, () -> {
                if (wirelessPage != WirelessPage.MAGNET || !hasMagnetCard()) return;
                switch (action) {
                    case "togglepickupmode" -> magnetMenu.togglePickupMode();
                    case "toggleinsertmode" -> magnetMenu.toggleInsertMode();
                    case "copy_up" -> magnetMenu.copyUp();
                    case "copy_down" -> magnetMenu.copyDown();
                    case "switch" -> magnetMenu.switchInsertPickup();
                }
                invalidateFilters();
                broadcastChanges();
            });
        }
    }

    public record WirelessSettings(boolean pickBlock, boolean restock, boolean magnet, boolean pickupToME, boolean craftIfMissing) {}
    public void setWirelessSettings(WirelessSettings settings) {
        if (isClientSide()) { sendClientAction("wirelessSettings", settings); return; }
        if (wirelessPage != WirelessPage.SETTINGS || !wirelessHost.stillValid()) return;
        var stack = wirelessHost.getItemStack();
        var tag = stack.getOrCreateTag();
        tag.putBoolean("pickBlock", settings.pickBlock());
        tag.putBoolean("restock", settings.restock());
        tag.putBoolean("craftIfMissing", settings.craftIfMissing());
        if (hasMagnetCard()) MagnetHandler.saveMagnetMode(stack,
                MagnetHandler.getMagnetMode(stack).set(settings.magnet(), settings.pickupToME()));
        getPlayer().getInventory().setChanged();
        invalidateFilters();
        broadcastChanges();
    }

    public TianshuMagnetMenu getMagnetMenu() { return magnetMenu; }
    public boolean hasMagnetCard() {
        var mode = MagnetHandler.getMagnetMode(wirelessHost.getItemStack());
        return mode != MagnetMode.NO_CARD && mode != MagnetMode.INVALID;
    }

    public void setWirelessPage(WirelessPage page) {
        if (isClientSide()) { wirelessPage = page; sendClientAction("wirelessPage", page); return; }
        if (page == WirelessPage.MAGNET && !hasMagnetCard()) return;
        if (wirelessPage == WirelessPage.TRASH && page != WirelessPage.TRASH) trash.clear();
        wirelessPage = page;
        broadcastChanges();
    }

    private void invalidateFilters() {
        if (isServerSide()) ((CraftingTerminalHandlerAccessor) CraftingTerminalHandler.getCraftingTerminalHandler(getPlayer())).ae2lt$invalidateCache();
    }

    private boolean isMagnetSlot(int index) {
        if (index < 0 || index >= slots.size()) return false;
        var semantic = getSlotSemantic(slots.get(index));
        return semantic == AE2wtlibSlotSemantics.PICKUP_CONFIG || semantic == AE2wtlibSlotSemantics.INSERT_CONFIG;
    }

    @Override public void setFilter(int slot, ItemStack stack) {
        super.setFilter(slot, stack);
        if (allowsSlot(slot) && isMagnetSlot(slot)) invalidateFilters();
    }

    @Override public void doAction(ServerPlayer player, InventoryAction action, int slot, long id) {
        super.doAction(player, action, slot, id);
        if (allowsSlot(slot) && isMagnetSlot(slot)) invalidateFilters();
    }

    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        super.clicked(slot, button, type, player);
        if (allowsSlot(slot) && isMagnetSlot(slot)) invalidateFilters();
    }

    @Override protected boolean allowsSlot(int index) {
        if (index < 0 || index >= slots.size()) return super.allowsSlot(index);
        var slot = slots.get(index);
        var semantic = getSlotSemantic(slot);
        if (semantic != null && ARMOR_SLOTS.contains(semantic)) return wirelessPage == WirelessPage.EQUIPMENT
                || wirelessPage == WirelessPage.MAIN;
        if (semantic == AE2wtlibSlotSemantics.PICKUP_CONFIG || semantic == AE2wtlibSlotSemantics.INSERT_CONFIG)
            return wirelessPage == WirelessPage.MAGNET && hasMagnetCard();
        if (semantic == AE2wtlibSlotSemantics.TRASH) return wirelessPage == WirelessPage.TRASH;
        if (wirelessPage != WirelessPage.MAIN)
            return semantic == SlotSemantics.PLAYER_INVENTORY || semantic == SlotSemantics.PLAYER_HOTBAR;
        return super.allowsSlot(index);
    }

    @Override protected boolean isMainWorkPage() { return wirelessPage == WirelessPage.MAIN; }
    @Override public boolean hasCompactWorkArea() { return true; }

    @Override protected boolean isValidQuickMoveDestination(Slot slot, ItemStack stack, boolean fromPlayer) {
        return allowsSlot(slot.index) && super.isValidQuickMoveDestination(slot, stack, fromPlayer);
    }

    @Override protected ItemStack transferStackToMenu(ItemStack stack) {
        // Shift-click in equipment/trash sub-pages belongs to that page, not to the ME inventory behind it.
        return wirelessPage == WirelessPage.MAIN ? super.transferStackToMenu(stack) : stack;
    }

    @Override public void broadcastChanges() {
        if (isServerSide() && magnetMenu != null) {
            pickupMode = magnetMenu.getMagnetHost().getPickupMode();
            insertMode = magnetMenu.getMagnetHost().getInsertMode();
            if (wirelessPage == WirelessPage.MAGNET && !hasMagnetCard()) wirelessPage = WirelessPage.SETTINGS;
        }
        super.broadcastChanges();
    }

    @Override public void onServerDataSync() {
        super.onServerDataSync();
        // Native MagnetScreen reads modes from MagnetHost's actual terminal stack.
        var stack = wirelessHost.getItemStack();
        stack.getOrCreateTag().putBoolean("pickupMode", pickupMode == IncludeExclude.WHITELIST);
        stack.getOrCreateTag().putBoolean("insertMode", insertMode == IncludeExclude.WHITELIST);
    }

    @Override public void removed(Player player) {
        if (isServerSide()) trash.clear();
        super.removed(player);
    }
}
