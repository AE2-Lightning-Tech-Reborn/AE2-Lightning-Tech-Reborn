package com.moakiee.ae2lt.mixin.big;

import appeng.api.networking.crafting.*;
import appeng.api.stacks.AEKey;
import appeng.api.storage.ISubMenuHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.guisync.GuiSync;
import appeng.menu.me.crafting.*;

import com.moakiee.ae2lt.crafting.big.*;
import com.moakiee.thunderbolt.ae2.crafting.ExactPlanReports;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

import java.math.BigInteger;
import java.util.concurrent.*;

@Mixin(value = CraftConfirmMenu.class, remap = false, priority = 1200)
public abstract class BigCraftConfirmMenuMixin extends AEBaseMenu implements BigConfirmMenu {
    @Shadow private Future<ICraftingPlan> job;
    @Shadow private ICraftingPlan result;
    @Shadow private AEKey whatToCraft;
    @Shadow private int amount;

    @Shadow
    public abstract ISubMenuHost getHost();

    @Shadow
    public abstract void setPlan(CraftingPlanSummary summary);

    @Shadow
    public abstract void clearError();

    @Unique private BigInteger ae2lt$amount;

    @Unique
    @GuiSync(30210)
    private boolean ae2lt$isBig;

    @Unique
    @GuiSync(30211)
    private String ae2lt$failure = "";

    protected BigCraftConfirmMenuMixin(MenuType<?> type, int id, Inventory inv, Object host) {
        super(type, id, inv, host);
    }

    public boolean ae2lt$isBig() {
        return ae2lt$isBig;
    }

    public String ae2lt$failure() {
        return ae2lt$failure;
    }

    public void ae2lt$planBig(AEKey key, BigInteger n) {
        if (isClientSide()) return;
        var service = BigCraftingMenus.available(getHost());
        if (service == null) return;
        if (job != null) job.cancel(true);
        clearError();
        setPlan(null);
        result = null;
        whatToCraft = key;
        amount = n.min(BigInteger.valueOf(Integer.MAX_VALUE)).intValueExact();
        ae2lt$amount = n;
        ae2lt$isBig = true;
        ae2lt$failure = "";
        try {
            job =
                    service.preview(key, n, getActionSource())
                            .handle(
                                    (p, error) ->
                                            new BigCraftingPlan(
                                                    key,
                                                    n,
                                                    p,
                                                    error == null ? "" : "planning_failed"));
        } catch (RuntimeException error) {
            job =
                    CompletableFuture.completedFuture(
                            new BigCraftingPlan(key, n, null, "planning_failed"));
        }
    }

    @Inject(method = "broadcastChanges()V", at = @At("HEAD"), remap = true)
    private void ae2lt$upgradeOverflow(CallbackInfo ci) {
        if (isClientSide() || job == null || !job.isDone() || job.isCancelled()) return;
        try {
            var finished = job.get();
            if (!ae2lt$isBig
                    && ExactPlanReports.isPreview(finished)
                    && BigCraftingMenus.available(getHost()) != null) {
                ae2lt$planBig(whatToCraft, BigInteger.valueOf(amount));
            } else if (finished instanceof BigCraftingPlan big) {
                ae2lt$failure = big.failure();
                if (big.exact() != null && !big.exact().verified()) ae2lt$failure = "unverified";
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | java.util.concurrent.CancellationException e) {
            /* Native menu owns non-exact failures. */
        }
    }

    @Inject(method = "cpuMatches", at = @At("HEAD"), cancellable = true)
    private void ae2lt$matchBigCpu(ICraftingCPU cpu, CallbackInfoReturnable<Boolean> cir) {
        if (ae2lt$isBig) {
            var s = BigCraftingMenus.service(cpu);
            cir.setReturnValue(s != null && s.available() && !s.busy());
        }
    }

    @Inject(method = "replan", at = @At("HEAD"), cancellable = true)
    private void ae2lt$replan(CallbackInfo ci) {
        if (ae2lt$isBig && !isClientSide()) {
            ae2lt$planBig(whatToCraft, ae2lt$amount);
            ci.cancel();
        }
    }

    @Inject(method = "goBack", at = @At("HEAD"), cancellable = true)
    private void ae2lt$back(CallbackInfo ci) {
        if (ae2lt$isBig && getPlayer() instanceof ServerPlayer player) {
            CraftAmountMenu.open(player, getLocator(), whatToCraft, 1);
            if (player.containerMenu instanceof BigAmountMenu big)
                big.ae2lt$initialAmount(ae2lt$amount);
            ci.cancel();
        }
    }
}
