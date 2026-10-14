package com.moakiee.ae2lt.compat.gto;

import java.lang.reflect.Field;

/** Read-only bridge for GTO's remaining output; no injection into GTO classes is required. */
public final class GtoCpuJobAccess {
    private record Fields(Field job, Field remaining) { }
    private static final ClassValue<Fields> FIELDS = new ClassValue<>() {
        @Override protected Fields computeValue(Class<?> type) {
            for (Class<?> owner = type; owner != null; owner = owner.getSuperclass()) {
                if (!owner.getName().equals("com.gtocore.api.ae2.crafting.OptimizedCraftingCpuLogic")) continue;
                try {
                    Field job = owner.getDeclaredField("job");
                    Field remaining = job.getType().getDeclaredField("remainingAmount");
                    job.setAccessible(true);
                    remaining.setAccessible(true);
                    return new Fields(job, remaining);
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException("Unsupported GTO crafting CPU", exception);
                }
            }
            throw new IllegalArgumentException("Expected GTO crafting CPU: " + type.getName());
        }
    };
    private GtoCpuJobAccess() { }
    public static long remaining(Object cpu) {
        try {
            var fields = FIELDS.get(cpu.getClass());
            var job = fields.job().get(cpu);
            return job == null ? 0 : Math.max(0, fields.remaining().getLong(job));
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot read GTO crafting job", exception);
        }
    }
}
