package com.moakiee.ae2lt.client.widgets;

import java.util.List;
import java.util.function.BooleanSupplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import appeng.client.gui.Icon;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.widgets.IconButton;

import com.moakiee.ae2lt.AE2LightningTech;

public final class PageButton extends IconButton {
    private static final ResourceLocation ICON_TEXTURE = new ResourceLocation(
            AE2LightningTech.MODID, "textures/gui/buttons/page_navigation.png");

    private final BooleanSupplier handlingRightClick;
    private final Runnable previous;
    private final Runnable next;
    private boolean canGoPrevious;
    private boolean canGoNext;

    public PageButton(BooleanSupplier handlingRightClick, Runnable previous, Runnable next) {
        super(button -> {});
        setDisableBackground(true);
        this.handlingRightClick = handlingRightClick;
        this.previous = previous;
        this.next = next;
        setMessage(Component.translatable("ae2lt.gui.page.navigate"));
    }

    public void setPage(int page, int totalPages) {
        boolean paginated = totalPages > 1;
        setVisibility(paginated);
        active = paginated;
        canGoPrevious = paginated && page > 0;
        canGoNext = paginated && page < totalPages - 1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active || !visible || !clicked(mouseX, mouseY)) {
            return false;
        }

        boolean rightClickRemapped = handlingRightClick.getAsBoolean();
        if (PageInput.isNextClick(button, rightClickRemapped) && canGoNext) {
            playDownSound(Minecraft.getInstance().getSoundManager());
            next.run();
            return true;
        }
        if (PageInput.isPreviousClick(button, rightClickRemapped) && canGoPrevious) {
            playDownSound(Minecraft.getInstance().getSoundManager());
            previous.run();
            return true;
        }
        return false;
    }

    @Override
    public void onPress() {
        if (canGoNext) {
            next.run();
        }
    }

    @Override
    protected Icon getIcon() {
        return Icon.TOOLBAR_BUTTON_BACKGROUND;
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!visible) {
            return;
        }
        super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);

        var blitter = Blitter.texture(ICON_TEXTURE, 16, 16)
                .src(0, 0, 16, 16);
        if (!active) {
            blitter.opacity(0.5F);
        }
        blitter.dest(getX(), getY())
                .blit(guiGraphics);
    }

    @Override
    public List<Component> getTooltipMessage() {
        return List.of(
                Component.translatable("ae2lt.gui.page.next_left_click"),
                Component.translatable("ae2lt.gui.page.previous_right_click"));
    }
}
