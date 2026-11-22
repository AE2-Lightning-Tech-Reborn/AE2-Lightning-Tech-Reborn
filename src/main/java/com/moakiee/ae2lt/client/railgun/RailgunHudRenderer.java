package com.moakiee.ae2lt.client.railgun;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.FillDirection;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.client.ldlib.LdlibHud;
import com.moakiee.ae2lt.client.ldlib.LdlibScreens;
import com.moakiee.ae2lt.client.ldlib.LdlibStyleSprite;
import com.moakiee.ae2lt.config.RailgunDefaults;
import com.moakiee.ae2lt.item.railgun.ElectromagneticRailgunItem;
import com.moakiee.ae2lt.registry.ModDataComponents;

/**
 * Renders a small charge-progress bar near the crosshair while the player is
 * holding right-click on the railgun.
 *
 * <p>The bar is a 13x19 lightning-bolt icon placed immediately to the right of
 * the crosshair. The empty texture is drawn as the background; the full
 * texture is revealed progressively from the bottom up as charge builds.
 */
@EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public final class RailgunHudRenderer {

    private static final ResourceLocation EMPTY_TEX = ResourceLocation.fromNamespaceAndPath(
            AE2LightningTech.MODID, "textures/gui/hud/lightning_charging_bar.png");
    private static final ResourceLocation FULL_TEX = ResourceLocation.fromNamespaceAndPath(
            AE2LightningTech.MODID, "textures/gui/hud/lightning_charging_bar_full.png");

    private static final int ICON_W = 13;
    private static final int ICON_H = 19;
    private static final IGuiTexture EMPTY = new LdlibStyleSprite(EMPTY_TEX, ICON_W, ICON_H).full();
    private static final IGuiTexture[] FILL = new LdlibStyleSprite(FULL_TEX, ICON_W, ICON_H).verticalSlices(true);
    /** Horizontal gap between the crosshair center and the icon's left edge. */
    private static final int CROSSHAIR_OFFSET_X = 10;

    private static LdlibHud hud;
    private static ProgressBar fill;

    private RailgunHudRenderer() {}

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Charge-progress bar only while right-click-charging.
        if (!mc.player.isUsingItem()) return;
        ItemStack stack = mc.player.getUseItem();
        if (!(stack.getItem() instanceof ElectromagneticRailgunItem)) return;

        long ticks = stack.getOrDefault(ModDataComponents.RAILGUN_CHARGE_TICKS.get(), 0L);
        int t3 = RailgunDefaults.CHARGE_TICKS_TIER3;
        float progress = Math.min(1.0f, (float) ticks / (float) t3);

        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        int x = w / 2 + CROSSHAIR_OFFSET_X;
        int y = (h - ICON_H) / 2;

        if (hud == null) {
            fill = LdlibScreens.spriteProgress(FillDirection.DOWN_TO_UP, FILL);
            var root = new UIElement().style(style -> style.background(EMPTY));
            root.addChild(LdlibScreens.at(fill, 0, 0, ICON_W, ICON_H));
            hud = new LdlibHud(root, ICON_W, ICON_H);
        }
        fill.setProgress(Mth.clamp(Math.round(ICON_H * progress), 0, ICON_H));
        hud.renderAt(e.getGuiGraphics(), e.getPartialTick(), x, y);
    }

}
