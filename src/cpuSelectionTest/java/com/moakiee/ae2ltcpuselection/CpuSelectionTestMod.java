package com.moakiee.ae2ltcpuselection;

import java.util.List;
import java.util.function.Consumer;
import java.util.Arrays;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.network.chat.Component;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Development-only mod that registers tests against transformed AE2 internals. */
@Mod(CpuSelectionTestMod.MODID)
public final class CpuSelectionTestMod {
    static final String MODID = "ae2lt_cpu_selection";

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(BuiltInRegistries.TEST_FUNCTION, MODID);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> CAPACITY =
            FUNCTIONS.register("capacity_loss_and_recovery", () -> guarded(
                    TimeWheelCpuSelectionGameTests::capacityLossAndRecoveryKeepTheManualTarget));
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> MISSING =
            FUNCTIONS.register("missing_target", () -> guarded(
                    TimeWheelCpuSelectionGameTests::missingTargetWaitsForExplicitReselection));
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> NEW_CPU =
            FUNCTIONS.register("newly_available_cpu", () -> guarded(
                    TimeWheelCpuSelectionGameTests::newlyAvailableCpuCannotDisplaceSelectedPool));
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> PROVIDER_GLOBAL =
            FUNCTIONS.register("provider_global_refresh", () -> guarded(
                    ProviderCacheGameTests::globalRefreshInvalidatesWithinTheSameTick));
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> PROVIDER_NODE =
            FUNCTIONS.register("provider_node_refresh", () -> guarded(
                    ProviderCacheGameTests::nodeAndPatternChangesInvalidateRetainedSnapshots));
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> BIG_CELLS =
            FUNCTIONS.register("big_multiple_cells", () -> guarded(
                    BigIntegerPipelineGameTests::multipleInfiniteCellsKeepLegacyProjectionPositive));
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> BIG_LIVE =
            FUNCTIONS.register("big_live_storage_mixins", () -> guarded(
                    BigIntegerPipelineGameTests::liveMixinsShareExactAndLegacyInventory));
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> BIG_PRIORITY =
            FUNCTIONS.register("big_native_storage_priority", () -> guarded(
                    BigIntegerPipelineGameTests::nativeStoragePriorityIsPreserved));
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> BIG_RESTART =
            FUNCTIONS.register("big_plan_restart_refund", () -> guarded(
                    BigIntegerPipelineGameTests::actualRecipePlanRestartCommitAndRefundKeepEveryUnit));
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> BIG_WORLD =
            FUNCTIONS.register("big_formed_machine_order", () -> guarded(
                    BigIntegerPipelineGameTests::formedMultidimensionalMachinesRunAnExactOrder));
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> PIGMEE_STORAGE =
            FUNCTIONS.register("pigmee_station_storage_power", () -> guarded(
                    PigmeeSynthesisStationGameTests::liveStorageAndPower));
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> PIGMEE_INTERFACE =
            FUNCTIONS.register("pigmee_station_rejects_me_interface", () -> guarded(
                    PigmeeSynthesisStationGameTests::rejectsMeInterface));
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> PIGMEE_MENU =
            FUNCTIONS.register("pigmee_station_menu_persistence", () -> guarded(
                    PigmeeSynthesisStationGameTests::menuCraftingAndPersistence));
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> PIGMEE_CAPS =
            FUNCTIONS.register("pigmee_catalyzer_capabilities", () -> guarded(
                    PigmeeCrystalCatalyzerGameTests::pigmeeCapabilitiesAndSharedRecipe));
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> PIGMEE_CYCLES =
            FUNCTIONS.register("pigmee_catalyzer_two_cycles", () -> guarded(
                    PigmeeCrystalCatalyzerGameTests::pigmeeRunsTwoWaterOnlyCyclesWithoutNetworkPower));
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> PIGMEE_WAIT =
            FUNCTIONS.register("pigmee_catalyzer_waits_for_inputs", () -> guarded(
                    PigmeeCrystalCatalyzerGameTests::pigmeeWaitsForFullCatalystStackAndWater));
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> PIGMEE_BACKPRESSURE =
            FUNCTIONS.register("pigmee_catalyzer_backpressure", () -> guarded(
                    PigmeeCrystalCatalyzerGameTests::pigmeeOutputBackpressurePausesAndResumes));
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> PIGMEE_RELOAD =
            FUNCTIONS.register("pigmee_catalyzer_saved_progress", () -> guarded(
                    PigmeeCrystalCatalyzerGameTests::pigmeeSavedProgressAndLegacyRecipeIdResume));
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> NORMAL_CATALYZER =
            FUNCTIONS.register("normal_catalyzer_requires_power", () -> guarded(
                    PigmeeCrystalCatalyzerGameTests::normalCatalyzerDoesNotGainFreeProcessing));
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> CPU_PERSISTENCE_MIXINS =
            FUNCTIONS.register("cpu_persistence_mixins", () -> guarded(
                    CpuSelectionTestMod::cpuPersistenceMixins));
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> ADVANCED_PATTERN =
            FUNCTIONS.register("advanced_directional_pattern_round_trip", () -> guarded(
                    AvailableAddonCompatGameTests::advancedDirectionalPatternRoundTrip));
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> ADDON_RECIPES =
            FUNCTIONS.register("available_addon_recipes_load", () -> guarded(
                    AvailableAddonCompatGameTests::availableAddonRecipesLoad));
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> APPFLUX_CELL =
            FUNCTIONS.register("applied_flux_cell_bridge", () -> guarded(
                    AvailableAddonCompatGameTests::appliedFluxCellBridge));
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> APPFLUX_DELIVERY =
            FUNCTIONS.register("applied_flux_wireless_power_delivery", () -> guarded(
                    AvailableAddonCompatGameTests::appliedFluxWirelessPowerDelivery));

