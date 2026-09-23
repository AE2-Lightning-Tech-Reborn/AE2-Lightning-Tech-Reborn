package com.moakiee.ae2lt.item;

import de.mari_023.ae2wtlib.api.terminal.ItemWT;
import com.moakiee.ae2lt.menu.TianshuWirelessPatternEncodingTermMenu;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import appeng.menu.locator.ItemMenuHostLocator;

/** Wireless item counterpart of the in-world Tianshu pattern encoding terminal. */
public final class TianshuWirelessPatternEncodingTerminalItem extends ItemWT {
    public TianshuWirelessPatternEncodingTerminalItem() {
        super(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.ITEM,
                net.minecraft.resources.Identifier.fromNamespaceAndPath(
                        "ae2lt", "wireless_tianshu_pattern_encoding_terminal"))));
    }

    @Override
    public MenuType<?> getMenuType(ItemMenuHostLocator locator, Player player) {
        return TianshuWirelessPatternEncodingTermMenu.TYPE;
    }

}
