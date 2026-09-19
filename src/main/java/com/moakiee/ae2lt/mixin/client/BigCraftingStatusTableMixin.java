package com.moakiee.ae2lt.mixin.client;

import appeng.api.client.AEKeyRendering;
import appeng.client.gui.me.crafting.CraftingStatusTableRenderer;
import appeng.core.localization.GuiText;
import appeng.menu.me.crafting.CraftingStatusEntry;

import com.moakiee.ae2lt.crafting.big.BigStatusEntry;
import com.moakiee.thunderbolt.ae2.crafting.ExactAmountFormatter;

import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;

@Mixin(value = CraftingStatusTableRenderer.class, remap = false)
public abstract class BigCraftingStatusTableMixin {
    @Inject(
            method =
                    "getEntryDescription(Lappeng/menu/me/crafting/CraftingStatusEntry;)Ljava/util/List;",
            at = @At("HEAD"),
            cancellable = true)
    private void ae2lt$description(
            CraftingStatusEntry e, CallbackInfoReturnable<List<Component>> cir) {
        var lines = ae2lt$lines(e, false);
        if (lines != null) cir.setReturnValue(lines);
    }

    @Inject(
            method =
                    "getEntryTooltip(Lappeng/menu/me/crafting/CraftingStatusEntry;)Ljava/util/List;",
            at = @At("HEAD"),
            cancellable = true)
    private void ae2lt$tooltip(CraftingStatusEntry e, CallbackInfoReturnable<List<Component>> cir) {
        var lines = ae2lt$lines(e, true);
        if (lines != null) cir.setReturnValue(lines);
    }

    @Unique
    private static List<Component> ae2lt$lines(CraftingStatusEntry e, boolean full) {
        var a = ((BigStatusEntry) e).ae2lt$amounts();
        if (a == null) return null;
        var lines =
                full
                        ? new ArrayList<>(AEKeyRendering.getTooltip(e.getWhat()))
                        : new ArrayList<Component>();
        int unit = e.getWhat().getAmountPerUnit();
        if (a.stored().signum() > 0)
            lines.add(
                    GuiText.FromStorage.text(
                            full
                                    ? ExactAmountFormatter.full(a.stored(), unit)
                                    : ExactAmountFormatter.compact(a.stored(), unit)));
        if (a.active().signum() > 0)
            lines.add(
                    GuiText.Crafting.text(
                            full
                                    ? ExactAmountFormatter.full(a.active(), unit)
                                    : ExactAmountFormatter.compact(a.active(), unit)));
        if (a.pending().signum() > 0)
            lines.add(
                    GuiText.Scheduled.text(
                            full
                                    ? ExactAmountFormatter.full(a.pending(), unit)
                                    : ExactAmountFormatter.compact(a.pending(), unit)));
        return lines;
    }
}
