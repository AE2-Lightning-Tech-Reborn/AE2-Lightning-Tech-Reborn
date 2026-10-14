package com.moakiee.ae2lt.mixin.mest;

import java.util.List;
import java.util.Set;
import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class MestMixinPlugin implements IMixinConfigPlugin {
    private boolean present;
    @Override public void onLoad(String mixinPackage) {
        present = LoadingModList.get().getModFileById("mesplicedterminal") != null;
    }
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        return present && (!mixin.endsWith("MestJeiWorkstationMixin")
                || LoadingModList.get().getModFileById("jei") != null);
    }
    @Override public String getRefMapperConfig() { return null; }
    @Override public List<String> getMixins() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public void preApply(String name, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String name, ClassNode node, String mixin, IMixinInfo info) {}
}
