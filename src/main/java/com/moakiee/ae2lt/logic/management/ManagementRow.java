package com.moakiee.ae2lt.logic.management;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;

public record ManagementRow(UUID id, DeviceLocation target, DeviceLocation source, String kind, int count,
        String state, boolean group, boolean canDisconnect, double[] rates, boolean canConfigure) {
    public ManagementRow(UUID id, DeviceLocation target, DeviceLocation source, String kind, int count,
            String state, boolean group, boolean canDisconnect, double[] rates) {
        this(id, target, source, kind, count, state, group, canDisconnect, rates, false);
    }
    public void write(RegistryFriendlyByteBuf b) {
        b.writeUUID(id); writeLocation(b, target); b.writeNullable(source, ManagementRow::writeLocation);
        b.writeUtf(kind, 32); b.writeVarInt(count); b.writeUtf(state, 32); b.writeBoolean(group); b.writeBoolean(canDisconnect);
        for (double rate : rates) b.writeDouble(rate);
        b.writeBoolean(canConfigure);
    }
    public static ManagementRow read(RegistryFriendlyByteBuf b) {
        var id = b.readUUID(); var target = readLocation(b); var source = b.readNullable(ManagementRow::readLocation);
        String kind = b.readUtf(32); int count = b.readVarInt(); String state = b.readUtf(32);
        boolean group = b.readBoolean(), disconnect = b.readBoolean(); double[] rates = new double[5];
        for (int i = 0; i < rates.length; i++) rates[i] = b.readDouble();
        return new ManagementRow(id, target, source, kind, count, state, group, disconnect, rates, b.readBoolean());
    }
    private static void writeLocation(net.minecraft.network.FriendlyByteBuf b, DeviceLocation d) {
        b.writeUtf(d.dimension(), 256); b.writeBlockPos(d.pos()); b.writeByte(d.face());
        b.writeUtf(d.type(), 256); b.writeUtf(d.name().substring(0, Math.min(128, d.name().length())), 128);
    }
    private static DeviceLocation readLocation(net.minecraft.network.FriendlyByteBuf b) {
        return new DeviceLocation(b.readUtf(256), b.readBlockPos(), b.readByte(), b.readUtf(256), b.readUtf(128));
    }
}
