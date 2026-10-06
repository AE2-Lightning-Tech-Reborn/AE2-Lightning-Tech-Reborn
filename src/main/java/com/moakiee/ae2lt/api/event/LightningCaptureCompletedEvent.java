package com.moakiee.ae2lt.api.event;

import com.moakiee.ae2lt.api.lightning.LightningTier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.Event;

/**
 * Non-cancellable Forge event after positive insertion, cooldown and crystal
 * processing. Zero insertion/cancelled attempts never post this event. Observers
 * must use insertedAmount, which may be less than requestedAmount.
 */
public final class LightningCaptureCompletedEvent extends Event {
    private final ServerLevel level;
    private final BlockPos collectorPos;
    private final LightningTier tier;
    private final boolean naturalWeather;
    private final long requestedAmount;
    private final long insertedAmount;

    public LightningCaptureCompletedEvent(ServerLevel level, BlockPos pos, LightningTier tier,
            boolean naturalWeather, long requestedAmount, long insertedAmount) {
        if (insertedAmount <= 0 || requestedAmount < insertedAmount) {
            throw new IllegalArgumentException("invalid committed capture");
        }
        this.level = level;
        this.collectorPos = pos.immutable();
        this.tier = tier;
        this.naturalWeather = naturalWeather;
        this.requestedAmount = requestedAmount;
        this.insertedAmount = insertedAmount;
    }

    public ServerLevel getLevel() { return level; }
    public BlockPos getCollectorPos() { return collectorPos; }
    public LightningTier getTier() { return tier; }
    public boolean isNaturalWeather() { return naturalWeather; }
    public long getRequestedAmount() { return requestedAmount; }
    public long getInsertedAmount() { return insertedAmount; }
}
