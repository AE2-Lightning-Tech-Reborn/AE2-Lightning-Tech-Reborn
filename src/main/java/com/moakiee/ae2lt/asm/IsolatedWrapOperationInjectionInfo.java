package com.moakiee.ae2lt.asm;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperationInjectionInfo;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.injection.struct.InjectionInfo;
import org.spongepowered.asm.mixin.injection.struct.Target;
import org.spongepowered.asm.mixin.transformer.MixinTargetContext;

/** Keeps wide operation arguments out of Mixin's potentially fragmented shared argument map. */
@InjectionInfo.AnnotationType(IsolatedWrapOperation.class)
@InjectionInfo.HandlerPrefix("wrapOperation")
public final class IsolatedWrapOperationInjectionInfo extends WrapOperationInjectionInfo {
    public IsolatedWrapOperationInjectionInfo(
            MixinTargetContext mixin, MethodNode method, AnnotationNode annotation) {
        super(mixin, method, asWrapOperation(annotation));
    }

    private static AnnotationNode asWrapOperation(AnnotationNode annotation) {
        // Let MixinExtras resolve its own Operation type, including on newer bundled versions.
        var result = new AnnotationNode(Type.getDescriptor(WrapOperation.class));
        annotation.accept(result);
        return result;
    }

    @Override
    public void lateInject() {
        Map<Target, Set<AbstractInsnNode>> originals = new IdentityHashMap<>();
        for (Target target : targetNodes.keySet()) {
            Set<AbstractInsnNode> instructions = Collections.newSetFromMap(new IdentityHashMap<>());
            Collections.addAll(instructions, target.insns.toArray());
            originals.put(target, instructions);
        }
        super.lateInject();
        originals.forEach(OperationScratchLocals::isolate);
    }
}
