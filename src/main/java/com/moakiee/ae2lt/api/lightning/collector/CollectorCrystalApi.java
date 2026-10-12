package com.moakiee.ae2lt.api.lightning.collector;

import com.moakiee.ae2lt.logic.extension.ItemExtensionRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

public final class CollectorCrystalApi {
    private static final ItemExtensionRegistry<CollectorCrystalBehavior> BEHAVIORS = new ItemExtensionRegistry<>();
    private CollectorCrystalApi() {}

    /** Register during common setup (on both physical sides), before load-complete. Duplicates fail. */
    public static void register(ResourceLocation itemId, CollectorCrystalBehavior behavior) {
        BEHAVIORS.register(itemId, behavior);
    }

    public static @Nullable CollectorCrystalBehavior find(ItemStack stack) {
        return stack.isEmpty() ? null : BEHAVIORS.get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    @ApiStatus.Internal
    public static void freeze() { BEHAVIORS.freeze(); }
}
