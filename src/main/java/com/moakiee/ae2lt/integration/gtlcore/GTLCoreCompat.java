package com.moakiee.ae2lt.integration.gtlcore;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import net.minecraftforge.fml.ModList;

import com.mojang.logging.LogUtils;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.menu.me.crafting.CraftingCPUMenu;

import com.moakiee.ae2lt.mixin.thunderbolt.accessor.CraftingCpuLogicAccessor;
import com.moakiee.ae2lt.mixin.thunderbolt.accessor.ExecutingCraftingJobAccessor;
import com.moakiee.ae2lt.mixin.thunderbolt.accessor.TaskProgressAccessor;

/**
 * Reflection-only bridge to GTLCore's crafting CPU internals (verified against GTLCore 1.2.3.2),
 * kept weak so AE2LT keeps working without GTLCore and without a compile-time dependency on it.
 * <p>
 * Two GTLCore behaviours change state AE2LT has to stay consistent with:
 * <ul>
 * <li><b>Pattern auto-expand.</b> GTLCore overwrites {@code CraftingCpuLogic.executeCrafting} and may
 * charge a single provider push as several crafting operations: it extracts that many input copies,
 * registers the same multiple of expected outputs and consumes the same amount of task progress.
 * The overload bookkeeping AE2LT registers from that push therefore has to scale identically.</li>
 * <li><b>Transfinite CPU selection.</b> GTLCore's menu hook cancels the {@code setCPU} head for its own
 * CPUs, exactly like the TimeWheel menu hook does, so whichever hook runs second is skipped and keeps
 * a stale selection. A stale selection leaks crafting status packets and makes {@code cancelCrafting}
 * cancel the job of a CPU that is no longer displayed.</li>
 * </ul>
 * Every entry point falls back to vanilla behaviour when GTLCore is absent or its internals moved.
 */
public final class GTLCoreCompat {
    private static final Logger LOG = LogUtils.getLogger();

    private static final String MOD_ID = "gtlcore";
    private static final String AUTO_EXPAND_CLASS =
            "org.gtlcore.gtlcore.integration.ae2.crafting.CraftingPatternAutoExpand";
    private static final String TRANSFINITE_CPU_FIELD = "gtlcore$transfiniteCpu";

    private static final @Nullable Method GET_OPERATIONS = lookupGetOperations();
    private static final @Nullable Field MENU_TRANSFINITE_CPU = lookupTransfiniteCpuField();

    private GTLCoreCompat() {
    }

    /**
     * Number of pattern copies the wrapped {@code pushPattern} call stands for, never below one.
     * <p>
     * GTLCore computes it from the remaining task progress and the capacity of the adjacent targets,
     * so the same query is repeated here right before the push, while the inputs of the push are
     * already extracted but the targets are still untouched. Outside GTLCore this is always one.
     */
    public static long pushedCopies(CraftingCpuLogic logic, ICraftingProvider provider, IPatternDetails details) {
        var getOperations = GET_OPERATIONS;
        if (getOperations == null) {
            return 1L;
        }
        try {
            var job = ((CraftingCpuLogicAccessor) logic).ae2lt$getJob();
            if (job == null) {
                return 1L;
            }
            Map<IPatternDetails, ?> tasks = ((ExecutingCraftingJobAccessor) job).ae2lt$getTasks();
            var progress = tasks == null ? null : tasks.get(details);
            if (progress == null) {
                return 1L;
            }
            long requested = ((TaskProgressAccessor) progress).ae2lt$getValue();
            if (requested <= 1L) {
                return 1L;
            }
            var operations = getOperations.invoke(null,
                    details.supportsPushInputsToExternalInventory(), provider, details, requested);
            return operations instanceof Long count && count > 1L ? count : 1L;
        } catch (Throwable unavailable) {
            return 1L;
        }
    }

    /** True when GTLCore's transfinite CPU currently owns the selection of the given menu. */
    public static boolean isTransfiniteSelected(CraftingCPUMenu menu) {
        var field = MENU_TRANSFINITE_CPU;
        if (field == null || menu == null) {
            return false;
        }
        try {
            return field.get(menu) != null;
        } catch (Throwable unavailable) {
            return false;
        }
    }

    /**
     * Drops GTLCore's transfinite selection. Both its status broadcast and its job cancellation gate
     * on that field, and the selection's listener keeps feeding this menu's incremental updates, so
     * clearing it is what makes the TimeWheel selection the only live one.
     */
    public static void clearTransfiniteSelection(CraftingCPUMenu menu, Consumer<AEKey> listener) {
        var field = MENU_TRANSFINITE_CPU;
        if (field == null || menu == null) {
            return;
        }
        try {
            var cpu = field.get(menu);
            if (cpu == null) {
                return;
            }
            field.set(menu, null);
            removeListener(cpu, listener);
        } catch (Throwable unavailable) {
            // A failed read changes nothing: GTLCore simply keeps driving its own selection.
        }
    }

    private static void removeListener(Object cpu, Consumer<AEKey> listener) {
        try {
            var logic = cpu.getClass().getMethod("getCraftingLogic").invoke(cpu);
            if (logic != null) {
                logic.getClass().getMethod("removeListener", Consumer.class).invoke(logic, listener);
            }
        } catch (Throwable unavailable) {
            // The stale listener only costs a few redundant incremental updates.
        }
    }

    private static @Nullable Method lookupGetOperations() {
        try {
            return Class.forName(AUTO_EXPAND_CLASS).getMethod("getOperations",
                    boolean.class, ICraftingProvider.class, IPatternDetails.class, long.class);
        } catch (Throwable absent) {
            if (isLoaded()) {
                LOG.warn("GTLCore is present but its pattern auto-expand entry point was not found; "
                        + "overload patterns will assume one crafting operation per provider push");
            }
            return null;
        }
    }

    private static @Nullable Field lookupTransfiniteCpuField() {
        try {
            var field = CraftingCPUMenu.class.getDeclaredField(TRANSFINITE_CPU_FIELD);
            field.setAccessible(true);
            return field;
        } catch (Throwable absent) {
            if (isLoaded()) {
                LOG.warn("GTLCore is present but its transfinite CPU selection field was not found; "
                        + "switching CPU families in the crafting CPU menu may leave a stale selection");
            }
            return null;
        }
    }

    private static boolean isLoaded() {
        var modList = ModList.get();
        return modList != null && modList.isLoaded(MOD_ID);
    }
}
