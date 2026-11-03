/* SPDX-License-Identifier: LGPL-3.0-or-later
 * Adapted from Thunderbolt Core Reborn dc4d9bc919edd26ef737c5cd7b1ef5fbae76439e.
 */
package com.moakiee.ae2lt.mixin.objects;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import com.google.common.base.Function;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.performance.NbtCopyViews;
import com.moakiee.ae2lt.performance.ObjectReuseOptions;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ListTag.class, priority = 900)
public abstract class ListTagCopyMixin {
    @Shadow @Final private List<Tag> list;

    @WrapOperation(method = "copy()Lnet/minecraft/nbt/ListTag;", at = @At(value = "INVOKE", remap = false,
            target = "Lcom/google/common/collect/Iterables;transform(Ljava/lang/Iterable;"
                    + "Lcom/google/common/base/Function;)Ljava/lang/Iterable;"), require = 0)
    private Iterable<Tag> ae2lt$fuseTransform(Iterable<Tag> source, Function<? super Tag, Tag> transform,
                                            Operation<Iterable<Tag>> original) {
        return ObjectReuseOptions.FAST_NBT_COPIES && source.getClass() == ArrayList.class
                ? new NbtCopyViews.TransformIterable(source, transform) : original.call(source, transform);
    }

    @WrapOperation(method = "copy()Lnet/minecraft/nbt/ListTag;", at = @At(value = "INVOKE", remap = false,
            target = "Lcom/google/common/collect/Lists;newArrayList(Ljava/lang/Iterable;)Ljava/util/ArrayList;"), require = 0)
    private ArrayList<Tag> ae2lt$collectCopy(Iterable<Tag> values, Operation<ArrayList<Tag>> original) {
        if (!ObjectReuseOptions.FAST_NBT_COPIES || values instanceof Collection<?>) return original.call(values);
        if (values instanceof NbtCopyViews.TransformIterable view) return view.copy(list.size());
        var result = new ArrayList<Tag>(list.size());
        for (var value : values) result.add(value);
        return result;
    }
}
