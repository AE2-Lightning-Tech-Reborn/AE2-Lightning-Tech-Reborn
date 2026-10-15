package com.moakiee.ae2lt.client.gui;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.widgets.IconButton;
import com.moakiee.ae2lt.logic.management.RemoteDeviceLocator;
import com.moakiee.ae2lt.mixin.client.AEBaseScreenAccessor;
import com.moakiee.ae2lt.mixin.client.VerticalButtonBarAccessor;
import com.moakiee.ae2lt.network.ReturnToDeviceManagementPacket;
import java.util.List;
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
        var toolbar = ((AEBaseScreenAccessor) screen).ae2lt$getVerticalToolbar();
        if (((VerticalButtonBarAccessor) toolbar).ae2lt$getButtons().stream().anyMatch(b -> b instanceof ReturnButton)) return;
        var button = new ReturnButton(screen.getMenu().containerId);
        toolbar.add(button); event.addListener(button);
    }
    private static final class ReturnButton extends IconButton {
        private ReturnButton(int menuId) { super(b -> PacketDistributor.sendToServer(new ReturnToDeviceManagementPacket(menuId))); }
        @Override protected Icon getIcon() { return Icon.BACK; }
        @Override public List<Component> getTooltipMessage() { return List.of(Component.translatable("ae2lt.management.return_devices")); }
    }
}
