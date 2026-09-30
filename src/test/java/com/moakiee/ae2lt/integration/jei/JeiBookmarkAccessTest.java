package com.moakiee.ae2lt.integration.jei;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class JeiBookmarkAccessTest {
    @TempDir
    Path temporaryDirectory;

    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "false,true", "true,true"})
    void absentJeiNeverLoadsOptionalIntegrations(boolean emiInstalled, boolean eaepInstalled) throws Exception {
        var source = temporaryDirectory.resolve("ModList.java");
        Files.writeString(source, """
                package net.neoforged.fml;
                public final class ModList {
                    private static final ModList INSTANCE = new ModList();
                    public static ModList get() { return INSTANCE; }
                    public boolean isLoaded(String id) {
                        return (id.equals("emi") && %s) || (id.equals("extendedae_plus") && %s);
                    }
                }
                """.formatted(emiInstalled, eaepInstalled));
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler);
        assertEquals(0, compiler.run(null, null, null, "-d", temporaryDirectory.toString(), source.toString()));
        byte[] modListBytes = Files.readAllBytes(temporaryDirectory.resolve("net/neoforged/fml/ModList.class"));
        var forbiddenLoads = new ArrayList<String>();
        String entryPoint = "com.moakiee.ae2lt.integration.jei.JeiBookmarkAccess";
        ClassLoader parent = getClass().getClassLoader();
        var loader = new ClassLoader(parent) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("mezz.jei.") || name.startsWith("com.extendedae_plus.")
                        || name.equals(entryPoint + "Impl")
                        || name.equals("com.moakiee.ae2lt.integration.jei.EaepBookmarkAdapter")) {
                    forbiddenLoads.add(name);
                    throw new ClassNotFoundException(name);
                }
                if (!name.equals(entryPoint) && !name.equals("net.neoforged.fml.ModList")) {
                    return super.loadClass(name, resolve);
                }
                Class<?> result = findLoadedClass(name);
                if (result == null) {
                    byte[] bytes;
                    if (name.equals("net.neoforged.fml.ModList")) {
                        bytes = modListBytes;
                    } else {
                        try (var input = parent.getResourceAsStream(name.replace('.', '/') + ".class")) {
                            assertNotNull(input);
                            bytes = input.readAllBytes();
                        } catch (IOException failure) {
                            throw new ClassNotFoundException(name, failure);
                        }
                    }
                    result = defineClass(name, bytes, 0, bytes.length);
                }
                if (resolve) {
                    resolveClass(result);
                }
                return result;
            }
        };

        Class<?> access = loader.loadClass(entryPoint);
        assertEquals(false, access.getMethod("isAvailable").invoke(null));
        access.getMethod("addMissingToBookmarks", List.class).invoke(null, List.of());
        assertTrue(forbiddenLoads.isEmpty(), () -> "Loaded optional integrations: " + forbiddenLoads);
    }
}
