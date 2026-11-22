package com.moakiee.ae2lt.client.easteregg;

import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.client.ldlib.LdlibHud;
import com.moakiee.ae2lt.logic.world.EasterEggAudience;
import net.minecraft.core.GlobalPos;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;

public final class EasterEggOverlay implements LayeredDraw.Layer {
    public static final EasterEggOverlay INSTANCE = new EasterEggOverlay();

    private static final SpriteTexture TEXTURE = SpriteTexture.of(
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "textures/gui/easter_egg.png"));
    private static final int IMAGE_WIDTH = 512;
    private static final int IMAGE_HEIGHT = 436;
    private static final float IMAGE_ASPECT = (float) IMAGE_WIDTH / IMAGE_HEIGHT;

    private static final EasterEggDisplayState STATE = new EasterEggDisplayState();
    private static GlobalPos source;

    private LdlibHud hud;

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

        int maxW = screenWidth * 3 / 4;
        int maxH = screenHeight * 3 / 4;
        int drawW, drawH;
        if ((float) maxW / maxH > IMAGE_ASPECT) {
            drawH = maxH;
            drawW = (int) (maxH * IMAGE_ASPECT);
        } else {
            drawW = maxW;
            drawH = (int) (maxW / IMAGE_ASPECT);
        }
        int x = (screenWidth - drawW) / 2;
        int y = (screenHeight - drawH) / 2;
        if (drawW <= 0 || drawH <= 0) {
            return;
        }

        if (hud == null) {
            var image = new UIElement().style(style -> style.background(TEXTURE));
            hud = new LdlibHud(image, IMAGE_WIDTH, IMAGE_HEIGHT);
        }
        guiGraphics.flush();
        hud.renderAt(guiGraphics, deltaTracker, x, y, drawW, drawH);
        guiGraphics.flush();
    }
}
