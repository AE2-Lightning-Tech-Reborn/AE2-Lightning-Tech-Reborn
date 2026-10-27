package com.moakiee.ae2lt.compat.gto;

import appeng.crafting.execution.CraftingCpuLogic;
import net.minecraft.nbt.CompoundTag;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.*;
import java.util.function.ToLongFunction;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Uses GTO's persisted contract, independent of randomized implementation/member names. */
public final class GtoCpuJobAccess {
    private static final ClassValue<ToLongFunction<CraftingCpuLogic>> READERS = new ClassValue<>() {
        @Override protected ToLongFunction<CraftingCpuLogic> computeValue(Class<?> type) {
            try {
                for (var field : type.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                    boolean job = java.util.Arrays.stream(field.getType().getDeclaredFields())
                            .anyMatch(member -> member.getType() == appeng.crafting.CraftingLink.class);
                    if (!job) continue;
                    var remaining = remainingField(field.getType());
                    if (remaining == null) continue;
                    var jobHandle = handle(field); var remainingHandle = handle(remaining);
                    return cpu -> {
                        var active = jobHandle.get(cpu);
                        return active == null ? 0 : (long) remainingHandle.get(active);
                    };
                }
                // Optional CPU wrappers retain the native implementation as an AE2-typed delegate.
                for (var field : type.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || field.getType() != CraftingCpuLogic.class) continue;
                    var delegate = handle(field);
                    return cpu -> remaining(delegate.get(cpu));
                }
            } catch (ReflectiveOperationException | java.io.IOException | RuntimeException unavailable) {
                // The persisted contract also supports third-party CPU implementations without a native ledger.
            }
            return GtoCpuJobAccess::persistedRemaining;
        }
    };
    private GtoCpuJobAccess() { }

    public static long remaining(Object cpu) {
        if (cpu instanceof GtoCompatibleCraftingCpuLogic compatible)
            return compatible.getRemainingOutputAmount();
        if (!(cpu instanceof CraftingCpuLogic logic))
            throw new IllegalArgumentException("Expected crafting CPU logic");
        if (!logic.hasJob()) return 0;
        return Math.max(0, READERS.get(logic.getClass()).applyAsLong(logic));
    }

    private static long persistedRemaining(CraftingCpuLogic logic) {
        var data = new CompoundTag();
        logic.writeToNBT(data);
        return Math.max(0, data.getCompound("job").getLong("remainingAmount"));
    }

    private static VarHandle handle(Field field) throws IllegalAccessException {
        return MethodHandles.privateLookupIn(field.getDeclaringClass(), MethodHandles.lookup()).unreflectVarHandle(field);
    }

    private static Field remainingField(Class<?> type) throws java.io.IOException, NoSuchFieldException {
        try (var bytes = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            if (bytes == null) return null;
            var node = new ClassNode(); new ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG);
            for (var method : node.methods) for (var instruction : method.instructions) {
                if (!(instruction instanceof LdcInsnNode constant) || !"remainingAmount".equals(constant.cst)) continue;
                for (var next = instruction.getNext(); next != null; next = next.getNext()) {
                    if (next instanceof MethodInsnNode) break;
                    if (next instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETFIELD
                            && field.owner.equals(node.name) && field.desc.equals("J"))
                        return type.getDeclaredField(field.name);
                }
            }
            return null;
        }
    }
}
