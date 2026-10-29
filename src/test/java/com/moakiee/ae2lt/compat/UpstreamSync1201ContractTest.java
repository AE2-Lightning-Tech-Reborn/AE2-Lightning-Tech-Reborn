package com.moakiee.ae2lt.compat;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.regex.Pattern;
import com.google.gson.JsonParser;
import com.moakiee.ae2lt.mixin.AE2LTMixinConfigPlugin;
import net.minecraftforge.fml.loading.LoadingModList;
import org.junit.jupiter.api.Test;

/** Integration contracts for the upstream port, beyond the individual helper tests. */
class UpstreamSync1201ContractTest {
    @Test void everyRegisteredMixinHasAnActualLocalSource() throws Exception {
        var config = JsonParser.parseString(Files.readString(Path.of("src/main/resources/ae2lt.mixins.json")))
                .getAsJsonObject();
        var seen = new HashSet<String>();
        for (var category : new String[] {"mixins", "client"}) {
            for (var entry : config.getAsJsonArray(category)) {
                String name = entry.getAsString();
                assertTrue(seen.add(name), "duplicate Mixin: " + name);
                assertTrue(Files.isRegularFile(Path.of("src/main/java/com/moakiee/ae2lt/mixin",
                        name.replace('.', '/') + ".java")), "missing local Mixin source: " + name);
            }
        }
    }

    @Test void migrationPacketsAreAppendedWithoutDuplicateRegistrations() throws Exception {
        String network = Files.readString(Path.of("src/main/java/com/moakiee/ae2lt/network/NetworkInit.java"));
        var matcher = Pattern.compile("([\\w.]+Packet)\\.class").matcher(network);
        var packets = new java.util.ArrayList<String>(); var seen = new HashSet<String>();
        while (matcher.find()) {
            String name = matcher.group(1); name = name.substring(name.lastIndexOf('.') + 1);
            assertTrue(seen.add(name), "duplicate packet: " + name); packets.add(name);
        }
        assertTrue(packets.size() > 2);
        assertEquals(java.util.List.of("MatrixPatternMigrationActionPacket", "MatrixPatternMigrationStatusPacket"),
                packets.subList(packets.size() - 2, packets.size()));
        assertTrue(network.contains("PROTOCOL_VERSION = \"12\""),
                "reject clients with a different packet table");
    }

    @Test void appFluxRoutingMixinIsRegisteredAndGuardedWhenOptionalModIsAbsent() throws Exception {
        var config = JsonParser.parseString(Files.readString(Path.of("src/main/resources/ae2lt.mixins.json")))
                .getAsJsonObject();
        boolean registered = false;
        for (var entry : config.getAsJsonArray("mixins")) {
            registered |= "appflux.InterfaceEnergyDistributionMixin".equals(entry.getAsString());
        }
        assertTrue(registered);
        var loading = LoadingModList.get();
        if (loading == null || loading.getModFileById("appflux") == null) {
            assertFalse(new AE2LTMixinConfigPlugin().shouldApplyMixin("appeng.helpers.InterfaceLogic",
                    "com.moakiee.ae2lt.mixin.appflux.InterfaceEnergyDistributionMixin"));
        }
    }

    @Test void allMigrationModelResourcesAreDevelopmentOnly() throws Exception {
        assertTrue(Files.isRegularFile(Path.of("src/jdb/resources/data/ae2lt_migration/structures/migration_test.nbt")));
        assertTrue(Files.isRegularFile(Path.of("src/jdb/resources/data/ae2lt_machine_recharge/structures/empty.nbt")));
        assertFalse(Files.exists(Path.of("src/main/resources/data/ae2lt_migration/structures/migration_test.nbt")));
    }
}
