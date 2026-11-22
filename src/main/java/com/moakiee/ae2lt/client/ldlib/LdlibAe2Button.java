package com.moakiee.ae2lt.client.ldlib;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import appeng.client.gui.Icon;
import appeng.client.gui.WidgetContainer;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ActionButton;
import appeng.api.config.ActionItems;
import appeng.core.localization.GuiText;
import appeng.core.network.serverbound.SwitchGuisPacket;
import appeng.menu.implementations.PriorityMenu;
import com.lowdragmc.lowdraglib2.gui.LDLibFonts;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.VanillaSpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/** An LDLib button hosted at an AE2 widget position. */
public class LdlibAe2Button extends LdlibAe2Widget {
    private final Consumer<LdlibAe2Button> action;
    protected final Button control;
    private UIElement toolbarFrame;
    private UIElement toolbarGlyph;

    protected LdlibAe2Button(int size, Button button, Component label, Runnable action) {
        this(size, size, button, label, ignored -> action.run());
    }

    private LdlibAe2Button(int width, int height, Button button, Component label, Consumer<LdlibAe2Button> action) {
        super(width, height, button, () -> List.of(label), 0, () -> true);
        this.action = action;
        this.control = button;
        setMessage(label);
        button.noText().setOnClick(event -> {
            event.stopPropagation();
            onPress();
        });
    }

    public static LdlibAe2Button addText(WidgetContainer widgets, ScreenStyle style, String id,
            Component label, Runnable action) {
        var bounds = style.getWidget(id);
        var button = text(bounds.getWidth(), bounds.getHeight(), label, action);
        widgets.add(id, button);
        return button;
    }

    public static LdlibAe2Button text(int width, int height, Component label, Runnable action) {
        return text(width, height, label, action, 0);
    }

    static LdlibAe2Button text(int width, int height, Component label, Runnable action, int labelYOffset) {
        return new TextButton(width, height, label, ignored -> action.run(), labelYOffset);
    }

    public static LdlibAe2Button textAt(int x, int y, int width, int height, Component label,
            Consumer<LdlibAe2Button> action) {
        var button = new TextButton(width, height, label, action, 0);
        button.setPosition(x, y);
        return button;
    }

    private static final class TextButton extends LdlibAe2Button {
        private static final VanillaSpriteTexture NORMAL = VanillaSpriteTexture.of("ae2:button");
        private static final VanillaSpriteTexture HIGHLIGHTED = VanillaSpriteTexture.of("ae2:button_highlighted");
        private static final VanillaSpriteTexture DISABLED = VanillaSpriteTexture.of("ae2:button_disabled");
        private static final int TEXT_INSET = 2;
        private final int labelYOffset;

        private TextButton(int width, int height, Component label, Consumer<LdlibAe2Button> action, int labelYOffset) {
            super(width, height, new Button(), label, action);
            this.labelYOffset = labelYOffset;
            var face = IGuiTexture.dynamic(() -> {
                var sprite = !active ? DISABLED : isHovered() ? HIGHLIGHTED : NORMAL;
                return alpha == 1.0F ? sprite : sprite.copy().setColor(Mth.ceil(alpha * 255) << 24 | 0xFFFFFF);
            });
            control.buttonStyle(style -> style.baseTexture(face).hoverTexture(face).pressedTexture(face));
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            var font = Minecraft.getInstance().font;
            var text = getMessage().getVisualOrderText();
            int textWidth = font.width(getMessage());
            int color = Mth.ceil(alpha * 255) << 24
                    | (!active ? 0x413F54 : isHovered() ? 0x517497 : 0xF2F2F2);
            // AE2 divides absolute bounds before truncating, including negative or short bounds.
            int top = (getY() + getY() + getHeight() - 9) / 2 + 1 + labelYOffset;
            int innerWidth = getWidth() - 2 * TEXT_INSET;
            if (textWidth <= innerWidth) {
                int lift = !active ? -1 : isHovered() ? 0 : 1;
                int left = getX() + getWidth() / 2 - font.width(text) / 2;
                LDLibFonts.drawText(graphics, font, text, left, top - lift, color, false);
            } else if (innerWidth > 0) {
                int overflow = textWidth - innerWidth;
                double seconds = Util.getMillis() / 1000.0;
                double period = Math.max(overflow * 0.5, 3.0);
                double phase = Math.sin(Math.PI / 2 * Math.cos(2 * Math.PI * seconds / period)) / 2 + 0.5;
                int left = getX() + TEXT_INSET;
                graphics.enableScissor(left, getY() + labelYOffset, left + innerWidth, getY() + getHeight() + labelYOffset);
                try {
                    LDLibFonts.drawText(graphics, font, text, left - (int) (phase * overflow), top, color, false);
                } finally {
                    graphics.disableScissor();
                }
            }
        }

