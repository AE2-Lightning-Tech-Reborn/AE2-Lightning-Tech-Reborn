package com.moakiee.ae2lt;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

class ThunderboltMixinBoundaryTest {
    @Test
    void ae2ltDoesNotDependOnThunderboltMixinImplementationClasses() throws Exception {
        // A MixinSquared compatibility selector is a String, not a verifier/linker type
        // dependency. Inspect every compiled type reference instead of source substrings.
        try (var files = Files.walk(Path.of("build/classes/java/main"))) {
            var classes = files.filter(path -> path.toString().endsWith(".class")).toList();
            assertFalse(classes.isEmpty(), "compiled production classes must be checked");
            for (var file : classes) assertNoImplementationTypes(Files.readAllBytes(file));
        }
    }

    @Test
    void rejectsDirectAccessorDependency() {
        assertThrows(AssertionError.class, () -> assertNoImplementationTypes(example(true)));
    }

    @Test
    void allowsCompatibilitySelectorWithoutLinkingItsClass() {
        assertDoesNotThrow(() -> assertNoImplementationTypes(example(false)));
    }

    private static void assertNoImplementationTypes(byte[] bytecode) {
        new ClassReader(bytecode).accept(new ClassRemapper(new ClassWriter(0), new Remapper() {
            @Override public String map(String internalName) {
                assertFalse(internalName.startsWith("com/moakiee/thunderbolt/mixin/"),
                        () -> "AE2LT must own its accessors instead of linking Thunderbolt mixins: " + internalName);
                return internalName;
            }
        }), ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }

    private static byte[] example(boolean directDependency) {
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "BoundaryExample", null, "java/lang/Object", null);
        if (directDependency) {
            writer.visitField(Opcodes.ACC_PRIVATE, "accessor",
                    "Lcom/moakiee/thunderbolt/mixin/example/Accessor;", null, null).visitEnd();
        } else {
            writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                    "selector", "Ljava/lang/String;", null,
                    "com.moakiee.thunderbolt.mixin.example.Accessor").visitEnd();
        }
        writer.visitEnd();
        return writer.toByteArray();
    }
}
