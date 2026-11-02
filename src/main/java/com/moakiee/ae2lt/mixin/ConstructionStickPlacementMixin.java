package com.moakiee.ae2lt.mixin;

import com.moakiee.ae2lt.event.FrequencyCardAutoConnectHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Construction Sticks posts EntityPlaceEvent before setBlockAndUpdate. Observe only its successful return. */
@Pseudo
@Mixin(targets = "mrbysco.constructionstick.basics.StickUtil", remap = false)
public abstract class ConstructionStickPlacementMixin {
    @Inject(method = "placeBlock", at = @At("RETURN"), require = 0, remap = false)
    private static void ae2lt$connectPlacedMachine(Level level, Player player, BlockState state, BlockPos pos,
            BlockItem item, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            FrequencyCardAutoConnectHandler.onSuccessfulPlacement(serverPlayer, serverLevel, pos);
        }
    }
}
