package com.moakiee.ae2lt.debug;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.StorageCells;
import appeng.blockentity.misc.InterfaceBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity;
import com.moakiee.ae2lt.logic.energy.AppFluxHelper;
import com.moakiee.ae2lt.registry.ModBlocks;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Isolated FE sinks exercise the real Applied Flux ticker and LT wireless distributor. */
@GameTestHolder("ae2lt_machine_recharge")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "ae2lt", bus = EventBusSubscriber.Bus.MOD)
public final class InterfaceEnergyRoutingGameTests {
    private static final BlockPos HOST = new BlockPos(2, 2, 2);
    private static final BlockPos ADJACENT = HOST.east();
    private static final BlockPos REMOTE = new BlockPos(4, 2, 4);
    private static final Map<BlockEntity, EnergyStorage> SINKS = new WeakHashMap<>();

    @SubscribeEvent
    public static void registerSinks(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, BlockEntityType.BARREL,
                (barrel, side) -> SINKS.get(barrel));
    }

    @GameTest(templateNamespace = "ae2lt_machine_recharge", template = "empty", timeoutTicks = 150)
    public static void wirelessFeedsOnlyBoundTarget(GameTestHelper h) { verify(h, true, true, false); }

    @GameTest(templateNamespace = "ae2lt_machine_recharge", template = "empty", timeoutTicks = 150)
    public static void wirelessWithoutBindingDoesNotLeakToAdjacent(GameTestHelper h) { verify(h, true, false, false); }

    @GameTest(templateNamespace = "ae2lt_machine_recharge", template = "empty", timeoutTicks = 150)
    public static void normalModeStillFeedsAdjacent(GameTestHelper h) { verify(h, false, false, false); }

    @GameTest(templateNamespace = "ae2lt_machine_recharge", template = "empty", timeoutTicks = 150)
    public static void nativeAeInterfaceStillFeedsAdjacent(GameTestHelper h) { verify(h, false, false, true); }

    private static void verify(GameTestHelper h, boolean wireless, boolean bound, boolean nativeAe) {
        h.assertTrue(AppFluxHelper.isAvailable(), "Applied Flux is required");
        h.setBlock(ADJACENT, Blocks.BARREL);
        h.setBlock(REMOTE, Blocks.BARREL);
        SINKS.put(h.getBlockEntity(ADJACENT), new EnergyStorage(20_000_000));
        SINKS.put(h.getBlockEntity(REMOTE), new EnergyStorage(20_000_000));
        h.setBlock(HOST, nativeAe ? AEBlocks.INTERFACE.block() : ModBlocks.OVERLOADED_INTERFACE.get());
        InterfaceBlockEntity host = h.getBlockEntity(HOST);
        host.getInterfaceLogic().getUpgrades().setItemDirect(0,
                new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("appflux:induction_card"))));
        if (host instanceof OverloadedInterfaceBlockEntity overloaded) {
            overloaded.setEnergyOutputDir(Direction.EAST);
            overloaded.setInterfaceMode(wireless ? OverloadedInterfaceBlockEntity.InterfaceMode.WIRELESS
                    : OverloadedInterfaceBlockEntity.InterfaceMode.NORMAL);
            if (bound) h.assertTrue(overloaded.addOrUpdateConnection(new OverloadedInterfaceBlockEntity.WirelessConnection(
                    h.getLevel().dimension(), h.absolutePos(REMOTE), Direction.NORTH)), "binding failed");
        }
        h.setBlock(HOST.below(), AEBlocks.CONTROLLER.block());
        h.setBlock(HOST.below(2), AEBlocks.CREATIVE_ENERGY_CELL.block());
        h.setBlock(HOST.below().west(), AEBlocks.DRIVE.block());
        var cell = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("appflux:fe_64m_cell")));
        var storage = StorageCells.getCellInventory(cell, null);
        h.assertTrue(storage != null && storage.insert(AppFluxHelper.FE_KEY, 100_000_000L,
                Actionable.MODULATE, IActionSource.ofMachine(host)) == 100_000_000L, "could not fill FE cell");
        storage.persist();
        DriveBlockEntity drive = h.getBlockEntity(HOST.below().west());
        drive.getInternalInventory().setItemDirect(0, cell);
        h.runAfterDelay(100, () -> {
            var near = h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, h.absolutePos(ADJACENT), Direction.WEST);
            var far = h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, h.absolutePos(REMOTE), Direction.NORTH);
            h.assertTrue(near != null && far != null, "missing test energy capability");
            h.assertTrue(wireless ? near.getEnergyStored() == 0 : near.getEnergyStored() > 0,
                    "adjacent FE=" + near.getEnergyStored() + ", wireless=" + wireless + ", native=" + nativeAe);
            h.assertTrue(bound ? far.getEnergyStored() > 0 : far.getEnergyStored() == 0,
                    "bound target FE=" + far.getEnergyStored() + ", bound=" + bound);
            h.succeed();
        });
    }
}
