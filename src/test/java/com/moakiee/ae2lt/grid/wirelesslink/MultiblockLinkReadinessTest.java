package com.moakiee.ae2lt.grid.wirelesslink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class MultiblockLinkReadinessTest {
    @Test
    void cachedMetadataStillReadsCurrentClusterAndBounds() {
        var owner = new Owner();
        var first = new Cluster();
        owner.cluster = first;
        assertSame(first, MultiblockLinkReadiness.invokeNoArg(owner, "getCluster"));
        assertEquals(BlockPos.ZERO, MultiblockLinkReadiness.invokeNoArg(first, "getBoundsMin"));
        first.min = new BlockPos(1, 2, 3);
        assertEquals(first.min, MultiblockLinkReadiness.invokeNoArg(first, "getBoundsMin"));
        var replacement = new Cluster();
        owner.cluster = replacement;
        assertSame(replacement, MultiblockLinkReadiness.invokeNoArg(owner, "getCluster"));
    }

    @Test
    void inheritedPrivateAndOverriddenHooksKeepTheirSemantics() {
        var owner = new InheritedOwner();
        var cluster = new Cluster();
        owner.cluster = cluster;
        assertSame(cluster, MultiblockLinkReadiness.invokeNoArg(owner, "getCluster"));
        var overridden = new OverriddenCluster();
        assertEquals(new BlockPos(4, 5, 6),
                MultiblockLinkReadiness.invokeNoArg(overridden, "getBoundsMax"));
    }

    @Test
    void missingHooksRemainOptionalOnRepeatedChecks() {
        var unrelated = new Object();
        for (int i = 0; i < 3; i++) {
            assertNull(MultiblockLinkReadiness.invokeNoArg(unrelated, "getCluster"));
            assertNull(MultiblockLinkReadiness.invokeNoArg(unrelated, "getBoundsMin"));
        }
    }

    @Test
    void failingInvocationDoesNotPoisonCachedDiscovery() {
        var cluster = new TransientCluster();
        assertNull(MultiblockLinkReadiness.invokeNoArg(cluster, "getBoundsMin"));
        assertSame(BlockPos.ZERO, MultiblockLinkReadiness.invokeNoArg(cluster, "getBoundsMin"));
    }

    private static class Owner {
        Cluster cluster;
        private Object getCluster() { return cluster; }
    }

    private static final class InheritedOwner extends Owner {}

    private static class Cluster {
        BlockPos min = BlockPos.ZERO;
        private BlockPos getBoundsMin() { return min; }
        protected BlockPos getBoundsMax() { return BlockPos.ZERO; }
    }

    private static final class OverriddenCluster extends Cluster {
        @Override protected BlockPos getBoundsMax() { return new BlockPos(4, 5, 6); }
    }

    private static final class TransientCluster {
        private boolean first = true;
        private BlockPos getBoundsMin() {
            if (first) {
                first = false;
                throw new IllegalStateException("Cluster is not ready");
            }
            return BlockPos.ZERO;
        }
    }
}
