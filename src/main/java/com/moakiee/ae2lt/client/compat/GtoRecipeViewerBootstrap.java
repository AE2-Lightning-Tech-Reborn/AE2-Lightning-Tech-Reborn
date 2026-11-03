package com.moakiee.ae2lt.client.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.compat.gto.GtoUselessJeiAbi;
import mezz.jei.api.IModPlugin;

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
        return restoreJei(plugins, GtoUselessJeiAbi.restorePlugin(),
                com.moakiee.ae2lt.integration.jei.JEIPlugin::new, GtoRecipeViewerBootstrap::uselessPlugin);
    }

    public static Set<String> withJeiServices(Set<String> nativeOnlyMods) {
        return withJeiServices(nativeOnlyMods, GtoUselessJeiAbi.restorePlugin());
    }

    static Set<String> withJeiServices(Set<String> nativeOnlyMods, boolean restoreUseless) {
        if (!nativeOnlyMods.contains(AE2LightningTech.MODID)
                && (!restoreUseless || !nativeOnlyMods.contains("useless_mod"))) return nativeOnlyMods;
        var result = new java.util.HashSet<>(nativeOnlyMods);
        result.remove(AE2LightningTech.MODID);
        if (restoreUseless) result.remove("useless_mod");
        return result;
    }

    static List<IModPlugin> restoreJei(List<IModPlugin> plugins, boolean restoreUseless,
            Supplier<? extends IModPlugin> lt, Supplier<? extends IModPlugin> useless) {
        var result = ensurePlugin(plugins, "com.moakiee.ae2lt.integration.jei.JEIPlugin", lt);
        if (restoreUseless) result = ensurePlugin(result, GtoUselessJeiAbi.PLUGIN, useless);
        return result;
    }

    private static List<IModPlugin> ensurePlugin(List<IModPlugin> plugins, String name,
            Supplier<? extends IModPlugin> factory) {
        var result = new ArrayList<IModPlugin>(plugins.size() + 1);
        boolean present = false;
        boolean duplicate = false;
        for (var plugin : plugins) {
            if (plugin.getClass().getName().equals(name)) {
                if (present) { duplicate = true; continue; }
                present = true;
            }
            result.add(plugin);
        }
        if (present) return duplicate ? result : plugins;
        var plugin = factory.get();
        if (!plugin.getClass().getName().equals(name)) {
            throw new IllegalStateException("Unexpected restored JEI plugin: " + plugin.getClass().getName());
        }
        result.add(plugin);
        return result;
    }

    private static IModPlugin uselessPlugin() {
        try {
            // Initialize only at JEI discovery, after the resource-only ABI gate has run.
            var value = Class.forName(GtoUselessJeiAbi.PLUGIN, true, GtoRecipeViewerBootstrap.class.getClassLoader())
                    .getConstructor().newInstance();
            if (value instanceof IModPlugin plugin) return plugin;
            throw new IllegalStateException("The installed Useless JEI plugin does not implement IModPlugin");
        } catch (ReflectiveOperationException | LinkageError failure) {
            throw new IllegalStateException("Cannot restore the installed Useless JEI plugin", failure);
        }
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
