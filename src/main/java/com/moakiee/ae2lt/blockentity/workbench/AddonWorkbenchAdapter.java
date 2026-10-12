package com.moakiee.ae2lt.blockentity.workbench;

import com.moakiee.ae2lt.api.device.WorkbenchDevice;
import com.moakiee.ae2lt.device.DeviceKind;
import com.moakiee.ae2lt.device.DeviceSlotType;
import com.moakiee.ae2lt.device.energy.DeviceEnergyBuffer;
import com.moakiee.ae2lt.device.module.DeviceModuleStorage;
import com.moakiee.ae2lt.device.network.DeviceNetworkBinding;
import com.moakiee.ae2lt.device.network.RailgunNetworkBinding;
import com.moakiee.ae2lt.menu.Ae2ltSlotSemantics;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;

/** Adapts the public contract without replacing any built-in device-kind entry. */
public final class AddonWorkbenchAdapter implements DeviceWorkbenchAdapter, DeviceModuleStorage, DeviceEnergyBuffer {
    private final WorkbenchDevice device;
    public AddonWorkbenchAdapter(WorkbenchDevice device) { this.device = Objects.requireNonNull(device); }
    public DeviceKind deviceKind() { return DeviceKind.RAILGUN; }
    public DeviceModuleStorage moduleStorage() { return this; }
    public DeviceEnergyBuffer energyBuffer() { return this; }
    public DeviceNetworkBinding networkBinding() { return RailgunNetworkBinding.INSTANCE; }
    public List<StructuralSlotSpec> structuralSlots() {
        return device.hasCoreSlot() ? List.of(new StructuralSlotSpec(0, DeviceSlotType.CORE,
                Ae2ltSlotSemantics.OVERLOAD_DEVICE_WORKBENCH_CORE)) : List.of();
    }
    public Predicate<ItemStack> moduleInputValidator(ItemStack deviceStack, HolderLookup.Provider registries) { return candidate -> device.canInstallOne(deviceStack, candidate); }
    public List<ItemStack> listModuleEntries(ItemStack deviceStack, HolderLookup.Provider registries) { return device.modules(deviceStack); }
    public boolean canInstallOne(ItemStack deviceStack, HolderLookup.Provider registries, ItemStack slot) { return device.canInstallOne(deviceStack, slot); }
    public boolean installOne(ItemStack deviceStack, HolderLookup.Provider registries, ItemStack slot) { return device.installOne(deviceStack, slot); }
    public ItemStack uninstallOne(ItemStack deviceStack, HolderLookup.Provider registries, String moduleId) { return device.uninstallOne(deviceStack, moduleId); }
    public ItemStack uninstallAll(ItemStack deviceStack, HolderLookup.Provider registries, String moduleId) { return device.uninstallAll(deviceStack, moduleId); }
    public String moduleTypeId(ItemStack slot) { return device.moduleId(slot); }
    public int maxInstallAmount(ItemStack slot) { return device.maxInstallAmount(slot); }
    public ItemStack getStructuralSlot(ItemStack deviceStack, HolderLookup.Provider registries, StructuralSlotSpec slot) { return device.core(deviceStack); }
    public void setStructuralSlot(ItemStack deviceStack, HolderLookup.Provider registries, StructuralSlotSpec slot, ItemStack value) {
        if (value.isEmpty() || device.canPlaceCore(deviceStack, value)) device.setCore(deviceStack, value.copy());
    }
    public ItemStack removeStructuralSlot(ItemStack deviceStack, HolderLookup.Provider registries, StructuralSlotSpec slot, int amount) {
        if (amount <= 0) return ItemStack.EMPTY;
        var original = device.core(deviceStack);
        var taken = original.copyWithCount(Math.min(original.getCount(), amount));
        var remaining = original.copy(); remaining.shrink(taken.getCount());
        device.setCore(deviceStack, remaining);
        return taken;
    }
    public boolean canPlaceStructural(ItemStack deviceStack, HolderLookup.Provider registries, StructuralSlotSpec slot, ItemStack value) { return device.canPlaceCore(deviceStack, value); }
    public boolean mayPickupStructural(ItemStack deviceStack, HolderLookup.Provider registries, StructuralSlotSpec slot, Player player, ItemStack carried) { return device.mayRemoveCore(deviceStack, player, carried); }
    public void onDeviceInserted(ItemStack deviceStack) { device.onInserted(deviceStack); }
    public void onModulesChanged(ItemStack deviceStack, HolderLookup.Provider registries, Dist dist) { device.onModulesChanged(deviceStack, dist == Dist.CLIENT); }
    public boolean serverTick(ItemStack deviceStack, WorkbenchDevice.Context context) { return device.serverTick(deviceStack, context); }
    public List<ItemStack> listEntries(ItemStack deviceStack) { return device.modules(deviceStack); }
    public int getCount(ItemStack deviceStack, String moduleId) { return listEntries(deviceStack).stream().filter(slot -> device.moduleId(slot).equals(moduleId)).mapToInt(ItemStack::getCount).sum(); }
    public boolean canInstallOne(ItemStack deviceStack, ItemStack slot) { return device.canInstallOne(deviceStack, slot); }
    public boolean installOne(ItemStack deviceStack, ItemStack slot) { return device.installOne(deviceStack, slot); }
    public ItemStack uninstallOne(ItemStack deviceStack, String moduleId) { return device.uninstallOne(deviceStack, moduleId); }
    public ItemStack uninstallAll(ItemStack deviceStack, String moduleId) { return device.uninstallAll(deviceStack, moduleId); }
    public boolean hasAnyInstalled(ItemStack deviceStack) { return !listEntries(deviceStack).isEmpty(); }
    public Stream<ItemStack> installedModuleStacks(ItemStack deviceStack) { return listEntries(deviceStack).stream(); }
    public long stored(ItemStack deviceStack) { return device.storedEnergy(deviceStack); }
    public long capacity(ItemStack deviceStack) { return device.energyCapacity(deviceStack); }
    public boolean tryConsume(ItemStack deviceStack, ServerPlayer player, long amount) { return amount == 0; }
    public void refill(ItemStack deviceStack, ServerPlayer player) {}
}
