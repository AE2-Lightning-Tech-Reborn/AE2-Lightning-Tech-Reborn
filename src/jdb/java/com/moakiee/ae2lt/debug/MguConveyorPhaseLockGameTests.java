package com.moakiee.ae2lt.debug;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.moakiee.ae2lt.celestweave.PhaseFlightMovementGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** Enable ae2lt_mgu only in runs with the real Forge 1.20.1 Mob Grinding Utils jar. */
@GameTestHolder("ae2lt_mgu")
@PrefixGameTestTemplate(false)
public final class MguConveyorPhaseLockGameTests {
    @GameTest(templateNamespace = "ae2lt_mgu", template = "workstation_test")
    public static void lockedPlayerResistsEveryConveyorVelocityWrite(GameTestHelper h) {
        checkPlayer(h, true, true);
    }

    @GameTest(templateNamespace = "ae2lt_mgu", template = "workstation_test")
    public static void ordinaryPlayerStillMoves(GameTestHelper h) {
        checkPlayer(h, false, false);
    }

    @GameTest(templateNamespace = "ae2lt_mgu", template = "workstation_test")
    public static void releasedProtectionRestoresConveyorMovement(GameTestHelper h) {
        checkPlayer(h, false, true);
    }

    private static void checkPlayer(GameTestHelper h, boolean locked, boolean initiallyLocked) {
        Block conveyor = conveyor();
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "MguPhaseTest"));
        BlockPos pos = h.absolutePos(new BlockPos(1, 1, 1));
        Vec3 initial = new Vec3(0.02, 0.03, 0.04);
        try {
            PhaseFlightMovementGuard.updatePhaseLockProtection(player, initiallyLocked, false);
            PhaseFlightMovementGuard.updatePhaseLockProtection(player, locked, false);
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                var state = conveyor.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, direction);
                // Exercise both centering calls as well as the main forward force. The self
                // movement scope allows Entity's generic guard, so this tests the MGU hook.
                for (double offset : new double[] {0.3, 0.5, 0.7}) {
                    PhaseFlightMovementGuard.runAsSelfMovement(player, () -> {
                        player.setPos(pos.getX() + offset, pos.getY() + 0.8, pos.getZ() + offset);
                        player.setDeltaMovement(initial);
                        conveyor.entityInside(state, h.getLevel(), pos, player);
                    });
                    h.assertTrue(player.getDeltaMovement().equals(initial) == locked,
                            "Unexpected conveyor force for " + direction + " at " + offset + ": " + player.getDeltaMovement());
                }
            }
            h.succeed();
        } finally {
            PhaseFlightMovementGuard.clear(player);
            player.discard();
        }
    }

    @GameTest(templateNamespace = "ae2lt_mgu", template = "workstation_test")
    public static void itemStillMovesAndKeepsExtendedLifetime(GameTestHelper h) {
        Block conveyor = conveyor();
        BlockPos pos = h.absolutePos(new BlockPos(1, 1, 1));
        var item = new ItemEntity(h.getLevel(), pos.getX() + 0.5, pos.getY() + 0.8,
                pos.getZ() + 0.5, new ItemStack(Items.DIAMOND));
        item.setDeltaMovement(Vec3.ZERO);
        conveyor.entityInside(conveyor.defaultBlockState(), h.getLevel(), pos, item);
        h.assertTrue(item.getDeltaMovement().horizontalDistance() > 0.1, "Conveyor stopped transporting items");
        h.assertTrue(item.getAge() == -6000, "Conveyor lost its extended item lifetime");
        item.discard();
        h.succeed();
    }

    private static Block conveyor() {
        return ForgeRegistries.BLOCKS.getValues().stream()
                .filter(block -> block.getClass().getName().equals("mob_grinding_utils.blocks.BlockEntityConveyor"))
                .findFirst().orElseThrow(() -> new AssertionError("Real MGU entity conveyor is required"));
    }
}
