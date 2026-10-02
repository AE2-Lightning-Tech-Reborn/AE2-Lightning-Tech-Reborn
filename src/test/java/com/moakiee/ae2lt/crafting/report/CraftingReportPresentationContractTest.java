package com.moakiee.ae2lt.crafting.report;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
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
                "java/com/moakiee/ae2lt/client/crafting/AE2LtCraftConfirmScreen.java"));
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

    @Test
    void bookmarkButtonFitsBetweenCancelAndStartAndDoesNotCloseTheScreen() throws Exception {
        String screen = Files.readString(ROOT.resolve(
                "java/com/moakiee/ae2lt/client/crafting/AE2LtCraftConfirmScreen.java"));
        String plugin = Files.readString(ROOT.resolve(
                "java/com/moakiee/ae2lt/integration/jei/JEIPlugin.java"));
        var widgets = JsonParser.parseString(Files.readString(ROOT.resolve(
                "resources/assets/ae2/screens/ae2lt_craft_confirm.json")))
                .getAsJsonObject().getAsJsonObject("widgets");
        var cancel = widgets.getAsJsonObject("cancel");
        var bookmark = widgets.getAsJsonObject("bookmarkMissing");
        var start = widgets.getAsJsonObject("start");
        assertTrue(cancel.get("left").getAsInt() + cancel.get("width").getAsInt()
                < bookmark.get("left").getAsInt());
        assertTrue(bookmark.get("left").getAsInt() + bookmark.get("width").getAsInt()
                < start.get("left").getAsInt());
        assertTrue(screen.contains("JeiBookmarkAccess.isAvailable() && MissingMaterialBookmarks.hasMissing(plan)"));
        int callbackStart = screen.indexOf("private void bookmarkMissing()");
        int callbackEnd = screen.indexOf("private void selectNextCpu()", callbackStart);
        String callback = screen.substring(callbackStart, callbackEnd);
        assertTrue(callback.contains("MissingMaterialBookmarks.keys(menu.getPlan())"));
        assertFalse(callback.contains("goBack"));
        assertFalse(callback.contains("startJob"));
        assertFalse(callback.contains("hasShiftDown"));
        assertTrue(plugin.contains("JeiBookmarkAccessImpl.setRuntime(runtime)"));
        assertTrue(plugin.contains("JeiBookmarkAccessImpl.clearRuntime()"));
        for (String language : new String[] {"en_us", "zh_cn"}) {
            var translations = JsonParser.parseString(Files.readString(ROOT.resolve(
                    "resources/assets/ae2lt/lang/" + language + ".json"))).getAsJsonObject();
            assertTrue(translations.has("gui.ae2lt.crafting_report.bookmark_missing"));
            assertTrue(translations.has("gui.ae2lt.crafting_report.bookmark_missing.tooltip"));
        }
    }

    @Test
    void forceStartUsesOneGuardedSubmissionPathAndPlayerTooltipsDescribeOnlyFunctions() throws Exception {
        String screen = Files.readString(ROOT.resolve(
                "java/com/moakiee/ae2lt/client/crafting/AE2LtCraftConfirmScreen.java"));
        assertTrue(screen.contains("this::startJob"));
        assertFalse(screen.contains("menu::startJob"));
        assertTrue(screen.contains("forceStart = canForce && hasShiftDown()"));
        assertTrue(screen.contains("start.active = !menu.hasNoCPU() && (startable || forceStart)"));
        int submitStart = screen.indexOf("private void startJob()");
        int submitEnd = screen.indexOf("private Component getNextCpuButtonLabel()", submitStart);
        String submit = screen.substring(submitStart, submitEnd);
        assertTrue(submit.contains("CraftingReportStartState.normallyStartable(plan, bigMode)"));
        assertTrue(submit.contains("menu.hasNoCPU()"));
        assertTrue(submit.indexOf("EaepForceCraftingAccess.synchronize(menu, forceStart)")
                < submit.indexOf("menu.startJob()"));
        assertTrue(screen.substring(screen.indexOf("public boolean keyPressed")).contains("startJob();"));
        for (String language : new String[] {"en_us", "zh_cn"}) {
            var translations = JsonParser.parseString(Files.readString(ROOT.resolve(
                    "resources/assets/ae2lt/lang/" + language + ".json"))).getAsJsonObject();
            for (String key : new String[] {"bookmark_missing.tooltip", "force_start.tooltip", "force_start.hint"}) {
                String tooltip = translations.get("gui.ae2lt.crafting_report." + key).getAsString();
                assertFalse(tooltip.contains("EAEP"));
                assertFalse(tooltip.contains("ExtendedAE"));
                assertFalse(tooltip.contains("接口"));
                assertFalse(tooltip.contains("integration"));
                assertFalse(tooltip.contains("requires JEI"));
            }
        }
    }
}
