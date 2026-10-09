package com.moakiee.ae2lt.logic.tianshu.terminal;
import appeng.menu.ISubMenu;
import de.mari_023.ae2wtlib.wct.WCTMenuHost;
import java.util.function.BiConsumer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;
public final class TianshuWirelessCraftingTermMenuHost extends WCTMenuHost implements TianshuCraftingTerminalHost {
    private final TianshuWorkstationStorage workstations;
    private final ItemStack anchor;
    public TianshuWirelessCraftingTermMenuHost(Player player, @Nullable Integer slot, ItemStack stack,
            BiConsumer<Player, ISubMenu> returnToMain) {
        super(player, slot, stack, returnToMain); anchor=stack;
        workstations=new TianshuWorkstationStorage(data -> {
            var key=com.moakiee.ae2lt.registry.ModDataComponents.TIANSHU_WORKSTATIONS;
            if (data.isEmpty()) key.remove(stack); else key.set(stack,data);
            player.getInventory().setChanged();
        });
        workstations.load(com.moakiee.ae2lt.registry.ModDataComponents.TIANSHU_WORKSTATIONS.getOrDefault(stack,new CompoundTag()));
    }
    @Override public TianshuWorkstationStorage getWorkstationStorage() { return workstations; }
    @Override public boolean stillValid() { return getItemStack()==anchor && super.stillValid(); }
}
