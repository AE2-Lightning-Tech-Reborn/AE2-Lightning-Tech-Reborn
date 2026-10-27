package com.moakiee.ae2lt.compat.gto;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** GTO's decoder names change between releases; its input/output contract remains stable. */
public final class GtoPatternDecodeTransform {
    private GtoPatternDecodeTransform() {}
    public static void filterOrdinaryProvider(ClassNode target) {
        int count = 0;
        for (var method : target.methods) {
            if (!method.name.equals("updatePatterns") || !method.desc.equals("()V")) continue;
            for (var instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKESTATIC) continue;
                if (!call.desc.equals("(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/block/entity/BlockEntity;Lappeng/api/networking/IGrid;)Lappeng/api/crafting/IPatternDetails;")
                        && !call.desc.equals("(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;)Lappeng/api/crafting/IPatternDetails;")) continue;
                method.instructions.insert(call, new MethodInsnNode(Opcodes.INVOKESTATIC,
                        "com/moakiee/ae2lt/compat/gto/GtoPatternDecodeFilter", "ordinaryProvider",
                        "(Lappeng/api/crafting/IPatternDetails;)Lappeng/api/crafting/IPatternDetails;", false));
                count++;
            }
        }
        if (count != 1) throw new IllegalStateException("Expected one native GTO provider decoder, found " + count);
    }
}
