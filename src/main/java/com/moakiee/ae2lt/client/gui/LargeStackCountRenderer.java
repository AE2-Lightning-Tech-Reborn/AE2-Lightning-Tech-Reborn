package com.moakiee.ae2lt.client.gui;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;

import com.moakiee.ae2lt.menu.LargeStackAppEngSlot;
import com.moakiee.ae2lt.mixin.client.GuiGraphicsBatchAccessor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.moakiee.ae2lt.client.ldlib.LdlibText;
import mezz.jei.api.gui.builder.ITooltipBuilder;

/**
 * Shared rendering for slots whose stack count may exceed 64.
 * <p>
 * Displays abbreviated counts (e.g. {@code 1.5K}, {@code 2.3M}) with a
 * scaled-down font, similar to how AE2 renders amounts in its ME terminal.
 * A tooltip showing the exact count is available via
 * {@link #appendCountTooltip}.
 */
public final class LargeStackCountRenderer {
    private static final float SCALE = 0.75F;
    private static final int SHADOW_COLOR = 0xFF413F54;
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    private LargeStackCountRenderer() {
    }

    /**
     * Renders the abbreviated stack count on a slot if it is a
     * {@link LargeStackAppEngSlot} with a count greater than 1.
     */
    public static void renderSlotCount(GuiGraphics guiGraphics, Font font, Slot slot) {
        if (!(slot instanceof LargeStackAppEngSlot)) {
            return;
        }

        var stack = slot.getItem();
        long amount = ((LargeStackAppEngSlot) slot).getDisplayedAmount();
        if (stack.isEmpty() || amount <= 1) {
            return;
        }

        String text = formatCount(amount);
        renderLabel(guiGraphics, font, slot.x, slot.y, text);
    }

    /**
     * Renders an abbreviated stack count at a slot position.
     * <p>
     * Intended for contexts like JEI where there is no real {@link Slot}
     * instance, but we still want to reuse the exact same large-stack count
     * visuals as the machine GUI.
     */
    public static void renderCountAt(GuiGraphics guiGraphics, Font font, int slotX, int slotY, long count) {
        if (count <= 1) {
            return;
        }

        renderLabel(guiGraphics, font, slotX, slotY, formatCount(count));
    }

    /**
     * Appends a tooltip line showing the exact count when the hovered slot is a
     * {@link LargeStackAppEngSlot} with a count greater than 1.
     */
    public static void appendCountTooltip(List<Component> lines, Slot slot) {
        if (!(slot instanceof LargeStackAppEngSlot) || !slot.hasItem()) {
            return;
        }

        appendCountTooltip(lines, ((LargeStackAppEngSlot) slot).getDisplayedAmount());
    }

    /**
     * Appends a tooltip line showing the exact count for any stack-like count
     * source, including JEI recipe ingredients.
     */
    public static void appendCountTooltip(List<Component> lines, long count) {
        if (count > 1) {
            lines.add(Component.translatable("ae2lt.gui.slot_count", String.format("%,d", count))
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /**
     * Appends a tooltip line showing the exact count for JEI rich tooltips.
     */
    public static void appendCountTooltip(ITooltipBuilder tooltip, long count) {
        if (count > 1) {
            tooltip.add(Component.translatable("ae2lt.gui.slot_count", String.format("%,d", count))
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /**
     * Formats a count into an abbreviated form:
     * <ul>
     * <li>{@code < 1000} &rarr; exact number</li>
     * <li>{@code < 1,000,000} &rarr; K suffix (e.g. {@code 1.5K})</li>
     * <li>{@code < 1,000,000,000} &rarr; M suffix (e.g. {@code 2.3M})</li>
     * <li>otherwise &rarr; B suffix</li>
     * </ul>
     */
    public static String formatCount(long count) {
        if (count < 1_000L) {
            return Long.toString(count);
        }
        if (count < 1_000_000L) {
            return formatWithSuffix(count, 1_000L, "K");
        }
        if (count < 1_000_000_000L) {
            return formatWithSuffix(count, 1_000_000L, "M");
        }
        return formatWithSuffix(count, 1_000_000_000L, "B");
    }

    private static String formatWithSuffix(long count, long divisor, String suffix) {
        double value = count / (double) divisor;
        if (value < 10) {
            String formatted = String.format("%.1f", value);
            // "1.0" -> "1"
            if (formatted.endsWith(".0")) {
                formatted = formatted.substring(0, formatted.length() - 2);
            }
            return formatted + suffix;
        }
        return Math.round(value) + suffix;
    }

    private static void renderLabel(GuiGraphics guiGraphics, Font font, int slotX, int slotY, String text) {
        drawLabel(guiGraphics, font, slotX, slotY, text, SCALE, 0, 300);
    }

    public static void renderTerminalLabel(GuiGraphics graphics, Font font, float slotX, float slotY,
                                           String text, boolean largeFont) {
        RenderSystem.disableBlend();
        try {
            drawLabel(graphics, font, slotX, slotY, text, largeFont ? 0.85F : 0.666F, largeFont ? 0 : -1, 200);
        } finally {
            RenderSystem.enableBlend();
        }
    }

    private static void drawLabel(GuiGraphics graphics, Font font, float slotX, float slotY, String text,
                                  float scale, int inset, int depth) {
        float inverseScale = 1.0F / scale;
        int drawX = (int) ((slotX + inset + 18.0F - font.width(text) * scale) * inverseScale);
        int drawY = (int) ((slotY + inset + 16.0F - 5.0F * scale) * inverseScale);
        var pose = graphics.pose();
        var minecraft = Minecraft.getInstance();
        var buffer = minecraft.renderBuffers().bufferSource();
        var textGraphics = graphics.bufferSource() == buffer ? graphics : new GuiGraphics(minecraft, buffer);
        var batching = (GuiGraphicsBatchAccessor) (Object) textGraphics;
        boolean wasManaged = batching.ae2lt$isManaged();
        pose.pushPose();
        try {
            // A GuiGraphics flush also resets depth state; retain the original explicit endBatch instead.
            batching.ae2lt$setManaged(true);
            pose.translate(0, 0, depth);
            pose.scale(scale, scale, scale);
            if (textGraphics != graphics) {
                textGraphics.pose().last().pose().set(pose.last().pose());
            }
            LdlibText.drawString(textGraphics, font, text, drawX + 1, drawY + 1, SHADOW_COLOR, false);
            LdlibText.drawString(textGraphics, font, text, drawX, drawY, TEXT_COLOR, false);
            buffer.endBatch();
        } finally {
            batching.ae2lt$setManaged(wasManaged);
            pose.popPose();
        }
    }
}
