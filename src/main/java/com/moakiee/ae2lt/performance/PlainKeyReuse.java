package com.moakiee.ae2lt.performance;

import appeng.api.stacks.AEKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.extensions.IForgeItem;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge 1.20.1 adaptation of Thunderbolt's plain resource key reuse (LGPL-3.0-or-later).
 * No NBT/capability canonicalization. AEItemKey.of(stack) still serializes capabilities and
 * updates its stack limit through the original AE2 code on every invocation.
 */
@Mod.EventBusSubscriber(modid = "ae2lt")
public final class PlainKeyReuse {
    public record Entry(Object generation, AEKey key) {}

    private static volatile Object generation = new Object();
    private static final ClassValue<Boolean> STATIC_STACK_LIMIT = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("getMaxStackSize", ItemStack.class).getDeclaringClass() == IForgeItem.class;
            } catch (ReflectiveOperationException | SecurityException ignored) {
                return false;
            }
        }
    };

    private PlainKeyReuse() {}

    public static boolean hasStaticStackLimit(Class<?> type) {
        return STATIC_STACK_LIMIT.get(type);
    }

    public static Object generation() {
        return generation;
    }

    public static AEKey find(Object resource, Object observedGeneration) {
        if (!(resource instanceof PlainKeyCacheOwner owner)) return null;
        Entry entry = owner.ae2lt$getPlainKey();
        return entry != null && entry.generation() == observedGeneration ? entry.key() : null;
    }

    public static <K extends AEKey> K publish(Object resource, Object observedGeneration, K created) {
        if (!(resource instanceof PlainKeyCacheOwner owner)) return created;
        // Constructors run outside the monitor. A late constructor cannot republish an old generation.
        synchronized (owner) {
            if (generation != observedGeneration) return created;
            Entry entry = owner.ae2lt$getPlainKey();
            if (entry == null || entry.generation() != observedGeneration) {
                owner.ae2lt$setPlainKey(new Entry(observedGeneration, created));
                return created;
            }
            @SuppressWarnings("unchecked") K cached = (K) entry.key();
            return cached;
        }
    }

    public static void clear() {
        generation = new Object();
        for (var item : BuiltInRegistries.ITEM) detach(item);
        for (var fluid : BuiltInRegistries.FLUID) detach(fluid);
    }

    private static void detach(Object resource) {
        if (resource instanceof PlainKeyCacheOwner owner) {
            synchronized (owner) {
                owner.ae2lt$setPlainKey(null);
            }
        }
    }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        clear();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        clear();
    }
}
