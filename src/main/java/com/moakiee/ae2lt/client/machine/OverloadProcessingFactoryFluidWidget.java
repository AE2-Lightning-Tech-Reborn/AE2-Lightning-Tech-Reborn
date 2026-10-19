package com.moakiee.ae2lt.client.machine;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.PoseStack;
import com.moakiee.ae2lt.client.compat.LegacyGui;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.ModList;

import appeng.client.gui.style.Blitter;
import appeng.core.localization.Tooltips;
import appeng.client.gui.widgets.ITooltip;

import com.moakiee.ae2lt.menu.OverloadProcessingFactoryMenu;

public class OverloadProcessingFactoryFluidWidget extends AbstractWidget implements ITooltip {
    private final Supplier<FluidStack> fluidSupplier;
    private final IntSupplier capacitySupplier;
    private final OverloadProcessingFactoryMenu menu;
    private final int tankIndex;

    private Fluid cachedFluid;
    private TextureAtlasSprite cachedSprite;

    public OverloadProcessingFactoryFluidWidget(
            OverloadProcessingFactoryMenu menu,
            int tankIndex,
            Supplier<FluidStack> fluidSupplier,
            IntSupplier capacitySupplier) {
        super(0, 0, 16, 54, Component.empty());
        this.menu = menu;
        this.tankIndex = tankIndex;
        this.fluidSupplier = fluidSupplier;
        this.capacitySupplier = capacitySupplier;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // AE2 WidgetContainer 只分发 button=0;右键/中键由 Screen 层的 mouseClicked
        // 拦截后调用 {@link #handleClick(int)}。
        if (!this.active || !this.visible || !isMouseOver(mouseX, mouseY) || button != 0) {
            return false;
        }
        return handleClick(button);
    }

    /** 由 Screen 层拦截后调用,支持左键/右键/shift。 */
    public boolean handleClick(int button) {
        if (!this.active || !this.visible) {
            return false;
        }
        if (Screen.hasShiftDown()) {
            menu.clientClearFluidTank(tankIndex);
            playClickSound();
            return true;
        }
        if (button == 0) {
            menu.clientExtractFluid(tankIndex);
            playClickSound();
            return true;
        }
        if (button == 1) {
            menu.clientInsertFluid(tankIndex);
            playClickSound();
            return true;
        }
        return false;
    }

    private void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public void renderButton(PoseStack guiGraphics, int mouseX, int mouseY, float partialTick) {
        FluidStack fluid = fluidSupplier.get();
        if (fluid.isEmpty()) {
            return;
        }

        int capacity = Math.max(1, capacitySupplier.getAsInt());
        int filled = (int) Math.round(height * (double) fluid.getAmount() / (double) capacity);
        if (filled <= 0) {
            return;
        }
        filled = Math.min(filled, height);

        TextureAtlasSprite sprite = resolveSprite(fluid);
        if (sprite == null) {
            return;
        }

        var attributes = IClientFluidTypeExtensions.of(fluid.getFluid());
        Blitter blitter = Blitter.sprite(sprite)
                .colorRgb(attributes.getTintColor(fluid))
                .blending(true);

        int x = this.x;
        int yBottom = y + height;
        int drawn = 0;
        while (drawn < filled) {
            int sliceH = Math.min(width, filled - drawn);
            blitter.dest(x, yBottom - drawn - sliceH, width, sliceH).blit(guiGraphics, 0);
            drawn += sliceH;
        }
    }

    private TextureAtlasSprite resolveSprite(FluidStack stack) {
        Fluid fluid = stack.getFluid();
        if (fluid != cachedFluid) {
            cachedFluid = fluid;
            var attributes = IClientFluidTypeExtensions.of(fluid);
            cachedSprite = Minecraft.getInstance()
                    .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                    .apply(attributes.getStillTexture(stack));
        }
        return cachedSprite;
    }

    @Override
    public List<Component> getTooltipMessage() {
        FluidStack fluid = fluidSupplier.get();
        int capacity = capacitySupplier.getAsInt();
        List<Component> lines = new ArrayList<>();
        if (fluid.isEmpty()) {
            lines.add(Component.translatable("ae2lt.gui.overload_factory.fluid.tooltip", 0, capacity)
                    .withStyle(Tooltips.NUMBER_TEXT));
        } else {
            lines.add(fluid.getDisplayName());
            lines.add(Component.empty());
            lines.add(Component.translatable("ae2lt.gui.overload_factory.fluid.tooltip", fluid.getAmount(), capacity)
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
    public Rect2i getTooltipArea() {
        return new Rect2i(x, y, width, height);
    }

    @Override
    public boolean isTooltipAreaVisible() {
        return true;
    }

    @Override
    public void updateNarration(NarrationElementOutput narrationElementOutput) {
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
