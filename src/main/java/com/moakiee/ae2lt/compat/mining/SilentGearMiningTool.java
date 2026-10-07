package com.moakiee.ae2lt.compat.mining;

import java.lang.reflect.Method;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

/**
 * Silent Gear keeps broken gear instead of consuming it, and it treats the last durability point as
 * already broken. A plain damage check therefore accepts unusable gear.
 */
public final class SilentGearMiningTool {
    private SilentGearMiningTool() {}

    public static boolean isBroken(ItemStack stack) {
        if (!ModList.get().isLoaded("silentgear") || !Access.CORE_ITEM.isInstance(stack.getItem())) return false;
        try {
            // Use the native predicate: permanent breakage and the indestructible trait change its meaning.
            return (boolean) Access.IS_BROKEN.invoke(null, stack);
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new IllegalStateException("Silent Gear mining tool integration failed", e);
        }
    }

    private static final class Access {
        private static final Class<?> CORE_ITEM;
        private static final Method IS_BROKEN;
        static {
            try {
                // ICoreItem is the 1.20.1 interface implemented by every Silent Gear equipment item.
                CORE_ITEM = Class.forName("net.silentchaos512.gear.api.item.ICoreItem");
                IS_BROKEN = Class.forName("net.silentchaos512.gear.util.GearHelper")
                        .getMethod("isBroken", ItemStack.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                throw new IllegalStateException("Unsupported Silent Gear mining API", e);
            }
        }
    }
}
