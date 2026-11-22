package com.moakiee.ae2lt.client.equipment;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.FillDirection;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar;
import com.moakiee.ae2lt.client.ldlib.LdlibHud;
import com.moakiee.ae2lt.client.ldlib.LdlibScreens;
import com.moakiee.ae2lt.client.ldlib.LdlibStyleSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.celestweave.ArmorEnergyBuffer;
import com.moakiee.ae2lt.celestweave.BaseCelestweaveArmorItem;
import com.moakiee.ae2lt.item.PhaseLockProjectionItem;
import com.moakiee.ae2lt.registry.ModDataComponents;

public final class CelestweaveArmorEnergyLevel implements LayeredDraw.Layer {
    public static final CelestweaveArmorEnergyLevel INSTANCE = new CelestweaveArmorEnergyLevel();

    private static final int BAR_WIDTH = 81;
    private static final int BAR_HEIGHT = 6;
    private static final int INNER_WIDTH = 79;
    private static final int INNER_HEIGHT = 4;
    private static final ResourceLocation BAR_BASE = ResourceLocation.fromNamespaceAndPath(
            "ae2lt", "textures/gui/hud/hud_bar.png");
    private static final ResourceLocation BAR_OVERLAY = ResourceLocation.fromNamespaceAndPath(
            "ae2lt", "textures/gui/hud/hud_bar_overlay.png");
    private static final IGuiTexture BACKGROUND = new LdlibStyleSprite(BAR_BASE, BAR_WIDTH, BAR_HEIGHT).full();
    private static final IGuiTexture[] FILL = new LdlibStyleSprite(BAR_OVERLAY, BAR_WIDTH, BAR_HEIGHT)
            .horizontalSlices(1, 1, INNER_WIDTH, INNER_HEIGHT);

    private LdlibHud hud;
    private ProgressBar fill;

    private CelestweaveArmorEnergyLevel() {
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || minecraft.gameMode == null
                || !minecraft.gameMode.canHurtPlayer()
                || minecraft.options.hideGui) {
            return;
        }

        long capacity = 0L;
        long stored = 0L;
        for (ItemStack stack : minecraft.player.getArmorSlots()) {
            if (stack.getItem() instanceof BaseCelestweaveArmorItem) {
                capacity = addClamped(capacity, ArmorEnergyBuffer.capacity(stack));
                stored = addClamped(stored, ArmorEnergyBuffer.read(stack));
            } else if (stack.getItem() instanceof PhaseLockProjectionItem) {
                var snapshot = stack.get(ModDataComponents.PHASE_LOCK_ENERGY_SNAPSHOT.get());
                if (snapshot != null) {
                    capacity = addClamped(capacity, snapshot.capacity());
                    stored = addClamped(stored, snapshot.stored());
                }
            }
        }
        if (capacity <= 0L) {
            return;
        }

        int x = graphics.guiWidth() / 2 - 91;
        int y = graphics.guiHeight() - minecraft.gui.leftHeight + 2;
        int length = Mth.clamp((int) Math.round(((double) Math.min(stored, capacity) / capacity) * INNER_WIDTH),
                0, INNER_WIDTH);

        if (hud == null) {
            fill = LdlibScreens.spriteProgress(FillDirection.LEFT_TO_RIGHT, FILL);
            var root = new UIElement().style(style -> style.background(BACKGROUND));
            root.addChild(LdlibScreens.at(fill, 1, 1, INNER_WIDTH, INNER_HEIGHT));
            hud = new LdlibHud(root, BAR_WIDTH, BAR_HEIGHT);
        }
        fill.setProgress(length);
        hud.renderAt(graphics, delta, x, y);
        minecraft.gui.leftHeight += 8;
    }

    private static long addClamped(long left, long right) {
        if (right <= 0L) {
            return left;
        }
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }
}
