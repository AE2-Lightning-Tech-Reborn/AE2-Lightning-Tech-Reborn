package com.moakiee.ae2lt.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class TianshuControllerToolbarSourceContractTest {
    private static final Path SCREEN_SOURCE = Path.of(
            "src/main/java/com/moakiee/ae2lt/client/TianshuSupercomputerControllerScreen.java");
    private static final Path BUTTON_SOURCE = Path.of(
            "src/main/java/com/moakiee/ae2lt/client/TextureToggleButton.java");
    private static final Path PACKET_SOURCE = Path.of(
            "src/main/java/com/moakiee/ae2lt/network/TianshuControllerActionPacket.java");
    private static final Path PORT_SOURCE = Path.of(
            "src/main/java/com/moakiee/ae2lt/blockentity/TianshuSupercomputerPortBlockEntity.java");
    private static final Path CONTROLLER_SOURCE = Path.of(
            "src/main/java/com/moakiee/ae2lt/blockentity/TianshuSupercomputerControllerBlockEntity.java");

    @Test
    void controllerToolbarCyclesAnExclusiveAlgorithmLock() throws Exception {
        String screen = Files.readString(SCREEN_SOURCE);
        String button = Files.readString(BUTTON_SOURCE);
        String packet = Files.readString(PACKET_SOURCE);
        String port = Files.readString(PORT_SOURCE);
        String controller = Files.readString(CONTROLLER_SOURCE);

        assertTrue(screen.contains("TextureToggleButton.ButtonType.QUICK_BUILD"));
        assertTrue(screen.contains("TianshuControllerActionPacket.Action.AUTO_BUILD"));
        assertTrue(screen.contains("TextureToggleButton.ButtonType.CPU_SELECTION"));
        assertTrue(screen.contains("TianshuControllerActionPacket.Action.CYCLE_ALGORITHM"));
        assertTrue(screen.contains("algorithmButton.setPosition(x, y + 22)"));
        assertTrue(screen.contains("algorithmButton.setStateIndex(menu.getAlgorithmIndex())"));
        assertTrue(screen.contains("menu.getAlgorithmTranslationKey()"));
        assertTrue(screen.contains("ae2lt.tianshu.gui.algorithm.v2"));
        assertTrue(screen.contains("ae2lt.tianshu.gui.algorithm.cp_sat"));
        assertTrue(screen.contains("ae2lt.tianshu.gui.algorithm.vanilla"));

        assertTrue(button.contains("texture(\"quick_compute_on\")"));
        assertTrue(button.contains("texture(\"lightning_high_voltage\")"));
        assertTrue(button.contains("texture(\"quick_compute_off\")"));

        assertTrue(packet.contains("CYCLE_ALGORITHM -> controller.cycleExclusivePlanningAlgorithm()"));
        assertTrue(controller.contains("ExclusiveCraftingPlanning.ownedAlgorithms()"));
        assertTrue(controller.contains("cycleExclusivePlanningAlgorithm()"));
        assertTrue(port.contains("getProvidedAlgorithms()"));
        assertTrue(port.contains("ExclusiveCraftingPlanning.ownedAlgorithms()"));
        assertTrue(port.contains("ExclusiveCraftingPlanning.normalize(selection.algorithmId())"));
    }
}
