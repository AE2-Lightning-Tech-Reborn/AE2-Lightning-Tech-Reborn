package com.moakiee.ae2lt.mixin;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Read binary shapes without loading client classes on a dedicated-server test JVM. */
class PortedMixinTargetContractTest {
    @ParameterizedTest
    @ValueSource(strings = {
        "big.BigCraftAmountMenuMixin", "big.BigCraftConfirmMenuMixin",
        "big.BigCraftingPlanSummaryMixin", "big.BigCraftingStatusEntryMixin", "big.BigCpuListEntryMixin",
        "client.BigNumberEntryWidgetMixin", "client.BigCraftAmountScreenMixin",
        "client.BigCraftingStatusTableMixin", "client.BigTerminalAmountMixin", "client.BigCpuListMixin",
        "CraftingCPURecordAccessor", "TimeWheelCraftingCPUCyclerMixin"
    })
    void selectorsMatchForgeAe2Binary(String name) throws Exception {
        var mixin = read("com/moakiee/ae2lt/mixin/" + name.replace('.', '/'));
        var declaration = annotations(mixin.visibleAnnotations, mixin.invisibleAnnotations).stream()
                .filter(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")).findFirst().orElseThrow();
        Object value = value(declaration, "value");
        String targetName = value != null ? ((Type) ((List<?>) value).get(0)).getInternalName()
                : ((String) ((List<?>) value(declaration, "targets")).get(0)).replace('.', '/');
        var target = read(targetName);
        var runtime = readRuntime(targetName);
        boolean classRemap = remap(declaration, true);
        for (var handler : mixin.methods) {
            for (var annotation : annotations(handler.visibleAnnotations, handler.invisibleAnnotations)) {
                Object selectors = value(annotation, "method");
                if (!(selectors instanceof List<?> methods)) continue;
                boolean methodRemap = remap(annotation, classRemap);
                var binary = methodRemap ? target : runtime;
                for (Object selector : methods) {
                    String text = (String) selector;
                    String methodName = text.contains("(") ? text.substring(0, text.indexOf('(')) : text;
                    var matches = binary.methods.stream().filter(m -> m.name.equals(methodName)
                            && (!text.contains("(") || (m.name + m.desc).equals(text))).toList();
                    assertFalse(matches.isEmpty(), name + " missing " + text
                            + (methodRemap ? "" : " on the obfuscated Forge AE2 binary"));
                    if (annotation.desc.endsWith("/Inject;")) {
                        for (var method : matches) assertEquals(method.access & Opcodes.ACC_STATIC,
                                handler.access & Opcodes.ACC_STATIC, name + " static mismatch: " + text);
                    }
                    Object atValue = value(annotation, "at");
                    var points = atValue instanceof List<?> list ? list : Collections.singletonList(atValue);
                    for (var point : points) {
                        if (!(point instanceof AnnotationNode at) || !"INVOKE".equals(value(at, "value"))) continue;
                        String invocation = (String) value(at, "target");
                        if (invocation == null) continue;
                        boolean found = matches.stream().anyMatch(m -> {
                            for (var instruction : m.instructions) {
                                if (instruction instanceof MethodInsnNode call
                                        && invocation.equals("L" + call.owner + ";" + call.name + call.desc)) return true;
                            }
                            return false;
                        });
                        assertTrue(found, name + " missing invocation " + invocation);
                    }
                }
            }
        }
    }
    private static Object value(AnnotationNode node, String key) {
        if (node.values != null) for (int i = 0; i < node.values.size(); i += 2)
            if (key.equals(node.values.get(i))) return node.values.get(i + 1);
        return null;
    }
    private static List<AnnotationNode> annotations(List<AnnotationNode> a, List<AnnotationNode> b) {
        var result = new ArrayList<AnnotationNode>();
        if (a != null) result.addAll(a);
        if (b != null) result.addAll(b);
        return result;
    }
    private static boolean remap(AnnotationNode node, boolean fallback) {
        Object remap = value(node, "remap");
        return remap instanceof Boolean flag ? flag : fallback;
    }

    private static ClassNode read(String name) throws Exception {
        try (var in = PortedMixinTargetContractTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(in, name);
            var result = new ClassNode();
            new ClassReader(in).accept(result, 0);
            return result;
        }
    }

    /**
     * ForgeGradle's {@code *.jar.input} sidecar stores the original artifact hash, not a
     * zip. Mixin {@code remap = false} must match that obfuscated binary, not the named
     * development jar that made {@code renderSlot} look valid.
     */
    private static ClassNode readRuntime(String name) throws Exception {
        var mapped = PortedMixinTargetContractTest.class.getClassLoader().getResource(name + ".class");
        assertNotNull(mapped, name);
        assertEquals("jar", mapped.getProtocol(), name);
        var spec = mapped.toString();
        var bang = spec.indexOf("!/");
        assertTrue(bang > 4, spec);
        var mappedJar = Path.of(URI.create(spec.substring("jar:".length(), bang))).toAbsolutePath();
        var obfuscatedJar = obfuscatedJar(mappedJar);
        try (var zip = new ZipFile(obfuscatedJar.toFile())) {
            var entry = zip.getEntry(name + ".class");
            assertNotNull(entry, name + " in " + obfuscatedJar);
            try (var in = zip.getInputStream(entry)) {
                var result = new ClassNode();
                new ClassReader(in).accept(result, 0);
                return result;
            }
        }
    }

    private static Path obfuscatedJar(Path mappedJar) throws Exception {
        var sidecar = Path.of(mappedJar + ".input");
        assertTrue(Files.isRegularFile(sidecar), sidecar.toString());
        String orig = null;
        for (String line : Files.readAllLines(sidecar)) {
            if (line.startsWith("orig=")) orig = line.substring("orig=".length()).trim();
        }
        assertNotNull(orig, sidecar.toString());
        Path caches = mappedJar;
        while (caches != null && !caches.getFileName().toString().equals("caches")) {
            caches = caches.getParent();
        }
        assertNotNull(caches, mappedJar.toString());
        String hash = orig;
        try (var stream = Files.walk(caches.resolve("modules-2").resolve("files-2.1"), 6)) {
            return stream.filter(path -> path.getParent() != null
                            && hash.equals(path.getParent().getFileName().toString())
                            && path.getFileName().toString().endsWith(".jar")
                            && !path.getFileName().toString().endsWith("-sources.jar"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("obfuscated jar for " + hash));
        }
    }
}
