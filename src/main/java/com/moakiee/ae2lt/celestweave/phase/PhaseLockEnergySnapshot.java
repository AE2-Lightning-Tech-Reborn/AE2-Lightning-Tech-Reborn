package com.moakiee.ae2lt.celestweave.phase;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Display-only energy values published by the private armor onto its equipped projection. */
public record PhaseLockEnergySnapshot(long capacity, long stored) {
    public static final StreamCodec<RegistryFriendlyByteBuf, PhaseLockEnergySnapshot> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG,
                    PhaseLockEnergySnapshot::capacity,
                    ByteBufCodecs.VAR_LONG,
                    PhaseLockEnergySnapshot::stored,
                    PhaseLockEnergySnapshot::new);

    public PhaseLockEnergySnapshot {
        capacity = Math.max(0L, capacity);
        stored = Math.clamp(stored, 0L, capacity);
    }
}
