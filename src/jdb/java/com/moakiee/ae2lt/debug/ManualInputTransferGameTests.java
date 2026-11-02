package com.moakiee.ae2lt.debug;

import appeng.menu.AEBaseMenu;
import appeng.core.sync.packets.GuiDataSyncPacket;
import com.moakiee.ae2lt.blockentity.*;
import com.moakiee.ae2lt.machine.lightningchamber.LargeStackItemHandler;
import com.moakiee.ae2lt.menu.*;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import java.util.function.IntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2lt")
@PrefixGameTestTemplate(false)
public final class ManualInputTransferGameTests {
    @GameTest(template = "pigmee_station_empty")
    public static void realMenuActionsConserveInputsAndShareMachineCooldown(GameTestHelper helper) {
        var pos = new BlockPos(2, 2, 2);
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "InputTransfer"));
        var absolute = helper.absolutePos(pos);
        player.setPos(absolute.getX() + .5, absolute.getY() + .5, absolute.getZ() + .5);
        for (int type = 0; type < 4; type++) {
            LargeStackItemHandler inventory;
            IntFunction<AEBaseMenu> create;
            int output;
            int matrix;
            switch (type) {
                case 0 -> {
                    helper.setBlock(pos, ModBlocks.LIGHTNING_ASSEMBLY_CHAMBER.get());
                    var host = (LightningAssemblyChamberBlockEntity) helper.getBlockEntity(pos);
                    host.setAutoExportEnabled(false);
                    inventory = host.getInventory(); output = 10; matrix = 9;
                    create = id -> new LightningAssemblyChamberMenu(id, player.getInventory(), host);
                }
                case 1 -> {
                    helper.setBlock(pos, ModBlocks.LIGHTNING_SIMULATION_CHAMBER.get());
                    var host = (LightningSimulationChamberBlockEntity) helper.getBlockEntity(pos);
                    host.setAutoExportEnabled(false);
                    inventory = host.getInventory(); output = 4; matrix = 3;
                    create = id -> new LightningSimulationChamberMenu(id, player.getInventory(), host);
                }
                case 2 -> {
                    helper.setBlock(pos, ModBlocks.OVERLOAD_PROCESSING_FACTORY.get());
                    var host = (OverloadProcessingFactoryBlockEntity) helper.getBlockEntity(pos);
                    host.setAutoExportEnabled(false);
                    inventory = host.getInventory(); output = 10; matrix = 9;
                    create = id -> new OverloadProcessingFactoryMenu(id, player.getInventory(), host);
                }
                default -> {
                    helper.setBlock(pos, ModBlocks.MINING_FACTORY.get());
                    var host = (MiningFactoryBlockEntity) helper.getBlockEntity(pos);
                    host.setAutoExportEnabled(false);
                    inventory = host.getInventory(); output = 2; matrix = 11;
                    inventory.setItemDirect(1, new ItemStack(Items.DIAMOND_PICKAXE));
                    create = id -> new MiningFactoryMenu(id, player.getInventory(), host);
                }
            }
            inventory.setItemDirect(0, new ItemStack(Items.STONE, 64));
            inventory.setItemDirect(output, new ItemStack(Items.STONE));
            inventory.setItemDirect(matrix, new ItemStack(ModItems.LIGHTNING_COLLAPSE_MATRIX.get()));
            var menu = create.apply(7);
            player.containerMenu = menu;
            action(player, menu);
            var feedback = (InputTransferMenu) menu;
            var result = feedback.getInputTransferResult();
            helper.assertTrue(result.accepted() && result.moved() == 64 && result.exported() == 0
                    && result.remainingSlots() == 0 && feedback.getInputTransferRevision() == 1,
                    "Server action did not report the exact transfer for machine " + type);
            helper.assertTrue(inventory.getStackInSlot(0).isEmpty()
                    && inventory.getStackInSlot(output).getCount() == 65
                    && inventory.getStackInSlot(matrix).getCount() == 1, "Transfer changed ownership or catalyst");
            action(player, menu);
            helper.assertTrue(!feedback.getInputTransferResult().accepted() && feedback.getInputTransferRevision() == 2,
                    "Cooldown refusal must also advance synchronized feedback");
            menu.removed(player);
            var reopened = create.apply(8);
            player.containerMenu = reopened;
            action(player, reopened);
            helper.assertTrue(!((InputTransferMenu) reopened).getInputTransferResult().accepted(),
                    "Reopening a menu must not bypass machine cooldown");
            reopened.removed(player);
            player.containerMenu = player.inventoryMenu;
        }
        helper.succeed();
    }

    private static void action(ServerPlayer player, AEBaseMenu menu) {
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buffer.writeVarInt(menu.containerId);
        buffer.writeUtf("transferInputs");
        var packet = new GuiDataSyncPacket(buffer);
        try { packet.serverPacketData(player); }
        finally { packet.getData().release(); buffer.release(); }
    }
}