    public CpuSelectionTestMod(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener(this::registerTests);
    }

    private void registerTests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(id("default"),
                new TestEnvironmentDefinition.AllOf(List.of()));
        event.registerTest(id("capacity_loss_and_recovery"), new FunctionGameTestInstance(
                CAPACITY.getKey(), new TestData<>(environment,
                        Identifier.withDefaultNamespace("empty"), 20, 0, true)));
        event.registerTest(id("missing_target"), new FunctionGameTestInstance(
                MISSING.getKey(), new TestData<>(environment,
                        Identifier.withDefaultNamespace("empty"), 20, 0, true)));
        event.registerTest(id("newly_available_cpu"), new FunctionGameTestInstance(
                NEW_CPU.getKey(), new TestData<>(environment,
                        Identifier.withDefaultNamespace("empty"), 20, 0, true)));
        event.registerTest(id("provider_global_refresh"), new FunctionGameTestInstance(
                PROVIDER_GLOBAL.getKey(), new TestData<>(environment,
                        Identifier.withDefaultNamespace("empty"), 40, 0, true)));
        event.registerTest(id("provider_node_refresh"), new FunctionGameTestInstance(
                PROVIDER_NODE.getKey(), new TestData<>(environment,
                        Identifier.withDefaultNamespace("empty"), 40, 0, true)));
        event.registerTest(id("big_multiple_cells"), new FunctionGameTestInstance(
                BIG_CELLS.getKey(), new TestData<>(environment,
                        Identifier.withDefaultNamespace("empty"), 20, 0, true)));
        event.registerTest(id("big_live_storage_mixins"), new FunctionGameTestInstance(
                BIG_LIVE.getKey(), new TestData<>(environment,
                        Identifier.withDefaultNamespace("empty"), 20, 0, true)));
        event.registerTest(id("big_native_storage_priority"), new FunctionGameTestInstance(
                BIG_PRIORITY.getKey(), new TestData<>(environment,
                        Identifier.withDefaultNamespace("empty"), 20, 0, true)));
        event.registerTest(id("big_plan_restart_refund"), new FunctionGameTestInstance(
                BIG_RESTART.getKey(), new TestData<>(environment,
                        Identifier.withDefaultNamespace("empty"), 20, 0, true)));
        var largeEnvironment = event.registerEnvironment(id("large_multiblock"),
                new TestEnvironmentDefinition.AllOf(List.of()));
        event.registerTest(id("big_formed_machine_order"), new FunctionGameTestInstance(
                BIG_WORLD.getKey(), new TestData<>(largeEnvironment,
                        Identifier.withDefaultNamespace("empty"), 300, 0, true)));
        var pigmeeEnvironment = event.registerEnvironment(id("pigmee_station"),
                new TestEnvironmentDefinition.AllOf(List.of()));
        var pigmeeTemplate = Identifier.fromNamespaceAndPath("ae2lt", "pigmee_station_empty");
        event.registerTest(id("pigmee_station_storage_power"), new FunctionGameTestInstance(
                PIGMEE_STORAGE.getKey(), new TestData<>(pigmeeEnvironment,
                        pigmeeTemplate, 100, 0, true)));
        event.registerTest(id("pigmee_station_rejects_me_interface"), new FunctionGameTestInstance(
                PIGMEE_INTERFACE.getKey(), new TestData<>(pigmeeEnvironment,
                        pigmeeTemplate, 100, 0, true)));
        event.registerTest(id("pigmee_station_menu_persistence"), new FunctionGameTestInstance(
                PIGMEE_MENU.getKey(), new TestData<>(pigmeeEnvironment,
                        pigmeeTemplate, 100, 0, true)));
        event.registerTest(id("pigmee_catalyzer_capabilities"), new FunctionGameTestInstance(
                PIGMEE_CAPS.getKey(), new TestData<>(pigmeeEnvironment,
                        pigmeeTemplate, 100, 0, true)));
        event.registerTest(id("pigmee_catalyzer_two_cycles"), new FunctionGameTestInstance(
                PIGMEE_CYCLES.getKey(), new TestData<>(pigmeeEnvironment,
                        pigmeeTemplate, 700, 0, true)));
        event.registerTest(id("pigmee_catalyzer_waits_for_inputs"), new FunctionGameTestInstance(
                PIGMEE_WAIT.getKey(), new TestData<>(pigmeeEnvironment,
                        pigmeeTemplate, 800, 0, true)));
        event.registerTest(id("pigmee_catalyzer_backpressure"), new FunctionGameTestInstance(
                PIGMEE_BACKPRESSURE.getKey(), new TestData<>(pigmeeEnvironment,
                        pigmeeTemplate, 900, 0, true)));
        event.registerTest(id("pigmee_catalyzer_saved_progress"), new FunctionGameTestInstance(
                PIGMEE_RELOAD.getKey(), new TestData<>(pigmeeEnvironment,
                        pigmeeTemplate, 500, 0, true)));
        event.registerTest(id("normal_catalyzer_requires_power"), new FunctionGameTestInstance(
                NORMAL_CATALYZER.getKey(), new TestData<>(pigmeeEnvironment,
                        pigmeeTemplate, 400, 0, true)));
        event.registerTest(id("cpu_persistence_mixins"), new FunctionGameTestInstance(
                CPU_PERSISTENCE_MIXINS.getKey(), new TestData<>(environment,
                        Identifier.withDefaultNamespace("empty"), 20, 0, true)));
        event.registerTest(id("advanced_directional_pattern_round_trip"), new FunctionGameTestInstance(
                ADVANCED_PATTERN.getKey(), new TestData<>(environment,
                        Identifier.withDefaultNamespace("empty"), 20, 0, true)));
        event.registerTest(id("available_addon_recipes_load"), new FunctionGameTestInstance(
                ADDON_RECIPES.getKey(), new TestData<>(environment,
                        Identifier.withDefaultNamespace("empty"), 20, 0, true)));
        event.registerTest(id("applied_flux_cell_bridge"), new FunctionGameTestInstance(
                APPFLUX_CELL.getKey(), new TestData<>(environment,
                        Identifier.withDefaultNamespace("empty"), 20, 0, true)));
        event.registerTest(id("applied_flux_wireless_power_delivery"), new FunctionGameTestInstance(
                APPFLUX_DELIVERY.getKey(), new TestData<>(pigmeeEnvironment,
                        pigmeeTemplate, 120, 0, true)));
    }

    private static void cpuPersistenceMixins(GameTestHelper helper) throws ClassNotFoundException {
        assertMixinApplied(appeng.crafting.execution.CraftingCpuLogic.class);
        if (!net.neoforged.fml.ModList.get().isLoaded("advanced_ae")) {
            throw new GameTestAssertException(Component.literal("AdvancedAE development dependency missing"), 0);
        }
        assertMixinApplied(Class.forName("net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic"));
        var registries = helper.getLevel().registryAccess();
        var pos = net.minecraft.core.BlockPos.ZERO;
        var ae2Cpu = new appeng.me.cluster.implementations.CraftingCPUCluster(pos, pos);
        var ae2Tag = new net.minecraft.nbt.CompoundTag();
        ae2Cpu.craftingLogic.writeToNBT(com.moakiee.ae2lt.api.compat.ValueIO.output(ae2Tag, registries));
        var reloadedAe2Cpu = new appeng.me.cluster.implementations.CraftingCPUCluster(pos, pos);
        reloadedAe2Cpu.craftingLogic.readFromNBT(com.moakiee.ae2lt.api.compat.ValueIO.input(ae2Tag, registries));
        var advancedCluster = new net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPUCluster(pos, pos);
        var advancedCpu = new net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPU(
                advancedCluster, java.util.UUID.randomUUID(), 1024L);
        var advancedTag = new net.minecraft.nbt.CompoundTag();
        advancedCpu.craftingLogic.writeToNBT(com.moakiee.ae2lt.api.compat.ValueIO.output(advancedTag, registries));
        var reloadedAdvancedCpu = new net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPU(
                advancedCluster, java.util.UUID.randomUUID(), 1024L);
        reloadedAdvancedCpu.craftingLogic.readFromNBT(
                com.moakiee.ae2lt.api.compat.ValueIO.input(advancedTag, registries));
        CpuPendingPersistenceGameTests.roundTrip(helper, ae2Cpu,
                ae2Cpu.craftingLogic, reloadedAe2Cpu.craftingLogic,
                ae2Cpu.craftingLogic::readFromNBT, ae2Cpu.craftingLogic::writeToNBT,
                reloadedAe2Cpu.craftingLogic::readFromNBT, reloadedAe2Cpu.craftingLogic::writeToNBT);
        CpuPendingPersistenceGameTests.roundTrip(helper, advancedCpu,
                advancedCpu.craftingLogic, reloadedAdvancedCpu.craftingLogic,
                advancedCpu.craftingLogic::readFromNBT, advancedCpu.craftingLogic::writeToNBT,
                reloadedAdvancedCpu.craftingLogic::readFromNBT, reloadedAdvancedCpu.craftingLogic::writeToNBT);
        helper.succeed();
    }

    private static void assertMixinApplied(Class<?> cpuLogic) {
        for (String name : List.of("ae2lt$writeOverloadState", "ae2lt$readOverloadState")) {
            if (Arrays.stream(cpuLogic.getDeclaredMethods()).noneMatch(method -> method.getName().contains(name))) {
                throw new GameTestAssertException(Component.literal(
                        "Missing overload persistence mixin " + name + " on " + cpuLogic.getName()), 0);
            }
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

    private static Consumer<GameTestHelper> guarded(CheckedTest test) {
        return helper -> {
            try {
                test.run(helper);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    @FunctionalInterface
    private interface CheckedTest {
        void run(GameTestHelper helper) throws Exception;
    }
}
