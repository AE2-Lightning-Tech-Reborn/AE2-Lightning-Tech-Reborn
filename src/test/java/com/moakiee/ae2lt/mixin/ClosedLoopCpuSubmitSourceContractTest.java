package com.moakiee.ae2lt.mixin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ClosedLoopCpuSubmitSourceContractTest {
    private static final Path VANILLA = Path.of(
            "src/main/java/com/moakiee/ae2lt/mixin/thunderbolt/CraftingCpuLogicMixin.java");
    private static final Path LEFTOVER = Path.of(
            "src/main/java/com/moakiee/ae2lt/mixin/thunderbolt/CraftingCpuLogicClosedLoopSubmitMixin.java");
    private static final Path ADV = Path.of(
            "src/main/java/com/moakiee/ae2lt/mixin/thunderbolt/AdvCraftingCpuLogicMixin.java");
    private static final Path ECO = Path.of(
            "src/main/java/com/moakiee/ae2lt/mixin/thunderbolt/ECOCraftingCpuLogicMixin.java");
    private static final Path TRANSFINITE = Path.of(
            "src/main/java/com/moakiee/ae2lt/mixin/gtlcore/TransfiniteCraftingLogicMixin.java");
    private static final Path PLUGIN = Path.of(
            "src/main/java/com/moakiee/ae2lt/mixin/AE2LTMixinConfigPlugin.java");
    private static final Path MIXINS = Path.of("src/main/resources/ae2lt.mixins.json");

    @Test
    void leftoverClosedLoopJobsAreRejectedOnForeignCpusAndOverloadWrapOutranksGtlOverwrite()
            throws Exception {
        String vanilla = Files.readString(VANILLA);
        String leftover = Files.readString(LEFTOVER);
        String adv = Files.readString(ADV);
        String eco = Files.readString(ECO);
        String transfinite = Files.readString(TRANSFINITE);
        String plugin = Files.readString(PLUGIN);
        String mixins = Files.readString(MIXINS);

        assertTrue(vanilla.contains("priority = 1200"),
                "WrapOperation on pushPattern must apply after GTLCore's priority-1100 overwrite");
        assertTrue(vanilla.contains("require = 0"));
        assertTrue(vanilla.contains("expect = 0"));
        assertFalse(vanilla.contains("method = \"trySubmitJob\""),
                "Leftover reject must not live in the wrap mixin; a wrap miss must not fail that class");
        assertFalse(vanilla.contains("ClosedLoopCpuSubmitGuard.rejectIfClosedLoop(plan, cir)"));

        assertTrue(leftover.contains("method = \"trySubmitJob\""));
        assertTrue(leftover.contains("ClosedLoopCpuSubmitGuard.rejectIfClosedLoop(plan, cir)"));
        assertTrue(mixins.contains("thunderbolt.CraftingCpuLogicClosedLoopSubmitMixin"));

        assertTrue(adv.contains("method = \"trySubmitJob\""));
        assertTrue(adv.contains("ClosedLoopCpuSubmitGuard.rejectIfClosedLoop(plan, cir)"));
        assertTrue(eco.contains("method = \"trySubmitJob\""));
        assertTrue(eco.contains("ClosedLoopCpuSubmitGuard.rejectIfClosedLoop(plan, cir)"));

        assertTrue(transfinite.contains("@Pseudo"));
        assertTrue(transfinite.contains(
                "targets = \"org.gtlcore.gtlcore.integration.ae2.crafting.transfinite.TransfiniteCraftingLogic\""));
        assertTrue(transfinite.contains("method = \"trySubmitJob\""));
        assertTrue(transfinite.contains("ClosedLoopCpuSubmitGuard.rejectIfClosedLoop(plan, cir)"));

        assertTrue(mixins.contains("gtlcore.TransfiniteCraftingLogicMixin"));
        assertTrue(plugin.contains("\"TransfiniteCraftingLogicMixin\", \"gtlcore\""));
    }
}
