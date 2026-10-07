package com.moakiee.ae2lt.machine.largeoverload;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Process cores and FE ports deliberately have no ME node or channel. */
public final class LargeFactoryAuxBlockEntity extends BlockEntity {
    private BlockPos controllerPos;
    private UUID machine;
    private final ItemStackHandler items;
    private final IEnergyStorage energy = new IEnergyStorage() {
        @Override public int receiveEnergy(int amount, boolean simulate) {
            var controller = controller();
            return controller == null ? 0 : controller.receiveEnergy(Math.max(0, amount), simulate);
        }
        @Override public int extractEnergy(int amount, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { var c = controller(); return c == null ? 0 : (int) Math.min(Integer.MAX_VALUE, c.energyStored()); }
        @Override public int getMaxEnergyStored() { var c = controller(); return c == null ? 0 : (int) Math.min(Integer.MAX_VALUE, c.energyCapacity()); }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return controller() != null; }
    };
    public LargeFactoryAuxBlockEntity(BlockPos pos, BlockState state) {
        super(LargeFactoryRegistration.AUX.get(), pos, state);
        items = new ItemStackHandler(LargeFactoryRegistration.component(state) == LargeFactoryComponent.PROCESS_CORE_HATCH ? LargeFactoryConfig.processSlots() : 0) {
            @Override public int getSlotLimit(int slot) { return 1; }
            @Override public boolean isItemValid(int slot, ItemStack stack) {
                return LargeFactoryRegistration.PROCESS_CORES.values().stream().anyMatch(item -> stack.is(item.get()));
            }
            @Override protected void onContentsChanged(int slot) {
                setChanged();
                var controller = controller();
                if (controller != null) controller.permissionsChanged();
            }
        };
    }
    public void bind(BlockPos pos, UUID id) { controllerPos = pos; machine = id; }
    public LargeFactoryControllerBlockEntity controller() {
        if (isRemoved() || level == null || level.getBlockEntity(worldPosition) != this
                || controllerPos == null || !level.isLoaded(controllerPos)) return null;
        return level.getBlockEntity(controllerPos) instanceof LargeFactoryControllerBlockEntity c && c.owns(worldPosition, machine) ? c : null;
    }
    public ItemStackHandler inventory() { return items; }
    public IEnergyStorage energy() { return LargeFactoryRegistration.component(getBlockState()) == LargeFactoryComponent.ENERGY_HATCH ? energy : null; }
    public void dropContents() {
        for (int i = 0; i < items.getSlots(); i++) {
            Block.popResource(level, worldPosition, items.getStackInSlot(i));
            items.setStackInSlot(i, ItemStack.EMPTY);
        }
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("ProcessCores", items.serializeNBT(registries));
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.deserializeNBT(registries, tag.getCompound("ProcessCores"));
        controllerPos = null; machine = null;
    }
}
