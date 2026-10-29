package com.moakiee.ae2lt.compat.gto;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;

/** Replace ordinary-AE2 CPU hooks that cannot target GTO's abstract CPU and renamed job. */
public final class GtoLegacyAddonMixins {
    private static boolean registered;
    private static boolean uselessRegistered;

    private GtoLegacyAddonMixins() { }

    private static Set<String> replacements() {
        var result = new HashSet<String>();
        for (String name : Set.of("ae2.accessor.CraftingCpuLogicAccessor",
                "ae2.accessor.ExecutingCraftingJobAccessor", "ae2.accessor.ExecutingCraftingJobTaskProgressAccessor",
                "ae2.crafting.CraftingCpuLogicManualWaitingMixin", "ae2.autopattern.CraftingCpuLogicPatternPowerMixin",
                "ae2.autopattern.PatternProviderLogicContainsModifyMixin",
                "ae2.autopattern.PatternProviderLogicContainsRedirectMixin")) {
            result.add("com.extendedae_plus.mixin." + name);
        }
        return Set.copyOf(result);
    }

    public static synchronized void registerEaep() {
        if (registered) return;
        register(replacements());
        GtoEaepMixinExtension.register();
        registered = true;
    }

    public static synchronized void registerUseless() {
        if (uselessRegistered) return;
        register(Set.of("com.sorrowmist.useless.mixin.ae2.CraftingCpuLogicMixin",
                "com.sorrowmist.useless.mixin.ae2.CraftingCpuLogicAccessor",
                "com.sorrowmist.useless.mixin.ae2.ExecutingCraftingJobAccessor"));
        uselessRegistered = true;
    }

    private static void register(Set<String> replacements) {
        try {
            // GTO includes MixinSquared. Reflection keeps this API off the path when the
            // optional addons are absent, and avoids bundling another copy of the library.
            var loader = GtoLegacyAddonMixins.class.getClassLoader();
            var api = Class.forName("com.bawnorton.mixinsquared.api.MixinCanceller", true, loader);
            var registrar = Class.forName("com.bawnorton.mixinsquared.canceller.MixinCancellerRegistrar", true, loader);
            var canceller = Proxy.newProxyInstance(loader, new Class<?>[] {api}, (proxy, method, arguments) ->
                    switch (method.getName()) {
                        case "shouldCancel" -> replacements.contains(arguments[1]);
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == arguments[0];
                        case "toString" -> "AE2LT GTO legacy addon CPU replacements";
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
            registrar.getMethod("register", api).invoke(null, canceller);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("AE2LT's legacy addon GTO adapters require the pack's MixinSquared", failure);
        }
    }
}
