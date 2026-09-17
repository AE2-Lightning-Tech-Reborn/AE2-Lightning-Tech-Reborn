package com.moakiee.ae2lt.crafting.report;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CraftingReportPresentationContractTest {
    private static final Path ROOT = Path.of("src/main");

    @Test
    void vanillaPlannerCannotEnableTheLightningReport() throws Exception {
        String mixin = Files.readString(ROOT.resolve(
                "java/com/moakiee/ae2lt/mixin/CraftConfirmMenuReportMixin.java"));

        assertTrue(mixin.contains("CraftingAlgorithmCalculationStatus.selected(job)"));
        assertTrue(mixin.contains("CraftingPlanningEngines.VANILLA_ID.equals(selectedPlanner)"));
        assertTrue(mixin.contains("if (selectedPlanner != null)"));
        assertFalse(mixin.contains("selectedPlanner != null\n                &&"));
    }

    @Test
    void reportShowsOnlyTheRequestedByteFormat() throws Exception {
        String screen = Files.readString(ROOT.resolve(
                "java/com/moakiee/ae2lt/client/AE2LtCraftConfirmScreen.java"));
        String style = Files.readString(ROOT.resolve(
                "resources/assets/ae2/screens/ae2lt_craft_confirm.json"));
        String chinese = Files.readString(ROOT.resolve(
                "resources/assets/ae2lt/lang/zh_cn.json"));

        assertFalse(screen.contains("formatDuration"));
        assertFalse(style.contains("calculation_time"));
        assertTrue(style.contains("\"left\": 28"));
        assertTrue(chinese.contains("\"gui.ae2lt.crafting_report.bytes\": \"字节: %sB   (%s)\""));
        assertTrue(screen.contains("NumberFormat.getIntegerInstance(Locale.US)"));
        assertTrue(screen.contains("ReadableNumberConverter.format(bytes, 4)"));
        assertFalse(screen.contains("BYTES_PER_TB"));
    }
}
