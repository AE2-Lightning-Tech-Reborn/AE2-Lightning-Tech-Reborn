package com.moakiee.ae2lt.client.compat;

import appeng.client.gui.Icon;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class TabButton extends appeng.client.gui.widgets.TabButton {
    public TabButton(Icon icon, Component message, OnPress onPress) {
        super(icon, message, Minecraft.getInstance().getItemRenderer(), onPress);
    }
    public TabButton(ItemStack icon, Component message, OnPress onPress) {
        super(icon, message, Minecraft.getInstance().getItemRenderer(), onPress);
    }
    public void setPosition(int x, int y) { this.x = x; this.y = y; }
}
