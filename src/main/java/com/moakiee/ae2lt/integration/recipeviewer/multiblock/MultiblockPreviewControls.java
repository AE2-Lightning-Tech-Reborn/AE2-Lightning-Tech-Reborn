package com.moakiee.ae2lt.integration.recipeviewer.multiblock;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.client.ldlib.LdlibStyleSprite;
import net.minecraft.resources.ResourceLocation;

/** LDLib sprite atlas for the shared JEI and EMI preview controls. */
final class MultiblockPreviewControls {
    static final int BUTTON_SIZE = 16;
    private static final LdlibStyleSprite ATLAS = new LdlibStyleSprite(
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "textures/gui/multiblock_controls.png"),
            96, 32);
    static final int ICON_COLOR = 0xFFE0E0E0;
    static final int HOVER_ICON_COLOR = 0xFFFFFFFF;
    private static final int DISABLED_ICON_COLOR = 0xFFA0A0A0;
    private static final SpriteTexture NORMAL = frame(0);
    private static final SpriteTexture HOVERED = frame(16);
    private static final SpriteTexture PRESSED = frame(32);
    private static final SpriteTexture PRESSED_HOVERED = frame(48);
    private static final SpriteTexture DISABLED = frame(64);
    private static final SpriteTexture INSET = frame(80);

    static IGuiTexture iconButtonFrame(
            BooleanSupplier pressed,
            BooleanSupplier contentPressed,
            BooleanSupplier enabled,
            BooleanSupplier hovered) {
        return IGuiTexture.dynamic(() -> enabled.getAsBoolean()
                ? buttonFrame(pressed.getAsBoolean() || contentPressed.getAsBoolean(), hovered.getAsBoolean())
                : DISABLED);
    }

    static UIElement iconGlyph(Supplier<PixelIcon> icon, BooleanSupplier contentPressed,
            BooleanSupplier enabled, BooleanSupplier hovered) {
        var texture = IGuiTexture.dynamic(() ->
                icon.get().texture(enabled.getAsBoolean(), hovered.getAsBoolean(), contentPressed.getAsBoolean()));
        return new UIElement().setAllowHitTest(false)
                .layout(l -> l.width(BUTTON_SIZE).height(BUTTON_SIZE).flexShrink(0))
                .style(s -> s.background(texture));
    }

    static IGuiTexture tabFrame(boolean pressed, BooleanSupplier hovered) {
        return IGuiTexture.dynamic(() -> buttonFrame(pressed, hovered.getAsBoolean()));
    }

    static IGuiTexture insetFrame() {
        return INSET;
    }

    private static SpriteTexture buttonFrame(boolean pressed, boolean hovered) {
        if (pressed) {
            return hovered ? PRESSED_HOVERED : PRESSED;
        }
        return hovered ? HOVERED : NORMAL;
    }

    private static SpriteTexture frame(int u) {
        // Two pixels on each edge stay fixed when the tab or indicator is stretched.
        return ATLAS.slice(u, 0, BUTTON_SIZE, BUTTON_SIZE).setBorder(2);
    }

    enum PixelIcon {
        RESET_VIEW(0, 9, 9),
        PLAY(9, 5, 9),
        PAUSE(18, 5, 7),
        SHELL(27, 8, 8),
        SHELL_HIDDEN(36, 9, 9),
        LAYERS_FULL(45, 7, 8),
        LAYERS_UP_TO(54, 7, 5),
        LAYERS_SINGLE(63, 7, 2),
        MINUS(72, 5, 1),
        PLUS(81, 5, 7);

        private final SpriteTexture normal;
        private final SpriteTexture highlighted;
        private final SpriteTexture disabled;
        private final IGuiTexture normalPressed;
        private final IGuiTexture highlightedPressed;
        private final IGuiTexture disabledPressed;

        PixelIcon(int u, int width, int height) {
            highlighted = ATLAS.slice(u, 16, width, height);
            highlighted.scale(width / (float) BUTTON_SIZE, height / (float) BUTTON_SIZE);
            normal = highlighted.copy().setColor(ICON_COLOR);
            disabled = highlighted.copy().setColor(DISABLED_ICON_COLOR);
            normalPressed = normal.copy().transform(0.5F, 0.5F);
            highlightedPressed = highlighted.copy().transform(0.5F, 0.5F);
            disabledPressed = disabled.copy().transform(0.5F, 0.5F);
        }

        private IGuiTexture texture(boolean enabled, boolean hovered, boolean pressed) {
            if (!enabled) {
                return pressed ? disabledPressed : disabled;
            }
            if (hovered) {
                return pressed ? highlightedPressed : highlighted;
            }
            return pressed ? normalPressed : normal;
        }
    }

    private MultiblockPreviewControls() {
    }
}
