package com.moakiee.ae2lt.mixin.recipeviewer;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import net.neoforged.fml.loading.LoadingModList;

/** Gates optional recipe-viewer mixins before their external API types are resolved. */
public final class RecipeViewerMixinPlugin implements IMixinConfigPlugin {
    private boolean jeiPresent;
    private boolean emiPresent;
    private boolean jeiSupplyPresent;
    private boolean jeiLegacyTransfer;
    private boolean uselessJeiPresent;

    @Override
    public void onLoad(String mixinPackage) {
        var mods = LoadingModList.get();
        jeiSupplyPresent = mods.getModFileById("jei") != null;
        // JEI 19.27+ performs the handler call from RecipeTransferService, not RecipeTransferUtil.
        jeiLegacyTransfer = jeiSupplyPresent && !jeiAtLeast(mods.getModFileById("jei"), 19, 27);
        jeiPresent = mods.getModFileById("jei") != null
                && mods.getModFileById("ae2jeiintegration") != null;
        emiPresent = mods.getModFileById("emi") != null;
        uselessJeiPresent = mods.getModFileById("useless_mod") != null
                && mods.getModFileById("jei") != null;
    }

    /** JEI 19.27 moved handler transfer out of RecipeTransferUtil. Unparseable versions keep the legacy hook. */
    private static boolean jeiAtLeast(net.neoforged.neoforgespi.language.IModFileInfo jei, int major, int minor) {
        if (jei == null || jei.getMods().isEmpty()) return false;
        var parts = jei.getMods().get(0).getVersion().toString().split("[^0-9]+");
        int seenMajor = parts.length > 0 && !parts[0].isEmpty() ? Integer.parseInt(parts[0]) : 0;
        int seenMinor = parts.length > 1 && !parts[1].isEmpty() ? Integer.parseInt(parts[1]) : 0;
        return seenMajor > major || (seenMajor == major && seenMinor >= minor);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith(".jei.JeiMultiblockLayoutMixin")) return jeiSupplyPresent;
        if (mixinClassName.endsWith(".jeisupply.JeiWirelessSupplyMixin")) return jeiLegacyTransfer;
        if (mixinClassName.contains(".jeisupply.")) return jeiSupplyPresent;
        if (mixinClassName.contains(".useless.")) return uselessJeiPresent;
        if (mixinClassName.contains(".jei.")) return jeiPresent;
        if (mixinClassName.contains(".emi.")) return emiPresent;
        return false;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(
            String targetClassName, ClassNode targetClass,
            String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(
            String targetClassName, ClassNode targetClass,
            String mixinClassName, IMixinInfo mixinInfo) {
    }
}
