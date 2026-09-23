package com.moakiee.ae2lt.item;

import com.moakiee.ae2lt.block.TianshuSupercomputingUnitBlock;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** Item tooltip for Tianshu structure blocks in the 26.1 item API. */
public final class TianshuSupercomputingUnitItem extends BlockItem {
    public TianshuSupercomputingUnitItem(TianshuSupercomputingUnitBlock block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
            Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        ((TianshuSupercomputingUnitBlock) getBlock()).appendItemTooltip(tooltip);
    }
}
