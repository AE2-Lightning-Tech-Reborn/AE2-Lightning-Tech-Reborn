package com.moakiee.ae2lt.compat.gto;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

class GtoCoreClientMixinExtensionTest {
    @Test
    void realDev25BinaryBindsBothUnchangedCoreCallbacks() throws Exception {
        var target = dev25Target();
        var core = coreMixin();
        var bodies = core.methods.stream().collect(Collectors.toMap(method -> method.name + method.desc,
                method -> List.of(method.instructions.toArray())));
        var preview = core.methods.stream().filter(method -> method.name.equals("thunderbolt$previewStatus"))
                .findFirst().orElseThrow();
        assertEquals(0, matchingCalls(target, modifier(preview)), "original gto.6 must reproduce the failed binding");

        GtoCoreClientMixinExtension.adapt(core, target);

        assertEquals(bodies.size(), core.methods.size());
        int hooks = 0;
        for (var method : core.methods) {
            assertEquals(bodies.get(method.name + method.desc), List.of(method.instructions.toArray()),
                    "retargeting must preserve Core's original algorithm/preview callback body");
            if (!method.name.startsWith("thunderbolt$")) continue;
            var modifier = modifier(method);
            assertEquals(1, matchingCalls(target, modifier), method.name);
            var at = (AnnotationNode) value(modifier, "at");
            var signature = (String) value(at, "target");
            int argument = (Integer) value(modifier, "index");
            assertEquals("Lnet/minecraft/network/chat/Component;",
                    Type.getArgumentTypes(signature.substring(signature.indexOf('(')))[argument].getDescriptor());
            hooks++;
        }
        assertEquals(2, hooks);
    }

    @Test
    void unexpectedClientLayoutFailsInsteadOfDroppingHooks() throws Exception {
        var target = dev25Target();
        var update = target.methods.stream().filter(method -> method.name.equals("updateBeforeRender"))
                .findFirst().orElseThrow();
        for (var instruction : update.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode call && call.owner.endsWith("/CraftingScreenTitle"))
                update.instructions.remove(call);
        }
        assertThrows(IllegalStateException.class, () -> GtoCoreClientMixinExtension.adapt(coreMixin(), target));
    }

    private static ClassNode dev25Target() throws Exception {
        var configured = System.getProperty("ae2lt.gtoDev25Ae2TestJar");
        assumeTrue(configured != null, "requires the actual AE2-GTO .40 release fixture");
        var jar = Path.of(configured);
        assertTrue(Files.isRegularFile(jar), "configured Dev25 fixture must exist");
        try (var archive = new ZipFile(jar.toFile())) {
            var entry = archive.getEntry("appeng/client/gui/me/crafting/CraftConfirmScreen.class");
            assertNotNull(entry);
            try (var input = archive.getInputStream(entry)) {
                var node = new ClassNode();
                new ClassReader(input).accept(node, 0);
                return node;
            }
        }
    }

    private static ClassNode coreMixin() throws Exception {
        try (var input = GtoCoreClientMixinExtensionTest.class.getClassLoader().getResourceAsStream(
                "com/moakiee/thunderbolt/mixin/ae2/client/CraftConfirmScreenMixin.class")) {
            assertNotNull(input);
            var node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }

    private static AnnotationNode modifier(MethodNode method) {
        return method.visibleAnnotations.stream().filter(annotation -> annotation.desc.endsWith("/ModifyArg;"))
                .findFirst().orElseThrow();
    }

    private static long matchingCalls(ClassNode target, AnnotationNode modifier) {
        var at = (AnnotationNode) value(modifier, "at");
        var signature = (String) value(at, "target");
        int ordinal = (Integer) value(at, "ordinal");
        var update = target.methods.stream().filter(method -> method.name.equals("updateBeforeRender"))
                .findFirst().orElseThrow();
        int calls = 0;
        for (var instruction : update.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && signature.equals("L" + call.owner + ";" + call.name + call.desc)) calls++;
        }
        return calls > ordinal ? 1 : 0;
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation.values != null) for (int index = 0; index < annotation.values.size(); index += 2)
            if (key.equals(annotation.values.get(index))) return annotation.values.get(index + 1);
        return null;
    }
}
