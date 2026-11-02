package com.moakiee.ae2lt.mixin.objects;

import com.moakiee.ae2lt.performance.PlainKeyCacheOwner;
import com.moakiee.ae2lt.performance.PlainKeyReuse;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Fluid.class)
public abstract class FluidPlainKeyCacheMixin implements PlainKeyCacheOwner {
    @Unique private volatile PlainKeyReuse.Entry ae2lt$plainKey;

    @Override public PlainKeyReuse.Entry ae2lt$getPlainKey() { return ae2lt$plainKey; }
    @Override public void ae2lt$setPlainKey(PlainKeyReuse.Entry entry) { ae2lt$plainKey = entry; }
}
