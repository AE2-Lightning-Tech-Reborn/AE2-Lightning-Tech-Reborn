package com.moakiee.ae2lt.client.frequency;

import com.mojang.blaze3d.platform.InputConstants;
import com.moakiee.ae2lt.item.OverloadedFrequencyCardItem;
import com.moakiee.ae2lt.item.TerminalCardAccess;
import com.moakiee.ae2lt.network.FrequencyCardUsePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class FrequencyCardBulkInput {
    private FrequencyCardBulkInput() {}

    /** Physical Ctrl also works on macOS, where Screen.hasControlDown normally means Command. */
    public static boolean controlDown() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    @SubscribeEvent
    public static void onUse(InputEvent.InteractionKeyMappingTriggered event) {
        var mc = Minecraft.getInstance();
        if (!event.isUseItem() || mc.player == null || mc.screen != null || Screen.hasShiftDown() || !controlDown()
                || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        var held = mc.player.getItemInHand(event.getHand());
        // Held cards already send the modifier with their ordinary use packet.
        if (held.getItem() instanceof OverloadedFrequencyCardItem || !TerminalCardAccess.hasCard(held)) return;
        var point = hit.getLocation();
        PacketDistributor.sendToServer(new FrequencyCardUsePacket(event.getHand(), hit.getBlockPos(), hit.getDirection(),
                point.x, point.y, point.z, false, true));
        event.setCanceled(true);
        event.setSwingHand(true);
    }
}
