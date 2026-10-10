package com.moakiee.ae2lt.network.railgun;
import java.util.function.Supplier;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

/**
 * Server-to-client charged-shot effects. The client resolves the shooter's barrel
 * for the trail origin and falls back to {@code from}.
 *
 * @param shooterId firing player
 * @param from server eye position used as a fallback
 * @param firstHit primary entity or block impact
 * @param chainPath endpoint pairs for arc segments
 * @param tier charge tier (0=HV, 1=EHV1, 2=EHV2, 3=EHV3)
 * @param isMax whether this is a maximum-tier shot
 * @param soundEnabled whether to play railgun sounds
 * @param impactRadius splash radius in blocks for the shockwave
 */
public record RailgunFirePacket(
        UUID shooterId,
        Vec3 from,
        Vec3 firstHit,
        List<Vec3> chainPath,
        int tier,
        boolean isMax,
        boolean soundEnabled,
        float impactRadius) {
public void write(FriendlyByteBuf buf) {
        buf.writeUUID(shooterId);
        buf.writeDouble(from.x); buf.writeDouble(from.y); buf.writeDouble(from.z);
        buf.writeDouble(firstHit.x); buf.writeDouble(firstHit.y); buf.writeDouble(firstHit.z);
        buf.writeVarInt(chainPath.size());
        for (Vec3 v : chainPath) {
            buf.writeDouble(v.x); buf.writeDouble(v.y); buf.writeDouble(v.z);
        }
        buf.writeVarInt(tier);
        buf.writeBoolean(isMax);
        buf.writeBoolean(soundEnabled);
        buf.writeFloat(impactRadius);
    }

    public static RailgunFirePacket decode(FriendlyByteBuf buf) {
        UUID shooterId = buf.readUUID();
        Vec3 from = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        Vec3 first = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        int n = buf.readVarInt();
        List<Vec3> path = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            path.add(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
        }
        int tier = buf.readVarInt();
        boolean isMax = buf.readBoolean();
        boolean soundEnabled = buf.readBoolean();
        float impactRadius = buf.readFloat();
        return new RailgunFirePacket(shooterId, from, first, path, tier, isMax, soundEnabled, impactRadius);
    }

    public static void handle(RailgunFirePacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> RailgunClientBridge.fire(p));
        ctx.setPacketHandled(true);
    }

}
