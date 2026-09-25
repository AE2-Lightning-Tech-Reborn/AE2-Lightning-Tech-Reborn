package com.moakiee.ae2lt.integration.ae2wtlib;

import appeng.items.tools.powered.WirelessTerminalItem;
import de.mari_023.ae2wtlib.wut.ItemWUT;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/** Plans on copies and rejects inventory, link and settings conflicts before consuming either input. */
public final class TianshuTerminalMerge {
    private TianshuTerminalMerge() {}
    public static boolean applies(ItemStack target, ItemStack source, String terminal) {
        return terminal.equals(Ae2wtlibIntegration.TIANSHU_CRAFTING_NAME)
                || TianshuWctIntegration.hasTianshuCrafting(target) || TianshuWctIntegration.hasTianshuCrafting(source);
    }
    public static ItemStack merge(ItemStack target, ItemStack source, String terminalName) {
        if (!(target.getItem() instanceof ItemWUT universal)
                || !(source.getItem() instanceof WirelessTerminalItem terminal)) return ItemStack.EMPTY;
        var result = target.copy();
        var tag = result.getOrCreateTag();
        tag.putBoolean(terminalName, true);
        if (source.hasTag()) for (var key : source.getTag().getAllKeys()) {
            if (key.equals("upgrades") || key.equals("internalCurrentPower") || key.equals("internalMaxPower")) continue;
            if (key.equals("currentTerminal") && tag.contains(key)) continue;
            var incoming = source.getTag().get(key);
            var existing = tag.get(key);
            if (key.equals("craftingGrid") || key.equals("viewcells") || key.equals("singularity")) {
                if (!containsItems(incoming)) continue;
                if (existing != null && !containsItems(existing)) { tag.put(key, incoming.copy()); continue; }
            }
            // Equal serialized inventories still represent two independently owned sets of items.
            if (existing != null && (containsItems(incoming) && containsItems(existing)
                    || !Objects.equals(existing, incoming))) return ItemStack.EMPTY;
            tag.put(key, incoming.copy());
        }
        var upgrades = universal.getUpgrades(result);
        for (var card : terminal.getUpgrades(source.copy())) {
            if (!upgrades.addItems(card.copy()).isEmpty()) return ItemStack.EMPTY;
        }
        universal.onUpgradesChanged(result, upgrades);
        double energy = universal.getAECurrentPower(target) + terminal.getAECurrentPower(source);
        if (!Double.isFinite(energy) || energy < 0 || energy > universal.getAEMaxPower(result)) return ItemStack.EMPTY;
        result.getOrCreateTag().putDouble("internalCurrentPower", energy);
        return result;
    }
    private static boolean containsItems(Tag value) {
        if (value instanceof CompoundTag compound) {
            if (compound.contains("id", Tag.TAG_STRING) && compound.getByte("Count") > 0) return true;
            for (var key : compound.getAllKeys()) if (containsItems(compound.get(key))) return true;
        } else if (value instanceof ListTag list) {
            for (var child : list) if (containsItems(child)) return true;
        }
        return false;
    }
}
