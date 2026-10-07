package com.moakiee.ae2lt.logic.craft.migration;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/** Cached optional-version lookups. Failure disables a source instead of guessing at its inventory. */
final class MigrationReflection {
    private static final ClassValue<Map<String, Method>> METHODS = new ClassValue<>() {
        @Override protected Map<String, Method> computeValue(Class<?> type) { return new HashMap<>(); }
    };
    private static final ClassValue<Map<String, Field>> FIELDS = new ClassValue<>() {
        @Override protected Map<String, Field> computeValue(Class<?> type) { return new HashMap<>(); }
    };

    private static final ClassValue<Map<String, Boolean>> FIELD_NAMES = new ClassValue<>() {
        @Override protected Map<String, Boolean> computeValue(Class<?> type) { return new HashMap<>(); }
    };

    static boolean hasMethod(Object receiver, String name) {
        for (Class<?> type = receiver.getClass(); type != null; type = type.getSuperclass()) {
            try { type.getDeclaredMethod(name); return true; }
            catch (NoSuchMethodException absent) { /* Continue through older optional versions. */ }
        }
        return false;
    }

    static boolean hasField(Object receiver, String name) {
        return FIELD_NAMES.get(receiver.getClass()).computeIfAbsent(name, ignored -> {
            for (Class<?> type = receiver.getClass(); type != null; type = type.getSuperclass()) {
                try { type.getDeclaredField(name); return true; }
                catch (NoSuchFieldException absent) { /* Try the next superclass. */ }
            }
            return false;
        });
    }

    static Object call(Object receiver, String name) {
        try {
            var methods = METHODS.get(receiver.getClass());
            Method method = methods.get(name);
            if (method == null) {
                Class<?> type = receiver.getClass();
                while (type != null) {
                    try { method = type.getDeclaredMethod(name); break; }
                    catch (NoSuchMethodException ignored) { type = type.getSuperclass(); }
                }
                if (method == null) method = receiver.getClass().getMethod(name);
                method.setAccessible(true);
                methods.put(name, method);
            }
            return method.invoke(receiver);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException("Unsupported migration method " + name, e);
        }
    }

    static Object field(Object receiver, String name) {
        try {
            var fields = FIELDS.get(receiver.getClass());
            Field field = fields.get(name);
            if (field == null) {
                Class<?> type = receiver.getClass();
                while (type != null) {
                    try { field = type.getDeclaredField(name); break; }
                    catch (NoSuchFieldException ignored) { type = type.getSuperclass(); }
                }
                if (field == null) throw new NoSuchFieldException(name);
                field.setAccessible(true);
                fields.put(name, field);
            }
            return field.get(receiver);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException("Unsupported migration field " + name, e);
        }
    }

    static boolean isType(Class<?> type, String className) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            if (current.getName().equals(className)) return true;
        }
        return false;
    }
}
