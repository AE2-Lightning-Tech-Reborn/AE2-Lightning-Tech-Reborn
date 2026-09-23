package com.moakiee.ae2ltwirelessporttest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;

import com.moakiee.ae2lt.blockentity.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Development-only registration for wireless interface and overload I/O GameTests. */
@Mod(WirelessPortTestMod.MODID)
public final class WirelessPortTestMod {
    public static final String MODID = "ae2lt_wireless_port_test";
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(BuiltInRegistries.TEST_FUNCTION, MODID);
    private static final List<TestSpec> TESTS = new ArrayList<>();

    static {
        add("overloaded_interface_import_recovery_wireless_warm_recovery_keeps_production", "wireless_io_07_import_recovery", 470, OverloadedInterfaceImportRecoveryGameTests::wirelessWarmRecoveryKeepsProduction);
        add("overloaded_interface_import_recovery_local_warm_recovery_keeps_production", "wireless_io_07_import_recovery", 470, OverloadedInterfaceImportRecoveryGameTests::localWarmRecoveryKeepsProduction);
        add("overloaded_interface_io_fast_wireless_exact_import_respects_export_exclusion", "wireless_io_02_exact_filter", 180, OverloadedInterfaceIoGameTests::fastWirelessExactImportRespectsExportExclusion);
        add("overloaded_interface_io_fast_local_exact_import_respects_export_exclusion", "wireless_io_02_exact_filter", 180, OverloadedInterfaceIoGameTests::fastLocalExactImportRespectsExportExclusion);
        add("overloaded_interface_io_fast_wireless_excluded_import_stops_and_wakes", "wireless_io_02_exact_plan", 150, OverloadedInterfaceIoGameTests::fastWirelessExcludedImportStopsAndWakes);
        add("overloaded_interface_io_fast_local_excluded_import_stops_and_wakes", "wireless_io_02_exact_plan", 150, OverloadedInterfaceIoGameTests::fastLocalExcludedImportStopsAndWakes);
        add("overloaded_interface_io_exact_import_plan_keeps_buffer_flush_and_export", "wireless_io_02_exact_plan", 180, OverloadedInterfaceIoGameTests::exactImportPlanKeepsBufferFlushAndExport);
        add("overloaded_interface_io_exact_import_plan_does_not_prune_fuzzy_or_inverted_filters", "wireless_io_02_exact_plan", 140, OverloadedInterfaceIoGameTests::exactImportPlanDoesNotPruneFuzzyOrInvertedFilters);
        add("overloaded_interface_io_fast_import_buffer_survives_save_and_reload", "wireless_io_02_transitions", 260, OverloadedInterfaceIoGameTests::fastImportBufferSurvivesSaveAndReload);
        add("overloaded_interface_io_fast_wireless_import_resumes_after_storage_recovery", "wireless_io_06_recovery", 240, OverloadedInterfaceIoGameTests::fastWirelessImportResumesAfterStorageRecovery);
        add("overloaded_interface_io_fast_local_import_resumes_after_storage_recovery", "wireless_io_06_recovery", 240, OverloadedInterfaceIoGameTests::fastLocalImportResumesAfterStorageRecovery);
        add("overloaded_interface_io_fast_wireless_import_restarts_after_idle", "wireless_io_06_recovery", 240, OverloadedInterfaceIoGameTests::fastWirelessImportRestartsAfterIdle);
        add("overloaded_interface_io_fast_local_import_restarts_after_idle", "wireless_io_06_recovery", 240, OverloadedInterfaceIoGameTests::fastLocalImportRestartsAfterIdle);
        add("overloaded_interface_io_wireless_export_fills_once_and_batches_refills", "wireless_io_07_refill", 300, OverloadedInterfaceIoGameTests::wirelessExportFillsOnceAndBatchesRefills);
        add("overloaded_interface_io_local_export_fills_once_and_batches_refills", "wireless_io_07_refill", 300, OverloadedInterfaceIoGameTests::localExportFillsOnceAndBatchesRefills);
        add("overloaded_interface_io_missing_export_key_does_not_block_other_keys", "wireless_io_07_refill", 140, OverloadedInterfaceIoGameTests::missingExportKeyDoesNotBlockOtherKeys);
        add("overloaded_interface_mode_normal_wireless_import_batches_without_blocking", "wireless_io_08_normal_import", 1080, OverloadedInterfaceModeGameTests::normalWirelessImportBatchesWithoutBlocking);
        add("overloaded_interface_mode_normal_wireless_export_batches_without_starving", "wireless_io_09_normal_export", 1080, OverloadedInterfaceModeGameTests::normalWirelessExportBatchesWithoutStarving);
        add("overloaded_interface_mode_normal_local_import_batches_without_blocking", "wireless_io_10_normal_local_import", 1080, OverloadedInterfaceModeGameTests::normalLocalImportBatchesWithoutBlocking);
        add("overloaded_interface_mode_normal_local_export_batches_without_starving", "wireless_io_11_normal_local_export", 1080, OverloadedInterfaceModeGameTests::normalLocalExportBatchesWithoutStarving);
        add("wireless_interface_export_fast_wireless_export_benchmark", "wireless_io_01_export_benchmark", 1500, WirelessInterfaceExportGameTests::fastWirelessExportBenchmark);
        add("wireless_interface_fast_wireless_empty_export_configuration_changes", "wireless_io_02_export_plan", 160, WirelessInterfaceGameTests::fastWirelessEmptyExportConfigurationChanges);
        add("wireless_interface_fast_local_empty_export_configuration_changes", "wireless_io_02_export_plan", 160, WirelessInterfaceGameTests::fastLocalEmptyExportConfigurationChanges);
        add("wireless_interface_fast_wireless_export_type_changes_remain_responsive", "wireless_io_02_export_plan", 170, WirelessInterfaceGameTests::fastWirelessExportTypeChangesRemainResponsive);
        add("wireless_interface_fast_local_export_type_changes_remain_responsive", "wireless_io_02_export_plan", 170, WirelessInterfaceGameTests::fastLocalExportTypeChangesRemainResponsive);
        add("wireless_interface_empty_export_keeps_owned_buffer_flush", "wireless_io_02_export_plan", 140, WirelessInterfaceGameTests::emptyExportKeepsOwnedBufferFlush);
        add("wireless_interface_fast_wireless_exact_import_respects_export_exclusion", "wireless_io_02_exact_filter", 180, WirelessInterfaceGameTests::fastWirelessExactImportRespectsExportExclusion);
        add("wireless_interface_fast_local_exact_import_respects_export_exclusion", "wireless_io_02_exact_filter", 180, WirelessInterfaceGameTests::fastLocalExactImportRespectsExportExclusion);
        add("wireless_interface_fast_wireless_excluded_import_stops_and_wakes", "wireless_io_02_exact_plan", 150, WirelessInterfaceGameTests::fastWirelessExcludedImportStopsAndWakes);
        add("wireless_interface_fast_local_excluded_import_stops_and_wakes", "wireless_io_02_exact_plan", 150, WirelessInterfaceGameTests::fastLocalExcludedImportStopsAndWakes);
        add("wireless_interface_exact_import_plan_keeps_buffer_flush_and_export", "wireless_io_02_exact_plan", 180, WirelessInterfaceGameTests::exactImportPlanKeepsBufferFlushAndExport);
        add("wireless_interface_exact_import_plan_does_not_prune_fuzzy_or_inverted_filters", "wireless_io_02_exact_plan", 140, WirelessInterfaceGameTests::exactImportPlanDoesNotPruneFuzzyOrInvertedFilters);
        add("wireless_interface_fast_import1024_continuous", "wireless_io_01_continuous", 1500, WirelessInterfaceGameTests::fastImport1024Continuous);
        add("wireless_interface_fast_import_high_cardinality_reject_recovery", "wireless_io_03_high_cardinality_reject", 1500, WirelessInterfaceGameTests::fastImportHighCardinalityRejectRecovery);
        add("wireless_interface_fast_import_equal_load_recovery", "wireless_io_04_equal_load", 1500, WirelessInterfaceGameTests::fastImportEqualLoadRecovery);
        add("wireless_interface_fast_import_equal_load_sustained", "wireless_io_05_equal_load", 1500, WirelessInterfaceGameTests::fastImportEqualLoadSustained);
        add("wireless_interface_fast_import_buffer_survives_save_and_reload", "wireless_io_02_transitions", 260, WirelessInterfaceGameTests::fastImportBufferSurvivesSaveAndReload);
        add("wireless_interface_fast_import_out_of_order_target_attribution", "wireless_io_02_transitions", 260, WirelessInterfaceGameTests::fastImportOutOfOrderTargetAttribution);
        add("wireless_interface_fast_import_cold_output_all_phases", "wireless_io_02_transitions", 180, WirelessInterfaceGameTests::fastImportColdOutputAllPhases);
        add("wireless_interface_fast_import_continuous_output_all_phases", "wireless_io_02_transitions", 180, WirelessInterfaceGameTests::fastImportContinuousOutputAllPhases);
        add("wireless_interface_fast_import256_transitions", "wireless_io_02_transitions", 470, WirelessInterfaceGameTests::fastImport256Transitions);
        add("wireless_interface_fast_wireless_import_resumes_after_storage_recovery", "wireless_io_06_recovery", 240, WirelessInterfaceGameTests::fastWirelessImportResumesAfterStorageRecovery);
        add("wireless_interface_fast_local_import_resumes_after_storage_recovery", "wireless_io_06_recovery", 240, WirelessInterfaceGameTests::fastLocalImportResumesAfterStorageRecovery);
    }

    private static void add(String name, String batch, int ticks, Consumer<GameTestHelper> action) {
        var function = FUNCTIONS.register(name, () -> action);
        TESTS.add(new TestSpec(name, batch, ticks, function));
    }

    public WirelessPortTestMod(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener(this::registerTests);
    }

    private void registerTests(RegisterGameTestsEvent event) {
        var batches = new LinkedHashMap<String, Holder<TestEnvironmentDefinition<?>>>();
        var template = Identifier.fromNamespaceAndPath("ae2lt", "wireless_io_empty");
        for (var spec : TESTS) {
            var environment = batches.computeIfAbsent(spec.batch, batch ->
                    event.registerEnvironment(id(batch), new TestEnvironmentDefinition.AllOf(List.of())));
            event.registerTest(id(spec.name), new FunctionGameTestInstance(
                    spec.function.getKey(), new TestData<>(environment, template, spec.ticks, 0, true)));
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

    private record TestSpec(String name, String batch, int ticks,
            DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> function) {}
}
