package com.moakiee.ae2lt.mixin.recipeviewer;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.nio.file.Files;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import net.minecraftforge.fml.loading.LoadingModList;

/** Gates optional recipe-viewer mixins before their external API types are resolved. */
public final class RecipeViewerMixinPlugin implements IMixinConfigPlugin {
    private boolean jeiPresent;
    private boolean emiPresent;
    private boolean jeiSupplyPresent;
    private boolean uselessPresent;
    private boolean jeiLegacyTransfer;
    private boolean jeiButtonController;
    private final Predicate<String> targetClassPresent;

    public RecipeViewerMixinPlugin() {
        this(RecipeViewerMixinPlugin::hasTargetClass);
    }

    RecipeViewerMixinPlugin(Predicate<String> targetClassPresent) {
        this.targetClassPresent = targetClassPresent;
    }

    @Override
    public void onLoad(String mixinPackage) {
        var mods = LoadingModList.get();
        // 1.20.1: AE2 embeds its JEI integration (EncodePatternTransferHandler and friends)
        // in the main jar, so only JEI itself is required (the 1.21 standalone
        // AE2JEIIntegration mod does not exist for 1.20.1).
        emiPresent = mods.getModFileById("emi") != null;
        configureJeiTransfer(mods.getModFileById("jei"), mods.getModFileById("ae2wtlib") != null);
        uselessPresent = jeiPresent && mods.getModFileById("useless_mod") != null;
    }

    void configureJeiTransfer(net.minecraftforge.forgespi.language.IModFileInfo jei, boolean wirelessLibraryPresent) {
        jeiPresent = jei != null;
        jeiButtonController = jeiAtLeast(jei, 15, 68);
        jeiSupplyPresent = jeiPresent && wirelessLibraryPresent;
        // JEI 15.57 introduced the service/context API; 15.68 moved button and keyboard
        // transfers into a controller. Retained legacy classes are no longer the active path.
        jeiLegacyTransfer = jeiSupplyPresent && !jeiAtLeast(jei, 15, 57);
    }

    private static boolean jeiAtLeast(net.minecraftforge.forgespi.language.IModFileInfo jei, int major, int minor) {
        if (jei == null || jei.getMods().isEmpty()) return false;
        return jei.getMods().get(0).getVersion().compareTo(new DefaultArtifactVersion(major + "." + minor + ".0")) >= 0;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        boolean enabled;
        if (mixinClassName.endsWith(".jeisupply.JeiWirelessSupplyMixin")) enabled = jeiLegacyTransfer;
        else if (mixinClassName.endsWith(".jeisupply.JeiWirelessSupplyServiceMixin")
                || mixinClassName.endsWith(".jeisupply.JeiWirelessSupplyContextAccessor"))
            enabled = jeiSupplyPresent && !jeiLegacyTransfer;
        else if (mixinClassName.endsWith(".jei.JeiLegacyRecipeTransferButtonMixin"))
            enabled = jeiPresent && !jeiButtonController;
        else if (mixinClassName.endsWith(".jei.JeiRecipeTransferButtonControllerMixin"))
            enabled = jeiPresent && jeiButtonController;
        else if (mixinClassName.contains(".jemi.")) enabled = jeiPresent && emiPresent;
        else if (mixinClassName.contains(".useless.")) enabled = uselessPresent;
        else if (mixinClassName.contains(".jeisupply.")) enabled = jeiSupplyPresent;
        else if (mixinClassName.contains(".jei.")) enabled = jeiPresent;
        else if (mixinClassName.contains(".emi.")) enabled = emiPresent;
        else return false;
        return enabled && targetClassPresent.test(targetClassName);
    }

    private static boolean hasTargetClass(String targetClassName) {
        // Inspect resources without loading optional API classes.
        String resource = targetClassName.replace('.', '/') + ".class";
        var mods = LoadingModList.get();
        return mods != null && mods.getModFiles().stream()
                .anyMatch(file -> Files.isRegularFile(file.getFile().findResource(resource)));
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
