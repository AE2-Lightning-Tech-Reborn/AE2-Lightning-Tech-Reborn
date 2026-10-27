package com.moakiee.ae2lt.client.tianshu;

import appeng.client.Point;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.AESubScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.AETextField;
import appeng.client.gui.widgets.TabButton;
import appeng.menu.me.common.MEStorageMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Shared panel geometry and container-preserving navigation for upload alias pages. */
abstract class TianshuUploadAliasScreen<M extends MEStorageMenu> extends AESubScreen<M, AEBaseScreen<M>> {
    private final TabButton back;
    private final Component heading;

    TianshuUploadAliasScreen(AEBaseScreen<M> parent, String style, String title) {
        super(parent, style);
        heading = text(title);
        back = new TabButton(Icon.BACK, Component.translatable("gui.back"), b -> onClose());
        widgets.add("back", back);
    }

    static Component text(String key, Object... args) {
        return Component.translatable("ae2lt.tianshu.aliases." + key, args);
    }

    @Override
    protected void init() {
        imageWidth = Math.min(304, Math.max(100, width - 16));
        imageHeight = Math.min(220, Math.max(100, height - 20));
        super.init();
        back.setPosition(leftPos + imageWidth - 28, topPos - 5);
        hideSlots();
    }

    private void hideSlots() {
        for (var slot : menu.slots) { slot.x = -10000; slot.y = -10000; }
    }

    @Override
    protected void updateBeforeRender() { super.updateBeforeRender(); hideSlots(); }

    protected void place(AE2Button button, int x, int y, int w) {
        button.setPosition(leftPos + x, topPos + y);
        button.setWidth(w);
    }

    protected void place(AETextField field, int x, int y, int w) {
        field.move(new Point(leftPos + x, topPos + y));
        field.resize(w, 16);
    }

    protected String clipped(String value, int width) {
        return font.width(value) <= width ? value : font.plainSubstrByWidth(value, Math.max(0, width - font.width("…"))) + "…";
    }

    @Override
    public void drawBG(GuiGraphics g, int x, int y, int mouseX, int mouseY, float partialTicks) {
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xff565656);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, 0xffc6c6c6);
    }

    @Override
    public void drawFG(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        g.drawString(font, clipped(heading.getString(), imageWidth - 42), 8, 7, 0x252525, false);
    }

    @Override
    public void onClose() { returnToParent(); }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
}
