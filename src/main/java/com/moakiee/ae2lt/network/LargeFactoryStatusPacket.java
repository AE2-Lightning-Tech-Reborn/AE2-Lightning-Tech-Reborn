package com.moakiee.ae2lt.network;

import java.util.ArrayList;
import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.machine.largeoverload.LargeFactoryMenu;
import com.moakiee.ae2lt.machine.largeoverload.LargeFactorySnapshot;
import net.minecraft.network.FriendlyByteBuf;

public record LargeFactoryStatusPacket(int token, LargeFactorySnapshot snapshot) {
    public static void encode(LargeFactoryStatusPacket packet, FriendlyByteBuf buf) {
        var s = packet.snapshot;
        buf.writeVarInt(packet.token); buf.writeUtf(s.status(), 128); buf.writeBoolean(s.formed());
        buf.writeBoolean(s.passive()); buf.writeBoolean(s.networkEnergy()); buf.writeUtf(s.core(), 64);
        buf.writeVarLong(s.storedEnergy()); buf.writeVarLong(s.energyCapacity());
        buf.writeVarLong(s.remainingOperations()); buf.writeVarLong(s.operationsPerTick());
        buf.writeVarInt(s.pendingTypes()); buf.writeVarInt(s.entryCount()); buf.writeVarInt(s.entryPage());
        buf.writeVarInt(s.rows().size());
        for (var row : s.rows()) {
            buf.writeVarInt(row.index()); buf.writeInt(row.slot()); buf.writeNbt(GenericStack.writeTag(row.output()));
            buf.writeBoolean(row.enabled()); buf.writeUtf(row.status(), 128); buf.writeUtf(row.recipe(), 256);
            buf.writeVarLong(row.operations()); buf.writeVarLong(row.energy()); buf.writeVarLong(row.high()); buf.writeVarLong(row.extreme());
        }
        buf.writeVarInt(s.issues().size());
        for (var issue : s.issues()) { buf.writeUtf(issue.problem(), 128); buf.writeBlockPos(issue.position()); buf.writeUtf(issue.expected(), 64); }
    }
    public static LargeFactoryStatusPacket decode(FriendlyByteBuf buf) {
        int token = buf.readVarInt(); String status = buf.readUtf(128);
        boolean formed = buf.readBoolean(), passive = buf.readBoolean(), network = buf.readBoolean();
        String core = buf.readUtf(64);
        long energy = buf.readVarLong(), capacity = buf.readVarLong(), remaining = buf.readVarLong(), perTick = buf.readVarLong();
        int pending = buf.readVarInt(), entries = buf.readVarInt(), page = buf.readVarInt();
        int rows = buf.readVarInt();
        if (rows < 0 || rows > 6) throw new IllegalArgumentException("Invalid factory row count");
        var rowList = new ArrayList<LargeFactorySnapshot.Row>();
        for (int i = 0; i < rows; i++) rowList.add(new LargeFactorySnapshot.Row(buf.readVarInt(), buf.readInt(),
                GenericStack.readTag(java.util.Objects.requireNonNull(buf.readNbt())), buf.readBoolean(), buf.readUtf(128), buf.readUtf(256),
                buf.readVarLong(), buf.readVarLong(), buf.readVarLong(), buf.readVarLong()));
        int issues = buf.readVarInt();
        if (issues < 0 || issues > 8) throw new IllegalArgumentException("Invalid factory issue count");
        var issueList = new ArrayList<LargeFactorySnapshot.Issue>();
        for (int i = 0; i < issues; i++) issueList.add(new LargeFactorySnapshot.Issue(buf.readUtf(128), buf.readBlockPos(), buf.readUtf(64)));
        return new LargeFactoryStatusPacket(token, new LargeFactorySnapshot(status, formed, passive, network, core,
                energy, capacity, remaining, perTick, pending, entries, page, rowList, issueList));
    }
    public static void handle(LargeFactoryStatusPacket packet, java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> context) {
        var ctx = context.get();
        ctx.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> acceptClient(packet)));
        ctx.setPacketHandled(true);
    }
    private static void acceptClient(LargeFactoryStatusPacket packet) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof LargeFactoryMenu menu && menu.containerId == packet.token) menu.snapshot = packet.snapshot;
    }
}
