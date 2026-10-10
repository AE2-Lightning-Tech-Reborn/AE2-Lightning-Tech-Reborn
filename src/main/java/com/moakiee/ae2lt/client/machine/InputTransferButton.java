package com.moakiee.ae2lt.client.machine;

import java.util.ArrayList;
import java.util.List;

import appeng.client.gui.Icon;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.widgets.IconButton;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.menu.InputTransferMenu;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Immediate local feedback, with authoritative inventory changes coming from the server. */
final class InputTransferButton extends IconButton {
    private static final ResourceLocation ICON_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            AE2LightningTech.MODID, "textures/gui/buttons/input_transfer.png");

    private final InputTransferMenu menu;
    private long sentAt = -1;
    private int sentRevision;

    InputTransferButton(InputTransferMenu menu) {
        super(button -> ((InputTransferButton) button).transfer());
        this.menu = menu;
        setMessage(Component.translatable("ae2lt.gui.input_transfer.title"));
    }

    private void transfer() {
        sentAt = Util.getMillis();
        sentRevision = menu.getInputTransferRevision();
        active = false;
        menu.clientTransferInputs();
    }

    private boolean pending() {
        return sentAt >= 0 && menu.getInputTransferRevision() == sentRevision && Util.getMillis() - sentAt < 3000;
    }

    @Override protected Icon getIcon() { return null; }

    @Override public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        active = !pending() && (sentAt < 0 || Util.getMillis() - sentAt >= 200);
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        if (!visible) {
            return;
        }

        int yOffset = isHovered() ? 1 : 0;
        var blitter = Blitter.texture(ICON_TEXTURE, 16, 16).src(0, 0, 16, 16);
        if (!active) {
            blitter.opacity(0.5F);
        }
        blitter.dest(getX(), getY() + 1 + yOffset).zOffset(3).blit(graphics);
    }

    @Override public List<Component> getTooltipMessage() {
        var lines = new ArrayList<Component>();
        lines.add(Component.translatable("ae2lt.gui.input_transfer.title"));
        lines.add(Component.translatable("ae2lt.gui.input_transfer.description"));
        lines.add(Component.translatable("ae2lt.gui.input_transfer.progress_warning"));
        if (pending()) {
            lines.add(Component.translatable("ae2lt.gui.input_transfer.pending"));
        } else if (menu.getInputTransferRevision() != 0) {
            var result = menu.getInputTransferResult();
            lines.add(result.accepted()
                    ? Component.translatable("ae2lt.gui.input_transfer.result", result.moved(), result.exported(), result.remainingSlots())
                    : Component.translatable("ae2lt.gui.input_transfer.cooldown"));
        }
        return lines;
    }
}
