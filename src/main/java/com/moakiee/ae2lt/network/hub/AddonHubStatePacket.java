package com.moakiee.ae2lt.network.hub;

import com.moakiee.ae2lt.api.device.DeviceHubPage.Setting;
import com.moakiee.ae2lt.menu.hub.DeviceHubMenu;
import com.moakiee.ae2lt.network.NetworkInit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AddonHubStatePacket(int containerId, UUID session, ResourceLocation page,
        boolean active, List<Setting> settings) implements CustomPacketPayload {
    public static final Type<AddonHubStatePacket> TYPE = new Type<>(NetworkInit.id("addon_hub_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AddonHubStatePacket> STREAM_CODEC =
            StreamCodec.ofMember(AddonHubStatePacket::write, AddonHubStatePacket::read);
    public AddonHubStatePacket { settings = List.copyOf(settings); if (settings.size() > 64) throw new IllegalArgumentException("too many settings"); }
    public Type<AddonHubStatePacket> type() { return TYPE; }
    private void write(RegistryFriendlyByteBuf b) {
        b.writeVarInt(containerId); b.writeUUID(session); b.writeResourceLocation(page); b.writeBoolean(active);
        b.writeVarInt(settings.size());
        for (var s : settings) {
            b.writeResourceLocation(s.id()); b.writeUtf(s.labelTranslationKey(), 256); b.writeUtf(s.displayValue(), 256);
            b.writeInt(s.value()); b.writeInt(s.min()); b.writeInt(s.max()); b.writeBoolean(s.editable());
        }
    }
    private static AddonHubStatePacket read(RegistryFriendlyByteBuf b) {
        int menu = b.readVarInt(); var session = b.readUUID(); var page = b.readResourceLocation(); boolean active = b.readBoolean();
        int count = b.readVarInt(); if (count < 0 || count > 64) throw new IllegalArgumentException("invalid setting count");
        var settings = new ArrayList<Setting>(count);
        for (int i = 0; i < count; i++) settings.add(new Setting(b.readResourceLocation(), b.readUtf(256), b.readUtf(256),
                b.readInt(), b.readInt(), b.readInt(), b.readBoolean()));
        return new AddonHubStatePacket(menu, session, page, active, settings);
    }
    public static void handle(AddonHubStatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof DeviceHubMenu menu && menu.containerId == packet.containerId) {
                menu.receiveAddonState(packet);
            }
        });
    }
}
