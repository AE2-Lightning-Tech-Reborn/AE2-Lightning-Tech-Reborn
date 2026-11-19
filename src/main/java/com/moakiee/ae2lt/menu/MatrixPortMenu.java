package com.moakiee.ae2lt.menu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.BaseInternalInventory;
import appeng.api.inventories.InternalInventory;
import appeng.crafting.pattern.EncodedPatternItem;
import appeng.menu.slot.AppEngSlot;
import appeng.util.inv.AppEngInternalInventory;

import com.moakiee.ae2lt.blockentity.MatrixPortBlockEntity;
import com.moakiee.ae2lt.blockentity.MatrixPatternStorageBlockEntity;

/**
 * Pattern inventory exposed by a formed matter warping matrix port.
 *
 * <p>The menu synchronizes one physical warehouse at a time. This bounds each container update
 * even when a matrix contains 50 full T3 warehouses.</p>
 */
public class MatrixPortMenu extends AbstractContainerMenu implements MatrixMigrationMenu {
    public static final MenuType<MatrixPortMenu> TYPE =
            IMenuTypeExtension.create(MatrixPortMenu::clientCreate);

    public static final int COLUMNS = 9;
    public static final int VISIBLE_ROWS = 6;
    public static final int MAX_PATTERN_SLOTS = 50 * 288;
    public static final int WAREHOUSE_SLOTS = 288;

    public static final int PATTERN_X = 13;
    public static final int PATTERN_Y = 21;
    public static final int PLAYER_INVENTORY_X = 13;
    public static final int PLAYER_INVENTORY_Y = 140;
    public static final int PLAYER_HOTBAR_Y = 198;

    private static final int SLOT_SPACING = 18;
    private static final int OFFSCREEN_SLOT = -10_000;

    private final BlockPos blockPos;
    @Nullable
    private final MatrixPortBlockEntity host;
    @Nullable
    private final AppEngInternalInventory clientInventory;
    private final List<MatrixPatternSlot> patternSlots = new ArrayList<>();
    private final int patternSlotCount;
    private final int[] warehouseCapacities;
    private final List<MatrixPatternStorageBlockEntity> openingStorages;
    private int selectedWarehouse;
    private long patternContentRevision;
    private final MatrixMigrationMenuState migrationState = new MatrixMigrationMenuState();

    public MatrixPortMenu(int containerId, Inventory playerInventory, MatrixPortBlockEntity host) {
        this(containerId,
                playerInventory,
                host.getBlockPos(),
                host,
                null,
                host.getPatternStorages().stream().mapToInt(storage -> storage.capacity()).toArray());
    }

