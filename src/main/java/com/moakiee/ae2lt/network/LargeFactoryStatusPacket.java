package com.moakiee.ae2lt.network;

import java.util.ArrayList;
import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.machine.largeoverload.LargeFactoryMenu;
import com.moakiee.ae2lt.machine.largeoverload.LargeFactorySnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record LargeFactoryStatusPacket(int token, LargeFactorySnapshot snapshot) implements CustomPacketPayload {
    public static final Type<LargeFactoryStatusPacket> TYPE = new Type<>(NetworkInit.id("large_factory_status"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LargeFactoryStatusPacket> STREAM_CODEC = StreamCodec.of(LargeFactoryStatusPacket::encode, LargeFactoryStatusPacket::decode);
    private static void encode(RegistryFriendlyByteBuf buf, LargeFactoryStatusPacket packet) {
        var s = packet.snapshot;
        buf.writeVarInt(packet.token); buf.writeUtf(s.status(), 128); buf.writeBoolean(s.formed());
        buf.writeBoolean(s.passive()); buf.writeBoolean(s.networkEnergy()); buf.writeUtf(s.core(), 64);
        buf.writeVarLong(s.storedEnergy()); buf.writeVarLong(s.energyCapacity());
        buf.writeVarLong(s.remainingOperations()); buf.writeVarLong(s.operationsPerTick());
        buf.writeVarInt(s.pendingTypes()); buf.writeVarInt(s.entryCount()); buf.writeVarInt(s.entryPage());
        buf.writeVarInt(s.rows().size());
        for (var row : s.rows()) {
            buf.writeVarInt(row.index()); buf.writeInt(row.slot()); GenericStack.STREAM_CODEC.encode(buf, row.output());
            buf.writeBoolean(row.enabled()); buf.writeUtf(row.status(), 128); buf.writeUtf(row.recipe(), 256);
            buf.writeVarLong(row.operations()); buf.writeVarLong(row.energy()); buf.writeVarLong(row.high()); buf.writeVarLong(row.extreme());
        }
        buf.writeVarInt(s.issues().size());
        for (var issue : s.issues()) { buf.writeUtf(issue.problem(), 128); buf.writeBlockPos(issue.position()); buf.writeUtf(issue.expected(), 64); }
    }
    private static LargeFactoryStatusPacket decode(RegistryFriendlyByteBuf buf) {
        int token = buf.readVarInt(); String status = buf.readUtf(128);
        boolean formed = buf.readBoolean(), passive = buf.readBoolean(), network = buf.readBoolean();
        String core = buf.readUtf(64);
        long energy = buf.readVarLong(), capacity = buf.readVarLong(), remaining = buf.readVarLong(), perTick = buf.readVarLong();
        int pending = buf.readVarInt(), entries = buf.readVarInt(), page = buf.readVarInt();
        int rows = buf.readVarInt();
        if (rows < 0 || rows > 6) throw new IllegalArgumentException("Invalid factory row count");
        var rowList = new ArrayList<LargeFactorySnapshot.Row>();
        for (int i = 0; i < rows; i++) rowList.add(new LargeFactorySnapshot.Row(buf.readVarInt(), buf.readInt(),
                GenericStack.STREAM_CODEC.decode(buf), buf.readBoolean(), buf.readUtf(128), buf.readUtf(256),
                buf.readVarLong(), buf.readVarLong(), buf.readVarLong(), buf.readVarLong()));
        int issues = buf.readVarInt();
        if (issues < 0 || issues > 8) throw new IllegalArgumentException("Invalid factory issue count");
        var issueList = new ArrayList<LargeFactorySnapshot.Issue>();
        for (int i = 0; i < issues; i++) issueList.add(new LargeFactorySnapshot.Issue(buf.readUtf(128), buf.readBlockPos(), buf.readUtf(64)));
        return new LargeFactoryStatusPacket(token, new LargeFactorySnapshot(status, formed, passive, network, core,
                energy, capacity, remaining, perTick, pending, entries, page, rowList, issueList));
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(LargeFactoryStatusPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof LargeFactoryMenu menu && menu.containerId == packet.token) menu.snapshot = packet.snapshot;
        });
    }
}
