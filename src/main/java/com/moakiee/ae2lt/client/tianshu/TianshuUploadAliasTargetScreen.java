package com.moakiee.ae2lt.client.tianshu;

import appeng.client.Point;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.AETextField;
import appeng.client.gui.widgets.Scrollbar;
import appeng.menu.me.common.MEStorageMenu;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuUploadTargetData;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Read-only target picker: its only result is a draft alias, never an upload request. */
final class TianshuUploadAliasTargetScreen<M extends MEStorageMenu> extends TianshuUploadAliasScreen<M> {
    private final TianshuPatternEncodingTermMenu terminal;
    private final Consumer<String> selected;
    private final AETextField search;
    private final Scrollbar scroll;
    private final AE2Button refresh;
    private List<TianshuUploadTargetData> snapshot = List.of(), filtered = List.of();
    private int revision, rows;
    private boolean waiting = true;
    private long requestedAt;

    TianshuUploadAliasTargetScreen(AEBaseScreen<M> parent, TianshuPatternEncodingTermMenu terminal, Consumer<String> selected) {
        super(parent, "/screens/tianshu_upload_alias_targets.json", "targets");
        this.terminal = terminal;
        this.selected = selected;
        search = widgets.addTextField("search");
        search.setMaxLength(256);
        search.setPlaceholder(text("target_search"));
        scroll = widgets.addScrollBar("scrollbar", Scrollbar.SMALL);
        search.setResponder(v -> filter());
        refresh = widgets.addButton("refresh", text("refresh"), this::request);
        request();
    }

    private void request() {
        revision = terminal.getUploadTargetsRevision();
        waiting = true;
        requestedAt = net.minecraft.Util.getMillis();
        terminal.requestUploadTargets();
    }

    @Override
    protected void init() {
        super.init();
        place(search, 8, 24, imageWidth - 16);
        place(refresh, imageWidth - 80, imageHeight - 26, 72);
        rows = Math.max(1, (imageHeight - 78) / 20);
        scroll.setPosition(new Point(imageWidth - 14, 46));
        scroll.setHeight(rows * 20);
        filter();
    }

    private void filter() {
        filtered = snapshot.stream().filter(t -> search.getValue().isBlank()
                || TianshuUploadTargetMatcher.matches(t, search.getValue())).toList();
        scroll.setRange(0, Math.max(0, filtered.size() - rows), 1);
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (revision != terminal.getUploadTargetsRevision()) {
            revision = terminal.getUploadTargetsRevision();
            snapshot = List.copyOf(terminal.getUploadTargets());
            waiting = false;
            filter();
        }
    }

    private int hovered(double x, double y) {
        if (x < leftPos + 8 || x >= leftPos + imageWidth - 20 || y < topPos + 46 || y >= topPos + 46 + rows * 20) return -1;
        int index = scroll.getCurrentScroll() + (int) ((y - topPos - 46) / 20);
        return index < filtered.size() ? index : -1;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        int index = hovered(x, y);
        if (button == 0 && index >= 0) {
            var group = filtered.get(index).group();
            String value = TianshuUploadTargetMatcher.preferredAlias(
                    group.icon() == null ? "" : group.icon().getId().toString(),
                    group.icon() == null ? "" : group.icon().getDisplayName().getString(), group.name().getString());
            returnToParent();
            selected.accept(value);
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public void drawFG(GuiGraphics g, int x, int y, int mx, int my) {
        super.drawFG(g, x, y, mx, my);
        g.fill(8, 46, imageWidth - 20, 46 + rows * 20, 0xffe2e2e2);
        for (int row = 0; row < rows; row++) {
            int index = scroll.getCurrentScroll() + row;
            if (index >= filtered.size()) break;
            int top = 46 + row * 20;
            if (index == hovered(mx, my)) g.fill(8, top, imageWidth - 20, top + 20, 0xffafc4d4);
            g.drawString(font, clipped(filtered.get(index).group().name().getString(), imageWidth - 40), 12, top + 6, 0x252525, false);
        }
        Component status = waiting ? text(net.minecraft.Util.getMillis() - requestedAt > 10000 ? "unavailable" : "loading")
                : text(filtered.isEmpty() ? "no_matches" : "select_hint");
        g.drawString(font, clipped(status.getString(), imageWidth - 100), 8, imageHeight - 20, 0x404040, false);
    }

    @Override
    protected void renderTooltip(GuiGraphics g, int mx, int my) {
        super.renderTooltip(g, mx, my);
        int index = hovered(mx, my);
        if (index >= 0) drawTooltip(g, mx, my, List.of(filtered.get(index).group().name(), text("select_hint")));
    }
}
