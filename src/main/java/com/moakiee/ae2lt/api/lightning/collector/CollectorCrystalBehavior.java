package com.moakiee.ae2lt.api.lightning.collector;

import com.moakiee.ae2lt.api.lightning.LightningTier;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/** A registered crystal owns its output preview and cultivation; vanilla cultivation is skipped. */
public interface CollectorCrystalBehavior {
    /** Called on either side. Do not mutate the crystal. */
    OutputRange preview(ItemStack crystal, LightningTier tier, OutputRange baseOutput);

    /**
     * Server-side, after a positive insertion and cooldown commit. May return a
     * replacement crystal (or EMPTY); the collector applies it only if the original
     * stack is still installed. The supplied stack is live. Do not throw after side effects.
     */
    default ItemStack onCaptured(Capture context, ItemStack crystal) { return crystal; }

    record OutputRange(int min, int max) {
        public OutputRange {
            if (min < 0 || max < min || (long) max - min + 1 > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("invalid collector output range");
            }
        }
    }

    record Capture(ServerLevel level, BlockPos collectorPos, LightningTier tier,
            boolean naturalWeather, long requestedAmount, long insertedAmount,
            Supplier<ItemStack> installedCrystal) {
        public Capture { collectorPos = collectorPos.immutable(); }
    }
}
