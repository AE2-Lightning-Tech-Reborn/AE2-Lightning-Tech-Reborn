package com.moakiee.ae2lt.client.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.Optional;

/** Shared 1.19.2 rendering operations for screens and overlays. */
public final class LegacyGui extends GuiComponent {
    private LegacyGui() {}
    public static int drawString(PoseStack pose, Font font, String text, int x, int y, int color, boolean shadow) {
        return shadow ? font.drawShadow(pose, text, x, y, color) : font.draw(pose, text, x, y, color);
    }
    public static int drawString(PoseStack pose, Font font, Component text, int x, int y, int color, boolean shadow) {
        return drawString(pose, font, text.getVisualOrderText(), x, y, color, shadow);
    }
    public static int drawString(PoseStack pose, Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        return shadow ? font.drawShadow(pose, text, x, y, color) : font.draw(pose, text, x, y, color);
    }
    public static void drawWordWrap(PoseStack pose, Font font, FormattedText text, int x, int y, int width, int color) {
        for (var line : font.split(text, width)) { font.draw(pose, line, x, y, color); y += font.lineHeight; }
    }
    public static void blit(PoseStack pose, ResourceLocation texture, int x, int y, float u, float v, int w, int h, int tw, int th) {
        RenderSystem.setShaderTexture(0, texture);
        GuiComponent.blit(pose, x, y, u, v, w, h, tw, th);
    }
    public static void blit(PoseStack pose, ResourceLocation texture, int x, int y, int u, int v, int w, int h) {
        blit(pose, texture, x, y, u, v, w, h, 256, 256);
    }
    public static void blit(PoseStack pose, ResourceLocation texture, int x, int y, int w, int h, float u, float v, int uw, int vh, int tw, int th) {
        RenderSystem.setShaderTexture(0, texture);
        GuiComponent.blit(pose, x, y, w, h, u, v, uw, vh, tw, th);
    }
    public static void renderItem(PoseStack pose, ItemStack stack, int x, int y) {
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.mulPoseMatrix(pose.last().pose());
        RenderSystem.applyModelViewMatrix();
        Minecraft.getInstance().getItemRenderer().renderAndDecorateItem(stack, x, y);
        modelView.popPose();
        RenderSystem.applyModelViewMatrix();
    }
    public static void renderFakeItem(PoseStack pose, ItemStack stack, int x, int y) { renderItem(pose, stack, x, y); }
    public static void renderItemDecorations(PoseStack pose, Font font, ItemStack stack, int x, int y) {
        renderItemDecorations(pose, font, stack, x, y, null);
    }
    public static void renderItemDecorations(PoseStack pose, Font font, ItemStack stack, int x, int y, String count) {
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.mulPoseMatrix(pose.last().pose());
        RenderSystem.applyModelViewMatrix();
        Minecraft.getInstance().getItemRenderer().renderGuiItemDecorations(font, stack, x, y, count);
        modelView.popPose();
        RenderSystem.applyModelViewMatrix();
    }
    public static void renderTooltip(PoseStack pose, Font font, Component text, int x, int y) {
        var screen = Minecraft.getInstance().screen;
        if (screen != null) screen.renderTooltip(pose, text, x, y);
    }
    public static void renderTooltip(PoseStack pose, Font font, List<Component> lines, Optional<TooltipComponent> component, int x, int y) {
        var screen = Minecraft.getInstance().screen;
        if (screen != null) screen.renderTooltip(pose, lines, component, x, y, font);
    }
    public static void renderTooltip(PoseStack pose, Font font, List<Component> lines, Optional<TooltipComponent> component, ItemStack stack, int x, int y) {
        var screen = Minecraft.getInstance().screen;
        if (screen != null) screen.renderTooltip(pose, lines, component, x, y, font, stack);
    }
    public static void renderTooltip(PoseStack pose, Font font, ItemStack stack, int x, int y) {
        var mc = Minecraft.getInstance();
        if (mc.screen != null) mc.screen.renderTooltip(pose, stack.getTooltipLines(mc.player,
            mc.options.advancedItemTooltips ? net.minecraft.world.item.TooltipFlag.Default.ADVANCED : net.minecraft.world.item.TooltipFlag.Default.NORMAL),
            stack.getTooltipImage(), x, y, font, stack);
    }
    public static void renderComponentTooltip(PoseStack pose, Font font, List<Component> lines, int x, int y) {
        var screen = Minecraft.getInstance().screen;
        if (screen != null) screen.renderComponentTooltip(pose, lines, x, y, font);
    }
    public static void renderOutline(PoseStack pose, int x, int y, int w, int h, int color) {
        fill(pose, x, y, x + w, y + 1, color); fill(pose, x, y + h - 1, x + w, y + h, color);
        fill(pose, x, y, x + 1, y + h, color); fill(pose, x + w - 1, y, x + w, y + h, color);
    }
    public static void enableScissor(PoseStack pose, int x, int y, int right, int bottom) { GuiComponent.enableScissor(x, y, right, bottom); }
    public static void disableScissor(PoseStack pose) { GuiComponent.disableScissor(); }
    public static void setColor(PoseStack pose, float r, float g, float b, float a) { RenderSystem.setShaderColor(r, g, b, a); }
    public static void flush(PoseStack pose) { Minecraft.getInstance().renderBuffers().bufferSource().endBatch(); }
}
