package com.moakiee.ae2lt.mixin.recipeviewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.List;

import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RecipeViewerMixinPluginTest {
    private static final String MIXIN_PACKAGE = "com.moakiee.ae2lt.mixin.recipeviewer.jeisupply.";

    @AfterEach
    void clearOptionalUselessSelection() {
        com.moakiee.ae2lt.compat.gto.GtoUselessJeiAbi.configure(false, "", "", getClass().getClassLoader());
    }

    @Test
    void missingLegacyApiInstallsExtensionAndOnlyEnablesItsMarkerInsideTheExactGate() {
        var plugin = new RecipeViewerMixinPlugin();
        var installs = new java.util.concurrent.atomic.AtomicInteger();
        var resources = new ClassLoader(null) {
            @Override public java.io.InputStream getResourceAsStream(String name) { return null; }
        };
        plugin.configureUselessJei(true, "15.2610.40", "20.2.4.7-gto.1", resources, installs::incrementAndGet);
        assertEquals(1, installs.get());
        assertTrue(plugin.shouldApplyMixin("unused",
                "com.moakiee.ae2lt.mixin.recipeviewer.useless.GtoUselessJeiAbiMixin"));
        assertTrue(com.moakiee.ae2lt.compat.gto.GtoUselessJeiAbi.restorePlugin());

        plugin.configureUselessJei(false, "15.2610.40", "20.2.4.7-gto.1", resources,
                () -> { throw new AssertionError("extension registered outside the client/GTO/addon gate"); });
        assertFalse(plugin.shouldApplyMixin("unused",
                "com.moakiee.ae2lt.mixin.recipeviewer.useless.GtoUselessJeiAbiMixin"));
        assertFalse(com.moakiee.ae2lt.compat.gto.GtoUselessJeiAbi.restorePlugin());
    }

    @ParameterizedTest
    @CsvSource({
            "15.20.0.129, true",
            "15.21.0.144, true",
            "15.56.0.219, true",
            "15.57.0, false",
            "15.57.0.220, false",
            "15.62.0.219, false",
            "15.74.0.227, false"
    })
    void selectsExactlyOneTransferHookAndOnlyEnablesContextsForModernJei(String version, boolean legacy) {
        var plugin = new RecipeViewerMixinPlugin();
        plugin.configureJeiTransfer(modFile(version), true);

        assertEquals(legacy, applies(plugin, "JeiWirelessSupplyMixin"));
        assertEquals(!legacy, applies(plugin, "JeiWirelessSupplyServiceMixin"));
        assertFalse(applies(plugin, "JeiWirelessSupplyContextAccessor"));
    }

    @Test
    void absentJeiDisablesAllTransferHooks() {
        var plugin = new RecipeViewerMixinPlugin();
        plugin.configureJeiTransfer(null, false);

        assertFalse(applies(plugin, "JeiWirelessSupplyMixin"));
        assertFalse(applies(plugin, "JeiWirelessSupplyServiceMixin"));
        assertFalse(applies(plugin, "JeiWirelessSupplyContextAccessor"));
    }

    @ParameterizedTest
    @CsvSource({"15.20.0.129, false", "15.57.0.220, false", "15.67.0.226, false",
            "15.68.0.226, true", "15.74.0.227, true"})
    void buttonHooksRemainExclusiveWithoutWirelessLibrary(String version, boolean controller) {
        var plugin = new RecipeViewerMixinPlugin();
        plugin.configureJeiTransfer(modFile(version), false);
        assertEquals(controller, plugin.shouldApplyMixin("unused",
                "com.moakiee.ae2lt.mixin.recipeviewer.jei.JeiRecipeTransferButtonControllerMixin"));
        assertEquals(!controller, plugin.shouldApplyMixin("unused",
                "com.moakiee.ae2lt.mixin.recipeviewer.jei.JeiLegacyRecipeTransferButtonMixin"));
        assertNotEquals(plugin.shouldApplyMixin("unused",
                        "com.moakiee.ae2lt.mixin.recipeviewer.jei.JeiMultiblockLayoutMixin"),
                plugin.shouldApplyMixin("unused",
                        "com.moakiee.ae2lt.mixin.recipeviewer.jei.JeiModernMultiblockLayoutMixin"));
        assertFalse(applies(plugin, "JeiWirelessSupplyMixin"));
        assertFalse(applies(plugin, "JeiWirelessSupplyServiceMixin"));
    }

    private static boolean applies(RecipeViewerMixinPlugin plugin, String mixin) {
        return plugin.shouldApplyMixin("unused", MIXIN_PACKAGE + mixin);
    }

    private static IModFileInfo modFile(String version) {
        var mod = (IModInfo) Proxy.newProxyInstance(IModInfo.class.getClassLoader(), new Class<?>[] {IModInfo.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getVersion")) return new DefaultArtifactVersion(version);
                    throw new UnsupportedOperationException(method.getName());
                });
        return (IModFileInfo) Proxy.newProxyInstance(IModFileInfo.class.getClassLoader(),
                new Class<?>[] {IModFileInfo.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getMods")) return List.of(mod);
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
