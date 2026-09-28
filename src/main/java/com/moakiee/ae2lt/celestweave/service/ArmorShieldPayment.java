package com.moakiee.ae2lt.celestweave.service;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.celestweave.ShieldChargeWindow;

/** Pays a shield/last-stand quote atomically, recording window credit only on success. */
public final class ArmorShieldPayment {
    private ArmorShieldPayment() {
    }

    public static boolean pay(ServerPlayer player, ItemStack armor, ShieldChargeWindow.Quote quote) {
        var lightningCost = ArmorLightningService.LightningCost.ehv(quote.ehvCost());
        if (!ArmorLightningService.hasCost(player, armor, lightningCost)) {
            ArmorResourceFeedback.noExtremeHighVoltage(player);
            return false;
        }
        var payment = ArmorEnergyService.consumeActiveCostPayment(player, armor, quote.feCost());
        if (!payment.paid()) {
            ArmorResourceFeedback.noFe(player);
            return false;
        }
        if (!ArmorLightningService.consume(player, armor, lightningCost)) {
            payment.refund();
            ArmorResourceFeedback.noExtremeHighVoltage(player);
            return false;
        }
        ShieldChargeWindow.record(armor, quote);
        return true;
    }
}
