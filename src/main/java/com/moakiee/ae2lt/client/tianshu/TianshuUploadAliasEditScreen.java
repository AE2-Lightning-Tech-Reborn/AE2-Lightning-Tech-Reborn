package com.moakiee.ae2lt.client.tianshu;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.AETextField;
import appeng.menu.me.common.MEStorageMenu;
import com.moakiee.ae2lt.config.AE2LTClientConfig;
import com.moakiee.ae2lt.config.UploadAliasRecords;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuUploadTargetData;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

final class TianshuUploadAliasEditScreen<M extends MEStorageMenu> extends TianshuUploadAliasScreen<M> {
    private final AETextField source, alias;
    private final AE2Button save, cancel, targets, matchCheck, refresh, clearSource, clearAlias;
    private final String original, initialSource, initialAlias;
    private final Consumer<String> onSaved;
    private List<TianshuUploadTargetData> snapshot = List.of();
    private boolean received, waiting, matchCheckExpanded;
    private int revision, formScroll;
    private long requestedAt;
    private Component status = Component.empty();

    TianshuUploadAliasEditScreen(AEBaseScreen<M> parent, UploadAliasRecords.Entry entry,
                                String sourceValue, String aliasValue, Consumer<String> onSaved) {
        super(parent, "/screens/tianshu_upload_alias_edit.json", entry == null ? "add" : "edit");
        original = entry == null ? null : entry.source();
        initialSource = entry == null && sourceValue.isBlank()
                ? TianshuUploadSourceSelection.collect(patternMenu()).sourceKey() : sourceValue;
        initialAlias = aliasValue;
        this.onSaved = onSaved;
        source = widgets.addTextField("source");
        alias = widgets.addTextField("alias");
        // Accept overlong drafts so validation can explain the limit instead of silently truncating input.
        source.setMaxLength(1024);
        alias.setMaxLength(1024);
        source.setValue(initialSource);
        alias.setValue(aliasValue);
        source.setTooltipMessage(List.of(text("source_help")));
        alias.setTooltipMessage(List.of(text("alias_help")));
        save = widgets.addButton("save", text("save"), this::save);
        cancel = widgets.addButton("cancel", text("cancel"), this::onClose);
        targets = widgets.addButton("targets", text("targets"), () ->
                switchToScreen(new TianshuUploadAliasTargetScreen<>(this, patternMenu(), alias::setValue)));
        matchCheck = widgets.addButton("matchCheck", text("match_check_show"), this::toggleMatchCheck);
        refresh = widgets.addButton("refresh", text("refresh"), this::requestTargets);
        clearSource = widgets.addButton("clearSource", Component.literal("×"), () -> source.setValue(""));
        clearAlias = widgets.addButton("clearAlias", Component.literal("×"), () -> alias.setValue(""));
        clearSource.setTooltip(Tooltip.create(text("clear_source")));
        clearAlias.setTooltip(Tooltip.create(text("clear_alias")));
        source.setResponder(v -> validate());
        alias.setResponder(v -> validate());
        validate();
    }

    private TianshuPatternEncodingTermMenu patternMenu() { return (TianshuPatternEncodingTermMenu) menu; }

    @Override
    protected void init() {
        super.init();
        int half = (imageWidth - 24) / 2;
        place(save, 16 + half, imageHeight - 26, half);
        place(cancel, 8, imageHeight - 26, half);
        layoutForm();
    }

    private void toggleMatchCheck() {
        matchCheckExpanded = !matchCheckExpanded;
        matchCheck.setMessage(text(matchCheckExpanded ? "match_check_hide" : "match_check_show"));
        if (matchCheckExpanded && !received && !waiting) requestTargets();
        layoutForm();
    }

    private int matchY() { return imageWidth < 280 ? 158 : 146; }

    private int statusY() { return matchCheckExpanded ? 176 : 146; }

    private int maxScroll() { return Math.max(0, statusY() + 14 - (imageHeight - 32)); }

    private void layoutForm() {
        formScroll = Math.min(formScroll, maxScroll());
        place(source, 8, 36 - formScroll, imageWidth - 44);
        place(alias, 8, 78 - formScroll, imageWidth - 44);
        place(clearSource, imageWidth - 30, 34 - formScroll, 22);
        place(clearAlias, imageWidth - 30, 76 - formScroll, 22);
        int half = (imageWidth - 24) / 2;
        place(targets, 8, 110 - formScroll, half);
        place(matchCheck, 16 + half, 110 - formScroll, half);
        if (imageWidth < 280) place(refresh, 16 + half, 134 - formScroll, half);
        else place(refresh, imageWidth - 72, 140 - formScroll, 64);
        for (var field : List.of(source, alias)) field.setVisible(field.getY() >= topPos + 24
                && field.getY() + 16 <= topPos + imageHeight - 32);
        for (var b : List.of(targets, matchCheck, refresh, clearSource, clearAlias))
            b.visible = b.getY() >= topPos + 24 && b.getY() + b.getHeight() <= topPos + imageHeight - 32;
        refresh.visible &= matchCheckExpanded;
    }

