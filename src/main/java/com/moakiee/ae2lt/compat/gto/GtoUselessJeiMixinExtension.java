package com.moakiee.ae2lt.compat.gto;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;
import org.spongepowered.asm.mixin.transformer.ext.IExtension;
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext;

/** Transform the three marked addon classes before Mixin consumes their cached interface metadata. */
public final class GtoUselessJeiMixinExtension implements IExtension {
    private static boolean registered;

    public static synchronized void register() {
        if (registered) return;
        var transformer = (IMixinTransformer) MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
        if (transformer == null) throw new IllegalStateException("Useless JEI adapter requires the active Mixin transformer");
        ((Extensions) transformer.getExtensions()).add(new GtoUselessJeiMixinExtension());
        registered = true;
    }

    @Override public boolean checkActive(MixinEnvironment environment) { return true; }

    @Override public void preApply(ITargetClassContext context) {
        var node = context.getClassNode();
        if (!GtoUselessJeiTransform.TARGETS.contains(node.name)) return;
        GtoUselessJeiTransform.apply(node);
        GtoUselessJeiTransform.synchronizeClassInfo(context.getClassInfo(), node);
        GtoUselessJeiTransform.synchronizeTargetSignature(context, node);
    }

    @Override public void postApply(ITargetClassContext context) {
        if (GtoUselessJeiTransform.TARGETS.contains(context.getClassNode().name)) {
            GtoUselessJeiTransform.audit(context.getClassNode());
        }
    }

    @Override public void export(MixinEnvironment environment, String name, boolean force, ClassNode node) { }
}
