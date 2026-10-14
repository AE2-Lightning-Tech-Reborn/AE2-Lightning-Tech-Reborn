package com.moakiee.ae2lt.compat.gto.jei;

import java.util.List;
import mezz.jei.api.runtime.IIngredientVisibility;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** JEI state owned by LT; no dependency on the removed AE2 JEI plugin. */
public final class GtoJeiRuntime {
    private static IJeiRuntime runtime;
    private GtoJeiRuntime() { }
    public static void set(IJeiRuntime value) { runtime = value; }
    static IIngredientVisibility ingredientVisibility() { return runtime.getIngredientVisibility(); }
    static void drawHoveringText(GuiGraphics graphics, List<Component> text, int x, int y) {
        graphics.renderComponentTooltip(Minecraft.getInstance().font, text, x, y);
    }
}
