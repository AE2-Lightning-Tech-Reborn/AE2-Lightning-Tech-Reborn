package com.moakiee.ae2lt.client.ldlib;

import appeng.client.gui.WidgetContainer;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import com.lowdragmc.lowdraglib2.gui.LDLibFonts;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** LDLib toggle positioned by the owning AE2 screen's widget style. */
public final class LdlibAe2Checkbox extends LdlibAe2Widget {
    private static final LdlibStyleSprite ATLAS = new LdlibStyleSprite(
            ResourceLocation.fromNamespaceAndPath("ae2", "textures/guis/checkbox.png"), 64, 64);
    private static final int SWITCH_WIDTH = 22;
    private static final int SWITCH_HEIGHT = 12;
    private static final int LABEL_X = 26;

    private final Toggle toggle;
    private Runnable listener;

    private LdlibAe2Checkbox(int width, int height, ScreenStyle style, Component label, Runnable listener) {
        this(width, height, style, label, listener, new Toggle());
    }

    private LdlibAe2Checkbox(int width, int height, ScreenStyle style, Component label, Runnable listener, Toggle toggle) {
        super(width, height, toggle);
        this.toggle = toggle;
        this.listener = listener;
        setMessage(label);
        toggle.noText().layout(layout -> layout.paddingAll(0));
        toggle.toggleStyle(t -> t.baseTexture(IGuiTexture.EMPTY).hoverTexture(IGuiTexture.EMPTY)
                .unmarkTexture(IGuiTexture.EMPTY).markTexture(IGuiTexture.EMPTY));
        toggle.toggleButton(button -> {
            LdlibScreens.at(button, 0, 0, width, height);
            button.buttonStyle(b -> b.pressedTexture(IGuiTexture.EMPTY));
        });
        toggle.markIcon(mark -> mark.setDisplay(false));
        var unchecked = ATLAS.slice(0, 28, SWITCH_WIDTH, SWITCH_HEIGHT);
        var uncheckedHover = ATLAS.slice(SWITCH_WIDTH, 28, SWITCH_WIDTH, SWITCH_HEIGHT);
        var checked = ATLAS.slice(0, 40, SWITCH_WIDTH, SWITCH_HEIGHT);
        var checkedHover = ATLAS.slice(SWITCH_WIDTH, 40, SWITCH_WIDTH, SWITCH_HEIGHT);
        var uncheckedMuted = unchecked.copy().setColor(0x7FFFFFFF);
        var checkedMuted = checked.copy().setColor(0x7FFFFFFF);
        int normalText = style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB();
        int mutedText = style.getColor(PaletteColor.MUTED_TEXT_COLOR).toARGB();
        toggle.style(s -> s.background((graphics, mouseX, mouseY, x, y, w, h, ticks) -> {
            boolean hover = !isFocused() && isMouseOver(getX() + mouseX, getY() + mouseY);
            var face = active
                    ? (isSelected() ? (hover ? checkedHover : checked) : (hover ? uncheckedHover : unchecked))
                    : (isSelected() ? checkedMuted : uncheckedMuted);
            face.draw(graphics, mouseX, mouseY, x, y, SWITCH_WIDTH, SWITCH_HEIGHT, ticks);

            var font = Minecraft.getInstance().font;
            var lines = font.split(getMessage(), Math.max(1, getWidth() - SWITCH_WIDTH));
            float lineY = y + (lines.size() > 1 ? 1 : 4);
            for (var line : lines) {
                LDLibFonts.drawText(graphics, font, line, x + LABEL_X, lineY, active ? normalText : mutedText, false);
                lineY += font.lineHeight;
            }
        }));
        toggle.setOnToggleChanged(selected -> {
            if (this.listener != null) {
                this.listener.run();
            }
        });
        toggle.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button == 0) {
                event.stopPropagation();
            }
        });
    }

    public static LdlibAe2Checkbox add(WidgetContainer widgets, ScreenStyle style, String id,
            Component label, Runnable listener) {
        var bounds = style.getWidget(id);
        int height = bounds.getHeight() != 0 ? bounds.getHeight() : 14;
        int width = bounds.getWidth();
        var result = new LdlibAe2Checkbox(width, height, style, label, listener);
        widgets.add(id, result);
        return result;
    }

    public boolean isSelected() {
        return toggle.isOn();
    }

    public void setSelected(boolean selected) {
        toggle.setOn(selected, false);
    }

    public void setChangeListener(Runnable listener) {
        this.listener = listener;
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, createNarrationMessage());
        if (active) {
            output.add(NarratedElementType.USAGE, Component.translatable(isFocused()
                    ? "narration.checkbox.usage.focused" : "narration.checkbox.usage.hovered"));
        }
    }

    @Override
    protected boolean handlesInput() {
        return true;
    }

    @Override
    public void onPress() {
        if (active && visible) {
            toggle.setOn(!isSelected(), true);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        toggle.setActive(active);
        return isValidClickButton(button) && super.mouseClicked(mouseX, mouseY, button);
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
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        toggle.setActive(active);
        if (!isMouseOver(mouseX, mouseY)) {
            setFocused(false);
        }
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
    }
}
