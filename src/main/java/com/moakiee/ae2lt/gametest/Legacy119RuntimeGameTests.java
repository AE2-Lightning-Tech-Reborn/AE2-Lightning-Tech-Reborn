package com.moakiee.ae2lt.gametest;

import java.util.ArrayList;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.blockentity.OverloadedControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity;
import com.moakiee.ae2lt.compat.LegacyNetworkInvBlockEntity;
import com.moakiee.ae2lt.compat.orientation.BlockOrientation;
import com.moakiee.ae2lt.compat.orientation.RelativeSide;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.thunderbolt.api.channel.HighCapacityChannelOwner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import static com.moakiee.ae2lt.compat.GameTestAssertions.assertTrue;

/** Exercises the 1.19 runtime bridges against transformed AE2 classes. */
@GameTestHolder("ae2lt")
@PrefixGameTestTemplate(false)
public final class Legacy119RuntimeGameTests {
    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void everyRegisteredMachineConstructsOnLegacyAe2(GameTestHelper helper) {
        int count = 0;
        for (var block : net.minecraftforge.registries.ForgeRegistries.BLOCKS) {
            var id = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(block);
            if (id == null || !id.getNamespace().equals("ae2lt") || !(block instanceof net.minecraft.world.level.block.EntityBlock entityBlock)) continue;
            try {
                var be = entityBlock.newBlockEntity(BlockPos.ZERO, block.defaultBlockState());
                assertTrue(be != null, "Machine created no block entity: " + id);
                count++;
            } catch (RuntimeException | LinkageError failure) {
                throw new net.minecraft.gametest.framework.GameTestAssertException("Machine construction failed for " + id + ": " + failure);
            }
        }
        assertTrue(count > 20, "Machine registry unexpectedly incomplete: " + count);
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void interfaceLastSlotSurvivesNativeCallbacksAndNbt(GameTestHelper helper) {
        var block = ModBlocks.OVERLOADED_INTERFACE.get();
        helper.setBlock(BlockPos.ZERO, block);
        var be = (OverloadedInterfaceBlockEntity) helper.getBlockEntity(BlockPos.ZERO);
        var logic = be.getInterfaceLogic();
        int last = logic.getConfig().size() - 1;
        assertTrue(last > 8, "Overloaded interface must expose more than AE2's nine slots");
        var value = new GenericStack(AEItemKey.of(Items.DIAMOND), 1234);
        logic.getConfig().setStack(last, value);
        var tag = be.saveWithoutMetadata();
        var restored = new OverloadedInterfaceBlockEntity(be.getBlockPos(), be.getBlockState());
        restored.load(tag);
        assertTrue(value.equals(restored.getInterfaceLogic().getConfig().getStack(last)), "Last config slot lost on reload");
        assertTrue(restored.getInterfaceLogic().getStorage().size() > last, "Proxy storage has no final slot");
        assertTrue(restored.getInterfaceLogic().getStorage().getStack(last) == null, "Config must not create stored items");
        helper.runAfterDelay(10, helper::succeed);
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void orientationFollowsBlockStateAndNativeWrenchUpdates(GameTestHelper helper) {
        helper.setBlock(BlockPos.ZERO, ModBlocks.OVERLOADED_IO_PORT.get());
        var be = (LegacyNetworkInvBlockEntity) helper.getBlockEntity(BlockPos.ZERO);
        be.setOrientation(Direction.EAST, Direction.UP);
        var orientation = BlockOrientation.get(helper.getBlockState(BlockPos.ZERO));
        assertTrue(orientation.getSide(RelativeSide.FRONT) == Direction.EAST, "Wrench orientation did not update blockstate");
        assertTrue(be.getForward() == Direction.EAST && be.getUp() == Direction.UP, "Native orientation differs from blockstate");
        helper.runAfterDelay(5, () -> {
            assertTrue(be.getForward() == Direction.EAST, "First tick reset orientation");
            helper.succeed();
        });
    }

    private static final class HighCapacityOwner implements HighCapacityChannelOwner {}
    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 150)
    public static void maxFlowFinalizesFortyDevicesThroughHighCapacityRelay(GameTestHelper helper) {
        helper.setBlock(BlockPos.ZERO, ModBlocks.OVERLOADED_CONTROLLER.get());
        var controller = (OverloadedControllerBlockEntity) helper.getBlockEntity(BlockPos.ZERO);
        var managed = new ArrayList<IManagedGridNode>();
        helper.runAfterDelay(5, () -> {
            try {
                var relay = node(helper, false); managed.add(relay);
                GridHelper.createGridConnection(controller.getMainNode().getNode(), relay.getNode());
                for (int i = 0; i < 40; i++) {
                    var device = node(helper, true); managed.add(device);
                    GridHelper.createGridConnection(relay.getNode(), device.getNode());
                }
            } catch (Exception failure) {
                managed.forEach(IManagedGridNode::destroy);
                throw new RuntimeException(failure);
            }
        });
        helper.runAfterDelay(45, () -> {
            try {
                var grid = controller.getMainNode().getGrid();
                assertTrue(!grid.getPathingService().isNetworkBooting(), "Channel calculation never finalized");
                long active = managed.stream().skip(1).filter(n -> n.getNode().meetsChannelRequirements()).count();
                assertTrue(active == 40, "Only " + active + " of 40 devices received channels");
                assertTrue(grid.getPathingService().getUsedChannels() == 40, "Pathing service channel count differs");
                helper.succeed();
            } finally { managed.forEach(IManagedGridNode::destroy); }
        });
    }
    private static IManagedGridNode node(GameTestHelper helper, boolean device) {
        var node = GridHelper.createManagedNode(new HighCapacityOwner(), new IGridNodeListener<HighCapacityOwner>() {
                    public void onSaveChanges(HighCapacityOwner owner, appeng.api.networking.IGridNode node) {}
                    public void onSecurityBreak(HighCapacityOwner owner, appeng.api.networking.IGridNode node) {}
                })
                .setInWorldNode(false).setIdlePowerUsage(0);
        node.setFlags(device ? new GridFlags[]{GridFlags.REQUIRE_CHANNEL, GridFlags.CANNOT_CARRY}
                : new GridFlags[]{GridFlags.DENSE_CAPACITY});
        node.create(helper.getLevel(), null);
        return node;
    }
}
