package com.moakiee.ae2lt.client.machine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ControllerLdlibTreeContractTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/com/moakiee/ae2lt/client/" + path));
    }

    @Test
    void controllerScreensDrawAndHandleControlsThroughTheLdlibRoot() throws Exception {
        String base = read("machine/MultiblockControllerScreen.java");
        String matrix = read("machine/MatrixControllerScreen.java");
        String tianshu = read("tianshu/TianshuSupercomputerControllerScreen.java");

        assertTrue(base.contains("LdlibScreens.embed(this, buildRoot(), this::addRenderableWidget)"));
        assertTrue(base.contains("protected abstract View view();"));
        assertFalse(base.contains("renderLabels("));
        assertFalse(base.contains("atlas.draw(graphics, leftPos"));
        for (String screen : new String[] {matrix, tianshu}) {
            assertFalse(screen.contains("addRenderableWidget("));
            assertFalse(screen.contains("TextureToggleButton"));
            assertFalse(screen.contains("LdlibAe2Button"));
            assertFalse(screen.contains("renderLabels("));
            assertTrue(screen.contains("LdlibToolbarButton.toolbar("));
        }
    }

    @Test
    void controllersKeepTheirActionsAndSideLayout() throws Exception {
        String matrix = read("machine/MatrixControllerScreen.java");
        String tianshu = read("tianshu/TianshuSupercomputerControllerScreen.java");

        assertTrue(matrix.contains("LdlibToolbarButton.toolbar(-18, 0, () -> BUILD"));
        assertTrue(matrix.contains("LdlibToolbarButton.toolbar(-18, 22, () -> UPGRADE"));
        assertTrue(matrix.contains("migration.ldlibButton(-18, 44)"));
        assertTrue(matrix.contains("MatrixControllerActionPacket.Action.AUTO_BUILD"));
        assertTrue(matrix.contains("MatrixControllerActionPacket.Action.UPGRADE_PATTERN_STORAGE"));
        assertTrue(tianshu.contains("TianshuControllerActionPacket.Action.OPEN_ALGORITHM_SELECTION"));
        assertTrue(tianshu.contains("LdlibToolbarButton.tab(imageWidth, 0, Icon.PRIORITY"));
        assertTrue(tianshu.contains("SwitchGuisPacket.openSubMenu(PriorityMenu.TYPE)"));
    }
}
