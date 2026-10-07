package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.item.OverloadedFrequencyCardItem;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

public record FrequencyCardLinkShortcutPacket(BlockPos pos, Direction face, Vec3 hitVec) {
    public static FrequencyCardLinkShortcutPacket decode(FriendlyByteBuf buffer) {
        return new FrequencyCardLinkShortcutPacket(
                buffer.readBlockPos(), buffer.readEnum(Direction.class),
                new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeEnum(face);
        buffer.writeDouble(hitVec.x);
        buffer.writeDouble(hitVec.y);
        buffer.writeDouble(hitVec.z);
    }

    public static void handle(FrequencyCardLinkShortcutPacket payload, Supplier<NetworkEvent.Context> context) {
        var packetContext = context.get();
        packetContext.enqueueWork(() -> {
            ServerPlayer player = packetContext.getSender();
            if (player != null) {
                payload.handleOnServer(player);
            }
        });
        packetContext.setPacketHandled(true);
    }

    private void handleOnServer(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)
                || !level.isLoaded(pos)
                || !player.canReach(pos, 1.0D)
                || !hitInsideBlock()) {
            return;
        }

        var selection = OverloadedFrequencyCardItem.selectLinkCard(player);
        if (selection.ambiguous()) {
            message(player, "ae2lt.frequency_card.link_ambiguous");
            return;
        }
        ItemStack card = selection.selected().orElse(ItemStack.EMPTY);
        if (card.isEmpty()) {
            message(player, "ae2lt.frequency_card.no_link_candidate");
            return;
        }

        FrequencyCardUsePacket.tryLinkWithCard(player, card, level, pos, face, hitVec);
    }

    boolean hitInsideBlock() {
        final double tolerance = 0.01D;
        return hitVec.x >= pos.getX() - tolerance && hitVec.x <= pos.getX() + 1 + tolerance
                && hitVec.y >= pos.getY() - tolerance && hitVec.y <= pos.getY() + 1 + tolerance
                && hitVec.z >= pos.getZ() - tolerance && hitVec.z <= pos.getZ() + 1 + tolerance;
    }

    private static void message(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
    }
}
