package com.moakiee.ae2lt.integration.eaep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import javax.tools.ToolProvider;

import appeng.menu.me.crafting.CraftConfirmMenu;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EaepForceCraftingAccessTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void absentEaepDoesNotLoadExternalTypesAndDoesNotBlockNormalSubmission() throws Exception {
        var source = temporaryDirectory.resolve("ModList.java");
        Files.writeString(source, """
                package net.neoforged.fml;
                public final class ModList {
                    private static final ModList INSTANCE = new ModList();
                    public static ModList get() { return INSTANCE; }
                    public boolean isLoaded(String id) { return false; }
                }
                """);
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler);
        assertEquals(0, compiler.run(null, null, null, "-d", temporaryDirectory.toString(), source.toString()));
        byte[] modListBytes = Files.readAllBytes(temporaryDirectory.resolve("net/neoforged/fml/ModList.class"));
        var forbiddenLoads = new ArrayList<String>();
        String entryPoint = EaepForceCraftingAccess.class.getName();
        ClassLoader parent = getClass().getClassLoader();
        var loader = new ClassLoader(parent) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("com.extendedae_plus.")) {
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
                if (resolve) resolveClass(result);
                return result;
            }
        };
        var access = loader.loadClass(entryPoint);
        assertEquals(false, access.getMethod("isAvailable", CraftConfirmMenu.class).invoke(null, new Object[] {null}));
        assertEquals(true, access.getMethod("synchronize", CraftConfirmMenu.class, boolean.class).invoke(null, null, false));
        assertEquals(false, access.getMethod("synchronize", CraftConfirmMenu.class, boolean.class).invoke(null, null, true));
        assertTrue(forbiddenLoads.isEmpty(), () -> "Loaded optional classes: " + forbiddenLoads);
    }
}
