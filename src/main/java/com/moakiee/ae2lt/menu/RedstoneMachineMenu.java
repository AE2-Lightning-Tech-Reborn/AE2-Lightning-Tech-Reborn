package com.moakiee.ae2lt.menu;

import appeng.api.config.RedstoneMode;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import com.moakiee.ae2lt.machine.common.RedstoneControlledMachine;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Shared server-owned redstone setting and upgrade-slot shift-click routing. */
public abstract class RedstoneMachineMenu<H extends AENetworkedBlockEntity & RedstoneControlledMachine>
        extends AEBaseMenu {
    @GuiSync(140) public RedstoneMode redstoneMode = RedstoneMode.IGNORE;
    @GuiSync(141) public boolean redstoneCardInstalled;
    @GuiSync(142) public boolean redstonePaused;

    private final H redstoneHost;

    protected RedstoneMachineMenu(MenuType<?> type, int id, Inventory playerInventory, H host) {
        super(type, id, playerInventory, host);
        redstoneHost = host;
        registerClientAction("cycleRedstoneMode", Boolean.class, backwards -> {
            if (isServerSide() && host.getRedstoneControl().hasCard()) {
                host.getRedstoneControl().cycleMode(Boolean.TRUE.equals(backwards));
                broadcastChanges();
            }
        });
    }

    public void clientCycleRedstoneMode(boolean backwards) {
        sendClientAction("cycleRedstoneMode", backwards);
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            var control = redstoneHost.getRedstoneControl();
            redstoneMode = control.getMode();
            redstoneCardInstalled = control.hasCard();
            redstonePaused = !control.isEnabled();
        }
        super.broadcastChanges();
    }

    protected ItemStack moveIntoRedstoneUpgrade(ItemStack stack) {
        for (Slot slot : getSlots(SlotSemantics.UPGRADE)) {
            stack = slot.safeInsert(stack);
            if (stack.isEmpty()) break;
        }
        return stack;
    }
}
