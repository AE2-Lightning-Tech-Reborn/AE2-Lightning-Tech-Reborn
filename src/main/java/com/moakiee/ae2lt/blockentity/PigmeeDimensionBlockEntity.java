package com.moakiee.ae2lt.blockentity;

import com.moakiee.ae2lt.registry.ModBlockEntities;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class PigmeeDimensionBlockEntity extends BlockEntity {
    private int dimensionId;
    private UUID placer;

    public PigmeeDimensionBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PIGMEE_DIMENSION.get(), pos, state);
    }

    public int dimensionId() { return dimensionId; }
    public UUID placer() { return placer; }
    public void setPlacer(UUID placer) { this.placer = placer; setChanged(); }
    public void bind(int id) {
        dimensionId = Math.max(0, id);
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("DimensionId", dimensionId);
        if (placer != null) tag.putUUID("Placer", placer);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        dimensionId = Math.max(0, tag.getInt("DimensionId"));
        placer = tag.hasUUID("Placer") ? tag.getUUID("Placer") : null;
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
