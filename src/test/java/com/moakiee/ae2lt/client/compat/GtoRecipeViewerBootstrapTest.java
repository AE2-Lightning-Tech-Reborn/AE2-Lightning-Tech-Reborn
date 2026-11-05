package com.moakiee.ae2lt.client.compat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import mezz.jei.api.IModPlugin;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

class GtoRecipeViewerBootstrapTest {
    private static final String LT = "com.moakiee.ae2lt.integration.jei.JEIPlugin";
    private static final String USELESS = "com.sorrowmist.useless.compat.jei.JEIPlugin";

    @Test
    void nativeEmiSupportDoesNotSuppressLtsJeiServicesOrMutateTheOriginalSet() {
        var original = Set.of("ae2lt", "useless_mod", "other");
        assertEquals(Set.of("useless_mod", "other"), GtoRecipeViewerBootstrap.withJeiServices(original, false));
        assertEquals(Set.of("ae2lt", "useless_mod", "other"), original);
    }

    @Test
    void onlyTheAbiApprovedUselessPluginMayAlsoProvideItsJeiCallbacks() {
        assertEquals(Set.of("other"), GtoRecipeViewerBootstrap.withJeiServices(
                Set.of("ae2lt", "useless_mod", "other"), true));
    }

    @Test
    void unrelatedNativePluginsRetainTheSameSuppressionSet() {
        var unrelated = Set.of("other", "emi", "jei");
        assertSame(unrelated, GtoRecipeViewerBootstrap.withJeiServices(unrelated, true));
    }

    @Test
    void existingLtDoesNotPreventRestoringOriginalUselessPlugin() throws Exception {
        var lt = plugin(LT);
        var other = plugin("test.viewer.WhitelistedPlugin");
        var useless = plugin(USELESS);
        var original = List.of(other, lt);
        var calls = new AtomicInteger();
        var result = GtoRecipeViewerBootstrap.restoreJei(original, true,
                () -> { throw new AssertionError("LT is already registered"); },
                () -> { calls.incrementAndGet(); return useless; });
        assertEquals(List.of(other, lt, useless), result);
        assertEquals(List.of(other, lt), original);
        assertEquals(1, calls.get());
    }

    @Test
    void existingOriginalPluginsAreNeitherInstantiatedAgainNorDuplicated() throws Exception {
        var original = List.of(plugin(USELESS), plugin("test.viewer.OtherPlugin"), plugin(LT));
        var result = GtoRecipeViewerBootstrap.restoreJei(original, true,
                () -> { throw new AssertionError("duplicate LT"); },
                () -> { throw new AssertionError("duplicate Useless"); });
        assertSame(original, result);
    }

    @Test
    void unsupportedUselessIsNeverResolvedAndOtherWhitelistEntriesRemain() throws Exception {
        var other = plugin("test.viewer.UnrelatedPlugin");
        var lt = plugin(LT);
        var result = GtoRecipeViewerBootstrap.restoreJei(List.of(other), false, () -> lt,
                () -> { throw new AssertionError("optional addon resolved outside its gate"); });
        assertEquals(List.of(other, lt), result);
    }

    @Test
    void duplicateExactAddonClassKeepsItsFirstOriginalInstanceAndAllOtherEntries() throws Exception {
        var first = plugin(USELESS);
        var duplicate = plugin(USELESS);
        var other = plugin("test.viewer.KeptPlugin");
        var lt = plugin(LT);
        var original = List.of(first, other, duplicate, lt);
        var result = GtoRecipeViewerBootstrap.restoreJei(original, true,
                () -> { throw new AssertionError("existing LT"); },
                () -> { throw new AssertionError("existing Useless"); });
        assertEquals(List.of(first, other, lt), result);
        assertEquals(4, original.size());
    }

    @Test
    void wrongPluginFactoryFailsBeforePublishingAChangedWhitelist() throws Exception {
        var original = List.of(plugin(LT));
        var wrong = plugin("test.viewer.WrongPlugin");
        assertThrows(IllegalStateException.class, () -> GtoRecipeViewerBootstrap.restoreJei(original, true,
                () -> { throw new AssertionError(); }, () -> wrong));
        assertEquals(1, original.size());
    }

    private static IModPlugin plugin(String name) throws Exception {
        // Use distinct exact class names without initializing either real optional plugin.
        String internal = name.replace('.', '/');
        var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internal, null, "java/lang/Object",
                new String[] {"mezz/jei/api/IModPlugin"});
        var constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
        var uid = writer.visitMethod(Opcodes.ACC_PUBLIC, "getPluginUid",
                "()Lnet/minecraft/resources/ResourceLocation;", null, null);
        uid.visitCode();
        uid.visitInsn(Opcodes.ACONST_NULL);
        uid.visitInsn(Opcodes.ARETURN);
        uid.visitMaxs(0, 0);
        uid.visitEnd();
        writer.visitEnd();
        var loader = new ClassLoader(IModPlugin.class.getClassLoader()) {
            Class<?> define() { return defineClass(name, writer.toByteArray(), 0, writer.toByteArray().length); }
        };
        return (IModPlugin) loader.define().getConstructor().newInstance();
    }
}