    private String sourceError() {
        String value = UploadAliasRecords.normalize(source.getValue());
        if (value.isEmpty()) return "required";
        if (value.contains("=")) return "separator";
        if (value.length() > 256) return "too_long";
        return "";
    }

    private String aliasError() {
        String value = alias.getValue().strip();
        if (value.isEmpty()) return "required";
        if (value.length() > 256) return "too_long";
        if (value.length() + UploadAliasRecords.normalize(source.getValue()).length() + 1 > 512) return "serialized_long";
        return "";
    }

    private void validate() {
        save.active = UploadAliasRecords.valid(source.getValue(), alias.getValue());
        status = Component.empty();
    }

    private void save() {
        if (!save.active) return;
        String key = UploadAliasRecords.normalize(source.getValue());
        String value = alias.getValue().strip();
        var existing = AE2LTClientConfig.uploadAliases().stream().filter(e -> e.source().equals(key)).findFirst();
        boolean collision = existing.isPresent() && !key.equals(original);
        if (collision && existing.get().alias().equals(value)) { status = text("exists"); return; }
        if (collision) {
            switchToScreen(new TianshuUploadAliasConfirmScreen<>(this,
                    text("replace_confirm", key, existing.get().alias(), value), "replace", () -> persist(key, value)));
        } else persist(key, value);
    }

    private void persist(String key, String value) {
        if (!AE2LTClientConfig.replaceUploadAlias(original, key, value)) { status = text("save_failed"); return; }
        returnToParent();
        onSaved.accept(key);
    }

    @Override
    public void onClose() {
        if (!source.getValue().equals(initialSource) || !alias.getValue().equals(initialAlias)) {
            switchToScreen(new TianshuUploadAliasConfirmScreen<>(this, text("discard_confirm"), "discard", this::returnToParent));
        } else returnToParent();
    }

    private void requestTargets() {
        revision = patternMenu().getUploadTargetsRevision();
        requestedAt = net.minecraft.Util.getMillis();
        waiting = true;
        patternMenu().requestUploadTargets();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if ((waiting || received) && patternMenu().getUploadTargetsRevision() != revision) {
            revision = patternMenu().getUploadTargetsRevision();
            snapshot = List.copyOf(patternMenu().getUploadTargets());
            received = true;
            waiting = false;
        }
    }

    private Component matchStatus() {
        if (waiting && net.minecraft.Util.getMillis() - requestedAt > 10000) return text(received ? "stale_failed" : "unavailable");
        if (waiting) return text(received ? "refreshing" : "loading");
        long count = snapshot.stream().filter(t -> TianshuUploadTargetMatcher.matches(t, alias.getValue().strip())).count();
        return text(count == 0 ? "no_matches" : count == 1 ? "one_match" : "matches", count);
    }

    @Override
    public void drawFG(GuiGraphics g, int x, int y, int mx, int my) {
        super.drawFG(g, x, y, mx, my);
        label(g, text("source"), 24, 0x252525);
        label(g, text("alias"), 66, 0x252525);
        if (!sourceError().isEmpty()) label(g, text(sourceError()), 55, 0xa02020);
        if (!aliasError().isEmpty()) label(g, text(aliasError()), 97, 0xa02020);
        if (matchCheckExpanded) label(g, matchStatus(), matchY(), 0x404040,
                imageWidth < 280 ? imageWidth - 16 : imageWidth - 88);
        label(g, status.getString().isEmpty() ? text("preview_hint") : status, statusY(), 0x404040);
    }

    private void label(GuiGraphics g, Component message, int y, int color) {
        label(g, message, y, color, imageWidth - 16);
    }

    private void label(GuiGraphics g, Component message, int y, int color, int availableWidth) {
        int top = y - formScroll;
        if (top >= 23 && top + font.lineHeight <= imageHeight - 32)
            g.drawString(font, clipped(message.getString(), availableWidth), 8, top, color, false);
    }

    @Override
    protected void renderTooltip(GuiGraphics g, int mx, int my) {
        super.renderTooltip(g, mx, my);
        int y = my - topPos + formScroll;
        if (mx < leftPos + 8 || mx >= leftPos + imageWidth - 8 || my < topPos + 24 || my >= topPos + imageHeight - 32) return;
        if (matchCheckExpanded && y >= matchY() && y < matchY() + font.lineHeight
                && (imageWidth < 280 || mx < leftPos + imageWidth - 80)) {
            var lines = new java.util.ArrayList<Component>();
            lines.add(matchStatus());
            snapshot.stream().filter(t -> TianshuUploadTargetMatcher.matches(t, alias.getValue().strip()))
                    .limit(8).forEach(t -> lines.add(t.group().name()));
            drawTooltip(g, mx, my, lines);
        } else if (y >= statusY()) drawTooltip(g, mx, my, List.of(text("preview_hint"), status));
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (maxScroll() > 0) {
            formScroll = Math.max(0, Math.min(maxScroll(), formScroll - (int) (dy * 18)));
            layoutForm();
            return true;
        }
        return super.mouseScrolled(x, y, dx, dy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_S && hasControlDown()) { save(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
}
