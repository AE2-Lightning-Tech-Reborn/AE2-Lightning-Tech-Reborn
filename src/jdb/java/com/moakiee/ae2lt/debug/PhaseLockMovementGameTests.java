package com.moakiee.ae2lt.debug;

import java.util.UUID;
import java.util.function.Consumer;

import com.moakiee.ae2lt.celestweave.PhaseFlightMovementGuard;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Paired native travel/collision runs, with only phase-lock force protection differing. */
@GameTestHolder("ae2lt_phase_movement")
@PrefixGameTestTemplate(false)
public final class PhaseLockMovementGameTests {
    @GameTest(templateNamespace = "ae2lt", template = "pigmee_station_empty")
    public static void walkingWallCollisionAndFrictionMatchUnprotectedPlayer(GameTestHelper h) {
        floor(h, Blocks.STONE.defaultBlockState());
        for (int z = 0; z < 7; z++) for (int y = 1; y < 4; y++) {
            h.setBlock(new BlockPos(4, y, z), Blocks.STONE);
        }
        paired(h, new Vec3(2.5, 1, 3.5), new Vec3(0, -0.08, 0), 30,
                p -> p.travel(new Vec3(1, 0, 0)), p -> {
                    h.assertTrue(p.horizontalCollision && p.onGround(), "must reach the wall and remain grounded");
                    h.assertTrue(Math.abs(p.getDeltaMovement().x) < 1.0E-9, "wall must clear horizontal velocity");
                });
        h.succeed();
    }