        @Override
        public boolean isTooltipAreaVisible() {
            return false;
        }
    }

    protected LdlibAe2Button(int size, Component label, Runnable action) {
        this(size, new Button(), label, action);
    }

    protected LdlibAe2Button(int width, int height, Component label, Runnable action) {
        this(width, height, new Button(), label, ignored -> action.run());
    }

    protected void toolbarFace(IGuiTexture glyph, boolean selected) {
        toolbarFace(glyph, selected, true);
    }

    protected void toolbarFace(IGuiTexture glyph, boolean selected, boolean showKeyboardFocus) {
        if (toolbarFrame == null) {
            useFaceTexture(IGuiTexture.EMPTY);
            toolbarFrame = addFacePart(-1, 0, 18, 20);
            toolbarGlyph = addFacePart(0, 1, 16, 16);
        }
        var normalFrame = LdlibAe2Icons.texture(Icon.TOOLBAR_BUTTON_BACKGROUND);
        var focusFrame = LdlibAe2Icons.texture(Icon.TOOLBAR_BUTTON_BACKGROUND_FOCUS);
        var hoverFrame = IGuiTexture.group(LdlibAe2Icons.texture(Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER))
                .transform(0.0F, 1.0F);
        var hoverGlyph = IGuiTexture.group(glyph).transform(0.0F, 1.0F);
        toolbarFrame.style(style -> style.background(toolbarLayer(IGuiTexture.dynamic(() -> {
            if (isHovered()) {
                return hoverFrame;
            }
            return selected || (showKeyboardFocus && isFocused()) ? focusFrame : normalFrame;
        }), 2)));
        toolbarGlyph.style(style -> style.background(toolbarLayer(
                IGuiTexture.dynamic(() -> isHovered() ? hoverGlyph : glyph), 3)));
    }

    protected final void toolbarGlyphOnly(IGuiTexture glyph) {
        useFaceTexture(IGuiTexture.EMPTY);
        var hoverGlyph = IGuiTexture.group(glyph).transform(0.0F, 1.0F);
        addFacePart(0, 1, 16, 16).style(style -> style.background(toolbarLayer(
                LdlibScreens.either(this::isHovered, hoverGlyph, glyph), 3)));
    }

