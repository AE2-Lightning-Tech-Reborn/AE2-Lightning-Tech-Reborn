package com.moakiee.ae2lt.gametest;

import appeng.capabilities.Capabilities;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageHelper;
import appeng.core.definitions.AEBlocks;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.CraftingTermSlot;
import appeng.helpers.InventoryAction;
import appeng.parts.reporting.CraftingTerminalPart;
import com.moakiee.ae2lt.blockentity.PigmeeSynthesisStationBlockEntity;
import com.moakiee.ae2lt.menu.PigmeeSynthesisStationMenu;
import com.moakiee.ae2lt.registry.ModBlocks;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real-world Forge regressions for local storage, crafting and recipe transfers. */
@GameTestHolder("ae2lt")
@PrefixGameTestTemplate(false)
public final class PigmeeSynthesisStationGameTests {
    private static final BlockPos STATION = new BlockPos(0, 2, 0);
    private static final BlockPos SOURCE = STATION.below();

    private static PigmeeSynthesisStationBlockEntity station(GameTestHelper helper) {
        helper.setBlock(STATION, ModBlocks.PIGMEE_SYNTHESIS_STATION.get());
        return (PigmeeSynthesisStationBlockEntity) helper.getBlockEntity(STATION);
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void recipePacketUsesLocalStorageWithoutNetwork(GameTestHelper helper) {
        var host = station(helper);
        helper.setBlock(SOURCE, Blocks.CHEST);
        var chest = (ChestBlockEntity) helper.getBlockEntity(SOURCE);
        chest.setItem(0, new ItemStack(Items.OAK_LOG, 8));
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "PigmeeFill"));
        player.setPos(host.getBlockPos().getX() + 0.5, host.getBlockPos().getY() + 0.5,
                host.getBlockPos().getZ() + 0.5);
        var menu = new PigmeeSynthesisStationMenu(1, player.getInventory(), host);
        player.containerMenu = menu;
        menu.getCraftingMatrix().setItemDirect(0, new ItemStack(Items.COBBLESTONE, 3));
        menu.broadcastChanges();
        com.moakiee.ae2lt.compat.GameTestAssertions.assertTrue(menu.isPowered(), "Local terminal must have usable power without a grid");
        com.moakiee.ae2lt.compat.GameTestAssertions.assertTrue(menu.getNetworkNode() == null, "Station must not create an ME network");
        var templates = net.minecraft.core.NonNullList.withSize(9, ItemStack.EMPTY);
        templates.set(0, new ItemStack(Items.OAK_LOG));
        // Exercise the receiving constructor: AE2's sending constructor only writes the payload.
        var payload = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        payload.writeBoolean(false);
        payload.writeInt(templates.size());
        templates.forEach(payload::writeItem);
        payload.writeBoolean(true);
        var packet = new appeng.core.sync.packets.FillCraftingGridFromRecipePacket(payload);
        payload.release();
        packet.serverPacketData(player);
        com.moakiee.ae2lt.compat.GameTestAssertions.assertTrue(menu.getCraftingMatrix().getStackInSlot(0).is(Items.OAK_LOG),
                "Fill packet must use adjacent chest ingredients");
        com.moakiee.ae2lt.compat.GameTestAssertions.assertTrue(chest.getItem(0).getCount() == 7, "Fill must debit exactly one real log");
        com.moakiee.ae2lt.compat.GameTestAssertions.assertTrue(host.getInventory().getAvailableStacks().get(AEItemKey.of(Items.COBBLESTONE)) == 3,
                "Displaced ingredients must return to the chest");
        packet.serverPacketData(player);
        com.moakiee.ae2lt.compat.GameTestAssertions.assertTrue(chest.getItem(0).getCount() == 7, "Repeated fill must retain matching ingredients");
        helper.setBlock(SOURCE, Blocks.AIR);
        menu.getCraftingMatrix().setItemDirect(0, ItemStack.EMPTY);
        packet.serverPacketData(player);
        com.moakiee.ae2lt.compat.GameTestAssertions.assertTrue(menu.getCraftingMatrix().getStackInSlot(0).isEmpty(),
                "Fill must not retain access to a removed chest");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void liveStorageAndPower(GameTestHelper helper) {
        var host = station(helper);
        var inventory = host.getInventory(); // Open before attaching storage.
        var key = AEItemKey.of(Items.IRON_INGOT);
        var action = IActionSource.empty();
        com.moakiee.ae2lt.compat.GameTestAssertions.assertTrue(!host.hasAdjacentStorage(), "Empty station must be disconnected");
        helper.setBlock(SOURCE, Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(SOURCE);
        chest.setItem(0, new ItemStack(Items.IRON_INGOT, 16));
        com.moakiee.ae2lt.compat.GameTestAssertions.assertTrue(host.getInventory() == inventory, "Menu storage identity must remain stable");
        com.moakiee.ae2lt.compat.GameTestAssertions.assertTrue(inventory.getAvailableStacks().get(key) == 16, "Attached chest must appear without reopening");
        com.moakiee.ae2lt.compat.GameTestAssertions.assertTrue(StorageHelper.poweredExtraction(host, inventory, key, 3, action) == 3,
                "Standalone extraction must need no AE network");
        com.moakiee.ae2lt.compat.GameTestAssertions.assertTrue(chest.getItem(0).getCount() == 13, "Extraction must debit the real chest");
        com.moakiee.ae2lt.compat.GameTestAssertions.assertTrue(inventory.insert(key, 2, Actionable.SIMULATE, action) == 2, "Simulated insertion");
        com.moakiee.ae2lt.compat.GameTestAssertions.assertTrue(chest.getItem(0).getCount() == 13, "Simulation must not mutate chest");
        helper.setBlock(SOURCE, Blocks.AIR);
        com.moakiee.ae2lt.compat.GameTestAssertions.assertTrue(inventory.extract(key, 64, Actionable.MODULATE, action) == 0,
                "Removed chest must not remain accessible through an open menu");
        helper.setBlock(SOURCE, Blocks.CHEST);
        com.moakiee.ae2lt.compat.GameTestAssertions.assertTrue(inventory.getAvailableStacks().get(key) == 0, "Replacement chest must be empty");
        com.moakiee.ae2lt.compat.GameTestAssertions.assertTrue(StorageHelper.poweredInsert(host, inventory, key, 4, action) == 4,
                "Standalone insertion must work");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void rejectsMeInterface(GameTestHelper helper) {
        var host = station(helper);
        helper.setBlock(SOURCE, AEBlocks.INTERFACE.block());
        helper.runAfterDelay(5, () -> {
            var capability = helper.getBlockEntity(SOURCE).getCapability(Capabilities.STORAGE_MONITORABLE_ACCESSOR, Direction.UP);
            com.moakiee.ae2lt.compat.GameTestAssertions.assertTrue(capability.isPresent(), "Fixture must actually expose ME_STORAGE");
            com.moakiee.ae2lt.compat.GameTestAssertions.assertTrue(!host.hasAdjacentStorage(), "ME interface must be rejected");
            com.moakiee.ae2lt.compat.GameTestAssertions.assertTrue(host.getInventory().insert(AEItemKey.of(Items.IRON_INGOT), 1,
                    Actionable.MODULATE, IActionSource.empty()) == 0, "ME interface must not accept station writes");
            helper.setBlock(STATION.east(), Blocks.CHEST);
            com.moakiee.ae2lt.compat.GameTestAssertions.assertTrue(host.hasAdjacentStorage(), "Rejected side must not hide another valid source");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void menuCraftingAndPersistence(GameTestHelper helper) {
        var host = station(helper);
        var matrix = host.getSubInventory(CraftingTerminalPart.INV_CRAFTING);
        com.moakiee.ae2lt.compat.GameTestAssertions.assertTrue(matrix.size() == 9, "AE2 crafting inventory ID must resolve all nine slots");
        matrix.setItemDirect(0, new ItemStack(Items.OAK_LOG, 2));
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(), new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "PigmeeTest"));
        var menu = new PigmeeSynthesisStationMenu(1, player.getInventory(), host);
        com.moakiee.ae2lt.compat.GameTestAssertions.assertTrue(menu.getCraftingMatrix() == matrix, "Menu must use the persistent matrix");
        var output = menu.getSlots(SlotSemantics.CRAFTING_RESULT).get(0);
        com.moakiee.ae2lt.compat.GameTestAssertions.assertTrue(output.getItem().is(Items.OAK_PLANKS) && output.getItem().getCount() == 4,
                "Crafting terminal must resolve the real vanilla recipe");
        player.containerMenu = menu;
        ((CraftingTermSlot) output).doClick(InventoryAction.CRAFT_ITEM, player);
        com.moakiee.ae2lt.compat.GameTestAssertions.assertTrue(menu.getCarried().is(Items.OAK_PLANKS) && menu.getCarried().getCount() == 4,
                "Taking the result must craft four planks");
        com.moakiee.ae2lt.compat.GameTestAssertions.assertTrue(matrix.getStackInSlot(0).getCount() == 1, "Crafting must consume one log");
        matrix.setItemDirect(0, new ItemStack(Items.OAK_LOG, 2));

        var tag = new CompoundTag();
        host.saveAdditional(tag);
        host.clearContent();
        com.moakiee.ae2lt.compat.GameTestAssertions.assertTrue(matrix.getStackInSlot(0).isEmpty(), "Clear content must clear the grid");
        host.loadTag(tag);
        com.moakiee.ae2lt.compat.GameTestAssertions.assertTrue(matrix.getStackInSlot(0).getCount() == 2, "NBT round trip must retain grid items");
        var drops = new ArrayList<ItemStack>();
        host.addAdditionalDrops(helper.getLevel(), helper.absolutePos(STATION), drops);
        com.moakiee.ae2lt.compat.GameTestAssertions.assertTrue(drops.size() == 1 && drops.get(0).getCount() == 2, "Grid contents must drop on removal");
        helper.succeed();
    }
}
