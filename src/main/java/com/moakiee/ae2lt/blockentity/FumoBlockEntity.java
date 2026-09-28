package com.moakiee.ae2lt.blockentity;

import com.moakiee.ae2lt.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.Nameable;
import org.jetbrains.annotations.Nullable;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class FumoBlockEntity extends BlockEntity implements Nameable {

    public static final float SPIN_DEGREES_PER_TICK = 6.0F;

    private static final String TAG_SPINNING = "Spinning";

    @Nullable
    private Component customName;

    private boolean spinning;
    private float yRot;
    private float prevYRot;

    public FumoBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FUMO.get(), pos, state);
    }

    @Override
    public Component getName() {
        return customName != null ? customName : getBlockState().getBlock().getName();
    }

    @Override
    @Nullable
    public Component getCustomName() {
        return customName;
    }

    public void setCustomName(@Nullable Component name) {
        customName = name;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void applyImplicitComponents(net.minecraft.core.component.DataComponentGetter input) {
        super.applyImplicitComponents(input);
        customName = input.get(DataComponents.CUSTOM_NAME);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        builder.set(DataComponents.CUSTOM_NAME, customName);
    }

    @Override
    public void removeComponentsFromTag(net.minecraft.world.level.storage.ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("CustomName");
    }

    public boolean isSpinning() {
        return spinning;
    }

    public float getRenderYRot(float partialTick) {
        return prevYRot + (yRot - prevYRot) * partialTick;
    }

    public void toggleSpinning() {
        spinning = !spinning;
        setChanged();
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, FumoBlockEntity be) {
        be.prevYRot = be.yRot;
        if (be.spinning) {
            be.yRot += SPIN_DEGREES_PER_TICK;
        }
    }

    @Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput output) {
        CompoundTag tag = com.moakiee.ae2lt.recipe.compat.LegacyValueIo.writableTag(output);
        HolderLookup.Provider registries = this.level != null ? this.level.registryAccess() : net.minecraft.core.RegistryAccess.EMPTY;
        super.saveAdditional(output);
        if (customName != null) output.store("CustomName", ComponentSerialization.CODEC, customName);
        tag.putBoolean(TAG_SPINNING, spinning);
    }

    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
        CompoundTag tag = com.moakiee.ae2lt.recipe.compat.LegacyValueIo.readableTag(input);
        HolderLookup.Provider registries = input.lookup();
        super.loadAdditional(input);
        spinning = tag.getBooleanOr(TAG_SPINNING, false);
        customName = input.read("CustomName", ComponentSerialization.CODEC).orElse(null);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        if (customName != null) tag.put("CustomName", ComponentSerialization.CODEC.encodeStart(registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), customName).getOrThrow());
        tag.putBoolean(TAG_SPINNING, spinning);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
