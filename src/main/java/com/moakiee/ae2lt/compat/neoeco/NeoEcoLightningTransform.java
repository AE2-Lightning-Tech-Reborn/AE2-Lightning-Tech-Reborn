package com.moakiee.ae2lt.compat.neoeco;

import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Adapts the identical land callback published in NeoECO 20.5.0/20.5.2 GTO. */
public final class NeoEcoLightningTransform {
    static final String TARGET = "net/minecraft/world/entity/LightningBolt";
    static final String MIXIN = "cn.dancingsnow.neoecoae.mixins.minecraft.LightningBoltMixin";
    static final String MERGED = "Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;";
    static final String CALLBACK = "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V";
    static final String WRITE = "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z";
    static final String HELPER = "com/moakiee/ae2lt/compat/neoeco/NeoEcoLightningProtection";
    static final String PROTECTED_WRITE = "(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/LightningBolt;)Z";

    private NeoEcoLightningTransform() {
    }

    public static void protect(ClassNode target) {
        if (!TARGET.equals(target.name)) {
            throw new IllegalStateException("NeoECO lightning protection received the wrong target: " + target.name);
        }
        var handlers = target.methods.stream()
                .filter(method -> ownedByNeoEco(method.visibleAnnotations) || ownedByNeoEco(method.invisibleAnnotations))
                .filter(method -> method.name.equals("onLightningLand") || method.name.endsWith("$onLightningLand"))
                .toList();
        if (handlers.size() != 1) {
            throw new IllegalStateException("Expected one merged NeoECO land callback, found " + handlers.size());
        }
        MethodNode handler = handlers.get(0);
        if (!CALLBACK.equals(handler.desc) || (handler.access & Opcodes.ACC_STATIC) != 0) {
            throw new IllegalStateException("NeoECO land callback signature changed: " + handler.name + handler.desc);
        }
        MethodInsnNode write = null;
        int nativeWrites = 0, protectedWrites = 0;
        for (var instruction : handler.instructions) {
            if (!(instruction instanceof MethodInsnNode call)) continue;
            if (call.getOpcode() == Opcodes.INVOKEVIRTUAL && !call.itf
                    && call.owner.equals("net/minecraft/world/level/Level")
                    && (call.name.equals("setBlockAndUpdate") || call.name.equals("m_46597_"))
                    && WRITE.equals(call.desc)) {
                write = call;
                nativeWrites++;
            }
            if (call.getOpcode() == Opcodes.INVOKESTATIC && !call.itf
                    && HELPER.equals(call.owner) && call.name.equals("setBlockAndUpdate")
                    && PROTECTED_WRITE.equals(call.desc)) {
                protectedWrites++;
            }
        }
        if (nativeWrites == 0 && protectedWrites == 1) return;
        if (nativeWrites != 1 || protectedWrites != 0) {
            throw new IllegalStateException("NeoECO land callback block-write contract changed: "
                    + nativeWrites + " native / " + protectedWrites + " protected");
        }
        // Preserve every original branch, random roll and advancement. The extra
        // receiver is this LightningBolt; no local-variable names or slots are assumed.
        handler.instructions.insertBefore(write, new VarInsnNode(Opcodes.ALOAD, 0));
        handler.instructions.set(write, new MethodInsnNode(Opcodes.INVOKESTATIC,
                HELPER, "setBlockAndUpdate", PROTECTED_WRITE, false));
        handler.maxStack = Math.max(handler.maxStack, 4);
    }

    private static boolean ownedByNeoEco(List<AnnotationNode> annotations) {
        if (annotations == null) return false;
        for (var annotation : annotations) {
            if (!MERGED.equals(annotation.desc) || annotation.values == null) continue;
            for (int i = 0; i < annotation.values.size(); i += 2) {
                if (annotation.values.get(i).equals("mixin") && MIXIN.equals(annotation.values.get(i + 1))) return true;
            }
        }
        return false;
    }
}
