package com.moakiee.ae2lt.mixin.appflux;

import com.glodblock.github.appflux.common.me.energy.EnergyHandler;
import com.moakiee.ae2lt.compat.gto.GtoAppFluxEnergyAdapter;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Use GTO's native class capability in place of AppFlux's obsolete Forge GTM registration. */
@Pseudo
@Mixin(targets = "com.glodblock.github.appflux.common.me.energy.EnergyHandler", remap = false)
public abstract class GtoEnergyHandlerMixin {
    @Redirect(method = "<clinit>", at = @At(value = "INVOKE",
            target = "Lcom/glodblock/github/appflux/config/AFConfig;gteuSupport()Z"))
    private static boolean ae2lt$replaceLegacyGtmRegistration() {
        // The replacement below still honors gteuSupport; only the incompatible registration is skipped.
        return false;
    }

    @Inject(method = "getHandler", at = @At(value = "FIELD", opcode = Opcodes.GETSTATIC,
            target = "Lnet/minecraftforge/common/capabilities/ForgeCapabilities;ENERGY:Lnet/minecraftforge/common/capabilities/Capability;"),
            cancellable = true)
    private static void ae2lt$nativeGtoHandler(BlockEntity target, Direction side,
            CallbackInfoReturnable<EnergyHandler.SendAction> cir) {
        // AppFlux's registered handlers (including FluxNetworks) retain priority over native EU.
        var handler = GtoAppFluxEnergyAdapter.getHandler(target, side);
        if (handler != null) cir.setReturnValue(handler);
    }
}
