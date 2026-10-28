package com.moakiee.ae2lt.mixin.objects;

import appeng.api.stacks.AEFluidKey;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.moakiee.ae2lt.performance.ObjectReuseOptions;
import com.moakiee.ae2lt.performance.PlainKeyReuse;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = AEFluidKey.class, remap = false)
public abstract class AEFluidKeyReuseMixin {
    @WrapMethod(method = "of(Lnet/minecraft/world/level/material/Fluid;Lnet/minecraft/nbt/CompoundTag;)"
            + "Lappeng/api/stacks/AEFluidKey;")
    private static AEFluidKey ae2lt$reusePlainKey(Fluid fluid, CompoundTag tag, Operation<AEFluidKey> original) {
        if (!ObjectReuseOptions.PLAIN_KEYS || tag != null) return original.call(fluid, tag);
        Object generation = PlainKeyReuse.generation();
        var cached = (AEFluidKey) PlainKeyReuse.find(fluid, generation);
        return cached != null ? cached : PlainKeyReuse.publish(fluid, generation, original.call(fluid, tag));
    }
}
