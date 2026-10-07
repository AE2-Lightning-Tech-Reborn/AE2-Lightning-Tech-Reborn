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
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;

import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Isolated FE sinks exercise the real Applied Flux ticker and LT wireless distributor. */
@GameTestHolder("ae2lt_machine_recharge")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "ae2lt", bus = EventBusSubscriber.Bus.FORGE)
public final class InterfaceEnergyRoutingGameTests {
    private static final BlockPos HOST = new BlockPos(2, 2, 2);
    private static final BlockPos ADJACENT = HOST.east();
    private static final BlockPos REMOTE = new BlockPos(4, 2, 4);

    @SubscribeEvent
    public static void registerSinks(AttachCapabilitiesEvent<BlockEntity> event) {
        if (event.getObject().getType() != BlockEntityType.BARREL) return;
        var storage = new EnergyStorage(20_000_000);
        var optional = LazyOptional.of(() -> storage);
        event.addCapability(new ResourceLocation("ae2lt", "test_energy"), new ICapabilityProvider() {
            public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
                return cap == ForgeCapabilities.ENERGY ? optional.cast() : LazyOptional.empty();
            }
        });
        event.addListener(optional::invalidate);
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
        h.setBlock(HOST, nativeAe ? AEBlocks.INTERFACE.block() : ModBlocks.OVERLOADED_INTERFACE.get());
        InterfaceBlockEntity host = (InterfaceBlockEntity) h.getBlockEntity(HOST);
        host.getInterfaceLogic().getUpgrades().setItemDirect(0,
                new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("appflux:induction_card"))));
        if (host instanceof OverloadedInterfaceBlockEntity overloaded) {
            overloaded.getLevel().setBlockAndUpdate(overloaded.getBlockPos(), overloaded.getBlockState().setValue(
                appeng.block.crafting.PatternProviderBlock.PUSH_DIRECTION, appeng.block.crafting.PushDirection.fromDirection(Direction.EAST)));
            overloaded.setInterfaceMode(wireless ? OverloadedInterfaceBlockEntity.InterfaceMode.WIRELESS
                    : OverloadedInterfaceBlockEntity.InterfaceMode.NORMAL);
            if (bound) h.assertTrue(overloaded.addOrUpdateConnection(new OverloadedInterfaceBlockEntity.WirelessConnection(
                    h.getLevel().dimension(), h.absolutePos(REMOTE), Direction.NORTH)), "binding failed");
        }
        h.setBlock(HOST.below(), AEBlocks.CONTROLLER.block());
        h.setBlock(HOST.below(2), AEBlocks.CREATIVE_ENERGY_CELL.block());
        h.setBlock(HOST.below().west(), AEBlocks.DRIVE.block());
        var cell = new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("appflux:fe_64m_cell")));
        var storage = StorageCells.getCellInventory(cell, null);
        h.assertTrue(storage != null && storage.insert(AppFluxHelper.FE_KEY, 100_000_000L,
                Actionable.MODULATE, IActionSource.ofMachine(host)) == 100_000_000L, "could not fill FE cell");
        storage.persist();
        DriveBlockEntity drive = (DriveBlockEntity) h.getBlockEntity(HOST.below().west());
        drive.getInternalInventory().setItemDirect(0, cell);
        h.runAfterDelay(100, () -> {
            var near = h.getBlockEntity(ADJACENT).getCapability(ForgeCapabilities.ENERGY, Direction.WEST).orElse(null);
            var far = h.getBlockEntity(REMOTE).getCapability(ForgeCapabilities.ENERGY, Direction.NORTH).orElse(null);
            h.assertTrue(near != null && far != null, "missing test energy capability");
            h.assertTrue(wireless ? near.getEnergyStored() == 0 : near.getEnergyStored() > 0,
                    "adjacent FE=" + near.getEnergyStored() + ", wireless=" + wireless + ", native=" + nativeAe);
            h.assertTrue(bound ? far.getEnergyStored() > 0 : far.getEnergyStored() == 0,
                    "bound target FE=" + far.getEnergyStored() + ", bound=" + bound);
            h.succeed();
        });
    }
}
