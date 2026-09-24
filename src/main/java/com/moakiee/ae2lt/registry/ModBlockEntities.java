package com.moakiee.ae2lt.registry;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.AtmosphericIonizerBlockEntity;
import com.moakiee.ae2lt.blockentity.CrystalCatalyzerBlockEntity;
import com.moakiee.ae2lt.blockentity.NetworkedCrystalCatalyzerBlockEntity;
import com.moakiee.ae2lt.blockentity.FirmamentConversionCoreBlockEntity;
import com.moakiee.ae2lt.blockentity.FumoBlockEntity;
import com.moakiee.ae2lt.blockentity.GhostOutputBlockEntity;
import com.moakiee.ae2lt.blockentity.ExtendedOverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.blockentity.LightningAssemblyChamberBlockEntity;
import com.moakiee.ae2lt.blockentity.LightningCollectorBlockEntity;
import com.moakiee.ae2lt.blockentity.LightningSimulationChamberBlockEntity;
import com.moakiee.ae2lt.blockentity.MatrixControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.MatrixPatternStorageBlockEntity;
import com.moakiee.ae2lt.blockentity.MatrixPortBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadDeviceWorkbenchBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadProcessingFactoryBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPowerSupplyBlockEntity;
import com.moakiee.ae2lt.blockentity.PigmeeMentalmathUnitBlockEntity;
import com.moakiee.ae2lt.blockentity.PigmeeMolecularAssemblerBlockEntity;
import com.moakiee.ae2lt.blockentity.PigmeePatternProviderBlockEntity;
import com.moakiee.ae2lt.blockentity.PigmeeSynthesisStationBlockEntity;
import com.moakiee.ae2lt.blockentity.TeslaCoilBlockEntity;
import com.moakiee.ae2lt.blockentity.TianshuSupercomputerControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.TianshuSupercomputerPortBlockEntity;
import com.moakiee.ae2lt.blockentity.TianshuPatternStorageBlockEntity;
import com.moakiee.ae2lt.blockentity.TianshuSeedStorageBlockEntity;
import com.moakiee.ae2lt.blockentity.AdvancedWirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.WirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.WirelessReceiverBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AE2LightningTech.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LightningCollectorBlockEntity>>
            LIGHTNING_COLLECTOR = BLOCK_ENTITY_TYPES.register(
                    "lightning_collector",
                    () -> com.moakiee.ae2lt.recipe.compat.LegacyBlockEntityTypes.of(
                            LightningCollectorBlockEntity::new,
                            ModBlocks.LIGHTNING_COLLECTOR.get())
                            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FirmamentConversionCoreBlockEntity>>
            FIRMAMENT_CONVERSION_CORE = BLOCK_ENTITY_TYPES.register(
                    "firmament_conversion_core",
                    () -> com.moakiee.ae2lt.recipe.compat.LegacyBlockEntityTypes.of(
                            FirmamentConversionCoreBlockEntity::new,
                            ModBlocks.FIRMAMENT_CONVERSION_CORE.get())
                            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OverloadedControllerBlockEntity>>
            OVERLOADED_CONTROLLER = BLOCK_ENTITY_TYPES.register(
                    "overloaded_controller",
                    () -> com.moakiee.ae2lt.recipe.compat.LegacyBlockEntityTypes.of(
                            OverloadedControllerBlockEntity::new,
                            ModBlocks.OVERLOADED_CONTROLLER.get())
                            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LightningSimulationChamberBlockEntity>>
            LIGHTNING_SIMULATION_CHAMBER = BLOCK_ENTITY_TYPES.register(
                    "lightning_simulation_room",
                    () -> com.moakiee.ae2lt.recipe.compat.LegacyBlockEntityTypes.of(
                            LightningSimulationChamberBlockEntity::new,
                            ModBlocks.LIGHTNING_SIMULATION_CHAMBER.get())
                            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LightningAssemblyChamberBlockEntity>>
            LIGHTNING_ASSEMBLY_CHAMBER = BLOCK_ENTITY_TYPES.register(
                    "lightning_assembly_chamber",
                    () -> com.moakiee.ae2lt.recipe.compat.LegacyBlockEntityTypes.of(
                            LightningAssemblyChamberBlockEntity::new,
                            ModBlocks.LIGHTNING_ASSEMBLY_CHAMBER.get())
                            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OverloadProcessingFactoryBlockEntity>>
            OVERLOAD_PROCESSING_FACTORY = BLOCK_ENTITY_TYPES.register(
                    "overload_processing_factory",
                    () -> com.moakiee.ae2lt.recipe.compat.LegacyBlockEntityTypes.of(
                            OverloadProcessingFactoryBlockEntity::new,
                            ModBlocks.OVERLOAD_PROCESSING_FACTORY.get())
                            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TeslaCoilBlockEntity>>
            TESLA_COIL = BLOCK_ENTITY_TYPES.register(
                    "tesla_coil",
                    () -> com.moakiee.ae2lt.recipe.compat.LegacyBlockEntityTypes.of(
                            TeslaCoilBlockEntity::new,
                            ModBlocks.TESLA_COIL.get())
                            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AtmosphericIonizerBlockEntity>>
            ATMOSPHERIC_IONIZER = BLOCK_ENTITY_TYPES.register(
                    "atmospheric_ionizer",
                    () -> com.moakiee.ae2lt.recipe.compat.LegacyBlockEntityTypes.of(
                            AtmosphericIonizerBlockEntity::new,
                            ModBlocks.ATMOSPHERIC_IONIZER.get())
                            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CrystalCatalyzerBlockEntity>>
            CRYSTAL_CATALYZER = BLOCK_ENTITY_TYPES.register(
                    "crystal_catalyzer",
                    () -> com.moakiee.ae2lt.recipe.compat.LegacyBlockEntityTypes.of(
                            NetworkedCrystalCatalyzerBlockEntity::new,
                            ModBlocks.CRYSTAL_CATALYZER.get())
                            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CrystalCatalyzerBlockEntity>>
            PIGMEE_CRYSTAL_CATALYZER = BLOCK_ENTITY_TYPES.register(
                    "pigmee_crystal_catalyzer",
                    () -> com.moakiee.ae2lt.recipe.compat.LegacyBlockEntityTypes.of(
                            CrystalCatalyzerBlockEntity::new,
                            ModBlocks.PIGMEE_CRYSTAL_CATALYZER.get())
                            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OverloadedPatternProviderBlockEntity>>
            OVERLOADED_PATTERN_PROVIDER = BLOCK_ENTITY_TYPES.register(
                    "overloaded_pattern_provider",
                    () -> com.moakiee.ae2lt.recipe.compat.LegacyBlockEntityTypes.of(
                            OverloadedPatternProviderBlockEntity::new,
                            ModBlocks.OVERLOADED_PATTERN_PROVIDER.get())
                            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ExtendedOverloadedPatternProviderBlockEntity>>
            EXTENDED_OVERLOADED_PATTERN_PROVIDER = BLOCK_ENTITY_TYPES.register(
                    "extended_overloaded_pattern_provider",
                    () -> com.moakiee.ae2lt.recipe.compat.LegacyBlockEntityTypes.of(
                            ExtendedOverloadedPatternProviderBlockEntity::new,
                            ModBlocks.EXTENDED_OVERLOADED_PATTERN_PROVIDER.get())
                            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OverloadedInterfaceBlockEntity>>
            OVERLOADED_INTERFACE = BLOCK_ENTITY_TYPES.register(
                    "overloaded_interface",
                    () -> com.moakiee.ae2lt.recipe.compat.LegacyBlockEntityTypes.of(
                            OverloadedInterfaceBlockEntity::new,
                            ModBlocks.OVERLOADED_INTERFACE.get())
                            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OverloadedPowerSupplyBlockEntity>>
            OVERLOADED_POWER_SUPPLY = ModBlocks.hasOverloadedPowerSupply()
                    ? BLOCK_ENTITY_TYPES.register(
                            "overloaded_power_supply",
                            () -> com.moakiee.ae2lt.recipe.compat.LegacyBlockEntityTypes.of(
                                    OverloadedPowerSupplyBlockEntity::new,
                                    ModBlocks.OVERLOADED_POWER_SUPPLY.get())
                                    )
                    : null;

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WirelessReceiverBlockEntity>>
            WIRELESS_RECEIVER = BLOCK_ENTITY_TYPES.register(
                    "wireless_receiver",
                    () -> com.moakiee.ae2lt.recipe.compat.LegacyBlockEntityTypes.of(
                            WirelessReceiverBlockEntity::new,
                            ModBlocks.WIRELESS_RECEIVER.get())
                            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WirelessOverloadedControllerBlockEntity>>
            WIRELESS_OVERLOADED_CONTROLLER = BLOCK_ENTITY_TYPES.register(
                    "wireless_overloaded_controller",
                    () -> com.moakiee.ae2lt.recipe.compat.LegacyBlockEntityTypes.of(
                            WirelessOverloadedControllerBlockEntity::new,
                            ModBlocks.WIRELESS_OVERLOADED_CONTROLLER.get())
                            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AdvancedWirelessOverloadedControllerBlockEntity>>
            ADVANCED_WIRELESS_OVERLOADED_CONTROLLER = BLOCK_ENTITY_TYPES.register(
                    "advanced_wireless_overloaded_controller",
                    () -> com.moakiee.ae2lt.recipe.compat.LegacyBlockEntityTypes.of(
                            AdvancedWirelessOverloadedControllerBlockEntity::new,
                            ModBlocks.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER.get())
                            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PigmeeMentalmathUnitBlockEntity>>
            PIGMEE_MENTALMATH_UNIT = BLOCK_ENTITY_TYPES.register(
                    "pigmee_mentalmath_unit",
                    () -> com.moakiee.ae2lt.recipe.compat.LegacyBlockEntityTypes.of(
                            PigmeeMentalmathUnitBlockEntity::new,
                            ModBlocks.PIGMEE_MENTALMATH_UNIT.get())
                            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PigmeePatternProviderBlockEntity>>
            PIGMEE_PATTERN_PROVIDER = BLOCK_ENTITY_TYPES.register(
                    "pigmee_pattern_provider",
                    () -> com.moakiee.ae2lt.recipe.compat.LegacyBlockEntityTypes.of(
                            PigmeePatternProviderBlockEntity::new,
                            ModBlocks.PIGMEE_PATTERN_PROVIDER.get())
                            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PigmeeMolecularAssemblerBlockEntity>>
            PIGMEE_MOLECULAR_ASSEMBLER = BLOCK_ENTITY_TYPES.register(
                    "pigmee_molecular_assembler",
                    () -> com.moakiee.ae2lt.recipe.compat.LegacyBlockEntityTypes.of(
                            PigmeeMolecularAssemblerBlockEntity::new,
                            ModBlocks.PIGMEE_MOLECULAR_ASSEMBLER.get())
                            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PigmeeSynthesisStationBlockEntity>>
            PIGMEE_SYNTHESIS_STATION = BLOCK_ENTITY_TYPES.register(
                    "pigmee_synthesis_station",
                    () -> com.moakiee.ae2lt.recipe.compat.LegacyBlockEntityTypes.of(
                            PigmeeSynthesisStationBlockEntity::new,
                            ModBlocks.PIGMEE_SYNTHESIS_STATION.get())
                            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TianshuSupercomputerControllerBlockEntity>>
            TIANSHU_SUPERCOMPUTER_CONTROLLER = BLOCK_ENTITY_TYPES.register(
                    "tianshu_supercomputer_controller",
                    () -> com.moakiee.ae2lt.recipe.compat.LegacyBlockEntityTypes.of(
                            TianshuSupercomputerControllerBlockEntity::new,
                            ModBlocks.TIANSHU_SUPERCOMPUTER_CONTROLLER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TianshuSupercomputerPortBlockEntity>>
            TIANSHU_SUPERCOMPUTER_PORT = BLOCK_ENTITY_TYPES.register(
                    "tianshu_supercomputer_port",
                    () -> com.moakiee.ae2lt.recipe.compat.LegacyBlockEntityTypes.of(
                            TianshuSupercomputerPortBlockEntity::new,
                            ModBlocks.TIANSHU_SUPERCOMPUTER_PORT.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TianshuSeedStorageBlockEntity>>
            TIANSHU_SEED_STORAGE = BLOCK_ENTITY_TYPES.register(
                    "closed_loop_seed_storage",
                    () -> com.moakiee.ae2lt.recipe.compat.LegacyBlockEntityTypes.of(
                            TianshuSeedStorageBlockEntity::new,
                            ModBlocks.CLOSED_LOOP_SEED_STORAGE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TianshuPatternStorageBlockEntity>>
            TIANSHU_PATTERN_STORAGE = BLOCK_ENTITY_TYPES.register(
                    "closed_loop_pattern_storage",
                    () -> com.moakiee.ae2lt.recipe.compat.LegacyBlockEntityTypes.of(
                            TianshuPatternStorageBlockEntity::new,
                            ModBlocks.CLOSED_LOOP_PATTERN_STORAGE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MatrixControllerBlockEntity>>
            MATRIX_CONTROLLER = BLOCK_ENTITY_TYPES.register(
                    "matter_warping_matrix_controller",
                    () -> com.moakiee.ae2lt.recipe.compat.LegacyBlockEntityTypes.of(
                            MatrixControllerBlockEntity::new,
                            ModBlocks.MATTER_WARPING_MATRIX_CONTROLLER.get())
                            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MatrixPortBlockEntity>>
            MATRIX_PORT = BLOCK_ENTITY_TYPES.register(
                    "matter_warping_matrix_port",
                    () -> com.moakiee.ae2lt.recipe.compat.LegacyBlockEntityTypes.of(
                            MatrixPortBlockEntity::new,
                            ModBlocks.MATTER_WARPING_MATRIX_PORT.get())
                            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MatrixPatternStorageBlockEntity>>
            MATRIX_PATTERN_STORAGE = BLOCK_ENTITY_TYPES.register(
                    "matter_warping_matrix_pattern_storage",
                    () -> com.moakiee.ae2lt.recipe.compat.LegacyBlockEntityTypes.of(
                            MatrixPatternStorageBlockEntity::new,
                            ModBlocks.MATTER_WARPING_MATRIX_PATTERN_STORAGE_T1.get(),
                            ModBlocks.MATTER_WARPING_MATRIX_PATTERN_STORAGE_T2.get())
                            );

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GhostOutputBlockEntity>>
            GHOST_OUTPUT = BLOCK_ENTITY_TYPES.register(
                    "ghost_output",
                    () -> com.moakiee.ae2lt.recipe.compat.LegacyBlockEntityTypes.of(
                            (pos, state) -> new GhostOutputBlockEntity(pos),
                            Blocks.AIR)
                            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FumoBlockEntity>>
            FUMO = BLOCK_ENTITY_TYPES.register(
                    "fumo",
                    () -> com.moakiee.ae2lt.recipe.compat.LegacyBlockEntityTypes.of(
                            FumoBlockEntity::new,
                            ModFumos.MOAKIEE_FUMO.get(),
                            ModFumos.CYSTRYSU_FUMO.get(),
                            ModFumos.PIGMEE_FUMO.get(),
                            ModFumos.CREATIVE_PIGMEE_FUMO.get(),
                            ModFumos.HYPERDIMENSIONAL_PIGMEE_FUMO.get())
                            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OverloadDeviceWorkbenchBlockEntity>>
            OVERLOAD_DEVICE_WORKBENCH = BLOCK_ENTITY_TYPES.register(
                    "overload_device_workbench",
                    () -> com.moakiee.ae2lt.recipe.compat.LegacyBlockEntityTypes.of(
                            OverloadDeviceWorkbenchBlockEntity::new,
                            ModBlocks.OVERLOAD_DEVICE_WORKBENCH.get())
                            );

    private ModBlockEntities() {
    }
}
