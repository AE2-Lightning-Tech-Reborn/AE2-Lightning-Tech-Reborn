package com.moakiee.ae2lt.mixin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class ExclusivePlanningMixinSourceContractTest {
    private static final Path CALCULATION = Path.of(
            "src/main/java/com/moakiee/ae2lt/mixin/thunderbolt/CraftingCalculationExclusivePlanningMixin.java");
    private static final Path SERVICE = Path.of(
            "src/main/java/com/moakiee/ae2lt/mixin/thunderbolt/CraftingServiceExclusivePlanningMixin.java");
    private static final Path MIXINS = Path.of("src/main/resources/ae2lt.mixins.json");
    private static final Path HELPER = Path.of(
            "src/main/java/com/moakiee/ae2lt/crafting/algorithm/ExclusiveCraftingPlanning.java");
    private static final Pattern PRIORITY = Pattern.compile("priority\\s*=\\s*(\\d+)");

    @Test
    void exclusiveEnginePicksReconfigureAfterThunderboltAndFailClosedOnVanilla()
            throws Exception {
        String calculation = Files.readString(CALCULATION);
        String service = Files.readString(SERVICE);
        String mixins = Files.readString(MIXINS);
        String helper = Files.readString(HELPER);

        var servicePriority = PRIORITY.matcher(service);
        assertTrue(servicePriority.find(), "CraftingService exclusive mixin must set an explicit priority");
        assertTrue(Integer.parseInt(servicePriority.group(1)) > 1000,
                "BEFORE injectors closer to ExecutorService.submit run later; priority must exceed Thunderbolt's default 1000");

        var calculationPriority = PRIORITY.matcher(calculation);
        assertTrue(calculationPriority.find(), "CraftingCalculation exclusive mixin must set an explicit priority");
        assertTrue(Integer.parseInt(calculationPriority.group(1)) < 1000,
                "MixinExtras WrapMethod nests higher priority outside; inner wrap must stay below Thunderbolt's default 1000 so only the vanilla original.call is skipped");

        assertTrue(service.contains("method = \"beginCraftingCalculation\""));
        assertTrue(service.contains("Ljava/util/concurrent/ExecutorService;submit"));
        assertTrue(service.contains("shift = At.Shift.BEFORE"));
        assertTrue(service.contains("ExclusiveCraftingPlanning.candidatesForConfigure"));
        assertTrue(service.contains("ae2lt$setExclusiveEngine"));
        assertFalse(service.contains("com.moakiee.thunderbolt.mixin"));

        assertTrue(calculation.contains("implements ExclusivePlanningLock"));
        assertTrue(calculation.contains("@WrapMethod(method = \"computePlan\")"));
        assertTrue(calculation.contains("if (this.ae2lt$exclusiveEngine)"));
        assertTrue(calculation.contains("return null;"));
        assertTrue(calculation.contains("return original.call();"));

        assertTrue(mixins.contains("thunderbolt.CraftingCalculationExclusivePlanningMixin"));
        assertTrue(mixins.contains("thunderbolt.CraftingServiceExclusivePlanningMixin"));
        assertFalse(mixins.contains("CraftingCalculationPlanningCandidatesAccessor"));

        assertTrue(helper.contains("PlanningChoice.engine(exclusive), PlanningChoice.VANILLA"));
    }
}
