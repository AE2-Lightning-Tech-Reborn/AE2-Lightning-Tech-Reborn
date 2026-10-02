package com.moakiee.ae2lt.client;

import org.lwjgl.glfw.GLFW;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.network.FrequencyCardLinkShortcutPacket;
import com.moakiee.ae2lt.network.ToggleFrequencyCardAutoConnectPacket;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = AE2LightningTech.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FrequencyCardKeyMappings {
    private static final String CATEGORY = "key.categories.ae2lt";

    private static final KeyMapping TOGGLE_AUTO_CONNECT = new KeyMapping(
            "key.ae2lt.toggle_frequency_card_auto_connect",
            GLFW.GLFW_KEY_UNKNOWN,
            CATEGORY);
    private static final KeyMapping TOGGLE_LINK = new KeyMapping(
            "key.ae2lt.toggle_frequency_card_link",
            GLFW.GLFW_KEY_UNKNOWN,
            CATEGORY);

    private FrequencyCardKeyMappings() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_AUTO_CONNECT);
        event.register(TOGGLE_LINK);
    }

    @EventBusSubscriber(modid = AE2LightningTech.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
    public static final class RuntimeHandler {
        private RuntimeHandler() {
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            while (TOGGLE_AUTO_CONNECT.consumeClick()) {
                PacketDistributor.sendToServer(ToggleFrequencyCardAutoConnectPacket.forPreferredCard());
            }
            while (TOGGLE_LINK.consumeClick()) {
                var minecraft = Minecraft.getInstance();
                if (minecraft.player == null || minecraft.level == null || minecraft.screen != null
                        || !(minecraft.hitResult instanceof BlockHitResult hit)) {
                    continue;
                }
                PacketDistributor.sendToServer(new FrequencyCardLinkShortcutPacket(
                        hit.getBlockPos(), hit.getDirection(), hit.getLocation()));
            }
        }
    }
}
