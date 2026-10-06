package com.moakiee.ae2lt.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.level.block.entity.BlockEntity;

import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity;

@Mixin(BlockEntity.class)
public abstract class BlockEntityInventoryChangeMixin {
    @Inject(method = "setChanged()V", at = @At("TAIL"), require = 0, expect = 0)
    private void ae2lt$wakeWirelessImport(CallbackInfo callback) {
        OverloadedInterfaceBlockEntity.onTargetInventoryChanged((BlockEntity) (Object) this);
    }
}