    private MatrixPortMenu(int containerId,
                           Inventory playerInventory,
                           BlockPos blockPos,
                           @Nullable MatrixPortBlockEntity host,
                           @Nullable AppEngInternalInventory clientInventory,
                           int[] warehouseCapacities) {
        super(TYPE, containerId);
        this.blockPos = blockPos;
        this.host = host;
        this.clientInventory = clientInventory;
        this.warehouseCapacities = warehouseCapacities;
        this.openingStorages = host == null ? List.of() : List.copyOf(host.getPatternStorages());
        this.patternSlotCount = WAREHOUSE_SLOTS;
        InternalInventory patternInventory = host != null
                ? new WarehousePageInventory()
                : clientInventory;
        migrationState.bind(playerInventory, this::getMigrationController);

        for (int slot = 0; slot < patternSlotCount; slot++) {
            var patternSlot = new MatrixPatternSlot(patternInventory, slot, this);
            patternSlot.x = OFFSCREEN_SLOT;
            patternSlot.y = OFFSCREEN_SLOT;
            patternSlots.add(patternSlot);
            addSlot(patternSlot);
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                addSlot(new Slot(
                        playerInventory,
                        column + row * COLUMNS + COLUMNS,
                        PLAYER_INVENTORY_X + column * SLOT_SPACING,
                        PLAYER_INVENTORY_Y + row * SLOT_SPACING));
            }
        }
        for (int column = 0; column < COLUMNS; column++) {
            addSlot(new Slot(
                    playerInventory,
                    column,
                    PLAYER_INVENTORY_X + column * SLOT_SPACING,
                    PLAYER_HOTBAR_Y));
        }
    }

    private static MatrixPortMenu clientCreate(int containerId,
                                               Inventory playerInventory,
                                               FriendlyByteBuf buffer) {
        BlockPos blockPos = buffer.readBlockPos();
        int warehouseCount = Mth.clamp(buffer.readVarInt(), 0, 50);
        int[] capacities = new int[warehouseCount];
        for (int i = 0; i < warehouseCount; i++) {
            capacities[i] = Mth.clamp(buffer.readVarInt(), 0, WAREHOUSE_SLOTS);
        }
        var clientInventory = new AppEngInternalInventory(WAREHOUSE_SLOTS);
        return new MatrixPortMenu(
                containerId, playerInventory, blockPos, null, clientInventory, capacities);
    }

    public static void writeExtraData(FriendlyByteBuf buffer, MatrixPortBlockEntity host) {
        buffer.writeBlockPos(host.getBlockPos());
        var storages = host.getPatternStorages();
        buffer.writeVarInt(storages.size());
        for (var storage : storages) buffer.writeVarInt(storage.capacity());
    }

    @Override public BlockPos getMigrationMenuPos() { return blockPos; }
    @Override public com.moakiee.ae2lt.blockentity.MatrixControllerBlockEntity getMigrationController() { return host == null ? null : host.getController(); }
    @Override public com.moakiee.ae2lt.logic.craft.migration.PatternMigrationSnapshot getMigrationSnapshot() { return migrationState.snapshot; }
    @Override public void acceptMigrationSnapshot(com.moakiee.ae2lt.logic.craft.migration.PatternMigrationSnapshot snapshot) { migrationState.snapshot = snapshot; }
    @Override public void broadcastChanges() { super.broadcastChanges(); migrationState.sync(this); }

    @Override
    public boolean stillValid(Player player) {
        if (host != null) {
            if (host.isRemoved() || host.getLevel() == null || player.level() != host.getLevel()
                    || !host.isFormed()) {
                return false;
            }
            BlockEntity current = host.getLevel().getBlockEntity(blockPos);
            if (current != host) {
                return false;
            }
            if (!host.getPatternStorages().equals(openingStorages)) return false;
        }
        return player.distanceToSqr(
                blockPos.getX() + 0.5D,
                blockPos.getY() + 0.5D,
                blockPos.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }

        Slot source = slots.get(index);
        if (!source.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack sourceStack = source.getItem();
        ItemStack original = sourceStack.copy();
        boolean moved;
        if (index < patternSlotCount) {
            moved = moveItemStackTo(sourceStack, patternSlotCount, slots.size(), true);
        } else {
            if (!PatternDetailsHelper.isEncodedPattern(sourceStack)) {
                return ItemStack.EMPTY;
            }
            moved = moveItemStackTo(sourceStack, 0, patternSlotCount, false);
        }

        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (sourceStack.isEmpty()) {
            source.set(ItemStack.EMPTY);
        } else {
            source.setChanged();
        }
        source.onTake(player, sourceStack);
        return original;
    }

    public int getPatternSlotCount() {
        return patternSlotCount;
    }

    public int getWarehouseCount() {
        return warehouseCapacities.length;
    }

    public int getSelectedWarehouse() {
        return selectedWarehouse;
    }

    public int getSelectedWarehouseCapacity() {
        return selectedWarehouse < warehouseCapacities.length
                ? warehouseCapacities[selectedWarehouse] : 0;
    }

    public void selectWarehouseClient(int warehouse) {
        if (clientInventory == null || warehouse < 0 || warehouse >= warehouseCapacities.length) return;
        selectedWarehouse = warehouse;
        clientInventory.clear();
        patternContentRevision++;
    }

    @Override
    public boolean clickMenuButton(Player player, int warehouse) {
        if (host == null || !stillValid(player) || warehouse < 0
                || warehouse >= warehouseCapacities.length) return false;
        selectedWarehouse = warehouse;
        broadcastFullState();
        return true;
    }

    /**
     * Monotonic client-side revision used by the search screen to avoid hashing every encoded
     * pattern every tick. Vanilla calls these update methods when a synchronized slot changes.
     */
    public long getPatternContentRevision() {
        return patternContentRevision;
    }

    @Override
    public void setItem(int slotId, int stateId, ItemStack stack) {
        super.setItem(slotId, stateId, stack);
        if (slotId >= 0 && slotId < patternSlotCount) {
            patternContentRevision++;
        }
    }

    @Override
    public void initializeContents(int stateId, List<ItemStack> items, ItemStack carried) {
        super.initializeContents(stateId, items, carried);
        if (patternSlotCount > 0) {
            patternContentRevision++;
        }
    }

    public List<MatrixPatternSlot> getPatternSlots() {
        return Collections.unmodifiableList(patternSlots);
    }

    private MatrixPatternStorageBlockEntity selectedStorage() {
        if (host == null || selectedWarehouse >= openingStorages.size()) return null;
        var storage = openingStorages.get(selectedWarehouse);
        return storage.isRemoved() ? null : storage;
    }

    private final class WarehousePageInventory extends BaseInternalInventory {
        @Override
        public int size() {
            return WAREHOUSE_SLOTS;
        }

        private InternalInventory inventoryFor(int slot) {
            var storage = selectedStorage();
            return storage != null && slot >= 0 && slot < storage.capacity()
                    ? storage.getTerminalPatternInventory() : null;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            var inventory = inventoryFor(slot);
            return inventory == null ? ItemStack.EMPTY : inventory.getStackInSlot(slot);
        }

        @Override
        public void setItemDirect(int slot, ItemStack stack) {
            var inventory = inventoryFor(slot);
            if (inventory != null) inventory.setItemDirect(slot, stack);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            var inventory = inventoryFor(slot);
            return inventory == null ? stack : inventory.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            var inventory = inventoryFor(slot);
            return inventory == null ? ItemStack.EMPTY : inventory.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            var inventory = inventoryFor(slot);
            return inventory == null ? 0 : inventory.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            var inventory = inventoryFor(slot);
            return inventory != null && inventory.isItemValid(slot, stack);
        }
    }

    public static final class MatrixPatternSlot extends AppEngSlot {
        private final int patternIndex;
        private final MatrixPortMenu menu;

        private MatrixPatternSlot(InternalInventory inventory, int patternIndex, MatrixPortMenu menu) {
            super(inventory, patternIndex);
            this.patternIndex = patternIndex;
            this.menu = menu;
        }

        public int getPatternIndex() {
            return patternIndex;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return patternIndex < menu.getSelectedWarehouseCapacity()
                    && PatternDetailsHelper.isEncodedPattern(stack) && super.mayPlace(stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public ItemStack getDisplayStack() {
            ItemStack pattern = super.getDisplayStack();
            if (!pattern.isEmpty() && pattern.getItem() instanceof EncodedPatternItem<?> encodedPattern) {
                ItemStack output = encodedPattern.getOutput(pattern);
                if (!output.isEmpty()) {
                    return output;
                }
            }
            return pattern;
        }
    }
}
