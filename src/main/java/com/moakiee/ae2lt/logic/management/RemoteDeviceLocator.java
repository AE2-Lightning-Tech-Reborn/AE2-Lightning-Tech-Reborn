package com.moakiee.ae2lt.logic.management;

import appeng.api.parts.IPartHost;
import appeng.menu.locator.MenuHostLocator;
import appeng.menu.locator.MenuLocators;
import appeng.parts.AEBasePart;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Client-only detached host snapshot; never installs a block, loads a chunk or accepts client NBT on the server. */
public final class RemoteDeviceLocator implements MenuHostLocator {
    private static final int MAX_UPDATE_BYTES = 262_144;
    private final RemoteDeviceSession session;
    private ResourceLocation blockEntityType;
    private BlockState state;
    private BlockPos pos;
    private int face;
    private CompoundTag update;
    private Object clientHost;

    public RemoteDeviceLocator(RemoteDeviceSession session) { this.session = session; }
    private RemoteDeviceLocator(FriendlyByteBuf buffer) {
        session = null;
        blockEntityType = buffer.readResourceLocation(); state = Block.stateById(buffer.readVarInt());
        pos = buffer.readBlockPos(); face = buffer.readByte();
        if (face < -1 || face > 5) throw new IllegalArgumentException("Invalid remote part side");
        byte[] bytes = buffer.readByteArray(MAX_UPDATE_BYTES);
        var data = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
        try { update = data.readNbt(); } finally { data.release(); }
    }
    public static void register() { MenuLocators.register(RemoteDeviceLocator.class, RemoteDeviceLocator::write, RemoteDeviceLocator::new); }
    public RemoteDeviceSession session() { return session; }
    public boolean valid(Player player) { return player.level().isClientSide ? session == null : session != null && session.valid(player); }
    private byte[] snapshot() {
        var be = RemoteDeviceSession.blockEntity(session.owner());
        var data = new FriendlyByteBuf(Unpooled.buffer());
        try {
            data.writeNbt(be.getUpdateTag(be.getLevel().registryAccess()));
            if (data.readableBytes() > MAX_UPDATE_BYTES) return null;
            byte[] bytes = new byte[data.readableBytes()]; data.readBytes(bytes); return bytes;
        } finally { data.release(); }
    }
    public boolean canSerialize() { return session != null && snapshot() != null; }
    private void write(FriendlyByteBuf buffer) {
        if (session == null) throw new IllegalStateException("Remote locators can only be issued by the server");
        var be = RemoteDeviceSession.blockEntity(session.owner());
        byte[] bytes = snapshot();
        if (bytes == null) throw new IllegalStateException("Remote host update exceeds menu limit");
        buffer.writeResourceLocation(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()));
        buffer.writeVarInt(Block.getId(be.getBlockState())); buffer.writeBlockPos(be.getBlockPos());
        buffer.writeByte(session.owner() instanceof AEBasePart part && part.getSide() != null ? part.getSide().ordinal() : -1);
        buffer.writeByteArray(bytes);
    }
    @Override public <T> T locate(Player player, Class<T> type) {
        if (!player.level().isClientSide) return valid(player) && type.isInstance(session.owner()) ? type.cast(session.owner()) : null;
        if (session != null) return null;
        if (clientHost == null) {
            var beType = BuiltInRegistries.BLOCK_ENTITY_TYPE.get(blockEntityType);
            BlockEntity be = beType == null ? null : beType.create(pos, state);
            if (be == null) return null;
            be.setLevel(player.level());
            if (update != null) be.loadWithComponents(update, player.registryAccess());
            clientHost = face < 0 ? be : be instanceof IPartHost host ? host.getPart(Direction.values()[face]) : null;
        }
        return type.isInstance(clientHost) ? type.cast(clientHost) : null;
    }
}
