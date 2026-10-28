package com.moakiee.ae2lt.compat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Validate an optional public ABI before any material/energy ownership crosses it. */
public final class OptionalApiMethods {
    private OptionalApiMethods() {}

    public static Class<?> load(ClassLoader loader, String name) throws ClassNotFoundException {
        // Discovery must not run addon static initializers.
        return Class.forName(name, false, loader);
    }

    public static Method instance(Class<?> owner, String name, Class<?> result, Class<?>... parameters)
            throws NoSuchMethodException {
        return method(owner, name, result, false, parameters);
    }

    public static Method staticMethod(Class<?> owner, String name, Class<?> result, Class<?>... parameters)
            throws NoSuchMethodException {
        return method(owner, name, result, true, parameters);
    }

    private static Method method(Class<?> owner, String name, Class<?> result, boolean isStatic,
                                 Class<?>... parameters) throws NoSuchMethodException {
        var method = owner.getMethod(name, parameters);
        if (!Modifier.isPublic(owner.getModifiers())
                || !Modifier.isPublic(method.getDeclaringClass().getModifiers())
                || method.getReturnType() != result
                || Modifier.isStatic(method.getModifiers()) != isStatic) {
            throw new NoSuchMethodException(owner.getName() + "." + name + " has an incompatible public ABI");
        }
        return method;
    }
}
