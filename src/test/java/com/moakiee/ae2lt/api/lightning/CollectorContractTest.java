package com.moakiee.ae2lt.api.lightning;

import static org.junit.jupiter.api.Assertions.*;
import com.moakiee.ae2lt.api.event.LightningCaptureCompletedEvent;
import com.moakiee.ae2lt.api.lightning.collector.CollectorCrystalBehavior.OutputRange;
import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.ICancellableEvent;
import org.junit.jupiter.api.Test;

class CollectorContractTest {
    @Test void onlyPositiveCommittedAmountsCanBeReported() {
        var pos = new BlockPos.MutableBlockPos(1, 2, 3);
        var event = new LightningCaptureCompletedEvent(null, pos, LightningTier.HIGH_VOLTAGE, false, 40, 7);
        pos.set(9, 9, 9);
        assertEquals(new BlockPos(1, 2, 3), event.getCollectorPos());
        assertEquals(40, event.getRequestedAmount()); assertEquals(7, event.getInsertedAmount());
        assertFalse(ICancellableEvent.class.isAssignableFrom(event.getClass()));
        assertThrows(IllegalArgumentException.class,
                () -> new LightningCaptureCompletedEvent(null, pos, LightningTier.HIGH_VOLTAGE, false, 40, 0));
    }

    @Test void outputRangeCannotOverflowVanillaRollBound() {
        assertThrows(IllegalArgumentException.class, () -> new OutputRange(0, Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> new OutputRange(2, 1));
        assertEquals(Integer.MAX_VALUE, new OutputRange(1, Integer.MAX_VALUE).max());
    }
}
