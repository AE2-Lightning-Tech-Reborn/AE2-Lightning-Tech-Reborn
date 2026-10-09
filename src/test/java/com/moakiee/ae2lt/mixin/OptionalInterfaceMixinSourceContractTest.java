package com.moakiee.ae2lt.mixin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class OptionalInterfaceMixinSourceContractTest {
    @Test
    void optionalIntegrationsHaveEarlyLoadingGuards() throws Exception {
        String plugin = Files.readString(Path.of("src/main/java/com/moakiee/ae2lt/mixin/AE2LTMixinConfigPlugin.java"));
        assertTrue(plugin.contains("\"InterfaceEnergyDistributionMixin\", \"appflux\""));
        assertTrue(plugin.contains("\"IpnContainerClickerMixin\", \"inventoryprofilesnext\""));
    }

    @Test
    void wirelessPollingDoesNotHookEveryBlockEntityDirtyMark() throws Exception {
        String mixins = Files.readString(Path.of("src/main/resources/ae2lt.mixins.json"));
        String source = Files.readString(Path.of("src/main/java/com/moakiee/ae2lt/blockentity/OverloadedInterfaceBlockEntity.java"));
        assertFalse(mixins.contains("BlockEntityInventoryChangeMixin"));
        assertFalse(source.contains("TARGET_CHANGE_LISTENERS"));
        assertTrue(source.contains("pollIOWheel(now)"));
        assertTrue(source.contains("importBackpressureWaiters.resumeReady"));
    }
}
