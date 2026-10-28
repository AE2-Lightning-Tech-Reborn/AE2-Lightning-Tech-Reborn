package com.moakiee.ae2lt.compat.gto;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Also used by the plain-JUnit agent, so tests execute the production adaptation. */
public final class GtoKeyCounterEntryTransform {
    private static final String HELPER = "com/moakiee/ae2lt/compat/gto/GtoKeyCounterEntries";

    private GtoKeyCounterEntryTransform() { }

    public static void apply(ClassNode target) {
        if (!target.name.equals("appeng/api/stacks/KeyCounter")) {
            throw new IllegalArgumentException("Unexpected counter target: " + target.name);
        }
        int iterators = 0;
        int consumers = 0;
        for (var method : target.methods) {
            if (method.name.equals("iterator") && method.desc.equals("()Ljava/util/Iterator;")) {
                int returns = 0;
                for (var instruction : method.instructions.toArray()) {
                    if (instruction.getOpcode() != Opcodes.ARETURN) continue;
                    method.instructions.insertBefore(instruction, new MethodInsnNode(Opcodes.INVOKESTATIC,
                            HELPER, "iterator", "(Ljava/util/Iterator;)Ljava/util/Iterator;", false));
                    returns++;
                }
                if (returns == 0) throw new IllegalStateException("GTO counter iterator has no return");
                iterators++;
            } else if (method.name.equals("forEach")
                    && method.desc.equals("(Ljava/util/function/Consumer;)V")) {
                var prefix = new InsnList();
                prefix.add(new VarInsnNode(Opcodes.ALOAD, 1));
                prefix.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "consumer",
                        "(Ljava/util/function/Consumer;)Ljava/util/function/Consumer;", false));
                prefix.add(new VarInsnNode(Opcodes.ASTORE, 1));
                method.instructions.insert(prefix);
                consumers++;
            }
        }
        if (iterators != 1 || consumers != 1) {
            throw new IllegalStateException("Expected GTO counter iterator/forEach, found "
                    + iterators + "/" + consumers);
        }
    }
}
