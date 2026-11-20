package com.moakiee.ae2lt.asm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import javax.script.ScriptEngine;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

class CtnhCoremodCompatibilityTest {
    private static final String PCCARD = "yuuki1293.pccard.PCCard";
    private static final String LANGUAGE_CONTEXT = "net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext";
    private static final String MOD_CONTEXT = "net.minecraftforge.fml.ModLoadingContext";

    @TempDir
    Path temporary;

    @Test
    void legacyLoaderConstructsModAndRegistersBothConfigsOnTheActiveContext() throws Exception {
        var definitions = compileFixture("""
                public PCCard(FMLJavaModLoadingContext context) {
                    constructorCalls++;
                    receivedContext = context;
                    context.registerConfig(ModConfig.Type.CLIENT, null);
                    context.registerConfig(ModConfig.Type.COMMON, null);
                }
                """);
        var node = read(definitions.get(PCCARD));
        transform(node);
        definitions.put(PCCARD, write(node));
        var loader = new FixtureLoader(definitions);
        var type = loader.loadClass(PCCARD);
        type.getConstructor().newInstance();
        var languageContext = loader.loadClass(LANGUAGE_CONTEXT).getField("CURRENT").get(null);
        var modContext = loader.loadClass(MOD_CONTEXT).getField("CURRENT").get(null);

        assertSame(languageContext, type.getField("receivedContext").get(null));
        assertEquals(1, type.getField("constructorCalls").getInt(null));
        assertEquals(2, modContext.getClass().getField("configCalls").getInt(modContext));
        assertEquals("CLIENT,COMMON,", modContext.getClass().getField("configTypes").get(modContext));
    }

    @Test
    void repeatedTransformationDoesNotDuplicateConstructorsOrConfigBridges() throws Exception {
        var definitions = compileFixture("""
                public PCCard(FMLJavaModLoadingContext context) {
                    constructorCalls++;
                    receivedContext = context;
                    context.registerConfig(ModConfig.Type.COMMON, null);
                }
                """);
        var node = read(definitions.get(PCCARD));
        transform(node);
        int methods = node.methods.size();
        transform(node);
        assertEquals(methods, node.methods.size());
        definitions.put(PCCARD, write(node));
        var loader = new FixtureLoader(definitions);
        var type = loader.loadClass(PCCARD);
        type.getConstructor().newInstance();
        type.getConstructor().newInstance();
        var modContext = loader.loadClass(MOD_CONTEXT).getField("CURRENT").get(null);

        assertEquals(2, type.getField("constructorCalls").getInt(null));
        assertEquals(2, modContext.getClass().getField("configCalls").getInt(modContext));
    }

    @Test
    void retainsAnExistingNoArgsConstructor() throws Exception {
        var definitions = compileFixture("""
                public PCCard() { receivedContext = "original"; }
                public PCCard(FMLJavaModLoadingContext context) { receivedContext = context; }
                """);
        var node = read(definitions.get(PCCARD));
        int methods = node.methods.size();
        transform(node);
        assertEquals(methods, node.methods.size());
        definitions.put(PCCARD, write(node));
        var type = new FixtureLoader(definitions).loadClass(PCCARD);
        type.getConstructor().newInstance();

        assertEquals("original", type.getField("receivedContext").get(null));
    }

    @Test
    void doesNotInventAConstructorForAnUnsupportedPccardApi() throws Exception {
        var definitions = compileFixture("public PCCard(String unsupported) { receivedContext = unsupported; }");
        var node = read(definitions.get(PCCARD));
        int methods = node.methods.size();
        transform(node);
        assertEquals(methods, node.methods.size());
        definitions.put(PCCARD, write(node));
        var type = new FixtureLoader(definitions).loadClass(PCCARD);

        assertThrows(NoSuchMethodException.class, () -> type.getConstructor());
    }

    @Test
    void leavesOtherModsUntouched() throws Exception {
        var node = new ClassNode();
        node.name = "other/mod/EntryPoint";
        transform(node);
        assertEquals(0, node.methods.size());
    }

