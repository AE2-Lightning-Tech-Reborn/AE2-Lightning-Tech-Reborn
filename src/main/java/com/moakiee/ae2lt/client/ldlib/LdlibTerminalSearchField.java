package com.moakiee.ae2lt.client.ldlib;

import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AETextField;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/** Keeps the AE2 terminal's search contract while LDLib handles text editing. */
public final class LdlibTerminalSearchField extends AETextField {
    private static final int MAX_SEARCH_LENGTH = 25;

    private final int fontPadding;
    private final int placeholderColor;
    private @Nullable SearchControl input;
    private boolean editable = true;
    private int maxLength = MAX_SEARCH_LENGTH;
    private int editableTextColor;
    private int readOnlyTextColor = 0xFF707070;
    private int selectionColor;

    public LdlibTerminalSearchField(ScreenStyle style, Font font) {
        super(style, font, 0, 0, 0, 0);
        fontPadding = font.width("_");
        placeholderColor = style.getColor(PaletteColor.TEXTFIELD_PLACEHOLDER).toARGB();
        setBordered(false);
        setMaxLength(MAX_SEARCH_LENGTH);
        setTextColor(0xFFFFFF);
    }

    @Override
    public void setValue(String value) {
        super.setValue(value);
        if (input != null) {
            input.setValue(getValue(), false);
            input.syncFromNativeModel();
        }
    }

    @Override
    public void setMaxLength(int length) {
        maxLength = length;
        super.setMaxLength(length);
        if (input != null) {
            input.setMaxLength(length, false);
            input.syncFromNativeModel();
        }
    }

    @Override
    public void setPlaceholder(Component placeholder) {
        super.setPlaceholder(placeholder);
        syncPlaceholder();
    }

    @Override
    public void selectAll() {
        super.selectAll();
        if (input != null) {
            input.syncFromNativeModel();
        }
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (input != null) {
            input.setFocused(isFocused());
        }
        syncPlaceholder();
    }

    @Override
    public void setEditable(boolean editable) {
        super.setEditable(editable);
        this.editable = editable;
        if (input != null) {
            input.setEditable(editable, true);
            input.setFocused(isFocused());
            syncTextColor();
        }
    }

    @Override
    public void setTextColor(int color) {
        super.setTextColor(color);
        editableTextColor = color;
        syncTextColor();
    }

    @Override
    public void setTextColorUneditable(int color) {
        super.setTextColorUneditable(color);
        readOnlyTextColor = color;
        syncTextColor();
    }

    @Override
    public void setSelectionColor(int color) {
        super.setSelectionColor(color);
        selectionColor = color;
        if (input != null) {
            input.setSelectionColor(color);
        }
    }

    private void syncTextColor() {
        if (input != null) {
            int color = editable ? editableTextColor : readOnlyTextColor;
            input.setTextColor(0xFF000000 | color);
        }
    }

    private void syncPlaceholder() {
        if (input != null) {
            input.setPlaceholder(isFocused() ? Component.empty() : tintPlaceholder(getPlaceholder()));
        }
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float ticks) {
        if (isVisible()) {
            editor().render(graphics, mouseX, mouseY, ticks);
        }
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        return isVisible() && isMouseOver(x, y) && editor().mouseClicked(
                Math.clamp(x, getX(), getX() + Math.max(0, getWidth() - 1)),
                Math.clamp(y, getY(), getY() + Math.max(0, getHeight() - 1)), button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double deltaX, double deltaY) {
        return super.mouseDragged(x, y, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (input != null) {
            input.mouseReleased(x, y, button);
        }
        return super.mouseReleased(x, y, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return isVisible() && isFocused() && (editor().keyPressed(keyCode, scanCode, modifiers)
                || (canConsumeInput() && keyCode != GLFW.GLFW_KEY_TAB && keyCode != GLFW.GLFW_KEY_ESCAPE));
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        return isVisible() && isFocused() && editor().charTyped(character, modifiers);
    }

    private SearchControl editor() {
        int width = Math.max(2, getWidth() + 4 + fontPadding);
        int height = Math.max(1, getHeight() + 4);
        if (input == null || input.getWidth() != width || input.getHeight() != height) {
            var previous = input;
            input = new SearchControl(width, height, fontPadding);
            input.setMaxLength(maxLength, false);
            input.setEditable(editable, true);
            syncTextColor();
            input.setValue(getValue(), false);
            input.bindNativeModel(this, true);
            input.setSelectionColor(selectionColor);
            input.syncFromNativeModel();
            input.setFocused(isFocused());
            syncPlaceholder();
            if (previous != null) {
                previous.dispose();
            }
        }
        input.setPosition(getX() - 2, getY() - 2);
        input.active = active;
        input.syncFromNativeModel();
        return input;
    }

    private Component tintPlaceholder(@Nullable Component placeholder) {
        return placeholder == null ? Component.empty()
                : placeholder.copy().withStyle(style -> style.withColor(placeholderColor & 0xFFFFFF));
    }

    private static final class SearchControl extends LdlibAe2TextField {
        SearchControl(int width, int height, int fontPadding) {
            super(width, height, true);
            useAe2Frame(fontPadding);
        }

    }
}
