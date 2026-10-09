package com.moakiee.ae2lt.integration.jei;

import java.lang.reflect.Method;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

record EaepBookmarkAdapter(Method runtimeGetter, Method itemAdder, Method fluidAdder) {
    static EaepBookmarkAdapter resolve(Class<?> helper) throws ReflectiveOperationException {
        return new EaepBookmarkAdapter(
                helper.getMethod("getRuntime"),
                helper.getMethod("addBookmark", ItemStack.class),
                helper.getMethod("addBookmark", FluidStack.class));
    }

    boolean isAvailable() throws ReflectiveOperationException {
        return runtimeGetter.invoke(null) != null;
    }

    void add(AEKey key) throws ReflectiveOperationException {
        if (key instanceof AEItemKey item) {
            itemAdder.invoke(null, item.toStack());
        } else if (key instanceof AEFluidKey fluid) {
            fluidAdder.invoke(null, fluid.toStack(1000));
        }
    }
}
