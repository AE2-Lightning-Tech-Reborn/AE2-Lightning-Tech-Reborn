package com.moakiee.ae2lt.debug;

import com.moakiee.ae2lt.celestweave.PhaseFlightMovementGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Opt-in tests against the actual MGU jar; no replacement conveyor or mocked velocity setter. */
@GameTestHolder("ae2lt_phase_conveyor")
@PrefixGameTestTemplate(false)
public final class PhaseLockConveyorGameTests {
    @GameTest(templateNamespace = "ae2lt", template = "pigmee_station_empty")
    public static void lockedPlayerDoesNotDriftDuringNativeTravel(GameTestHelper h) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            PhaseLockMovementGameTests.floor(h, conveyor(h, direction));
            var player = PhaseLockMovementGameTests.player(h, new Vec3(3.3, 0.875, 3.3));
            player.setDeltaMovement(0, -0.08, 0);
            Vec3 start = player.position();
            try {
                PhaseFlightMovementGuard.updatePhaseLockProtection(player, true, true);
                for (int tick = 0; tick < 10; tick++) {
                    player.travel(Vec3.ZERO);
                    h.assertTrue(player.position().distanceToSqr(start) < 1.0E-12,
                            "conveyor moved a protected player during travel: " + direction);
                    h.assertTrue(player.getDeltaMovement().horizontalDistance() < 1.0E-9,
                            "conveyor injected velocity inside authorized travel: " + direction);
                    h.assertTrue(player.onGround(), "conveyor surface must retain floor collision");
                }
            } finally {
                PhaseFlightMovementGuard.clear(player);
            }
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "ae2lt", template = "pigmee_station_empty")
    public static void protectedPlayerCanWalkJumpAndCollideOnConveyor(GameTestHelper h) {
        PhaseLockMovementGameTests.floor(h, conveyor(h, Direction.NORTH));
        for (int z = 0; z < 7; z++) for (int y = 1; y < 4; y++) {
            h.setBlock(new BlockPos(4, y, z), Blocks.STONE);
        }
        var player = PhaseLockMovementGameTests.player(h, new Vec3(2.5, 0.875, 3.5));
        Vec3 start = player.position();
        player.setDeltaMovement(0, -0.08, 0);
        try {
            PhaseFlightMovementGuard.updatePhaseLockProtection(player, true, true);
            for (int tick = 0; tick < 30; tick++) player.travel(new Vec3(1, 0, 0));
            h.assertTrue(player.getX() > start.x + 0.5, "phase lock blocked walking input on a conveyor");
            h.assertTrue(Math.abs(player.getZ() - start.z) < 1.0E-9, "conveyor added sideways drift to walking");
            h.assertTrue(player.horizontalCollision && player.onGround(), "wall and conveyor floor collision must work");
            player.jumpFromGround();
            h.assertTrue(player.getDeltaMovement().y > 0, "phase lock blocked jumping off the conveyor");
            player.travel(Vec3.ZERO);
            h.assertTrue(player.getY() > start.y, "jump failed to leave the conveyor surface");
        } finally {
            PhaseFlightMovementGuard.clear(player);
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "ae2lt", template = "pigmee_station_empty")
    public static void disabledProtectionAndSneakingPreserveConveyorBehavior(GameTestHelper h) {
        PhaseLockMovementGameTests.floor(h, conveyor(h, Direction.EAST));
        var player = PhaseLockMovementGameTests.player(h, new Vec3(2.5, 0.875, 3.5));
        player.setDeltaMovement(0, -0.08, 0);
        try {
            // Teleport protection alone must not disable conveyors.
            PhaseFlightMovementGuard.updatePhaseLockProtection(player, false, true);
            double startX = player.getX();
            for (int tick = 0; tick < 4; tick++) player.travel(Vec3.ZERO);
            h.assertTrue(player.getX() > startX + 0.1, "disabled force lock changed conveyor movement");

            PhaseFlightMovementGuard.runAsSelfMovement(player, () -> player.setDeltaMovement(0, -0.08, 0));
            player.setShiftKeyDown(true);
            startX = player.getX();
            for (int tick = 0; tick < 4; tick++) player.travel(Vec3.ZERO);
            h.assertTrue(Math.abs(player.getX() - startX) < 1.0E-9, "MGU's sneak-to-stop behavior changed");

            player.setShiftKeyDown(false);
            PhaseFlightMovementGuard.updatePhaseLockProtection(player, true, true);
            startX = player.getX();
            for (int tick = 0; tick < 4; tick++) player.travel(Vec3.ZERO);
            h.assertTrue(Math.abs(player.getX() - startX) < 1.0E-9, "enabling force protection did not stop new pushes");
            PhaseFlightMovementGuard.updatePhaseLockProtection(player, false, true);
            for (int tick = 0; tick < 4; tick++) player.travel(Vec3.ZERO);
            h.assertTrue(player.getX() > startX + 0.1, "disabling force protection did not restore conveyor movement");
        } finally {
            PhaseFlightMovementGuard.clear(player);
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "ae2lt", template = "pigmee_station_empty")
    public static void mobsAndItemsStillReceiveConveyorMovement(GameTestHelper h) {
        BlockState state = conveyor(h, Direction.EAST);
        PhaseLockMovementGameTests.floor(h, state);
        BlockPos pos = h.absolutePos(new BlockPos(3, 0, 3));
        Zombie mob = new Zombie(EntityType.ZOMBIE, h.getLevel());
        mob.setPos(pos.getX() + 0.5, pos.getY() + 0.875, pos.getZ() + 0.5);
        state.entityInside(h.getLevel(), pos, mob);
        h.assertTrue(mob.getDeltaMovement().x > 0 && mob.isPersistenceRequired(),
                "conveyor must still push mobs and mark them persistent");
        var item = new ItemEntity(h.getLevel(), pos.getX() + 0.5, pos.getY() + 0.875,
                pos.getZ() + 0.5, new ItemStack(Items.STONE));
        item.setDeltaMovement(Vec3.ZERO);
        state.entityInside(h.getLevel(), pos, item);
        h.assertTrue(item.getDeltaMovement().x > 0 && item.getAge() < 0,
                "conveyor must still push items and extend their lifetime");
        h.succeed();
    }

    private static BlockState conveyor(GameTestHelper h, Direction direction) {
        var id = ResourceLocation.fromNamespaceAndPath("mob_grinding_utils", "entity_conveyor");
        h.assertTrue(BuiltInRegistries.BLOCK.containsKey(id), "real MGU jar must be loaded for conveyor tests");
        return BuiltInRegistries.BLOCK.get(id).defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, direction);
    }
}
