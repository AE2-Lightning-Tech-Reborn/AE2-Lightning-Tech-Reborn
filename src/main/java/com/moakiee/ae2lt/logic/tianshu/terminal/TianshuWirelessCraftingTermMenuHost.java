package com.moakiee.ae2lt.logic.tianshu.terminal;

import appeng.menu.ISubMenu;
import com.moakiee.ae2lt.registry.ModDataComponents;
import de.mari_023.ae2wtlib.wct.WCTMenuHost;
import java.util.function.BiConsumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Uses the native WCT crafting inventory and its NBT schema inside universal terminals. */
public final class TianshuWirelessCraftingTermMenuHost extends WCTMenuHost implements TianshuCraftingTerminalHost {
    private final TianshuWorkstationStorage workstations;
    private final ItemStack workstationAnchor;

    public TianshuWirelessCraftingTermMenuHost(Player player, @Nullable Integer slot, ItemStack stack,
            BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super(player, slot, stack, returnToMainMenu);
        workstationAnchor = stack;
        workstations = new TianshuWorkstationStorage(data -> {
            if (data.isEmpty()) ModDataComponents.TIANSHU_WORKSTATIONS.remove(stack);
            else ModDataComponents.TIANSHU_WORKSTATIONS.set(stack, data);
            player.getInventory().setChanged();
        });
        workstations.load(ModDataComponents.TIANSHU_WORKSTATIONS.getOrDefault(stack, new CompoundTag()));
    }

    @Override public TianshuWorkstationStorage getWorkstationStorage() { return workstations; }
    @Override public boolean stillValid() {
        return getItemStack() == workstationAnchor && super.stillValid();
    }
}
