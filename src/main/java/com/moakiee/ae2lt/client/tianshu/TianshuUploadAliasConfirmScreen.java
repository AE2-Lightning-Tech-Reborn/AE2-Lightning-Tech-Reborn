package com.moakiee.ae2lt.client.tianshu;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.widgets.AE2Button;
import appeng.menu.me.common.MEStorageMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

final class TianshuUploadAliasConfirmScreen<M extends MEStorageMenu> extends TianshuUploadAliasScreen<M> {
    private final Component message;
    private int scroll;
    private final AE2Button cancel;
    private final AE2Button confirm;

    TianshuUploadAliasConfirmScreen(AEBaseScreen<M> parent, Component message, String action, Runnable accepted) {
        super(parent, "/screens/tianshu_upload_alias_confirm.json", "confirm");
        this.message = message;
        cancel = widgets.addButton("cancel", text("cancel"), this::onClose);
        confirm = widgets.addButton("confirm", text(action), () -> { returnToParent(); accepted.run(); });
    }

    @Override
    protected void init() {
        super.init();
        place(cancel, 8, imageHeight - 26, (imageWidth - 24) / 2);
        place(confirm, imageWidth / 2 + 4, imageHeight - 26, (imageWidth - 24) / 2);
        setInitialFocus(cancel);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        int count = Math.max(1, (imageHeight - 64) / font.lineHeight);
        scroll = Math.max(0, Math.min(font.split(message, imageWidth - 16).size() - count, scroll - (int) dy * 3));
        return true;
    }

    @Override
    public void drawFG(GuiGraphics g, int x, int y, int mx, int my) {
        super.drawFG(g, x, y, mx, my);
        var lines = font.split(message, imageWidth - 16);
        int count = Math.max(1, (imageHeight - 64) / font.lineHeight);
        scroll = Math.min(scroll, Math.max(0, lines.size() - count));
        for (int row = 0; row < count && row + scroll < lines.size(); row++)
            g.drawString(font, lines.get(row + scroll), 8, 30 + row * font.lineHeight, 0x252525, false);
    }
}
