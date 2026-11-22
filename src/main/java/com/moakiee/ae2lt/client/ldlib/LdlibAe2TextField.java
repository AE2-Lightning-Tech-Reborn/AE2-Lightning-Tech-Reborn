package com.moakiee.ae2lt.client.ldlib;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import appeng.client.gui.WidgetContainer;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.math.Size;
import com.moakiee.ae2lt.mixin.client.EditBoxAccessor;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringUtil;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/** Editable LDLib text hosted in an AE2 screen style position. */
public class LdlibAe2TextField extends LdlibAe2Widget {
    private static final int AE_FIELD_HEIGHT = 12;
    private static final int FIELD_PADDING = 2;
    private static final LdlibStyleSprite AE_FIELD_ATLAS = new LdlibStyleSprite(
            ResourceLocation.fromNamespaceAndPath("ae2", "textures/guis/text_field.png"),
            Size.of(128, 128), 0, 0, 128, 128);

    private final TextField field;
    private final UIElement atlasFrame;
    private Predicate<String> filter = value -> value != null;
    private @Nullable Consumer<String> responder;
    private @Nullable EditBox nativeModel;
    private boolean nativeOwnsFocus;
    private long focusedTime = Util.getMillis();
    private int maxLength = 32;
    private List<Component> tooltip = List.of();
    private boolean editable = true;
    private Integer framedTextColor;
    private int cursorPadding;
    private int editorHorizontalPadding = FIELD_PADDING * 2;
    private boolean ae2Frame;
    private int selectionColor = 0xFF0000FF;
    private int placeholderColor;
    private Component placeholder = Component.empty();

    protected LdlibAe2TextField(int width, int height) {
        this(width, height, new TextField());
    }

    protected LdlibAe2TextField(int width, int height, boolean truncateEdits) {
        this(width, height, truncateEdits ? new BoundedTextField() : new TextField());
    }

    private LdlibAe2TextField(int width, int height, TextField field) {
        this(width, height, field, new UIElement().setAllowHitTest(false).setDisplay(false));
    }

    private LdlibAe2TextField(int width, int height, TextField field, UIElement atlasFrame) {
        super(width, height, new UIElement().setAllowHitTest(false).setOverflowVisible(true).addChildren(
                LdlibScreens.at(atlasFrame, 0, 0, width, AE_FIELD_HEIGHT),
                LdlibScreens.at(field, 0, 0, width, height)));
        this.field = field;
        this.atlasFrame = atlasFrame;
        if (field instanceof BoundedTextField bounded) {
            bounded.owner = this;
            // Native caret and selection extend outside the nine-pixel text content box.
            field.setOverflowVisible(true);
        }
        field.setTextValidator(value -> value.length() <= maxLength && filter.test(value));
        field.setTextResponder(value -> {
            if (responder != null) {
                responder.accept(value);
            }
        });
        field.style(style -> style.background(new ColorRectTexture(0xFF252525)));
        field.layout(layout -> layout.paddingHorizontal(FIELD_PADDING));
        field.textFieldStyle(style -> style.textColor(0xFFE0E0E0).textShadow(false));
    }

    public static LdlibAe2TextField add(WidgetContainer widgets, ScreenStyle style, String id) {
        var bounds = style.getWidget(id);
        var field = createFramed(bounds.getWidth(), bounds.getHeight(), 0xFFFFFFFF,
                style.getColor(PaletteColor.TEXTFIELD_PLACEHOLDER).toARGB());
        field.setMaxLength(25);
        field.setSelectionColor(style.getColor(PaletteColor.TEXTFIELD_SELECTION).toARGB());
        widgets.add(id, field);
        return field;
    }

    public static LdlibAe2TextField create(int width, int height) {
        return createFramed(width, height, 0xFFFFFFFF, 0xFF606060);
    }

    public static LdlibAe2TextField create(int width, int height, ScreenStyle style) {
        var editor = createFramed(width, height, style.getColor(PaletteColor.TEXTFIELD_TEXT).toARGB(),
                style.getColor(PaletteColor.TEXTFIELD_PLACEHOLDER).toARGB());
        editor.setSelectionColor(style.getColor(PaletteColor.TEXTFIELD_SELECTION).toARGB());
        return editor;
    }

    private static LdlibAe2TextField createFramed(int width, int height, int textColor, int placeholderColor) {
        var editor = new LdlibAe2TextField(width, height, true);
        editor.useFramedEditor(textColor, placeholderColor);
        return editor;
    }

