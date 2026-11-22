package com.moakiee.ae2lt.client.ldlib;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.function.Consumer;

import appeng.client.Point;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.NumberEntryType;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ConfirmableTextField;
import appeng.client.gui.widgets.NumberEntryWidget;
import appeng.core.localization.GuiText;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.moakiee.ae2lt.client.widgets.BigNumberEntry;
import com.moakiee.ae2lt.mixin.client.ScreenAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/** LDLib2 input and step controls backed by AE2's quantity parser and unit conversion. */
public final class LdlibNumberEntryWidget extends NumberEntryWidget {
    private static final int NORMAL_INPUT_LENGTH = 16;
    private static final int BIG_INPUT_LENGTH = 5000;
    private static final List<Component> INVALID_NUMBER = List.of(GuiText.InvalidNumber.text());

    private record StepButton(Button action, LdlibAe2Button control) {
        void refresh() {
            control.setMessage(action.getMessage());
            control.active = action.active;
            control.visible = action.visible;
        }
    }

    private final int textColor;
    private final int errorColor;
    private final int selectionColor;
    private final boolean largeAmounts;
    private final List<StepButton> steps = new ArrayList<>();
    private boolean extendedRange;
    private boolean entryActive = true;
    private @Nullable ConfirmableTextField modelField;
    private @Nullable AmountField input;
    private @Nullable Rect2i fieldBounds;
    private @Nullable ModularUI unitLayer;
    private @Nullable Runnable confirm;

    public LdlibNumberEntryWidget(ScreenStyle style, NumberEntryType type, boolean largeAmounts) {
        super(style, type);
        this.largeAmounts = largeAmounts;
        textColor = style.getColor(PaletteColor.TEXTFIELD_TEXT).toARGB();
        errorColor = style.getColor(PaletteColor.TEXTFIELD_ERROR).toARGB();
        selectionColor = style.getColor(PaletteColor.TEXTFIELD_SELECTION).toARGB();
        setHideValidationIcon(true);
        if (largeAmounts) {
            setExtendedRange(true, true);
        }
    }

    public void setExtendedRange(boolean enabled, boolean exact) {
        extendedRange = enabled;
        ((BigNumberEntry) (Object) this).ae2lt$setExtendedRange(enabled, exact);
    }

    private boolean isValidAmount() {
        return extendedRange ? ((BigNumberEntry) (Object) this).ae2lt$getBig().isPresent()
                : getLongValue().isPresent();
    }

    @Override
    public void populateScreen(Consumer<AbstractWidget> add, Rect2i screen, AEBaseScreen<?> owner) {
        releaseUnitLayer();
        for (var step : steps) {
            step.control.dispose();
        }
        steps.clear();
        super.populateScreen(widget -> {
            if (widget instanceof ConfirmableTextField text) {
                modelField = text;
                var previous = input;
                input = new AmountField(text, Minecraft.getInstance().font.width("_"));
                input.setPosition(text.getX() - 2, text.getY() - 2);
                input.setMaxLength(largeAmounts ? BIG_INPUT_LENGTH : NORMAL_INPUT_LENGTH, false);
                input.setValue(text.getValue(), false);
                input.bindNativeModel(text);
                input.restoreModelSelection();
                if (previous != null) {
                    previous.dispose();
                }
                add.accept(input);
            } else if (widget instanceof Button button) {
                var control = LdlibAe2Button.textAt(button.getX(), button.getY(),
                        button.getWidth(), button.getHeight(), button.getMessage(), ignored -> {
                            button.onPress();
                            refreshInput();
                        });
                var step = new StepButton(button, control);
                step.refresh();
                steps.add(step);
                add.accept(control);
            }
        }, screen, owner);
        setActive(entryActive);
        if (input != null) {
            ((ScreenAccessor) (Object) owner).ae2lt$setInitialFocus(input);
            refreshInput();
        }
    }

    public boolean hasRejectedEdit() {
        return input != null && input.hasRejectedEdit();
    }

    @Override
    public void setActive(boolean active) {
        entryActive = active;
        super.setActive(active);
        if (input != null) {
            input.setEditable(active, true);
            input.refreshTextColor();
        }
        for (var step : steps) {
            step.refresh();
        }
    }

    @Override
    public void setLongValue(long value) {
        super.setLongValue(value);
        refreshInput();
        if (input != null) {
            input.restoreModelSelection();
        }
    }

