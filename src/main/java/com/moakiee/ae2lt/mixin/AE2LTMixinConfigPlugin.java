package com.moakiee.ae2lt.mixin;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import net.minecraftforge.fml.loading.LoadingModList;

/** Keeps mixins that target optional addons out of Mixin's target-resolution pass. */
public final class AE2LTMixinConfigPlugin implements IMixinConfigPlugin {
    private static final Map<String, String> REQUIRED_MODS = Map.of(
            "GtoCraftingPatternValidityCacheMixin", "gtocore",
            "AdvCraftingCpuAccessor", "advanced_ae",
            "AdvCraftingCpuLogicMixin", "advanced_ae",
            "InterfaceEnergyDistributionMixin", "appflux",
            "NeoEcoRemoteMachineUiMixin", "neoecoae",
            "IpnContainerClickerMixin", "inventoryprofilesnext",
            "EntityConveyorPhaseLockMixin", "mob_grinding_utils");

    @Override
    public void onLoad(String mixinPackage) {
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()
                && hasVersion("ae2", "15.2610.40") && hasVersion("thunderbolt", "2.0.3-gto.6")) {
            com.moakiee.ae2lt.compat.gto.GtoCoreClientMixinExtension.register();
        }
        if (isModLoaded("gtocore") && hasVersion("extendedae_plus", "1.5.5", "1.6.3")) {
            com.moakiee.ae2lt.compat.gto.GtoLegacyAddonMixins.registerEaep();
        }
        if (isModLoaded("gtocore") && hasVersion("useless_mod", "20.2.4.7", "20.2.4.7-gto.1")) {
            com.moakiee.ae2lt.compat.gto.GtoLegacyAddonMixins.registerUseless();
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        int separator = mixinClassName.lastIndexOf('.');
        String simpleName = separator >= 0 ? mixinClassName.substring(separator + 1) : mixinClassName;
        if (simpleName.equals("GtoHandshakePendingMessagesMixin")) {
            return isModLoaded("gtocore");
        }
        if (simpleName.equals("GtoLoginPacketFlushMixin")) {
            return isModLoaded("gtocore") && isModLoaded("krypton");
        }
        if (simpleName.equals("GtoKeyCounterEntryMixin")) {
            return isModLoaded("gtocore")
                    && (isModLoaded("extendedae_plus") || isModLoaded("useless_mod"));
        }
        if (simpleName.equals("GtoEaepProviderMixin") || simpleName.equals("GtoEaepPatternUploadMixin")
                || simpleName.equals("GtoEaepManualStateMixin")) {
            return isModLoaded("gtocore") && hasVersion("extendedae_plus", "1.5.5", "1.6.3");
        }
        String requiredMod = REQUIRED_MODS.get(simpleName);
        return requiredMod == null || isModLoaded(requiredMod);
    }

    private static boolean isModLoaded(String modId) {
        try {
            var loadingMods = LoadingModList.get();
            return loadingMods != null && loadingMods.getModFileById(modId) != null;
        } catch (RuntimeException ignored) {
            // Mixin runs during early startup. If discovery is unavailable, skipping an optional
            // target is safer than loading addon-owned classes that may not exist.
            return false;
        }
    }

    private static boolean hasVersion(String modId, String... versions) {
        var mods = LoadingModList.get();
        if (mods == null || mods.getModFileById(modId) == null) return false;
        for (var mod : mods.getModFileById(modId).getMods()) {
            if (mod.getModId().equals(modId)) {
                return java.util.Arrays.asList(versions).contains(mod.getVersion().toString());
            }
        }
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
            String targetClassName,
            ClassNode targetClass,
            String mixinClassName,
            IMixinInfo mixinInfo) {
        if (mixinClassName.endsWith(".NeoEcoRemoteMachineUiMixin")) {
            com.moakiee.ae2lt.compat.neoeco.NeoEcoRemoteUiTransform.isolateClientWorld(targetClass);
        }
        if (mixinClassName.endsWith(".GtoKeyCounterEntryMixin")) {
            com.moakiee.ae2lt.compat.gto.GtoKeyCounterEntryTransform.apply(targetClass);
        }
    }

    @Override
    public void postApply(
            String targetClassName,
            ClassNode targetClass,
            String mixinClassName,
            IMixinInfo mixinInfo) {
        if (mixinClassName.endsWith(".GtoEaepProviderMixin")) {
            com.moakiee.ae2lt.compat.gto.GtoEaepProviderTransform.apply(targetClass);
        }
        if (mixinClassName.endsWith(".GtoEaepManualStateMixin")) {
            targetClass.interfaces.add("com/extendedae_plus/api/crafting/IManualCraftingState");
        }
        if (mixinClassName.endsWith(".PatternProviderLogicMixin")) {
            com.moakiee.ae2lt.compat.gto.GtoPatternDecodeTransform.filterOrdinaryProvider(targetClass);
        }
    }
}