    protected final void useFramedEditor(int textColor, int placeholderColor) {
        cursorPadding = Minecraft.getInstance().font.width("_");
        useAe2Frame(cursorPadding);
        framedTextColor = textColor;
        this.placeholderColor = placeholderColor;
        setTextColor(textColor);
    }

    public void setBordered(boolean bordered) {
        atlasFrame.setDisplay(false);
        field.style(style -> style.background(bordered ? new ColorRectTexture(0xFF252525) : IGuiTexture.EMPTY));
    }

    protected void useUnborderedEditBoxStyle() {
        ae2Frame = false;
        editorHorizontalPadding = 0;
        setBordered(false);
        // LDLib centers a 9 px text line. Pad the bottom to keep the old top-aligned baseline.
        field.layout(layout -> layout.paddingAll(0).paddingHorizontal(0)
                .paddingBottom(Math.max(0, getHeight() - 9)));
        field.textFieldStyle(style -> style.textShadow(true).focusOverlay(IGuiTexture.EMPTY));
    }

    protected void useAe2Frame(int cursorPadding) {
        ae2Frame = true;
        this.cursorPadding = cursorPadding;
        editorHorizontalPadding = FIELD_PADDING * 2 + cursorPadding;
        if (atlasFrame.getChildren().isEmpty()) {
            int editorWidth = Math.max(getWidth(), FIELD_PADDING * 2);
            atlasFrame.addChild(framePart(0, 0, 1));
            for (int x = 1; x < editorWidth - 1; x += 126) {
                atlasFrame.addChild(framePart(x, 1, Math.min(126, editorWidth - 1 - x)));
            }
            atlasFrame.addChild(framePart(editorWidth - 1, 127, 1));
        }
        atlasFrame.setDisplay(true);
        field.style(style -> style.background(IGuiTexture.EMPTY));
        // Match AETextField's unbordered EditBox origin and reserve its cursor width.
        field.layout(layout -> layout.paddingLeft(FIELD_PADDING).paddingRight(FIELD_PADDING + cursorPadding)
                .paddingTop(FIELD_PADDING).paddingBottom(Math.max(0, getHeight() - FIELD_PADDING - 9)));
        field.textFieldStyle(style -> style.textShadow(true).focusOverlay(IGuiTexture.EMPTY));
    }

    private UIElement framePart(int x, int atlasX, int width) {
        var normal = AE_FIELD_ATLAS.slice(atlasX, 0, width, AE_FIELD_HEIGHT);
        var disabled = AE_FIELD_ATLAS.slice(atlasX, AE_FIELD_HEIGHT, width, AE_FIELD_HEIGHT);
        var focused = AE_FIELD_ATLAS.slice(atlasX, 2 * AE_FIELD_HEIGHT, width, AE_FIELD_HEIGHT);
        var part = new UIElement().setAllowHitTest(false)
                .style(style -> style.background(IGuiTexture.dynamic(
                        () -> !editable ? disabled : isFocused() ? focused : normal)));
        return LdlibScreens.at(part, x, 0, width, AE_FIELD_HEIGHT);
    }

    protected void selectAllText() {
        // AETextField notifies the move to zero before extending its anchor to the end.
        setTextSelection(0, 0);
        notifyResponder(getValue());
        setSelection(getValue().length(), field.getCursorPos());
        syncNativeSelection();
    }

    public int getCursorPosition() {
        return field.getCursorPos();
    }

    public void moveCursorTo(int position) {
        int cursor = Math.clamp(position, 0, getValue().length());
        setTextSelection(cursor, cursor);
        notifyResponder(getValue());
    }

    protected void setTextSelection(int cursor, int anchor) {
        field.setCursor(cursor);
        setSelection(anchor, cursor);
        syncNativeSelection();
    }

    private void setSelection(int anchor, int cursor) {
        field.setSelection(anchor, cursor);
        scrollToAnchor();
    }

    private void scrollToAnchor() {
        if (field instanceof BoundedTextField bounded) {
            bounded.scrollTo(field.getSelectionStart());
        }
    }

    protected void restoreSelectionFrom(LdlibAe2TextField previous) {
        field.setCursor(previous.field.getCursorPos());
        field.setSelection(previous.field.getSelectionStart(), previous.field.getSelectionEnd());
        if (field instanceof BoundedTextField bounded && previous.field instanceof BoundedTextField old) {
            bounded.displayPos = old.displayPos;
        }
        syncNativeSelection();
    }

