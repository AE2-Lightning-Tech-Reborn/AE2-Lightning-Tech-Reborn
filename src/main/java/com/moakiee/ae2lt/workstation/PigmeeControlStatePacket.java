package com.moakiee.ae2lt.workstation;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PigmeeControlStatePacket(UUID actor, UUID token, boolean active, boolean opening, float health, int food, float yaw, float pitch, int selected, java.util.List<net.minecraft.world.item.ItemStack> hotbar, String actionResult) implements CustomPacketPayload {
    public static final Type<PigmeeControlStatePacket> TYPE = new Type<>(ResourceLocation.parse("ae2lt:pigmee_control_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PigmeeControlStatePacket> STREAM_CODEC = StreamCodec.ofMember(
            (v,b) -> { b.writeUUID(v.actor); b.writeUUID(v.token); b.writeBoolean(v.active); b.writeBoolean(v.opening); b.writeFloat(v.health); b.writeVarInt(v.food); b.writeFloat(v.yaw); b.writeFloat(v.pitch); b.writeVarInt(v.selected);
                for (var stack : v.hotbar) net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC.encode(b, stack);
                b.writeUtf(v.actionResult, 64); },
            b -> {
                var actor = b.readUUID(); var token = b.readUUID(); boolean active = b.readBoolean(), opening = b.readBoolean();
                float health = b.readFloat(); int food = b.readVarInt(); float yaw = b.readFloat(), pitch = b.readFloat(); int selected = b.readVarInt();
                var hotbar = new java.util.ArrayList<net.minecraft.world.item.ItemStack>(9);
                for (int i = 0; i < 9; i++) hotbar.add(net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC.decode(b));
                return new PigmeeControlStatePacket(actor, token, active, opening, health, food, yaw, pitch, selected, hotbar, b.readUtf(64));
            });
    @Override public Type<PigmeeControlStatePacket> type() { return TYPE; }
    static PigmeeControlStatePacket of(PigmeeControlLeases.Lease lease, boolean active) {
        return new PigmeeControlStatePacket(lease.actor.getUUID(), lease.token, active, false, lease.actor.getHealth(), lease.actor.getFoodData().getFoodLevel(), lease.actor.getYRot(), lease.actor.getXRot(), lease.actor.getInventory().selected, hotbar(lease.actor), lease.actionResult);
    }
    static PigmeeControlStatePacket opening(PigmeeControlLeases.Lease lease) {
        return new PigmeeControlStatePacket(lease.actor.getUUID(), lease.token, true, true, lease.actor.getHealth(), lease.actor.getFoodData().getFoodLevel(), lease.actor.getYRot(), lease.actor.getXRot(), lease.actor.getInventory().selected, hotbar(lease.actor), lease.actionResult);
    }
    private static java.util.List<net.minecraft.world.item.ItemStack> hotbar(PigmeeServerPlayer actor) {
        return java.util.stream.IntStream.range(0, 9).mapToObj(i -> actor.getInventory().getItem(i).copy()).toList();
    }
    public static void handle(PigmeeControlStatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> com.moakiee.ae2lt.client.workstation.PigmeeControlScreen.receive(packet));
    }
}
