package com.moakiee.ae2lt.logic.interfaces;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.ToLongFunction;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.MultiCraftingTracker;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCPU;
import net.minecraft.world.level.Level;

/** Network stocking policy; AE2 still owns link persistence, delivery and cancellation. */
final class OverloadedInterfaceCrafting {
    static final long RETRY_TICKS = 100;

    // Keep jobs and links in the original tracker so InterfaceLogic's NBT,
    // insertCraftedItems, jobStateChange and crafting-card removal still work.
    private static final Method GET_JOB = trackerMethod("getJob", int.class);
    private static final Method SET_JOB = trackerMethod("setJob", int.class, Future.class);
    private static final Method GET_LINK = trackerMethod("getLink", int.class);
    private static final Method SET_LINK = trackerMethod("setLink", int.class, ICraftingLink.class);
    private static final Field CPU_JOB = accessibleField(CraftingCpuLogic.class, "job");
    private static final Field REMAINING_OUTPUT = accessibleField(ExecutingCraftingJob.class, "remainingAmount");

    private final MultiCraftingTracker tracker;
    private final ICraftingRequester requester;
    private final AEKey[] requestKeys;
    private final Map<AEKey, Long> retryAfter = new HashMap<>();
    private ICraftingService calculationService;

    record Slot(int index, AEKey key, long amount, boolean unlimited) {}

    OverloadedInterfaceCrafting(MultiCraftingTracker tracker, ICraftingRequester requester, int slots) {
        this.tracker = tracker;
        this.requester = requester;
        requestKeys = new AEKey[slots];
    }

    void reset() {
        Arrays.fill(requestKeys, null);
        retryAfter.clear();
        calculationService = null;
    }

    boolean tick(List<Slot> configured, long now, Level level, ICraftingService crafting,
                 IActionSource source, ToLongFunction<AEKey> storedAmount) {
        if (calculationService != null && calculationService != crafting) {
            for (int i = 0; i < requestKeys.length; i++) cancelCalculation(i);
            retryAfter.clear();
        }
        calculationService = crafting;

        var keys = new AEKey[requestKeys.length];
        var demands = new LinkedHashMap<AEKey, Demand>();
        for (var slot : configured) {
            keys[slot.index()] = slot.key();
            demands.computeIfAbsent(slot.key(), key -> new Demand()).add(slot);
        }
        retryAfter.keySet().retainAll(demands.keySet());

        var linkedKeys = new HashSet<AEKey>();
        for (int i = 0; i < requestKeys.length; i++) {
            var link = getLink(i);
            if (link != null && (link.isDone() || link.isCanceled())) {
                setLink(i, null);
                link = null;
            }
            if (link != null) {
                // Restored links have no transient request key; their persisted
                // config slot supplies it until the CPU reconnects to the requester.
                var key = requestKeys[i] != null ? requestKeys[i] : keys[i];
                if (key != null) linkedKeys.add(key);
            } else if (getJob(i) != null && !java.util.Objects.equals(requestKeys[i], keys[i])) {
                cancelCalculation(i);
            } else if (getJob(i) == null) {
                requestKeys[i] = null;
            }
        }

        var inFlight = inFlightAmounts(crafting);
        boolean submitted = false;
        for (var entry : demands.entrySet()) {
            var key = entry.getKey();
            var demand = entry.getValue();
            long amount = demand.deficit(storedAmount.applyAsLong(key), inFlight.get(key));
            if (amount <= 0 || linkedKeys.contains(key)) {
                cancelCalculations(key);
                if (amount <= 0) retryAfter.remove(key);
                continue;
            }

            int slot = -1;
            for (int candidate : demand.slots) {
                if (getJob(candidate) != null) {
                    if (slot == -1) slot = candidate;
                    else cancelCalculation(candidate);
                }
            }
            if (slot == -1) {
                for (int candidate : demand.slots) {
                    if (getLink(candidate) == null) {
                        slot = candidate;
                        break;
                    }
                }
            }
            if (slot != -1) submitted |= request(slot, key, amount, now, level, crafting, source);
        }
        return submitted;
    }

