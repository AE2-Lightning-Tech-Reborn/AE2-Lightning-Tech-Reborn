package com.moakiee.ae2lt.mixin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class GtlExclusivePlanningSourceContractTest {
    private static final Path CONTROLLER = Path.of(
            "src/main/java/com/moakiee/ae2lt/mixin/gtlcore/TransfiniteComputationArrayMachineMixin.java");
    private static final Path INTERFACE = Path.of(
            "src/main/java/com/moakiee/ae2lt/mixin/gtlcore/MECraftingCPUInterfacePartMachineMixin.java");
    private static final Path UI = Path.of(
            "src/main/java/com/moakiee/ae2lt/integration/gtlcore/TransfiniteExclusivePlanningUi.java");
    private static final Path HELPER = Path.of(
            "src/main/java/com/moakiee/ae2lt/crafting/algorithm/ExclusiveCraftingPlanning.java");
    private static final Path PLUGIN = Path.of(
            "src/main/java/com/moakiee/ae2lt/mixin/AE2LTMixinConfigPlugin.java");
    private static final Path MIXINS = Path.of("src/main/resources/ae2lt.mixins.json");
    private static final Path EN = Path.of("src/main/resources/assets/ae2lt/lang/en_us.json");
    private static final Path ZH = Path.of("src/main/resources/assets/ae2lt/lang/zh_cn.json");

    @Test
    void transfiniteGuiCyclesAnExclusiveLockPublishedOnTheMeInterfaceNode() throws Exception {
        String controller = Files.readString(CONTROLLER);
        String iface = Files.readString(INTERFACE);
        String ui = Files.readString(UI);
        String helper = Files.readString(HELPER);
        String plugin = Files.readString(PLUGIN);
        String mixins = Files.readString(MIXINS);
        String en = Files.readString(EN);
        String zh = Files.readString(ZH);

        assertTrue(controller.contains("@Pseudo"));
        assertTrue(controller.contains(
                "targets = \"org.gtlcore.gtlcore.common.machine.multiblock.electric.TransfiniteComputationArrayMachine\""));
        assertTrue(controller.contains("implements ExclusiveCraftingLockSource"));
        assertTrue(controller.contains("DefaultCraftingAlgorithmProviderState"));
        assertTrue(controller.contains("ExclusiveCraftingPlanning.ownedAlgorithms()"));
        assertTrue(controller.contains("method = \"saveCustomPersistedData\""));
        assertTrue(controller.contains("method = \"loadCustomPersistedData\""));
        assertTrue(controller.contains("AE2LT$ALGORITHM_TAG = \"CraftingAlgorithmProvider\""));
        assertTrue(controller.contains("method = \"createUIWidget\""));
        assertTrue(controller.contains("TransfiniteExclusivePlanningUi.attach"));
        assertTrue(controller.contains("ae2lt$cycleExclusiveAlgorithm"));
        assertFalse(controller.contains("@Persisted"));
        assertFalse(controller.contains("@DescSynced"));

        assertTrue(iface.contains("@Pseudo"));
        assertTrue(iface.contains(
                "targets = \"org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MECraftingCPUInterfacePartMachine\""));
        assertTrue(iface.contains("implements CraftingAlgorithmProvider"));
        assertTrue(iface.contains("ExclusiveCraftingLockSource"));
        assertTrue(iface.contains("addService"));
        assertTrue(iface.contains("CraftingAlgorithmProvider.class"));
        assertTrue(iface.contains("TransfiniteExclusivePlanningAccess.formedController"));
        assertTrue(iface.contains("ae2lt$nodeActive()"));

        assertTrue(ui.contains("BUTTON_Y = 83"));
        assertTrue(ui.contains("CYCLE_COMPONENT_ID = \"ae2lt_cycle_algorithm\""));
        assertTrue(ui.contains("withButton"));
        assertTrue(ui.contains("clickHandler"));
        assertTrue(ui.contains("!remote"));

        assertTrue(helper.contains("exclusiveTianshuAlgorithm(grid)"));
        assertTrue(helper.contains("exclusiveLockSourceAlgorithm(grid)"));
        assertTrue(helper.contains("grid.getNodes()"));
        assertTrue(helper.contains("CraftingAlgorithmProvider.class"));

        assertTrue(mixins.contains("gtlcore.TransfiniteComputationArrayMachineMixin"));
        assertTrue(mixins.contains("gtlcore.MECraftingCPUInterfacePartMachineMixin"));
        assertTrue(plugin.contains("\"TransfiniteComputationArrayMachineMixin\", \"gtlcore\""));
        assertTrue(plugin.contains("\"MECraftingCPUInterfacePartMachineMixin\", \"gtlcore\""));

        assertTrue(en.contains("ae2lt.gtl.gui.algorithm.tooltip"));
        assertTrue(zh.contains("ae2lt.gtl.gui.algorithm.tooltip"));
        assertTrue(en.contains("ae2lt.tianshu.gui.algorithm.v2"));
        assertTrue(zh.contains("ae2lt.tianshu.gui.algorithm.v2"));
    }
}