    protected void setTextColor(int color) {
        field.textFieldStyle(style -> style.textColor(color));
    }

    public void setSelectionColor(int color) {
        selectionColor = color;
    }

    private Font editorFont() {
        return nativeModel == null ? Minecraft.getInstance().font
                : ((EditBoxAccessor) (Object) nativeModel).ae2lt$getEditorFont();
    }

    private int editorWidth() {
        return Math.max(0, getWidth() - editorHorizontalPadding);
    }

    private FormattedCharSequence formatEditorText(String text, int offset) {
        return nativeModel == null ? FormattedCharSequence.forward(text, Style.EMPTY)
                : ((EditBoxAccessor) (Object) nativeModel).ae2lt$getEditorFormatter().apply(text, offset);
    }

    private void drawEditorContents(GUIContext context, BoundedTextField editor) {
        var graphics = context.graphics;
        Font font = editorFont();
        var style = editor.getTextFieldStyle();
        var accessor = nativeModel == null ? null : (EditBoxAccessor) (Object) nativeModel;
        boolean shadow = nativeModel == null ? style.textShadow() : nativeModel.getTextShadow();
        String value = editor.getRawText();
        int displayPos = editor.displayPos;
        String visible = font.plainSubstrByWidth(value.substring(displayPos), editorWidth());
        int cursor = editor.getCursorPos() - displayPos;
        int anchor = Math.clamp(editor.getSelectionStart() - displayPos, 0, visible.length());
        boolean cursorVisible = cursor >= 0 && cursor <= visible.length();
        long blinkStart = nativeOwnsFocus && accessor != null ? accessor.ae2lt$getEditorFocusedTime() : focusedTime;
        boolean blink = isFocused() && (Util.getMillis() - blinkStart) / 300L % 2L == 0L && cursorVisible;
        int x = (int) editor.getContentX();
        int y = (int) editor.getContentY();
        int endX = x;
        if (!visible.isEmpty()) {
            String before = cursorVisible ? visible.substring(0, cursor) : visible;
            endX = graphics.drawString(font, formatEditorText(before, displayPos), x, y, style.textColor(), shadow);
        }
        boolean insertCursor = editor.getCursorPos() < value.length() || value.length() >= maxLength;
        int cursorX = endX;
        if (!cursorVisible) {
            cursorX = cursor > 0 ? x + editorWidth() : x;
        } else if (insertCursor) {
            cursorX = endX - 1;
            endX--;
        }
        if (!visible.isEmpty() && cursorVisible && cursor < visible.length()) {
            graphics.drawString(font, formatEditorText(visible.substring(cursor), editor.getCursorPos()),
                    endX, y, style.textColor(), shadow);
        }
        if (!ae2Frame && visible.isEmpty() && !isFocused()) {
            graphics.drawString(font, placeholder, endX, y, style.textColor(), shadow);
        }
        String suggestion = accessor == null ? null : accessor.ae2lt$getEditorSuggestion();
        if (!insertCursor && suggestion != null) {
            graphics.drawString(font, suggestion, cursorX - 1, y, -8355712, shadow);
        }
        if (blink) {
            if (insertCursor) {
                graphics.fill(RenderType.guiOverlay(), cursorX, y - 1, cursorX + 1, y + 10, -3092272);
            } else {
                graphics.drawString(font, "_", cursorX, y, style.textColor(), shadow);
            }
        }
        if (anchor != cursor) {
            int anchorX = x + font.width(visible.substring(0, anchor)) - 1;
            drawEditorSelection(context, cursorX, anchorX, x, y);
        }
        if (ae2Frame && value.isEmpty() && !isFocused()) {
            graphics.drawString(Minecraft.getInstance().font, placeholder, x, y, placeholderColor, false);
        }
    }

    private void drawEditorSelection(GUIContext context, int cursorX, int anchorX, int x, int y) {
        int right = Math.max(cursorX, anchorX);
        int left = Math.min(cursorX, anchorX);
        if (ae2Frame) {
            if (!isFocused()) {
                return;
            }
            right = Math.clamp(right + 1, x, x + editorWidth());
            left = Math.clamp(left - 1, x, x + editorWidth());
            // AETextField uses the screen palette with OR_REVERSE, not guiTextHighlight's blue.
            context.graphics.flush();
            RenderSystem.enableColorLogicOp();
            try {
                RenderSystem.logicOp(GlStateManager.LogicOp.OR_REVERSE);
                context.graphics.fill(right, y + 8, left, y - 1, selectionColor);
                context.graphics.flush();
            } finally {
                RenderSystem.disableColorLogicOp();
            }
        } else {
            context.graphics.fill(RenderType.guiTextHighlight(), Math.min(right, x + editorWidth()), y + 10,
                    Math.min(left, x + editorWidth()), y - 1, -16776961);
        }
    }

