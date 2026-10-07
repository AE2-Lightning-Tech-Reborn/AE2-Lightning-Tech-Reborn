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
import net.neoforged.api.distmarker.Dist;

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
    public Predicate<ItemStack> moduleInputValidator(ItemStack d, HolderLookup.Provider r) { return s -> device.canInstallOne(d, s); }
    public List<ItemStack> listModuleEntries(ItemStack d, HolderLookup.Provider r) { return device.modules(d); }
    public boolean canInstallOne(ItemStack d, HolderLookup.Provider r, ItemStack s) { return device.canInstallOne(d, s); }
    public boolean installOne(ItemStack d, HolderLookup.Provider r, ItemStack s) { return device.installOne(d, s); }
    public ItemStack uninstallOne(ItemStack d, HolderLookup.Provider r, String id) { return device.uninstallOne(d, id); }
    public ItemStack uninstallAll(ItemStack d, HolderLookup.Provider r, String id) { return device.uninstallAll(d, id); }
    public String moduleTypeId(ItemStack s) { return device.moduleId(s); }
    public int maxInstallAmount(ItemStack s) { return device.maxInstallAmount(s); }
    public ItemStack getStructuralSlot(ItemStack d, HolderLookup.Provider r, StructuralSlotSpec s) { return device.core(d); }
    public void setStructuralSlot(ItemStack d, HolderLookup.Provider r, StructuralSlotSpec s, ItemStack value) {
        if (value.isEmpty() || device.canPlaceCore(d, value)) device.setCore(d, value.copy());
    }
    public ItemStack removeStructuralSlot(ItemStack d, HolderLookup.Provider r, StructuralSlotSpec s, int amount) {
        if (amount <= 0) return ItemStack.EMPTY;
        var original = device.core(d);
        var taken = original.copyWithCount(Math.min(original.getCount(), amount));
        var remaining = original.copy(); remaining.shrink(taken.getCount());
        device.setCore(d, remaining);
        return taken;
    }
    public boolean canPlaceStructural(ItemStack d, HolderLookup.Provider r, StructuralSlotSpec s, ItemStack value) { return device.canPlaceCore(d, value); }
    public boolean mayPickupStructural(ItemStack d, HolderLookup.Provider r, StructuralSlotSpec s, Player p, ItemStack carried) { return device.mayRemoveCore(d, p, carried); }
    public void onDeviceInserted(ItemStack d) { device.onInserted(d); }
    public void onModulesChanged(ItemStack d, HolderLookup.Provider r, Dist dist) { device.onModulesChanged(d, dist == Dist.CLIENT); }
    public boolean serverTick(ItemStack d, WorkbenchDevice.Context context) { return device.serverTick(d, context); }
    public List<ItemStack> listEntries(ItemStack d) { return device.modules(d); }
    public int getCount(ItemStack d, String id) { return listEntries(d).stream().filter(s -> device.moduleId(s).equals(id)).mapToInt(ItemStack::getCount).sum(); }
    public boolean canInstallOne(ItemStack d, ItemStack s) { return device.canInstallOne(d, s); }
    public boolean installOne(ItemStack d, ItemStack s) { return device.installOne(d, s); }
    public ItemStack uninstallOne(ItemStack d, String id) { return device.uninstallOne(d, id); }
    public ItemStack uninstallAll(ItemStack d, String id) { return device.uninstallAll(d, id); }
    public boolean hasAnyInstalled(ItemStack d) { return !listEntries(d).isEmpty(); }
    public Stream<ItemStack> installedModuleStacks(ItemStack d) { return listEntries(d).stream(); }
    public long stored(ItemStack d) { return device.storedEnergy(d); }
    public long capacity(ItemStack d) { return device.energyCapacity(d); }
    public boolean tryConsume(ItemStack d, ServerPlayer p, long amount) { return amount == 0; }
    public void refill(ItemStack d, ServerPlayer p) {}
}
