package com.moakiee.ae2lt.compat;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Static release-byte audit. Never resolves Core/AE2 through JUnit's same-package fixtures. */
@EnabledIfSystemProperty(named = "ae2lt.coreConsumerAudit.enabled", matches = "true")
class CoreConsumerAbiAuditTest {
    private static final String CORE = "com/moakiee/thunderbolt/";
    private static final String LT = "com/moakiee/ae2lt/";
    private static final Map<String, Map<String, ClassNode>> INPUTS = new HashMap<>();
    private static final SortedSet<String> TYPES = new TreeSet<>();
    private static final SortedSet<Link> LINKS = new TreeSet<>(Comparator.comparing(Link::toString));
    private static Path evidence;

    record Link(String caller, String owner, String name, String desc, boolean field,
                boolean isStatic, boolean interfaceOwner) {}
    record Member(String owner, int access) {}

    @BeforeAll
    static void readPinnedReleaseInputs() throws Exception {
        evidence = Path.of(required("evidence"));
        Files.createDirectories(evidence);
        var identities = new ArrayList<String>();
        for (String id : List.of("lt", "core", "ae2Dev23", "ae2Dev25")) {
            var path = Path.of(required(id + ".jar"));
            var sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
            assertEquals(required(id + ".sha256").toLowerCase(Locale.ROOT), sha, id + " release identity");
            identities.add(id + "\t" + sha + "\t" + Files.size(path));
            var classes = new HashMap<String, ClassNode>();
            try (var jar = new ZipFile(path.toFile())) {
                for (var entries = jar.entries(); entries.hasMoreElements();) {
                    var entry = entries.nextElement();
                    if (!entry.getName().endsWith(".class") || entry.getName().startsWith("META-INF/")) continue;
                    try (var in = jar.getInputStream(entry)) {
                        var node = new ClassNode();
                        new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                        assertNull(classes.put(node.name, node), "Duplicate class " + node.name);
                    }
                }
            }
            assertFalse(classes.isEmpty(), id + " has no class bytes");
            INPUTS.put(id, classes);
        }
        for (var type : INPUTS.get("lt").values()) {
            if (!type.name.startsWith(LT)) continue;
            referenceType(type.superName);
            type.interfaces.forEach(CoreConsumerAbiAuditTest::referenceType);
            for (var field : type.fields) referenceDescriptor(field.desc);
            for (var method : type.methods) {
                referenceDescriptor(method.desc);
                if (method.exceptions != null) method.exceptions.forEach(CoreConsumerAbiAuditTest::referenceType);
                for (var insn : method.instructions) {
                    if (insn instanceof MethodInsnNode call) {
                        reference(new Link(type.name, call.owner, call.name, call.desc, false,
                                call.getOpcode() == Opcodes.INVOKESTATIC, call.itf));
                    } else if (insn instanceof FieldInsnNode field) {
                        reference(new Link(type.name, field.owner, field.name, field.desc, true,
                                field.getOpcode() == Opcodes.GETSTATIC || field.getOpcode() == Opcodes.PUTSTATIC, false));
                    } else if (insn instanceof TypeInsnNode used) {
                        referenceType(used.desc);
                    } else if (insn instanceof MultiANewArrayInsnNode used) {
                        referenceDescriptor(used.desc);
                    } else if (insn instanceof LdcInsnNode constant) {
                        referenceConstant(type.name, constant.cst);
                    } else if (insn instanceof InvokeDynamicInsnNode dynamic) {
                        referenceDescriptor(dynamic.desc);
                        referenceConstant(type.name, dynamic.bsm);
                        for (var arg : dynamic.bsmArgs) referenceConstant(type.name, arg);
                    }
                }
            }
        }
        assertFalse(LINKS.isEmpty(), "LT archive has no Core consumers");
        Files.write(evidence.resolve("inputs.tsv"), identities, StandardCharsets.UTF_8);
        Files.write(evidence.resolve("core-types.txt"), TYPES, StandardCharsets.UTF_8);
        Files.write(evidence.resolve("core-consumers.tsv"), LINKS.stream().map(Link::toString).toList(), StandardCharsets.UTF_8);
    }

    @Test
    void releaseCoreSymbolsResolveAgainstDev23() throws Exception { audit("ae2Dev23"); }

    @Test
    void releaseCoreSymbolsResolveAgainstDev25() throws Exception { audit("ae2Dev25"); }

