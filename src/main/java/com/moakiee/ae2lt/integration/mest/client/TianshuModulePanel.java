package com.moakiee.ae2lt.integration.mest.client;

import appeng.client.gui.style.StyleManager;
import appeng.menu.me.common.IClientRepo;
import appeng.menu.me.common.GridInventoryEntry;
import com.lhy.mest.api.client.MestModuleContext;
import com.lhy.mest.client.dock.ModulePanel;
import com.moakiee.ae2lt.client.tianshu.TianshuCraftingTermScreen;
import com.moakiee.ae2lt.client.tianshu.TianshuPatternEncodingTermScreen;
import com.moakiee.ae2lt.client.tianshu.TianshuMaintenanceTermScreen;
import com.moakiee.ae2lt.integration.mest.MestSlot;
import com.moakiee.ae2lt.menu.extension.TianshuMenuContainer;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

/** A cropped native work area. All native controls and custom result gestures are preserved. */
public final class TianshuModulePanel extends ModulePanel {
    final MestModuleContext context;
    final boolean encoding;
    public final TianshuMaintenanceTermScreen<?> source;
    private boolean pressed;
    public TianshuModulePanel(MestModuleContext context, boolean encoding) {
        this.context = context; this.encoding = encoding;
        var menus = (TianshuMenuContainer) context.menu();
        source = encoding
                ? new TianshuPatternEncodingTermScreen<>(menus.tianshuPatternMenu(), context.menu().getPlayerInventory(), title(),
                        StyleManager.loadStyleDoc("/screens/terminals/tianshu_pattern_encoding_terminal.json"))
                : new TianshuCraftingTermScreen<>(menus.tianshuCraftingMenu(), context.menu().getPlayerInventory(), title(),
                        StyleManager.loadStyleDoc("/screens/terminals/tianshu_crafting_terminal.json"));
        source.init(Minecraft.getInstance(), 320, 320);
        context.slots().forEach(this::registerSlot);
        // Fan out the existing stream; this creates no second network subscription or server scan.
        var existing = context.menu().getClientRepo();
        var fanout = existing instanceof FanoutRepo old ? old : new FanoutRepo(existing);
        var child = source.getMenu().getClientRepo();
        child.handleUpdate(true, List.copyOf(fanout.getAllEntries()));
        fanout.children.put(id(), child);
        context.menu().setClientRepo(fanout);
        MestTianshuClient.attach(this);
    }
    private static final class FanoutRepo implements IClientRepo {
        private final IClientRepo primary;
        private final java.util.Map<String, IClientRepo> children = new java.util.HashMap<>();
        private FanoutRepo(IClientRepo primary) { this.primary = primary; }
        @Override public void handleUpdate(boolean full, List<GridInventoryEntry> entries) {
            primary.handleUpdate(full, entries);
            for (var child : children.values()) child.handleUpdate(full, entries);
        }
        @Override public Set<GridInventoryEntry> getAllEntries() { return primary.getAllEntries(); }
        @Override public java.util.Collection<GridInventoryEntry> getByIngredient(net.minecraft.world.item.crafting.Ingredient ingredient) {
            return primary.getByIngredient(ingredient);
        }
    }
    public boolean isEncoding() { return encoding; }
    @Override public com.lhy.mest.client.dock.model.ModuleDefaults defaults() {
        return new com.lhy.mest.client.dock.model.ModuleDefaults(false, false, false, true);
    }
    @Override public appeng.client.gui.Icon icon() { return encoding ? appeng.client.gui.Icon.TAB_PROCESSING : appeng.client.gui.Icon.TAB_CRAFTING; }
    @Override public String id() { return context.moduleId(); }
    @Override public Component title() { return Component.translatable(encoding ? "gui.ae2lt.mest.pattern" : "gui.ae2lt.mest.crafting"); }
    @Override public int minWidth() { return encoding ? 204 : 200; }
    @Override public int defaultWidth() { return minWidth(); }
    @Override public int minHeight() { return TITLE_BAR_HEIGHT + CONTENT_PADDING + crop().getHeight(); }
    @Override public int defaultHeight() { return minHeight(); }
    private Rect2i crop() { return source.embeddedPanelBounds(); }
    private int dx() { return contentLeft() - crop().getX(); }
    private int dy() { return contentTop() - crop().getY(); }
    @Override public void layoutSlots() {
        source.getMenu().setLinkStatus(context.menu().getLinkStatus());
        source.prepareEmbeddedPanel();
        var crop = crop();
        var originals = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<net.minecraft.world.inventory.Slot, Boolean>());
        for (var slot : ownedSlots()) originals.add(((MestSlot) slot).original());
        for (var slot : source.getMenu().slots) if (!originals.contains(slot)) { slot.x = slot.y = -9999; }
        for (var slot : ownedSlots()) {
            var original = ((MestSlot) slot).original();
            int ox = source.embeddedLeft() + original.x, oy = source.embeddedTop() + original.y;
            if (visible && original.isActive() && crop.contains(ox, oy)) placeSlot(slot, ox + dx(), oy + dy());
            else hideSlot(slot);
        }
    }
    private void draw(GuiGraphics g, Runnable render) {
        g.enableScissor(contentLeft(), contentTop(), contentLeft() + contentWidth(), contentTop() + contentHeight());
        g.pose().pushPose(); g.pose().translate(dx(), dy(), 0);
        try { render.run(); } finally { g.pose().popPose(); g.disableScissor(); }
    }
    @Override public void renderBackgroundContent(GuiGraphics g, Font font, int mx, int my, float tick) {
        layoutSlots();
        draw(g, () -> source.drawEmbeddedBackground(g, mx - dx(), my - dy(), tick));
    }
    @Override public void renderForegroundContent(GuiGraphics g, Font font, int mx, int my, float tick) {
        draw(g, () -> source.drawEmbeddedForeground(g, mx - dx(), my - dy()));
    }
    @Override public void renderSlots(GuiGraphics g, PanelSlotRenderer renderer) {
        draw(g, () -> { for (var slot : ownedSlots()) if (slot.x > -1000) source.drawEmbeddedSlot(g, ((MestSlot) slot).original()); });
    }
    public void tooltip(GuiGraphics g, int mx, int my) {
        source.updateEmbeddedHover(mx - dx(), my - dy());
        g.pose().pushPose(); g.pose().translate(dx(), dy(), 0);
        source.drawEmbeddedTooltips(g, mx - dx(), my - dy());
        g.pose().popPose();
    }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (my < contentTop() || !contains(mx, my)) return false;
        MestTianshuClient.focus(this); context.screen().meListPanel().setSearchFocused(false);
        source.updateEmbeddedHover(mx - dx(), my - dy());
        pressed = true;
        MestTianshuClient.input(source.getMenu(), () -> source.mouseClicked(mx - dx(), my - dy(), button));
        return true;
    }
    public boolean mouseReleased(double mx, double my, int button) {
        if (!pressed) return false;
        pressed = false;
        return MestTianshuClient.input(source.getMenu(), () -> source.mouseReleased(mx - dx(), my - dy(), button));
    }
    public boolean mouseDragged(double mx, double my, int button, double dragX, double dragY) {
        return pressed && MestTianshuClient.input(source.getMenu(), () -> source.mouseDragged(mx - dx(), my - dy(), button, dragX, dragY));
    }
    @Override public boolean mouseScrolled(double mx, double my, double amount) {
        return MestTianshuClient.input(source.getMenu(), () -> source.mouseScrolled(mx - dx(), my - dy(), 0, amount));
    }
    public boolean keyPressed(int key, int scan, int mods) {
        return MestTianshuClient.input(source.getMenu(), () -> source.keyPressed(key, scan, mods));
    }
    public boolean charTyped(char character, int mods) { return source.embeddedTextFocused() && source.charTyped(character, mods); }
}
