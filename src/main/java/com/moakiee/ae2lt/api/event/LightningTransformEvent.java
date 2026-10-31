package com.moakiee.ae2lt.api.event;

/** Fired once per server lightning bolt before LT transforms dropped items. */
public final class LightningTransformEvent extends net.neoforged.bus.api.Event {
    private final net.minecraft.server.level.ServerLevel level;
    private final net.minecraft.world.entity.LightningBolt lightning;
    public LightningTransformEvent(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.LightningBolt lightning) {
        this.level = level;
        this.lightning = lightning;
    }
    public net.minecraft.server.level.ServerLevel level() { return level; }
    public net.minecraft.world.entity.LightningBolt lightning() { return lightning; }
}