    private static void audit(String ae2) throws Exception {
        var repository = repository(ae2);
        for (var type : TYPES) {
            var resolved = repository.get(type);
            assertNotNull(resolved, ae2 + ": missing Core type " + type);
            assertTrue((resolved.access & Opcodes.ACC_PUBLIC) != 0, "Inaccessible Core type " + type);
        }
        for (var link : LINKS) verifyLink(repository, link);
        Files.writeString(evidence.resolve(ae2 + ".txt"), "Static symbols passed: " + TYPES.size()
                + " Core types; " + LINKS.size() + " distinct caller/member references.\n");
    }

    @Test
    void concreteLtConsumersStillSatisfyCoreAbstractContractsAndInheritance() throws Exception {
        var inspected = new TreeSet<String>();
        for (var ae2 : List.of("ae2Dev23", "ae2Dev25")) {
            var repository = repository(ae2);
            for (var type : INPUTS.get("lt").values()) {
                if (!type.name.startsWith(LT)) continue;
                var ancestors = ancestors(repository, type.name);
                for (var ancestor : ancestors) {
                    if (!ancestor.name.startsWith(CORE)) continue;
                    inspected.add(type.name + " -> " + ancestor.name);
                    if ((ancestor.access & Opcodes.ACC_INTERFACE) == 0)
                        assertEquals(0, ancestor.access & Opcodes.ACC_FINAL, "LT extends final Core class: " + type.name);
                    for (var method : ancestor.methods) {
                        if ((method.access & Opcodes.ACC_FINAL) != 0 && !method.name.startsWith("<")) {
                            assertFalse(type.methods.stream().anyMatch(m -> m.name.equals(method.name) && m.desc.equals(method.desc)),
                                    "LT overrides final Core method: " + type.name + " " + method.name + method.desc);
                        }
                        if ((type.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE)) != 0
                                || (method.access & Opcodes.ACC_ABSTRACT) == 0) continue;
                        var implementation = findMember(repository, type.name, method.name, method.desc, false, new HashSet<>());
                        assertNotNull(implementation, "Missing implementation: " + type.name + " " + method.name + method.desc);
                        assertEquals(0, implementation.access & Opcodes.ACC_ABSTRACT,
                                "New mandatory Core method for LT: " + type.name + " " + method.name + method.desc);
                    }
                }
            }
        }
        assertFalse(inspected.isEmpty(), "No LT Core implementations inspected");
        Files.write(evidence.resolve("lt-core-inheritance.txt"), inspected, StandardCharsets.UTF_8);
    }

    @Test
    void reflectedNativeAllocationResultLayoutsStillExist() {
        var helper = CORE + "core/crafting/batch/ParallelBatchCpuHelper";
        var result = INPUTS.get("core").get(helper + "$BulkResult");
        var remainder = INPUTS.get("core").get(helper + "$RemainderSpec");
        assertNotNull(result);
        assertNotNull(remainder);
        // LT deliberately uses getDeclaredConstructor + setAccessible for these internal layouts.
        assertTrue(result.methods.stream().anyMatch(m -> m.name.equals("<init>") && m.desc.equals(
                "([Lappeng/api/stacks/KeyCounter;J[Lappeng/api/stacks/KeyCounter;[Lappeng/api/stacks/KeyCounter;Ljava/util/List;)V")));
        assertTrue(remainder.methods.stream().anyMatch(m -> m.name.equals("<init>")
                && m.desc.equals("(Lappeng/api/stacks/AEKey;JZ)V")));
    }

    @Test
    void coreMixinTargetsShadowsAndInvocationAnchorsStillExist() throws Exception {
        var anchors = new TreeSet<String>();
        for (var mixin : INPUTS.get("lt").values()) {
            for (var annotation : annotations(mixin.visibleAnnotations, mixin.invisibleAnnotations)) {
                if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) continue;
                for (var value : values(annotationValue(annotation, "value"))) {
                    var targetName = ((Type) value).getInternalName();
                    if (!targetName.startsWith(CORE)) continue;
                    var target = INPUTS.get("core").get(targetName);
                    assertNotNull(target, "Missing mixin target " + targetName);
                    for (var shadow : mixin.fields) {
                        if (annotations(shadow.visibleAnnotations, shadow.invisibleAnnotations).stream()
                                .noneMatch(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Shadow;"))) continue;
                        assertTrue(target.fields.stream().anyMatch(f -> f.name.equals(shadow.name) && f.desc.equals(shadow.desc)
                                && (f.access & Opcodes.ACC_STATIC) == (shadow.access & Opcodes.ACC_STATIC)),
                                "Missing Core shadow: " + targetName + " " + shadow.name + shadow.desc);
                        anchors.add(mixin.name + "\t" + targetName + "\tshadow " + shadow.name + shadow.desc);
                    }
                    for (var handler : mixin.methods) {
                        for (var injection : annotations(handler.visibleAnnotations, handler.invisibleAnnotations)) {
                            var selectors = values(annotationValue(injection, "method"));
                            for (var selectorValue : selectors) {
                                var selector = (String) selectorValue;
                                int descAt = selector.indexOf('(');
                                var name = descAt < 0 ? selector : selector.substring(0, descAt);
                                var methods = target.methods.stream().filter(m -> m.name.equals(name)
                                        && (descAt < 0 || m.desc.equals(selector.substring(descAt)))).toList();
                                assertFalse(methods.isEmpty(), "Missing Core mixin method " + targetName + "." + selector);
                                for (var atValue : values(annotationValue(injection, "at"))) {
                                    var at = (AnnotationNode) atValue;
                                    var kind = annotationValue(at, "value");
                                    if (!"INVOKE".equals(kind)) {
                                        anchors.add(mixin.name + "\t" + targetName + "\t" + selector + "\t" + kind);
                                        continue;
                                    }
                                    var invoke = (String) annotationValue(at, "target");
                                    long hits = methods.stream().flatMap(m -> Arrays.stream(m.instructions.toArray()))
                                            .filter(i -> i instanceof MethodInsnNode call
                                                    && invoke.equals("L" + call.owner + ";" + call.name + call.desc)).count();
                                    assertTrue(hits > 0, "Missing Core INVOKE anchor: " + targetName + "." + selector + " -> " + invoke);
                                    if (mixin.name.endsWith("/TimeWheelBatchInputAllocationMixin"))
                                        assertEquals(1, hits, "Native extraction must have exactly one LT allocation hook");
                                    anchors.add(mixin.name + "\t" + targetName + "\t" + selector + "\t" + invoke + "\thits=" + hits);
                                }
                            }
                        }
                    }
                }
            }
        }
        assertFalse(anchors.isEmpty(), "No Core mixin anchors inspected");
        Files.write(evidence.resolve("core-mixin-anchors.tsv"), anchors, StandardCharsets.UTF_8);
    }

    private static List<AnnotationNode> annotations(List<AnnotationNode> visible, List<AnnotationNode> invisible) {
        var result = new ArrayList<AnnotationNode>();
        if (visible != null) result.addAll(visible);
        if (invisible != null) result.addAll(invisible);
        return result;
    }

    private static Object annotationValue(AnnotationNode annotation, String key) {
        if (annotation.values != null) for (int i = 0; i < annotation.values.size(); i += 2)
            if (annotation.values.get(i).equals(key)) return annotation.values.get(i + 1);
        return null;
    }

    private static List<?> values(Object value) {
        return value == null ? List.of() : value instanceof List<?> list ? list : List.of(value);
    }

    @Test
    void auditorRejectsMissingMembersStaticChangesAndInterfaceChanges() throws Exception {
        var actual = LINKS.stream().filter(link -> !link.field && link.isStatic && !link.interfaceOwner
                && INPUTS.get("core").get(link.owner).methods.stream()
                .anyMatch(m -> m.name.equals(link.name) && m.desc.equals(link.desc))).findFirst().orElseThrow();
        var repository = repository("ae2Dev23");
        var original = repository.get(actual.owner);
        var changed = new ClassNode();
        original.accept(changed);
        repository.put(actual.owner, changed);
        var target = changed.methods.stream().filter(m -> m.name.equals(actual.name) && m.desc.equals(actual.desc)).findFirst().orElseThrow();
        target.access &= ~Opcodes.ACC_STATIC;
        assertThrows(AssertionError.class, () -> verifyLink(repository, actual));
        target.access |= Opcodes.ACC_STATIC;
        changed.access |= Opcodes.ACC_INTERFACE;
        assertThrows(AssertionError.class, () -> verifyLink(repository, actual));
        changed.access &= ~Opcodes.ACC_INTERFACE;
        changed.methods.remove(target);
        assertThrows(AssertionError.class, () -> verifyLink(repository, actual));
    }

    private static void verifyLink(Map<String, ClassNode> repository, Link link) {
        var owner = repository.get(link.owner);
        assertNotNull(owner, link.toString());
        if (!link.field) assertEquals(link.interfaceOwner, (owner.access & Opcodes.ACC_INTERFACE) != 0, link.toString());
        var member = findMember(repository, link.owner, link.name, link.desc, link.field, new HashSet<>());
        assertNotNull(member, "Unresolved " + link);
        assertEquals(link.isStatic, (member.access & Opcodes.ACC_STATIC) != 0, "Staticness changed: " + link);
        assertTrue((member.access & Opcodes.ACC_PUBLIC) != 0
                        || ((member.access & Opcodes.ACC_PROTECTED) != 0 && ancestors(repository, link.caller).stream()
                        .anyMatch(a -> a.name.equals(member.owner))), "Inaccessible " + link);
    }

    private static Member findMember(Map<String, ClassNode> repository, String owner, String name,
                                     String desc, boolean field, Set<String> visited) {
        if (owner == null || !visited.add(owner)) return null;
        var type = repository.get(owner);
        if (type == null) return null;
        if (field) {
            for (var member : type.fields)
                if (member.name.equals(name) && member.desc.equals(desc)) return new Member(owner, member.access);
        } else {
            for (var member : type.methods)
                if (member.name.equals(name) && member.desc.equals(desc)) return new Member(owner, member.access);
        }
        if (name.equals("<init>")) return null;
        var inherited = findMember(repository, type.superName, name, desc, field, visited);
        if (inherited != null) return inherited;
        for (var parent : type.interfaces) {
            inherited = findMember(repository, parent, name, desc, field, visited);
            if (inherited != null) return inherited;
        }
        return null;
    }

    private static List<ClassNode> ancestors(Map<String, ClassNode> repository, String type) {
        var found = new ArrayList<ClassNode>();
        var pending = new ArrayDeque<String>();
        var visited = new HashSet<String>();
        pending.add(type);
        while (!pending.isEmpty()) {
            var name = pending.remove();
            if (!visited.add(name)) continue;
            var node = repository.get(name);
            if (node == null) continue;
            if (!name.equals(type)) found.add(node);
            if (node.superName != null) pending.add(node.superName);
            pending.addAll(node.interfaces);
        }
        return found;
    }

    private static Map<String, ClassNode> repository(String ae2) throws IOException {
        var result = new HashMap<String, ClassNode>();
        result.putAll(INPUTS.get(ae2));
        result.putAll(INPUTS.get("core"));
        result.putAll(INPUTS.get("lt"));
        // java.* ancestors only; never fall back to Core/AE2/test-fixture classpath resources.
        var pending = new ArrayDeque<>(result.values().stream().map(n -> n.superName)
                .filter(Objects::nonNull).filter(n -> n.startsWith("java/")).toList());
        while (!pending.isEmpty()) {
            var name = pending.remove();
            if (result.containsKey(name)) continue;
            try (var in = Object.class.getResourceAsStream("/" + name + ".class")) {
                if (in == null) continue;
                var node = new ClassNode();
                new ClassReader(in).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                result.put(name, node);
                if (node.superName != null) pending.add(node.superName);
                pending.addAll(node.interfaces);
            }
        }
        return result;
    }

    private static void reference(Link link) {
        referenceType(link.owner);
        referenceDescriptor(link.desc);
        if (link.owner.startsWith(CORE)) LINKS.add(link);
    }

    private static void referenceConstant(String caller, Object value) {
        if (value instanceof Type type) referenceDescriptor(type.getDescriptor());
        else if (value instanceof Handle handle) reference(new Link(caller, handle.getOwner(), handle.getName(),
                handle.getDesc(), handle.getTag() <= Opcodes.H_PUTSTATIC,
                handle.getTag() == Opcodes.H_GETSTATIC || handle.getTag() == Opcodes.H_PUTSTATIC
                        || handle.getTag() == Opcodes.H_INVOKESTATIC, handle.isInterface()));
        else if (value instanceof ConstantDynamic dynamic) {
            referenceDescriptor(dynamic.getDescriptor());
            referenceConstant(caller, dynamic.getBootstrapMethod());
            for (int i = 0; i < dynamic.getBootstrapMethodArgumentCount(); i++) referenceConstant(caller, dynamic.getBootstrapMethodArgument(i));
        }
    }

    private static void referenceDescriptor(String descriptor) {
        var type = Type.getType(descriptor);
        if (type.getSort() == Type.METHOD) {
            for (var arg : type.getArgumentTypes()) referenceDescriptor(arg.getDescriptor());
            referenceDescriptor(type.getReturnType().getDescriptor());
        } else if (type.getSort() == Type.ARRAY) referenceDescriptor(type.getElementType().getDescriptor());
        else if (type.getSort() == Type.OBJECT) referenceType(type.getInternalName());
    }

    private static void referenceType(String type) {
        if (type == null) return;
        if (type.startsWith("[")) referenceDescriptor(type);
        else if (type.startsWith(CORE)) TYPES.add(type);
    }

    private static String required(String name) {
        return Objects.requireNonNull(System.getProperty("ae2lt.coreConsumerAudit." + name), "Missing explicit " + name);
    }
}
