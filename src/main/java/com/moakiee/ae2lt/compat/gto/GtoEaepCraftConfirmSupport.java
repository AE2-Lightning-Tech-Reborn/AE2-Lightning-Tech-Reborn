package com.moakiee.ae2lt.compat.gto;

import appeng.client.gui.me.crafting.CraftConfirmScreen;
import appeng.core.localization.GuiText;
import appeng.menu.me.crafting.CraftingPlanSummary;
import com.moakiee.thunderbolt.ae2.crafting.ExactAmountFormatter;
import com.moakiee.thunderbolt.ae2.crafting.ExactPlanReports;
import com.moakiee.thunderbolt.core.crafting.algorithm.menu.CraftingAlgorithmNameMenu;
import net.minecraft.network.chat.Component;

/** Compose EAEP's late formatted title with Core's algorithm and exact-plan contract. Client only. */
public final class GtoEaepCraftConfirmSupport {
    private GtoEaepCraftConfirmSupport() { }

    public static Component preserveCoreTitle(Component original, CraftConfirmScreen screen) {
        var menu = screen.getMenu();
        var algorithm = menu instanceof CraftingAlgorithmNameMenu extension
                ? extension.thunderbolt$getCraftingAlgorithmName() : Component.empty();
        return title(original, menu.getPlan(), algorithm);
    }

    static Component title(Component original, CraftingPlanSummary plan, Component algorithm) {
        var exact = ExactPlanReports.get(plan);
        if (exact != null) return GuiText.BytesUsed.text(ExactAmountFormatter.compact(exact.bytes(), 1));
        if (!algorithm.getString().isEmpty()) {
            return Component.translatable("gui.thunderbolt.crafting_plan.algorithm", original, algorithm);
        }
        return original;
    }
}
