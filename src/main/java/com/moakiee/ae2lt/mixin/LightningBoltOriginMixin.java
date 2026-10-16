package com.moakiee.ae2lt.mixin;

import com.moakiee.ae2lt.util.NaturalLightningOrigin;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.LightningBolt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightningBolt.class)
public abstract class LightningBoltOriginMixin implements NaturalLightningOrigin {
    @Unique
    private static final EntityDataAccessor<Boolean> ae2lt$naturalWeatherLightning =
            SynchedEntityData.defineId(LightningBolt.class, EntityDataSerializers.BOOLEAN);

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void ae2lt$defineOrigin(CallbackInfo ci) {
        ((LightningBolt) (Object) this).getEntityData().define(ae2lt$naturalWeatherLightning, false);
    }

    @Override
    public boolean ae2lt$isNaturalWeatherLightning() {
        return ((LightningBolt) (Object) this).getEntityData().get(ae2lt$naturalWeatherLightning);
    }

    @Override
    public void ae2lt$setNaturalWeatherLightning(boolean natural) {
        ((LightningBolt) (Object) this).getEntityData().set(ae2lt$naturalWeatherLightning, natural);
    }
}
