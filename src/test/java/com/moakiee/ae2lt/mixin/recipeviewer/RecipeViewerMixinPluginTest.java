package com.moakiee.ae2lt.mixin.recipeviewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Proxy;
import java.util.List;

import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RecipeViewerMixinPluginTest {
    private static final String MIXIN_PACKAGE = "com.moakiee.ae2lt.mixin.recipeviewer.jeisupply.";

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
        assertEquals(!legacy, applies(plugin, "JeiWirelessSupplyContextAccessor"));
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
        assertEquals(true, plugin.shouldApplyMixin("unused",
                "com.moakiee.ae2lt.mixin.recipeviewer.jei.JeiMultiblockLayoutMixin"));
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
