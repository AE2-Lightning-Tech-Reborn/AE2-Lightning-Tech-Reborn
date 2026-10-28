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
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/** Shared panel geometry and container-preserving navigation for upload alias pages. */
abstract class TianshuUploadAliasScreen<M extends MEStorageMenu> extends AESubScreen<M, AEBaseScreen<M>> {
    protected static final ResourceLocation PANEL_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "ae2lt", "textures/gui/wireless_overloaded_home.png");
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
        imageWidth = Math.min(preferredWidth(), Math.max(100, width - 16));
        imageHeight = Math.min(preferredHeight(), Math.max(100, height - 20));
        super.init();
        back.setPosition(leftPos + imageWidth - 28, topPos - 5);
        hideSlots();
    }

    protected int preferredHeight() { return 220; }

    protected int preferredWidth() { return 304; }

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
        field.resize(w, 12);
        // Recompute EditBox's horizontal viewport after expanding a freshly styled widget.
        int cursor = field.getCursorPosition();
        field.setCursorPosition(0);
        field.setCursorPosition(cursor);
    }

    protected String clipped(String value, int width) {
        return font.width(value) <= width ? value : font.plainSubstrByWidth(value, Math.max(0, width - font.width("…"))) + "…";
    }

    @Override
    public void drawBG(GuiGraphics g, int x, int y, int mouseX, int mouseY, float partialTicks) {
        int[] sourceX = {0, 10, 185, 195};
        int[] sourceY = {0, 18, 155, 157};
        int[] targetX = {0, 10, imageWidth - 10, imageWidth};
        int[] targetY = {0, 18, imageHeight - 2, imageHeight};
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                g.blit(PANEL_TEXTURE, x + targetX[column], y + targetY[row],
                        targetX[column + 1] - targetX[column], targetY[row + 1] - targetY[row],
                        sourceX[column], sourceY[row], sourceX[column + 1] - sourceX[column],
                        row == 1 ? 1 : sourceY[row + 1] - sourceY[row], 256, 256);
            }
        }
        // AE2 19.x paints only the first 126 pixels of an input's textured interior.
        // Continue that same texture for these wider forms, behind the normal widget rendering.
        var texture = appeng.client.gui.style.Blitter.texture("guis/text_field.png", 128, 128);
        for (var child : children()) {
            if (!(child instanceof AETextField field) || !field.isVisible()) continue;
            int innerEnd = field.getWidth() + 3 + font.width("_");
            int textureY = field.isFocused() ? 24 : 0;
            for (int offset = 127; offset < innerEnd; offset += 126) {
                texture.src(1, textureY, Math.min(126, innerEnd - offset), 12)
                        .dest(field.getX() - 2 + offset, field.getY() - 2).blit(g);
            }
        }
    }

    @Override
    public void drawFG(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        g.drawString(font, clipped(heading.getString(), imageWidth - 42), 10, 7, 0x2A2E3F, false);
    }

    @Override
    public void onClose() { returnToParent(); }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
}
