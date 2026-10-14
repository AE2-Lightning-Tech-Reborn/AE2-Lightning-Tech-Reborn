package com.moakiee.ae2lt.celestweave.phase;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Display-only energy values published by the private armor onto its equipped projection. */
public record PhaseLockEnergySnapshot(long capacity, long stored) {
    public static final Codec<PhaseLockEnergySnapshot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("capacity").forGetter(PhaseLockEnergySnapshot::capacity),
            Codec.LONG.fieldOf("stored").forGetter(PhaseLockEnergySnapshot::stored))
            .apply(instance, PhaseLockEnergySnapshot::new));

    public PhaseLockEnergySnapshot {
        capacity = Math.max(0L, capacity);
        stored = Math.max(0L, Math.min(stored, capacity));
    }
}
