package com.moakiee.ae2lt.mixin.recipeviewer;

import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import net.minecraftforge.fml.loading.LoadingModList;

/** Gates optional recipe-viewer mixins before their external API types are resolved. */
public final class RecipeViewerMixinPlugin implements IMixinConfigPlugin {
    private boolean uselessPresent;
    private boolean jeiPresent;
    private boolean emiPresent;
    private boolean jeiSupplyPresent;
    private boolean jeiLegacyTransfer;
    private boolean jeiButtonController;
    private boolean modernJeiLayouts;

    @Override
    public void onLoad(String mixinPackage) {
        var mods = LoadingModList.get();
        // 1.20.1: AE2 embeds its JEI integration (EncodePatternTransferHandler and friends)
        // in the main jar, so only JEI itself is required (the 1.21 standalone
        // AE2JEIIntegration mod does not exist for 1.20.1).
        uselessPresent = mods.getModFileById("useless_mod") != null;
        jeiPresent = mods.getModFileById("jei") != null;
        emiPresent = mods.getModFileById("emi") != null;
        configureJeiTransfer(mods.getModFileById("jei"), mods.getModFileById("ae2wtlib") != null);
    }

    void configureJeiTransfer(net.minecraftforge.forgespi.language.IModFileInfo jei, boolean wirelessLibraryPresent) {
        jeiPresent = jei != null;
        jeiButtonController = jeiAtLeast(jei, 15, 68);
        jeiSupplyPresent = jeiPresent && wirelessLibraryPresent;
        // JEI 15.57 introduced RecipeTransferService and IRecipeTransferContext on 1.20.1.
        jeiLegacyTransfer = jeiSupplyPresent && !jeiAtLeast(jei, 15, 57);
        modernJeiLayouts = jeiPresent && hasModernJeiLayouts();
    }

    private static boolean hasModernJeiLayouts() {
        try (var stream = RecipeViewerMixinPlugin.class.getClassLoader().getResourceAsStream(
                "mezz/jei/gui/recipes/RecipeGuiLogic.class")) {
            if (stream == null) return false;
            var type = new ClassNode();
            new org.objectweb.asm.ClassReader(stream).accept(type,
                    org.objectweb.asm.ClassReader.SKIP_CODE | org.objectweb.asm.ClassReader.SKIP_DEBUG
                            | org.objectweb.asm.ClassReader.SKIP_FRAMES);
            return type.methods.stream().anyMatch(method -> method.name.equals("getVisibleRecipeLayoutsWithButtons")
                    && method.desc.startsWith("(Lmezz/jei/common/util/ImmutableSize2i;"));
        } catch (java.io.IOException ignored) {
            return false;
        }
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
        if (mixinClassName.contains(".useless.")) return jeiPresent && uselessPresent;
        if (mixinClassName.endsWith(".jei.JeiMultiblockLayoutMixin")) return jeiPresent && !modernJeiLayouts;
        if (mixinClassName.endsWith(".jei.JeiModernMultiblockLayoutMixin")) return jeiPresent && modernJeiLayouts;
        if (mixinClassName.endsWith(".jeisupply.JeiWirelessSupplyMixin")) return jeiLegacyTransfer;
        if (mixinClassName.endsWith(".jeisupply.JeiWirelessSupplyServiceMixin")
                || mixinClassName.endsWith(".jeisupply.JeiWirelessSupplyContextAccessor")) {
            return jeiSupplyPresent && !jeiLegacyTransfer;
        }
        if (mixinClassName.endsWith(".jei.JeiLegacyRecipeTransferButtonMixin")) return jeiPresent && !jeiButtonController;
        if (mixinClassName.endsWith(".jei.JeiRecipeTransferButtonControllerMixin")) return jeiPresent && jeiButtonController;
        if (mixinClassName.contains(".jeisupply.")) return jeiSupplyPresent;
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
