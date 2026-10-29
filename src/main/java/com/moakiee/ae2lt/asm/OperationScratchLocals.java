package com.moakiee.ae2lt.asm;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.injection.struct.Target;

/** Relocates only newly emitted scratch stores and loads, preserving all pre-existing locals. */
final class OperationScratchLocals {
    private OperationScratchLocals() {
    }

    static void isolate(Target target, Set<AbstractInsnNode> originals) {
        Map<Local, Integer> replacements = new HashMap<>();
        for (AbstractInsnNode instruction : target.insns) {
            if (!originals.contains(instruction) && instruction instanceof VarInsnNode variable
                    && variable.getOpcode() >= Opcodes.ISTORE && variable.getOpcode() <= Opcodes.ASTORE) {
                Local local = new Local(variable.var, variable.getOpcode() - Opcodes.ISTORE);
                replacements.computeIfAbsent(local, key -> target.allocateLocals(
                        key.type() == Opcodes.LSTORE - Opcodes.ISTORE
                                || key.type() == Opcodes.DSTORE - Opcodes.ISTORE ? 2 : 1));
            }
        }
        for (AbstractInsnNode instruction : target.insns) {
            if (!originals.contains(instruction) && instruction instanceof VarInsnNode variable) {
                int opcode = variable.getOpcode();
                int type = opcode >= Opcodes.ISTORE && opcode <= Opcodes.ASTORE
                        ? opcode - Opcodes.ISTORE : opcode - Opcodes.ILOAD;
                Integer replacement = replacements.get(new Local(variable.var, type));
                if (replacement != null) {
                    variable.var = replacement;
                }
            }
        }
    }

    private record Local(int index, int type) {
    }
}
