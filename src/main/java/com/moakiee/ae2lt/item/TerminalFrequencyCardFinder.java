package com.moakiee.ae2lt.item;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import appeng.api.upgrades.IUpgradeableItem;

/**
 * Finds installed frequency-card upgrades in carried or Curios terminals via {@link IUpgradeableItem}.
 * Returns read-only snapshots for auto-connect. Persist edits through the terminal's
 * {@link appeng.api.upgrades.IUpgradeInventory}.
 */
public final class TerminalFrequencyCardFinder {

    private TerminalFrequencyCardFinder() {
    }

    public static List<ItemStack> findFrequencyCards(Player player) {
        List<ItemStack> result = new ArrayList<>();

        var inventory = player.getInventory();
        for (ItemStack stack : inventory.items) {
            collectFromTerminal(stack, result);
        }
        for (ItemStack stack : inventory.offhand) {
            collectFromTerminal(stack, result);
        }
        for (ItemStack stack : CuriosFrequencyCardFinder.findAllEquippedStacks(player)) {
            collectFromTerminal(stack, result);
        }

        return result;
    }

    private static void collectFromTerminal(ItemStack terminalStack, List<ItemStack> out) {
        if (terminalStack.isEmpty() || !(terminalStack.getItem() instanceof IUpgradeableItem upgradeable)) {
            return;
        }
        var upgrades = upgradeable.getUpgrades(terminalStack);
        for (int slot = 0; slot < upgrades.size(); slot++) {
            ItemStack card = upgrades.getStackInSlot(slot);
            if (card.getItem() instanceof OverloadedFrequencyCardItem) {
                out.add(card);
            }
        }
    }
}
