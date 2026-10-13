package com.moakiee.ae2lt.mixin;

import com.moakiee.ae2lt.workstation.PigmeeActorManager;
import com.moakiee.ae2lt.workstation.PigmeeIdentityPacket;
import com.moakiee.ae2lt.workstation.PigmeeServerPlayer;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.neoforged.neoforge.network.bundle.PacketAndPayloadAcceptor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Profile must precede AddEntity, including late join and re-tracking. */
@Mixin(ServerEntity.class)
public abstract class PigmeePairingMixin {
    @Shadow @Final private Entity entity;
    @Inject(method = "sendPairingData", at = @At("HEAD"))
    private void ae2lt$pigmeeIdentity(ServerPlayer viewer, PacketAndPayloadAcceptor<ClientGamePacketListener> packets, CallbackInfo ci) {
        if (entity instanceof PigmeeServerPlayer actor && !viewer.isFakePlayer()) {
            packets.accept(PigmeeActorManager.profilePacket(java.util.List.of(actor)));
            packets.accept(new PigmeeIdentityPacket(actor.getUUID(), actor.variant().ordinal()));
        }
    }
}
