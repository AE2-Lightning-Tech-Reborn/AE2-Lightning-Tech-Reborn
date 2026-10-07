package com.moakiee.ae2lt.machine.largeoverload;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Counts work before entering recipe or external-network calls, across dimensions and hatches. */
public final class LargeFactoryWorkBudget {
    public enum Work { DISPATCH, MATCH, NETWORK, SCAN, BUILD, PATTERN }
    private static final int[] SERVER_LIMIT = {512, 512, 4096, 16, 1024, 128};
    private static final int[] FACTORY_LIMIT = {64, 64, 256, 1, 16, 4};
    private static final Map<MinecraftServer, Meter> METERS = new WeakHashMap<>();
    private static final class Meter {
        final Map<UUID, Integer> loaded = new LinkedHashMap<>();
        final Map<UUID, int[]> machines = new HashMap<>();
        final Map<UUID, Integer> order = new HashMap<>();
        final int[] used = new int[Work.values().length];
        boolean orderDirty;
        long tick = Long.MIN_VALUE;
        long denied;
    }
    private LargeFactoryWorkBudget() { }
    public static void register(ServerLevel level, UUID id) {
        var meter = meter(level); meter.loaded.merge(id, 1, Integer::sum); meter.orderDirty = true;
    }
    public static void unregister(ServerLevel level, UUID id) {
        var meter = meter(level); meter.loaded.computeIfPresent(id, (key, count) -> count <= 1 ? null : count - 1); meter.orderDirty = true;
    }
    private static Meter meter(ServerLevel level) { return METERS.computeIfAbsent(level.getServer(), ignored -> new Meter()); }
    public static boolean take(LargeFactoryHatchBlockEntity hatch, Work work) {
        if (!(hatch.getLevel() instanceof ServerLevel level)) return false;
        var controller = hatch.controller();
        UUID id = controller == null ? hatch.accountId() : controller.machineId();
        boolean accepted = take(level, id, work);
        if (!accepted) { hatch.status("work_budget"); if (controller != null) controller.wakeNextTick(); }
        return accepted;
    }
    public static boolean take(LargeFactoryControllerBlockEntity controller, Work work) {
        return controller.getLevel() instanceof ServerLevel level && take(level, controller.machineId(), work);
    }
    private static boolean take(ServerLevel level, UUID id, Work work) {
        var meter = meter(level);
        long tick = level.getServer().getTickCount();
        if (meter.tick != tick) {
            meter.tick = tick; meter.machines.clear(); java.util.Arrays.fill(meter.used, 0);
        }
        if (meter.orderDirty) {
            meter.order.clear();
            int index = 0;
            for (var machine : meter.loaded.keySet()) meter.order.put(machine, index++);
            meter.orderDirty = false;
        }
        int kind = work.ordinal();
        int count = meter.loaded.size();
        // When there are more loaded factories than grants, rotate eligibility instead of starving later tickers.
        if (count > SERVER_LIMIT[kind] && meter.order.containsKey(id)) {
            int start = (int) (Math.floorMod(tick, count) * SERVER_LIMIT[kind] % count);
            if (Math.floorMod(meter.order.get(id) - start, count) >= SERVER_LIMIT[kind]) { meter.denied++; return false; }
        }
        int share = Math.min(FACTORY_LIMIT[kind], Math.max(1, SERVER_LIMIT[kind] / Math.max(1, meter.loaded.size())));
        var used = meter.machines.computeIfAbsent(id, ignored -> new int[Work.values().length]);
        if (used[kind] >= share || meter.used[kind] >= SERVER_LIMIT[kind]) {
            meter.denied++; return false;
        }
        used[kind]++; meter.used[kind]++;
        return true;
    }
    public static int[] snapshot(ServerLevel level) { return meter(level).used.clone(); }
    public static long denied(ServerLevel level) { return meter(level).denied; }
}
