package com.moakiee.ae2lt.client.tianshu;

import appeng.client.gui.AEBaseScreen;
import net.minecraft.client.gui.components.Button;
import appeng.client.gui.widgets.AETextField;
import appeng.menu.me.common.MEStorageMenu;
import com.moakiee.ae2lt.config.AE2LTClientConfig;
import com.moakiee.ae2lt.config.UploadAliasRecords;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class TianshuUploadAliasEditScreen<M extends MEStorageMenu> extends TianshuUploadTargetScreen<M> {
    private final AETextField source, alias;
    private final Button save, cancel;
    private final Component heading;
    private final String original, initialSource, initialAlias;
    private final Consumer<String> onSaved;
    private Component status = Component.empty();

    TianshuUploadAliasEditScreen(AEBaseScreen<M> parent, UploadAliasRecords.Entry entry,
                                String sourceValue, String aliasValue, Consumer<String> onSaved) {
        super(parent, "/screens/tianshu_upload_alias_edit.json", true);
        heading = text(entry == null ? "add_title" : "edit_title");
        original = entry == null ? null : entry.source();
        initialSource = entry == null && sourceValue.isBlank()
                ? TianshuUploadSourceSelection.collect(patternMenu()).sourceKey() : sourceValue;
        initialAlias = aliasValue;
        this.onSaved = onSaved;
        source = sourceField;
        alias = aliasField;
        source.setPlaceholder(text("source"));
        alias.setPlaceholder(text("alias"));
        // Accept overlong drafts so validation can explain the limit instead of silently truncating input.
        source.setMaxLength(1024);
        alias.setMaxLength(1024);
        source.setValue(initialSource);
        alias.setValue(aliasValue);
        save = widgets.addButton("save", text("save"), this::save);
        cancel = widgets.addButton("cancel", text("cancel"), this::returnToParent);
        source.setResponder(v -> validate());
        alias.setResponder(v -> { refreshAliasQuery(); validate(); });
        validate();
    }

    private TianshuPatternEncodingTermMenu patternMenu() { return (TianshuPatternEncodingTermMenu) menu; }

    /** Copies recipe identity into this draft without transferring ingredients or saving it. */
    public void setRecipeSource(Object recipe, String fallbackSourceKey) {
        source.setValue(TianshuRecipeTransferContext.sourceKeyFor(recipe, fallbackSourceKey));
    }

    @Override
    protected void init() {
        super.init();
        int half = (imageWidth - 24) / 2;
        save.setPosition(leftPos + 16 + half, topPos + imageHeight - 26);
        save.setWidth(half);
        cancel.setPosition(leftPos + 8, topPos + imageHeight - 26);
        cancel.setWidth(half);
    }

    private static Component text(String key, Object... args) {
        return TianshuUploadAliasScreen.text(key, args);
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
        var sourceTooltip = new java.util.ArrayList<Component>();
        sourceTooltip.add(text("source"));
        if (!sourceError().isEmpty()) sourceTooltip.add(text(sourceError()));
        sourceTooltip.addAll(List.of(text("source_help"), text("editor_hint"), text("clear_field")));
        source.setTooltipMessage(sourceTooltip);
        var aliasTooltip = new java.util.ArrayList<Component>();
        aliasTooltip.add(text("alias"));
        if (!aliasError().isEmpty()) aliasTooltip.add(text(aliasError()));
        aliasTooltip.addAll(List.of(text("alias_help"), text("alias_match_help"), text("clear_field")));
        alias.setTooltipMessage(aliasTooltip);
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

    @Override
    public void drawFG(GuiGraphics g, int x, int y, int mx, int my) {
        super.drawFG(g, x, y, mx, my);
        g.drawString(font, heading, 7, 6, 0x252525, false);
        Component message = footerMessage();
        g.drawString(font, font.plainSubstrByWidth(message.getString(), imageWidth - 16),
                8, imageHeight - 39, save.active ? 0x404040 : 0xa02020, false);
    }

    private Component footerMessage() {
        if (!status.getString().isEmpty()) return status;
        if (!sourceError().isEmpty()) return text("field_error", text("source"), text(sourceError()));
        if (!aliasError().isEmpty()) return text("field_error", text("alias"), text(aliasError()));
        return text("select_provider_hint");
    }

    @Override
    protected void renderTooltip(GuiGraphics g, int mx, int my) {
        super.renderTooltip(g, mx, my);
        if (mx >= leftPos + 8 && mx < leftPos + imageWidth - 8
                && my >= topPos + imageHeight - 42 && my < topPos + imageHeight - 28) {
            drawTooltip(g, mx, my, List.of(footerMessage(), text("editor_save_hint"), text("editor_hint")));
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_S && hasControlDown()) { save(); return true; }
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
}
