package com.moakiee.ae2lt.mixin.recipeviewer;

import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import com.moakiee.ae2lt.compat.gto.GtoUselessJeiAbi;
import com.moakiee.ae2lt.compat.gto.GtoUselessJeiMixinExtension;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.forgespi.language.IModFileInfo;

/** Gates optional recipe-viewer mixins before their external API types are resolved. */
public final class RecipeViewerMixinPlugin implements IMixinConfigPlugin {
    private boolean uselessPresent;
    private boolean jeiPresent;
    private boolean emiPresent;
    private boolean gtoPresent;
    private boolean jemiLifecycle;
    private boolean jeiSupplyPresent;
    private boolean jeiLegacyTransfer;
    private boolean jeiButtonController;
    private boolean modernJeiLayouts;
    private boolean uselessJeiAbi;

    @Override
    public void onLoad(String mixinPackage) {
        var mods = LoadingModList.get();
        // 1.20.1: AE2 embeds its JEI integration (EncodePatternTransferHandler and friends)
        // in the main jar, so only JEI itself is required (the 1.21 standalone
        // AE2JEIIntegration mod does not exist for 1.20.1).
        uselessPresent = mods.getModFileById("useless_mod") != null;
        jeiPresent = mods.getModFileById("jei") != null;
        emiPresent = mods.getModFileById("emi") != null;
        gtoPresent = mods.getModFileById("gtocore") != null;
        var emi = mods.getModFileById("emi");
        jemiLifecycle = emi != null && emi.getMods().stream().anyMatch(mod -> mod.getModId().equals("emi")
                && Set.of("1.1.25", "1.1.1027").contains(mod.getVersion().toString()));
        configureJeiTransfer(mods.getModFileById("jei"), mods.getModFileById("ae2wtlib") != null);
        configureUselessJei(
                FMLLoader.getDist().isClient() && gtoPresent && jeiPresent && uselessPresent,
                modVersion(mods.getModFileById("ae2"), "ae2"),
                modVersion(mods.getModFileById("useless_mod"), "useless_mod"), getClass().getClassLoader(),
                GtoUselessJeiMixinExtension::register);
    }

    void configureUselessJei(boolean eligible, String aeVersion, String uselessVersion,
            ClassLoader loader, Runnable installExtension) {
        uselessJeiAbi = GtoUselessJeiAbi.configure(eligible, aeVersion, uselessVersion, loader);
        if (uselessJeiAbi) installExtension.run();
    }

    private static String modVersion(IModFileInfo file, String id) {
        if (file == null) return "";
        return file.getMods().stream().filter(mod -> mod.getModId().equals(id))
                .map(mod -> mod.getVersion().toString()).findFirst().orElse("");
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
        if (mixinClassName.endsWith(".emi.GtoEmiPluginDiscoveryMixin")) return gtoPresent && emiPresent;
        if (mixinClassName.endsWith(".emi.GtoJemiLifecycleMixin")) return gtoPresent && jeiPresent && jemiLifecycle;
        if (mixinClassName.endsWith(".emi.GtoJemiRecipeTypesMixin")) return gtoPresent && jeiPresent && jemiLifecycle;
        if (mixinClassName.endsWith(".emi.GtoJemiServicesMixin")) return gtoPresent && jeiPresent && jemiLifecycle;
        if (mixinClassName.endsWith(".jei.GtoJeiPluginDiscoveryMixin")) return gtoPresent && jeiPresent;
        if (mixinClassName.endsWith(".useless.GtoUselessJeiAbiMixin")) return uselessJeiAbi;
        if (mixinClassName.contains(".useless.")) return jeiPresent && uselessPresent;
        if (mixinClassName.endsWith(".jei.JeiMultiblockLayoutMixin")) return jeiPresent && !modernJeiLayouts;
        if (mixinClassName.endsWith(".jei.JeiModernMultiblockLayoutMixin")) return jeiPresent && modernJeiLayouts;
        if (mixinClassName.endsWith(".jeisupply.JeiWirelessSupplyMixin")) return jeiLegacyTransfer;
        if (mixinClassName.endsWith(".jeisupply.JeiWirelessSupplyServiceMixin")) {
            return jeiSupplyPresent && !jeiLegacyTransfer;
        }
        if (mixinClassName.endsWith(".jei.JeiLegacyRecipeTransferButtonMixin")) return jeiPresent && !jeiButtonController;
        if (mixinClassName.endsWith(".jei.JeiRecipeTransferButtonControllerMixin")) return jeiPresent && jeiButtonController;
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
