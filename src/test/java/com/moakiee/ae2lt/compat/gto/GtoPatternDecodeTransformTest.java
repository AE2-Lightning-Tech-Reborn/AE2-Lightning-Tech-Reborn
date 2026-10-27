package com.moakiee.ae2lt.compat.gto;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

class GtoPatternDecodeTransformTest {
    private static final String RESULT = "Lappeng/api/crafting/IPatternDetails;";
    private static final String NATIVE = "(Lnet/minecraft/world/item/ItemStack;"
            + "Lnet/minecraft/world/level/block/entity/BlockEntity;Lappeng/api/networking/IGrid;)" + RESULT;

    private static ClassNode provider(String descriptor, int decoders) {
        var type = new ClassNode();
        var method = new MethodNode(Opcodes.ACC_PUBLIC, "updatePatterns", "()V", null, null);
        type.methods.add(method);
        for (int i = 0; i < decoders; i++) {
            method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "com/gtolib/randomized/\u4e00", "\u4e8c", descriptor, false));
            method.instructions.add(new InsnNode(Opcodes.POP));
        }
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        return type;
    }

    @Test void filtersDecodedResultWithoutChangingRandomizedNativeCall() {
        var type = provider(NATIVE, 1);
        var call = (MethodInsnNode) type.methods.get(0).instructions.getFirst();
        GtoPatternDecodeTransform.filterOrdinaryProvider(type);
        assertEquals("com/gtolib/randomized/\u4e00", call.owner);
        assertEquals(NATIVE, call.desc);
        var filter = assertInstanceOf(MethodInsnNode.class, call.getNext());
        assertEquals("ordinaryProvider", filter.name);
        assertEquals("(" + RESULT + ")" + RESULT, filter.desc);
        assertEquals(Opcodes.POP, filter.getNext().getOpcode());
    }

    @Test void failsExplicitlyWhenNativeDecoderContractChanges() {
        assertThrows(IllegalStateException.class,
                () -> GtoPatternDecodeTransform.filterOrdinaryProvider(provider("()" + RESULT, 1)));
        assertThrows(IllegalStateException.class,
                () -> GtoPatternDecodeTransform.filterOrdinaryProvider(provider(NATIVE, 2)));
    }
}
