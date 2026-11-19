package com.moakiee.ae2lt.mixin.recipeviewer;

import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
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
        configureJeiTransfer(mods.getModFileById("jei"));
        jeiPresent = mods.getModFileById("jei") != null
                && mods.getModFileById("ae2jeiintegration") != null;
        emiPresent = mods.getModFileById("emi") != null;
        uselessJeiPresent = mods.getModFileById("useless_mod") != null
                && mods.getModFileById("jei") != null;
    }

    void configureJeiTransfer(net.neoforged.neoforgespi.language.IModFileInfo jei) {
        jeiSupplyPresent = jei != null;
        // JEI 19.52 introduced RecipeTransferService and IRecipeTransferContext together.
        jeiLegacyTransfer = jeiSupplyPresent && !jeiAtLeast(jei, 19, 52);
    }

    private static boolean jeiAtLeast(net.neoforged.neoforgespi.language.IModFileInfo jei, int major, int minor) {
        if (jei == null || jei.getMods().isEmpty()) return false;
        return jei.getMods().get(0).getVersion().compareTo(new DefaultArtifactVersion(major + "." + minor + ".0")) >= 0;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith(".jei.JeiMultiblockLayoutMixin")
                || mixinClassName.endsWith(".jei.JeiRecipeTransferButtonControllerMixin")) return jeiSupplyPresent;
        if (mixinClassName.endsWith(".jeisupply.JeiWirelessSupplyMixin")) return jeiLegacyTransfer;
        if (mixinClassName.endsWith(".jeisupply.JeiWirelessSupplyServiceMixin")) {
            return jeiSupplyPresent && !jeiLegacyTransfer;
        }

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
