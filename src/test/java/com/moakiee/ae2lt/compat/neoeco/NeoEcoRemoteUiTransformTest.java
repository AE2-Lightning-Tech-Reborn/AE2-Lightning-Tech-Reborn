package com.moakiee.ae2lt.compat.neoeco;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

class NeoEcoRemoteUiTransformTest {
    private static final String CORE_HELPER = "com/moakiee/thunderbolt/compat/neoeco/NeoEcoClientLevel";
    private static final String LT_HELPER = "com/moakiee/ae2lt/compat/neoeco/NeoEcoClientWorld";

    @Test void coreTransformedReaderIsLeftUnchanged() {
        var type = transformedReader(CORE_HELPER, CORE_HELPER);
        byte[] before = bytes(type);
        assertDoesNotThrow(() -> NeoEcoRemoteUiTransform.isolateClientWorld(type));
        assertArrayEquals(before, bytes(type), "preserve Core's client isolation and UI protocol");
    }

    @Test void ltTransformedReaderIsLeftUnchanged() {
        var type = transformedReader(LT_HELPER, LT_HELPER);
        byte[] before = bytes(type);
        assertDoesNotThrow(() -> NeoEcoRemoteUiTransform.isolateClientWorld(type));
        assertArrayEquals(before, bytes(type), "reapplying LT's transform must be idempotent");
    }

    @Test void partialAndMixedTransformsAreRejectedWithoutMutation() {
        assertRejectedUnchanged(transformedReader(CORE_HELPER));
        assertRejectedUnchanged(transformedReader(CORE_HELPER, LT_HELPER));
        var partial = transformedReader(CORE_HELPER);
        addLegacyLookup(partial.methods.getFirst());
        assertRejectedUnchanged(partial);
    }

    @Test void malformedHelperCallsAreRejectedWithoutMutation() {
        var wrongName = transformedReader(CORE_HELPER, CORE_HELPER);
        ((MethodInsnNode) wrongName.methods.getFirst().instructions.getFirst()).name = "unknown";
        assertRejectedUnchanged(wrongName);

        var wrongDescriptor = transformedReader(CORE_HELPER, CORE_HELPER);
        ((MethodInsnNode) wrongDescriptor.methods.getFirst().instructions.getFirst()).desc = "()Ljava/lang/Object;";
        assertRejectedUnchanged(wrongDescriptor);

        var virtual = transformedReader(CORE_HELPER, CORE_HELPER);
        ((MethodInsnNode) virtual.methods.getFirst().instructions.getFirst()).setOpcode(Opcodes.INVOKEVIRTUAL);
        assertRejectedUnchanged(virtual);

        var interfaceCall = transformedReader(CORE_HELPER, CORE_HELPER);
        ((MethodInsnNode) interfaceCall.methods.getFirst().instructions.getFirst()).itf = true;
        assertRejectedUnchanged(interfaceCall);

        var wrongReaderDescriptor = transformedReader(CORE_HELPER, CORE_HELPER);
        wrongReaderDescriptor.methods.getFirst().desc = "()Ljava/lang/Object;";
        assertRejectedUnchanged(wrongReaderDescriptor);

        assertRejectedUnchanged(transformedReader(CORE_HELPER, CORE_HELPER, CORE_HELPER));
    }

    @Test void transformedReaderWithResidualClientReferencesIsRejectedWithoutMutation() {
        var field = transformedReader(CORE_HELPER, CORE_HELPER);
        field.methods.getFirst().instructions.insert(new FieldInsnNode(Opcodes.GETSTATIC,
                "example/Holder", "level", "Lnet/minecraft/client/multiplayer/ClientLevel;"));
        assertRejectedUnchanged(field);

        var call = transformedReader(CORE_HELPER, CORE_HELPER);
        addLegacyLookup(call.methods.getFirst());
        assertRejectedUnchanged(call);

        var cast = transformedReader(CORE_HELPER, CORE_HELPER);
        cast.methods.getFirst().instructions.insert(new TypeInsnNode(Opcodes.CHECKCAST,
                "net/minecraft/client/multiplayer/ClientLevel"));
        assertRejectedUnchanged(cast);
    }

    @Test void upstreamGuardedReaderIsLeftUnchanged() {
        var type = guardedReader();
        byte[] before = bytes(type);
        assertDoesNotThrow(() -> NeoEcoRemoteUiTransform.isolateClientWorld(type));
        assertArrayEquals(before, bytes(type), "preserve upstream side guard and UI protocol");
    }

    @Test void unknownReaderWithNoOldLookupsIsRejected() {
        var type = guardedReader();
        var method = type.methods.getFirst();
        method.instructions.clear();
        method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        assertThrows(IllegalStateException.class, () -> NeoEcoRemoteUiTransform.isolateClientWorld(type));
    }

    @Test void invertedSideGuardAndWrongJumpDestinationAreRejected() {
        var inverted = guardedReader();
        for (var instruction : inverted.methods.getFirst().instructions)
            if (instruction instanceof FieldInsnNode field && field.name.equals("CLIENT")) field.name = "DEDICATED_SERVER";
        assertThrows(IllegalStateException.class, () -> NeoEcoRemoteUiTransform.isolateClientWorld(inverted));

        var wrongBranch = guardedReader();
        var branches = new java.util.ArrayList<JumpInsnNode>();
        for (var instruction : wrongBranch.methods.getFirst().instructions)
            if (instruction instanceof JumpInsnNode jump) branches.add(jump);
        branches.getFirst().label = branches.getLast().label;
        assertThrows(IllegalStateException.class, () -> NeoEcoRemoteUiTransform.isolateClientWorld(wrongBranch));
    }

