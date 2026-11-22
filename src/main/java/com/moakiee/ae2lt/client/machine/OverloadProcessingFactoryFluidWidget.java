package com.moakiee.ae2lt.client.machine;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

import com.moakiee.ae2lt.menu.OverloadProcessingFactoryMenu;
import appeng.client.gui.style.Blitter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

public class OverloadProcessingFactoryFluidWidget extends MachineFluidWidget {
    private Fluid cachedFluid;
    private TextureAtlasSprite cachedSprite;

    public OverloadProcessingFactoryFluidWidget(OverloadProcessingFactoryMenu menu, int tankIndex,
            Supplier<FluidStack> fluidSupplier, IntSupplier capacitySupplier) {
        super(16, 54, fluidSupplier, capacitySupplier, "ae2lt.gui.overload_factory.fluid.tooltip",
                () -> menu.clientClearFluidTank(tankIndex), () -> menu.clientExtractFluid(tankIndex),
                () -> menu.clientInsertFluid(tankIndex));
    }

    @Override
    protected Blitter createFluidBlitter(FluidStack stack) {
        Fluid fluid = stack.getFluid();
        if (fluid != cachedFluid) {
            cachedFluid = fluid;
            var attributes = IClientFluidTypeExtensions.of(fluid);
            cachedSprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                    .apply(attributes.getStillTexture(stack));
        }
        if (cachedSprite == null) {
            return null;
        }
        return Blitter.sprite(cachedSprite)
                .colorRgb(IClientFluidTypeExtensions.of(fluid).getTintColor(stack))
                .blending(true);
    }
}
