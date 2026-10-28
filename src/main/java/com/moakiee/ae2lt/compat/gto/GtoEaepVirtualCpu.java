package com.moakiee.ae2lt.compat.gto;

import java.lang.reflect.*;
import java.util.*;
import appeng.api.crafting.IPatternDetails;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.CraftingCpuLogic;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

/** EAEP's last-task virtual completion rule, applied to the actual GTO CPU implementation. */
public final class GtoEaepVirtualCpu {
    private record Access(Field job, Field tasks, Method finish, Field delegate) { }
    private static final ClassValue<Access> ACCESS = new ClassValue<>() {
        @Override protected Access computeValue(Class<?> type) {
            try {
                for (var field : type.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                    if (Arrays.stream(field.getType().getDeclaredFields())
                            .noneMatch(member -> member.getType() == CraftingLink.class)) continue;
                    var maps = Arrays.stream(field.getType().getDeclaredFields())
                            .filter(member -> !Modifier.isStatic(member.getModifiers())
                                    && Map.class.isAssignableFrom(member.getType())
                                    && member.getGenericType() instanceof ParameterizedType signature
                                    && signature.getActualTypeArguments()[0] == IPatternDetails.class).toList();
                    if (maps.size() != 1) throw new IllegalStateException("Ambiguous native GTO task ledger");
                    String finishName = null;
                    try (var input = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
                        if (input == null) throw new IllegalStateException("Native GTO CPU bytecode unavailable");
                        var node = new ClassNode();
                        new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG);
                        for (var method : node.methods) {
                            if (!method.desc.equals("(Z)V")) continue;
                            for (var instruction : method.instructions) {
                                if (instruction instanceof MethodInsnNode call
                                        && call.owner.equals("appeng/crafting/CraftingLink")
                                        && call.name.equals("markDone") && call.desc.equals("()V")) {
                                    if (finishName != null) throw new IllegalStateException("Ambiguous native finish method");
                                    finishName = method.name;
                                }
                            }
                        }
                    }
                    if (finishName == null) throw new IllegalStateException("Native GTO finish contract missing");
                    var finish = type.getDeclaredMethod(finishName, boolean.class);
                    field.setAccessible(true);
                    maps.get(0).setAccessible(true);
                    finish.setAccessible(true);
                    return new Access(field, maps.get(0), finish, null);
                }
                for (var field : type.getDeclaredFields()) {
                    if (!Modifier.isStatic(field.getModifiers()) && field.getType() == CraftingCpuLogic.class) {
                        field.setAccessible(true);
                        return new Access(null, null, null, field);
                    }
                }
                throw new IllegalStateException("Unsupported GTO CPU: " + type.getName());
            } catch (ReflectiveOperationException | java.io.IOException failure) {
                throw new IllegalStateException("Cannot read GTO CPU completion contract", failure);
            }
        }
    };
    private static final ClassValue<Field> VALUES = new ClassValue<>() {
        @Override protected Field computeValue(Class<?> type) {
            var fields = Arrays.stream(type.getDeclaredFields()).filter(field ->
                    !Modifier.isStatic(field.getModifiers()) && field.getType() == long.class).toList();
            if (fields.size() != 1) throw new IllegalStateException("Ambiguous native task progress: " + type);
            fields.get(0).setAccessible(true);
            return fields.get(0);
        }
    };

    private GtoEaepVirtualCpu() { }

    public static boolean completeLastTask(CraftingCpuLogic cpu, IPatternDetails pattern) {
        if (cpu instanceof GtoCompatibleCraftingCpuLogic compatible)
            return compatible.completeEaepVirtualTask(pattern);
        if (!cpu.hasJob()) return false;
        try {
            var access = ACCESS.get(cpu.getClass());
            if (access.delegate != null)
                return completeLastTask((CraftingCpuLogic) access.delegate.get(cpu), pattern);
            var job = access.job.get(cpu);
            if (job == null) return false;
            var tasks = (Map<?, ?>) access.tasks.get(job);
            var current = tasks.get(pattern);
            if (current == null || value(current) > 1) return false;
            for (var progress : tasks.values()) {
                if (progress == null) continue;
                long remaining = value(progress);
                if (progress == current) remaining--;
                if (remaining > 0) return false;
            }
            cpu.cluster.updateOutput(null);
            access.finish.invoke(cpu, true);
            return true;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot complete native GTO virtual task", failure);
        }
    }

    private static long value(Object progress) throws IllegalAccessException {
        return progress instanceof Number number ? number.longValue() : VALUES.get(progress.getClass()).getLong(progress);
    }
}
