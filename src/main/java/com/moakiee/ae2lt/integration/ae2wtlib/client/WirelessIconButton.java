package com.moakiee.ae2lt.integration.ae2wtlib.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.math.Size;
import com.moakiee.ae2lt.client.ldlib.LdlibAe2Button;
import com.moakiee.ae2lt.client.ldlib.LdlibScreens;
import com.moakiee.ae2lt.client.ldlib.LdlibStyleSprite;
import de.mari_023.ae2wtlib.api.gui.Icon;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

public final class WirelessIconButton extends LdlibAe2Button {
    private record Frames(Icon normal, Icon focused, Icon hovered) {}

    private static final Map<Icon, SpriteTexture> ICONS = new HashMap<>();
    private static final Frames BUTTON = new Frames(
            Icon.BUTTON_BACKGROUND, Icon.BUTTON_BACKGROUND_FOCUSED, Icon.BUTTON_BACKGROUND_HOVERED);
    private final Supplier<List<Component>> tooltip;
    private final Supplier<Icon> icon;
    private final Frames frames;
    private final UIElement glyph = new UIElement().setAllowHitTest(false);
    private Icon displayedIcon;
    private Icon displayedFrame;

    WirelessIconButton(Icon icon, Component label, Runnable action) {
        this(() -> icon, () -> List.of(label), action, BUTTON);
    }

    WirelessIconButton(Supplier<Icon> icon, Supplier<Component> label, Runnable action) {
        this(icon, () -> List.of(label.get()), action, BUTTON);
    }

    private WirelessIconButton(Supplier<Icon> icon, Supplier<List<Component>> tooltip,
                               Runnable action, Frames frames) {
        super(16, tooltip.get().getFirst(), action);
        this.tooltip = tooltip;
        this.icon = icon;
        this.frames = frames;
        control.buttonStyle(style -> style.baseTexture(IGuiTexture.EMPTY)
                .hoverTexture(IGuiTexture.EMPTY).pressedTexture(IGuiTexture.EMPTY));

        addFrame(frames.normal(), 0);
        addFrame(frames.focused(), 0);
        addFrame(frames.hovered(), 1);
        control.addChild(glyph);
    }

    @Override
    public List<Component> getTooltipMessage() {
        return tooltip.get();
    }

    @Override
    public Rect2i getTooltipArea() {
        var frame = currentFrame();
        return new Rect2i(getX(), getY(), frame.width(), frame.height());
    }

    static SpriteTexture sprite(Icon icon) {
        var atlas = icon.texture();
        return new LdlibStyleSprite(atlas.location(), Size.of(atlas.width(), atlas.height()),
                icon.x(), icon.y(), icon.width(), icon.height()).full();
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        refreshGlyph();
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
    }

    private void refreshGlyph() {
        var current = icon.get();
        var frame = currentFrame();
        if (!current.equals(displayedIcon) || frame != displayedFrame) {
            displayedFrame = frame;
            int inset = frame.width() > 16 ? 1 : 0;
            LdlibScreens.at(glyph, inset - 1, inset + (isHovered() ? 1 : 0), current.width(), current.height());
        }
        if (!current.equals(displayedIcon)) {
            displayedIcon = current;
            glyph.style(style -> style.background(atDepth(ICONS.computeIfAbsent(current, WirelessIconButton::sprite), 3)));
        }
    }

    private Icon currentFrame() {
        return isHovered() ? frames.hovered() : isFocused() ? frames.focused() : frames.normal();
    }

    private void addFrame(Icon frame, int top) {
        var face = atDepth(ICONS.computeIfAbsent(frame, WirelessIconButton::sprite), 2);
        var texture = IGuiTexture.dynamic(() -> currentFrame() == frame ? face : IGuiTexture.EMPTY);
        var element = new UIElement().setAllowHitTest(false).style(style -> style.background(texture));
        control.addChild(LdlibScreens.at(element, -1, top, frame.width(), frame.height()));
    }

    private static IGuiTexture atDepth(IGuiTexture texture, int depth) {
        return (graphics, mouseX, mouseY, x, y, width, height, ticks) -> {
            var pose = graphics.pose();
            pose.pushPose();
            try {
                pose.translate(0, 0, depth);
                texture.draw(graphics, mouseX, mouseY, x, y, width, height, ticks);
            } finally {
                pose.popPose();
            }
        };
    }
}
