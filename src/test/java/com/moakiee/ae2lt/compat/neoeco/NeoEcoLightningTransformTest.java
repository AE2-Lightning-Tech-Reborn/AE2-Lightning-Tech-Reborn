package com.moakiee.ae2lt.compat.neoeco;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

class NeoEcoLightningTransformTest {
    @ParameterizedTest
    @MethodSource("com.moakiee.ae2lt.compat.neoeco.NeoEcoPublishedFixtures#releases")
    void publishedCallbacksRetainEveryRandomRollBranchAndOtherSideEffect(
            NeoEcoPublishedFixtures.Release release) throws Exception {
        ClassNode target = new ClassNode();
        try (var archive = new JarFile(release.requireJar().toFile())) {
            var entry = archive.getJarEntry(NeoEcoLightningTransform.MIXIN.replace('.', '/') + ".class");
            assertNotNull(entry, "The supported release must contain its real lightning mixin");
            new ClassReader(archive.getInputStream(entry)).accept(target, 0);
        }
        var mixin = target.invisibleAnnotations.stream()
                .filter(annotation -> annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;"))
                .findFirst().orElseThrow();
        assertEquals(1000, annotationValue(mixin, "priority", 1000),
                "LT's priority-500 marker must follow the actual NeoECO mixin");
        var handler = target.methods.stream().filter(method -> method.name.equals("onLightningLand"))
                .findFirst().orElseThrow();
        assertEquals(NeoEcoLightningTransform.CALLBACK, handler.desc);
        assertTrue(Arrays.stream(handler.instructions.toArray()).anyMatch(instruction ->
                instruction instanceof MethodInsnNode call
                        && call.owner.equals("net/minecraft/util/RandomSource") && call.name.equals("m_188503_")),
                "The fixture must exercise NeoECO's real distance-dependent random conversion");
        // Model Mixin's merge metadata without loading any Minecraft or addon classes.
        target.name = NeoEcoLightningTransform.TARGET;
        handler.name = "handler$published$onLightningLand";
        markOwned(handler, NeoEcoLightningTransform.MIXIN);
        assertOnlyTheWriteChanges(target, handler);
    }

    @Test
    void supportsDevelopmentNamesAndDoesNotTouchAnotherModsHandler() {
        var target = target();
        var handler = handler("setBlockAndUpdate");
        target.methods.add(handler);
        var unrelated = handler("setBlockAndUpdate");
        markOwned(unrelated, "another.mod.LightningBoltMixin");
        target.methods.add(unrelated);
        var before = unrelated.instructions.toArray();
        assertOnlyTheWriteChanges(target, handler);
        assertArrayEquals(before, unrelated.instructions.toArray(), "Other mods' block writes are out of scope");
    }

    @Test
    void rejectsMissingOrAmbiguousNeoEcoOwnershipBeforeChangingInstructions() {
        var target = target();
        var handler = handler("m_46597_");
        markOwned(handler, "another.mod.LightningBoltMixin");
        target.methods.add(handler);
        var before = handler.instructions.toArray();
        assertThrows(IllegalStateException.class, () -> NeoEcoLightningTransform.protect(target));
        assertArrayEquals(before, handler.instructions.toArray());
        markOwned(handler, NeoEcoLightningTransform.MIXIN);
        target.methods.add(handler("m_46597_"));
        assertThrows(IllegalStateException.class, () -> NeoEcoLightningTransform.protect(target));
        assertArrayEquals(before, handler.instructions.toArray());
    }

    @Test
    void rejectsChangedCallbackSignatureOrStaticReceiver() {
        var target = target();
        var handler = handler("m_46597_");
        target.methods.add(handler);
        handler.desc = "()V";
        assertThrows(IllegalStateException.class, () -> NeoEcoLightningTransform.protect(target));
        handler.desc = NeoEcoLightningTransform.CALLBACK;
        handler.access |= Opcodes.ACC_STATIC;
        assertThrows(IllegalStateException.class, () -> NeoEcoLightningTransform.protect(target));
    }

    @Test
    void rejectsMissingOrDuplicateNativeWritesAndWrongTarget() {
        var target = target();
        var handler = handler("futureRenamedWrite");
        target.methods.add(handler);
        assertThrows(IllegalStateException.class, () -> NeoEcoLightningTransform.protect(target));
        handler.instructions.add(write("m_46597_"));
        handler.instructions.add(write("m_46597_"));
        assertThrows(IllegalStateException.class, () -> NeoEcoLightningTransform.protect(target));
        target.name = "another/mod/LightningBolt";
        assertThrows(IllegalStateException.class, () -> NeoEcoLightningTransform.protect(target));
    }

    private static void assertOnlyTheWriteChanges(ClassNode target, MethodNode handler) {
        var before = handler.instructions.toArray();
        var original = Arrays.stream(before).filter(instruction -> instruction instanceof MethodInsnNode call
                && call.owner.equals("net/minecraft/world/level/Level")
                && NeoEcoLightningTransform.WRITE.equals(call.desc)).findFirst().orElseThrow();
        int originalIndex = Arrays.asList(before).indexOf(original);
        NeoEcoLightningTransform.protect(target);
        var after = handler.instructions.toArray();
        assertEquals(before.length + 1, after.length);
        for (int i = 0; i < originalIndex; i++) assertSame(before[i], after[i]);
        var receiver = assertInstanceOf(VarInsnNode.class, after[originalIndex]);
        assertEquals(Opcodes.ALOAD, receiver.getOpcode());
        assertEquals(0, receiver.var);
        var protectedWrite = assertInstanceOf(MethodInsnNode.class, after[originalIndex + 1]);
        assertEquals(Opcodes.INVOKESTATIC, protectedWrite.getOpcode());
        assertEquals(NeoEcoLightningTransform.HELPER, protectedWrite.owner);
        assertEquals("setBlockAndUpdate", protectedWrite.name);
        assertEquals(NeoEcoLightningTransform.PROTECTED_WRITE, protectedWrite.desc);
        for (int i = originalIndex + 1; i < before.length; i++) assertSame(before[i], after[i + 1]);
        NeoEcoLightningTransform.protect(target);
        assertArrayEquals(after, handler.instructions.toArray(), "A repeated plugin callback must not wrap twice");
    }

    private static ClassNode target() {
        var target = new ClassNode();
        target.name = NeoEcoLightningTransform.TARGET;
        return target;
    }

    private static MethodNode handler(String writeName) {
        var handler = new MethodNode(Opcodes.ACC_PRIVATE, "handler$qa$onLightningLand",
                NeoEcoLightningTransform.CALLBACK, null, null);
        handler.instructions.add(write(writeName));
        markOwned(handler, NeoEcoLightningTransform.MIXIN);
        return handler;
    }

    private static MethodInsnNode write(String name) {
        return new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/Level",
                name, NeoEcoLightningTransform.WRITE, false);
    }

    private static void markOwned(MethodNode handler, String owner) {
        var merged = new AnnotationNode(NeoEcoLightningTransform.MERGED);
        merged.values = new ArrayList<>(List.of("mixin", owner, "priority", 1000));
        if (handler.visibleAnnotations == null) handler.visibleAnnotations = new ArrayList<>();
        handler.visibleAnnotations.removeIf(annotation -> annotation.desc.equals(NeoEcoLightningTransform.MERGED));
        handler.visibleAnnotations.add(merged);
    }

    private static Object annotationValue(AnnotationNode annotation, String key, Object fallback) {
        if (annotation.values != null) for (int i = 0; i < annotation.values.size(); i += 2) {
            if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        }
        return fallback;
    }
}
