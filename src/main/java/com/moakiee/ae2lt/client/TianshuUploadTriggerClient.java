package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.config.AE2LTClientConfig;
import net.minecraft.client.gui.screens.Screen;

public final class TianshuUploadTriggerClient {
    private TianshuUploadTriggerClient() {
    }

    public static boolean shouldTrigger() {
        return switch (AE2LTClientConfig.uploadTrigger()) {
            case NO_SHIFT -> !net.minecraft.client.Minecraft.getInstance().hasShiftDown();
            case SHIFT -> net.minecraft.client.Minecraft.getInstance().hasShiftDown();
            case CTRL -> net.minecraft.client.Minecraft.getInstance().hasControlDown();
            case ALT -> net.minecraft.client.Minecraft.getInstance().hasAltDown();
            case MANUAL_ONLY -> false;
        };
    }
}
