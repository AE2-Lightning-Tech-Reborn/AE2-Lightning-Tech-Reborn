package com.moakiee.ae2lt.network.railgun;
import java.util.function.Supplier;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

/**
 * Server-to-client chain effects, sent only when a sustained beam triggers a chain.
 *
 * @param shooterId firing player
 * @param firstHit primary impact and first arc origin
 * @param chainPath flat endpoint pairs, one pair per arc segment
 * @param soundEnabled whether to play railgun sounds
 * @param ehv whether to use the EHV palette
 */
public record RailgunBeamChainFxPacket(UUID shooterId, Vec3 firstHit, List<Vec3> chainPath, boolean soundEnabled, boolean ehv) {
public void write(FriendlyByteBuf buf) {
        buf.writeUUID(shooterId);
        buf.writeDouble(firstHit.x);
        buf.writeDouble(firstHit.y);
        buf.writeDouble(firstHit.z);
        buf.writeVarInt(chainPath.size());
        for (Vec3 v : chainPath) {
            buf.writeDouble(v.x);
            buf.writeDouble(v.y);
            buf.writeDouble(v.z);
        }
        buf.writeBoolean(soundEnabled);
        buf.writeBoolean(ehv);
    }

    public static RailgunBeamChainFxPacket decode(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        Vec3 first = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        int n = buf.readVarInt();
        List<Vec3> path = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            path.add(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
        }
        boolean soundEnabled = buf.readBoolean();
        boolean ehv = buf.readBoolean();
        return new RailgunBeamChainFxPacket(id, first, path, soundEnabled, ehv);
    }

    public static void handle(RailgunBeamChainFxPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> RailgunClientBridge.beamChainFx(p));
        ctx.setPacketHandled(true);
    }

}
