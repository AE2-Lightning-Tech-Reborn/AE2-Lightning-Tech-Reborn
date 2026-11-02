package com.moakiee.ae2lt.debug;

import com.moakiee.ae2lt.logic.craft.migration.PatternMigrationGameTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2lt_actual")
@PrefixGameTestTemplate(false)
public final class ActualCompatibilityGameTests {
    @GameTest(templateNamespace = "ae2lt_actual", template = "empty")
    public static void actualDependenciesRequired(GameTestHelper helper) {
        for (String id : new String[] {"extendedae_plus", "ldlib", "expatternprovider"}) {
            helper.assertTrue(ModList.get().isLoaded(id), "Required dependency missing: " + id);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "ae2lt_actual", template = "migration_test", timeoutTicks = 300)
    public static void actualExtendedAePlusAggregate(GameTestHelper helper) throws Exception {
        helper.assertTrue(ModList.get().isLoaded("extendedae_plus"), "ExtendedAE Plus must not be skipped");
        PatternMigrationGameTests.eaepAggregateDeduplicatesPhysicalCoreInventories(helper);
    }

    @GameTest(templateNamespace = "ae2lt_actual", template = "migration_test", timeoutTicks = 300)
    public static void actualExtendedAePhysicalCore(GameTestHelper helper) throws Exception {
        helper.assertTrue(ModList.get().isLoaded("expatternprovider"), "ExtendedAE must not be skipped");
        PatternMigrationGameTests.eaeCoreUsesPhysicalSlotsAndDefersCatalogRefresh(helper);
    }
}
