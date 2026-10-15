package com.moakiee.ae2lt.client.gui;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.widgets.TabButton;
import com.moakiee.ae2lt.logic.management.RemoteDeviceLocator;
import com.moakiee.ae2lt.network.ReturnToDeviceManagementPacket;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class RemoteDeviceMenuNavigation {
    @SubscribeEvent public static void addReturnButton(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof AEBaseScreen<?> screen)
                || !(screen.getMenu().getLocator() instanceof RemoteDeviceLocator)) return;
        if (screen.children().stream().anyMatch(b -> b instanceof ReturnButton)) return;
        var button = new ReturnButton(screen.getMenu().containerId);
        // Keep navigation above the upper-left corner, separate from device settings and help.
        button.setPosition(screen.getGuiLeft() - 18, screen.getGuiTop() - 24);
        event.addListener(button);
    }
    public static List<Rect2i> includeReturnButton(AEBaseScreen<?> screen, List<Rect2i> zones) {
        for (var child : screen.children()) {
            if (child instanceof ReturnButton button && button.visible) {
                var result = new ArrayList<>(zones);
                result.add(new Rect2i(button.getX(), button.getY(), button.getWidth(), button.getHeight()));
                return result;
            }
        }
        return zones;
    }
    private static final class ReturnButton extends TabButton {
        private ReturnButton(int menuId) {
            super(Icon.BACK, Component.translatable("ae2lt.management.return_devices"),
                    b -> PacketDistributor.sendToServer(new ReturnToDeviceManagementPacket(menuId)));
            setStyle(Style.BOX);
            setTooltip(Tooltip.create(Component.translatable("ae2lt.management.return_devices")));
        }
    }
}
