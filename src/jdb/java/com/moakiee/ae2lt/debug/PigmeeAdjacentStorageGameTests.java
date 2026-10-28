package com.moakiee.ae2lt.debug;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.core.sync.packets.FillCraftingGridFromRecipePacket;
import appeng.helpers.InventoryAction;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.CraftingTermSlot;
import appeng.parts.reporting.CraftingTerminalPart;
import com.moakiee.ae2lt.blockentity.PigmeeSynthesisStationBlockEntity;
import com.moakiee.ae2lt.gametest.PigmeeSynthesisStationGameTests;
import com.moakiee.ae2lt.menu.PigmeeSynthesisStationMenu;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.mojang.authlib.GameProfile;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import io.netty.buffer.Unpooled;

/** Opt-in real-world regressions; never included in the published jar. */
@GameTestHolder("ae2lt_pigmee_storage")
@PrefixGameTestTemplate(false)
public final class PigmeeAdjacentStorageGameTests {
    private static final BlockPos STATION = new BlockPos(2, 2, 2);
    private static final IActionSource ACTION = IActionSource.empty();
    private static final Map<BlockPos, IItemHandler> ITEM_PORTS = new HashMap<>();
    private static final Map<BlockPos, IFluidHandler> FLUID_PORTS = new HashMap<>();

