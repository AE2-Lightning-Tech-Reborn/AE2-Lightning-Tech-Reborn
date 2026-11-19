package com.moakiee.ae2lt.api.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import appeng.client.gui.Icon;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.widgets.ITooltip;

/**
 * Client-only AE2 toolbar button with caller-supplied textures and state tooltips.
 * Clicking reports the displayed index; the caller owns state changes and synchronization.
 */
public class TextureToolbarButton extends Button implements ITooltip {
    private final List<ResourceLocation> textures;
    private final List<List<Component>> tooltips;
    private final java.util.function.IntConsumer listener;

    private int stateIndex;

    public TextureToolbarButton(List<ResourceLocation> textures, java.util.function.IntConsumer listener) {
        super(0, 0, 16, 16, Component.empty(), btn -> listener.accept(0), DEFAULT_NARRATION);
        this.textures = List.copyOf(textures);
        if (this.textures.isEmpty()) {
            throw new IllegalArgumentException("At least one toolbar texture is required");
        }
        this.tooltips = new ArrayList<>(Collections.nCopies(textures.size(), List.of()));
        this.listener = java.util.Objects.requireNonNull(listener);
    }

    public int getStateCount() {
        return this.textures.size();
    }

    /** 通用状态切换入口,索引会被 clamp 到合法范围。 */
    public void setStateIndex(int index) {
        if (this.textures.isEmpty()) {
            this.stateIndex = 0;
            return;
        }
        if (index < 0) index = 0;
        if (index >= this.textures.size()) index = this.textures.size() - 1;
        this.stateIndex = index;
    }

    public int getStateIndex() {
        return this.stateIndex;
    }

    /** 通用 tooltip 设置;对越界索引静默忽略。 */
    public void setTooltipAt(int index, List<Component> lines) {
        if (index < 0 || index >= this.tooltips.size()) {
            return;
        }
        this.tooltips.set(index, lines == null ? List.of() : List.copyOf(lines));
    }

    // ====== 2 态兼容 API:索引 0 = OFF,索引 1 = ON ======

    public void setState(boolean isOn) {
        setStateIndex(isOn ? 1 : 0);
    }

    public void setTooltipOn(List<Component> lines) {
        setTooltipAt(1, lines);
    }

    public void setTooltipOff(List<Component> lines) {
        setTooltipAt(0, lines);
    }

    public void setVisibility(boolean visible) {
        this.visible = visible;
        this.active = visible;
    }

    @Override
    public void onPress() {
        this.listener.accept(this.stateIndex);
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!this.visible) {
            return;
        }

        var yOffset = isHovered() ? 1 : 0;
        var background = isHovered() ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER
                : isFocused() ? Icon.TOOLBAR_BUTTON_BACKGROUND_FOCUS : Icon.TOOLBAR_BUTTON_BACKGROUND;

        background.getBlitter()
                .dest(getX() - 1, getY() + yOffset, 18, 20)
                .zOffset(2)
                .blit(guiGraphics);

        if (this.textures.isEmpty()) {
            return;
        }

        int idx = Math.min(this.stateIndex, this.textures.size() - 1);
        var blitter = Blitter.texture(this.textures.get(idx), 16, 16).src(0, 0, 16, 16);
        if (!this.active) {
            blitter.opacity(0.5f);
        }
        blitter.dest(getX(), getY() + 1 + yOffset).zOffset(3).blit(guiGraphics);
    }

    @Override
    public List<Component> getTooltipMessage() {
        if (this.tooltips.isEmpty()) {
            return Collections.emptyList();
        }
        int idx = Math.min(this.stateIndex, this.tooltips.size() - 1);
        return this.tooltips.get(idx);
    }

    @Override
    public Rect2i getTooltipArea() {
        return new Rect2i(getX(), getY(), 16, 16);
    }

    @Override
    public boolean isTooltipAreaVisible() {
        return this.visible && !getTooltipMessage().isEmpty();
    }

}
