package com.moakiee.ae2lt.client.machine;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.moakiee.ae2lt.client.ldlib.LdlibAe2Button;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

public final class MachineOutputSideButton extends LdlibAe2Button {
    private final Component sideLabel;
    private final String tooltipPrefix;
    private ItemStack display = ItemStack.EMPTY;
    private boolean enabled;

    MachineOutputSideButton(Component sideLabel, String tooltipPrefix, Runnable action) {
        super(16, sideLabel, action);
        this.sideLabel = sideLabel;
        this.tooltipPrefix = tooltipPrefix;
        setMessage(Component.empty());
        toolbarFace(IGuiTexture.EMPTY, false, false);
    }

    void setDisplay(@Nullable ItemLike item) {
        display = item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    void setOn(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        toolbarFace(IGuiTexture.EMPTY, enabled, false);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!visible) {
            return;
        }
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        if (!display.isEmpty()) {
            graphics.renderItem(display, getX(), getY() + 1 + (isHovered() ? 1 : 0), 0, 3);
        }
    }

    @Override
    public List<Component> getTooltipMessage() {
        return List.of(sideLabel, Component.translatable(tooltipPrefix + (enabled ? ".enabled" : ".disabled")));
    }

    @Override
    public boolean isTooltipAreaVisible() {
        return visible;
    }
}