    private static IGuiTexture toolbarLayer(IGuiTexture texture, int depth) {
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

    protected final void boxTabFace(IGuiTexture glyph, boolean highlightOnHover) {
        useFaceTexture(IGuiTexture.EMPTY);
        BooleanSupplier highlighted = () -> isFocused() || (highlightOnHover && isHovered());
        var idle = LdlibAe2Icons.texture(Icon.TAB_BUTTON_BACKGROUND);
        var focus = LdlibAe2Icons.texture(Icon.TAB_BUTTON_BACKGROUND_FOCUS);
        addFacePart(0, 0, 20, 20).style(style -> style.background(
                LdlibScreens.either(highlighted, IGuiTexture.EMPTY, idle)));
        addFacePart(0, 0, 22, 22).style(style -> style.background(
                LdlibScreens.either(highlighted, focus, IGuiTexture.EMPTY)));
        addFacePart(2, 1, 16, 16).style(style -> style.background(glyph));
    }

    private UIElement addFacePart(int x, int y, int width, int height) {
        var part = LdlibScreens.at(new UIElement().setAllowHitTest(false), x, y, width, height);
        control.addChild(part);
        return part;
    }

    private void useFaceTexture(IGuiTexture face) {
        control.buttonStyle(style -> style.baseTexture(face).hoverTexture(face).pressedTexture(face));
    }

    public static LdlibAe2Button toolbar(Icon glyph, Component label, Runnable action) {
        return toolbar(glyph, label, action, () -> List.of(label));
    }

    public static LdlibAe2Button toolbar(Icon glyph, Component label, Runnable action,
            Supplier<List<Component>> tooltip) {
        var button = new LdlibAe2Button(16, label, action) {
            @Override
            public List<Component> getTooltipMessage() {
                return tooltip.get();
            }
        };
        button.toolbarFace(LdlibAe2Icons.texture(glyph), false);
        return button;
    }

    public void setVisibility(boolean visible) {
        this.visible = visible;
        this.active = visible;
    }

    public static LdlibAe2Button back(Component label, Runnable action) {
        return tab(22, Icon.BACK, label, action);
    }

    public static void addOpenPriority(WidgetContainer widgets) {
        widgets.add("openPriority", tab(22, Icon.PRIORITY, GuiText.Priority.text(),
                () -> PacketDistributor.sendToServer(SwitchGuisPacket.openSubMenu(PriorityMenu.TYPE))));
    }

    public static LdlibAe2Button tab(int hitSize, Icon glyph, Component label, Runnable action) {
        var button = new LdlibAe2Button(hitSize, label, action);
        button.boxTabFace(LdlibAe2Icons.texture(glyph), false);
        return button;
    }

    public static LdlibAe2Button clear(Component label, Runnable action) {
        return small(Icon.S_CLEAR, label, action);
    }

    /** Preserves AE2's localized, pre-wrapped action message. */
    public static Component actionMessage(ActionItems action) {
        return new ActionButton(action, () -> {}).getMessage();
    }

    public static LdlibAe2Button small(Icon glyph, ActionItems item, Runnable action) {
        return small(glyph, actionMessage(item), action);
    }

    public static LdlibAe2Button small(Icon glyph, Component label, Runnable action) {
        var button = new LdlibAe2Button(8, label, action);
        button.useFaceTexture(IGuiTexture.dynamic(() -> LdlibAe2Icons.texture(glyph, button.active)));
        return button;
    }

    public static LdlibAe2Button bareIcon(Supplier<Icon> icon, Component label, Runnable action,
            Supplier<List<Component>> tooltip) {
        return bareIcon(16, icon, label, action, tooltip);
    }

    public static LdlibAe2Button bareIcon(int size, Supplier<Icon> icon, Component label, Runnable action,
            Supplier<List<Component>> tooltip) {
        LdlibAe2Button button = new LdlibAe2Button(size, label, action) {
            @Override
            public List<Component> getTooltipMessage() {
                return tooltip.get();
            }

            @Override
            public boolean isTooltipAreaVisible() {
                return visible;
            }
        };
        button.setMessage(Component.empty());
        button.useFaceTexture(IGuiTexture.dynamic(
                () -> LdlibAe2Icons.texture(icon.get(), size != 8 || button.active)));
        return button;
    }

    public static LdlibAe2Button small(Icon glyph, Component label, Component description, Runnable action) {
        LdlibAe2Button button = new LdlibAe2Button(8, label, action) {
            @Override
            public List<Component> getTooltipMessage() {
                return List.of(label, description);
            }
        };
        button.useFaceTexture(IGuiTexture.dynamic(() -> LdlibAe2Icons.texture(glyph, button.active)));
        return button;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // LDLib marks a registered listener as handled even when the button ignores this key.
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
    protected final boolean handlesInput() {
        return true;
    }

    @Override
    public void onPress() {
        if (active && visible) {
            action.accept(this);
        }
    }
}
