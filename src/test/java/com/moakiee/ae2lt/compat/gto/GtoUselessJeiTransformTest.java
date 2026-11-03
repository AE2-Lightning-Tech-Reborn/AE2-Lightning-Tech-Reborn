package com.moakiee.ae2lt.compat.gto;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.util.ClassSignature;

class GtoUselessJeiTransformTest {
    private static final String PREFIX = "com/sorrowmist/useless/compat/jei/";
    private static final String CONVERTER = "appeng/api/integrations/jei/IngredientConverter";

    @ParameterizedTest
    @ValueSource(strings = {"GenericStackJeiIngredientProviders", "AEKeyJeiIngredients", "AEKeyJeiIngredients$2"})
    void actualAddonClassRetainsAllBehaviorAndOnlyChangesTheFourTypeBindings(String name) throws Exception {
        var original = actual(name);
        byte[] before = bytes(original);
        var node = actual(name);
        var cache = new CachedInfo(original);

        GtoUselessJeiTransform.apply(node);
        GtoUselessJeiTransform.synchronizeClassInfo(cache, node);

        assertDoesNotThrow(() -> GtoUselessJeiTransform.audit(node));
        assertEquals(new HashSet<>(node.interfaces), cache.interfaces);
        assertEquals(ClassSignature.of(node).toString(), cache.signature.toString());
        assertEquals(original.name, node.name);
        assertEquals(original.methods.size(), node.methods.size());
        assertEquals(original.fields.size(), node.fields.size());
        var reverse = GtoUselessJeiAbi.TYPES.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
        var restored = new ClassNode();
        node.accept(new ClassRemapper(restored, new SimpleRemapper(reverse)));
        assertArrayEquals(before, bytes(restored),
                "all method bodies, custom AEKey storage/rendering/catalog hooks and metadata must survive relocation");
    }

    @ParameterizedTest
    @ValueSource(strings = {"GenericStackJeiIngredientProviders", "AEKeyJeiIngredients", "AEKeyJeiIngredients$2"})
    void changedAddonContractsAreRejectedInsteadOfApplyingAPartialAdapter(String name) throws Exception {
        var node = actual(name);
        if (name.endsWith("$2")) node.interfaces.clear();
        else node.fields.stream().filter(field -> field.name.equals(name.equals("AEKeyJeiIngredients")
                ? "CONVERTER" : "ITEMS")).findFirst().orElseThrow().desc = "Ljava/lang/Object;";
        assertThrows(IllegalStateException.class, () -> GtoUselessJeiTransform.apply(node));
    }

    @Test
    void actualJarHasExactlyTheThreeAuditedConsumersOfTheRemovedApi() throws Exception {
        var consumers = new HashSet<String>();
        try (var archive = actualArchive()) {
            for (var entry : java.util.Collections.list(archive.entries())) {
                if (!entry.getName().endsWith(".class")) continue;
                try (var input = archive.getInputStream(entry)) {
                    var contents = new String(input.readAllBytes(), StandardCharsets.ISO_8859_1);
                    if (GtoUselessJeiAbi.TYPES.keySet().stream().anyMatch(contents::contains))
                        consumers.add(entry.getName().substring(0, entry.getName().length() - 6));
                }
            }
        }
        assertEquals(GtoUselessJeiTransform.TARGETS, consumers,
                "new consumers require a fresh audit rather than an implicit package-wide transform");
    }

    @Test
    void remapsFramesSignaturesAnnotationsClassLiteralsAndMethodHandles() {
        var node = synthetic();
        GtoUselessJeiTransform.apply(node);
        var local = GtoUselessJeiAbi.TYPES.get(CONVERTER);
        assertTrue(node.signature.contains(local));
        assertTrue(node.fields.stream().filter(field -> field.name.equals("metadata")).findFirst().orElseThrow()
                .signature.contains(local));
        var extra = node.methods.stream().filter(method -> method.name.equals("extra")).findFirst().orElseThrow();
        assertEquals("(L" + local + ";)L" + local + ";", extra.desc);
        assertTrue(extra.signature.contains(local));
        var frame = (FrameNode) extra.instructions.get(1);
        assertEquals(List.of(local), frame.local);
        assertEquals(local, ((Type) ((LdcInsnNode) extra.instructions.get(2)).cst).getInternalName());
        var handle = (Handle) ((LdcInsnNode) extra.instructions.get(4)).cst;
        assertEquals(GtoUselessJeiAbi.TYPES.get("appeng/api/integrations/jei/IngredientConverters"), handle.getOwner());
        assertEquals("(L" + local + ";)Z", handle.getDesc());
        assertDoesNotThrow(() -> GtoUselessJeiTransform.audit(node));
        extra.signature = "(L" + CONVERTER + ";)L" + CONVERTER + ";";
        assertThrows(IllegalStateException.class, () -> GtoUselessJeiTransform.audit(node));
    }