    @Test
    void resourceParserDelegatesToTheValidatedConstructorAndIsIdempotent() throws Exception {
        var definitions = compileResourceLocation("""
                public ResourceLocation(String value) {
                    if (value.equals("invalid") || value.startsWith("invalid:")) {
                        throw new IllegalArgumentException("invalid identifier");
                    }
                    this.value = value;
                }
                public ResourceLocation(String namespace, String path) { this(namespace + ":" + path); }
                """);
        var name = "net.minecraft.resources.ResourceLocation";
        var node = read(definitions.get(name));
        transform(node, "ctnh_resource_location");
        int methods = node.methods.size();
        transform(node, "ctnh_resource_location");
        assertEquals(methods, node.methods.size());
        definitions.put(name, write(node));
        var type = new FixtureLoader(definitions).loadClass(name);
        var parse = type.getMethod("parse", String.class);
        var parsed = parse.invoke(null, "ae2:pattern");
        assertEquals("ae2:pattern", type.getField("value").get(parsed));
        var failure = assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> parse.invoke(null, "invalid"));
        assertEquals(IllegalArgumentException.class, failure.getCause().getClass());
        var namespaced = type.getMethod("fromNamespaceAndPath", String.class, String.class);
        assertEquals("ae2:pattern", type.getField("value").get(namespaced.invoke(null, "ae2", "pattern")));
        var namespacedFailure = assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> namespaced.invoke(null, "invalid", "pattern"));
        assertEquals(IllegalArgumentException.class, namespacedFailure.getCause().getClass());
    }

    @Test
    void retainsAnExistingResourceLocationParser() throws Exception {
        var definitions = compileResourceLocation("""
                public ResourceLocation(String value) { this.value = value; }
                public static ResourceLocation parse(String value) {
                    return new ResourceLocation("existing:" + value);
                }
                """);
        var name = "net.minecraft.resources.ResourceLocation";
        var node = read(definitions.get(name));
        int methods = node.methods.size();
        transform(node, "ctnh_resource_location");
        assertEquals(methods, node.methods.size());
        definitions.put(name, write(node));
        var type = new FixtureLoader(definitions).loadClass(name);
        var parsed = type.getMethod("parse", String.class).invoke(null, "pattern");
        assertEquals("existing:pattern", type.getField("value").get(parsed));
    }

    @Test
    void doesNotBackportParsingWithoutTheExpectedConstructor() throws Exception {
        var definitions = compileResourceLocation("public ResourceLocation() { value = \"original\"; }");
        var name = "net.minecraft.resources.ResourceLocation";
        var node = read(definitions.get(name));
        int methods = node.methods.size();
        transform(node, "ctnh_resource_location");
        assertEquals(methods, node.methods.size());
        definitions.put(name, write(node));
        var type = new FixtureLoader(definitions).loadClass(name);
        assertThrows(NoSuchMethodException.class, () -> type.getMethod("parse", String.class));
    }

    @Test
    void retainsAnExistingNamespacedResourceLocationFactory() throws Exception {
        var definitions = compileResourceLocation("""
                public ResourceLocation(String namespace, String path) { value = namespace + ":" + path; }
                public static ResourceLocation fromNamespaceAndPath(String namespace, String path) {
                    return new ResourceLocation("existing", path);
                }
                """);
        var name = "net.minecraft.resources.ResourceLocation";
        var node = read(definitions.get(name));
        int methods = node.methods.size();
        transform(node, "ctnh_resource_location");
        assertEquals(methods, node.methods.size());
        definitions.put(name, write(node));
        var type = new FixtureLoader(definitions).loadClass(name);
        var parsed = type.getMethod("fromNamespaceAndPath", String.class, String.class)
                .invoke(null, "ae2", "pattern");
        assertEquals("existing:pattern", type.getField("value").get(parsed));
    }

    @Test
    void singularityIconTimerStillRunsWithoutKeepingTheJvmAlive() throws Exception {
        verifySingularityTimer("new java.util.Timer(\"Singularity Icon Timer\")", true);
    }

    @Test
    void retainsAnAlreadyDaemonSingularityTimer() throws Exception {
        verifySingularityTimer("new java.util.Timer(\"Singularity Icon Timer\", true)", true);
    }

    @Test
    void leavesOtherTimersInTheSingularityClassUnchanged() throws Exception {
        verifySingularityTimer("new java.util.Timer(\"Unrelated timer\")", false);
    }

    private void verifySingularityTimer(String constructor, boolean expectedDaemon) throws Exception {
        var name = "committee.nova.mods.avaritia.common.item.singularity.SingularityItem";
        var definitions = compileSources(Map.of(name.replace('.', '/') + ".java", """
                package committee.nova.mods.avaritia.common.item.singularity;
                public class SingularityItem {
                    public static final java.util.concurrent.CompletableFuture<Boolean> daemon =
                            new java.util.concurrent.CompletableFuture<>();
                    public static final java.util.Timer timer = %s;
                    static {
                        timer.schedule(new java.util.TimerTask() {
                            public void run() { daemon.complete(Thread.currentThread().isDaemon()); }
                        }, 0L);
                    }
                }
                """.formatted(constructor)));
        var node = read(definitions.get(name));
        transform(node, "avaritia_singularity_timer");
        var first = write(node);
        transform(node, "avaritia_singularity_timer");
        org.junit.jupiter.api.Assertions.assertArrayEquals(first, write(node));
        definitions.put(name, write(node));
        var type = new FixtureLoader(definitions).loadClass(name);
        var timer = (java.util.Timer) type.getField("timer").get(null);
        try {
            var result = (java.util.concurrent.CompletableFuture<?>) type.getField("daemon").get(null);
            assertEquals(expectedDaemon, result.get(5, java.util.concurrent.TimeUnit.SECONDS));
        } finally {
            timer.cancel();
        }
    }

    private void transform(ClassNode node) throws Exception {
        transform(node, "pccard_constructor");
    }

    private void transform(ClassNode node, String transformer) throws Exception {
        var factory = Class.forName("net.minecraftforge.coremod.NashornFactory");
        var createEngine = factory.getDeclaredMethod("createEngine");
        createEngine.setAccessible(true);
        var engine = (ScriptEngine) createEngine.invoke(null);
        assertNotNull(engine);
        try (var script = Files.newBufferedReader(Path.of("src/main/resources/coremods/" + transformer + ".js"))) {
            engine.eval(script);
        }
        engine.put("fixture", node);
        assertSame(node, engine.eval("initializeCoreMod()." + transformer + ".transformer(fixture)"));
    }

    private ClassNode read(byte[] bytes) {
        var node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private byte[] write(ClassNode node) {
        var writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private Map<String, byte[]> compileFixture(String constructors) throws Exception {
        var sources = Map.of(
                "net/minecraftforge/fml/config/IConfigSpec.java", """
                        package net.minecraftforge.fml.config;
                        public interface IConfigSpec {}
                        """,
                "net/minecraftforge/fml/config/ModConfig.java", """
                        package net.minecraftforge.fml.config;
                        public class ModConfig { public enum Type { CLIENT, COMMON } }
                        """,
                "net/minecraftforge/fml/ModLoadingContext.java", """
                        package net.minecraftforge.fml;
                        import net.minecraftforge.fml.config.*;
                        public class ModLoadingContext {
                            public static final ModLoadingContext CURRENT = new ModLoadingContext();
                            public int configCalls;
                            public String configTypes = "";
                            public static ModLoadingContext get() { return CURRENT; }
                            public void registerConfig(ModConfig.Type type, IConfigSpec spec) {
                                configCalls++;
                                configTypes += type.name() + ",";
                            }
                        }
                        """,
                "net/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext.java", """
                        package net.minecraftforge.fml.javafmlmod;
                        import net.minecraftforge.fml.config.*;
                        public class FMLJavaModLoadingContext {
                            public static final FMLJavaModLoadingContext CURRENT = new FMLJavaModLoadingContext();
                            public static FMLJavaModLoadingContext get() { return CURRENT; }
                            public void registerConfig(ModConfig.Type type, IConfigSpec spec) {
                                throw new AssertionError("The newer config API must not execute");
                            }
                        }
                        """,
                "yuuki1293/pccard/PCCard.java", """
                        package yuuki1293.pccard;
                        import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
                        import net.minecraftforge.fml.config.ModConfig;
                        public class PCCard {
                            public static int constructorCalls;
                            public static Object receivedContext;
                            %s
                        }
                        """.formatted(constructors));
        return compileSources(sources);
    }

    private Map<String, byte[]> compileResourceLocation(String methods) throws Exception {
        return compileSources(Map.of("net/minecraft/resources/ResourceLocation.java", """
                package net.minecraft.resources;
                public class ResourceLocation {
                    public final String value;
                    %s
                }
                """.formatted(methods)));
    }

    private Map<String, byte[]> compileSources(Map<String, String> sources) throws Exception {
        var classes = Files.createDirectories(temporary.resolve("classes"));
        var arguments = new ArrayList<String>();
        arguments.addAll(java.util.List.of("-proc:none", "--release", "17", "-encoding", "UTF-8", "-d", classes.toString()));
        for (var entry : sources.entrySet()) {
            var source = temporary.resolve(entry.getKey());
            Files.createDirectories(source.getParent());
            Files.writeString(source, entry.getValue());
            arguments.add(source.toString());
        }
        var diagnostics = new ByteArrayOutputStream();
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, diagnostics, diagnostics,
                arguments.toArray(String[]::new)), diagnostics.toString(StandardCharsets.UTF_8));
        var definitions = new HashMap<String, byte[]>();
        try (var files = Files.walk(classes)) {
            for (var file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                var name = classes.relativize(file).toString().replace('\\', '.').replace('/', '.');
                definitions.put(name.substring(0, name.length() - ".class".length()), Files.readAllBytes(file));
            }
        }
        return definitions;
    }

    private static final class FixtureLoader extends ClassLoader {
        private final Map<String, byte[]> definitions;

        private FixtureLoader(Map<String, byte[]> definitions) {
            super(CtnhCoremodCompatibilityTest.class.getClassLoader());
            this.definitions = definitions;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                var loaded = findLoadedClass(name);
                if (loaded == null) {
                    var bytes = definitions.get(name);
                    loaded = bytes == null ? super.loadClass(name, false) : defineClass(name, bytes, 0, bytes.length);
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }
    }
}
