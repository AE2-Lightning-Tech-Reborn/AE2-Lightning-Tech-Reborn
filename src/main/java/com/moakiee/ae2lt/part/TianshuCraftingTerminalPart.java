package com.moakiee.ae2lt.part;

import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.items.parts.PartModels;
import appeng.parts.PartModel;
import appeng.parts.reporting.CraftingTerminalPart;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuCraftingTerminalHost;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWorkstationStorage;
import com.moakiee.ae2lt.menu.TianshuCraftingTermMenu;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

public final class TianshuCraftingTerminalPart extends CraftingTerminalPart implements TianshuCraftingTerminalHost {
    private final TianshuWorkstationStorage workstations = new TianshuWorkstationStorage(data -> {
        if (getHost() != null) getHost().markForSave();
    });
    @PartModels private static final ResourceLocation OFF = new ResourceLocation("ae2lt", "part/tianshu_crafting_terminal_off");
    @PartModels private static final ResourceLocation ON = new ResourceLocation("ae2lt", "part/tianshu_crafting_terminal_on");
    private static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, OFF, MODEL_STATUS_OFF);
    private static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, ON, MODEL_STATUS_ON);
    private static final IPartModel MODELS_CHANNEL = new PartModel(MODEL_BASE, ON, MODEL_STATUS_HAS_CHANNEL);

    public TianshuCraftingTerminalPart(IPartItem<?> partItem) { super(partItem); }
    @Override public MenuType<?> getMenuType(Player player) { return TianshuCraftingTermMenu.TYPE; }
    @Override public IPartModel getStaticModels() { return selectModel(MODELS_OFF, MODELS_ON, MODELS_CHANNEL); }

    @Override public TianshuWorkstationStorage getWorkstationStorage() { return workstations; }

    @Override public void readFromNBT(CompoundTag data) {
        super.readFromNBT(data);
        workstations.load(data.getCompound("tianshuWorkstations"));
    }

    @Override public void writeToNBT(CompoundTag data) {
        super.writeToNBT(data);
        data.put("tianshuWorkstations", workstations.read());
    }

    @Override public void addAdditionalDrops(List<ItemStack> drops, boolean wrenched) {
        super.addAdditionalDrops(drops, wrenched);
        workstations.addDrops(drops);
    }

    @Override public void clearContent() {
        super.clearContent();
        workstations.clear();
    }
}
