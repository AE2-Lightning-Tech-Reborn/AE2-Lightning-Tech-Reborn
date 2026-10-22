package com.moakiee.ae2lt.crafting.big;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.stacks.AEItemKey;
import com.moakiee.thunderbolt.core.crafting.big.BigLongSlice;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BigCraftingTransferTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(),
                    new net.minecraftforge.fml.loading.EarlyLoadingException("test", null, List.of()));
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void hugeEscrowStillFundsOneCopyAndLongBoundedBatches() {
        var raw = AEItemKey.of(Items.IRON_INGOT);
        var out = AEItemKey.of(Items.GOLD_INGOT);
        var huge = BigInteger.TEN.pow(100);
        for (long capacity : new long[] {1, Long.MAX_VALUE}) {
            var transfer = BigCraftingTransfer.from(BigLongSlice.plan(huge, Map.of(raw, huge.multiply(BigInteger.TWO)),
                    Map.of(raw, 2L), Map.of(), Map.of(out, 3L), Map.of(), capacity));
            long copies = Math.min(capacity, Long.MAX_VALUE / 3);
            assertEquals(BigInteger.valueOf(copies), transfer.copies());
            assertEquals(BigInteger.valueOf(copies).multiply(BigInteger.TWO), transfer.inputs().get(raw));
            assertEquals(BigInteger.valueOf(copies).multiply(BigInteger.valueOf(3)), transfer.outputs().get(out));
        }
    }

    @Test
    void sharedInputsAreSuppliedOnceAcrossPlanningAndPartialAcceptance() {
        var raw = AEItemKey.of(Items.IRON_INGOT);
        var mold = AEItemKey.of(Items.BUCKET);
        var out = AEItemKey.of(Items.GOLD_INGOT);
        var inputs = Map.<appeng.api.stacks.AEKey, Long>of(raw, 2L, mold, 1L);
        var fixed = Map.<appeng.api.stacks.AEKey, Long>of(mold, 1L);
        var transfer = BigCraftingTransfer.from(BigLongSlice.plan(BigInteger.TEN,
                Map.of(raw, BigInteger.valueOf(20), mold, BigInteger.ONE),
                inputs, fixed, Map.of(out, 3L), Map.of(), 10));
        assertEquals(BigInteger.TEN, transfer.copies());
        assertEquals(BigInteger.ONE, transfer.inputs().get(mold));
        var partial = BigCraftingTransfer.from(BigLongSlice.of(3, inputs, fixed, Map.of(out, 3L), Map.of()));
        assertEquals(BigInteger.ONE, partial.inputs().get(mold));
        assertEquals(BigInteger.valueOf(6), partial.inputs().get(raw));
        assertTrue(BigCraftingTransfer.from(BigLongSlice.of(0, inputs, fixed, Map.of(out, 3L), Map.of())).inputs().isEmpty());
    }
}
