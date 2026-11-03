package com.moakiee.ae2lt.asm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.injection.struct.Target;

class IsolatedWrapOperationInjectionInfoTest {
    @Test
    void fragmentedMixinArgumentMapOverwritesCallbackWithoutIsolation() {
        Fixture fixture = fixture();
        assertEquals(29, fixture.callback().var);
        assertThrows(VerifyError.class, () -> invoke(fixture.target().classNode));
    }

    @Test
    void isolatedWideArgumentsPassJvmVerificationAndPreserveExistingCallback() throws Exception {
        Fixture fixture = fixture();
        OperationScratchLocals.isolate(fixture.target(), fixture.originals());
        assertEquals(29, fixture.callback().var, "Existing callbacks must keep their original locals");
        assertNotNull(invoke(fixture.target().classNode));
    }

    private static Fixture fixture() {
        ClassNode owner = new ClassNode();
        owner.version = Opcodes.V17;
        owner.access = Opcodes.ACC_PUBLIC;
        owner.name = "IsolatedOperationFixture";
        owner.superName = "java/lang/Object";
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "run", "()Ljava/lang/Object;", null, null);
        method.maxLocals = 27;
        owner.methods.add(method);
        Target target = new Target(owner, method);
        Type ref = Type.getType(Object.class);

        target.generateArgMap(new Type[]{ref, ref}, 0);
        int callbackIndex = target.allocateLocal();
        target.generateArgMap(new Type[]{ref, ref, ref}, 0);

        method.instructions.add(new TypeInsnNode(Opcodes.NEW, "java/lang/Object"));
        method.instructions.add(new InsnNode(Opcodes.DUP));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "java/lang/Object", "<init>", "()V", false));
        var callback = new VarInsnNode(Opcodes.ASTORE, callbackIndex);
        method.instructions.add(callback);
        var callbackLoad = new VarInsnNode(Opcodes.ALOAD, callbackIndex);
        method.instructions.add(callbackLoad);
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        Set<AbstractInsnNode> originals = Collections.newSetFromMap(new IdentityHashMap<>());
        Collections.addAll(originals, method.instructions.toArray());

        // The real Mixin allocator reuses 28..29 for the first double, clobbering the callback.
        int[] arguments = target.generateArgMap(new Type[]{
                ref, Type.DOUBLE_TYPE, Type.DOUBLE_TYPE, Type.DOUBLE_TYPE}, 0);
        assertEquals(callbackIndex, arguments[1] + 1);
        InsnList wrapper = new InsnList();
        wrapper.add(new InsnNode(Opcodes.ACONST_NULL));
        wrapper.add(new VarInsnNode(Opcodes.ASTORE, arguments[0]));
        for (int i = 1; i < arguments.length; i++) {
            wrapper.add(new InsnNode(Opcodes.DCONST_0));
            wrapper.add(new VarInsnNode(Opcodes.DSTORE, arguments[i]));
        }
        wrapper.add(new VarInsnNode(Opcodes.ALOAD, arguments[0]));
        wrapper.add(new InsnNode(Opcodes.POP));
        for (int i = 1; i < arguments.length; i++) {
            wrapper.add(new VarInsnNode(Opcodes.DLOAD, arguments[i]));
            wrapper.add(new InsnNode(Opcodes.POP2));
        }
        method.instructions.insertBefore(callbackLoad, wrapper);
        return new Fixture(target, originals, callback);
    }

    private static Object invoke(ClassNode owner) throws Exception {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        owner.accept(writer);
        byte[] bytes = writer.toByteArray();
        Class<?> fixture = new ClassLoader(IsolatedWrapOperationInjectionInfoTest.class.getClassLoader()) {
            Class<?> loadFixture() {
                return defineClass(null, bytes, 0, bytes.length);
            }
        }.loadFixture();
        return fixture.getMethod("run").invoke(null);
    }

    private record Fixture(Target target, Set<AbstractInsnNode> originals, VarInsnNode callback) {
    }
}
