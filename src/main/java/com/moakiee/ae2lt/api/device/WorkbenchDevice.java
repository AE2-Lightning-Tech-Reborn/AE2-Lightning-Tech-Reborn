package com.moakiee.ae2lt.api.device;

import appeng.api.networking.IGrid;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Device operations used by the shared workbench's one core slot and module list.
 * Mutators own the device's components, never the cursor/input stack. InstallOne
 * consumes one unit logically; the workbench removes that unit from its input.
 */
public interface WorkbenchDevice {
    default boolean hasCoreSlot() { return true; }
    ItemStack core(ItemStack device);
    boolean canPlaceCore(ItemStack device, ItemStack core);
    void setCore(ItemStack device, ItemStack core);
    default boolean mayRemoveCore(ItemStack device, Player player, ItemStack carried) { return true; }

    List<ItemStack> modules(ItemStack device);
    String moduleId(ItemStack module);
    int maxInstallAmount(ItemStack module);
    boolean canInstallOne(ItemStack device, ItemStack module);
    boolean installOne(ItemStack device, ItemStack module);
    ItemStack uninstallOne(ItemStack device, String moduleId);
    ItemStack uninstallAll(ItemStack device, String moduleId);

    default long storedEnergy(ItemStack device) { return 0; }
    default long energyCapacity(ItemStack device) { return 0; }
    default void onInserted(ItemStack device) {}
    default void onModulesChanged(ItemStack device, boolean clientSide) {}

    /** Active server-side workbench only. Return true when persistent state changed. */
    default boolean serverTick(ItemStack device, Context context) { return false; }

    record Context(ServerLevel level, BlockPos pos, IGrid grid) {
        public Context { pos = pos.immutable(); }
    }
}
