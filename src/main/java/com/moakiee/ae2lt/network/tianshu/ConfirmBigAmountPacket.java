package com.moakiee.ae2lt.network.tianshu;

import com.moakiee.ae2lt.crafting.big.BigAmountMenu;
import com.moakiee.ae2lt.network.NetworkInit;
import com.moakiee.thunderbolt.core.storage.big.BigAmounts;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.math.BigInteger;

/** The key and host come exclusively from the player's currently open native amount menu. */
public record ConfirmBigAmountPacket(
        int containerId, BigInteger amount, boolean missing, boolean autoStart)
        implements CustomPacketPayload {
    public static final Type<ConfirmBigAmountPacket> TYPE =
            new Type<>(NetworkInit.id("confirm_big_amount"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ConfirmBigAmountPacket> STREAM_CODEC =
            StreamCodec.ofMember(ConfirmBigAmountPacket::write, ConfirmBigAmountPacket::read);

    private void write(RegistryFriendlyByteBuf b) {
        b.writeVarInt(containerId);
        b.writeByteArray(BigAmounts.nonNegative(amount).toByteArray());
        b.writeBoolean(missing);
        b.writeBoolean(autoStart);
    }

    private static ConfirmBigAmountPacket read(RegistryFriendlyByteBuf b) {
        return new ConfirmBigAmountPacket(
                b.readVarInt(),
                BigAmounts.nonNegative(new BigInteger(b.readByteArray(2049))),
                b.readBoolean(),
                b.readBoolean());
    }

    public Type<ConfirmBigAmountPacket> type() {
        return TYPE;
    }

    public static void handle(ConfirmBigAmountPacket p, IPayloadContext c) {
        c.enqueueWork(
                () -> {
                    var m = c.player().containerMenu;
                    if (m.containerId == p.containerId && m instanceof BigAmountMenu big)
                        big.ae2lt$confirm(p.amount, p.missing, p.autoStart);
                });
    }
}
