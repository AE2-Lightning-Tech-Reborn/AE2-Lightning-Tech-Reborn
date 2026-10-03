package com.moakiee.ae2lt.blockentity;

import appeng.capabilities.Capabilities;
import appeng.api.inventories.InternalInventory;
import appeng.api.implementations.menuobjects.IPortableTerminal;
import appeng.api.storage.MEStorage;
import appeng.api.util.IConfigManager;
import appeng.api.config.Settings;
import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.config.ViewItems;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.parts.reporting.CraftingTerminalPart;
import appeng.me.storage.CompositeStorage;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.parts.automation.StackWorldBehaviors;
import appeng.parts.automation.ForgeExternalStorageStrategy;
import appeng.parts.automation.HandlerStrategy;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import com.moakiee.ae2lt.block.PigmeeSynthesisStationBlock;
import com.moakiee.ae2lt.menu.PigmeeSynthesisStationMenu;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import appeng.api.behaviors.ExternalStorageStrategy;
import appeng.api.stacks.AEKeyType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

/**
 * Terminal host for {@link PigmeeSynthesisStationBlock}.
 *
 * <p>External-storage strategies from all six neighbours are combined. In
 * particular, a neighbour exposing {@link Capabilities#STORAGE} is
 * skipped entirely; this keeps the station from becoming a disguised terminal
 * merely by placing it next to an interface.</p>
 *
 * <p>AE2 15 uses {@link IPortableTerminal} as the marker for a terminal with its
 * own power source. Implement it here to use the local, power-free storage path
 * without creating an ME grid node.</p>
 */
