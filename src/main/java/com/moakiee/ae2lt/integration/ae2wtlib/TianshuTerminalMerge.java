package com.moakiee.ae2lt.integration.ae2wtlib;
import appeng.api.config.Actionable;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWorkstationStorage;
import com.moakiee.ae2lt.registry.ModDataComponents;
import de.mari_023.ae2wtlib.terminal.ItemWT;
import de.mari_023.ae2wtlib.wut.ItemWUT;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
public final class TianshuTerminalMerge {
    public static boolean applies(ItemStack target,ItemStack source,String name) {
        return name.equals(Ae2wtlibIntegration.TIANSHU_CRAFTING_NAME)||TianshuWctIntegration.hasTianshuCrafting(target)||TianshuWctIntegration.hasTianshuCrafting(source);
    }
    public static ItemStack merge(ItemStack target,ItemStack source,String name) {
        if(!(target.getItem() instanceof ItemWUT universal)||!(source.getItem() instanceof ItemWT terminal))return ItemStack.EMPTY;
        var workstation = com.moakiee.ae2lt.registry.ModDataComponents.TIANSHU_WORKSTATIONS;
        if (TianshuWorkstationStorage.containsItems(workstation.getOrDefault(target,new CompoundTag()))
                && TianshuWorkstationStorage.containsItems(workstation.getOrDefault(source,new CompoundTag()))) return ItemStack.EMPTY;
        for (var key : java.util.List.of("craftingGrid", "viewcells", "singularity")) {
            if (hasPhysicalInventory(target, key) && hasPhysicalInventory(source, key)) return ItemStack.EMPTY;
        }
        var result=target.copy();var data=result.getOrCreateTag();
        var incoming=source.getTag()==null?new CompoundTag():source.getTag().copy();
        incoming.remove("internalCurrentPower");incoming.remove("internalMaxPower");incoming.remove("upgrades");incoming.remove("currentTerminal");
        for(var key:incoming.getAllKeys()) {
            var value=incoming.get(key);var old=data.get(key);
            if(value instanceof CompoundTag compound && compound.isEmpty())continue;
            if(value instanceof net.minecraft.nbt.ListTag list && list.isEmpty())continue;
            if(old!=null&&!old.equals(value))return ItemStack.EMPTY;
            data.put(key,value.copy());
        }
        data.putBoolean(name,true);
        var upgrades=universal.getUpgrades(result);
        for(var card:terminal.getUpgrades(source.copy())) if(!upgrades.addItems(card.copy()).isEmpty())return ItemStack.EMPTY;
        universal.onUpgradesChanged(result,upgrades);
        double added=terminal.getAECurrentPower(source),energy=universal.getAECurrentPower(target)+added;
        if(!Double.isFinite(energy)||energy<0||energy>universal.getAEMaxPower(result))return ItemStack.EMPTY;
        if(universal.injectAEPower(result,added,Actionable.MODULATE)>0)return ItemStack.EMPTY;
        return result;
    }
    private static boolean hasPhysicalInventory(ItemStack stack, String key) {
        if (stack.getTag() == null) return false;
        var inventory = new appeng.util.inv.AppEngInternalInventory(64);
        inventory.readFromNBT(stack.getTag(), key);
        for (var item : inventory) if (!item.isEmpty()) return true;
        return false;
    }
    private TianshuTerminalMerge() {}
}
