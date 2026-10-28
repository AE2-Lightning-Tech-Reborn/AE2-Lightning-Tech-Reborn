package com.moakiee.ae2lt.mixin.objects;

import appeng.api.stacks.AEItemKey;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.moakiee.ae2lt.performance.ObjectReuseOptions;
import com.moakiee.ae2lt.performance.PlainKeyReuse;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ItemLike;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = AEItemKey.class, remap = false)
public abstract class AEItemKeyReuseMixin {
    @WrapMethod(method = "of(Lnet/minecraft/world/level/ItemLike;Lnet/minecraft/nbt/CompoundTag;"
            + "Lnet/minecraft/nbt/CompoundTag;)Lappeng/api/stacks/AEItemKey;")
    private static AEItemKey ae2lt$reusePlainKey(ItemLike itemLike, CompoundTag tag, CompoundTag caps,
                                               Operation<AEItemKey> original) {
        if (!ObjectReuseOptions.PLAIN_KEYS || tag != null || caps != null) {
            return original.call(itemLike, tag, caps);
        }
        var item = itemLike.asItem();
        if (!PlainKeyReuse.hasStaticStackLimit(item.getClass())) return original.call(itemLike, tag, caps);
        Object generation = PlainKeyReuse.generation();
        var cached = (AEItemKey) PlainKeyReuse.find(item, generation);
        return cached != null ? cached : PlainKeyReuse.publish(item, generation, original.call(item, tag, caps));
    }
}
