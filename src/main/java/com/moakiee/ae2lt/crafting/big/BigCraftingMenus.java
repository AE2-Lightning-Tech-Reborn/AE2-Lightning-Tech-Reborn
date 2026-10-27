package com.moakiee.ae2lt.crafting.big;

import appeng.api.networking.crafting.ICraftingCPU;

import com.moakiee.ae2lt.blockentity.*;
import com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCpuPool;
import com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCPU;
import com.moakiee.thunderbolt.api.crafting.CraftingPlanningEngines;
import com.moakiee.thunderbolt.core.crafting.planner.ThunderboltV2PlanningEngine;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionHost;
import net.minecraft.world.level.Level;

public final class BigCraftingMenus {
    private BigCraftingMenus() {}

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
        return available(context.grid());
    }

    public static BigCraftingService available(IGrid grid) {
        for (var port : grid.getActiveMachines(TianshuSupercomputerPortBlockEntity.class)) {
            var c = port.getController();
            if (port.isLinkActive() && c != null && c.bigCrafting().available()) return c.bigCrafting();
        }
        return null;
    }

    /** Extended terminal requests must use the same selected algorithm as ordinary requests. */
    public static Context planningContext(Object host) {
        var context = context(host);
        if (context == null) return null;
        if (!CraftingPlanningEngines.isSelected(context.grid(), ThunderboltV2PlanningEngine.ID)) return null;
        boolean hasTianshu = context.grid().getCraftingService().getCpus().stream()
                .anyMatch(cpu -> cpu instanceof TimeWheelCraftingCPU || cpu instanceof TimeWheelCraftingCpuPool);
        return hasTianshu ? context : null;
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