    protected boolean hasRejectedEdit() {
        return field.isError();
    }

    public String getValue() {
        return field.getValue();
    }

    public void setValue(String value) {
        setValue(value, true);
    }

    void setValue(String value, boolean notify) {
        if (filter.test(value)) {
            field.setText(value.substring(0, Math.min(value.length(), maxLength)), false);
            field.setCursor(getValue().length());
            setSelection(field.getCursorPos(), field.getCursorPos());
            if (notify) {
                // EditBox.moveCursorToEnd notifies the displayed value first. setValue
                // then collapses any selection made by that callback and reports its argument.
                notifyResponder(getValue());
                setSelection(field.getCursorPos(), field.getCursorPos());
                notifyResponder(value);
            }
        }
    }

    public void setMaxLength(int maxLength) {
        setMaxLength(maxLength, true);
    }

    void setMaxLength(int maxLength, boolean notify) {
        this.maxLength = maxLength;
        if (field instanceof BoundedTextField bounded) {
            bounded.limit = maxLength;
        }
        if (getValue().length() > maxLength) {
            field.setText(getValue().substring(0, maxLength), notify);
        }
    }

    void bindNativeModel(EditBox model) {
        bindNativeModel(model, false);
    }

    void bindNativeModel(EditBox model, boolean nativeOwnsFocus) {
        nativeModel = model;
        this.nativeOwnsFocus = nativeOwnsFocus;
        var accessor = (EditBoxAccessor) (Object) model;
        setFilter(value -> accessor.ae2lt$getEditorFilter().test(value));
        setResponder(ignored -> {
            String value = getValue();
            if (!model.getValue().equals(value) && !accessor.ae2lt$getEditorFilter().test(value)) {
                syncFromNativeModel();
                return;
            }
            accessor.ae2lt$setEditorValue(value);
            syncNativeSelection();
            accessor.ae2lt$notifyEditorChange(value);
            syncFromNativeModel();
        });
    }

    void syncFromNativeModel() {
        var model = nativeModel;
        if (model != null) {
            var accessor = (EditBoxAccessor) (Object) model;
            int modelMaxLength = accessor.ae2lt$getEditorMaxLength();
            if (maxLength != modelMaxLength) {
                setMaxLength(modelMaxLength, false);
            }
            if (!getValue().equals(model.getValue())) {
                setValue(model.getValue(), false);
            }
            field.setCursor(model.getCursorPosition());
            field.setSelection(accessor.ae2lt$getEditorAnchor(), field.getCursorPos());
            if (field instanceof BoundedTextField bounded) {
                bounded.displayPos = accessor.ae2lt$getEditorDisplayPos();
            }
        }
    }

    private void syncNativeSelection() {
        var model = nativeModel;
        if (model != null) {
            var accessor = (EditBoxAccessor) (Object) model;
            accessor.ae2lt$setEditorCursor(field.getCursorPos());
            accessor.ae2lt$setEditorAnchor(field.getSelectionStart());
            if (field instanceof BoundedTextField bounded) {
                accessor.ae2lt$setEditorDisplayPos(bounded.displayPos);
            }
        }
    }

    private void notifyResponder(String value) {
        if (responder != null) {
            responder.accept(value);
        }
    }

    public void setFilter(Predicate<String> filter) {
        this.filter = filter;
        if (field instanceof BoundedTextField bounded) {
            bounded.editFilter = filter;
        }
    }

    public void setResponder(@Nullable Consumer<String> responder) {
        this.responder = responder;
    }

    public void setPlaceholder(Component placeholder) {
        this.placeholder = framedTextColor == null ? placeholder : placeholder.copy().withColor(placeholderColor & 0xFFFFFF);
        refreshPlaceholder();
    }

