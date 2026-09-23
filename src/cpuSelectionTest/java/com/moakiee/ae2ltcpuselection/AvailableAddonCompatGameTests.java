package com.moakiee.ae2ltcpuselection;

import java.util.List;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.logic.AdvancedAECompat;
import com.moakiee.ae2lt.logic.energy.AppFluxBridge;
import com.moakiee.ae2lt.blockentity.OverloadedPowerSupplyBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadProcessingFactoryBlockEntity;
import com.moakiee.ae2lt.recipe.compat.LegacyRecipeAccess;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;
import com.glodblock.github.extendedae.recipe.CrystalAssemblerRecipe;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.pedroksl.advanced_ae.common.patterns.AdvProcessingPattern;

/** Development-only integration checks against 26.1.2 addon jars. */
public final class AvailableAddonCompatGameTests {
    private AvailableAddonCompatGameTests() {}

    public static void advancedDirectionalPatternRoundTrip(GameTestHelper helper) {
        var input = AEItemKey.of(Items.IRON_INGOT);
        var output = AEItemKey.of(Items.GOLD_INGOT);
        var source = PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(input, 3)), List.of(new GenericStack(output, 2)));
        var encoded = AdvancedAECompat.encodeWithDirections(
                source, helper.getLevel(), List.of(Direction.NORTH.ordinal() + 1));
        require(encoded != null && !encoded.isEmpty(), "AdvancedAE pattern encoding failed");
        var details = PatternDetailsHelper.decodePattern(encoded, helper.getLevel());
        require(details instanceof AdvProcessingPattern, "AdvancedAE pattern did not decode");
        require(AdvancedAECompat.isDirectional(details), "directional input flag was lost");
        require(AdvancedAECompat.getDirectionForKey(details, input) == Direction.NORTH,
                "input direction was lost");
        var restored = AdvancedAECompat.restoreForEditing(details, 36, 12);
        require(restored != null && restored.directions()[0] == Direction.NORTH.ordinal() + 1,
                "terminal editing state lost input direction");
        require(restored.inputs().get(0).amount() == 3 && restored.outputs().get(0).amount() == 2,
                "terminal editing state changed quantities");
        helper.succeed();
    }

    public static void availableAddonRecipesLoad(GameTestHelper helper) {
        var recipes = LegacyRecipeAccess.manager(helper.getLevel());
        for (var path : List.of(
                "assembler/overload_processor", "crystal_catalyzer/dust/entro_block",
                "crystal_catalyzer/entro_block", "cutter/unoverloaded_circuit_board",
                "lightning_assembly/overloaded_power_supply",
                "overload_processing/aae_quantum_alloy",
                "overload_processing/aae_quantum_alloy_plate",
                "overload_processing/aae_quantum_infusion",
                "overload_processing/aae_quantum_processor",
                "overload_processing/aae_shattered_singularity",
                "overload_processing/appflux_charged_redstone",
                "overload_processing/appflux_energy_processor",
                "overload_processing/appflux_harden_insulating_resin",
                "overload_processing/appflux_redstone_crystal",
                "overload_processing/eae_concurrent_processor",
                "overload_processing/eae_entro_crystal",
                "overload_processing/eae_entro_ingot")) {
            var id = Identifier.fromNamespaceAndPath("ae2lt", path);
            require(LegacyRecipeAccess.byId(recipes, id).isPresent(), "Missing addon recipe " + id);
        }
        require(LegacyRecipeAccess.byId(recipes, Identifier.fromNamespaceAndPath("ae2lt", "silicon_block"))
                .isEmpty(), "fallback silicon recipe should be disabled when ExtendedAE is loaded");
        require(LegacyRecipeAccess.byId(recipes, Identifier.fromNamespaceAndPath("ae2lt", "silicon_decompress"))
                .isEmpty(), "fallback silicon decompression should be disabled when ExtendedAE is loaded");
        var assembler = LegacyRecipeAccess.byId(recipes,
                Identifier.fromNamespaceAndPath("ae2lt", "assembler/overload_processor"))
                .orElseThrow().value();
        require(assembler instanceof CrystalAssemblerRecipe,
                "ExtendedAE did not decode the overload processor as a crystal assembler recipe");
        var crystalRecipe = (CrystalAssemblerRecipe) assembler;
        require(crystalRecipe.energy == 2_000 && crystalRecipe.output.create().getCount() == 4
                        && crystalRecipe.output.create().is(ModItems.OVERLOAD_PROCESSOR.get()),
                "ExtendedAE assembler energy or four-item output changed during recipe migration");
        require(crystalRecipe.getInputs().size() == 3
                        && crystalRecipe.getInputs().stream().allMatch(input -> input.getAmount() == 4)
                        && crystalRecipe.getInputs().getFirst().getIngredient()
                                .test(ModItems.OVERLOAD_CIRCUIT_BOARD.toStack()),
                "ExtendedAE assembler input amounts or board ingredient did not decode");
        helper.succeed();
    }

    public static void appliedFluxCellBridge(GameTestHelper helper) {
        require(AppFluxBridge.isAvailable() && AppFluxBridge.canUseEnergyHandler(),
                "Applied Flux energy bridge did not initialize from the installed mod");
        var card = AppFluxBridge.getInductionCard();
        require(card != null && AppFluxBridge.isInductionCard(card),
                "Applied Flux induction card was not resolved from the live item registry");
        var cell = BuiltInRegistries.ITEM.get(Identifier.parse("appflux:fe_1k_cell"))
                .orElseThrow().value();
        var stack = new ItemStack(cell);
        require(AppFluxBridge.isFluxCell(stack), "Applied Flux cell type was not recognized");
        require(AppFluxBridge.getFluxCellCapacity(stack) > 0,
                "Applied Flux cell capacity could not be read through the AE2 storage bridge");
        var pos = helper.absolutePos(BlockPos.ZERO);
        var level = helper.getLevel();
        level.setBlockAndUpdate(pos, ModBlocks.OVERLOADED_POWER_SUPPLY.get().defaultBlockState());
        var supply = (OverloadedPowerSupplyBlockEntity) level.getBlockEntity(pos);
        require(supply != null, "Overloaded power supply did not create its block entity");
        supply.getCellInventory().setItemDirect(0, stack);
        require(supply.getBufferCapacity() > 0,
                "Overloaded power supply did not accept the installed Applied Flux cell");
        var storage = supply.getInstalledCellStorage();
        require(storage != null, "Installed Applied Flux cell has no ME storage view");
        long inserted = storage.insert(AppFluxBridge.FE_KEY, 1_000, Actionable.MODULATE, IActionSource.empty());
        require(inserted == 1_000, "Applied Flux cell did not accept FE through the power supply");
        require(storage.extract(AppFluxBridge.FE_KEY, 400, Actionable.MODULATE, IActionSource.empty()) == 400,
                "Applied Flux cell did not return FE through the power supply");
        supply.persistCellStorage();
        helper.succeed();
    }

    public static void appliedFluxWirelessPowerDelivery(GameTestHelper helper) {
        var powerPos = new BlockPos(2, 1, 2);
        var supplyPos = powerPos.south();
        var drivePos = powerPos.east();
        var machinePos = new BlockPos(6, 1, 3);
        helper.setBlock(powerPos, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(supplyPos, ModBlocks.OVERLOADED_POWER_SUPPLY.get());
        helper.setBlock(drivePos, AEBlocks.DRIVE.block());
        helper.setBlock(machinePos, ModBlocks.OVERLOAD_PROCESSING_FACTORY.get());

        var supply = helper.getBlockEntity(supplyPos, OverloadedPowerSupplyBlockEntity.class);
        var drive = helper.getBlockEntity(drivePos, DriveBlockEntity.class);
        var machine = helper.getBlockEntity(machinePos, OverloadProcessingFactoryBlockEntity.class);
        var cell = BuiltInRegistries.ITEM.get(Identifier.parse("appflux:fe_1k_cell"))
                .orElseThrow().value();
        require(drive.getInternalInventory().insertItem(0, new ItemStack(cell), false).isEmpty(),
                "AE drive rejected the real Applied Flux FE cell");
        require(supply.addOrUpdateConnection(helper.getLevel().dimension(),
                        helper.absolutePos(machinePos), Direction.UP),
                "Power supply rejected the wireless machine connection");

        helper.runAfterDelay(20, () -> {
            var grid = supply.getMainNode().getGrid();
            require(supply.getMainNode().isActive() && grid != null,
                    "Power supply did not join the powered AE network");
            require(drive.getCellInventory(0) != null,
                    "AE drive did not mount the installed Applied Flux cell");
            require(drive.getMainNode().getGrid() == grid,
                    "AE drive and power supply are not connected to the same grid");
            var storage = grid.getStorageService().getInventory();
            long directCapacity = drive.getCellInventory(0).insert(AppFluxBridge.FE_KEY,
                    1_000, Actionable.SIMULATE, IActionSource.empty());
            long networkCapacity = storage.insert(AppFluxBridge.FE_KEY,
                    1_000, Actionable.SIMULATE, IActionSource.empty());
            require(networkCapacity > 0,
                    "Applied Flux FE cell is mounted but grid cannot insert FE: direct="
                            + directCapacity);
            require(storage.insert(AppFluxBridge.FE_KEY, 1_000,
                            Actionable.MODULATE, IActionSource.empty()) == 1_000,
                    "AE grid refused FE insertion after successful simulation");
        });
        helper.runAfterDelay(80, () -> {
            long delivered = machine.getEnergyStorage().getEnergyStored();
            require(delivered > 0,
                    "Power supply did not deliver stored FE to its wireless machine");
            helper.succeed();
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(Component.literal(message), 0);
    }
}
