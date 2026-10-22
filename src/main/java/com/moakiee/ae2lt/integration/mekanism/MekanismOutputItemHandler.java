package com.moakiee.ae2lt.integration.mekanism;

import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

import appeng.api.storage.MEStorage;
import appeng.me.storage.ExternalStorageFacade;

import mekanism.api.inventory.IMekanismInventory;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.component.config.slot.InventorySlotInfo;
import mekanism.common.tile.interfaces.ISideConfiguration;

/** Keeps automatic provider returns out of Mekanism's input, extra and energy slots. */
public final class MekanismOutputItemHandler implements IItemHandler {
    private final IItemHandler delegate;
    private final IMekanismInventory inventory;
    private final Supplier<ConfigInfo> config;
    private final Direction face;

    MekanismOutputItemHandler(IItemHandler delegate, IMekanismInventory inventory,
                             Supplier<ConfigInfo> config, Direction face) {
        this.delegate = delegate;
        this.inventory = inventory;
        this.config = config;
        this.face = face;
    }

    /** Called only when Mekanism is installed; other inventories keep their usual storage view. */
    @Nullable
    public static MEStorage restrict(BlockEntity blockEntity, Direction face, MEStorage fallback) {
        if (!(blockEntity instanceof ISideConfiguration configuration)
                || !(blockEntity instanceof IMekanismInventory inventory)
                || !inventory.hasInventory()
                || configuration.getConfig().getConfig(TransmissionType.ITEM) == null) {
            return fallback;
        }
        var level = blockEntity.getLevel();
        var handler = level == null ? null : level.getBlockEntity(blockEntity.getBlockPos()) == blockEntity
                ? blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, face).orElse(null) : null;
        return handler == null ? null : ExternalStorageFacade.of(new MekanismOutputItemHandler(
                handler, inventory,
                () -> configuration.getConfig().getConfig(TransmissionType.ITEM), face));
    }

    private boolean isOutput(int slot) {
        // Resolve the live sided slot list: changing a machine's side config can
        // change both the exposed slots and their indices without replacing this wrapper.
        if (delegate.getSlots() != inventory.getSlots(face)) {
            return false;
        }
        var inventorySlot = inventory.getInventorySlot(slot, face);
        var itemConfig = config.get();
        if (inventorySlot == null || itemConfig == null) {
            return false;
        }
        // INPUT_OUTPUT also describes inputs. Only explicit output groups
        // identify products; extractability alone also includes auxiliary slots.
        return contains(itemConfig, DataType.OUTPUT, inventorySlot)
                || contains(itemConfig, DataType.OUTPUT_1, inventorySlot)
                || contains(itemConfig, DataType.OUTPUT_2, inventorySlot);
    }

    private static boolean contains(ConfigInfo config, DataType type, IInventorySlot slot) {
        return config.getSlotInfo(type) instanceof InventorySlotInfo info && info.hasSlot(slot);
    }

    @Override
    public int getSlots() {
        return delegate.getSlots();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return isOutput(slot) ? delegate.getStackInSlot(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return isOutput(slot) ? delegate.insertItem(slot, stack, simulate) : stack;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return isOutput(slot) ? delegate.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return isOutput(slot) ? delegate.getSlotLimit(slot) : 0;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return isOutput(slot) && delegate.isItemValid(slot, stack);
    }
}
