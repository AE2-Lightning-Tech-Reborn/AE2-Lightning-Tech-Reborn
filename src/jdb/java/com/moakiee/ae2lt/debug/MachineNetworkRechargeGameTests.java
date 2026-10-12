package com.moakiee.ae2lt.debug;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.StorageCells;
import appeng.blockentity.grid.AENetworkBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.blockentity.*;
import com.moakiee.ae2lt.logic.AppFluxHelper;
import com.moakiee.ae2lt.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real AE grids and Applied Flux cells, with no ingredients or manual machine wakeups. */
@GameTestHolder("ae2lt_machine_recharge")
@PrefixGameTestTemplate(false)
public final class MachineNetworkRechargeGameTests {
    private static final BlockPos POS = new BlockPos(2, 2, 2);

    @GameTest(templateNamespace = "ae2lt_machine_recharge", template = "empty", timeoutTicks = 150)
    public static void simulationPrecharges(GameTestHelper h) { precharge(h, ModBlocks.LIGHTNING_SIMULATION_CHAMBER.get()); }

    @GameTest(templateNamespace = "ae2lt_machine_recharge", template = "empty", timeoutTicks = 150)
    public static void assemblyPrecharges(GameTestHelper h) { precharge(h, ModBlocks.LIGHTNING_ASSEMBLY_CHAMBER.get()); }

    @GameTest(templateNamespace = "ae2lt_machine_recharge", template = "empty", timeoutTicks = 150)
    public static void catalyzerPrecharges(GameTestHelper h) { precharge(h, ModBlocks.CRYSTAL_CATALYZER.get()); }

    @GameTest(templateNamespace = "ae2lt_machine_recharge", template = "empty", timeoutTicks = 150)
    public static void overloadFactoryPrecharges(GameTestHelper h) { precharge(h, ModBlocks.OVERLOAD_PROCESSING_FACTORY.get()); }

    @GameTest(templateNamespace = "ae2lt_machine_recharge", template = "empty", timeoutTicks = 150)
    public static void teslaPrecharges(GameTestHelper h) { precharge(h, ModBlocks.TESLA_COIL.get()); }

    @GameTest(templateNamespace = "ae2lt_machine_recharge", template = "empty", timeoutTicks = 150)
    public static void miningPrecharges(GameTestHelper h) { precharge(h, ModBlocks.MINING_FACTORY.get()); }

    @GameTest(templateNamespace = "ae2lt_machine_recharge", template = "empty", timeoutTicks = 150)
    public static void teslaRecoversWhenNetworkGetsFe(GameTestHelper h) { delayed(h, ModBlocks.TESLA_COIL.get()); }

    @GameTest(templateNamespace = "ae2lt_machine_recharge", template = "empty", timeoutTicks = 150)
    public static void simulationRecoversWhenNetworkGetsFe(GameTestHelper h) { delayed(h, ModBlocks.LIGHTNING_SIMULATION_CHAMBER.get()); }

    @GameTest(templateNamespace = "ae2lt_machine_recharge", template = "empty", timeoutTicks = 150)
    public static void miningRecoversWhenNetworkGetsFe(GameTestHelper h) { delayed(h, ModBlocks.MINING_FACTORY.get()); }

    private static void precharge(GameTestHelper h, Block block) {
        var machine = setup(h, block, true);
        var energy = energy(machine);
        energy.receiveEnergy(energy.getMaxEnergyStored() / 2, false);
        h.runAfterDelay(80, () -> {
            check(energy.getEnergyStored() == energy.getMaxEnergyStored(),
                    block + " did not refill from half-full while idle: " + energy.getEnergyStored());
            h.succeed();
        });
    }

    private static void delayed(GameTestHelper h, Block block) {
        var machine = setup(h, block, false);
        var energy = energy(machine);
        h.runAfterDelay(40, () -> {
            check(energy.getEnergyStored() == 0, "empty network supplied energy");
            var grid = machine.getMainNode().getGrid();
            check(grid != null, "test machine has no grid");
            long inserted = grid.getStorageService().getInventory().insert(AppFluxHelper.FE_KEY,
                    1_000_000_000L, Actionable.MODULATE, IActionSource.ofMachine(machine));
            check(inserted == 1_000_000_000L, "could not supply network FE");
        });
        h.runAfterDelay(100, () -> {
            check(energy.getEnergyStored() == energy.getMaxEnergyStored(),
                    block + " did not recover when network FE arrived: " + energy.getEnergyStored());
            h.succeed();
        });
    }

    private static AENetworkBlockEntity setup(GameTestHelper h, Block block, boolean fill) {
        check(AppFluxHelper.isAvailable(), "Applied Flux must be loaded for this suite");
        h.setBlock(POS, block);
        AENetworkBlockEntity machine = (AENetworkBlockEntity) h.getBlockEntity(POS);
        h.setBlock(POS.below(), AEBlocks.CONTROLLER.block());
        h.setBlock(POS.below(2), AEBlocks.CREATIVE_ENERGY_CELL.block());
        h.setBlock(POS.below().east(), AEBlocks.DRIVE.block());
        var cell = new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("appflux:fe_64m_cell")));
        var storage = StorageCells.getCellInventory(cell, null);
        check(storage != null, "Applied Flux cell was not registered");
        if (fill) {
            check(storage.insert(AppFluxHelper.FE_KEY, 1_000_000_000L, Actionable.MODULATE,
                    IActionSource.ofMachine(machine)) == 1_000_000_000L, "could not fill FE cell");
            storage.persist();
        }
        DriveBlockEntity drive = (DriveBlockEntity) h.getBlockEntity(POS.below().east());
        drive.getInternalInventory().setItemDirect(0, cell);
        return machine;
    }

    private static IEnergyStorage energy(AENetworkBlockEntity machine) {
        if (machine instanceof LightningSimulationChamberBlockEntity m) return m.getEnergyStorage();
        if (machine instanceof LightningAssemblyChamberBlockEntity m) return m.getEnergyStorage();
        if (machine instanceof CrystalCatalyzerBlockEntity m) return m.getEnergyStorage();
        if (machine instanceof OverloadProcessingFactoryBlockEntity m) return m.getEnergyStorage();
        if (machine instanceof TeslaCoilBlockEntity m) return m.getEnergyStorage();
        if (machine instanceof MiningFactoryBlockEntity m) return m.getEnergyStorage();
        throw new AssertionError("unknown machine " + machine);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new net.minecraft.gametest.framework.GameTestAssertException(message);
    }
}
