package com.moakiee.ae2ltalphaporttest;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.*;

/** 26.1 registration for the unchanged main regression scenarios. */
@Mod(AlphaPortTestMod.MODID)
public final class AlphaPortTestMod {
    public static final String MODID = "ae2lt_alpha_port_test";
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(BuiltInRegistries.TEST_FUNCTION, MODID);
    private static final List<Spec> TESTS = new ArrayList<>();
    static {
        add("jei_wireless_supply", "jei", 200, com.moakiee.ae2lt.debug.JeiWirelessSupplyGameTests::supplyAndNativeTransfer);
        add("workstation_workstations", "workstation", 100, com.moakiee.ae2lt.debug.TianshuCraftingGameTests::workstations);
        add("workstation_cell_workbench", "workstation", 100, com.moakiee.ae2lt.debug.TianshuCraftingGameTests::cellWorkbench);
        add("workstation_native_anvil_callbacks", "workstation", 100, com.moakiee.ae2lt.debug.TianshuCraftingGameTests::nativeAnvilCallbacks);
        add("workstation_wireless", "workstation", 100, com.moakiee.ae2lt.debug.TianshuCraftingGameTests::wireless);
        add("workstation_shared_workstations_invalidate_other_viewers", "workstation", 100, com.moakiee.ae2lt.debug.TianshuWorkCraftingGameTests::sharedWorkstationsInvalidateOtherViewers);
        add("workstation_wireless_workstations_survive_item_save_and_reload", "workstation", 100, com.moakiee.ae2lt.debug.TianshuWorkCraftingGameTests::wirelessWorkstationsSurviveItemSaveAndReload);
        add("workstation_wired_part_saves_and_drops_only_real_inputs", "workstation", 100, com.moakiee.ae2lt.debug.TianshuWorkCraftingGameTests::wiredPartSavesAndDropsOnlyRealInputs);
        add("workstation_manual_inputs_persist_across_close", "workstation", 100, com.moakiee.ae2lt.debug.TianshuWorkCraftingGameTests::manualInputsPersistAcrossClose);
        add("workstation_ae2_click_parity", "workstation", 100, com.moakiee.ae2lt.debug.TianshuWorkCraftingGameTests::ae2ClickParity);
        add("workstation_work_input_and_result_boundaries", "workstation", 100, com.moakiee.ae2lt.debug.TianshuWorkCraftingGameTests::workInputAndResultBoundaries);
        add("workstation_smithing_and_anvil_batch_callbacks", "workstation", 100, com.moakiee.ae2lt.debug.TianshuWorkCraftingGameTests::smithingAndAnvilBatchCallbacks);
        add("transaction_network_extraction", "blockentity", 160, com.moakiee.ae2lt.blockentity.OverloadedInterfacePassiveInputGameTests::transactionalExtractionUsesLiveNetworkAcrossConfiguredSlots);
        add("native_recipe_fill_across_faces", "debug", 100, com.moakiee.ae2lt.debug.PigmeeAdjacentStorageGameTests::nativeRecipeFillAcrossFaces);
        if (Boolean.getBoolean("ae2lt.pigmeeCuttingBoardTest")) {
            add("cutting_board_and_native_recipe_fill", "debug", 100, com.moakiee.ae2lt.debug.PigmeeAdjacentStorageGameTests::cuttingBoardAndNativeRecipeFill);
        }
        add("transaction_rollback_nested_commit", "blockentity", 160, com.moakiee.ae2lt.blockentity.OverloadedInterfacePassiveInputGameTests::transactionalRollbackAndNestedCommitKeepOwnership);
        add("transaction_shared_capacity_power", "blockentity", 160, com.moakiee.ae2lt.blockentity.OverloadedInterfacePassiveInputGameTests::transactionsReserveSharedCapacityAndPower);
        add("normal_passive_input_flushes_with_automatic_io_disabled", "blockentity", 160, com.moakiee.ae2lt.blockentity.OverloadedInterfacePassiveInputGameTests::normalPassiveInputFlushesWithAutomaticIoDisabled);
        add("wireless_passive_input_flushes_with_no_remote_connections", "blockentity", 160, com.moakiee.ae2lt.blockentity.OverloadedInterfacePassiveInputGameTests::wirelessPassiveInputFlushesWithNoRemoteConnections);
        add("rejecting_network_retains_input_through_save_reload_and_recovery", "blockentity", 180, com.moakiee.ae2lt.blockentity.OverloadedInterfacePassiveInputGameTests::rejectingNetworkRetainsInputThroughSaveReloadAndRecovery);
        add("finite_buffer_backpressure_filter_and_removal_conserve_items", "blockentity", 160, com.moakiee.ae2lt.blockentity.OverloadedInterfacePassiveInputGameTests::finiteBufferBackpressureFilterAndRemovalConserveItems);
        add("partially_full_cell_retains_remainder_across_reload_and_capacity_recovery", "blockentity", 180, com.moakiee.ae2lt.blockentity.OverloadedInterfacePassiveInputGameTests::partiallyFullCellRetainsRemainderAcrossReloadAndCapacityRecovery);
        add("energy_charged_once_and_inactive_grid_rejects_input", "blockentity", 160, com.moakiee.ae2lt.blockentity.OverloadedInterfacePassiveInputGameTests::energyChargedOnceAndInactiveGridRejectsInput);
        add("fluids_persist_and_network_reentry_is_rejected_while_gui_remains_immediate", "blockentity", 160, com.moakiee.ae2lt.blockentity.OverloadedInterfacePassiveInputGameTests::fluidsPersistAndNetworkReentryIsRejectedWhileGuiRemainsImmediate);
        add("recipe_mining_and_loot_are_registered", "block", 100, com.moakiee.ae2lt.block.OverloadAlloyAnvilGameTests::recipeMiningAndLootAreRegistered);
        add("native_menu_honors_callbacks_and_xp_without_alloy_wear", "block", 100, com.moakiee.ae2lt.block.OverloadAlloyAnvilGameTests::nativeMenuHonorsCallbacksAndXpWithoutAlloyWear);
        add("unsupported_anvils_fall_and_keep_all_four_facings", "block", 100, com.moakiee.ae2lt.block.OverloadAlloyAnvilGameTests::unsupportedAnvilsFallAndKeepAllFourFacings);
        add("support_prevents_falling_until_removed", "block", 100, com.moakiee.ae2lt.block.OverloadAlloyAnvilGameTests::supportPreventsFallingUntilRemoved);
        add("heavy_impact_hurts_entities_without_destroying_alloy_and_survives_reload", "block", 100, com.moakiee.ae2lt.block.OverloadAlloyAnvilGameTests::heavyImpactHurtsEntitiesWithoutDestroyingAlloyAndSurvivesReload);
        add("torch_landing_drops_exactly_one_alloy_anvil", "block", 100, com.moakiee.ae2lt.block.OverloadAlloyAnvilGameTests::torchLandingDropsExactlyOneAlloyAnvil);
        add("ordinary_anvils_still_wear_on_impact", "block", 100, com.moakiee.ae2lt.block.OverloadAlloyAnvilGameTests::ordinaryAnvilsStillWearOnImpact);
        add("existing_storage_and_power_contract", "debug", 100, com.moakiee.ae2lt.debug.PigmeeAdjacentStorageGameTests::existingStorageAndPowerContract);
        add("existing_me_exclusion_contract", "debug", 100, com.moakiee.ae2lt.debug.PigmeeAdjacentStorageGameTests::existingMeExclusionContract);
        add("existing_crafting_and_persistence_contract", "debug", 100, com.moakiee.ae2lt.debug.PigmeeAdjacentStorageGameTests::existingCraftingAndPersistenceContract);
        add("all_six_sides_and_split_item_transfers", "debug", 100, com.moakiee.ae2lt.debug.PigmeeAdjacentStorageGameTests::allSixSidesAndSplitItemTransfers);
        add("live_removal_and_replacement", "debug", 100, com.moakiee.ae2lt.debug.PigmeeAdjacentStorageGameTests::liveRemovalAndReplacement);
        add("shared_handlers_count_and_simulate_once", "debug", 100, com.moakiee.ae2lt.debug.PigmeeAdjacentStorageGameTests::sharedHandlersCountAndSimulateOnce);
        add("silent_multiblock_handler_replacement_and_removal", "debug", 100, com.moakiee.ae2lt.debug.PigmeeAdjacentStorageGameTests::silentMultiblockHandlerReplacementAndRemoval);
        add("removed_host_and_removed_neighbour_reject_access", "debug", 100, com.moakiee.ae2lt.debug.PigmeeAdjacentStorageGameTests::removedHostAndRemovedNeighbourRejectAccess);
        add("independent_fluid_tanks_combine", "debug", 100, com.moakiee.ae2lt.debug.PigmeeAdjacentStorageGameTests::independentFluidTanksCombine);
    }
    private static void add(String name, String group, int ticks, Consumer<GameTestHelper> action) {
        TESTS.add(new Spec(name, group, ticks, FUNCTIONS.register(name, () -> helper -> {
            try { action.accept(helper); }
            catch (AssertionError error) { helper.fail(net.minecraft.network.chat.Component.literal(error.toString())); }
        })));
    }
    public AlphaPortTestMod(IEventBus bus) {
        FUNCTIONS.register(bus);
        bus.addListener(this::registerTests);
        bus.addListener(com.moakiee.ae2lt.debug.PigmeeAdjacentStorageGameTests::registerCapabilities);
    }
    private void registerTests(RegisterGameTestsEvent event) {
        Map<String, Holder<TestEnvironmentDefinition<?>>> groups = new HashMap<>();
        for (var spec : TESTS) {
            String groupFilter = System.getProperty("ae2lt.alphaPortTestGroup", "");
            if (!groupFilter.isEmpty() && !groupFilter.equals(spec.group)) continue;
            var environment = groups.computeIfAbsent(spec.group, group -> event.registerEnvironment(id(group), new TestEnvironmentDefinition.AllOf(List.of())));
            var template = spec.group.equals("workstation") ? Identifier.fromNamespaceAndPath("ae2lt", "workstation_test") : Identifier.fromNamespaceAndPath(switch (spec.group) {
                case "jei" -> "ae2lt_jei_supply";
                case "block" -> "ae2lt_anvil";
                case "blockentity" -> "ae2lt_interface_input";
                default -> "ae2lt_pigmee_storage";
            }, "empty");
            event.registerTest(id(spec.name), new FunctionGameTestInstance(spec.function.getKey(),
                    new TestData<>(environment, template, spec.ticks, 0, true)));
        }
    }
    private static Identifier id(String name) { return Identifier.fromNamespaceAndPath(MODID, name); }
    private record Spec(String name, String group, int ticks, DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> function) {}
}
