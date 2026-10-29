package com.moakiee.ae2lt.compat.gto;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/** The ordinary pushPattern is a stub in GTO; its real dispatcher has a randomized name. */
public final class GtoEaepProviderTransform {
    private GtoEaepProviderTransform() { }

    public static void apply(ClassNode node) {
        if (!node.name.equals("appeng/helpers/patternprovider/PatternProviderLogic"))
            throw new IllegalArgumentException("Unexpected provider target: " + node.name);
        int matches = 0;
        int successHooks = 0;
        int blockingHooks = 0;
        for (var method : node.methods) {
            if (method.name.equals("onPushPatternSuccess")
                    && method.desc.equals("(Lappeng/api/crafting/IPatternDetails;)V")) {
                int returns = 0;
                for (var instruction : method.instructions.toArray()) {
                    if (instruction.getOpcode() != Opcodes.RETURN) continue;
                    var hook = new InsnList();
                    hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                            "com/moakiee/ae2lt/compat/gto/GtoEaepProviderSupport", "tryVirtualCompletion",
                            "(Lappeng/helpers/patternprovider/PatternProviderLogic;Lappeng/api/crafting/IPatternDetails;)V", false));
                    method.instructions.insertBefore(instruction, hook);
                    returns++;
                }
                if (returns == 0) throw new IllegalStateException("GTO provider success callback has no return");
                successHooks++;
            }
            if (!method.desc.startsWith("(Lappeng/api/crafting/IPatternDetails;"
                    + "Lcom/gto/datasynclib/util/holder/ObjHolder;Ljava/util/function/Supplier;)")) continue;
            for (var instruction : method.instructions.toArray()) {
                if (instruction instanceof MethodInsnNode call
                        && call.owner.equals("appeng/helpers/patternprovider/PatternProviderTarget")
                        && call.name.equals("containsPatternInput") && call.desc.equals("(Ljava/util/Set;)Z")) {
                    var arguments = new InsnList();
                    arguments.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    arguments.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    method.instructions.insertBefore(call, arguments);
                    method.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC,
                            "com/moakiee/ae2lt/compat/gto/GtoEaepProviderSupport", "containsBlockingInput",
                            "(Lappeng/helpers/patternprovider/PatternProviderTarget;Ljava/util/Set;"
                                    + "Lappeng/helpers/patternprovider/PatternProviderLogic;Lappeng/api/crafting/IPatternDetails;)Z", false));
                    blockingHooks++;
                }
                if (instruction instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKEINTERFACE
                        && call.owner.equals("java/util/List") && call.name.equals("contains")
                        && call.desc.equals("(Ljava/lang/Object;)Z")) {
                    method.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC,
                            "com/moakiee/ae2lt/compat/gto/GtoEaepProviderSupport", "contains",
                            "(Ljava/util/List;Ljava/lang/Object;)Z", false));
                    matches++;
                }
            }
        }
        if (matches != 1) throw new IllegalStateException("Expected one GTO provider lookup, found " + matches);
        if (successHooks != 1) throw new IllegalStateException("Expected one GTO provider success callback, found " + successHooks);
        if (blockingHooks != 1) throw new IllegalStateException("Expected one GTO blocking check, found " + blockingHooks);
    }
}
