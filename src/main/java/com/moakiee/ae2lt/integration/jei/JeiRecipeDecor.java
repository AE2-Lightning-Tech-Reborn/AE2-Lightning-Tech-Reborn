package com.moakiee.ae2lt.integration.jei;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

import com.moakiee.ae2lt.integration.recipeviewer.RecipeDecor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;

/**
 * Renders a per-recipe {@link RecipeDecor} from a JEI category's {@code draw()} method,
 * which keeps the decoration underneath JEI's own slots. Paint descriptions are cached per recipe.
 */
public final class JeiRecipeDecor<R> {
    private final Function<R, RecipeDecor> factory;
    private final Map<R, RecipeDecor> cache = Collections.synchronizedMap(new WeakHashMap<>());

    public JeiRecipeDecor(Function<R, RecipeDecor> factory) {
        this.factory = factory;
    }

    public void draw(R recipe, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        cache.computeIfAbsent(recipe, factory)
                .draw(guiGraphics, (float) mouseX, (float) mouseY, partialTick);
    }

    public static void addArrow(IRecipeExtrasBuilder builder, int x, int y) {
        builder.addRecipeArrow().setPosition(x, y);
    }

    public static void addScrollingText(IRecipeExtrasBuilder builder, int width, int top, int height,
            List<Component> paragraphs, int color) {
        List<net.minecraft.network.chat.FormattedText> contents = paragraphs.stream()
                .<net.minecraft.network.chat.FormattedText>map(paragraph -> paragraph.copy().withColor(color))
                .toList();
        builder.addScrollBoxWidget(width, height, 0, top).setContents(contents);
    }
}
