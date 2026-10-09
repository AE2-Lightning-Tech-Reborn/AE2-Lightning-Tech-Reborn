package com.moakiee.ae2lt.block;

import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.StateDefinition;
import appeng.block.crafting.PatternProviderBlock;
import appeng.block.crafting.PushDirection;
import appeng.core.definitions.AEBlocks;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import appeng.menu.locator.MenuLocators;
import appeng.util.InteractionUtil;

public class OverloadedInterfaceBlock extends AE2LTBaseEntityBlock<OverloadedInterfaceBlockEntity> {

    public OverloadedInterfaceBlock() {
        super(metalProps().forceSolidOn());
        registerDefaultState(defaultBlockState().setValue(PatternProviderBlock.PUSH_DIRECTION, PushDirection.ALL));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PatternProviderBlock.PUSH_DIRECTION);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack heldItem, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (InteractionUtil.canWrenchRotate(heldItem)) {
            // AE2's native all/opposite/clicked-face/rotation behavior. This
            // method only updates PUSH_DIRECTION, shared by our blockstate.
            AEBlocks.PATTERN_PROVIDER.block().setSide(level, pos, hit.getDirection());
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return super.useItemOn(heldItem, state, level, pos, player, hand, hit);
    }

    @Override
    public void appendHoverText(net.minecraft.world.item.ItemStack stack,
                                net.minecraft.world.level.BlockGetter context,
                                java.util.List<net.minecraft.network.chat.Component> tooltip,
                                net.minecraft.world.item.TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        var tag = stack.getTag();
        var blockEntityTag = stack.getTagElement("BlockEntityTag");
        if ((tag != null && tag.contains("ae2ltPassiveInput", net.minecraft.nbt.Tag.TAG_LIST))
                || (blockEntityTag != null && blockEntityTag.contains(
                        "ae2ltPassiveInput", net.minecraft.nbt.Tag.TAG_LIST))) {
            tooltip.add(net.minecraft.network.chat.Component.translatable(
                    "tooltip.ae2lt.overloaded_interface.pending_input")
                    .withStyle(net.minecraft.ChatFormatting.YELLOW));
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (InteractionUtil.isInAlternateUseMode(player)) {
            return InteractionResult.PASS;
        }

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
