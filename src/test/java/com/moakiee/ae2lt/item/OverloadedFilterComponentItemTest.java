package com.moakiee.ae2lt.item;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OverloadedFilterComponentItemTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void emptyFilterAlwaysAllowsKeysEvenWhenInverted() {
        var matcher = OverloadedFilterComponentItem.createMatcher(Set.of(), null, true);
        assertTrue(matcher.test(AEItemKey.of(Items.STONE)));
    }

    @Test
    void exactFilterKeepsSnapshotAndInvertsMatches() {
        AEKey stone = AEItemKey.of(Items.STONE);
        AEKey dirt = AEItemKey.of(Items.DIRT);
        Set<AEKey> configured = new HashSet<>(Set.of(stone));
        var allow = OverloadedFilterComponentItem.createMatcher(configured, null, false);
        var deny = OverloadedFilterComponentItem.createMatcher(configured, null, true);
        configured.clear();

        assertTrue(allow.test(stone));
        assertFalse(allow.test(dirt));
        assertTrue(allow.test(stone));
        assertFalse(deny.test(stone));
        assertTrue(deny.test(dirt));
    }
}
