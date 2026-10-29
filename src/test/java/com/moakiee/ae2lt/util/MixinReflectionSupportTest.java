package com.moakiee.ae2lt.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

class MixinReflectionSupportTest {
    private static int initializations;

    @Test
    void discoveryDoesNotInitializeAnOptionalClass() {
        var type = MixinReflectionSupport.findClassSafe(getClass().getName() + "$Uninitialized");
        assertNotNull(type);
        assertNotNull(MixinReflectionSupport.findDeclaredMethodSafe(type, "query"));
        assertEquals(0, initializations);
    }

    @Test
    void missingDescriptorDependenciesAreContainedDuringLookup() {
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "audit/BrokenApi",
                null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC, "field", "Laudit/Absent;", null, null).visitEnd();
        writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "query", "()Laudit/Absent;",
                null, null).visitEnd();
        writer.visitEnd();
        var type = new ClassLoader(getClass().getClassLoader()) {
            Class<?> define() { return defineClass("audit.BrokenApi", writer.toByteArray(), 0, writer.toByteArray().length); }
        }.define();
        assertNull(MixinReflectionSupport.findDeclaredFieldSafe(type, "field"));
        assertNull(MixinReflectionSupport.findDeclaredMethodSafe(type, "query"));
    }

    @Test
    void wrongReceiverAndArgumentsReturnFallbacks() {
        var field = MixinReflectionSupport.findDeclaredFieldSafe(Valid.class, "value");
        var method = MixinReflectionSupport.findDeclaredMethodSafe(Valid.class, "query", long.class);
        assertNull(MixinReflectionSupport.getFieldValueSafe(field, new Object()));
        assertEquals(42, MixinReflectionSupport.getLongFieldSafe(field, new Object(), 42));
        assertDoesNotThrow(() -> MixinReflectionSupport.setLongFieldSafe(field, new Object(), 1, "test write"));
        assertNull(MixinReflectionSupport.invokeMethodSafe(method, new Valid(), "test wrong argument", "wrong"));
        assertEquals(9L, MixinReflectionSupport.invokeMethodSafe(method, new Valid(), "test valid", 9L));
    }

    @Test
    void failedStaticInitializationDoesNotPoisonTheCallerOnRepeatedReads() {
        var field = MixinReflectionSupport.findDeclaredFieldSafe(BrokenInitializer.class, "value");
        assertNotNull(field);
        assertEquals(42, MixinReflectionSupport.getLongFieldSafe(field, null, 42));
        assertEquals(42, MixinReflectionSupport.getLongFieldSafe(field, null, 42));
    }

    @Test
    void optionalCpuMixinsUseTheHardenedLocalHelper() throws Exception {
        for (String name : new String[] {"AdvCraftingCpuLogicMixin", "ECOCraftingCpuLogicMixin"}) {
            String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                    "src/main/java/com/moakiee/ae2lt/mixin/thunderbolt/" + name + ".java"));
            assertTrue(source.contains("import com.moakiee.ae2lt.util.MixinReflectionSupport;"));
            assertFalse(source.contains("import com.moakiee.thunderbolt.core.util.MixinReflectionSupport;"));
        }
    }

    public static class Uninitialized {
        static { initializations++; }
        public static boolean query() { return true; }
    }

    public static class Valid {
        public long value;
        public long query(long amount) { return amount; }
    }

    public static class BrokenInitializer {
        public static long value = fail();
        private static long fail() { throw new NoSuchMethodError("Optional dependency missing"); }
    }
}
