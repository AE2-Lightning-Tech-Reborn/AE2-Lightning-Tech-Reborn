package com.moakiee.ae2lt.debug;

import com.moakiee.ae2lt.logic.craft.migration.PatternMigrationGameTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2lt_migration_perf")
@PrefixGameTestTemplate(false)
public final class PatternMigrationPerformanceGameTests {
    @GameTest(templateNamespace="ae2lt_migration_perf", template="migration_test", timeoutTicks=6000)
    public static void hundredThousandSlotsPreserveNativeCatalog(GameTestHelper helper) {
        PatternMigrationGameTests.oneHundredThousandPhysicalSlotsUseBoundedSlices(helper);
    }
}
