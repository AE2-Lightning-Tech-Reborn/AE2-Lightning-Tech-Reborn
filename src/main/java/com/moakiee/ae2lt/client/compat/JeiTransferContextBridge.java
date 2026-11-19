package com.moakiee.ae2lt.client.compat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Uses the public context API without linking it on JEI versions older than 19.52.
 * No mixin into JEI's context implementation, private fields, or concrete copy factory is needed.
 */
public final class JeiTransferContextBridge {
    private JeiTransferContextBridge() {}

    private static final class Api {
        static final Class<?> TYPE = contextApi();
        static final Method CONTAINER = method("getContainer");
        static final Method RECIPE = method("getRecipe");
        static final Method SLOTS = method("getRecipeSlots");
        static final Method PLAYER = method("getPlayer");
        static final Method MAXIMUM = method("isMaxTransfer");

        private static Class<?> contextApi() {
            try {
                return Class.forName("mezz.jei.api.recipe.transfer.IRecipeTransferContext", false,
                        JeiTransferContextBridge.class.getClassLoader());
            } catch (ClassNotFoundException error) {
                throw new IllegalStateException("JEI transfer context API is unavailable", error);
            }
        }

        private static Method method(String name) {
            try {
                return TYPE.getMethod(name);
            } catch (NoSuchMethodException error) {
                throw new IllegalStateException("Unsupported JEI context API: " + name, error);
            }
        }
    }

    public static AbstractContainerMenu container(Object context) { return (AbstractContainerMenu) invoke(Api.CONTAINER, context); }
    public static Object recipe(Object context) { return invoke(Api.RECIPE, context); }
    public static IRecipeSlotsView slots(Object context) { return (IRecipeSlotsView) invoke(Api.SLOTS, context); }
    public static Player player(Object context) { return (Player) invoke(Api.PLAYER, context); }
    public static boolean maximum(Object context) { return (boolean) invoke(Api.MAXIMUM, context); }

    public static Object withSlots(Object context, IRecipeSlotsView slots, boolean maximum) {
        if (slots == null && maximum == maximum(context)) return context;
        return Proxy.newProxyInstance(Api.TYPE.getClassLoader(), new Class<?>[] {Api.TYPE}, (proxy, method, args) -> {
            if (method.getName().equals("getRecipeSlots") && slots != null) return slots;
            if (method.getName().equals("isMaxTransfer")) return maximum;
            return invoke(method, context, args);
        });
    }

    private static Object invoke(Method method, Object context, Object... args) {
        try {
            return method.invoke(context, args);
        } catch (InvocationTargetException error) {
            if (error.getCause() instanceof RuntimeException cause) throw cause;
            if (error.getCause() instanceof Error cause) throw cause;
            throw new IllegalStateException("JEI context call failed", error.getCause());
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Cannot access public JEI context API", error);
        }
    }
}
