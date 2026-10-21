package com.moakiee.ae2lt.mixin.recipeviewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Proxy;
import java.util.List;

import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RecipeViewerMixinPluginTest {
    private static final String MIXIN_PACKAGE = "com.moakiee.ae2lt.mixin.recipeviewer.jeisupply.";

    @ParameterizedTest
    @CsvSource({
            "19.26.0.316, true",
            "19.27.0.336, true",
            "19.51.0.399, true",
            "19.52.0, false",
            "19.52.0.400, false",
            "19.64.0.455, false"
    })
    void selectsExactlyOneTransferHookAndOnlyEnablesContextsForModernJei(String version, boolean legacy) {
        var plugin = new RecipeViewerMixinPlugin();
        plugin.configureJeiTransfer(modFile(version));

        assertEquals(legacy, applies(plugin, "JeiWirelessSupplyMixin"));
        assertEquals(!legacy, applies(plugin, "JeiWirelessSupplyServiceMixin"));
        assertEquals(!legacy, applies(plugin, "JeiWirelessSupplyContextAccessor"));
    }

    @Test
    void absentJeiDisablesAllTransferHooks() {
        var plugin = new RecipeViewerMixinPlugin();
        plugin.configureJeiTransfer(null);

        assertFalse(applies(plugin, "JeiWirelessSupplyMixin"));
        assertFalse(applies(plugin, "JeiWirelessSupplyServiceMixin"));
        assertFalse(applies(plugin, "JeiWirelessSupplyContextAccessor"));
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
