package com.moakiee.ae2lt.client.core;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoreEffectShaderSourceContractTest {
    @Test
    void vertexInputsUseTheNativeShaderInstanceContract() throws Exception {
        String shader = Files.readString(Path.of(
                "src/main/resources/assets/ae2lt/shaders/core/multiblock/core.vsh"));

        assertAll(
                () -> assertTrue(shader.startsWith("#version 330")),
                () -> assertTrue(shader.contains("in vec3 Position;")),
                () -> assertTrue(shader.contains("in vec4 Color;")),
                () -> assertTrue(shader.contains("in vec3 Normal;")),
                () -> assertTrue(shader.contains("#moj_import <minecraft:dynamictransforms.glsl>")),
                () -> assertTrue(shader.contains("#moj_import <minecraft:projection.glsl>")),
                () -> assertTrue(shader.contains("#moj_import <minecraft:globals.glsl>")),
                () -> assertTrue(shader.contains("#define EffectTime (GameTime * 1200.0)")),
                () -> assertFalse(shader.contains("layout(location")),
                () -> assertFalse(shader.contains("VeilRenderTime")));
    }

    @Test
    void shaderPipelinesUseNamespacedNativeResources() throws Exception {
        Path shaderDirectory = Path.of(
                "src/main/resources/assets/ae2lt/shaders/core/multiblock");
        String pipelines = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/core/CoreEffectShaders.java"));

        assertAll(
                () -> assertTrue(Files.exists(shaderDirectory.resolve("core.vsh"))),
                () -> assertTrue(Files.exists(shaderDirectory.resolve("matrix_core.fsh"))),
                () -> assertTrue(Files.exists(shaderDirectory.resolve("tianshu_core.fsh"))),
                () -> assertTrue(pipelines.contains("id(\"core/multiblock/core\")")),
                () -> assertTrue(pipelines.contains("id(\"core/multiblock/matrix_core\")")),
                () -> assertTrue(pipelines.contains("id(\"core/multiblock/tianshu_core\")")),
                () -> assertTrue(pipelines.contains("RegisterRenderPipelinesEvent")),
                () -> assertTrue(pipelines.contains("event.registerPipeline(TIANSHU)")),
                () -> assertTrue(pipelines.contains("event.registerPipeline(MATRIX_CORE)")));
    }

    @Test
    void buildAndMetadataDeclareVeilAsOptional() throws Exception {
        String build = Files.readString(Path.of("build.gradle"));
        String metadata = Files.readString(Path.of(
                "src/main/templates/META-INF/neoforge.mods.toml"));

        assertAll(
                () -> assertTrue(build.contains("ae2ltEnableVeilDevRuntime")),
                () -> assertFalse(build.contains("implementation(\"foundry.veil")),
                () -> assertFalse(build.contains("compileOnly(\"foundry.veil")),
                () -> assertTrue(metadata.contains("modId = \"veil\"")),
                () -> assertTrue(metadata.contains("type = \"optional\"")),
                () -> assertTrue(metadata.contains("versionRange = \"*\"")),
                () -> assertTrue(Files.exists(Path.of(
                        "src/main/resources/assets/ae2lt/pinwheel/shaders/program/multiblock/core.vsh"))));
    }

    @Test
    void incompatibleVeilCanFallBackAfterStartup() throws Exception {
        String backend = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/core/CoreEffectBackend.java"));
        String renderTypes = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/core/CoreEffectRenderTypes.java"));
        String nativeShaders = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/core/CoreEffectShaders.java"));

        assertAll(
                () -> assertTrue(backend.contains("detectCompatibleVeil()")),
                () -> assertTrue(backend.contains("disableVeil(Throwable cause)")),
                () -> assertTrue(backend.contains("new DefaultArtifactVersion(\"4.3.0\")")),
                () -> assertTrue(backend.contains("new DefaultArtifactVersion(\"5.0.0\")")),
                () -> assertTrue(backend.contains("installedVersion.compareTo(MINIMUM_VEIL_VERSION) < 0")),
                () -> assertTrue(backend.contains("installedVersion.compareTo(MAXIMUM_VEIL_VERSION) >= 0")),
                () -> assertTrue(renderTypes.contains("CoreEffectShaders.tianshu()")),
                () -> assertTrue(backend.contains("VeilCoreEffectShaders.isApiCompatible()")),
                () -> assertFalse(nativeShaders.contains(
                        "LOGGER.info(\"Veil detected; using the Veil core-effect shader backend\");\n"
                                + "            return;")),
                () -> assertTrue(nativeShaders.contains("event.registerPipeline(TIANSHU)")),
                () -> assertTrue(nativeShaders.contains("event.registerPipeline(MATRIX_CORE)")));
    }

    @Test
    void optionalVeilClassesAreIsolatedFromTheNativeBackend() throws Exception {
        Path javaRoot = Path.of("src/main/java");
        List<Path> veilReferences;
        try (var files = Files.walk(javaRoot)) {
            veilReferences = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        try {
                            return Files.readString(path).contains("foundry.veil");
                        } catch (IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .map(javaRoot::relativize)
                    .toList();
        }

        String bridge = Files.readString(javaRoot.resolve(
                "com/moakiee/ae2lt/client/core/veil/VeilCoreEffectShaders.java"));
        assertEquals(List.of(), veilReferences);
        assertTrue(bridge.contains("return false;"));
    }

    @Test
    void activeShaderPacksUseAKnownVanillaShaderFallback() throws Exception {
        String backend = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/core/CoreEffectBackend.java"));
        String renderTypes = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/core/CoreEffectRenderTypes.java"));
        String geometry = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/core/CoreEffectGeometry.java"));

        assertAll(
                () -> assertTrue(backend.contains("modList.isLoaded(\"iris\")")),
                () -> assertTrue(backend.contains("modList.isLoaded(\"oculus\")")),
                () -> assertTrue(backend.contains("net.irisshaders.iris.api.v0.IrisApi")),
                () -> assertTrue(backend.contains("net.coderbot.iris.api.v0.IrisApi")),
                () -> assertTrue(backend.contains("isShaderPackInUse")),
                () -> assertFalse(backend.contains("import net.irisshaders")),
                () -> assertTrue(renderTypes.contains("CoreEffectShaders.tianshuFallback()")),
                () -> assertTrue(renderTypes.contains("CoreEffectShaders.matrixCoreFallback()")),
                () -> assertTrue(renderTypes.contains("shaderPackActive ?")),
                () -> assertTrue(geometry.contains("CoreEffectBackend.useShaderPackFallback()")));
    }

    @Test
    void nativeShadersRetainCoreAndTianshuVisualLogic() throws Exception {
        Path nativeDirectory = Path.of(
                "src/main/resources/assets/ae2lt/shaders/core/multiblock");
        String core = Files.readString(nativeDirectory.resolve("core.vsh"));
        String matrix = Files.readString(nativeDirectory.resolve("matrix_core.fsh"));
        String tianshu = Files.readString(nativeDirectory.resolve("tianshu_core.fsh"));

        assertAll(
                () -> assertTrue(core.contains("float displacement = sin(phase * 1.7)")),
                () -> assertTrue(matrix.contains("float trace = smoothstep(")),
                () -> assertTrue(matrix.contains("float fissure = 1.0 - smoothstep(")),
                () -> assertTrue(tianshu.contains("float latitude =")),
                () -> assertTrue(tianshu.contains("float longitude =")));
    }
}
