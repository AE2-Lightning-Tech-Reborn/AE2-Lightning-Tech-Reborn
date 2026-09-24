package com.moakiee.ae2lt.part;

import appeng.api.parts.IPartItem;
import appeng.parts.reporting.CraftingTerminalPart;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuCraftingTerminalHost;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWorkstationStorage;
import com.moakiee.ae2lt.menu.TianshuCraftingTermMenu;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

public final class TianshuCraftingTerminalPart extends CraftingTerminalPart implements TianshuCraftingTerminalHost {
    private final TianshuWorkstationStorage workstations = new TianshuWorkstationStorage(data -> {
        if (getHost() != null) getHost().markForSave();
    });

    public TianshuCraftingTerminalPart(IPartItem<?> partItem) { super(partItem); }
    @Override public MenuType<?> getMenuType(Player player) { return TianshuCraftingTermMenu.TYPE; }

    @Override public TianshuWorkstationStorage getWorkstationStorage() { return workstations; }

    @Override public void readFromNBT(net.minecraft.world.level.storage.ValueInput data) {
        super.readFromNBT(data);
        workstations.load(data.read("tianshuWorkstations", CompoundTag.CODEC).orElseGet(CompoundTag::new));
    }

    @Override public void writeToNBT(net.minecraft.world.level.storage.ValueOutput data) {
        super.writeToNBT(data);
        data.store("tianshuWorkstations", CompoundTag.CODEC, workstations.read());
    }

    @Override public void addAdditionalDrops(List<ItemStack> drops, boolean wrenched) {
        super.addAdditionalDrops(drops, wrenched);
        workstations.addDrops(drops, getLevel().registryAccess());
    }

    @Override public void clearContent() {
        super.clearContent();
        workstations.clear();
    }
}