    private boolean request(int slot, AEKey key, long amount, long now, Level level,
                            ICraftingService crafting, IActionSource source) {
        var future = getJob(slot);
        if (future != null) {
            if (!future.isDone()) return false;
            setJob(slot, null);
            requestKeys[slot] = null;
            try {
                var plan = future.get();
                var output = plan != null ? plan.finalOutput() : null;
                if (plan == null || plan.simulation() || output == null
                        || !key.equals(output.what()) || output.amount() <= 0) {
                    retryAfter.put(key, now + RETRY_TICKS);
                    return false;
                }
                // Stock or another CPU may have covered the original deficit
                // while this plan was calculating. Replan only the current gap.
                if (output.amount() > amount) return false;
                var result = crafting.submitJob(plan, requester, null, false, source);
                if (result.successful() && result.link() != null) {
                    setLink(slot, result.link());
                    requestKeys[slot] = key;
                    retryAfter.remove(key);
                    return true;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException | CancellationException e) {
                // A failed/cancelled calculation must leave the tracker retryable.
            }
            retryAfter.put(key, now + RETRY_TICKS);
        } else if (now >= retryAfter.getOrDefault(key, Long.MIN_VALUE)) {
            setJob(slot, crafting.beginCraftingCalculation(
                    level, () -> source, key, amount, CalculationStrategy.CRAFT_LESS));
            requestKeys[slot] = key;
        }
        return false;
    }

    private void cancelCalculations(AEKey key) {
        for (int i = 0; i < requestKeys.length; i++) {
            if (key.equals(requestKeys[i])) cancelCalculation(i);
        }
    }

    private void cancelCalculation(int slot) {
        var future = getJob(slot);
        if (future != null) {
            future.cancel(true);
            setJob(slot, null);
        }
        if (getLink(slot) == null) requestKeys[slot] = null;
    }

    static KeyCounter inFlightAmounts(ICraftingService crafting) {
        var amounts = new KeyCounter();
        for (var cpu : crafting.getCpus()) {
            if (!cpu.isBusy()) continue;
            var status = cpu.getJobStatus();
            var output = status != null ? status.crafting() : null;
            if (output != null && output.amount() > 0) {
                long remaining = output.amount();
                if (cpu instanceof CraftingCPUCluster cluster) {
                    // CraftingJobStatus reports the original output, not what
                    // remains after partial deliveries to the network.
                    try {
                        var job = CPU_JOB.get(cluster.craftingLogic);
                        remaining = job != null ? REMAINING_OUTPUT.getLong(job) : 0;
                    } catch (IllegalAccessException e) {
                        throw new IllegalStateException("Failed to read AE2 remaining crafting output", e);
                    }
                } else if (cpu instanceof TimeWheelCraftingCPU timeWheel) {
                    remaining = timeWheel.getCraftingLogic().getRemainingOutputAmount();
                }
                amounts.set(output.what(), OverloadedAmountMath.saturatingAdd(
                        amounts.get(output.what()), Math.max(0, remaining)));
            }
        }
        return amounts;
    }

    private static final class Demand {
        final List<Integer> slots = new ArrayList<>();
        long target;
        long emptyBuffer;

        void add(Slot slot) {
            slots.add(slot.index());
            if (slot.unlimited()) {
                emptyBuffer = Math.max(emptyBuffer, 1024L * slot.key().getType().getAmountPerByte());
            } else {
                target = OverloadedAmountMath.saturatingAdd(target, Math.max(0, slot.amount()));
            }
        }

        long deficit(long stock, long inFlight) {
            long covered = OverloadedAmountMath.saturatingAdd(Math.max(0, stock), Math.max(0, inFlight));
            if (covered == 0) return Math.max(target, emptyBuffer);
            return covered >= target ? 0 : target - covered;
        }
    }

    private static Method trackerMethod(String name, Class<?>... arguments) {
        try {
            var method = MultiCraftingTracker.class.getDeclaredMethod(name, arguments);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to access AE2 crafting tracker: " + name, e);
        }
    }

    private static Field accessibleField(Class<?> type, String name) {
        try {
            var field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to access crafting output: " + name, e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T invoke(Method method, Object... arguments) {
        try {
            return (T) method.invoke(tracker, arguments);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to use AE2 crafting tracker: " + method.getName(), e);
        }
    }

    private Future<ICraftingPlan> getJob(int slot) { return invoke(GET_JOB, slot); }
    private void setJob(int slot, Future<ICraftingPlan> job) { invoke(SET_JOB, slot, job); }
    private ICraftingLink getLink(int slot) { return invoke(GET_LINK, slot); }
    private void setLink(int slot, ICraftingLink link) { invoke(SET_LINK, slot, link); }
}
