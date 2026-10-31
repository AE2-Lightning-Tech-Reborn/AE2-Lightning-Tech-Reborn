package com.moakiee.ae2lt.api.event;

/** Fired before an atmospheric ionizer adds its generated lightning to the world. */
public final class IonizerLightningEvent extends net.neoforged.bus.api.Event {
    private final net.minecraft.world.entity.LightningBolt lightning;
    private final net.minecraft.core.BlockPos ionizerPos;
    public IonizerLightningEvent(net.minecraft.world.entity.LightningBolt lightning, net.minecraft.core.BlockPos ionizerPos) {
        this.lightning = lightning;
        this.ionizerPos = ionizerPos.immutable();
    }
    public net.minecraft.world.entity.LightningBolt lightning() { return lightning; }
    public net.minecraft.core.BlockPos ionizerPos() { return ionizerPos; }
}
