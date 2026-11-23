package com.moakiee.ae2lt.client.tianshu;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class TianshuControllerToolbarSourceContractTest {
    @Test
    void autoBuildAndAlgorithmSelectionRemainAvailableTogether() throws Exception {
        String screen = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/tianshu/TianshuSupercomputerControllerScreen.java"));
        String packet = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/network/TianshuControllerActionPacket.java"));

        int buildButton = screen.indexOf("glyph(\"quick_build\")");
        int selectionButton = screen.indexOf("glyph(\"algorithm_settings\")");

        assertTrue(buildButton >= 0);
        assertTrue(selectionButton > buildButton);
        assertTrue(screen.contains("TianshuControllerActionPacket.Action.AUTO_BUILD"));
        assertTrue(screen.contains("LdlibToolbarButton.toolbar(-18, 0, () -> BUILD"));
        assertTrue(screen.contains("LdlibToolbarButton.toolbar(-18, 22, () -> SELECTION"));
        assertTrue(packet.contains("case AUTO_BUILD -> controller.autoBuild(player)"));
    }
}
