package com.moakiee.ae2lt.item.railgun;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/** Shared settlement mode for both railgun execution modules. */
public enum RailgunExecutionMode implements StringRepresentable {
    OFF("off"),
    NORMAL("normal"),
    FORCED("forced"),
    PERCENTAGE("percentage");

    public static final Codec<RailgunExecutionMode> CODEC =
            StringRepresentable.fromEnum(RailgunExecutionMode::values);

    private final String serializedName;

    RailgunExecutionMode(String serializedName) {
        this.serializedName = serializedName;
    }

    public RailgunExecutionMode next() {
        return switch (this) {
            case OFF -> PERCENTAGE;
            case PERCENTAGE -> NORMAL;
            case NORMAL -> FORCED;
            case FORCED -> OFF;
        };
    }

    public RailgunExecutionMode next(boolean supportsPercentage) {
        RailgunExecutionMode next = next();
        return !supportsPercentage && next == PERCENTAGE ? NORMAL : next;
    }

    public RailgunExecutionMode forMultidimensional() {
        return this == PERCENTAGE ? NORMAL : this;
    }

    public boolean entersExecutionFlow() {
        return this == NORMAL || this == FORCED;
    }

    public boolean forcesRemoval() {
        return this == FORCED;
    }

    public String translationKey() {
        return "ae2lt.railgun.config.overload_execution_mode." + serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
