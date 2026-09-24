package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.logic.EasterEggAudience;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.core.GlobalPos;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;

public final class EasterEggOverlay implements LayeredDraw.Layer {
    public static final EasterEggOverlay INSTANCE = new EasterEggOverlay();

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "textures/gui/easter_egg.png");

    private static final EasterEggDisplayState STATE = new EasterEggDisplayState();
    private static GlobalPos source;

    private EasterEggOverlay() {
    }

    public static void trigger(GlobalPos origin) {
        if (isNearby(origin) && STATE.trigger()) source = origin;
    }

    public static boolean isActive() {
        return STATE.isActive();
    }

    public static void tick() {
        STATE.tick();
        if (source != null && !isNearby(source)) {
            STATE.dismiss();
            source = null;
        }
    }

    private static boolean isNearby(GlobalPos origin) {
        var mc = Minecraft.getInstance();
        return origin != null && mc.player != null && mc.level != null
                && EasterEggAudience.includes(origin, mc.level.dimension(), mc.player.position());
    }

    /**
     * Force-clears the overlay state. Called on logout / world unload so the
     * easter-egg image cannot bleed into the next session (and so the static
     * tick counter does not retain references that survive the logical client).
     */
    public static void reset() {
        STATE.reset();
        source = null;
    }

    @Override
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        if (!STATE.isVisible() || !isNearby(source)) {
            return;
        }

        var mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        int imgWidth = 512;
        int imgHeight = 436;
        float aspect = (float) imgWidth / imgHeight;

        int maxW = screenWidth * 3 / 4;
        int maxH = screenHeight * 3 / 4;
        int drawW, drawH;
        if ((float) maxW / maxH > aspect) {
            drawH = maxH;
            drawW = (int) (maxH * aspect);
        } else {
            drawW = maxW;
            drawH = (int) (maxW / aspect);
        }
        int x = (screenWidth - drawW) / 2;
        int y = (screenHeight - drawH) / 2;

        // The plain texture blit does not enable blending. Without it the PNG's
        // transparent pixels (and the old alpha-zero lead-in) can cover the HUD.
        guiGraphics.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        guiGraphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        try {
            guiGraphics.blit(TEXTURE, x, y, drawW, drawH, 0, 0, imgWidth, imgHeight, imgWidth, imgHeight);
        } finally {
            guiGraphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.disableBlend();
        }
    }
}
