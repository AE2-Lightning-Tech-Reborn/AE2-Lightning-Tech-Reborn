package com.moakiee.ae2lt.mixin.objects;

import com.moakiee.ae2lt.performance.PlainKeyCacheOwner;
import com.moakiee.ae2lt.performance.PlainKeyReuse;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Item.class)
public abstract class ItemPlainKeyCacheMixin implements PlainKeyCacheOwner {
    @Unique private volatile PlainKeyReuse.Entry ae2lt$plainKey;

    @Override public PlainKeyReuse.Entry ae2lt$getPlainKey() { return ae2lt$plainKey; }
    @Override public void ae2lt$setPlainKey(PlainKeyReuse.Entry entry) { ae2lt$plainKey = entry; }
}
