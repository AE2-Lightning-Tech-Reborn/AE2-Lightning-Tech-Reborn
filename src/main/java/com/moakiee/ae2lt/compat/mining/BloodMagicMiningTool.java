package com.moakiee.ae2lt.compat.mining;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

/** Charges harvest anointments on a prospective tool copy, without invoking real mining events. */
public final class BloodMagicMiningTool {
    private final Map<Object, Object> anointments;
    private BloodMagicMiningTool(Map<Object, Object> anointments) { this.anointments = anointments; }

    public static BloodMagicMiningTool read(ItemStack tool) {
        var modList = ModList.get();
        if (modList == null || !modList.isLoaded("bloodmagic")) return null;
        var data = tool.getTag();
        if (data == null || !data.contains("anointment_holder")) return null;
        Access.ensureSupported();
        var api = Access.API;
        try {
            Object holder = api.read().invoke(null, tool);
            @SuppressWarnings("unchecked")
            Map<Object, Object> entries = (Map<Object, Object>) api.entries().invoke(holder);
            return new BloodMagicMiningTool(new HashMap<>(entries));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) { throw failure(e); }
    }

    /** End this batch when an effect expires; remaining input starts with the updated tool next batch. */
    public int limit(int requested) {
        Access.ensureSupported();
        var api = Access.API;
        try {
            for (var entry : anointments.entrySet()) {
                if ((boolean) api.harvest().invoke(entry.getKey())) {
                    int left = (int) api.maxDamage().invoke(entry.getValue()) - (int) api.damage().invoke(entry.getValue());
                    requested = Math.min(requested, Math.max(0, left));
                }
            }
            return requested;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) { throw failure(e); }
    }

    public void consume(ItemStack tool, int processed) {
        if (tool.isEmpty() || processed == 0) return;
        Access.ensureSupported();
        var api = Access.API;
        try {
            var remaining = new HashMap<>(anointments);
            for (var entry : anointments.entrySet()) {
                if (!(boolean) api.harvest().invoke(entry.getKey())) continue;
                api.hurt().invoke(entry.getValue(), processed);
                if ((boolean) api.expired().invoke(entry.getValue())) remaining.remove(entry.getKey());
            }
            Object holder = api.constructor().newInstance(remaining);
            for (Object removed : anointments.keySet()) {
                if (!remaining.containsKey(removed)) {
                    // Apply only the item cleanup hook, not the holder's world sound/particle wrapper.
                    api.remove().invoke(removed, holder, tool, EquipmentSlot.MAINHAND);
                }
            }
            api.write().invoke(holder, tool);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) { throw failure(e); }
    }

    private static IllegalStateException failure(Throwable e) {
        return new IllegalStateException("Blood Magic mining anointment integration failed", e);
    }
    private static final class Access {
        private static final Api API;
        private static final Throwable FAILURE;
        static {
            Api api = null;
            Throwable failure = null;
            try {
                var holder = Class.forName("wayoftime.bloodmagic.anointment.AnointmentHolder");
                var anointment = Class.forName("wayoftime.bloodmagic.anointment.Anointment");
                var data = Class.forName("wayoftime.bloodmagic.anointment.AnointmentData");
                api = new Api(holder.getConstructor(Map.class),
                        method(holder, "fromItemStack", holder, true, ItemStack.class),
                        method(holder, "getAnointments", Map.class, false),
                        method(anointment, "consumeOnHarvest", boolean.class, false),
                        method(data, "getMaxDamage", int.class, false),
                        method(data, "getDamage", int.class, false),
                        method(data, "damage", void.class, false, int.class),
                        method(data, "isMaxDamage", boolean.class, false),
                        method(anointment, "removeAnointment", boolean.class, false,
                                holder, ItemStack.class, EquipmentSlot.class),
                        method(holder, "toItemStack", void.class, false, ItemStack.class));
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                failure = e;
            }
            API = api;
            FAILURE = failure;
        }

        private static void ensureSupported() {
            if (FAILURE != null) throw new IllegalStateException("Unsupported Blood Magic mining API", FAILURE);
        }

        private static Method method(Class<?> owner, String name, Class<?> returnType,
                boolean isStatic, Class<?>... parameters) throws NoSuchMethodException {
            var method = owner.getMethod(name, parameters);
            if (Modifier.isStatic(method.getModifiers()) != isStatic
                    || !returnType.isAssignableFrom(method.getReturnType())) {
                throw new NoSuchMethodException("Incompatible Blood Magic mining method: " + name);
            }
            return method;
        }

        private record Api(Constructor<?> constructor, Method read, Method entries, Method harvest,
                Method maxDamage, Method damage, Method hurt, Method expired, Method remove, Method write) {}
    }
}
