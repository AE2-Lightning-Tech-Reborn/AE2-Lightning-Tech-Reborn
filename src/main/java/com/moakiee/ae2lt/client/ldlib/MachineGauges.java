package com.moakiee.ae2lt.client.ldlib;

import static com.moakiee.ae2lt.client.ldlib.LdlibScreens.at;
import static com.moakiee.ae2lt.client.ldlib.LdlibScreens.spriteProgress;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.FillDirection;
import net.minecraft.client.gui.GuiGraphics;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import appeng.client.gui.style.Blitter;
import appeng.menu.interfaces.IProgressProvider;

/**
 * LDLib2-rendered machine gauges for AE2 machine screens: energy bars, progress
 * overlays and the lightning status icon. Each gauge is an {@link LdlibAe2Widget},
 * so the AE2 style JSON keeps positioning it under the same widget id.
 */
public final class MachineGauges {
    public static final ResourceLocation STATUS_ICON = ResourceLocation.fromNamespaceAndPath(
            "ae2lt", "textures/gui/buttons/lightning.png");
    public static final int STATUS_ICON_SIZE = 16;

    private MachineGauges() {
    }

    /** Vertical energy bar: faded full sprite plus the stored fraction filled bottom-up. */
    public static LdlibAe2Widget energyBar(Blitter fill, LongSupplier stored, LongSupplier capacity,
            String tooltipKey) {
        return energyBar(fill, stored, capacity, tooltipKey, () -> true);
    }

    public static LdlibAe2Widget energyBar(Blitter fill, LongSupplier stored, LongSupplier capacity,
            String tooltipKey, BooleanSupplier visible) {
        var sprite = LdlibStyleSprite.from(fill);
        int width = sprite.width();
        int height = sprite.height();
        var background = sprite.full().setColor(0x33FFFFFF);
        var bar = spriteProgress(FillDirection.DOWN_TO_UP, sprite.verticalSlices(true));
        var root = element(background);
        root.addChild(at(bar, 0, 0, width, height));
        Supplier<List<Component>> tooltip = () -> List.of(Component.translatable(
                tooltipKey, stored.getAsLong(), capacity.getAsLong()));
        return gauge(width, height, root, () -> {
            long cap = Math.max(1L, capacity.getAsLong());
            long value = Mth.clamp(stored.getAsLong(), 0L, cap);
            bar.setProgress((int) Math.round(height * (double) value / cap));
        }, tooltip, 2, visible);
    }

    /** Horizontal overlay revealed left-to-right. */
    public static LdlibAe2Widget horizontalProgress(Blitter overlay, DoubleSupplier progress, boolean roundUp) {
        var sprite = LdlibStyleSprite.from(overlay);
        int width = sprite.width();
        int height = sprite.height();
        var bar = spriteProgress(FillDirection.LEFT_TO_RIGHT, sprite.horizontalSlices(0, 0, width, height));
        return gauge(width, height, bar, () -> {
            double exact = width * Mth.clamp(progress.getAsDouble(), 0, 1);
            bar.setProgress(roundUp ? (int) Math.ceil(exact) : (int) Math.round(exact));
        }, null, 0, () -> true);
    }

    public static LdlibAe2Widget verticalProgress(Blitter overlay, DoubleSupplier progress) {
        var sprite = LdlibStyleSprite.from(overlay);
        int height = sprite.height();
        var bar = spriteProgress(FillDirection.UP_TO_DOWN, sprite.verticalSlices(false));
        return gauge(sprite.width(), height, bar,
                () -> bar.setProgress((int) Math.ceil(height * Mth.clamp(progress.getAsDouble(), 0, 1))),
                null, 0, () -> true);
    }

    public static LdlibAe2Widget verticalFilledProgress(Blitter overlay, IProgressProvider progress,
            Supplier<List<Component>> tooltip) {
        var sprite = LdlibStyleSprite.from(overlay);
        int height = sprite.height();
        var bar = spriteProgress(FillDirection.DOWN_TO_UP, sprite.verticalSlices(true));
        return gauge(sprite.width(), height, bar,
                () -> bar.setProgress(verticalFillPixels(height, progress.getCurrentProgress(), progress.getMaxProgress())),
                tooltip, 2, () -> true);
    }

    static int verticalFillPixels(int height, int current, int max) {
        return max > 0 ? (int) ((long) height * Math.clamp(current, 0, max) / max) : 0;
    }

    /** Staged overlay that grows from the top and bottom edges toward the middle. */
    public static LdlibAe2Widget stagedConvergingProgress(Blitter overlay, DoubleSupplier progress, int stages) {
        var sprite = LdlibStyleSprite.from(overlay);
        int width = sprite.width();
        int height = sprite.height();
        var top = spriteProgress(FillDirection.UP_TO_DOWN, sprite.verticalSlices(false));
        var bottom = spriteProgress(FillDirection.DOWN_TO_UP, sprite.verticalSlices(true));
        var root = new UIElement();
        root.addChildren(at(top, 0, 0, width, height), at(bottom, 0, 0, width, height));
        return gauge(width, height, root, () -> {
            int stage = (int) Math.ceil(Mth.clamp(progress.getAsDouble(), 0, 1) * stages);
            int rows = Mth.ceil(height * stage / (float) stages);
            int topRows = rows / 2;
            top.setProgress(topRows);
            bottom.setProgress(rows - topRows);
        }, null, 0, () -> true);
    }

    private static LdlibAe2Widget gauge(int width, int height, UIElement root, Runnable update,
            Supplier<List<Component>> tooltip, int margin, BooleanSupplier visible) {
        return new LdlibAe2Widget(width, height, root, tooltip, margin, visible) {
            @Override
            protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float ticks) {
                update.run();
                super.renderWidget(graphics, mouseX, mouseY, ticks);
            }
        };
    }

    /** 16x16 lightning icon whose tooltip carries the machine status lines. */
    public static LdlibAe2Widget statusIcon(Supplier<List<Component>> tooltip) {
        var icon = new LdlibStyleSprite(STATUS_ICON, STATUS_ICON_SIZE, STATUS_ICON_SIZE).full();
        return new LdlibAe2Widget(STATUS_ICON_SIZE, STATUS_ICON_SIZE, element(icon), tooltip, 0, () -> true);
    }

    private static UIElement element(IGuiTexture background) {
        var element = new UIElement();
        element.setAllowHitTest(false);
        element.style(s -> s.background(background));
        return element;
    }
}
