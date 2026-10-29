package com.moakiee.ae2lt.crafting.big;

import appeng.api.networking.crafting.ICraftingCPU;

import com.moakiee.ae2lt.blockentity.*;
import com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCpuPool;
import com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCPU;
import com.moakiee.thunderbolt.api.crafting.ICraftingPlanningService;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionHost;
import net.minecraft.world.level.Level;

public final class BigCraftingMenus {
    private BigCraftingMenus() {}

    public static boolean hasTianshu(IGrid grid) {
        // A full/busy pool may no longer be published as an available CPU. Its active port
        // still proves that the CPU belongs to this grid, irrespective of free capacity.
        for (var port : grid.getActiveMachines(TianshuSupercomputerPortBlockEntity.class)) {
            var pool = port.getTimeWheelCraftingCpuPool();
            if (pool != null && pool.isActive() && port.getGrid() == grid) return true;
        }
        return grid.getCraftingService().getCpus().stream().anyMatch(cpu ->
                cpu instanceof TimeWheelCraftingCpuPool || cpu instanceof TimeWheelCraftingCPU
                        || cpu instanceof BigCraftingCpu);
    }

    public static boolean planningEnabled(Object host) {
        var context = context(host);
        return context != null && planningEnabled(context.grid());
    }

    public static boolean planningEnabled(IGrid grid) {
        return hasTianshu(grid) && BigCraftingPlanningPolicy.enabled(true,
                grid.getService(ICraftingPlanningService.class).resolve());
    }

    public static TianshuSupercomputerControllerBlockEntity controller(Object host) {
        if (host instanceof TianshuSupercomputerControllerBlockEntity c) return c;
        if (host instanceof TianshuSupercomputerPortBlockEntity p) return p.getController();
        return null;
    }

    public static BigCraftingService service(ICraftingCPU cpu) {
        if (!(cpu instanceof TimeWheelCraftingCpuPool pool)) return null;
        var c = controller(pool.getHost());
        return c == null ? null : c.bigCrafting();
    }

    public static BigCraftingService available(Object host) {
        var context = context(host);
        if (context == null) return null;
        for (var port : context.grid().getActiveMachines(TianshuSupercomputerPortBlockEntity.class)) {
            var c = port.getController();
            if (port.isLinkActive() && c != null && c.bigCrafting().available()) return c.bigCrafting();
        }
        return null;
    }

    /** Native amount/confirmation menus only need an actionable grid, not a terminal subtype. */
    public static Context context(Object host) {
        if (!(host instanceof IActionHost actionHost)) return null;
        var node = actionHost.getActionableNode();
        return node != null && node.isActive() && node.getGrid() != null
                ? new Context(node.getGrid(), node.getLevel()) : null;
    }

    public record Context(IGrid grid, Level level) {}
}
