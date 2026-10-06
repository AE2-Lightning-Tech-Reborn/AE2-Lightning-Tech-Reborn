package com.moakiee.ae2lt.network.hub;

import com.moakiee.ae2lt.menu.hub.DeviceHubMenu;
import com.moakiee.ae2lt.network.NetworkInit;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AddonHubActionPacket(int containerId, UUID session, ResourceLocation page,
        ResourceLocation setting, int expectedValue, int value) implements CustomPacketPayload {
    public static final Type<AddonHubActionPacket> TYPE = new Type<>(NetworkInit.id("addon_hub_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AddonHubActionPacket> STREAM_CODEC = StreamCodec.ofMember(
            AddonHubActionPacket::write, b -> new AddonHubActionPacket(b.readVarInt(), b.readUUID(),
                    b.readResourceLocation(), b.readResourceLocation(), b.readInt(), b.readInt()));
    public Type<AddonHubActionPacket> type() { return TYPE; }
    private void write(RegistryFriendlyByteBuf b) {
        b.writeVarInt(containerId); b.writeUUID(session); b.writeResourceLocation(page); b.writeResourceLocation(setting);
        b.writeInt(expectedValue); b.writeInt(value);
    }
    public static void handle(AddonHubActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof DeviceHubMenu menu) {
                menu.configureAddon(player, packet);
            }
        });
    }
}
