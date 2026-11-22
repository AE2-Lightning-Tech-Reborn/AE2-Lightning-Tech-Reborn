package com.moakiee.ae2lt.integration.recipeviewer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.moakiee.ae2lt.client.equipment.LightningKeyRenderHandler;
import com.moakiee.ae2lt.client.ldlib.LdlibStyleSprite;
import com.moakiee.ae2lt.me.key.LightningKey;

import dev.vfyjxf.taffy.style.TaffyPosition;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Builder for the non-ingredient part of a recipe page (machine background art,
 * animated process overlays, info text) as LDLib2 textures or an optional {@link ModularUI}.
 *
 * <p>JEI categories paint the textures from {@code draw()}, beneath JEI's slots.
 * EMI recipes use native drawable widgets in their original widget paint order.
 * Slots, tanks and ingredient lookups stay native to each viewer.</p>
 */
public final class RecipeDecor {
    private static final int RECIPE_ATLAS_SIZE = 256;
    private final int width;
    private final int height;
    private final boolean emiText;
    private final List<Drawable> drawables = new ArrayList<>();

    private RecipeDecor(int width, int height, boolean emiText) {
        this.width = width;
        this.height = height;
        this.emiText = emiText;
    }

    public static RecipeDecor of(int width, int height) {
        return new RecipeDecor(width, height, false);
    }

    /** Retains EMI TextWidget's integer center offset and text depth. */
    public static RecipeDecor forEmi(int width, int height) {
        return new RecipeDecor(width, height, true);
    }

    /** Static region of a 256-by-256 recipe atlas drawn 1:1 at {@code x, y}. */
    public RecipeDecor texture(ResourceLocation texture, int x, int y, int w, int h, int u, int v) {
        var atlas = new LdlibStyleSprite(texture, RECIPE_ATLAS_SIZE, RECIPE_ATLAS_SIZE);
        return drawable(x, y, w, h, (graphics, mouseX, mouseY, left, top, boxWidth, boxHeight, ticks) -> {
            if (emiText) {
                graphics.setColor(1, 1, 1, 1);
            }
            atlas.draw(graphics, (int) left, (int) top, u, v, w, h);
        });
    }

    public RecipeDecor lightningIcon(int x, int y, LightningKey key) {
        return drawable(x, y, 16, 16, LightningKeyRenderHandler.guiIcon(key));
    }

    /** Overlay revealed left-to-right over {@code cycleMillis}, looping (process arrows). */
    public RecipeDecor horizontalReveal(ResourceLocation texture, int x, int y, int w, int h, int u, int v,
            long cycleMillis) {
        return horizontalReveal(texture, x, y, w, h, u, v,
                () -> revealPixels(Util.getMillis(), cycleMillis, w));
    }

    /** Uses EMI's wall-clock phase and integer pixel steps for its process overlays. */
    public RecipeDecor horizontalRevealWallClock(ResourceLocation texture, int x, int y, int w, int h, int u, int v,
            long cycleMillis) {
        return horizontalReveal(texture, x, y, w, h, u, v,
                () -> wallClockRevealPixels(System.currentTimeMillis(), cycleMillis, w));
    }

    private RecipeDecor horizontalReveal(ResourceLocation texture, int x, int y, int w, int h, int u, int v,
            IntSupplier pixels) {
        var atlas = new LdlibStyleSprite(texture, RECIPE_ATLAS_SIZE, RECIPE_ATLAS_SIZE);
        return drawable(x, y, w, h, (graphics, mouseX, mouseY, left, top, boxWidth, boxHeight, ticks) -> {
            int revealedWidth = pixels.getAsInt();
            if (revealedWidth <= 0 && !emiText) {
                return;
            }
            atlas.draw(graphics, (int) left, (int) top, u, v, revealedWidth, h);
        });
    }

    private static int revealPixels(long timeMillis, long cycleMillis, int width) {
        double progress = (timeMillis % cycleMillis) / (double) cycleMillis;
        return Mth.clamp((int) Math.ceil(progress * width), 0, width);
    }

    private static int wallClockRevealPixels(long timeMillis, long cycleMillis, int width) {
        int cycle = Math.toIntExact(cycleMillis);
        int elapsed = (int) (timeMillis % cycle);
        return width * elapsed / cycle;
    }

