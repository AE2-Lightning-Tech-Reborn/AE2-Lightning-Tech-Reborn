package com.moakiee.ae2lt.item;

import com.moakiee.ae2lt.block.PigmeeBuildingBlock;
import com.moakiee.ae2lt.block.PigmeeBuildingPanelBlock;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

/** 26.1 routes block item tooltips through the item rather than the vanilla block. */
public final class PigmeeBuildingBlockItem extends BlockItem {
    public PigmeeBuildingBlockItem(Block block, Properties properties) { super(block, properties); }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        if (getBlock() instanceof PigmeeBuildingBlock base) base.appendHoverText(stack, context, tooltip, flag);
        else if (getBlock() instanceof PigmeeBuildingPanelBlock panel) panel.appendHoverText(stack, context, tooltip, flag);
    }
}
