package com.moakiee.ae2lt.client.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;

/** Retains the LT button builder and tooltips on the 1.19.2 widget API. */
public class Button extends net.minecraft.client.gui.components.Button {
    private Tooltip tooltip;
    public Button(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress);
    }
    protected Button(Builder builder) { this(builder.x, builder.y, builder.width, builder.height, builder.message, builder.onPress); }
    public static Builder builder(Component message, OnPress onPress) { return new Builder(message, onPress); }
    public void setTooltip(Tooltip tooltip) { this.tooltip = tooltip; }
    public int getX() { return x; }
    public int getY() { return y; }
    public void setX(int value) { x = value; }
    public void setY(int value) { y = value; }
    public void setHeight(int value) { height = value; }
    public void setPosition(int x, int y) { this.x = x; this.y = y; }
    @Override public void renderButton(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        renderWidget(pose, mouseX, mouseY, partialTick);
    }
    protected void renderWidget(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        super.renderButton(pose, mouseX, mouseY, partialTick);
    }
    @Override public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        super.render(pose, mouseX, mouseY, partialTick);
        if (visible && isHovered && tooltip != null) LegacyGui.renderTooltip(pose, Minecraft.getInstance().font, tooltip.text(), mouseX, mouseY);
    }
    public static final class Builder {
        private final Component message;
        private final OnPress onPress;
        private int x, y, width = 150, height = 20;
        private Tooltip tooltip;
        Builder(Component message, OnPress onPress) { this.message = message; this.onPress = onPress; }
        public Builder bounds(int x, int y, int width, int height) { this.x=x; this.y=y; this.width=width; this.height=height; return this; }
        public Builder pos(int x, int y) { this.x=x; this.y=y; return this; }
        public Builder size(int width, int height) { this.width=width; this.height=height; return this; }
        public Builder width(int width) { this.width=width; return this; }
        public Builder tooltip(Tooltip tooltip) { this.tooltip=tooltip; return this; }
        public Button build() { var button = new Button(this); button.setTooltip(tooltip); return button; }
    }
}
