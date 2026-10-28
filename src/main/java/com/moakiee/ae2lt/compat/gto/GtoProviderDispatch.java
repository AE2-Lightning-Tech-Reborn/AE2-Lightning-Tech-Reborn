package com.moakiee.ae2lt.compat.gto;

import java.lang.reflect.*;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Supplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;

/** Bridge one LT-owned copy to GTO's real dispatcher, whose ordinary pushPattern is a stub. */
public final class GtoProviderDispatch {
    private record Access(Method dispatch, Constructor<?> holder, Field value, Object success) { }
    private static final ClassValue<Optional<Access>> ACCESS = new ClassValue<>() {
        @Override protected Optional<Access> computeValue(Class<?> type) {
            if (!PatternProviderLogic.class.isAssignableFrom(type)) return Optional.empty();
            try {
                // Preserve addon providers with their own single-copy implementation.
                if (type.getMethod("pushPattern", IPatternDetails.class, KeyCounter[].class).getDeclaringClass()
                        != PatternProviderLogic.class) return Optional.empty();
                var methods = Arrays.stream(type.getMethods()).filter(method -> {
                    var args = method.getParameterTypes();
                    return !method.isBridge() && !method.isSynthetic() && args.length == 3
                            && args[0] == IPatternDetails.class
                            && args[1].getName().equals("com.gto.datasynclib.util.holder.ObjHolder")
                            && args[2] == Supplier.class && method.getReturnType().isEnum();
                }).toList();
                if (methods.isEmpty()) return Optional.empty();
                if (methods.size() != 1) throw new IllegalStateException("Ambiguous GTO provider dispatcher: " + type);
                var method = methods.get(0);
                var holder = method.getParameterTypes()[1];
                var success = Arrays.stream(method.getReturnType().getEnumConstants())
                        .filter(value -> ((Enum<?>) value).name().equals("SUCCESS")).findFirst().orElseThrow();
                return Optional.of(new Access(method, holder.getConstructor(Object.class), holder.getField("value"), success));
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("GTO provider dispatch contract changed", failure);
            }
        }
    };
    private GtoProviderDispatch() { }

    public static boolean push(ICraftingProvider provider, IPatternDetails pattern, KeyCounter[] inputs) {
        var access = ACCESS.get(provider.getClass()).orElse(null);
        if (access == null) return provider.pushPattern(pattern, inputs);
        final Object holder;
        try { holder = access.holder.newInstance((Object) inputs); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot allocate GTO dispatch holder", failure); }
        var accepted = new boolean[1];
        Supplier<Object> stopAfterOne = () -> {
            accepted[0] = true;
            try { access.value.set(holder, null); }
            catch (IllegalAccessException failure) { throw new IllegalStateException(failure); }
            return access.success;
        };
        try {
            var result = access.dispatch.invoke(provider, pattern, holder, stopAfterOne);
            if (!accepted[0] && result == access.success)
                throw new Indeterminate("GTO reported success without settling the input holder", null);
            return accepted[0];
        } catch (InvocationTargetException failure) {
            // The target may already own part/all of the input. Never refund or retry that copy.
            throw new Indeterminate("GTO provider threw after entering dispatch", failure.getCause());
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Cannot invoke GTO dispatcher", failure);
        }
    }

    public static boolean isIndeterminate(Throwable failure) {
        for (var current = failure; current != null; current = current.getCause())
            if (current instanceof Indeterminate) return true;
        return false;
    }
    private static final class Indeterminate extends RuntimeException {
        private Indeterminate(String message, Throwable cause) { super(message, cause); }
    }
}
