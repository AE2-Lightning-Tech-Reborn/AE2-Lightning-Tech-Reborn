package com.moakiee.ae2lt.mixin;

import com.moakiee.ae2lt.celestweave.CelestweaveArmorUndyingHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    /**
     * Complements the loot guard for mods that copy LivingEntity#die instead of invoking it.
     * Only the death event byte is suppressed; every unrelated entity event is untouched.
     */
    @Inject(method = "broadcastEntityEvent", at = @At("HEAD"), cancellable = true)
    private void ae2lt$suppressProtectedCopiedDeathAnimation(
            Entity entity,
            byte eventId,
            CallbackInfo ci) {
        if (eventId == EntityEvent.DEATH
                && entity instanceof ServerPlayer player
                && CelestweaveArmorUndyingHandler.protectBeforeDeathSideEffect(player)) {
            ci.cancel();
        }
    }
}
