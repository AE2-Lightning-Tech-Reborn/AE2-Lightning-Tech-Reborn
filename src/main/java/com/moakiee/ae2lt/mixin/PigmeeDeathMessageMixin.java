package com.moakiee.ae2lt.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.workstation.PigmeeServerPlayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Only proxy deaths use the workstation reporter; real player messages keep native rules. */
@Mixin(ServerPlayer.class)
public abstract class PigmeeDeathMessageMixin {
    @WrapOperation(method = "die", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/GameRules;getBoolean(Lnet/minecraft/world/level/GameRules$Key;)Z"))
    private boolean ae2lt$oneProxyDeathMessage(GameRules rules, GameRules.Key<GameRules.BooleanValue> key, Operation<Boolean> original) {
        if ((Object) this instanceof PigmeeServerPlayer && key == GameRules.RULE_SHOWDEATHMESSAGES) return false;
        return original.call(rules, key);
    }
}
