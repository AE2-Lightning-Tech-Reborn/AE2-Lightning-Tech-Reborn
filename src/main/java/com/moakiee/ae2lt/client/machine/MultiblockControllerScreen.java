package com.moakiee.ae2lt.client.machine;

import com.moakiee.ae2lt.client.ldlib.LdlibScreens;
import com.moakiee.ae2lt.client.ldlib.LdlibStyleSprite;
import com.moakiee.ae2lt.client.ldlib.LdlibText;
import com.lowdragmc.lowdraglib2.gui.LDLibFonts;

import appeng.client.gui.style.PaletteColor;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.client.gui.AE2GuiPalette;

import com.moakiee.ae2lt.logic.compute.ComputeTier;
import java.util.Locale;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Block;

public abstract class MultiblockControllerScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
    protected static final int BLUE = 221, RED = 228, AMBER = 235, GREEN = 242;
    private static final int BAR_WIDTH = 150;
    private final LdlibStyleSprite atlas;
    private final int machineIconV;

    protected MultiblockControllerScreen(T menu, Inventory inventory, Component title, String atlasName, int machineIconV) {
        super(menu, inventory, title);
        atlas = new LdlibStyleSprite(ResourceLocation.fromNamespaceAndPath(
                AE2LightningTech.MODID, "textures/guis/" + atlasName + ".png"), 256, 256);
        this.machineIconV = machineIconV;
        imageWidth = 186;
        imageHeight = 220;
    }

    protected static int textColor() {
        return AE2GuiPalette.color(PaletteColor.DEFAULT_TEXT_COLOR);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        atlas.draw(graphics, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    protected void drawHeader(GuiGraphics graphics, ComputeTier tier, Block core, Component name, boolean formed, boolean warning) {
        text(graphics, title.getString(), 28, 8, 145);
        if (tier != null && core != null) {
            int frameV = switch (tier) {
                case BASELINE -> 105;
                case QUANTUM -> 71;
                case OVERLOAD -> 37;
                case MULTIDIMENSIONAL -> 3;
            };
            atlas.draw(graphics, 20, 36, 188, frameV, 22, 32);
            var sprite = minecraft.getBlockRenderer().getBlockModelShaper().getParticleIcon(core.defaultBlockState());
            LdlibScreens.drawAtlasIcon(graphics, 23, 44, 0, sprite);
        }
        atlas.draw(graphics, 110, 44, 215, machineIconV, 16, 16);
        atlas.draw(graphics, 56, 56, 216, !formed ? 40 : warning ? 50 : 60, 8, 8);
        text(graphics, name.getString(), 55, 42, 46);
        String state = !formed ? "unformed" : warning ? "warning" : "ready";
        text(graphics, Component.translatable("ae2lt.controller." + state).getString(), 67, 56, 35);
    }

    protected void metric(GuiGraphics graphics, int row, String label, String value, double fraction, int barV) {
        int offset = row * 39;
        text(graphics, Component.translatable(label).getString(), 31, 82 + offset, 136);
        int valueWidth = Math.min(150, font.width(value));
        text(graphics, value, 168 - valueWidth, 94 + offset, 150);
        int barY = 104 + offset;
        if (fraction == Double.POSITIVE_INFINITY) {
            for (int start = 0; start < BAR_WIDTH; start += 12) {
                atlas.draw(graphics, 18 + start, barY, 1 + start, barV, Math.min(6, BAR_WIDTH - start), 5);
            }
        } else if (fraction > 0) {
            int width = Math.clamp(Math.round(BAR_WIDTH * fraction), 1, BAR_WIDTH);
            atlas.draw(graphics, 18, barY, 1, barV, width, 5);
        }
    }

    protected void markHeatRange(GuiGraphics graphics, double low, double high) {
        for (double edge : new double[] {low, high}) {
            int marker = 18 + (int) Math.round(BAR_WIDTH * edge);
            LdlibScreens.fillRect(graphics, marker, 103, marker + 1, 110, 0xFFF2F2F2);
        }
    }

    protected void text(GuiGraphics graphics, String value, int x, int y, int width) {
        float scale = Math.min(1F, (float) width / Math.max(1, font.width(value)));
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1F);
        LdlibText.drawString(graphics, font, value, 0, 0, textColor(), false);
        graphics.pose().popPose();
    }

    protected void drawUnformed(GuiGraphics graphics, Component issue) {
        var lines = font.split(issue, 135);
        int linesPerPanel = Math.max(2, Math.ceilDiv(lines.size(), 3));
        float step = 21F / linesPerPanel;
        float scale = Math.min(1F, step / font.lineHeight);
        for (int index = 0; index < lines.size(); index++) {
            float y = 82 + (index / linesPerPanel) * 39 + (index % linesPerPanel) * step;
            graphics.pose().pushPose();
            graphics.pose().translate(31, y, 0);
            graphics.pose().scale(scale, scale, 1F);
            LDLibFonts.drawText(graphics, font, lines.get(index), 0, 0, textColor(), false);
            graphics.pose().popPose();
        }
        text(graphics, Component.translatable("ae2lt.matrix.gui.hint_unformed").getString(), 16, 195, 154);
    }

    protected static String formatCount(long value) {
        return value == Long.MAX_VALUE ? "∞" : String.format(Locale.ROOT, "%,d", value);
    }

    protected static String percent(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value * 100.0D);
    }
}
