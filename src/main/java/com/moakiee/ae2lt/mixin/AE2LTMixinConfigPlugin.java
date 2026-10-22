package com.moakiee.ae2lt.mixin;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.nio.file.Files;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import net.minecraftforge.fml.loading.LoadingModList;

/** Keeps mixins that target optional addons out of Mixin's target-resolution pass. */
public final class AE2LTMixinConfigPlugin implements IMixinConfigPlugin {
    private static final Map<String, String> REQUIRED_MODS = Map.ofEntries(
            Map.entry("AdvCraftingCpuAccessor", "advanced_ae"),
            Map.entry("AdvCraftingCpuLogicMixin", "advanced_ae"),
            Map.entry("InterfaceEnergyDistributionMixin", "appflux"),
            Map.entry("IpnContainerClickerMixin", "inventoryprofilesnext"),
            Map.entry("EntityConveyorPhaseLockMixin", "mob_grinding_utils"),
            Map.entry("TransfiniteCraftingLogicMixin", "gtlcore"),
            Map.entry("TransfiniteComputationArrayMachineMixin", "gtlcore"),
            Map.entry("MECraftingCPUInterfacePartMachineMixin", "gtlcore"),
            Map.entry("PatternMigrationEaeBatchMixin", "expatternprovider"),
            Map.entry("PatternMigrationEaepBatchMixin", "extendedae_plus"),
            Map.entry("ExtendedAePlusUpgradeCallbackMixin", "extendedae_plus"));

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        int separator = mixinClassName.lastIndexOf('.');
        String simpleName = separator >= 0 ? mixinClassName.substring(separator + 1) : mixinClassName;
        String requiredMod = REQUIRED_MODS.get(simpleName);
        if (requiredMod == null) {
            return true;
        }
        return isTargetPresent(requiredMod, targetClassName);
    }

    private static boolean isTargetPresent(String modId, String targetClassName) {
        try {
            var loadingMods = LoadingModList.get();
            var mod = loadingMods == null ? null : loadingMods.getModFileById(modId);
            // Inspect the discovered mod file without defining or transforming its classes.
            // Some integrations (AppFlux) modify an AE2 class rather than an addon-owned one.
            if (mod == null) return false;
            String resource = targetClassName.replace('.', '/') + ".class";
            return loadingMods.getModFiles().stream()
                    .anyMatch(file -> Files.isRegularFile(file.getFile().findResource(resource)));
        } catch (RuntimeException ignored) {
            // Mixin runs during early startup. If discovery is unavailable, skipping an optional
            // target is safer than loading addon-owned classes that may not exist.
            return false;
        }
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
    }

    @Override
    public void postApply(
            String targetClassName,
            ClassNode targetClass,
            String mixinClassName,
            IMixinInfo mixinInfo) {
    }
}
