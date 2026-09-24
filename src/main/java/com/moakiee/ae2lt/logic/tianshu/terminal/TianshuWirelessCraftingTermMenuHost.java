package com.moakiee.ae2lt.logic.tianshu.terminal;

import appeng.api.ids.AEComponents;
import appeng.api.implementations.blockentities.IViewCellStorage;
import appeng.api.inventories.InternalInventory;
import appeng.items.contents.StackDependentSupplier;
import appeng.menu.ISubMenu;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.parts.reporting.CraftingTerminalPart;
import appeng.util.inv.SupplierInternalInventory;
import com.moakiee.ae2lt.registry.ModDataComponents;
import de.mari_023.ae2wtlib.api.terminal.ItemWT;
import de.mari_023.ae2wtlib.api.terminal.WTMenuHost;
import java.util.function.BiConsumer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

/** Shares AE2WTLib's ordinary crafting component when installed in a universal terminal. */
public final class TianshuWirelessCraftingTermMenuHost extends WTMenuHost
        implements TianshuCraftingTerminalHost, IViewCellStorage {
    private final InternalInventory craftingGrid;
    private final TianshuWorkstationStorage workstations;
    private final ItemStack workstationAnchor;

    public TianshuWirelessCraftingTermMenuHost(ItemWT item, Player player, ItemMenuHostLocator locator,
            BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super(item, player, locator, returnToMainMenu);
        craftingGrid = new SupplierInternalInventory<>(new StackDependentSupplier<>(this::getItemStack,
                stack -> createInv(player, stack, AEComponents.CRAFTING_INV, 9)));
        var terminal = getItemStack();
        workstationAnchor = terminal;
        workstations = new TianshuWorkstationStorage(data -> {
            if (data.isEmpty()) terminal.remove(ModDataComponents.TIANSHU_WORKSTATIONS);
            else terminal.set(ModDataComponents.TIANSHU_WORKSTATIONS, CustomData.of(data));
            player.getInventory().setChanged();
        });
        workstations.load(terminal.getOrDefault(ModDataComponents.TIANSHU_WORKSTATIONS, CustomData.EMPTY).copyTag());
    }

    @Override public TianshuWorkstationStorage getWorkstationStorage() { return workstations; }

    @Override public boolean isValid() {
        return (isClientSide() || getItemStack() == workstationAnchor) && super.isValid();
    }

    @Nullable @Override public InternalInventory getSubInventory(Identifier id) {
        return CraftingTerminalPart.INV_CRAFTING.equals(id) ? craftingGrid : super.getSubInventory(id);
    }
}
