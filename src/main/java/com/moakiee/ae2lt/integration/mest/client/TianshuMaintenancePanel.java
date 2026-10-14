package com.moakiee.ae2lt.integration.mest.client;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AmountFormat;
import com.lhy.mest.api.client.MestModuleContext;
import com.lhy.mest.client.dock.ModulePanel;
import com.moakiee.ae2lt.client.tianshu.TianshuGlobalReserveScreen;
import com.moakiee.ae2lt.menu.extension.TianshuMenuContainer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Live maintenance summary with access to the same validated rule and reserve editors. */
public final class TianshuMaintenancePanel extends ModulePanel {
    private final MestModuleContext context;
    private int firstRow;
    public TianshuMaintenancePanel(MestModuleContext context) { this.context = context; context.slots().forEach(this::registerSlot); }
    @Override public com.lhy.mest.client.dock.model.ModuleDefaults defaults() {
        return new com.lhy.mest.client.dock.model.ModuleDefaults(false, false, false, true);
    }
    @Override public String id() { return context.moduleId(); }
    @Override public Component title() { return Component.translatable("gui.ae2lt.mest.maintenance"); }
    @Override public int minWidth() { return 200; }
    @Override public int defaultWidth() { return 200; }
    @Override public int minHeight() { return 124; }
    @Override public int defaultHeight() { return 124; }
    @Override public void layoutSlots() { ownedSlots().forEach(ModulePanel::hideSlot); }
    private com.moakiee.ae2lt.menu.TianshuCraftingTermMenu menu() { return ((TianshuMenuContainer) context.menu()).tianshuCraftingMenu(); }
    @Override public void renderBackgroundContent(GuiGraphics g, Font font, int mx, int my, float tick) {
        var source = MestTianshuClient.crafting(context.screen());
        if (source != null) source.source.prepareEmbeddedPanel();
        int x = contentLeft(), y = contentTop();
        g.drawString(font, Component.translatable("gui.ae2lt.mest.maintenance.open"), x, y + 2, 0x413F54, false);
        var entries = menu().getMaintenanceSummary().values().stream().toList();
        if (entries.isEmpty()) {
            g.drawWordWrap(font, Component.translatable("gui.ae2lt.mest.maintenance.hint"), x, y + 22, contentWidth(), 0x413F54);
            return;
        }
        firstRow = Math.min(firstRow, Math.max(0, entries.size() - 4));
        for (int i = 0; i < 4 && firstRow + i < entries.size(); i++) {
            var entry = entries.get(firstRow + i);
            int rowY = y + 20 + i * 18;
            AEKeyRendering.drawInGui(net.minecraft.client.Minecraft.getInstance(), g, x, rowY, entry.key());
            g.drawString(font, font.plainSubstrByWidth(entry.key().getDisplayName().getString(), contentWidth() - 72), x + 20, rowY + 4, 0x413F54, false);
            g.drawString(font, entry.key().formatAmount(entry.storedAmount(), AmountFormat.SLOT), x + contentWidth() - 46, rowY + 4, 0x413F54, false);
        }
    }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || my < contentTop() || !contains(mx, my)) return false;
        var panel = MestTianshuClient.crafting(context.screen());
        if (panel == null) return false;
        if (my < contentTop() + 18) {
            panel.source.switchToScreen(new TianshuGlobalReserveScreen<>(panel.source));
        } else {
            var entries = menu().getMaintenanceSummary().values().stream().toList();
            int index = firstRow + (int) (my - contentTop() - 20) / 18;
            if (index >= 0 && index < entries.size()) panel.source.requestMaintenanceEditorFor(entries.get(index).key());
        }
        return true;
    }
    @Override public boolean mouseScrolled(double mx, double my, double amount) {
        firstRow = Math.max(0, firstRow - (int) Math.signum(amount)); return true;
    }
}
