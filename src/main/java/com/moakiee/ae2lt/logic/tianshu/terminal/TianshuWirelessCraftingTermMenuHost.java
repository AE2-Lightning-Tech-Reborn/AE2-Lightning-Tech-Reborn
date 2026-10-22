package com.moakiee.ae2lt.logic.tianshu.terminal;

import appeng.menu.ISubMenu;
import de.mari_023.ae2wtlib.wct.WCTMenuHost;
import java.util.function.BiConsumer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Uses WTLib's native crafting-grid NBT, shared with ordinary WUT crafting. */
public final class TianshuWirelessCraftingTermMenuHost extends WCTMenuHost implements TianshuCraftingTerminalHost {
    public static final String WORKSTATIONS_TAG = "ae2lt:tianshu_workstations";
    private final TianshuWorkstationStorage workstations;
    private final ItemStack workstationAnchor;
    private final Integer playerInventorySlot;
    public Integer getPlayerInventorySlot() { return playerInventorySlot; }

    public TianshuWirelessCraftingTermMenuHost(Player player, @Nullable Integer inventorySlot, ItemStack stack,
            BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super(player, inventorySlot, stack, returnToMainMenu);
        playerInventorySlot = inventorySlot;
        workstationAnchor = getItemStack();
        workstations = new TianshuWorkstationStorage(data -> {
            if (data.isEmpty()) workstationAnchor.removeTagKey(WORKSTATIONS_TAG);
            else workstationAnchor.getOrCreateTag().put(WORKSTATIONS_TAG, data);
            player.getInventory().setChanged();
        });
        var tag = workstationAnchor.getTagElement(WORKSTATIONS_TAG);
        if (tag != null) workstations.load(tag);
    }

    @Override public TianshuWorkstationStorage getWorkstationStorage() { return workstations; }
    @Override public boolean stillValid() {
        return (getPlayer().level().isClientSide() || getItemStack() == workstationAnchor) && super.stillValid();
    }
}
