package com.moakiee.ae2lt.mixin.objects;

import java.util.HashMap;
import java.util.Map;
import com.google.common.base.Function;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.performance.NbtCopyViews;
import com.moakiee.ae2lt.performance.ObjectReuseOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = CompoundTag.class, priority = 900)
public abstract class CompoundTagCopyMixin {
    @WrapOperation(method = "copy()Lnet/minecraft/nbt/CompoundTag;", at = @At(value = "INVOKE", remap = false,
            target = "Lcom/google/common/collect/Maps;transformValues(Ljava/util/Map;"
                    + "Lcom/google/common/base/Function;)Ljava/util/Map;"), require = 0)
    private Map<String, Tag> ae2lt$fuseTransform(Map<String, Tag> source,
            Function<? super Tag, Tag> transform, Operation<Map<String, Tag>> original) {
        return ObjectReuseOptions.FAST_NBT_COPIES && source.getClass() == HashMap.class
                ? new NbtCopyViews.TransformMap(source, transform) : original.call(source, transform);
    }

    @WrapOperation(method = "copy()Lnet/minecraft/nbt/CompoundTag;", at = @At(value = "INVOKE", remap = false,
            target = "Lcom/google/common/collect/Maps;newHashMap(Ljava/util/Map;)Ljava/util/HashMap;"), require = 0)
    private HashMap<String, Tag> ae2lt$collectCopy(Map<String, Tag> values,
                                                Operation<HashMap<String, Tag>> original) {
        return ObjectReuseOptions.FAST_NBT_COPIES && values instanceof NbtCopyViews.TransformMap view
                ? view.copy() : original.call(values);
    }
}