    @Test void incompleteLegacyReaderFailsWithoutPartiallyChangingTheClass() {
        var type = guardedReader();
        var method = type.methods.getFirst();
        method.instructions.clear();
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "net/minecraft/client/Minecraft", "getInstance", "()Lnet/minecraft/client/Minecraft;", false));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD,
                "net/minecraft/client/Minecraft", "level", "Lnet/minecraft/client/multiplayer/ClientLevel;"));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        byte[] before = bytes(type);
        assertThrows(IllegalStateException.class, () -> NeoEcoRemoteUiTransform.isolateClientWorld(type));
        assertArrayEquals(before, bytes(type));
    }

    private static ClassNode guardedReader() {
        String owner = "cn/dancingsnow/neoecoae/compat/ae2/ECORemoteMachineUI";
        var type = new ClassNode();
        type.version = Opcodes.V17;
        type.access = Opcodes.ACC_PUBLIC;
        type.name = owner + "$1";
        type.superName = "java/lang/Object";
        String descriptor = "(Lnet/minecraft/network/FriendlyByteBuf;)L" + owner + "$Holder;";
        var method = new MethodNode(Opcodes.ACC_PROTECTED, "readHolderFromSyncData", descriptor, null, null);
        var server = new LabelNode();
        var done = new LabelNode();
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC,
                "net/minecraftforge/fml/loading/FMLEnvironment", "dist", "Lnet/minecraftforge/api/distmarker/Dist;"));
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC,
                "net/minecraftforge/api/distmarker/Dist", "CLIENT", "Lnet/minecraftforge/api/distmarker/Dist;"));
        method.instructions.add(new JumpInsnNode(Opcodes.IF_ACMPNE, server));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                owner + "$ClientHolderReader", "read", descriptor, false));
        method.instructions.add(new JumpInsnNode(Opcodes.GOTO, done));
        method.instructions.add(server);
        method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        method.instructions.add(done);
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.maxStack = 2;
        method.maxLocals = 2;
        type.methods.add(method);
        return type;
    }

    private static ClassNode transformedReader(String... helpers) {
        var type = guardedReader();
        var method = type.methods.getFirst();
        method.instructions.clear();
        for (String helper : helpers) {
            method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, helper, "current",
                    "()Lnet/minecraft/world/level/Level;", false));
            method.instructions.add(new InsnNode(Opcodes.POP));
        }
        method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        return type;
    }

    private static void addLegacyLookup(MethodNode method) {
        var lookup = new InsnList();
        lookup.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "net/minecraft/client/Minecraft", "getInstance", "()Lnet/minecraft/client/Minecraft;", false));
        lookup.add(new FieldInsnNode(Opcodes.GETFIELD,
                "net/minecraft/client/Minecraft", "level", "Lnet/minecraft/client/multiplayer/ClientLevel;"));
        lookup.add(new InsnNode(Opcodes.POP));
        method.instructions.insert(lookup);
    }

    private static void assertRejectedUnchanged(ClassNode type) {
        byte[] before = bytes(type);
        assertThrows(IllegalStateException.class, () -> NeoEcoRemoteUiTransform.isolateClientWorld(type));
        assertArrayEquals(before, bytes(type), "reject the entire unsupported transform before changing any instructions");
    }

    private static byte[] bytes(ClassNode type) {
        var writer = new ClassWriter(0);
        type.accept(writer);
        return writer.toByteArray();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.moakiee.ae2lt.compat.neoeco.NeoEcoPublishedFixtures#releases")
    void publishedUiRetainsItsProtocolWithoutClientTypesInSharedMethods(NeoEcoPublishedFixtures.Release release) throws Exception {
        Path jar = release.requireJar();
        try (var archive = new JarFile(jar.toFile())) {
            var type = new ClassNode();
            new ClassReader(archive.getInputStream(archive.getJarEntry(
                    "cn/dancingsnow/neoecoae/compat/ae2/ECORemoteMachineUI$1.class")))
                    .accept(type, 0);
            byte[] original = bytes(type);
            NeoEcoRemoteUiTransform.isolateClientWorld(type);
            if (release.upstreamIsolation())
                assertArrayEquals(original, bytes(type), "preserve the actual upstream guarded UI unchanged");
            int lookups = 0;
            boolean writesHolder = false, createsUi = false;
            for (var method : type.methods) {
                writesHolder |= method.name.equals("writeHolderToSyncData");
                createsUi |= method.name.equals("createUITemplate");
                for (var instruction : method.instructions.toArray()) {
                    if (instruction instanceof MethodInsnNode call) {
                        assertFalse(call.owner.startsWith("net/minecraft/client/"));
                        if (call.owner.endsWith("/NeoEcoClientWorld")) {
                            assertEquals("()Lnet/minecraft/world/level/Level;", call.desc);
                            lookups++;
                        }
                    }
                    if (instruction instanceof FieldInsnNode field)
                        assertFalse(field.desc.contains("net/minecraft/client/"));
                }
            }
            assertEquals(release.upstreamIsolation() ? 0 : 2, lookups);
            assertTrue(writesHolder && createsUi);
            byte[] isolated = bytes(type);
            NeoEcoRemoteUiTransform.isolateClientWorld(type);
            assertArrayEquals(isolated, bytes(type), "reprocessing the published UI must preserve its entire protocol");
        }
    }
}
