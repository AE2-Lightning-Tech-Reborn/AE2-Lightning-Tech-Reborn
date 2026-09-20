package com.moakiee.ae2lt.crafting.big;

import appeng.api.networking.crafting.ICraftingCPU;

import com.moakiee.ae2lt.blockentity.*;
import com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCpuPool;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuPatternTerminalHost;

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
        if (!(host instanceof TianshuPatternTerminalHost terminal)) return null;
        for (var port : terminal.getAvailableTianshu()) {
            var c = port.getController();
            if (c != null && c.bigCrafting().available()) return c.bigCrafting();
        }
        return null;
    }
}
