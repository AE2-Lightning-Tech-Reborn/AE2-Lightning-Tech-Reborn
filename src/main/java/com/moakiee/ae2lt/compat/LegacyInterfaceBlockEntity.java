package com.moakiee.ae2lt.compat;

import appeng.blockentity.misc.InterfaceBlockEntity;
import appeng.helpers.InterfaceLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** AE2 12 creates its interface logic inline, before addons can supply their implementation. */
public abstract class LegacyInterfaceBlockEntity extends InterfaceBlockEntity {
    protected LegacyInterfaceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        try {
            var field = InterfaceBlockEntity.class.getDeclaredField("logic");
            field.setAccessible(true);
            field.set(this, createLogic());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot install the overloaded interface logic", failure);
        }
    }
    protected abstract InterfaceLogic createLogic();
}