    // Capability-only port: a Container fallback would outlive revoked capabilities.
    private static final class PortBlockEntity extends BlockEntity {
        private PortBlockEntity(BlockPos pos, BlockState state) {
            super(BlockEntityType.BARREL, pos, state);
        }

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
            if (capability == ForgeCapabilities.ITEM_HANDLER) {
                var handler = ITEM_PORTS.get(getBlockPos());
                return handler == null ? LazyOptional.empty() : LazyOptional.of(() -> handler).cast();
            }
            if (capability == ForgeCapabilities.FLUID_HANDLER) {
                var handler = FLUID_PORTS.get(getBlockPos());
                return handler == null ? LazyOptional.empty() : LazyOptional.of(() -> handler).cast();
            }
            return super.getCapability(capability, side);
        }
    }

    private static PigmeeSynthesisStationBlockEntity station(GameTestHelper helper) {
        helper.setBlock(STATION, ModBlocks.PIGMEE_SYNTHESIS_STATION.get());
        return (PigmeeSynthesisStationBlockEntity) helper.getBlockEntity(STATION);
    }

    private static BarrelBlockEntity barrel(GameTestHelper helper, Direction side, ItemStack stack) {
        helper.setBlock(STATION.relative(side), Blocks.BARREL);
        var barrel = (BarrelBlockEntity) helper.getBlockEntity(STATION.relative(side));
        barrel.setItem(0, stack);
        return barrel;
    }

    private static void port(GameTestHelper helper, Direction side, IItemHandler items, IFluidHandler fluids) {
        var pos = helper.absolutePos(STATION.relative(side));
        if (items != null) ITEM_PORTS.put(pos, items);
        if (fluids != null) FLUID_PORTS.put(pos, fluids);
        helper.setBlock(STATION.relative(side), Blocks.BARREL);
        helper.getLevel().setBlockEntity(new PortBlockEntity(pos, Blocks.BARREL.defaultBlockState()));
    }

    private static void clearPorts(GameTestHelper helper) {
        for (var side : Direction.values()) {
            var pos = helper.absolutePos(STATION.relative(side));
            ITEM_PORTS.remove(pos);
            FLUID_PORTS.remove(pos);
        }
    }

    @GameTest(templateNamespace = "ae2lt", template = "pigmee_station_empty")
    public static void existingStorageAndPowerContract(GameTestHelper helper) {
        PigmeeSynthesisStationGameTests.liveStorageAndPower(helper);
    }

    @GameTest(templateNamespace = "ae2lt", template = "pigmee_station_empty")
    public static void existingMeExclusionContract(GameTestHelper helper) {
        PigmeeSynthesisStationGameTests.rejectsMeInterface(helper);
    }

    @GameTest(templateNamespace = "ae2lt", template = "pigmee_station_empty")
    public static void existingCraftingAndPersistenceContract(GameTestHelper helper) {
        PigmeeSynthesisStationGameTests.menuCraftingAndPersistence(helper);
    }

    @GameTest(templateNamespace = "ae2lt", template = "pigmee_station_empty")
    public static void allSixSidesAndSplitItemTransfers(GameTestHelper helper) {
        var host = station(helper);
        var storage = host.getInventory();
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var barrels = new EnumMap<Direction, BarrelBlockEntity>(Direction.class);
        for (var side : Direction.values()) {
            barrels.put(side, barrel(helper, side, new ItemStack(Items.IRON_INGOT, 16)));
        }
        helper.assertTrue(storage.getAvailableStacks().get(iron) == 96, "All six faces must contribute");
        helper.assertTrue(storage.extract(iron, 70, Actionable.SIMULATE, ACTION) == 70, "Simulate across five inventories");
        helper.assertTrue(storage.getAvailableStacks().get(iron) == 96, "Simulation must not remove items");
        helper.assertTrue(storage.extract(iron, 70, Actionable.MODULATE, ACTION) == 70, "Extraction must span faces");
        helper.assertTrue(storage.getAvailableStacks().get(iron) == 26, "Combined count after extraction");
        helper.assertTrue(barrels.values().stream().mapToInt(b -> b.getItem(0).getCount()).sum() == 26,
                "Physical count matches the terminal");
        // Leave 4 and 6 slots of stack capacity on two faces; all other slots are full.
        for (var barrel : barrels.values()) {
            for (int slot = 0; slot < barrel.getContainerSize(); slot++) {
                barrel.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
            }
        }
        barrels.get(Direction.NORTH).setItem(0, new ItemStack(Items.IRON_INGOT, 60));
        barrels.get(Direction.EAST).setItem(0, new ItemStack(Items.IRON_INGOT, 58));
        helper.assertTrue(storage.insert(iron, 12, Actionable.SIMULATE, ACTION) == 10, "Simulate aggregate free capacity");
        helper.assertTrue(storage.getAvailableStacks().get(iron) == 118, "Insertion simulation is read-only");
        helper.assertTrue(storage.insert(iron, 12, Actionable.MODULATE, ACTION) == 10, "Insertion must spill to another face");
        helper.assertTrue(storage.getAvailableStacks().get(iron) == 128, "Only ten items inserted");
        helper.assertTrue(storage.insert(iron, 1, Actionable.MODULATE, ACTION) == 0, "Full aggregate rejects remainder");
        helper.succeed();
    }

    @GameTest(templateNamespace = "ae2lt", template = "pigmee_station_empty")
    public static void cuttingBoardAndNativeRecipeFill(GameTestHelper helper) {
        var host = station(helper);
        var storage = host.getInventory();
        var board = BuiltInRegistries.BLOCK.get(new ResourceLocation("farmersdelight:cutting_board"));
        helper.assertTrue(board != Blocks.AIR, "This suite requires the real Farmer's Delight jar");
        var north = barrel(helper, Direction.NORTH, new ItemStack(Items.OAK_PLANKS, 2));
        var east = barrel(helper, Direction.EAST, new ItemStack(Items.OAK_PLANKS, 1));
        var south = barrel(helper, Direction.SOUTH, new ItemStack(Items.COBBLESTONE, 4));
        var west = barrel(helper, Direction.WEST, new ItemStack(Items.IRON_INGOT, 1));
        var down = barrel(helper, Direction.DOWN, new ItemStack(Items.REDSTONE, 1));
        var matrix = host.getSubInventory(CraftingTerminalPart.INV_CRAFTING);
        matrix.setItemDirect(0, new ItemStack(Items.OAK_LOG, 7));
        helper.setBlock(STATION.above(), board);
        helper.assertTrue(storage.getAvailableStacks().get(AEItemKey.of(Items.OAK_PLANKS)) == 3,
                "Empty cutting board must not hide side inventories");
        helper.assertTrue(matrix.getStackInSlot(0).getCount() == 7, "Placement preserves crafting slots");
        var boardItems = helper.getLevel().getBlockEntity(helper.absolutePos(STATION.above()))
                .getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN).orElse(null);
        helper.assertTrue(boardItems != null && boardItems.insertItem(0, new ItemStack(Items.CARROT), false).isEmpty(),
                "Real cutting board inventory accepts fixture item");
        helper.assertTrue(storage.getAvailableStacks().get(AEItemKey.of(Items.CARROT)) == 1
                && storage.getAvailableStacks().get(AEItemKey.of(Items.OAK_PLANKS)) == 3,
                "Filled board and side inventories appear together");
        helper.assertTrue(storage.extract(AEItemKey.of(Items.CARROT), 1, Actionable.MODULATE, ACTION) == 1
                && boardItems.getStackInSlot(0).isEmpty(), "Extract from the board without losing other faces");
        helper.setBlock(STATION.above(), Blocks.AIR);
        helper.assertTrue(storage.getAvailableStacks().get(AEItemKey.of(Items.OAK_PLANKS)) == 3, "Removing board preserves list");
        helper.setBlock(STATION.above(), board);
        matrix.clear();
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "PigmeeMergeQA"));
        player.setPos(host.getBlockPos().getX() + .5, host.getBlockPos().getY() + .5, host.getBlockPos().getZ() + .5);
        player.getInventory().clearContent();
        var menu = new PigmeeSynthesisStationMenu(1, player.getInventory(), host);
        player.containerMenu = menu;
        var template = NonNullList.withSize(9, ItemStack.EMPTY);
        for (int slot : new int[] {0, 1, 2}) template.set(slot, new ItemStack(Items.OAK_PLANKS));
        for (int slot : new int[] {3, 5, 6, 8}) template.set(slot, new ItemStack(Items.COBBLESTONE));
        template.set(4, new ItemStack(Items.IRON_INGOT));
        template.set(7, new ItemStack(Items.REDSTONE));
        var payload = new FriendlyByteBuf(Unpooled.buffer());
        FillCraftingGridFromRecipePacket packet;
        try {
            payload.writeBoolean(false);
            payload.writeInt(template.size());
            template.forEach(payload::writeItem);
            payload.writeBoolean(true);
            packet = new FillCraftingGridFromRecipePacket(payload);
        } finally {
            payload.release();
        }
        packet.serverPacketData(player);
        for (int slot = 0; slot < 9; slot++) {
            helper.assertTrue(ItemStack.isSameItemSameTags(matrix.getStackInSlot(slot), template.get(slot))
                    && matrix.getStackInSlot(slot).getCount() == 1, "Native recipe fill slot " + slot);
        }
        for (var barrel : new BarrelBlockEntity[] {north, east, south, west, down}) {
            helper.assertTrue(barrel.isEmpty(), "Recipe fill debits each physical source");
        }
        var output = menu.getSlots(SlotSemantics.CRAFTING_RESULT).get(0);
        helper.assertTrue(output.getItem().is(Items.PISTON), "Native menu resolves the filled recipe");
        ((CraftingTermSlot) output).doClick(InventoryAction.CRAFT_ITEM, player);
        helper.assertTrue(menu.getCarried().is(Items.PISTON) && menu.getCarried().getCount() == 1,
                "Craft piston from ingredients sourced across five faces");
        helper.assertTrue(host.getInventory() == storage, "Menu storage identity stays stable");
        helper.succeed();
    }

    @GameTest(templateNamespace = "ae2lt", template = "pigmee_station_empty")
    public static void liveRemovalAndReplacement(GameTestHelper helper) {
        var host = station(helper);
        var storage = host.getInventory();
        var iron = AEItemKey.of(Items.IRON_INGOT);
        barrel(helper, Direction.NORTH, new ItemStack(Items.IRON_INGOT, 10));
        barrel(helper, Direction.EAST, new ItemStack(Items.IRON_INGOT, 20));
        helper.assertTrue(storage.getAvailableStacks().get(iron) == 30, "Initial combined count");
        helper.setBlock(STATION.north(), Blocks.AIR);
        helper.assertTrue(storage.getAvailableStacks().get(iron) == 20, "Removed source immediately disappears");
        barrel(helper, Direction.NORTH, new ItemStack(Items.IRON_INGOT, 7));
        helper.assertTrue(storage.getAvailableStacks().get(iron) == 27, "Replacement capability is used live");
        helper.assertTrue(storage.extract(iron, 99, Actionable.MODULATE, ACTION) == 27, "No stale source can be extracted");
        helper.setBlock(STATION.north(), Blocks.AIR);
        helper.setBlock(STATION.east(), Blocks.AIR);
        helper.assertTrue(!host.hasAdjacentStorage(), "Disconnect when all sources are removed");
        helper.succeed();
    }

    @GameTest(templateNamespace = "ae2lt", template = "pigmee_station_empty")
    public static void sharedHandlersCountAndSimulateOnce(GameTestHelper helper) {
        var host = station(helper);
        var storage = host.getInventory();
        var items = new ItemStackHandler(1);
        items.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 16));
        var fluid = new FluidTank(4000);
        fluid.fill(new FluidStack(Fluids.WATER, 2000), IFluidHandler.FluidAction.EXECUTE);
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var water = AEFluidKey.of(Fluids.WATER);
        try {
            port(helper, Direction.NORTH, items, fluid);
            port(helper, Direction.EAST, items, fluid);
            helper.assertTrue(storage.getAvailableStacks().get(iron) == 16, "Shared item handler is counted once");
            helper.assertTrue(storage.getAvailableStacks().get(water) == 2000, "Shared fluid handler is counted once");
            helper.assertTrue(storage.extract(iron, 40, Actionable.SIMULATE, ACTION) == 16, "Shared stock simulation does not double");
            helper.assertTrue(storage.insert(iron, 100, Actionable.SIMULATE, ACTION) == 48, "Shared item capacity does not double");
            helper.assertTrue(storage.extract(water, 6000, Actionable.SIMULATE, ACTION) == 2000, "Shared fluid stock does not double");
            helper.assertTrue(storage.insert(water, 6000, Actionable.SIMULATE, ACTION) == 2000, "Shared fluid capacity does not double");
            helper.assertTrue(items.getStackInSlot(0).getCount() == 16 && fluid.getFluidAmount() == 2000,
                    "All simulations preserve the backing inventories");
            helper.assertTrue(storage.extract(iron, 40, Actionable.MODULATE, ACTION) == 16 && items.getStackInSlot(0).isEmpty(),
                    "Shared handler extraction conserves items");
            helper.assertTrue(storage.insert(water, 6000, Actionable.MODULATE, ACTION) == 2000 && fluid.getFluidAmount() == 4000,
                    "Shared handler insertion conserves fluid");
            ITEM_PORTS.remove(helper.absolutePos(STATION.north()));
            FLUID_PORTS.remove(helper.absolutePos(STATION.north()));
            helper.setBlock(STATION.north(), Blocks.AIR);
            helper.assertTrue(storage.getAvailableStacks().get(water) == 4000, "Remaining port still exposes shared storage");
            helper.succeed();
        } finally {
            clearPorts(helper);
        }
    }

    @GameTest(templateNamespace = "ae2lt", template = "pigmee_station_empty")
    public static void silentMultiblockHandlerReplacementAndRemoval(GameTestHelper helper) {
        var host = station(helper);
        var storage = host.getInventory();
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var water = AEFluidKey.of(Fluids.WATER);
        var oldItems = new ItemStackHandler(1);
        oldItems.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 16));
        var oldFluid = new FluidTank(4000);
        oldFluid.fill(new FluidStack(Fluids.WATER, 2000), IFluidHandler.FluidAction.EXECUTE);
        var newItems = new ItemStackHandler(1);
        newItems.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 7));
        var newFluid = new FluidTank(4000);
        newFluid.fill(new FluidStack(Fluids.WATER, 500), IFluidHandler.FluidAction.EXECUTE);
        try {
            port(helper, Direction.NORTH, oldItems, oldFluid);
            port(helper, Direction.EAST, oldItems, oldFluid);
            helper.assertTrue(storage.getAvailableStacks().get(iron) == 16
                    && storage.getAvailableStacks().get(water) == 2000, "Prime both cached multiblock ports");
            // Deliberately omit capability invalidation: the part blocks remain
            // while their remote controller changes the backing inventory.
            for (var side : new Direction[] {Direction.NORTH, Direction.EAST}) {
                var pos = helper.absolutePos(STATION.relative(side));
                ITEM_PORTS.put(pos, newItems);
                FLUID_PORTS.put(pos, newFluid);
            }
            helper.assertTrue(storage.extract(iron, 64, Actionable.SIMULATE, ACTION) == 7,
                    "Simulation must use the replacement once, without a prior list refresh");
            helper.assertTrue(storage.getAvailableStacks().get(iron) == 7
                    && storage.getAvailableStacks().get(water) == 500, "Only live replacement stock is listed");
            helper.assertTrue(storage.extract(iron, 64, Actionable.MODULATE, ACTION) == 7
                    && newItems.getStackInSlot(0).isEmpty(), "Extraction must debit the replacement");
            helper.assertTrue(storage.insert(water, 500, Actionable.MODULATE, ACTION) == 500
                    && newFluid.getFluidAmount() == 1000, "Insertion must reach the replacement");
            for (var side : new Direction[] {Direction.NORTH, Direction.EAST}) {
                var pos = helper.absolutePos(STATION.relative(side));
                ITEM_PORTS.remove(pos);
                FLUID_PORTS.remove(pos);
            }
            helper.assertTrue(storage.insert(iron, 1, Actionable.MODULATE, ACTION) == 0
                    && storage.extract(water, 4000, Actionable.MODULATE, ACTION) == 0,
                    "Revoked capabilities must immediately reject writes and extraction");
            helper.assertTrue(storage.getAvailableStacks().isEmpty() && !host.hasAdjacentStorage(),
                    "Revoked multiblock must disappear without reopening");
            helper.assertTrue(oldItems.getStackInSlot(0).getCount() == 16 && oldFluid.getFluidAmount() == 2000,
                    "Detached inventories must never be read as live stock or mutated");
            helper.assertTrue(newItems.getStackInSlot(0).isEmpty() && newFluid.getFluidAmount() == 1000,
                    "Revoked replacement inventories must remain untouched");
            helper.succeed();
        } finally {
            clearPorts(helper);
        }
    }

    @GameTest(templateNamespace = "ae2lt", template = "pigmee_station_empty")
    public static void removedHostAndRemovedNeighbourRejectAccess(GameTestHelper helper) {
        var host = station(helper);
        var storage = host.getInventory();
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var source = barrel(helper, Direction.NORTH, new ItemStack(Items.IRON_INGOT, 12));
        helper.assertTrue(storage.getAvailableStacks().get(iron) == 12, "Prime source");
        source.setRemoved();
        helper.assertTrue(storage.extract(iron, 12, Actionable.MODULATE, ACTION) == 0,
                "Removed entity still present at its position must be rejected");
        helper.setBlock(STATION.north(), Blocks.AIR);
        source = barrel(helper, Direction.NORTH, new ItemStack(Items.IRON_INGOT, 12));
        helper.assertTrue(storage.getAvailableStacks().get(iron) == 12, "Restored source is accessible");
        helper.setBlock(STATION, Blocks.AIR);
        // Model a stale reference incorrectly marked live by a moving-block mod.
        host.clearRemoved();
        helper.assertTrue(storage.extract(iron, 12, Actionable.MODULATE, ACTION) == 0
                && storage.insert(iron, 1, Actionable.MODULATE, ACTION) == 0,
                "Detached station must not access the old position's neighbours");
        helper.assertTrue(source.getItem(0).getCount() == 12, "Rejected access preserves physical stock");
        helper.succeed();
    }

    @GameTest(templateNamespace = "ae2lt", template = "pigmee_station_empty")
    public static void independentFluidTanksCombine(GameTestHelper helper) {
        var host = station(helper);
        var storage = host.getInventory();
        var first = new FluidTank(4000);
        var second = new FluidTank(4000);
        first.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
        second.fill(new FluidStack(Fluids.WATER, 2000), IFluidHandler.FluidAction.EXECUTE);
        var water = AEFluidKey.of(Fluids.WATER);
        try {
            port(helper, Direction.NORTH, null, first);
            port(helper, Direction.EAST, null, second);
            helper.assertTrue(storage.getAvailableStacks().get(water) == 3000, "Combine same fluid across tanks");
            helper.assertTrue(storage.extract(water, 2500, Actionable.SIMULATE, ACTION) == 2500
                    && first.getFluidAmount() + second.getFluidAmount() == 3000, "Fluid extraction simulation");
            helper.assertTrue(storage.extract(water, 2500, Actionable.MODULATE, ACTION) == 2500
                    && first.getFluidAmount() + second.getFluidAmount() == 500, "Drain across faces");
            helper.assertTrue(storage.insert(water, 10000, Actionable.SIMULATE, ACTION) == 7500
                    && first.getFluidAmount() + second.getFluidAmount() == 500, "Fluid insertion simulation");
            helper.assertTrue(storage.insert(water, 10000, Actionable.MODULATE, ACTION) == 7500
                    && first.getFluidAmount() + second.getFluidAmount() == 8000, "Fill across faces, report accepted amount");
            helper.succeed();
        } finally {
            clearPorts(helper);
        }
    }
}
