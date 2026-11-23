package com.moakiee.ae2lt.client.ldlib;

import appeng.client.gui.Icon;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.moakiee.ae2lt.AE2LightningTech;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

/**
 * AE2-styled side controls built directly as LDLib elements, for screens whose whole UI lives in
 * one LDLib root. Positions are GUI-local, so they may lie outside the root's own bounds.
 */
public final class LdlibToolbarButton {
    private LdlibToolbarButton() {
    }

    /** A 16x16 glyph from {@code textures/gui/buttons/<name>.png}. */
    public static IGuiTexture glyph(String name) {
        return new LdlibStyleSprite(ResourceLocation.fromNamespaceAndPath(
                AE2LightningTech.MODID, "textures/gui/buttons/" + name + ".png"), 16, 16).full();
    }

    /** The 16x16 toolbar button: framed glyph that drops one pixel while hovered. */
    public static UIElement toolbar(int x, int y, Supplier<IGuiTexture> glyph, Runnable action,
            Supplier<List<Component>> tooltip) {
        var normalFrame = LdlibAe2Icons.texture(Icon.TOOLBAR_BUTTON_BACKGROUND);
        var hoverFrame = LdlibAe2Icons.texture(Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER);
        IGuiTexture face = (graphics, mouseX, mouseY, left, top, width, height, ticks) -> {
            boolean hovered = mouseX >= left && mouseX < left + 16 && mouseY >= top && mouseY < top + 16;
            int offset = hovered ? 1 : 0;
            (hovered ? hoverFrame : normalFrame).draw(graphics, mouseX, mouseY, left - 1, top + offset, 18, 20, ticks);
            glyph.get().draw(graphics, mouseX, mouseY, left, top + 1 + offset, 16, 16, ticks);
        };
        return control(x, y, 16, face, action, tooltip);
    }

    /** The 20x20 AE2 box-tab button with an icon glyph. */
    public static UIElement tab(int x, int y, Icon glyph, Runnable action, Component tooltip) {
        var frame = LdlibAe2Icons.texture(Icon.TAB_BUTTON_BACKGROUND);
        var icon = LdlibAe2Icons.texture(glyph);
        IGuiTexture face = (graphics, mouseX, mouseY, left, top, width, height, ticks) -> {
            frame.draw(graphics, mouseX, mouseY, left, top, 20, 20, ticks);
            icon.draw(graphics, mouseX, mouseY, left + 2, top + 1, 16, 16, ticks);
        };
        return control(x, y, 20, face, action, () -> List.of(tooltip));
    }

    private static UIElement control(int x, int y, int size, IGuiTexture face, Runnable action,
            Supplier<List<Component>> tooltip) {
        var element = new UIElement();
        element.style(s -> s.background(face));
        LdlibScreens.onLeftClick(element, () -> {
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            action.run();
        });
        LdlibScreens.tooltip(element, tooltip);
        return LdlibScreens.at(element, x, y, size, size);
    }
}
