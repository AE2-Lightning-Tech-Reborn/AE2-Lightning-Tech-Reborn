package com.moakiee.ae2lt.compat.mining;

import java.lang.reflect.Method;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/** Silent Gear retains broken items, sometimes with stale tool components in saved stacks. */
public final class SilentGearMiningTool {
    private SilentGearMiningTool() {}

    public static boolean isBroken(ItemStack stack) {
        if (!ModList.get().isLoaded("silentgear") || !Access.GEAR_ITEM.isInstance(stack.getItem())) return false;
        try {
            // Use the native predicate: permanent breakage and indestructible traits change its meaning.
            return (boolean) Access.IS_BROKEN.invoke(null, stack);
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new IllegalStateException("Silent Gear mining tool integration failed", e);
        }
    }

    private static final class Access {
        private static final Class<?> GEAR_ITEM;
        private static final Method IS_BROKEN;
        static {
            try {
                GEAR_ITEM = Class.forName("net.silentchaos512.gear.api.item.GearItem");
                IS_BROKEN = Class.forName("net.silentchaos512.gear.util.GearHelper")
                        .getMethod("isBroken", ItemStack.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                throw new IllegalStateException("Unsupported Silent Gear mining API", e);
            }
        }
    }
}
