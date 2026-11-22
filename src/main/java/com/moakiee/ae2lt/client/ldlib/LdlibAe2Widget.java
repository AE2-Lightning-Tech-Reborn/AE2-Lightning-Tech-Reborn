package com.moakiee.ae2lt.client.ldlib;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

import appeng.client.gui.widgets.ITooltip;

/**
 * An AE2-compatible widget whose content is a small LDLib2 {@link ModularUI}.
 *
 * <p>AE2 screens place widgets from their style JSON ({@code widgets.add(id, widget)}),
 * so this host keeps that contract: AE2 sets the position, LDLib draws the content
 * (laid out to the widget's own size) and AE2's {@link ITooltip} pipeline shows the
 * tooltip. Interactive subclasses can opt into forwarding input to LDLib.</p>
 */
public class LdlibAe2Widget extends Button implements ITooltip {
    private final ModularUI modularUI;
    private boolean disposed;
    @Nullable
    private final Supplier<List<Component>> tooltip;
    private final BooleanSupplier visibleWhen;
    private final int tooltipMargin;

    public LdlibAe2Widget(int width, int height, UIElement root,
            @Nullable Supplier<List<Component>> tooltip, int tooltipMargin, BooleanSupplier visibleWhen) {
        super(Button.builder(Component.empty(), ignored -> {}).size(width, height));
        root.layout(l -> l.width(width).height(height));
        this.modularUI = ModularUI.of(UI.of(root));
        this.modularUI.init(width, height);
        this.tooltip = tooltip;
        this.tooltipMargin = tooltipMargin;
        this.visibleWhen = visibleWhen;
    }

    public LdlibAe2Widget(int width, int height, UIElement root) {
        this(width, height, root, null, 0, () -> true);
    }

    /** Releases a permanently discarded host; this instance must not be rendered again. */
    public final void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        try {
            setFocused(false);
            modularUI.clearFocus();
        } finally {
            modularUI.onRemoved();
        }
    }

    protected final ModularUI modularUI() {
        return modularUI;
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!visibleWhen.getAsBoolean()) {
            return;
        }
        var pose = guiGraphics.pose();
        pose.pushPose();
        try {
            pose.translate(getX(), getY(), 0.0F);
            modularUI.getWidget().render(guiGraphics, mouseX - getX(), mouseY - getY(), partialTick);
        } finally {
            pose.popPose();
        }
    }

    @Override
    public List<Component> getTooltipMessage() {
        if (tooltip == null) {
            return List.of();
        }
        List<Component> lines = tooltip.get();
        return lines != null ? lines : List.of();
    }

    @Override
    public Rect2i getTooltipArea() {
        return new Rect2i(getX() - tooltipMargin, getY() - tooltipMargin,
                width + 2 * tooltipMargin, height + 2 * tooltipMargin);
    }

    @Override
    public boolean isTooltipAreaVisible() {
        return visible && tooltip != null && visibleWhen.getAsBoolean();
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return handlesInput() && visibleWhen.getAsBoolean() && super.isMouseOver(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active || !isMouseOver(mouseX, mouseY)) {
            return false;
        }
        double x = mouseX - getX();
        double y = mouseY - getY();
        modularUI.refreshHoveredElement((float) x, (float) y);
        return modularUI.getWidget().mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!handlesInput()) {
            return false;
        }
        double x = mouseX - getX();
        double y = mouseY - getY();
        modularUI.refreshHoveredElement((float) x, (float) y);
        return modularUI.getWidget().mouseReleased(x, y, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return handlesInput() && super.keyPressed(keyCode, scanCode, modifiers);
    }

    protected boolean handlesInput() {
        return false;
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        if (handlesInput()) {
            defaultButtonNarrationText(narrationElementOutput);
        }
    }
}
