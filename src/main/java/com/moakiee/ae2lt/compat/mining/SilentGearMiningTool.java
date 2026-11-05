package com.moakiee.ae2lt.compat.mining;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

/**
 * Silent Gear keeps broken gear instead of consuming it, and it treats the last durability point as
 * already broken. A plain damage check therefore accepts unusable gear.
 */
public final class SilentGearMiningTool {
    private SilentGearMiningTool() {}

    public static boolean isBroken(ItemStack stack) {
        var modList = ModList.get();
        if (modList == null || !modList.isLoaded("silentgear")) return false;
        Access.ensureSupported();
        var api = Access.API;
        if (!api.coreItem().isInstance(stack.getItem())) return false;
        try {
            // Use the native predicate: permanent breakage and the indestructible trait change its meaning.
            return (boolean) api.isBroken().invoke(null, stack);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            throw new IllegalStateException("Silent Gear mining tool integration failed", e);
        }
    }

    private static final class Access {
        private static final Api API;
        private static final Throwable FAILURE;
        static {
            Api api = null;
            Throwable failure = null;
            try {
                // ICoreItem is the 1.20.1 interface implemented by every Silent Gear equipment item.
                var coreItem = Class.forName("net.silentchaos512.gear.api.item.ICoreItem");
                var isBroken = Class.forName("net.silentchaos512.gear.util.GearHelper")
                        .getMethod("isBroken", ItemStack.class);
                if (!Modifier.isStatic(isBroken.getModifiers()) || isBroken.getReturnType() != boolean.class) {
                    throw new NoSuchMethodException("Incompatible Silent Gear isBroken method");
                }
                api = new Api(coreItem, isBroken);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                failure = e;
            }
            API = api;
            FAILURE = failure;
        }

        private static void ensureSupported() {
            if (FAILURE != null) throw new IllegalStateException("Unsupported Silent Gear mining API", FAILURE);
        }

        private record Api(Class<?> coreItem, Method isBroken) {}
    }
}
