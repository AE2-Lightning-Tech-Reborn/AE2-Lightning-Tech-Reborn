package com.moakiee.ae2lt.menu;

import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.slot.RestrictedInputSlot;
import com.moakiee.ae2lt.integration.ae2wtlib.TianshuWctIntegration;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWirelessCraftingTermMenuHost;
import de.mari_023.ae2wtlib.AE2wtlibSlotSemantics;
import de.mari_023.ae2wtlib.wut.ItemWUT;
import de.mari_023.ae2wtlib.terminal.WTMenuHost;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.fml.ModList;

public class TianshuWirelessCraftingTermMenu extends TianshuCraftingTermMenu {
    public static final MenuType<TianshuWirelessCraftingTermMenu> TYPE = Ae2ltMenuBuilder.buildUnregistered(
            MenuTypeBuilder.create(TianshuWirelessCraftingTermMenu::create, TianshuWirelessCraftingTermMenuHost.class),
            new ResourceLocation("ae2lt", "wireless_tianshu_crafting_terminal"));
    protected final TianshuWirelessCraftingTermMenuHost wirelessHost;

    public TianshuWirelessCraftingTermMenu(int id, Inventory inventory, TianshuWirelessCraftingTermMenuHost host) {
        super(TYPE, id, inventory, host);
        wirelessHost = host;
        addSlot(new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.QE_SINGULARITY,
                host.getSubInventory(WTMenuHost.INV_SINGULARITY), 0), AE2wtlibSlotSemantics.SINGULARITY);
    }
    private static TianshuWirelessCraftingTermMenu create(int id, Inventory inventory, TianshuWirelessCraftingTermMenuHost host) {
        return ModList.get().isLoaded("ae2wtlib") ? TianshuWctIntegration.createMenu(id, inventory, host)
                : new TianshuWirelessCraftingTermMenu(id, inventory, host);
    }
    public boolean isWUT() { return wirelessHost.getItemStack().getItem() instanceof ItemWUT; }
    public TianshuWirelessCraftingTermMenuHost getWirelessHost() { return wirelessHost; }
    @Override public appeng.api.networking.IGridNode getNetworkNode() {
        return wirelessHost == null ? super.getNetworkNode() : wirelessHost.getActionableNode();
    }
}