    @Test
    void unauditedTargetOrAdditionalLegacyTypeFailsExplicitly() {
        var other = synthetic();
        other.name = PREFIX + "NewIngredientConsumer";
        assertThrows(IllegalStateException.class, () -> GtoUselessJeiTransform.apply(other));
        var extraType = synthetic();
        extraType.fields.add(new FieldNode(0, "newType", "Lappeng/api/integrations/jei/NewApi;", null, null));
        assertThrows(IllegalStateException.class, () -> GtoUselessJeiTransform.apply(extraType));
    }

    @Test
    void mixinMetadataDescriptorsSignaturesAndHashSetsStayConsistentWithTheCompleteNode() {
        var node = synthetic();
        var info = new CachedInfo(node);
        GtoUselessJeiTransform.apply(node);
        GtoUselessJeiTransform.synchronizeClassInfo(info, node);
        assertEquals(new HashSet<>(node.interfaces), info.interfaces);
        assertEquals(ClassSignature.of(node).toString(), info.signature.toString());
        for (var member : info.fields) {
            assertTrue(info.fields.contains(member), "descriptor mutation must not break HashSet lookup");
            assertTrue(node.fields.stream().anyMatch(field -> field.name.equals(member.getName())
                    && field.desc.equals(member.getDesc())));
            assertFalse(member.getDesc().contains("appeng/integration/modules/jei/"));
        }
        for (var member : info.methods) {
            assertTrue(info.methods.contains(member));
            assertTrue(node.methods.stream().anyMatch(method -> method.name.equals(member.getName())
                    && method.desc.equals(member.getDesc())));
            assertFalse(member.getDesc().contains(CONVERTER));
        }
        assertTrue(info.methods.stream().filter(member -> member.name.equals("extra")).findFirst().orElseThrow()
                .originalDesc.contains(CONVERTER), "Mixin's original descriptor remains available for source matching");
    }