    @Override
    public OptionalLong getLongValue() {
        return hasRejectedEdit() ? OptionalLong.empty() : super.getLongValue();
    }

    @Override
    public void setTextFieldBounds(Rect2i bounds) {
        super.setTextFieldBounds(bounds);
        fieldBounds = bounds;
        releaseUnitLayer();
    }

    public void releaseUnitLayer() {
        var previous = unitLayer;
        unitLayer = null;
        if (previous != null) {
            previous.onRemoved();
        }
    }

    @Override
    public void setOnConfirm(Runnable confirm) {
        this.confirm = confirm;
    }

    @Override
    public boolean onMouseWheel(Point mouse, double delta) {
        if (super.onMouseWheel(mouse, delta)) {
            refreshInput();
            return true;
        }
        return false;
    }

    @Override
    public void updateBeforeRender() {
        super.updateBeforeRender();
        for (var step : steps) {
            step.refresh();
        }
        refreshInput();
    }

    private void refreshInput() {
        if (input == null || modelField == null) {
            return;
        }
        input.syncFromNativeModel();
        if (hasRejectedEdit()) {
            input.updateValidation(false, INVALID_NUMBER);
        } else {
            var hints = modelField.getTooltipMessage();
            if (input.validationHints != hints) {
                input.validationHints = hints;
                input.valid = isValidAmount();
            }
            input.updateValidation(input.valid, hints);
        }
    }

    @Override
    public void drawBackgroundLayer(GuiGraphics graphics, Rect2i screen, Point mouse) {
        var unit = getType().unit();
        if (unit == null || fieldBounds == null) {
            return;
        }
        if (unitLayer == null || unitLayer.getScreenWidth() != screen.getWidth()
                || unitLayer.getScreenHeight() != screen.getHeight()) {
            releaseUnitLayer();
            var root = new UIElement();
            var font = Minecraft.getInstance().font;
            int width = font.width(unit);
            int x = fieldBounds.getX() + fieldBounds.getWidth() - width;
            int y = (int) (fieldBounds.getY() + (fieldBounds.getHeight() - font.lineHeight) / 2f + 1);
            var label = LdlibScreens.addLabel(root, Component.literal(unit),
                    0xFF000000 | ChatFormatting.DARK_GRAY.getColor(), x, y, width + 1);
            label.textStyle(style -> style.textWrap(TextWrap.NONE));
            unitLayer = LdlibScreens.localLayer(root, screen.getWidth(), screen.getHeight());
        }
        var pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(screen.getX(), screen.getY(), 0);
            unitLayer.getWidget().render(graphics, mouse.getX(), mouse.getY(), 0);
        } finally {
            pose.popPose();
        }
    }

    private final class AmountField extends LdlibAe2TextField {
        private final ConfirmableTextField model;
        private int editableColor = textColor;
        private int color;
        private boolean valid;
        private @Nullable List<Component> validationHints;

        AmountField(ConfirmableTextField model, int cursorPadding) {
            super(Math.max(2, model.getWidth() + 4 + cursorPadding),
                    Math.max(1, model.getHeight() + 4), true);
            this.model = model;
            useAe2Frame(cursorPadding);
            setSelectionColor(LdlibNumberEntryWidget.this.selectionColor);
        }

        void restoreModelSelection() {
            syncFromNativeModel();
        }

        @Override
        public boolean mouseClicked(double x, double y, int button) {
            return isMouseOver(x, y) && super.mouseClicked(
                    Math.clamp(x, model.getX(), model.getX() + Math.max(0, model.getWidth() - 1)),
                    Math.clamp(y, model.getY(), model.getY() + Math.max(0, model.getHeight() - 1)), button);
        }

        void updateValidation(boolean valid, List<Component> tooltip) {
            editableColor = valid ? textColor : errorColor;
            refreshTextColor();
            setTooltipMessage(tooltip);
        }

        private void refreshTextColor() {
            int next = entryActive ? editableColor : 0xFF707070;
            if (color != next) {
                color = next;
                setTextColor(color);
            }
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (!isFocused() || !active || !visible) {
                return false;
            }
            if (!entryActive) {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                if (confirm != null && !hasRejectedEdit() && isValidAmount()) {
                    confirm.run();
                }
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers)
                    || (keyCode != GLFW.GLFW_KEY_TAB && keyCode != GLFW.GLFW_KEY_ESCAPE);
        }
    }

}
