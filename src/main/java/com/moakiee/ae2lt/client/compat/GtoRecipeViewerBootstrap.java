package com.moakiee.ae2lt.client.compat;

import java.util.ArrayList;
import java.util.List;

import com.moakiee.ae2lt.AE2LightningTech;

/** Add LT after GTO's viewer discovery; its internal event owner is obfuscated in release jars. */
public final class GtoRecipeViewerBootstrap {
    private GtoRecipeViewerBootstrap() { }

    public static List<Object> withEmi(List<Object> plugins) {
        try {
            // The published EMI API jar omits this runtime container type.
            var type = Class.forName("dev.emi.emi.registry.EmiPluginContainer");
            var id = type.getMethod("id");
            for (var plugin : plugins) if (AE2LightningTech.MODID.equals(id.invoke(plugin))) return plugins;
            var result = new ArrayList<>(plugins);
            result.add(type.getConstructor(dev.emi.emi.api.EmiPlugin.class, String.class)
                    .newInstance(new com.moakiee.ae2lt.integration.emi.AE2LTEmiPlugin(), AE2LightningTech.MODID));
            return result;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot add LT to the installed EMI plugin list", failure);
        }
    }

    public static List<mezz.jei.api.IModPlugin> withJei(List<mezz.jei.api.IModPlugin> plugins) {
        for (var plugin : plugins) if (plugin instanceof com.moakiee.ae2lt.integration.jei.JEIPlugin) return plugins;
        var result = new ArrayList<>(plugins);
        result.add(new com.moakiee.ae2lt.integration.jei.JEIPlugin());
        return result;
    }

    /** Snapshot JEI's volatile runtime before a hover query; unload can precede EMI cleanup. */
    public static dev.emi.emi.api.stack.EmiStackInteraction jemiStackAt(
            mezz.jei.api.runtime.IJeiRuntime runtime, net.minecraft.client.gui.screens.Screen screen, int x, int y) {
        if (runtime == null) return dev.emi.emi.api.stack.EmiStackInteraction.EMPTY;
        var helper = runtime.getScreenHelper();
        if (helper == null) return dev.emi.emi.api.stack.EmiStackInteraction.EMPTY;
        var ingredients = helper.getClickableIngredientUnderMouse(screen, x, y).iterator();
        if (!ingredients.hasNext()) return dev.emi.emi.api.stack.EmiStackInteraction.EMPTY;
        try {
            var stack = (dev.emi.emi.api.stack.EmiStack) JemiConversion.CONVERT.invoke(null,
                    ingredients.next().getTypedIngredient());
            return new dev.emi.emi.api.stack.EmiStackInteraction(stack, null, false);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot convert the installed JEMI hover ingredient", failure);
        }
    }

    private static final class JemiConversion {
        private static final java.lang.reflect.Method CONVERT = converter();
        private static java.lang.reflect.Method converter() {
            try {
                return Class.forName("dev.emi.emi.jemi.JemiUtil")
                        .getMethod("getStack", mezz.jei.api.ingredients.ITypedIngredient.class);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Installed JEMI conversion API changed", failure);
            }
        }
    }
}
