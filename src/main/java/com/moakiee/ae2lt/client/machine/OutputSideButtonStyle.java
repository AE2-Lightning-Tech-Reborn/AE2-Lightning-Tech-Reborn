package com.moakiee.ae2lt.client.machine;

import com.mojang.blaze3d.vertex.PoseStack;
import com.moakiee.ae2lt.client.compat.LegacyGui;

final class OutputSideButtonStyle {
    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int EDGE = 0xFF555555;
    private static final int DISABLED_FACE = 0xFF8B8B8B;
    private static final int ENABLED_FACE = 0xFFDFDFDF;

    private OutputSideButtonStyle() {
    }

    static void renderBackground(PoseStack graphics, int x, int y, boolean enabled) {
        int face = enabled ? ENABLED_FACE : DISABLED_FACE;

        LegacyGui.fill(graphics, x + 2, y + 2, x + 16, y + 16, face);

        LegacyGui.fill(graphics, x + 3, y, x + 15, y + 1, BLACK);
        LegacyGui.fill(graphics, x + 2, y + 1, x + 3, y + 2, BLACK);
        LegacyGui.fill(graphics, x + 15, y + 1, x + 16, y + 2, BLACK);
        LegacyGui.fill(graphics, x + 1, y + 2, x + 2, y + 3, BLACK);
        LegacyGui.fill(graphics, x + 16, y + 2, x + 17, y + 3, BLACK);
        LegacyGui.fill(graphics, x, y + 3, x + 1, y + 15, BLACK);
        LegacyGui.fill(graphics, x + 17, y + 3, x + 18, y + 15, BLACK);
        LegacyGui.fill(graphics, x + 1, y + 3, x + 2, y + 15, EDGE);
        LegacyGui.fill(graphics, x + 16, y + 3, x + 17, y + 15, EDGE);
        LegacyGui.fill(graphics, x + 1, y + 15, x + 2, y + 16, BLACK);
        LegacyGui.fill(graphics, x + 16, y + 15, x + 17, y + 16, BLACK);
        LegacyGui.fill(graphics, x + 2, y + 16, x + 3, y + 17, BLACK);
        LegacyGui.fill(graphics, x + 15, y + 16, x + 16, y + 17, BLACK);
        LegacyGui.fill(graphics, x + 3, y + 17, x + 15, y + 18, BLACK);

        LegacyGui.fill(graphics, x + 3, y + 1, x + 15, y + 2, WHITE);
        LegacyGui.fill(graphics, x + 3, y + 16, x + 15, y + 17, EDGE);
    }

    static void renderClearIcon(PoseStack graphics, int x, int y) {
        LegacyGui.fill(graphics, x, y, x + 8, y + 8, BLACK);
        LegacyGui.fill(graphics, x + 1, y + 1, x + 7, y + 7, DISABLED_FACE);
        LegacyGui.fill(graphics, x + 2, y + 2, x + 3, y + 3, ENABLED_FACE);
        LegacyGui.fill(graphics, x + 5, y + 2, x + 6, y + 3, ENABLED_FACE);
        LegacyGui.fill(graphics, x + 3, y + 3, x + 5, y + 5, ENABLED_FACE);
        LegacyGui.fill(graphics, x + 2, y + 5, x + 3, y + 6, ENABLED_FACE);
        LegacyGui.fill(graphics, x + 5, y + 5, x + 6, y + 6, ENABLED_FACE);
    }
}
