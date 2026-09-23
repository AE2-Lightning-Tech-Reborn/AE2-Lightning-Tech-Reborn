package com.moakiee.ae2lt.client;

import net.minecraft.resources.Identifier;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.neoforge.client.gui.GuiLayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.celestweave.ArmorEnergyBuffer;
import com.moakiee.ae2lt.celestweave.BaseCelestweaveArmorItem;

public final class CelestweaveArmorEnergyLevel implements GuiLayer {
    public static final CelestweaveArmorEnergyLevel INSTANCE = new CelestweaveArmorEnergyLevel();

    private static final int BAR_WIDTH = 81;
    private static final int BAR_HEIGHT = 6;
    private static final int INNER_WIDTH = 79;
    private static final int INNER_HEIGHT = 4;
    private static final Identifier BAR_BASE = Identifier.fromNamespaceAndPath(
            "ae2lt", "textures/gui/hud/hud_bar.png");
    private static final Identifier BAR_OVERLAY = Identifier.fromNamespaceAndPath(
            "ae2lt", "textures/gui/hud/hud_bar_overlay.png");

    private CelestweaveArmorEnergyLevel() {
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || minecraft.gameMode == null
                || !minecraft.gameMode.canHurtPlayer()
                || minecraft.options.hideGui) {
            return;
        }

        long capacity = 0L;
        long stored = 0L;
        for (var slot : new net.minecraft.world.entity.EquipmentSlot[] {
                net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET}) {
            ItemStack stack = minecraft.player.getItemBySlot(slot);
            if (stack.getItem() instanceof BaseCelestweaveArmorItem) {
                capacity = addClamped(capacity, ArmorEnergyBuffer.capacity(stack));
                stored = addClamped(stored, ArmorEnergyBuffer.read(stack));
            }
        }
        if (capacity <= 0L) {
            return;
        }

        int x = graphics.guiWidth() / 2 - 91;
        int y = graphics.guiHeight() - minecraft.gui.leftHeight + 2;
        int length = Mth.clamp((int) Math.round(((double) Math.min(stored, capacity) / capacity) * INNER_WIDTH),
                0, INNER_WIDTH);

        graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, BAR_BASE, x, y, 0, 0, BAR_WIDTH, BAR_HEIGHT, BAR_WIDTH, BAR_HEIGHT);
        if (length > 0) {
            graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, BAR_OVERLAY, x + 1, y + 1, length, INNER_HEIGHT, 1, 1, length, INNER_HEIGHT, BAR_WIDTH, BAR_HEIGHT);
        }
        minecraft.gui.leftHeight += 8;
    }

    private static long addClamped(long left, long right) {
        if (right <= 0L) {
            return left;
        }
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }
}