    @GameTest(templateNamespace = "ae2lt", template = "pigmee_station_empty")
    public static void sprintJumpCeilingAndLandingMatchUnprotectedPlayer(GameTestHelper h) {
        floor(h, Blocks.STONE.defaultBlockState());
        for (int x = 0; x < 7; x++) for (int z = 0; z < 7; z++) {
            h.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
        }
        var ordinary = player(h, new Vec3(3.5, 1, 2.5));
        var locked = player(h, new Vec3(3.5, 1, 2.5));
        try {
            PhaseFlightMovementGuard.updatePhaseLockProtection(locked, true, true);
            for (var p : new ProbePlayer[]{ordinary, locked}) {
                p.setSprinting(true);
                p.jumpFromGround();
            }
            same(h, ordinary, locked);
            h.assertTrue(locked.getDeltaMovement().y > 0 && locked.getDeltaMovement().z > 0,
                    "ground jump and sprint impulse must both survive");
            double peak = locked.getY();
            boolean ceilingCollision = false;
            for (int tick = 0; tick < 30; tick++) {
                ordinary.travel(Vec3.ZERO);
                locked.travel(Vec3.ZERO);
                same(h, ordinary, locked);
                peak = Math.max(peak, locked.getY());
                ceilingCollision |= locked.verticalCollision && !locked.onGround();
            }
            h.assertTrue(peak > h.absolutePos(BlockPos.ZERO).getY() + 1 && ceilingCollision,
                    "jump must rise and hit the ceiling");
            h.assertTrue(locked.onGround(), "jump must land on the solid floor");
        } finally {
            PhaseFlightMovementGuard.clear(locked);
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "ae2lt", template = "pigmee_station_empty")
    public static void slimeBounceAndStepFrictionMatchUnprotectedPlayer(GameTestHelper h) {
        floor(h, Blocks.SLIME_BLOCK.defaultBlockState());
        paired(h, new Vec3(3.5, 1.1, 3.5), new Vec3(0.1, -0.5, 0), 1,
                p -> p.travel(Vec3.ZERO), p ->
                        h.assertTrue(p.getDeltaMovement().y > 0.2, "slime landing must still bounce"));
        paired(h, new Vec3(3.5, 1, 3.5), new Vec3(0.2, -0.08, 0), 1,
                p -> p.travel(Vec3.ZERO), p ->
                        h.assertTrue(p.getDeltaMovement().x > 0 && p.getDeltaMovement().x < 0.1,
                                "slime step callback must still slow horizontal movement"));
        h.succeed();
    }

    @GameTest(templateNamespace = "ae2lt", template = "pigmee_station_empty")
    public static void honeySideSlideMatchesUnprotectedPlayer(GameTestHelper h) {
        floor(h, Blocks.STONE.defaultBlockState());
        for (int y = 1; y < 5; y++) h.setBlock(new BlockPos(3, y, 3), Blocks.HONEY_BLOCK);
        paired(h, new Vec3(4.22, 3.3, 3.5), new Vec3(0, -0.4, 0), 1,
                p -> p.travel(Vec3.ZERO), p ->
                        h.assertTrue(p.getDeltaMovement().y > -0.2 && p.getDeltaMovement().y < 0,
                                "honey collision callback must still limit falling speed"));
        h.succeed();
    }

    @GameTest(templateNamespace = "ae2lt", template = "pigmee_station_empty")
    public static void cobwebMovementMatchesUnprotectedPlayer(GameTestHelper h) {
        floor(h, Blocks.STONE.defaultBlockState());
        h.setBlock(new BlockPos(3, 1, 3), Blocks.COBWEB);
        paired(h, new Vec3(3.5, 1, 3.5), new Vec3(0.2, -0.08, 0), 3,
                p -> p.travel(Vec3.ZERO), p ->
                        h.assertTrue(p.getDeltaMovement().horizontalDistance() < 0.01,
                                "web collision must still consume movement"));
        h.succeed();
    }

    private static void paired(GameTestHelper h, Vec3 pos, Vec3 velocity, int ticks,
            Consumer<ProbePlayer> step, Consumer<ProbePlayer> check) {
        var ordinary = player(h, pos);
        var locked = player(h, pos);
        ordinary.setDeltaMovement(velocity);
        locked.setDeltaMovement(velocity);
        try {
            PhaseFlightMovementGuard.updatePhaseLockProtection(locked, true, true);
            for (int tick = 0; tick < ticks; tick++) {
                step.accept(ordinary);
                step.accept(locked);
                same(h, ordinary, locked);
            }
            check.accept(locked);
        } finally {
            PhaseFlightMovementGuard.clear(locked);
        }
    }

    private static void same(GameTestHelper h, ProbePlayer expected, ProbePlayer actual) {
        h.assertTrue(expected.position().distanceToSqr(actual.position()) < 1.0E-12,
                "protection changed native position: " + expected.position() + " / " + actual.position());
        h.assertTrue(expected.getDeltaMovement().distanceToSqr(actual.getDeltaMovement()) < 1.0E-12,
                "protection changed native velocity: " + expected.getDeltaMovement() + " / " + actual.getDeltaMovement());
        h.assertTrue(expected.onGround() == actual.onGround()
                        && expected.horizontalCollision == actual.horizontalCollision
                        && expected.verticalCollision == actual.verticalCollision,
                "protection changed native collision flags");
        h.assertTrue(!PhaseFlightMovementGuard.isSelfMovementAuthorized(actual), "travel authorization leaked");
    }

    static void floor(GameTestHelper h, BlockState block) {
        for (int x = 0; x < 7; x++) for (int z = 0; z < 7; z++) {
            h.setBlock(new BlockPos(x, 0, z), block);
            for (int y = 1; y < 6; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
    }

    static ProbePlayer player(GameTestHelper h, Vec3 relativePos) {
        var player = new ProbePlayer(h.getLevel(), h.absolutePos(BlockPos.ZERO));
        Vec3 pos = Vec3.atLowerCornerOf(h.absolutePos(BlockPos.ZERO)).add(relativePos);
        player.setPos(pos.x, pos.y, pos.z);
        player.setOnGround(relativePos.y <= 1);
        player.setSpeed(0.1F);
        return player;
    }

    // A local-controlled Player runs the real Player.travel -> LivingEntity.travel -> Entity.move
    // path in a server test world, including the installed mixins and native block callbacks.
    static final class ProbePlayer extends Player {
        ProbePlayer(ServerLevel level, BlockPos pos) {
            super(level, pos, 0, new GameProfile(UUID.randomUUID(), "phase-movement-test"));
        }

        @Override public boolean isSpectator() { return false; }
        @Override public boolean isCreative() { return false; }
        @Override public boolean isLocalPlayer() { return true; }
    }
}
