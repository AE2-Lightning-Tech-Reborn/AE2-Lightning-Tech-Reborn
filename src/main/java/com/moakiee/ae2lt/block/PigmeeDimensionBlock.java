package com.moakiee.ae2lt.block;

import com.moakiee.ae2lt.blockentity.PigmeeDimensionBlockEntity;
import com.moakiee.ae2lt.dimension.PigmeeDimensionTravel;
import com.moakiee.ae2lt.dimension.PigmeeDimensionKind;
import com.moakiee.ae2lt.menu.PigmeeDimensionMenu;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

public final class PigmeeDimensionBlock extends Block implements EntityBlock {
    private final PigmeeDimensionKind kind;
    public PigmeeDimensionBlock(boolean even) {
        this(even ? PigmeeDimensionKind.EVEN : PigmeeDimensionKind.ODD);
    }
    public PigmeeDimensionBlock(PigmeeDimensionKind kind) {
        super(Properties.of().mapColor(MapColor.COLOR_PINK).strength(3.0F, 6.0F));
        this.kind = kind;
    }
    public PigmeeDimensionKind kind() { return kind; }
    public boolean even() { return kind == PigmeeDimensionKind.EVEN; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new PigmeeDimensionBlockEntity(pos, state); }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof PigmeeDimensionBlockEntity be) {
            var saved = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            if (saved.contains("DimensionId")) be.bind(saved.getInt("DimensionId"));
            if (saved.hasUUID("Placer")) be.setPlacer(saved.getUUID("Placer"));
            if (placer instanceof Player && be.placer() == null) be.setPlacer(placer.getUUID());
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof PigmeeDimensionBlockEntity be) {
            if (be.dimensionId() > 0 && !player.isShiftKeyDown()) {
                PigmeeDimensionTravel.enter(serverPlayer, be.dimensionId());
            } else {
                PigmeeDimensionMenu.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.ae2lt.pigmee_dimension.use").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.ae2lt.pigmee_dimension.owner").withStyle(ChatFormatting.GRAY));
    }
}
