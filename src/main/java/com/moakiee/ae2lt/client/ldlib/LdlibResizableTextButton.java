package com.moakiee.ae2lt.client.ldlib;

import appeng.client.gui.widgets.AE2Button;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Keeps the AE2 button contract when a screen assigns dimensions after widget creation. */
public final class LdlibResizableTextButton extends AE2Button {
    private LdlibAe2Button view;
    private final int labelYOffset;

    public LdlibResizableTextButton(Component label, Runnable action) {
        this(label, action, 0);
    }

    public LdlibResizableTextButton(Component label, Runnable action, int labelYOffset) {
        super(label, ignored -> action.run());
        this.labelYOffset = labelYOffset;
    }

    private LdlibAe2Button view() {
        if (view == null || view.getWidth() != getWidth() || view.getHeight() != getHeight()) {
            var previous = view;
            view = LdlibAe2Button.text(getWidth(), getHeight(), getMessage(), this::onPress, labelYOffset);
            if (previous != null) {
                previous.dispose();
            }
        }
        view.setMessage(getMessage());
        view.setPosition(getX(), getY());
        view.setFocused(isFocused());
        view.setAlpha(alpha);
        view.active = active;
        view.visible = visible;
        return view;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return isValidClickButton(button) && isMouseOver(mouseX, mouseY)
                && view().mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (view != null && isValidClickButton(button)) {
            view.mouseReleased(mouseX, mouseY, button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float ticks) {
        view().render(graphics, mouseX, mouseY, ticks);
    }
}
