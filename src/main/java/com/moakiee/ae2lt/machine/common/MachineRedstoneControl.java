package com.moakiee.ae2lt.machine.common;

import java.util.function.BooleanSupplier;

import appeng.api.config.RedstoneMode;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.core.definitions.AEItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ItemLike;

/** Level-sensitive recipe gate. Disabling it never cancels an in-flight recipe. */
public final class MachineRedstoneControl {
    private static final String MODE_TAG = "RedstoneMode";
    private static final String UPGRADES_TAG = "Upgrades";
    private static final RedstoneMode[] MODES = {
            RedstoneMode.IGNORE, RedstoneMode.HIGH_SIGNAL, RedstoneMode.LOW_SIGNAL
    };

    private final IUpgradeInventory upgrades;
    private final BooleanSupplier powered;
    private final Runnable changed;
    private RedstoneMode mode = RedstoneMode.IGNORE;

    public MachineRedstoneControl(ItemLike machine, int slots, BooleanSupplier powered, Runnable changed) {
        this.powered = powered;
        this.changed = changed;
        this.upgrades = UpgradeInventories.forMachine(machine, slots, changed::run);
    }

    public IUpgradeInventory getUpgrades() {
        return upgrades;
    }

    public boolean hasCard() {
        return upgrades.getInstalledUpgrades(AEItems.REDSTONE_CARD) > 0;
    }

    public RedstoneMode getMode() {
        return mode;
    }

    public void setMode(RedstoneMode mode) {
        if (mode == null || mode == RedstoneMode.SIGNAL_PULSE || mode == this.mode) return;
        this.mode = mode;
        changed.run();
    }

    public void cycleMode(boolean backwards) {
        for (int i = 0; i < MODES.length; i++) {
            if (MODES[i] == mode) {
                setMode(MODES[Math.floorMod(i + (backwards ? -1 : 1), MODES.length)]);
                return;
            }
        }
    }

    public boolean isEnabled() {
        return !hasCard() || mode == RedstoneMode.IGNORE
                || powered.getAsBoolean() == (mode == RedstoneMode.HIGH_SIGNAL);
    }

    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        upgrades.writeToNBT(tag, UPGRADES_TAG, registries);
        writeSettings(tag);
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        // AE2 overlays saved slots; clear first so loading an old/empty save removes stale cards.
        upgrades.clear();
        upgrades.readFromNBT(tag, UPGRADES_TAG, registries);
        mode = RedstoneMode.IGNORE;
        readSettings(tag);
    }

    public void writeSettings(CompoundTag tag) {
        tag.putString(MODE_TAG, mode.name());
    }

    public void readSettings(CompoundTag tag) {
        if (!tag.contains(MODE_TAG)) return;
        try {
            setMode(RedstoneMode.valueOf(tag.getString(MODE_TAG)));
        } catch (IllegalArgumentException ignored) {
            setMode(RedstoneMode.IGNORE);
        }
    }
}
