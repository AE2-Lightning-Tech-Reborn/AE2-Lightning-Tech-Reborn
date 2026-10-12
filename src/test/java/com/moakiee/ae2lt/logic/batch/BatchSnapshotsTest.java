package com.moakiee.ae2lt.logic.batch;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BatchSnapshotsTest {
    @BeforeAll
    static void bootstrap() {
        if (net.minecraftforge.fml.loading.LoadingModList.get() == null) {
            net.minecraftforge.fml.loading.LoadingModList.of(List.of(), List.of(), null);
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void outputAmountsBoundCapacityBeforeDispatch() {
        var outputs = List.of(stack(2), stack(3));
        long capacity = BatchSnapshots.safeCapacity(outputs, Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE / 3, capacity);
        assertEquals(7, BatchSnapshots.safeCapacity(outputs, 7));
        var receipt = BatchSnapshots.multiply(outputs, capacity);
        assertEquals(capacity * 2, receipt.get(0).amount());
        assertEquals(capacity * 3, receipt.get(1).amount());
        assertThrows(ArithmeticException.class, () -> BatchSnapshots.multiply(outputs, capacity + 1));
    }

    @Test
    void zeroOutputsDoNotDivideByZeroAndNegativeOutputsDisableDispatch() {
        assertEquals(9, BatchSnapshots.safeCapacity(List.of(stack(0)), 9));
        assertEquals(0, BatchSnapshots.safeCapacity(List.of(stack(-1)), 9));
        assertEquals(0, BatchSnapshots.safeCapacity(List.of(stack(1)), -1));
    }

    @Test
    void zeroAcceptanceHasNoOutputsAndPositiveReceiptsAreImmutable() {
        var outputs = List.of(stack(4));
        assertTrue(BatchSnapshots.multiply(outputs, 0).isEmpty());
        var receipt = BatchSnapshots.multiply(outputs, 3);
        assertEquals(12, receipt.get(0).amount());
        assertEquals(4, outputs.get(0).amount());
        assertSame(outputs.get(0).what(), receipt.get(0).what());
        assertThrows(UnsupportedOperationException.class, () -> receipt.add(stack(1)));
    }

    @Test
    void emptyOutputsKeepTheProviderCapacity() {
        assertEquals(Long.MAX_VALUE, BatchSnapshots.safeCapacity(List.of(), Long.MAX_VALUE));
        assertTrue(BatchSnapshots.multiply(List.of(), 1).isEmpty());
    }

    private static GenericStack stack(long amount) {
        return new GenericStack(AEItemKey.of(Items.STICK), amount);
    }
}
