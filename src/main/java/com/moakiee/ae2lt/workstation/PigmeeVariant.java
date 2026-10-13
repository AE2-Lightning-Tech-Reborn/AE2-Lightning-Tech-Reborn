package com.moakiee.ae2lt.workstation;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Only the three identities promised by the workstation design. Abilities are a later stage. */
public enum PigmeeVariant {
    NORMAL("pigmee_fumo", GameType.SURVIVAL),
    HYPERDIMENSIONAL("hyperdimensional_pigmee_fumo", GameType.SURVIVAL),
    CREATIVE("creative_pigmee_fumo", GameType.CREATIVE);

    private final ResourceLocation blockId;
    private final GameType gameType;

    PigmeeVariant(String path, GameType gameType) {
        this.blockId = ResourceLocation.fromNamespaceAndPath("ae2lt", path);
        this.gameType = gameType;
    }

    public ResourceLocation blockId() { return blockId; }
    public GameType gameType() { return gameType; }

    @Nullable
    public static PigmeeVariant fromBlock(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        for (PigmeeVariant variant : values()) if (variant.blockId.equals(id)) return variant;
        return null;
    }
}
