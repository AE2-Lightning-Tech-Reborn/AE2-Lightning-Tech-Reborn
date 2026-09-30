package com.moakiee.ae2lt.block;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.block.AEBaseEntityBlock;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import com.moakiee.ae2lt.blockentity.OverloadedIOPortBlockEntity;
import com.moakiee.ae2lt.menu.OverloadedIOPortMenu;
import com.moakiee.ae2lt.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import java.util.List;

public class OverloadedIOPortBlock extends AEBaseEntityBlock<OverloadedIOPortBlockEntity> {
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");
    public OverloadedIOPortBlock() {
        super(metalProps());
        registerDefaultState(defaultBlockState().setValue(POWERED, false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder); builder.add(POWERED);
    }
    @Override public IOrientationStrategy getOrientationStrategy() { return OrientationStrategies.full(); }
    @Override protected BlockState updateBlockStateFromBlockEntity(BlockState state, OverloadedIOPortBlockEntity be) {
        return state.setValue(POWERED, be.isActive());
    }
    @Override public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                          BlockPos from, boolean moving) {
        var be = getBlockEntity(level, pos);
        if (be != null) be.updateRedstoneState();
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                         Player player, BlockHitResult hit) {
        var be = getBlockEntity(level, pos);
        if (be == null) return InteractionResult.PASS;
        if (!level.isClientSide) MenuOpener.open(OverloadedIOPortMenu.TYPE, player, MenuLocators.forBlockEntity(be));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                          List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        lines.add(Component.translatable("tooltip.ae2lt.overloaded_io_port.batch"));
        lines.add(Component.translatable("tooltip.ae2lt.overloaded_io_port.power"));
        if (stack.has(ModDataComponents.IO_PORT_PENDING.get()))
            lines.add(Component.translatable("tooltip.ae2lt.overloaded_io_port.pending")
                    .withStyle(net.minecraft.ChatFormatting.YELLOW));
    }
}