public final class PigmeeSynthesisStationBlockEntity extends AEBaseBlockEntity
        implements IPortableTerminal, InternalInventoryHost {
    public static final ResourceLocation INV_CRAFTING =
            CraftingTerminalPart.INV_CRAFTING;

    private static final String TAG_CRAFTING = "CraftingInventory";
    private final AppEngInternalInventory craftingInventory =
            new AppEngInternalInventory(this, 9);
    private final IConfigManager configManager = new appeng.util.ConfigManager(this::saveChanges);

    private final Map<Direction, Map<AEKeyType, ExternalStorageStrategy>> strategiesBySide =
            new EnumMap<>(Direction.class);

    // AE2 captures this object when opening the menu. Resolve the live target on
    // every operation so removed/replaced containers cannot remain accessible.
    private final MEStorage terminalStorage = new MEStorage() {
        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            if (amount <= 0) {
                return 0;
            }
            long remaining = amount;
            for (var storage : findAdjacentStorages()) {
                remaining -= storage.insert(what, remaining, mode, source);
                if (remaining == 0) {
                    break;
                }
            }
            return amount - remaining;
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            if (amount <= 0) {
                return 0;
            }
            long remaining = amount;
            for (var storage : findAdjacentStorages()) {
                remaining -= storage.extract(what, remaining, mode, source);
                if (remaining == 0) {
                    break;
                }
            }
            return amount - remaining;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            for (var storage : findAdjacentStorages()) {
                storage.getAvailableStacks(out);
            }
        }

        @Override
        public Component getDescription() {
            return Component.translatable("block.ae2lt.pigmee_synthesis_station");
        }
    };

    public PigmeeSynthesisStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PIGMEE_SYNTHESIS_STATION.get(), pos, state);
        configManager.registerSetting(Settings.SORT_BY, SortOrder.NAME);
        configManager.registerSetting(Settings.SORT_DIRECTION, SortDir.ASCENDING);
        configManager.registerSetting(Settings.VIEW_MODE, ViewItems.ALL);
        configManager.registerSetting(Settings.TYPE_FILTER, appeng.api.config.TypeFilter.ALL);
    }

    @Override
    public MEStorage getInventory() {
        return terminalStorage;
    }

    public boolean hasAdjacentStorage() {
        return !findAdjacentStorages().isEmpty();
    }

    /** Manual crafting and adjacent inventory access do not require AE power. */
    @Override
    public double extractAEPower(double amount, Actionable mode, PowerMultiplier multiplier) {
        return amount;
    }

    @Override
    public IConfigManager getConfigManager() {
        return configManager;
    }

    @Override
    public InternalInventory getSubInventory(ResourceLocation id) {
        return INV_CRAFTING.equals(id)
                ? craftingInventory
                : InternalInventory.empty();
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        if (level != null && !level.isClientSide()) {
            MenuOpener.open(
                    PigmeeSynthesisStationMenu.TYPE,
                    player,
                    MenuLocators.forBlockEntity(this));
        }
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return ModBlocks.PIGMEE_SYNTHESIS_STATION.get().asItem().getDefaultInstance();
    }

    @Override
    public void onChangeInventory(InternalInventory inventory, int slot) {
        saveChanges();
    }

    @Override
    public boolean isClientSide() {
        return level == null || level.isClientSide();
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        for (ItemStack stack : craftingInventory) {
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        craftingInventory.clear();
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        craftingInventory.writeToNBT(tag, TAG_CRAFTING);
        configManager.writeToNBT(tag);
    }

    @Override
    public void loadTag(CompoundTag tag) {
        super.loadTag(tag);
        craftingInventory.readFromNBT(tag, TAG_CRAFTING);
        configManager.readFromNBT(tag);
    }

    @Override
    public void setRemoved() {
        strategiesBySide.clear();
        super.setRemoved();
    }

    @Override
    public void clearRemoved() {
        strategiesBySide.clear();
        super.clearRemoved();
    }

    private List<MEStorage> findAdjacentStorages() {
        if (level == null || level.isClientSide() || isRemoved()
                || !level.hasChunkAt(worldPosition) || level.getBlockEntity(worldPosition) != this) {
            return List.of();
        }

        var serverLevel = (net.minecraft.server.level.ServerLevel) level;
        var storages = new ArrayList<MEStorage>(6);
        Map<AEKeyType, Set<Object>> seenHandlers = new IdentityHashMap<>();
        for (Direction side : Direction.values()) {
            BlockPos targetPos = worldPosition.relative(side);
            if (!level.hasChunkAt(targetPos)) {
                strategiesBySide.remove(side);
                continue;
            }

            BlockEntity target = level.getBlockEntity(targetPos);
            if (target != null && target.isRemoved()) {
                strategiesBySide.remove(side);
                continue;
            }

            // ME storage is intentionally not a valid station source. This is
            // the important distinction from simply mounting an interface.
            if (target != null && target.getCapability(Capabilities.STORAGE, side.getOpposite()).isPresent()) {
                strategiesBySide.remove(side);
                continue;
            }

            var strategies = strategiesBySide.computeIfAbsent(side, direction ->
                    StackWorldBehaviors.createExternalStorageStrategies(
                            serverLevel, targetPos, direction.getOpposite()));
            if (strategies.isEmpty()) {
                continue;
            }

            Map<AEKeyType, MEStorage> wrappers =
                    new IdentityHashMap<>(strategies.size());
            for (var entry : strategies.entrySet()) {
                Object identity = null;
                MEStorage wrapper;
                // A multiblock can replace its inventory without invalidating
                // every part's capability cache. Use the actual live handler
                // for AE2's standard adapters, including when it becomes null.
                // The wrapper and the deduplication identity must be the same
                // handler, not a cached wrapper paired with a fresh identity.
                if (entry.getValue() instanceof ForgeExternalStorageStrategy<?, ?>
                        && entry.getKey() == AEKeyType.items()) {
                    var handler = target == null ? null : target.getCapability(
                            ForgeCapabilities.ITEM_HANDLER, side.getOpposite()).orElse(null);
                    identity = handler;
                    var facade = handler == null ? null : HandlerStrategy.ITEMS.getFacade(handler);
                    if (facade != null) {
                        facade.setChangeListener(this::saveChanges);
                        facade.setExtractableOnly(false);
                    }
                    wrapper = facade;
                } else if (entry.getValue() instanceof ForgeExternalStorageStrategy<?, ?>
                        && entry.getKey() == AEKeyType.fluids()) {
                    var handler = target == null ? null : target.getCapability(
                            ForgeCapabilities.FLUID_HANDLER, side.getOpposite()).orElse(null);
                    identity = handler;
                    var facade = handler == null ? null : HandlerStrategy.FLUIDS.getFacade(handler);
                    if (facade != null) {
                        facade.setChangeListener(this::saveChanges);
                        facade.setExtractableOnly(false);
                    }
                    wrapper = facade;
                } else {
                    wrapper = entry.getValue().createWrapper(false, this::saveChanges);
                }
                if (wrapper != null) {
                    // Multiple ports may expose the exact same handler. Count
                    // and simulate it only once, independently for each key type.
                    if (identity == null && entry.getKey() == AEKeyType.items()) {
                        identity = target == null ? null : target.getCapability(
                                ForgeCapabilities.ITEM_HANDLER, side.getOpposite()).orElse(null);
                    } else if (identity == null && entry.getKey() == AEKeyType.fluids()) {
                        identity = target == null ? null : target.getCapability(
                                ForgeCapabilities.FLUID_HANDLER, side.getOpposite()).orElse(null);
                    }
                    if (seenHandlers.computeIfAbsent(entry.getKey(), key ->
                            Collections.newSetFromMap(new IdentityHashMap<>()))
                            .add(identity != null ? identity : wrapper)) {
                        wrappers.put(entry.getKey(), wrapper);
                    }
                }
            }
            if (!wrappers.isEmpty()) {
                storages.add(new CompositeStorage(wrappers));
            }
        }
        return storages;
    }
}
