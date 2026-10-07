package com.moakiee.ae2lt.mixin;

import java.util.concurrent.Future;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.CraftingSubmitErrorCode;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.menu.me.crafting.CraftConfirmMenu;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.crafting.report.CraftingReportInventory;
import com.moakiee.ae2lt.crafting.report.CraftingReportMenuState;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Match the long-amount entry point merged by Data Energistics at priority 1000 as well.
@Mixin(value = CraftConfirmMenu.class, remap = false, priority = 1100)
public abstract class CraftConfirmReplanMixin {
    @Unique
    private CraftingReportInventory ae2lt$inventory;

    @Unique
    private boolean ae2lt$reuseInventory;

    @WrapOperation(method = {"planJob", "data_energistics$planJob"}, at = @At(value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingService;beginCraftingCalculation("
                    + "Lnet/minecraft/world/level/Level;Lappeng/api/networking/crafting/ICraftingSimulationRequester;"
                    + "Lappeng/api/stacks/AEKey;JLappeng/api/networking/crafting/CalculationStrategy;)Ljava/util/concurrent/Future;"))
    private Future<ICraftingPlan> ae2lt$captureInventory(
            ICraftingService service, Level level, ICraftingSimulationRequester requester,
            AEKey what, long amount, CalculationStrategy strategy, Operation<Future<ICraftingPlan>> original) {
        if (!ae2lt$reuseInventory) {
            ae2lt$inventory = new CraftingReportInventory();
        }
        return ae2lt$inventory.captureCalculation(
                () -> original.call(service, level, requester, what, amount, strategy));
    }

    @Inject(method = "startJob", at = @At("RETURN"))
    private void ae2lt$rememberMissing(CallbackInfo ci) {
        var menu = (CraftConfirmMenu) (Object) this;
        if (menu.isClientSide() || ae2lt$inventory == null) {
            return;
        }
        var failure = menu.submitError.result();
        if (failure != null && failure.errorCode() == CraftingSubmitErrorCode.MISSING_INGREDIENT
                && failure.errorDetail() instanceof GenericStack deficit) {
            ae2lt$inventory.recordMissing(deficit);
        }
    }

    @Inject(method = "replan", at = @At("HEAD"))
    private void ae2lt$clearClientPreview(CallbackInfo ci) {
        var menu = (CraftConfirmMenu) (Object) this;
        if (menu.isClientSide() && ((CraftingReportMenuState) menu).ae2lt$shouldShowReport()) {
            menu.setPlan(null);
        }
    }

    @WrapOperation(method = "replan", at = @At(value = "INVOKE",
            target = "Lappeng/menu/me/crafting/CraftConfirmMenu;planJob("
                    + "Lappeng/api/stacks/AEKey;ILappeng/api/networking/crafting/CalculationStrategy;)Z"))
    private boolean ae2lt$replanFromReducedSnapshot(CraftConfirmMenu menu, AEKey what, int amount,
            CalculationStrategy strategy, Operation<Boolean> original) {
        ae2lt$reuseInventory = ((CraftingReportMenuState) menu).ae2lt$shouldShowReport()
                && ae2lt$inventory != null && ae2lt$inventory.isCaptured();
        try {
            if (ae2lt$reuseInventory) {
                ae2lt$inventory.subtractMissing();
                menu.setPlan(null);
            }
            return original.call(menu, what, amount, strategy);
        } finally {
            ae2lt$reuseInventory = false;
        }
    }
}
