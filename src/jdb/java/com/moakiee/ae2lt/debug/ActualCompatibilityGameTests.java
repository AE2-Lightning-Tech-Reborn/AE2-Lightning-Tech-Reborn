package com.moakiee.ae2lt.debug;

import com.moakiee.ae2lt.logic.craft.migration.PatternMigrationGameTests;
import com.moakiee.ae2lt.event.NaturalLightningTransformationHandler;
import com.moakiee.ae2lt.lightning.strike.LightningStrikeRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2lt_actual")
@PrefixGameTestTemplate(false)
public final class ActualCompatibilityGameTests {
    @GameTest(templateNamespace = "ae2lt_actual", template = "empty")
    public static void actualDependenciesRequired(GameTestHelper helper) {
        for (String id : new String[] {"neoecoae", "extendedae_plus", "ldlib", "expatternprovider"}) {
            helper.assertTrue(ModList.get().isLoaded(id), "Required dependency missing: " + id);
        }
        helper.assertTrue(Boolean.getBoolean("ae2lt.neoecoTestPresent"), "NeoEco recipe fixture must be enabled");
        helper.succeed();
    }

    @GameTest(templateNamespace = "ae2lt_actual", template = "migration_test", timeoutTicks = 300)
    public static void actualNeoEcoPatternInventory(GameTestHelper helper) throws Exception {
        helper.assertTrue(ModList.get().isLoaded("neoecoae"), "NeoEco must not be skipped");
        PatternMigrationGameTests.ecoPhysicalSlotsAndSharedLeaseUseActualApi(helper);
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

    @GameTest(templateNamespace = "ae2lt_actual", template = "empty", timeoutTicks = 200)
    public static void actualNeoEcoCatalystRecipe(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("neoecoae"), "NeoEco catalyst must not be skipped");
        NeoEcoCatalyzerGameTests.optionalRecipeProducesSixteenAndRetainsCatalyst(helper);
    }

    @GameTest(templateNamespace = "ae2lt_actual", template = "migration_test", timeoutTicks = 20)
    public static void actualNeoEcoOrdinaryLightningStillTransforms(GameTestHelper helper) throws Exception {
        BlockPos center = helper.absolutePos(new BlockPos(4, 2, 4));
        var quartz = BuiltInRegistries.BLOCK.get(new ResourceLocation("ae2", "flawless_budding_quartz"));
        helper.getLevel().setBlockAndUpdate(center, quartz.defaultBlockState());
        invokeNeoLightning(helper, center, 2);
        assertNeoCrystal(helper, center);
        helper.succeed();
    }

    @GameTest(templateNamespace = "ae2lt_actual", template = "migration_test", timeoutTicks = 20)
    public static void actualNeoEcoIncompleteRitualDoesNotReserveCenter(GameTestHelper helper) throws Exception {
        var recipe = richLightningRecipe(helper);
        BlockPos center = buildRichRitual(helper, recipe);
        var missing = recipe.requirements().get(0);
        helper.getLevel().setBlockAndUpdate(center.offset(missing.offset()), Blocks.AIR.defaultBlockState());
        invokeNeoLightning(helper, center, 2);
        assertNeoCrystal(helper, center);
        for (var requirement : recipe.requirements()) {
            var actual = helper.getLevel().getBlockState(center.offset(requirement.offset()));
            helper.assertTrue(requirement == missing ? actual.isAir() : actual.is(requirement.block()),
                    "Neo conversion must leave the incomplete LT ritual's materials intact");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "ae2lt_actual", template = "migration_test", timeoutTicks = 20)
    public static void actualNeoEcoCompleteRitualAllowsNearbyConversion(GameTestHelper helper) throws Exception {
        var recipe = richLightningRecipe(helper);
        BlockPos center = buildRichRitual(helper, recipe);
        BlockPos nearby = center.offset(1, 1, 0);
        helper.getLevel().setBlockAndUpdate(nearby, recipe.centerInput().defaultBlockState());
        // Neo scans center (bound 2) before this unrelated neighbor (bound 4).
        // Both rolls succeed, so a whole-handler cancellation would fail this test.
        invokeNeoLightning(helper, center, 2, 4);
        assertNeoCrystal(helper, nearby);
        helper.assertTrue(helper.getLevel().getBlockState(center).is(recipe.centerInput()),
                "Artificial lightning must preserve the complete natural-only LT center");
        for (var requirement : recipe.requirements()) {
            helper.assertTrue(helper.getLevel().getBlockState(center.offset(requirement.offset())).is(requirement.block()),
                    "Reserving the LT center must not consume any materials");
        }
        helper.succeed();
    }

    private static LightningStrikeRecipe richLightningRecipe(GameTestHelper helper) {
        return (LightningStrikeRecipe) helper.getLevel().getRecipeManager().byKey(new ResourceLocation(
                "ae2lt", "lightning_strike/rich_flawless_budding_overload_crystal")).orElseThrow();
    }

    private static BlockPos buildRichRitual(GameTestHelper helper, LightningStrikeRecipe recipe) {
        var level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(4, 2, 4));
        level.setBlockAndUpdate(center, recipe.centerInput().defaultBlockState());
        for (var requirement : recipe.requirements()) {
            level.setBlockAndUpdate(center.offset(requirement.offset()), requirement.block().defaultBlockState());
        }
        level.setBlockAndUpdate(center.above(), Blocks.LIGHTNING_ROD.defaultBlockState());
        return center;
    }

    private static void assertNeoCrystal(GameTestHelper helper, BlockPos pos) {
        var crystal = BuiltInRegistries.BLOCK.get(new ResourceLocation("neoecoae", "flawless_budding_energized_crystal"));
        helper.assertTrue(crystal != Blocks.AIR && helper.getLevel().getBlockState(pos).is(crystal),
                "The actual NeoECO lightning handler must still convert ordinary AE2 budding quartz");
    }

    private static void invokeNeoLightning(GameTestHelper helper, BlockPos center, int... bounds) throws Exception {
        helper.assertTrue(ModList.get().isLoaded("neoecoae"), "Real NeoECO is required");
        var level = helper.getLevel();
        var lightning = new LightningBolt(EntityType.LIGHTNING_BOLT, level);
        lightning.setPos(Vec3.atBottomCenterOf(center.above()));
        // Invoke the real join callback synchronously to isolate Neo's random rolls from other mods.
        // The existing main GameTests separately cover actual entity admission and LT pre-tick.
        var method = Class.forName("cn.dancingsnow.neoecoae.event.NELightningTransformEvents")
                .getMethod("onEntityJoinLevel", EntityJoinLevelEvent.class);
        long seed = seedForSuccessfulRolls(bounds);
        level.getRandom().setSeed(seed);
        method.invoke(null, new EntityJoinLevelEvent(lightning, level));
        helper.assertTrue(lightning.isAlive() && lightning.getPersistentData().isEmpty(),
                "Reserving a ritual must not discard, mark or collect the lightning early");
        System.out.println("NEOECO_LIGHTNING_REAL_CALLBACK_SEED=" + seed);
    }

    private static long seedForSuccessfulRolls(int... bounds) {
        for (long seed = 0; seed < 100_000; seed++) {
            var random = RandomSource.create(seed);
            boolean success = true;
            for (int bound : bounds) {
                if (random.nextInt(bound) != 0) {
                    success = false;
                    break;
                }
            }
            if (success) {
                return seed;
            }
        }
        throw new AssertionError("No deterministic NeoECO lightning seed found");
    }
}
