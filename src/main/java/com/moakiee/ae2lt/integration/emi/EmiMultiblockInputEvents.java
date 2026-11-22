package com.moakiee.ae2lt.integration.emi;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Gives the LDLib preview its wheel events before EMI can use them to change recipe pages. */
final class EmiMultiblockInputEvents {
    private record Preview(WeakReference<Screen> screen, WeakReference<ModularUI> ui,
            float left, float top, float right, float bottom) {
        boolean contains(double x, double y) {
            return x >= left && x < right && y >= top && y < bottom;
        }
    }

    private static final List<Preview> RENDERED_PREVIEWS = new ArrayList<>();
    private static boolean registered;

    private EmiMultiblockInputEvents() {
    }

    static synchronized void register() {
        if (!registered) {
            NeoForge.EVENT_BUS.addListener(EmiMultiblockInputEvents::beforeScreenRender);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, EmiMultiblockInputEvents::onMouseScrolled);
            registered = true;
        }
    }

    private static void beforeScreenRender(ScreenEvent.Render.Pre event) {
        RENDERED_PREVIEWS.clear();
    }

    static void recordRender(ModularUI ui, GuiGraphics graphics) {
        var screen = Minecraft.getInstance().screen;
        var pose = graphics.pose().last().pose();
        float left = pose.m30();
        float top = pose.m31();
        RENDERED_PREVIEWS.add(new Preview(new WeakReference<>(screen), new WeakReference<>(ui), left, top,
                left + pose.m00() * ui.getScreenWidth(), top + pose.m11() * ui.getScreenHeight()));
    }

    private static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        double x = event.getMouseX();
        double y = event.getMouseY();
        for (var preview : RENDERED_PREVIEWS.reversed()) {
            var ui = preview.ui().get();
            if (ui != null && preview.screen().get() == event.getScreen() && preview.contains(x, y)
                    && ui.getWidget().mouseScrolled(x, y, event.getScrollDeltaX(), event.getScrollDeltaY())) {
                event.setCanceled(true);
                return;
            }
        }
    }
}
