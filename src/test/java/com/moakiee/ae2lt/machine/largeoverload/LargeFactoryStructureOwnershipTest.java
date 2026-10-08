package com.moakiee.ae2lt.machine.largeoverload;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

class LargeFactoryStructureOwnershipTest {
    @Test
    void intersectingFactoriesCannotAcquirePartialMembership() {
        var ownership = new LargeFactoryStructureOwnership();
        var first = ownership.claim(UUID.randomUUID(), formation(BlockPos.ZERO)).orElseThrow();
        var overlapping = formation(new BlockPos(2, 0, 0));
        UUID secondId = UUID.randomUUID();
        assertTrue(ownership.claim(secondId, overlapping).isEmpty());
        assertTrue(ownership.isCurrent(first));
        ownership.release(first);
        var second = ownership.claim(secondId, overlapping).orElseThrow();
        for (var member : overlapping.members()) assertTrue(ownership.owns(second, member.position()));
    }

    @Test
    void sameMachineIdCannotOwnTwoControllersAndStaleTokenCannotInheritReformation() {
        var ownership = new LargeFactoryStructureOwnership();
        UUID id = UUID.randomUUID();
        var initial = ownership.claim(id, formation(BlockPos.ZERO)).orElseThrow();
        assertTrue(ownership.claim(id, formation(new BlockPos(100, 0, 0))).isEmpty());
        ownership.release(initial);
        var replacement = ownership.claim(id, formation(BlockPos.ZERO)).orElseThrow();
        assertFalse(ownership.isCurrent(initial));
        assertFalse(ownership.owns(initial, BlockPos.ZERO));
        ownership.release(initial);
        assertTrue(ownership.owns(replacement, BlockPos.ZERO));
    }

    @Test
    void changingCentralCoreInvalidatesImmediatelyButNearbyChangesDoNot() {
        var ownership = new LargeFactoryStructureOwnership();
        var binding = ownership.claim(UUID.randomUUID(), formation(BlockPos.ZERO)).orElseThrow();
        assertTrue(ownership.invalidateAt(new BlockPos(6, 0, 0)).isEmpty());
        assertTrue(ownership.isCurrent(binding));
        assertEquals(java.util.List.of(binding), ownership.invalidateAt(new BlockPos(0, 0, 1)));
        assertFalse(ownership.isCurrent(binding));
        assertFalse(ownership.owns(binding, BlockPos.ZERO));
    }

    @Test
    void worldTeardownRevokesRetainedTokensBeforeReformation() {
        var ownership = new LargeFactoryStructureOwnership();
        UUID id = UUID.randomUUID();
        var first = ownership.claim(id, formation(BlockPos.ZERO)).orElseThrow();
        var distant = ownership.claim(UUID.randomUUID(), formation(new BlockPos(100, 0, 0))).orElseThrow();
        assertTrue(first.isCurrent());
        assertTrue(first.owns(BlockPos.ZERO));
        ownership.invalidateAll();
        assertFalse(first.isCurrent());
        assertFalse(first.owns(BlockPos.ZERO));
        assertFalse(distant.isCurrent());
        assertNull(ownership.at(BlockPos.ZERO));
        var replacement = ownership.claim(id, formation(BlockPos.ZERO)).orElseThrow();
        assertTrue(replacement.owns(BlockPos.ZERO));
        ownership.release(first);
        assertTrue(replacement.isCurrent());
        assertFalse(first.isCurrent());
    }

    @Test
    void unloadingAnyCoveredChunkReleasesAllMembersOnlyOfAffectedFactories() {
        var ownership = new LargeFactoryStructureOwnership();
        var first = ownership.claim(UUID.randomUUID(), formation(BlockPos.ZERO)).orElseThrow();
        var distant = ownership.claim(UUID.randomUUID(), formation(new BlockPos(100, 0, 0))).orElseThrow();
        assertEquals(java.util.List.of(first), ownership.invalidateChunk(new ChunkPos(-1, 0)));
        assertFalse(ownership.isCurrent(first));
        assertTrue(ownership.isCurrent(distant));
        assertTrue(ownership.claim(UUID.randomUUID(), formation(BlockPos.ZERO)).isPresent());
    }

    private static LargeFactoryStructure.Formation formation(BlockPos controller) {
        Map<BlockPos, LargeFactoryComponent> blocks = new HashMap<>();
        for (var cell : LargeFactoryStructure.cells()) {
            var component = switch (cell.role()) {
                case AIR -> LargeFactoryComponent.AIR;
                case CONTROLLER -> LargeFactoryComponent.CONTROLLER;
                case CORE -> LargeFactoryComponent.CORE_T1;
                case FRAME -> LargeFactoryComponent.FRAME;
                case CASING -> LargeFactoryComponent.CASING;
                case HATCH -> LargeFactoryComponent.PATTERN_HATCH;
            };
            blocks.put(LargeFactoryStructure.worldPosition(controller, cell.localPosition(), Direction.NORTH), component);
        }
        return LargeFactoryStructure.scan(controller, Direction.NORTH, p -> true, blocks::get,
                p -> LargeFactoryStructure.FirmamentReadiness.READY).formation();
    }
}
