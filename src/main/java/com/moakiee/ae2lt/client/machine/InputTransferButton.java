package com.moakiee.ae2lt.client.machine;

import java.util.ArrayList;
import java.util.List;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.moakiee.ae2lt.client.ldlib.LdlibAe2Button;
import com.moakiee.ae2lt.client.ldlib.LdlibStyleSprite;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.menu.InputTransferMenu;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Immediate local feedback, with authoritative inventory changes coming from the server. */
final class InputTransferButton extends LdlibAe2Button {
    private static final ResourceLocation ICON_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            AE2LightningTech.MODID, "textures/gui/buttons/input_transfer.png");
    private static final LdlibStyleSprite ATLAS = new LdlibStyleSprite(ICON_TEXTURE, 16, 16);
    private static final SpriteTexture ICON = ATLAS.full();
    private static final SpriteTexture DISABLED_ICON = ATLAS.full().setColor(0x7FFFFFFF);

    private final InputTransferMenu menu;
    private long sentAt = -1;
    private int sentRevision;

    InputTransferButton(InputTransferMenu menu) {
        super(16, Component.translatable("ae2lt.gui.input_transfer.title"), menu::clientTransferInputs);
        this.menu = menu;
        toolbarFace(IGuiTexture.dynamic(() -> active ? ICON : DISABLED_ICON), false);
    }

    @Override
    public void onPress() {
        if (!active || !visible) {
            return;
        }
        sentAt = Util.getMillis();
        sentRevision = menu.getInputTransferRevision();
        active = false;
        menu.clientTransferInputs();
    }

    private boolean pending() {
        return sentAt >= 0 && menu.getInputTransferRevision() == sentRevision && Util.getMillis() - sentAt < 3000;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        active = !pending() && (sentAt < 0 || Util.getMillis() - sentAt >= 200);
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
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
