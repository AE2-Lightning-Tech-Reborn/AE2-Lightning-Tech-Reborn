package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.item.OverloadedFrequencyCardItem;
import com.moakiee.ae2lt.item.TerminalCardAccess;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import appeng.menu.AEBaseMenu;
import appeng.menu.locator.ItemMenuHostLocator;

public record ToggleFrequencyCardAutoConnectPacket(Optional<InteractionHand> hand, boolean terminalCard, boolean energy)
        implements CustomPacketPayload {
    public static final Type<ToggleFrequencyCardAutoConnectPacket> TYPE =
            new Type<>(NetworkInit.id("toggle_frequency_card_auto_connect"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleFrequencyCardAutoConnectPacket> STREAM_CODEC =
            StreamCodec.ofMember(ToggleFrequencyCardAutoConnectPacket::write, ToggleFrequencyCardAutoConnectPacket::decode);

    @Override
    public Type<ToggleFrequencyCardAutoConnectPacket> type() {
        return TYPE;
    }

    public static ToggleFrequencyCardAutoConnectPacket forHand(InteractionHand hand) {
        return new ToggleFrequencyCardAutoConnectPacket(Optional.of(hand), false, false);
    }

    public static ToggleFrequencyCardAutoConnectPacket forPreferredCard() {
        return new ToggleFrequencyCardAutoConnectPacket(Optional.empty(), false, false);
    }

    public static ToggleFrequencyCardAutoConnectPacket forTerminalCard() {
        return forTerminalCard(false);
    }

    public static ToggleFrequencyCardAutoConnectPacket forTerminalCard(boolean energy) {
        return new ToggleFrequencyCardAutoConnectPacket(Optional.empty(), true, energy);
    }

    public static ToggleFrequencyCardAutoConnectPacket decode(RegistryFriendlyByteBuf buf) {
        boolean terminalCard = buf.readBoolean();
        Optional<InteractionHand> hand = buf.readBoolean()
                ? Optional.of(buf.readEnum(InteractionHand.class))
                : Optional.empty();
        return new ToggleFrequencyCardAutoConnectPacket(hand, terminalCard, buf.readBoolean());
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(terminalCard);
        buf.writeBoolean(hand.isPresent());
        hand.ifPresent(value -> buf.writeEnum(value));
        buf.writeBoolean(energy);
    }

    public static void handle(ToggleFrequencyCardAutoConnectPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                payload.handleOnServer(player);
            }
        });
    }

    private void handleOnServer(ServerPlayer player) {
        if (energy && !OverloadedFrequencyCardItem.isAutoConnectEnergyAvailable()) {
            player.displayClientMessage(Component.translatable("ae2lt.frequency_card.auto_energy_requires_appflux")
                    .withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        if (terminalCard) {
            handleTerminalCard(player);
            return;
        }

        ItemStack stack;
        if (hand.isPresent()) {
            stack = player.getItemInHand(hand.get());
        } else {
            var selection = OverloadedFrequencyCardItem.selectToggleCard(player);
            if (selection.ambiguous()) {
                player.displayClientMessage(
                        Component.translatable("ae2lt.frequency_card.auto_ambiguous")
                                .withStyle(ChatFormatting.RED),
                        true);
                return;
            }
            stack = selection.selected().orElse(ItemStack.EMPTY);
            if (stack.isEmpty()) {
                player.displayClientMessage(
                        Component.translatable("ae2lt.frequency_card.no_toggle_candidate")
                                .withStyle(ChatFormatting.RED),
                        true);
                return;
            }
        }
        if (!(stack.getItem() instanceof OverloadedFrequencyCardItem)) return;

        var data = OverloadedFrequencyCardItem.getData(stack);
        if (data.isBound() && !data.canBeUsedBy(player.getUUID())) {
            player.displayClientMessage(
                    Component.translatable("ae2lt.frequency_card.card_owner_mismatch")
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        var updated = energy ? data.toggleAutoConnectEnergy() : data.toggleAutoConnect();
        OverloadedFrequencyCardItem.setData(stack, updated);
        boolean enabled = energy ? updated.autoConnectEnergy() : updated.autoConnect();
        messageAutoConnectState(player, enabled);
    }

    private void handleTerminalCard(ServerPlayer player) {
        if (!(player.containerMenu instanceof AEBaseMenu aeMenu)
                || !(aeMenu.getLocator() instanceof ItemMenuHostLocator locator)
                || !aeMenu.stillValid(player)) {
            player.displayClientMessage(
                    Component.translatable("ae2lt.gui.error.rejected").withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        ItemStack terminal = locator.locateItem(player);
        if (!TerminalCardAccess.hasCard(terminal)) {
            player.displayClientMessage(
                    Component.translatable("ae2lt.frequency_card.terminal_no_card")
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        var data = TerminalCardAccess.readCardData(terminal);
        if (data.isBound() && !data.canBeUsedBy(player.getUUID())) {
            player.displayClientMessage(
                    Component.translatable("ae2lt.frequency_card.card_owner_mismatch")
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        if (!TerminalCardAccess.updateCard(terminal, cardData -> energy ? cardData.toggleAutoConnectEnergy() : cardData.toggleAutoConnect())) {
            player.displayClientMessage(
                    Component.translatable("ae2lt.frequency_card.terminal_no_card")
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        var updated = TerminalCardAccess.readCardData(terminal);
        messageAutoConnectState(player, energy ? updated.autoConnectEnergy() : updated.autoConnect());
    }

    private void messageAutoConnectState(ServerPlayer player, boolean enabled) {
        player.displayClientMessage(
                Component.translatable("ae2lt.frequency_card.auto_" + (energy ? "energy_" : "")
                                + (enabled ? "enabled" : "disabled"))
                        .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.YELLOW),
                true);
    }
}
