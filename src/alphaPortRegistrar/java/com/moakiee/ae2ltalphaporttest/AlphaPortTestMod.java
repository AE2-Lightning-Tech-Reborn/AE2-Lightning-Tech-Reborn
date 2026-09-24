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
        add("upload_wired_full_target_retains_encoded_pattern", "upload", 150, com.moakiee.ae2lt.debug.TianshuPatternUploadGameTests::wiredFullTargetRetainsEncodedPattern);
        add("upload_wireless_full_target_retains_encoded_pattern", "upload", 150, com.moakiee.ae2lt.debug.TianshuPatternUploadGameTests::wirelessFullTargetRetainsEncodedPattern);
        add("pigmee_drops_legacy_wireless_binding", "fluid", 100, com.moakiee.ae2ltcpuselection.PigmeeCrystalCatalyzerGameTests::pigmeeDropsLegacyWirelessBindingOnLoad);
        add("building_recipe_loads_is_visible_and_survives_network_sync", "building", 100, helper -> { try { com.moakiee.ae2lt.debug.PigmeeBuildingGameTests.recipeLoadsIsVisibleAndSurvivesNetworkSync(helper); } catch (Exception e) { throw new RuntimeException(e); } });
        add("building_pickup_and_shift_craft_preserve_named_stacked_pigmee", "building", 100, helper -> { try { com.moakiee.ae2lt.debug.PigmeeBuildingGameTests.pickupAndShiftCraftPreserveNamedStackedPigmee(helper); } catch (Exception e) { throw new RuntimeException(e); } });
        add("building_shift_craft_stops_at_full_inventory_without_spending_ingredients", "building", 100, helper -> { try { com.moakiee.ae2lt.debug.PigmeeBuildingGameTests.shiftCraftStopsAtFullInventoryWithoutSpendingIngredients(helper); } catch (Exception e) { throw new RuntimeException(e); } });
        add("building_other_pigmee_recipes_still_consume_pigmee", "building", 100, helper -> { try { com.moakiee.ae2lt.debug.PigmeeBuildingGameTests.otherPigmeeRecipesStillConsumePigmee(helper); } catch (Exception e) { throw new RuntimeException(e); } });
        add("building_basic_block_has_simple_placement_and_self_drop", "building", 100, helper -> { try { com.moakiee.ae2lt.debug.PigmeeBuildingGameTests.basicBlockHasSimplePlacementAndSelfDrop(helper); } catch (Exception e) { throw new RuntimeException(e); } });
        add("building_all_thirty_two_panels_place_and_drop_without_block_entities", "building", 100, helper -> { try { com.moakiee.ae2lt.debug.PigmeeBuildingGameTests.allThirtyTwoPanelsPlaceAndDropWithoutBlockEntities(helper); } catch (Exception e) { throw new RuntimeException(e); } });
        add("building_all_inputs_offer_all_thirty_two_dye_free_stonecutting_outputs", "building", 100, helper -> { try { com.moakiee.ae2lt.debug.PigmeeBuildingGameTests.allInputsOfferAllThirtyTwoDyeFreeStonecuttingOutputs(helper); } catch (Exception e) { throw new RuntimeException(e); } });
        add("building_finished_panels_return_one_basic_block_in_both_crafting_grids", "building", 100, helper -> { try { com.moakiee.ae2lt.debug.PigmeeBuildingGameTests.finishedPanelsReturnOneBasicBlockInBothCraftingGrids(helper); } catch (Exception e) { throw new RuntimeException(e); } });
        add("building_conversion_recipes_do_not_accept_unrelated_materials", "building", 100, helper -> { try { com.moakiee.ae2lt.debug.PigmeeBuildingGameTests.conversionRecipesDoNotAcceptUnrelatedMaterials(helper); } catch (Exception e) { throw new RuntimeException(e); } });
        add("seed_reconcile_through_real_controller_and_me_network", "seed", 200, helper -> { try { com.moakiee.ae2lt.debug.TianshuSeedRefillGameTests.reconcileThroughRealControllerAndMeNetwork(helper); } catch (Exception e) { throw new RuntimeException(e); } });
        add("quantum_pattern_menu_survives_stale_bridge", "quantum", 100, helper -> { try { com.moakiee.ae2lt.debug.TianshuQuantumBridgeGameTests.patternMenuSurvivesStaleBridge(helper); } catch (Exception e) { throw new RuntimeException(e); } });
        add("quantum_crafting_menu_survives_stale_bridge", "quantum", 100, helper -> { try { com.moakiee.ae2lt.debug.TianshuQuantumBridgeGameTests.craftingMenuSurvivesStaleBridge(helper); } catch (Exception e) { throw new RuntimeException(e); } });
        add("quantum_status_and_connection_refresh_discard_stale_bridge", "quantum", 100, helper -> { try { com.moakiee.ae2lt.debug.TianshuQuantumBridgeGameTests.statusAndConnectionRefreshDiscardStaleBridge(helper); } catch (Exception e) { throw new RuntimeException(e); } });
        add("quantum_real_bridge_rebuild_and_wireless_crafting", "quantum", 240, helper -> { try { com.moakiee.ae2lt.debug.TianshuQuantumBridgeGameTests.realBridgeRebuildAndWirelessCrafting(helper); } catch (Exception e) { throw new RuntimeException(e); } });
        add("quantum_real_bridge_loss_falls_back_to_local_access_point", "quantum", 140, helper -> { try { com.moakiee.ae2lt.debug.TianshuQuantumBridgeGameTests.realBridgeLossFallsBackToLocalAccessPoint(helper); } catch (Exception e) { throw new RuntimeException(e); } });
        add("railgun_charged_percentages_are_ordinary_damage_and_never_double_apply", "railgun", 100, helper -> { try { com.moakiee.ae2lt.logic.railgun.RailgunCombatGameTests.chargedPercentagesAreOrdinaryDamageAndNeverDoubleApply(helper); } catch (Exception e) { throw new RuntimeException(e); } });
        add("railgun_modes_modules_and_settings_stay_independent", "railgun", 100, helper -> { try { com.moakiee.ae2lt.logic.railgun.RailgunCombatGameTests.modesModulesAndSettingsStayIndependent(helper); } catch (Exception e) { throw new RuntimeException(e); } });
        add("railgun_real_me_beam_pays_ehv_and_honors_continuous_damage", "railgun", 160, helper -> { try { com.moakiee.ae2lt.logic.railgun.RailgunCombatGameTests.realMeBeamPaysEhvAndHonorsContinuousDamage(helper); } catch (Exception e) { throw new RuntimeException(e); } });
        add("fluid_optional_recipe_data_and_codecs", "fluid", 100, helper -> { try { com.moakiee.ae2lt.blockentity.CrystalCatalyzerFluidGameTests.optionalRecipeDataAndCodecs(helper); } catch (Exception e) { throw new RuntimeException(e); } });
        add("fluid_same_catalyst_different_fluids_select_correct_output", "fluid", 100, helper -> { try { com.moakiee.ae2lt.blockentity.CrystalCatalyzerFluidGameTests.sameCatalystDifferentFluidsSelectCorrectOutput(helper); } catch (Exception e) { throw new RuntimeException(e); } });
        add("fluid_legacy_water1024", "fluid", 200, helper -> { try { com.moakiee.ae2lt.blockentity.CrystalCatalyzerFluidGameTests.legacyWater1024(helper); } catch (Exception e) { throw new RuntimeException(e); } });
        add("fluid_fluxite1024", "fluid", 200, helper -> { try { com.moakiee.ae2lt.blockentity.CrystalCatalyzerFluidGameTests.fluxite1024(helper); } catch (Exception e) { throw new RuntimeException(e); } });
        add("fluid_uranium1024", "fluid", 200, helper -> { try { com.moakiee.ae2lt.blockentity.CrystalCatalyzerFluidGameTests.uranium1024(helper); } catch (Exception e) { throw new RuntimeException(e); } });
        add("fluid_time_crystal1024", "fluid", 200, helper -> { try { com.moakiee.ae2lt.blockentity.CrystalCatalyzerFluidGameTests.timeCrystal1024(helper); } catch (Exception e) { throw new RuntimeException(e); } });
        add("fluid_saved_cycle_waits_for_fluid_and_output", "fluid", 300, helper -> { try { com.moakiee.ae2lt.blockentity.CrystalCatalyzerFluidGameTests.savedCycleWaitsForFluidAndOutput(helper); } catch (Exception e) { throw new RuntimeException(e); } });
        add("fluid_pigmee_rejects_special_fluids_and_legacy_fluxite", "fluid", 200, helper -> { try { com.moakiee.ae2lt.blockentity.CrystalCatalyzerFluidGameTests.pigmeeRejectsSpecialFluidsAndLegacyFluxite(helper); } catch (Exception e) { throw new RuntimeException(e); } });
        add("fluid_changed_recipe_fluid_does_not_strand_saved_cycle", "fluid", 100, helper -> { try { com.moakiee.ae2lt.blockentity.CrystalCatalyzerFluidGameTests.changedRecipeFluidDoesNotStrandSavedCycle(helper); } catch (Exception e) { throw new RuntimeException(e); } });
        add("fluid_pigmee_still_needs100_distinct_ticks", "fluid", 350, helper -> { try { com.moakiee.ae2lt.blockentity.CrystalCatalyzerFluidGameTests.pigmeeStillNeeds100DistinctTicks(helper); } catch (Exception e) { throw new RuntimeException(e); } });
        add("fluid_pigmee_legacy_progress_still_resumes", "fluid", 500, helper -> { try { com.moakiee.ae2lt.blockentity.CrystalCatalyzerFluidGameTests.pigmeeLegacyProgressStillResumes(helper); } catch (Exception e) { throw new RuntimeException(e); } });
        add("fluid_normal_still_requires_energy_and_lightning", "fluid", 400, helper -> { try { com.moakiee.ae2lt.blockentity.CrystalCatalyzerFluidGameTests.normalStillRequiresEnergyAndLightning(helper); } catch (Exception e) { throw new RuntimeException(e); } });
        add("appgen_origination_processor_loads_matches_and_syncs_only_with_app_gen", "appgen", 100, helper -> { try { com.moakiee.ae2lt.debug.AppGenProcessorGameTests.originationProcessorLoadsMatchesAndSyncsOnlyWithAppGen(helper); } catch (Exception e) { throw new RuntimeException(e); } });
        add("appgen_ember_synthesis_preserves_upstream_reaction", "appgen", 100, helper -> { try { com.moakiee.ae2lt.debug.AppGenProcessorGameTests.emberSynthesisPreservesUpstreamReaction(helper); } catch (Exception e) { throw new RuntimeException(e); } });
        add("appgen_ember_duplication_preserves_upstream_reaction", "appgen", 100, helper -> { try { com.moakiee.ae2lt.debug.AppGenProcessorGameTests.emberDuplicationPreservesUpstreamReaction(helper); } catch (Exception e) { throw new RuntimeException(e); } });
        add("appgen_ember_charging_preserves_upstream_reaction", "appgen", 100, helper -> { try { com.moakiee.ae2lt.debug.AppGenProcessorGameTests.emberChargingPreservesUpstreamReaction(helper); } catch (Exception e) { throw new RuntimeException(e); } });
        add("pigmeecapabilitiesandsharedrecipe", "fluid", 100, com.moakiee.ae2ltcpuselection.PigmeeCrystalCatalyzerGameTests::pigmeeCapabilitiesAndSharedRecipe);
        add("pigmeerepeatedticksdonotaccelerate", "fluid", 350, com.moakiee.ae2ltcpuselection.PigmeeCrystalCatalyzerGameTests::pigmeeRepeatedTicksDoNotAccelerate);
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
        // All groups use the same empty environment. A shared holder prevents 26.1's
        // runner from repeatedly replacing chunk tickets while earlier batches still load.
        var environment = event.registerEnvironment(id("regression"),
                new TestEnvironmentDefinition.AllOf(List.of()));
        for (var spec : TESTS) {
            String groupFilter = System.getProperty("ae2lt.alphaPortTestGroup", "");
            if (!groupFilter.isEmpty() && !groupFilter.equals(spec.group)) continue;
            var template = java.util.Set.of("workstation", "seed", "quantum", "upload").contains(spec.group) ? Identifier.fromNamespaceAndPath("ae2lt", "workstation_test") : Identifier.fromNamespaceAndPath(switch (spec.group) {
                case "building", "appgen" -> "ae2lt_pigmee_storage";
                case "fluid" -> "ae2lt_catalyzer";
                case "railgun" -> "ae2lt_railgun";
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
