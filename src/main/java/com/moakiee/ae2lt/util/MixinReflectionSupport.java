package com.moakiee.ae2lt.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

/**
 * Shared reflection helpers for {@link org.spongepowered.asm.mixin.Pseudo} mixins
 * that target optional dependencies such as AdvancedAE.
 * <p>
 * Discovery does not initialize optional classes. Lookup helpers return {@code null}
 * when a class, member descriptor or access permission is unavailable. Invocation
 * helpers also contain linkage and receiver/argument failures, logging a warning at
 * most once per {@code action} key before returning the caller's fallback.
 */
public final class MixinReflectionSupport {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> LOGGED_FAILURES =
            Collections.synchronizedSet(new HashSet<>());

    private MixinReflectionSupport() {
    }

    @Nullable
    public static Class<?> findClassSafe(String fqn) {
        try {
            return Class.forName(fqn, false, MixinReflectionSupport.class.getClassLoader());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    public static Field findDeclaredFieldSafe(@Nullable Class<?> owner, String name) {
        if (owner == null) return null;
        try {
            var field = owner.getDeclaredField(name);
            return field.trySetAccessible() ? field : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    public static Method findDeclaredMethodSafe(@Nullable Class<?> owner, String name,
                                                Class<?>... parameterTypes) {
        if (owner == null) return null;
        try {
            var method = owner.getDeclaredMethod(name, parameterTypes);
            return method.trySetAccessible() ? method : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    public static Object getFieldValueSafe(@Nullable Field field, Object target) {
        if (field == null) return null;
        try {
            return field.get(target);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            logReflectionFailure("read reflective field", e);
            return null;
        }
    }

    public static long getLongFieldSafe(@Nullable Field field, Object target, long fallback) {
        if (field == null) return fallback;
        try {
            return field.getLong(target);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            logReflectionFailure("read reflective long field", e);
            return fallback;
        }
    }

    public static void setLongFieldSafe(@Nullable Field field, Object target, long value, String action) {
        if (field == null) return;
        try {
            field.setLong(target, value);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            logReflectionFailure(action, e);
        }
    }

    @Nullable
    public static Object invokeMethodSafe(@Nullable Method method, Object target, String action,
                                          Object... args) {
        if (method == null) return null;
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            logReflectionFailure(action, e);
            return null;
        }
    }

    public static void logReflectionFailure(String action, Throwable exception) {
        if (LOGGED_FAILURES.add(action)) {
            LOGGER.warn("AE2LT reflection failed while trying to {}.", action, exception);
        }
    }
}
