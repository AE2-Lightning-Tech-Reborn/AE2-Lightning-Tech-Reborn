package com.moakiee.ae2lt.compat.gto;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import com.moakiee.ae2lt.overload.runtime.model.EncodedOverloadPattern;
import com.moakiee.ae2lt.overload.runtime.model.MatchMode;
import com.moakiee.ae2lt.overload.runtime.pattern.*;
import net.minecraftforge.fml.ModList;

/** Adapt only output matching; Useless still owns its pattern identity, inputs and machine dispatch. */
public final class GtoUselessDynamicPatterns {
    private record Api(Method resolve, Method pattern, Method outputMode) { }
    private static boolean initialized;
    private static Api api;
    private static final Map<IPatternDetails, OverloadPatternDetails> VIEWS = new WeakHashMap<>();
    private GtoUselessDynamicPatterns() { }

    private static synchronized Api api() {
        if (initialized) return api;
        var mods = ModList.get();
        if (mods == null) return null;
        var mod = mods.getModContainerById("useless_mod");
        if (mod.isEmpty() || !java.util.Set.of("20.2.4.7", "20.2.4.7-gto.1")
                .contains(mod.get().getModInfo().getVersion().toString())) {
            initialized = true;
            return null;
        }
        try {
            var loader = GtoUselessDynamicPatterns.class.getClassLoader();
            var execution = Class.forName("com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicPatternExecution", false, loader);
            var dynamic = Class.forName("com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicComponentPattern", false, loader);
            var resolve = execution.getMethod("resolve", IPatternDetails.class);
            api = new Api(resolve, resolve.getReturnType().getMethod("pattern"), dynamic.getMethod("isItemIdOutput", int.class));
            initialized = true;
            return api;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Supported Useless dynamic-pattern API changed", failure);
        }
    }

    public static boolean isDynamic(IPatternDetails details) {
        return view(com.moakiee.thunderbolt.core.crafting.support.CraftingPatternDelegates
                .forProviderLookup(details)) != null;
    }

    public static synchronized OverloadPatternDetails view(IPatternDetails details) {
        var contract = api();
        if (contract == null) return null;
        if (VIEWS.containsKey(details)) return VIEWS.get(details);
        try {
            var resolved = contract.resolve.invoke(null, details);
            if (resolved == null) { VIEWS.put(details, null); return null; }
            var dynamic = contract.pattern.invoke(resolved);
            var parsed = ParsedPatternDefinition.builder(details.getDefinition().toStack(1));
            var modes = EncodedOverloadPattern.builder();
            var outputs = details.getOutputs();
            for (int slot = 0; slot < outputs.length; slot++) {
                if (!(outputs[slot].what() instanceof AEItemKey key)) continue;
                // This view carries slot identity/mode only. Registration uses the actual long
                // output amounts, avoiding the editor model's int-sized ItemStack quantities.
                parsed.output(slot, key.toStack(1), slot == 0);
                if ((boolean) contract.outputMode.invoke(dynamic, slot)) modes.output(slot, MatchMode.ID_ONLY);
            }
            var view = new OverloadPatternDetails(parsed.build(), modes.build());
            VIEWS.put(details, view);
            return view;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot adapt Useless dynamic output matching", failure);
        }
    }

    public static OverloadedProviderOnlyPatternDetails outputView(IPatternDetails details) {
        var original = com.moakiee.thunderbolt.core.crafting.support.CraftingPatternDelegates.forProviderLookup(details);
        return original instanceof OverloadedProviderOnlyPatternDetails overloaded ? overloaded : view(original);
    }
}
