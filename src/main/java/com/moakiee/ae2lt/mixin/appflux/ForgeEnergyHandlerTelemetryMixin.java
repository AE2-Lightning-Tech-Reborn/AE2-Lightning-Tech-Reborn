package com.moakiee.ae2lt.mixin.appflux;

import com.glodblock.github.appflux.common.me.energy.EnergyHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.logic.management.PowerTelemetry;
import com.moakiee.ae2lt.logic.management.AppFluxTelemetry;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.energy.IEnergyStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Forge AppFlux sends through void callbacks; observe the receivers' actual receipts. */
@Pseudo
@Mixin(targets = "com.glodblock.github.appflux.common.me.energy.EnergyHandler", remap = false)
public abstract class ForgeEnergyHandlerTelemetryMixin {
    @Inject(method = "getHandler", at = @At("RETURN"), cancellable = true)
    private static void ae2lt$locate(BlockEntity target, Direction side,
            CallbackInfoReturnable<EnergyHandler.SendAction> cir) {
        var action = cir.getReturnValue();
        if (action == null || action == EnergyHandler.SendAction.NOOP || target.getLevel() == null) return;
        var location = new PowerTelemetry.Target(target.getLevel().dimension().location().toString(),
                target.getBlockPos().immutable(), side.ordinal());
        cir.setReturnValue((storage, source) -> AppFluxTelemetry.send(source, location, () -> action.send(storage, source)));
    }

    @WrapOperation(method = "lambda$static$0", at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/energy/IEnergyStorage;receiveEnergy(IZ)I"))
    private static int ae2lt$forgeReceipt(IEnergyStorage receiver, int amount, boolean simulate,
            Operation<Integer> original) {
        int accepted = original.call(receiver, amount, simulate);
        if (!simulate && accepted >= 0 && accepted <= amount)
            AppFluxTelemetry.record(accepted);
        return accepted;
    }

    @WrapOperation(method = "lambda$static$3", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lcom/gregtechceu/gtceu/api/capability/compat/FeCompat;toFeLong(JI)J"))
    private static long ae2lt$gregtechReceipt(long eu, int ratio, Operation<Long> original) {
        long accepted = original.call(eu, ratio);
        AppFluxTelemetry.record(accepted);
        return accepted;
    }
}
