package com.moakiee.ae2lt.client.ldlib;

import appeng.client.gui.Icon;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class LdlibHorizontalTab extends LdlibAe2Button {
    private boolean selected;

    public LdlibHorizontalTab(Icon icon, Component tooltip, Runnable action) {
        this(22, 22, LdlibAe2Icons.texture(icon), tooltip, action, 3, 2);
    }

    public LdlibHorizontalTab(ItemStack icon, Component tooltip, Runnable action) {
        this(22, 22, itemTexture(icon, true), tooltip, action, 3, 2);
    }

    public static LdlibHorizontalTab compact(int width, int height, ItemStack item, Icon icon,
            Component tooltip, Runnable action) {
        int tabWidth = width != 0 ? width : 22;
        int tabHeight = height != 0 ? height : 22;
        var glyph = icon == null ? itemTexture(item, false) : LdlibAe2Icons.texture(icon);
        return new LdlibHorizontalTab(tabWidth, tabHeight, glyph, tooltip, action, 1, (tabHeight - 16) / 2);
    }

    private static IGuiTexture itemTexture(ItemStack stack, boolean decorations) {
        return (graphics, mouseX, mouseY, x, y, width, height, ticks) -> {
            graphics.flush();
            var pose = graphics.pose();
            pose.pushPose();
            try {
                // AE2 tabs use +100; DrawerHelper changes depth and clears the depth buffer.
                pose.translate(0, 0, 100);
                graphics.renderItem(stack, (int) x, (int) y);
                if (decorations) {
                    graphics.renderItemDecorations(Minecraft.getInstance().font, stack, (int) x, (int) y);
                }
            } finally {
                pose.popPose();
            }
        };
    }

    private LdlibHorizontalTab(int width, int height, IGuiTexture glyph, Component tooltip,
            Runnable action, int iconX, int iconY) {
        super(width, height, tooltip, action);
        var normal = LdlibAe2Icons.texture(Icon.HORIZONTAL_TAB);
        var selectedFace = LdlibAe2Icons.texture(Icon.HORIZONTAL_TAB_SELECTED);
        var focused = LdlibAe2Icons.texture(Icon.HORIZONTAL_TAB_FOCUS);
        var base = IGuiTexture.dynamic(() -> isFocused() ? focused : selected ? selectedFace : normal);
        control.buttonStyle(style -> style.baseTexture(base).hoverTexture(base).pressedTexture(base));
        var image = new UIElement().setAllowHitTest(false).style(style -> style.background(glyph));
        control.addChild(LdlibScreens.at(image, iconX, iconY, 16, 16));
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

}
