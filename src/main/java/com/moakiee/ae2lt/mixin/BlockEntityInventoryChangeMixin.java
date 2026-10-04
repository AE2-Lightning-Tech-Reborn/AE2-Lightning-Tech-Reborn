package com.moakiee.ae2lt.mixin;

import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntity.class)
public abstract class BlockEntityInventoryChangeMixin {
    @Inject(method = "setChanged", at = @At("TAIL"))
    private void ae2lt$wakeWirelessImport(CallbackInfo callback) {
        OverloadedInterfaceBlockEntity.onTargetInventoryChanged((BlockEntity) (Object) this);
    }
}
