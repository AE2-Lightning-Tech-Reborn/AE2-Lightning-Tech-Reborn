package com.moakiee.ae2lt.crafting.big;

import java.math.BigInteger;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import com.moakiee.thunderbolt.api.crafting.ExactCraftingBackend;
import net.minecraft.world.level.Level;

/** Tianshu execution capability supplied to Thunderbolt's selected-algorithm request gateway. */
public final class TianshuExactCraftingBackend implements ExactCraftingBackend {
    public static final TianshuExactCraftingBackend INSTANCE = new TianshuExactCraftingBackend();

    private TianshuExactCraftingBackend() {}

    @Override public boolean isAvailable(IGrid grid) {
        return BigCraftingMenus.available(grid) != null;
    }

    @Override public boolean matchesCpu(ICraftingCPU cpu) {
        var service = BigCraftingMenus.service(cpu);
        return service != null && service.available() && !service.busy();
    }

    @Override public Future<ICraftingPlan> plan(
            IGrid grid, Level level, AEKey target, BigInteger amount, IActionSource source) {
        try {
            return BigCraftingService.preview(grid, level, target, amount, source)
                    .handle((plan, error) -> new BigCraftingPlan(
                            target, amount, plan, error == null ? "" : "planning_failed"));
        } catch (RuntimeException error) {
            return CompletableFuture.completedFuture(new BigCraftingPlan(target, amount, null, "planning_failed"));
        }
    }
}
