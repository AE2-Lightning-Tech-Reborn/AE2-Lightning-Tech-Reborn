package com.moakiee.ae2lt.block;

import com.moakiee.ae2lt.menu.OverloadAlloyAnvilMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.state.BlockState;

/** A falling, wear-free anvil with the vanilla anvil recipe and experience costs. */
public final class OverloadAlloyAnvilBlock extends AnvilBlock {
    public OverloadAlloyAnvilBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new SimpleMenuProvider((id, inventory, player) ->
                new OverloadAlloyAnvilMenu(id, inventory, ContainerLevelAccess.create(level, pos)),
                Component.translatable("block.ae2lt.overload_alloy_anvil"));
    }
}
