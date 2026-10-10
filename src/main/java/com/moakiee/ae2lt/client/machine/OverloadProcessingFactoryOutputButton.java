package com.moakiee.ae2lt.client.machine;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import appeng.client.gui.Icon;
import appeng.client.gui.widgets.IconButton;

public class OverloadProcessingFactoryOutputButton extends IconButton {
    private final Component sideLabel;
    private final String statusPrefix;
    private ItemStack display = ItemStack.EMPTY;
    private boolean on;

    public OverloadProcessingFactoryOutputButton(Component sideLabel, OnPress onPress) {
        this(sideLabel, onPress, "ae2lt.gui.overload_factory.output_side.");
    }

    protected OverloadProcessingFactoryOutputButton(Component sideLabel, OnPress onPress, String statusPrefix) {
        super(onPress);
        this.sideLabel = sideLabel;
        this.statusPrefix = statusPrefix;
    }

    public void setDisplay(@Nullable ItemLike itemLike) {
        this.display = itemLike == null ? ItemStack.EMPTY : new ItemStack(itemLike);
    }

    public void setOn(boolean on) {
        this.on = on;
    }

    @Override
    protected Icon getIcon() {
        return Icon.TOOLBAR_BUTTON_BACKGROUND;
    }

    @Override
    protected Item getItemOverlay() {
        return display.isEmpty() ? null : display.getItem();
    }

    @Override
    public List<Component> getTooltipMessage() {
        return List.of(
                sideLabel,
                Component.translatable(statusPrefix + (on ? "enabled" : "disabled")));
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partial) {
        if (!this.visible) {
            return;
        }

        OutputSideButtonStyle.renderBackground(guiGraphics, getX(), getY(), on);

        if (!display.isEmpty()) {
            guiGraphics.renderItem(display, getX() + 1, getY() + 1, 0, 3);
        }
    }
}
