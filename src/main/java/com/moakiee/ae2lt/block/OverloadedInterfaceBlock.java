package com.moakiee.ae2lt.block;

import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import appeng.block.AEBaseEntityBlock;
import appeng.menu.locator.MenuLocators;

public class OverloadedInterfaceBlock extends AEBaseEntityBlock<OverloadedInterfaceBlockEntity> {

    public OverloadedInterfaceBlock() {
        super(metalProps().forceSolidOn());
    }

    @Override
    public void appendHoverText(net.minecraft.world.item.ItemStack stack,
                                net.minecraft.world.item.Item.TooltipContext context,
                                java.util.List<net.minecraft.network.chat.Component> tooltip,
                                net.minecraft.world.item.TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (stack.has(com.moakiee.ae2lt.registry.ModDataComponents.INTERFACE_INPUT_BUFFER.get())) {
            tooltip.add(net.minecraft.network.chat.Component.translatable(
                    "tooltip.ae2lt.overloaded_interface.pending_input")
                    .withStyle(net.minecraft.ChatFormatting.YELLOW));
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        var be = this.getBlockEntity(level, pos);
        if (be != null) {
            if (!level.isClientSide()) {
                be.openMenu(player, MenuLocators.forBlockEntity(be));
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return InteractionResult.PASS;
    }
}
