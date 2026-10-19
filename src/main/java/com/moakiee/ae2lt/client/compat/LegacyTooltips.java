package com.moakiee.ae2lt.client.compat;

import com.moakiee.ae2lt.AE2LightningTech;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.WeakHashMap;

/** Delayed tooltip rendering for both native AE2 widgets and LT widgets. */
@Mod.EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public final class LegacyTooltips {
    private static final WeakHashMap<AbstractWidget, Tooltip> TOOLTIPS = new WeakHashMap<>();
    private LegacyTooltips() {}
    public static void set(AbstractWidget widget, Tooltip tooltip) {
        if (tooltip == null) TOOLTIPS.remove(widget); else TOOLTIPS.put(widget, tooltip);
    }
    @SubscribeEvent public static void render(ScreenEvent.Render.Post event) {
        for (var child : event.getScreen().children()) {
            if (child instanceof AbstractWidget widget && widget.visible && widget.isMouseOver(event.getMouseX(), event.getMouseY())) {
                var tooltip = TOOLTIPS.get(widget);
                if (tooltip != null) {
                    LegacyGui.renderTooltip(event.getPoseStack(), Minecraft.getInstance().font, tooltip.text(), event.getMouseX(), event.getMouseY());
                    return;
                }
            }
        }
    }
}
