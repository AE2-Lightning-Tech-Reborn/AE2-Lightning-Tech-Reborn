package com.moakiee.ae2lt.client.machine;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.FillDirection;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.style.FluidBlitter;
import com.lowdragmc.lowdraglib2.gui.util.UISoundUtils;
import com.moakiee.ae2lt.client.ldlib.LdlibAe2Widget;
import com.moakiee.ae2lt.client.ldlib.LdlibScreens;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import org.lwjgl.glfw.GLFW;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.fml.ModList;

import appeng.core.localization.Tooltips;

abstract class MachineFluidWidget extends LdlibAe2Widget {
    private static final int TILE_SIZE = 16;
    private final Supplier<FluidStack> fluidSupplier;
    private final IntSupplier capacitySupplier;
    private final String amountTooltip;
    private final ProgressBar fill;
    private FluidStack displayedFluid = FluidStack.EMPTY;

    MachineFluidWidget(int width, int height, Supplier<FluidStack> fluidSupplier, IntSupplier capacitySupplier,
            String amountTooltip, Runnable clear, Runnable extract, Runnable insert, UIElement... overlays) {
        this(width, height, new UIElement(), fluidSupplier, capacitySupplier, amountTooltip, clear, extract, insert,
                overlays);
    }

    private MachineFluidWidget(int width, int height, UIElement tank, Supplier<FluidStack> fluidSupplier,
            IntSupplier capacitySupplier, String amountTooltip, Runnable clear, Runnable extract, Runnable insert,
            UIElement[] overlays) {
        super(width, height, tank);
        this.fluidSupplier = fluidSupplier;
        this.capacitySupplier = capacitySupplier;
        this.amountTooltip = amountTooltip;
        fill = LdlibScreens.pixelProgress(FillDirection.DOWN_TO_UP, height);
        fill.barContainer(element -> element.setAllowHitTest(false));
        fill.barBackground.setAllowHitTest(false);
        fill.bar(element -> {
            element.setAllowHitTest(false);
            element.style(style -> style.background((graphics, mouseX, mouseY, left, top, barWidth, barHeight, ticks) ->
                    drawFluidFill(graphics, displayedFluid, left, top, barWidth, Math.round(barHeight))));
        });
        tank.addChild(LdlibScreens.at(fill, 0, 0, width, height));
        tank.addChildren(overlays);
        tank.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.isShiftDown()) {
                clear.run();
            } else if (event.button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                extract.run();
            } else if (event.button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                insert.run();
            } else {
                return;
            }
            event.stopPropagation();
            UISoundUtils.playButtonClickSound();
        });
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        displayedFluid = fluidSupplier.get();
        int capacity = Math.max(1, capacitySupplier.getAsInt());
        fill.setProgress(displayedFluid.isEmpty() ? 0
                : Math.round(fill.getMaxValue() * (double) displayedFluid.getAmount() / capacity));
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
    }

    private void drawFluidFill(GuiGraphics graphics, FluidStack fluid, float left, float top, float width,
            int filled) {
        if (fluid.isEmpty()) {
            return;
        }
        int remaining = filled;
        while (remaining > 0) {
            int chunk = Math.min(TILE_SIZE, remaining);
            var blitter = createFluidBlitter(fluid);
            if (blitter == null) {
                return;
            }
            // Keep the original bottom-up submissions and compressed full texture in the top strip.
            blitter.dest((int) left, (int) top + remaining - chunk, (int) width, chunk).blit(graphics);
            remaining -= chunk;
        }
    }

    protected Blitter createFluidBlitter(FluidStack fluid) {
        // CrystalCatalyzer used FluidBlitter's explicit blending(false).
        return FluidBlitter.create(fluid);
    }

    @Override
    protected boolean handlesInput() {
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                || UIElement.isShiftDown()) && super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public List<Component> getTooltipMessage() {
        FluidStack fluid = fluidSupplier.get();
        int capacity = capacitySupplier.getAsInt();
        List<Component> lines = new ArrayList<>();
        if (fluid.isEmpty()) {
            lines.add(Component.translatable(amountTooltip, 0, capacity)
                    .withStyle(Tooltips.NUMBER_TEXT));
        } else {
            lines.add(fluid.getHoverName());
            lines.add(Component.empty());
            lines.add(Component.translatable(amountTooltip, fluid.getAmount(), capacity)
                    .withStyle(Tooltips.NUMBER_TEXT));
            lines.add(Component.empty());
            lines.add(Component.literal(getModDisplayName(fluid))
                    .withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC));
        }
        lines.add(Component.empty());
        lines.add(Component.translatable("ae2lt.gui.fluid_tank.action.insert").withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable("ae2lt.gui.fluid_tank.action.extract").withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable("ae2lt.gui.fluid_tank.action.clear").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    @Override
    public boolean isTooltipAreaVisible() {
        return visible;
    }

    private static String getModDisplayName(FluidStack fluid) {
        var key = fluid.getFluid() == Fluids.EMPTY
                ? null
                : fluid.getFluid().builtInRegistryHolder().key().location();
        if (key == null) {
            return "Minecraft";
        }

        var namespace = key.getNamespace();
        if ("c".equals(namespace)) {
            return "Common";
        }

        return ModList.get()
                .getModContainerById(namespace)
                .map(container -> container.getModInfo().getDisplayName())
                .orElseGet(() -> namespace.replace('_', ' '));
    }
}
