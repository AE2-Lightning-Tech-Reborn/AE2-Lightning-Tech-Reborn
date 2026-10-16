package com.moakiee.ae2lt.integration.jei;

import java.lang.reflect.Method;

@FunctionalInterface
interface JeiBookmarkWriter {
    void add(Object ingredient) throws ReflectiveOperationException;

    static JeiBookmarkWriter resolve(Object runtime, Class<?> ingredientType)
            throws ReflectiveOperationException {
        Method managerGetter;
        try {
            managerGetter = runtime.getClass().getMethod("getBookmarkManager");
        } catch (NoSuchMethodException absent) {
            managerGetter = null;
        }
        if (managerGetter != null) {
            Object manager = managerGetter.invoke(runtime);
            Method add = manager.getClass().getMethod("add", ingredientType);
            return ingredient -> add.invoke(manager, ingredient);
        }

        Object overlay = runtime.getClass().getMethod("getBookmarkOverlay").invoke(runtime);
        Object list = field(overlay, "bookmarkList");
        try {
            Method add = list.getClass().getMethod("add", ingredientType);
            return ingredient -> add.invoke(list, ingredient);
        } catch (NoSuchMethodException absent) {
            // Older JEI 15 builds expose only the IBookmark overload.
        }
        Class<?> bookmarkType = Class.forName("mezz.jei.gui.bookmarks.IBookmark", false,
                runtime.getClass().getClassLoader());
        Method add = list.getClass().getMethod("add", bookmarkType);
        try {
            Object factory = field(list, "bookmarkFactory");
            Method create = factory.getClass().getMethod("create", ingredientType);
            return ingredient -> add.invoke(list, create.invoke(factory, ingredient));
        } catch (NoSuchFieldException absent) {
            // JEI 15's IngredientBookmark factory needs the runtime ingredient manager.
            Object manager = runtime.getClass().getMethod("getIngredientManager").invoke(runtime);
            Class<?> managerType = Class.forName("mezz.jei.api.runtime.IIngredientManager", false,
                    runtime.getClass().getClassLoader());
            Class<?> ingredientBookmark = Class.forName("mezz.jei.gui.bookmarks.IngredientBookmark", false,
                    runtime.getClass().getClassLoader());
            Method create = ingredientBookmark.getMethod("create", ingredientType, managerType);
            return ingredient -> add.invoke(list, create.invoke(null, ingredient, manager));
        }
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }
}