    /** Vertical sprite sheet animation ({@code frames} square frames, {@code frameMillis} each). */
    public RecipeDecor frames(ResourceLocation texture, int x, int y, int size, int frames, long frameMillis) {
        var atlas = new LdlibStyleSprite(texture, size, size * frames);
        var sprites = new IGuiTexture[frames];
        for (int i = 0; i < frames; i++) {
            int frameTop = i * size;
            sprites[i] = (graphics, mouseX, mouseY, left, top, boxWidth, boxHeight, ticks) ->
                    atlas.draw(graphics, (int) left, (int) top, 0, frameTop, size, size);
        }
        return drawable(x, y, size, size, IGuiTexture.dynamic(() -> {
            int frame = (int) ((Util.getMillis() / frameMillis) % frames);
            return sprites[frame];
        }));
    }

    /** Text centred across the whole page. */
    public RecipeDecor centeredText(Component text, int y, int color) {
        return centeredText(() -> text, width / 2, y, color, true);
    }

    /** Re-evaluates live text once per draw before measuring and rendering it. */
    public RecipeDecor centeredText(Supplier<Component> text, int y, int color) {
        return centeredText(text, width / 2, y, color, true);
    }

    /** Text centred on {@code centerX}. */
    public RecipeDecor centeredText(Component text, int centerX, int y, int color) {
        return centeredText(() -> text, centerX, y, color, false);
    }

    private RecipeDecor centeredText(Supplier<Component> text, int centerX, int y, int color, boolean wholePage) {
        return drawable(0, y, width, 9,
                (graphics, mouseX, mouseY, x, top, boxWidth, boxHeight, ticks) -> {
                    Component value = text.get();
                    var font = Minecraft.getInstance().font;
                    int textWidth = font.width(value);
                    int left = wholePage && !emiText ? (width - textWidth) / 2 : centerX - textWidth / 2;
                    var pose = graphics.pose();
                    pose.pushPose();
                    try {
                        pose.translate(x, top, emiText ? 300 : 0);
                        graphics.drawString(font, value, left, 0, color, false);
                    } finally {
                        pose.popPose();
                    }
                });
    }

    /** Text centred across the page, drawn at {@code scale} (used when a viewer clamps the height). */
    public RecipeDecor scaledCenteredText(Component text, int y, int height, int color, float scale) {
        return drawable(0, y, width, height,
                (graphics, mouseX, mouseY, x, top, boxWidth, boxHeight, ticks) -> {
                    var font = Minecraft.getInstance().font;
                    var pose = graphics.pose();
                    pose.pushPose();
                    try {
                        pose.translate(x, top, 0);
                        pose.scale(scale, scale, 1);
                        int left = Math.round((width / 2f) / scale - font.width(text) / 2f);
                        graphics.drawString(font, text, left, 0, color, false);
                    } finally {
                        pose.popPose();
                    }
                });
    }

    public RecipeDecor drawable(int x, int y, int w, int h, IGuiTexture texture) {
        drawables.add(new Drawable(x, y, w, h, texture));
        return this;
    }

    /** Paint operations in registration order, without a UI frame's flush or depth changes. */
    public List<Drawable> drawables() {
        return List.copyOf(drawables);
    }

    public record Drawable(int x, int y, int width, int height, IGuiTexture texture) {
    }

    /** Draws in the caller's existing pose and buffer state, at page-local coordinates. */
    public void draw(GuiGraphics graphics, float mouseX, float mouseY, float partialTick) {
        for (var drawable : drawables) {
            drawable.texture().draw(graphics, mouseX, mouseY, drawable.x(), drawable.y(),
                    drawable.width(), drawable.height(), partialTick);
        }
    }

    public ModularUI build() {
        var root = new UIElement();
        root.layout(l -> l.width(width).height(height));
        root.setAllowHitTest(false);
        for (var drawable : drawables) {
            var element = new UIElement();
            element.setAllowHitTest(false);
            element.style(s -> s.background(drawable.texture()));
            root.addChild(place(element, drawable.x(), drawable.y(), drawable.width(), drawable.height()));
        }
        var modularUI = ModularUI.of(UI.of(root));
        modularUI.init(width, height);
        return modularUI;
    }

    private static <E extends UIElement> E place(E element, int x, int y, int w, int h) {
        element.layout(l -> l.positionType(TaffyPosition.ABSOLUTE).left(x).top(y).width(w).height(h));
        return element;
    }
}