    @Override
    protected MutableComponent createNarrationMessage() {
        var model = nativeModel;
        return Component.translatable("gui.narrate.editBox",
                model == null ? getMessage() : model.getMessage(),
                model == null ? getValue() : model.getValue());
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, createNarrationMessage());
    }

    private void refreshPlaceholder() {
        var shown = framedTextColor != null && isFocused() ? Component.empty() : placeholder;
        field.textFieldStyle(style -> style.placeholder(shown));
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public void setEditable(boolean editable) {
        setEditable(editable, true);
    }

    public void setEditable(boolean editable, boolean allowReadOnlySelection) {
        boolean changed = this.editable != editable;
        this.editable = editable;
        if (field instanceof BoundedTextField bounded) {
            bounded.readOnly = !editable;
        }
        if (!allowReadOnlySelection) {
            active = editable;
        }
        if (changed && framedTextColor != null) {
            setTextColor(editable ? framedTextColor : 0xFF707070);
        }
    }

    public void setTooltipMessage(List<Component> tooltip) {
        this.tooltip = List.copyOf(tooltip);
    }

    @Override
    public List<Component> getTooltipMessage() {
        return tooltip;
    }

    @Override
    public boolean isTooltipAreaVisible() {
        return visible && !tooltip.isEmpty();
    }

    @Override
    protected boolean handlesInput() {
        return true;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return active && super.isMouseOver(mouseX, mouseY);
    }

    @Override
    public void setFocused(boolean focused) {
        boolean changed = isFocused() != focused;
        super.setFocused(focused);
        if (focused) {
            focusedTime = Util.getMillis();
            modularUI().requestFocus(field);
        } else {
            int cursor = field.getCursorPos();
            int selectionStart = field.getSelectionStart();
            int selectionEnd = field.getSelectionEnd();
            var bounded = field instanceof BoundedTextField value ? value : null;
            int displayPos = bounded == null ? 0 : bounded.displayPos;
            modularUI().clearFocus();
            // Vanilla EditBox preserves its selection and viewport when focus moves away.
            field.setCursor(cursor);
            field.setSelection(selectionStart, selectionEnd);
            if (bounded != null) {
                bounded.displayPos = displayPos;
            }
        }
        if (changed && framedTextColor != null) {
            refreshPlaceholder();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !active || !isMouseOver(mouseX, mouseY)) {
            return false;
        }
        if (framedTextColor != null) {
            int left = getX() + FIELD_PADDING;
            int top = getY() + FIELD_PADDING;
            mouseX = Math.clamp(mouseX, left,
                    Math.max(left, getX() + getWidth() - FIELD_PADDING - cursorPadding - 1));
            mouseY = Math.clamp(mouseY, top, Math.max(top, getY() + getHeight() - FIELD_PADDING - 1));
        }
        super.mouseClicked(mouseX, mouseY, button);
        notifyResponder(getValue());
        syncNativeSelection();
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        // EditBox inherits AbstractWidget's no-op drag, which still consumes the left button.
        return isValidClickButton(button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!isValidClickButton(button)) {
            return false;
        }
        super.mouseReleased(mouseX, mouseY, button);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isFocused() || !active || !visible) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            // A registered LDLib listener alone does not prove that a key was handled.
            return false;
        }
        if (Screen.isSelectAll(keyCode)) {
            field.setCursor(getValue().length());
            setSelection(field.getCursorPos(), field.getCursorPos());
            syncNativeSelection();
            notifyResponder(getValue());
            setSelection(0, field.getCursorPos());
            syncNativeSelection();
            return true;
        }
        if (!editable) {
            return handleReadOnlyKey(keyCode, scanCode, modifiers);
        }
        boolean navigationKey = switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_HOME, GLFW.GLFW_KEY_END -> true;
            default -> false;
        };
        boolean editingKey = navigationKey || Screen.isCopy(keyCode)
                || Screen.isCut(keyCode) || Screen.isPaste(keyCode)
                || keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE;
        if (editingKey) {
            modularUI().getWidget().keyPressed(keyCode, scanCode, modifiers);
            if (navigationKey) {
                if (!Screen.hasShiftDown()) {
                    scrollToAnchor();
                }
                notifyResponder(getValue());
            }
            syncNativeSelection();
            return true;
        }
        // Keep LDLib undo/redo and other editor commands out of vanilla fields.
        return framedTextColor != null;
    }

    private boolean handleReadOnlyKey(int keyCode, int scanCode, int modifiers) {
        if (Screen.isCopy(keyCode) || Screen.isCut(keyCode)) {
            Minecraft.getInstance().keyboardHandler.setClipboard(field.getHighlighted());
            return true;
        }
        if (Screen.isPaste(keyCode)) {
            return true;
        }
        return switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_HOME, GLFW.GLFW_KEY_END -> {
                modularUI().getWidget().keyPressed(keyCode, scanCode, modifiers);
                if (!Screen.hasShiftDown()) {
                    scrollToAnchor();
                }
                notifyResponder(getValue());
                syncNativeSelection();
                yield true;
            }
            case GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_KEY_DELETE -> true;
            default -> false;
        };
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        return isFocused() && active && visible && editable
                && StringUtil.isAllowedChatCharacter(character)
                && modularUI().getWidget().charTyped(character, modifiers);
    }

    private static final class BoundedTextField extends TextField {
        private @Nullable LdlibAe2TextField owner;
        private int displayPos;
        private int limit = 32;
        private Predicate<String> editFilter = value -> true;
        private boolean readOnly;

        private Font editorFont() {
            return owner == null ? Minecraft.getInstance().font : owner.editorFont();
        }

        private int editorWidth() {
            return owner == null ? Math.max(0, (int) getContentWidth()) : owner.editorWidth();
        }

        private void scrollTo(int position) {
            String value = getRawText();
            displayPos = Math.min(displayPos, value.length());
            if (value.isEmpty()) {
                displayPos = 0;
                return;
            }
            Font font = editorFont();
            int width = editorWidth();
            int end = displayPos + font.plainSubstrByWidth(value.substring(displayPos), width).length();
            if (position == displayPos) {
                displayPos -= font.plainSubstrByWidth(value, width, true).length();
            }
            if (position > end) {
                displayPos += position - end;
            } else if (position <= displayPos) {
                displayPos = position;
            }
            displayPos = Math.clamp(displayPos, 0, value.length());
        }

        @Override
        public void setCursor(int cursor) {
            super.setCursor(cursor);
            scrollTo(getCursorPos());
        }

        @Override
        protected void onRawTextUpdate() {
            // Accepted insertion/deletion sets cursor and anchor before the original responder.
            scrollTo(getCursorPos());
            scrollTo(getSelectionStart());
            super.onRawTextUpdate();
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            if (owner == null) {
                super.drawBackgroundAdditional(context);
            } else {
                owner.drawEditorContents(context, this);
            }
        }

        @Override
        public int getCursorUnderMouseX(double mouseX) {
            int width = (int) Math.floor(mouseX) - (int) getContentX();
            String visible = editorFont().plainSubstrByWidth(getRawText().substring(displayPos), editorWidth());
            return displayPos + editorFont().plainSubstrByWidth(visible, width).length();
        }

        @Override
        protected void onMouseDown(UIEvent event) {
            if (event.button != 0) {
                return;
            }
            boolean selecting = Screen.hasShiftDown();
            int cursor = getCursorUnderMouseX(getLocalMouse(event.x, event.y).x);
            int anchor = selecting ? getSelectionStart() : cursor;
            setCursor(cursor);
            setSelection(anchor, getCursorPos());
            if (!selecting) {
                scrollTo(anchor);
            }
        }

        @Override
        public boolean isEditable() {
            return !readOnly && super.isEditable();
        }

        @Override
        public void insertText(String text) {
            String current = getRawText();
            boolean selected = getSelectionStart() != getSelectionEnd();
            int start = selected ? Math.min(getSelectionStart(), getSelectionEnd()) : getCursorPos();
            int end = selected ? Math.max(getSelectionStart(), getSelectionEnd()) : start;
            int available = limit - current.length() + end - start;
            if (available <= 0) {
                return;
            }
            String filtered = StringUtil.filterText(text);
            if (filtered.length() > available) {
                int cut = Character.isHighSurrogate(filtered.charAt(available - 1)) ? available - 1 : available;
                filtered = filtered.substring(0, cut);
            }
            String next = current.substring(0, start) + filtered + current.substring(end);
            if (editFilter.test(next)) {
                boolean unchanged = current.equals(next);
                super.insertText(filtered);
                if (unchanged) {
                    notifyListeners();
                }
            }
        }

        @Override
        public void deleteCharsToPos(int position) {
            if (getSelectionStart() != getSelectionEnd()) {
                insertText("");
                return;
            }
            int cursor = getCursorPos();
            int start = Math.min(position, cursor);
            if (position != cursor && allowsReplacement(start, Math.max(position, cursor), "")) {
                setSelection(start, start);
                super.deleteCharsToPos(position);
            }
        }

        private boolean allowsReplacement(int start, int end, String replacement) {
            String current = getRawText();
            return editFilter.test(current.substring(0, start) + replacement + current.substring(end));
        }
    }
}