    @Test
    void mixinMetadataMismatchIsFatalRatherThanLeavingStaleDescriptors() {
        var node = synthetic();
        var info = new CachedInfo(node);
        info.fields.add(new CachedMember("missing", "L" + CONVERTER + ";"));
        GtoUselessJeiTransform.apply(node);
        assertThrows(IllegalStateException.class, () -> GtoUselessJeiTransform.synchronizeClassInfo(info, node));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GenericStackJeiIngredientProviders", "AEKeyJeiIngredients", "AEKeyJeiIngredients$2"})
    void targetContextCannotRestoreItsStaleSignatureAfterMixinMerging(String name) throws Exception {
        var node = actual(name);
        var info = new CachedInfo(node);
        var context = new CachedContext(info.signature);
        GtoUselessJeiTransform.apply(node);
        GtoUselessJeiTransform.synchronizeClassInfo(info, node);
        String mapped = node.signature;
        context.writeBack(node);
        if (name.endsWith("$2")) assertThrows(IllegalStateException.class, () -> GtoUselessJeiTransform.audit(node),
                "the real TargetClassContext's separate old reference reproduces the postApply failure");
        node.signature = mapped;
        GtoUselessJeiTransform.synchronizeTargetSignature(context, node);
        context.writeBack(node);
        assertEquals(ClassSignature.of(node).toString(), context.signature.toString());
        assertDoesNotThrow(() -> GtoUselessJeiTransform.audit(node));
    }

    @Test
    void changedTargetSignatureCacheIsRejected() {
        var node = synthetic();
        var unrelated = new ClassNode();
        unrelated.name = "test/Unrelated";
        unrelated.superName = "java/lang/Object";
        var context = new CachedContext(ClassSignature.of(unrelated));
        GtoUselessJeiTransform.apply(node);
        assertThrows(IllegalStateException.class, () -> GtoUselessJeiTransform.synchronizeTargetSignature(context, node));
    }

    @Test
    void anonymousConverterInterfaceCacheAndGenericSignatureUseTheSameLocalType() {
        var node = new ClassNode();
        node.version = Opcodes.V17;
        node.access = Opcodes.ACC_PUBLIC;
        node.name = PREFIX + "AEKeyJeiIngredients$2";
        node.superName = "java/lang/Object";
        node.interfaces.add(CONVERTER);
        node.signature = "Ljava/lang/Object;L" + CONVERTER + "<L" + PREFIX + "AEKeyJeiIngredients$KeyIngredient;>;";
        for (String[] protocol : new String[][] {
                {"getIngredientType", "()Lmezz/jei/api/ingredients/IIngredientType;"},
                {"getIngredientFromStack", "(Lappeng/api/stacks/GenericStack;)Ljava/lang/Object;"},
                {"getStackFromIngredient", "(Ljava/lang/Object;)Lappeng/api/stacks/GenericStack;"}}) {
            var method = new MethodNode(Opcodes.ACC_PUBLIC, protocol[0], protocol[1], null, null);
            method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
            method.instructions.add(new InsnNode(Opcodes.ARETURN));
            method.maxStack = 1;
            method.maxLocals = 2;
            node.methods.add(method);
        }
        var info = new CachedInfo(node);
        GtoUselessJeiTransform.apply(node);
        GtoUselessJeiTransform.synchronizeClassInfo(info, node);
        var local = GtoUselessJeiAbi.TYPES.get(CONVERTER);
        assertEquals(Set.of(local), info.interfaces);
        assertEquals(List.of(local), node.interfaces);
        assertTrue(info.signature.toString().contains(local));
        assertFalse(info.signature.toString().contains(CONVERTER));
    }

    @Test
    void installedMixinBinaryExposesTheInspectedCacheAndActiveDescriptorContract() throws Exception {
        var info = resource("org/spongepowered/asm/mixin/transformer/ClassInfo");
        for (String field : new String[] {"interfaces", "fields", "methods"})
            assertTrue(info.fields.stream().anyMatch(value -> value.name.equals(field) && value.desc.equals("Ljava/util/Set;")));
        assertTrue(info.fields.stream().anyMatch(value -> value.name.equals("signature")
                && value.desc.equals("Lorg/spongepowered/asm/util/ClassSignature;")));
        var member = resource("org/spongepowered/asm/mixin/transformer/ClassInfo$Member");
        for (String getter : new String[] {"getName", "getDesc"})
            GtoUselessJeiAbi.method(member, getter, "()Ljava/lang/String;", false);
        GtoUselessJeiAbi.method(member, "remapTo", "(Ljava/lang/String;)Ljava/lang/String;", false);
        var context = resource("org/spongepowered/asm/mixin/transformer/TargetClassContext");
        assertTrue(context.fields.stream().anyMatch(field -> field.name.equals("signature")
                && field.desc.equals("Lorg/spongepowered/asm/util/ClassSignature;")));
        var writeBack = context.methods.stream().filter(method -> method.name.equals("applySignature"))
                .findFirst().orElseThrow();
        assertTrue(java.util.Arrays.stream(writeBack.instructions.toArray()).anyMatch(instruction ->
                instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETFIELD
                        && field.name.equals("signature")));
    }

    private static ClassNode synthetic() {
        var node = new ClassNode();
        node.version = Opcodes.V17;
        node.access = Opcodes.ACC_PUBLIC;
        node.name = PREFIX + "GenericStackJeiIngredientProviders";
        node.superName = "java/lang/Object";
        node.interfaces.add("java/util/function/Supplier");
        node.signature = "Ljava/lang/Object;Ljava/util/function/Supplier<L" + CONVERTER + ";>;";
        node.fields.add(new FieldNode(Opcodes.ACC_STATIC, "ITEMS",
                "Lappeng/integration/modules/jei/ItemIngredientConverter;", null, null));
        node.fields.add(new FieldNode(Opcodes.ACC_STATIC, "FLUIDS",
                "Lappeng/integration/modules/jei/FluidIngredientConverter;", null, null));
        node.fields.add(new FieldNode(0, "metadata", "Ljava/util/List;", "Ljava/util/List<L" + CONVERTER + ";>;", null));
        var convert = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "convert",
                "(Lappeng/api/stacks/GenericStack;L" + CONVERTER + ";)L" + PREFIX
                        + "GenericStackJeiIngredientProvider$Ingredient;", null, null);
        convert.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        convert.instructions.add(new InsnNode(Opcodes.ARETURN));
        convert.maxStack = 1;
        convert.maxLocals = 2;
        node.methods.add(convert);
        var extra = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "extra",
                "(L" + CONVERTER + ";)L" + CONVERTER + ";",
                "<T::L" + CONVERTER + ";>(TT;)TT;", null);
        extra.instructions.add(new LabelNode());
        extra.instructions.add(new FrameNode(Opcodes.F_NEW, 1, new Object[] {CONVERTER}, 0, new Object[0]));
        extra.instructions.add(new LdcInsnNode(Type.getObjectType(CONVERTER)));
        extra.instructions.add(new InsnNode(Opcodes.POP));
        extra.instructions.add(new LdcInsnNode(new Handle(Opcodes.H_INVOKESTATIC,
                "appeng/api/integrations/jei/IngredientConverters", "register", "(L" + CONVERTER + ";)Z", false)));
        extra.instructions.add(new InsnNode(Opcodes.POP));
        extra.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        extra.instructions.add(new InsnNode(Opcodes.ARETURN));
        var annotation = new AnnotationNode("Ltest/IngredientMetadata;");
        annotation.values = List.of("ingredient", Type.getObjectType(CONVERTER));
        extra.visibleAnnotations = List.of(annotation);
        extra.maxLocals = 1;
        extra.maxStack = 1;
        node.methods.add(extra);
        return node;
    }

    private static ZipFile actualArchive() throws Exception {
        String configured = System.getProperty("ae2lt.uselessJeiTestJar");
        assumeTrue(configured != null, "requires the actual supported Useless release fixture");
        var jar = Path.of(configured);
        assertTrue(Files.isRegularFile(jar), "configured Useless archive is missing: " + jar);
        return new ZipFile(jar.toFile());
    }

    private static ClassNode actual(String name) throws Exception {
        try (var archive = actualArchive()) {
            var entry = archive.getEntry(PREFIX + name + ".class");
            assertNotNull(entry);
            try (var input = archive.getInputStream(entry)) {
                var node = new ClassNode();
                new ClassReader(input).accept(node, 0);
                return node;
            }
        }
    }

    private static ClassNode resource(String name) throws Exception {
        try (var input = GtoUselessJeiTransformTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(input);
            var node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }

    private static byte[] bytes(ClassNode node) {
        var writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    public static final class CachedContext {
        private final ClassSignature signature;
        CachedContext(ClassSignature signature) { this.signature = signature; }
        void writeBack(ClassNode node) { node.signature = signature.toString(); }
    }

    public static final class CachedInfo {
        private final Set<String> interfaces;
        private final Set<CachedMember> fields = new HashSet<>();
        private final Set<CachedMember> methods = new HashSet<>();
        private ClassSignature signature;
        CachedInfo(ClassNode node) {
            interfaces = new HashSet<>(node.interfaces);
            node.fields.forEach(field -> fields.add(new CachedMember(field.name, field.desc)));
            node.methods.forEach(method -> methods.add(new CachedMember(method.name, method.desc)));
            signature = ClassSignature.of(node);
        }
    }

    public static final class CachedMember {
        private final String name;
        private final String originalDesc;
        private String descriptor;
        CachedMember(String name, String descriptor) { this.name = name; this.originalDesc = descriptor; this.descriptor = descriptor; }
        public String getName() { return name; }
        public String getDesc() { return descriptor; }
        public String remapTo(String mapped) { return descriptor = mapped; }
        @Override public int hashCode() { return (name + descriptor).hashCode(); }
        @Override public boolean equals(Object other) {
            return other instanceof CachedMember member && name.equals(member.name) && descriptor.equals(member.descriptor);
        }
    }
}
