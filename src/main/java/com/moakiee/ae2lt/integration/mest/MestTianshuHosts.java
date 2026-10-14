package com.moakiee.ae2lt.integration.mest;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGridNode;
import appeng.api.storage.ILinkStatus;
import appeng.api.storage.MEStorage;
import com.lhy.mest.terminal.MESTMenuHost;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWirelessCraftingTermMenuHost;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWirelessPatternEncodingTermMenuHost;
import com.moakiee.ae2lt.registry.ModDataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;

/** Shares MEST's checked live link and battery, while retaining native Tianshu item persistence. */
final class MestTianshuHosts {
    private MestTianshuHosts() {}

    static final class Crafting extends TianshuWirelessCraftingTermMenuHost {
        private final MESTMenuHost outer;
        Crafting(MESTMenuHost outer) {
            super(outer.getItem(), outer.getPlayer(), outer.getLocator(), outer::returnToMainMenu);
            this.outer = outer;
        }
        @Override public IGridNode getActionableNode() { return outer == null ? null : outer.getActionableNode(); }
        @Override public MEStorage getInventory() { return outer == null ? super.getInventory() : outer.getInventory(); }
        @Override public ILinkStatus getLinkStatus() { return outer == null ? super.getLinkStatus() : outer.getLinkStatus(); }
        @Override public double extractAEPower(double amount, Actionable action, PowerMultiplier multiplier) {
            return outer == null ? 0 : outer.extractAEPower(amount, action, multiplier);
        }
        @Override public void tick() {}
        @Override public boolean isValid() { return outer != null && outer.isValid() && super.isValid(); }
    }

    static final class Pattern extends TianshuWirelessPatternEncodingTermMenuHost {
        private final MESTMenuHost outer;
        Pattern(MESTMenuHost outer) {
            super(outer.getItem(), outer.getPlayer(), outer.getLocator(), outer::returnToMainMenu);
            this.outer = outer;
        }
        @Override protected CompoundTag readPatternData() {
            return getItemStack().getOrDefault(ModDataComponents.MEST_TIANSHU_PATTERN, CustomData.EMPTY).copyTag();
        }
        @Override protected void writePatternData(CompoundTag data) {
            getItemStack().set(ModDataComponents.MEST_TIANSHU_PATTERN, CustomData.of(data));
            getPlayer().getInventory().setChanged();
        }
        @Override public IGridNode getActionableNode() { return outer == null ? null : outer.getActionableNode(); }
        @Override public MEStorage getInventory() { return outer == null ? super.getInventory() : outer.getInventory(); }
        @Override public ILinkStatus getLinkStatus() { return outer == null ? super.getLinkStatus() : outer.getLinkStatus(); }
        @Override public double extractAEPower(double amount, Actionable action, PowerMultiplier multiplier) {
            return outer == null ? 0 : outer.extractAEPower(amount, action, multiplier);
        }
        @Override public void tick() {}
        @Override public boolean isValid() { return outer != null && outer.isValid() && super.isValid(); }
    }
}
