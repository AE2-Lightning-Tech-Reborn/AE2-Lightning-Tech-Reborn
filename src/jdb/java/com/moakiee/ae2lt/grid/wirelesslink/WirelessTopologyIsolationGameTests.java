package com.moakiee.ae2lt.grid.wirelesslink;

import appeng.api.parts.PartHelper;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import appeng.parts.AEBasePart;
import appeng.parts.networking.QuartzFiberPart;
import com.moakiee.ae2lt.blockentity.AdvancedWirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.grid.FrequencySecurityLevel;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2lt_topology_isolation")
@PrefixGameTestTemplate(false)
public final class WirelessTopologyIsolationGameTests {
    private static final BlockPos TRANSMITTER = new BlockPos(2, 2, 2);
    private static final BlockPos LEFT = new BlockPos(5, 2, 6);

    private record Fixture(ServerPlayer player, int frequency, BlockPos left, BlockPos middle, BlockPos right,
                           AEBasePart leftCable, AEBasePart middleCable, AEBasePart rightCable, QuartzFiberPart fiber) {}

    private static Fixture fixture(GameTestHelper helper, boolean quartz) {
        var level = helper.getLevel();
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "TopologyQA"));
        helper.setBlock(TRANSMITTER, ModBlocks.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER.get());
        helper.setBlock(TRANSMITTER.west(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        AdvancedWirelessOverloadedControllerBlockEntity controller = helper.getBlockEntity(TRANSMITTER);
        var frequency = WirelessFrequencyManager.get().createFrequency(player, "Topology QA", 0xffffff,
                FrequencySecurityLevel.PRIVATE, "");
        controller.setFrequency(frequency.getId());
        var left = helper.absolutePos(LEFT);
        var cable = AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT);
        var a = PartHelper.setPart(level, left, null, player, cable);
        var fiber = quartz ? PartHelper.setPart(level, left, Direction.EAST, player, AEParts.QUARTZ_FIBER.asItem()) : null;
        var b = PartHelper.setPart(level, left.east(), null, player, cable);
        var c = PartHelper.setPart(level, left.east(2), null, player, cable);
        return new Fixture(player, frequency.getId(), left, left.east(), left.east(2), a, b, c, fiber);
    }

    private static WirelessLinkRegistry registry(GameTestHelper helper) {
        return WirelessLinkRegistry.get(helper.getLevel().getServer());
    }

    private static void link(GameTestHelper helper, Fixture f, BlockPos pos) {
        var result = registry(helper).handleManualUse(f.player, f.frequency, helper.getLevel(), pos, Direction.UP, null);
        helper.assertTrue(result.translationKey().equals("ae2lt.frequency_card.connected")
                || result.translationKey().equals("ae2lt.frequency_card.pending"), "fixture link failed: " + result);
    }

    private static void assertFiberIsolation(GameTestHelper helper, Fixture f) {
        var inner = f.fiber.getGridNode();
        var outer = f.fiber.getExternalFacingNode();
        helper.assertTrue(inner != outer && inner.getGrid() != outer.getGrid(), "quartz fiber bridged independent grids");
        helper.assertTrue(!PhysicalGridCluster.collect(outer).contains(inner), "physical traversal crossed fiber");
        helper.assertTrue(!registry(helper).inspectTarget(helper.getLevel(), f.left, Direction.UP, null).isPresent(),
                "topology recovery copied the outer frequency to the inner cable");
        helper.assertTrue(!registry(helper).inspectTarget(helper.getLevel(), f.left, Direction.EAST, null).isPresent(),
                "topology recovery copied the outer frequency to the inner fiber node");
    }

    @GameTest(templateNamespace = "ae2lt", template = "workstation_test", timeoutTicks = 150)
    public static void harmlessClickNextToFiberCannotConnectOppositeGrid(GameTestHelper helper) {
        var f = fixture(helper, true);
        helper.runAfterDelay(30, () -> {
            assertFiberIsolation(helper, f);
            link(helper, f, f.middle);
        });
        helper.runAfterDelay(55, () -> {
            assertFiberIsolation(helper, f);
            // The left-click hook takes this snapshot even when the block is never broken.
            registry(helper).prepareClusterTopologyChange(helper.getLevel(), f.middle);
        });
        helper.runAfterDelay(85, () -> {
            assertFiberIsolation(helper, f);
            helper.assertTrue(registry(helper).inspectTarget(helper.getLevel(), f.middle, Direction.UP, null).isPresent(),
                    "harmless click removed the legitimate link");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "ae2lt", template = "workstation_test", timeoutTicks = 150)
    public static void breakingOuterCableDoesNotInheritThroughFiber(GameTestHelper helper) {
        var f = fixture(helper, true);
        helper.runAfterDelay(30, () -> link(helper, f, f.middle));
        helper.runAfterDelay(55, () -> {
            assertFiberIsolation(helper, f);
            registry(helper).onBlockChanged(helper.getLevel(), f.middle);
            helper.getLevel().setBlockAndUpdate(f.middle, Blocks.AIR.defaultBlockState());
        });
        helper.runAfterDelay(85, () -> {
            assertFiberIsolation(helper, f);
            helper.assertTrue(registry(helper).inspectTarget(helper.getLevel(), f.right, Direction.UP, null).isPresent(),
                    "surviving outer cable lost its own frequency");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "ae2lt", template = "workstation_test", timeoutTicks = 150)
    public static void ordinaryCableSplitStillPreservesBothComponents(GameTestHelper helper) {
        var f = fixture(helper, false);
        helper.runAfterDelay(30, () -> link(helper, f, f.middle));
        helper.runAfterDelay(55, () -> {
            registry(helper).onBlockChanged(helper.getLevel(), f.middle);
            helper.getLevel().setBlockAndUpdate(f.middle, Blocks.AIR.defaultBlockState());
        });
        helper.runAfterDelay(85, () -> {
            for (var pos : new BlockPos[] {f.left, f.right}) {
                helper.assertTrue(registry(helper).inspectTarget(helper.getLevel(), pos, Direction.UP, null).isPresent(),
                        "ordinary surviving component lost frequency at " + pos);
            }
            helper.succeed();
        });
    }
}
