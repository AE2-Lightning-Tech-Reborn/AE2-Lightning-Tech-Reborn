package com.moakiee.ae2lt.integration.ae2wtlib;

import appeng.items.tools.powered.WirelessTerminalItem;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWorkstationStorage;
import de.mari_023.ae2wtlib.wut.ItemWUT;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/** Plans on copies. Conflicting links, real inventories or excess upgrades reject the recipe. */
public final class TianshuTerminalMerge {
    private static final Set<String> ENERGY_UPGRADES = Set.of("internalCurrentPower", "internalMaxPower", "upgrades");
    private TianshuTerminalMerge() {}

    public static boolean applies(ItemStack target, ItemStack source, String terminal) {
        return Ae2wtlibIntegration.TIANSHU_CRAFTING_NAME.equals(terminal)
                || TianshuWctIntegration.hasTianshuCrafting(target) || TianshuWctIntegration.hasTianshuCrafting(source);
    }

    public static ItemStack merge(ItemStack target, ItemStack source, String terminal) {
        if (!(target.getItem() instanceof ItemWUT universal)
                || !(source.getItem() instanceof WirelessTerminalItem incomingItem)) return ItemStack.EMPTY;
        var result = target.copy();
        var resultTag = result.getOrCreateTag();
        var sourceTag = source.getTag();
        if (sourceTag != null) for (var key : sourceTag.getAllKeys()) {
            if (ENERGY_UPGRADES.contains(key)) continue;
            var incoming = sourceTag.get(key);
            var existing = resultTag.get(key);
            if (key.equals("currentTerminal") && existing != null) continue;
            if (isPhysicalInventory(key)) {
                boolean hasIncoming = containsItems(key, incoming);
                boolean hasExisting = containsItems(key, existing);
                if (!hasIncoming) continue;
                if (hasExisting) return ItemStack.EMPTY;
            } else if (existing != null && !existing.equals(incoming)) {
                return ItemStack.EMPTY;
            }
            resultTag.put(key, incoming.copy());
        }
        resultTag.putBoolean(terminal, true);
        var upgrades = universal.getUpgrades(result);
        for (var card : incomingItem.getUpgrades(source.copy())) {
            if (!upgrades.addItems(card.copy()).isEmpty()) return ItemStack.EMPTY;
        }
        universal.onUpgradesChanged(result, upgrades);
        double energy = universal.getAECurrentPower(target) + incomingItem.getAECurrentPower(source);
        if (!Double.isFinite(energy) || energy < 0 || energy > universal.getAEMaxPower(result)) return ItemStack.EMPTY;
        result.getOrCreateTag().putDouble("internalCurrentPower", energy);
        return result;
    }

    private static boolean isPhysicalInventory(String key) {
        String lower = key.toLowerCase(java.util.Locale.ROOT);
        return key.equals("ae2lt:tianshu_workstations") || key.equals("patternEncodingLogic")
                || lower.contains("inventory") || lower.equals("craftinggrid") || lower.equals("crafting")
                || lower.equals("viewcells") || lower.equals("singularity");
    }

    private static boolean containsItems(String key, Tag value) {
        if (value == null) return false;
        if (key.equals("ae2lt:tianshu_workstations") && value instanceof CompoundTag work)
            return TianshuWorkstationStorage.containsItems(work);
        if (key.equals("patternEncodingLogic") && value instanceof CompoundTag pattern)
            return hasItem(pattern.get("blankPattern")) || hasItem(pattern.get("encodedPattern"));
        return hasItem(value);
    }

    private static boolean hasItem(Tag value) {
        if (value instanceof CompoundTag compound) {
            if (compound.contains("id", Tag.TAG_STRING) && compound.getInt("Count") > 0) return true;
            for (var key : compound.getAllKeys()) if (hasItem(compound.get(key))) return true;
        } else if (value instanceof ListTag list) {
            for (var entry : list) if (hasItem(entry)) return true;
        }
        return false;
    }
}
