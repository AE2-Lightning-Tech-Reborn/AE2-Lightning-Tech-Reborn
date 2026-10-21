package com.moakiee.ae2lt.item;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;

import com.moakiee.thunderbolt.core.storage.cell.IIndexedStorageCellItem;
import com.moakiee.thunderbolt.core.storage.cell.IndexedCellSummary;
import com.moakiee.thunderbolt.core.storage.cell.IndexedStorage;
import com.moakiee.thunderbolt.core.storage.cell.ByteTracker;

public final class InfiniteStorageCellItem extends AE2LTItem implements IIndexedStorageCellItem {

    private static final ResourceLocation STORAGE_TYPE =
            new ResourceLocation("ae2lt", "infinite_cell");

    private final long capacityLo;
    private final long capacityHi;
    private final int bytesPerType;
    private final int maxTypes;
    private final double idleDrain;

    public InfiniteStorageCellItem(Properties props,
                                   long capacityLo, long capacityHi,
                                   int bytesPerType, int maxTypes,
                                   double idleDrain) {
        super(props.stacksTo(1));
        this.capacityLo = capacityLo;
        this.capacityHi = capacityHi;
        this.bytesPerType = bytesPerType;
        this.maxTypes = maxTypes;
        this.idleDrain = idleDrain;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        var summary = readSummary(stack);
        if (summary == null) return;
        int types = summary.totalTypes();
        long bytes = summary.usedBytes();

        tooltipComponents.add(Component.translatable(
                "tooltip.ae2lt.infinite_cell.types", String.format("%,d", types))
                .withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable(
                "tooltip.ae2lt.infinite_cell.bytes", formatBytes(bytes))
                .withStyle(ChatFormatting.GRAY));
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1_000) return String.format("%,d B", bytes);
        if (bytes < 1_000_000) return String.format("%.1f KB", bytes / 1_000.0);
        if (bytes < 1_000_000_000) return String.format("%.1f MB", bytes / 1_000_000.0);
        if (bytes < 1_000_000_000_000L) return String.format("%.1f GB", bytes / 1_000_000_000.0);
        return String.format("%.1f TB", bytes / 1_000_000_000_000.0);
    }

    @Override public boolean supportsBigAmounts(ItemStack stack) { return true; }

    public long getCapacityLo() { return capacityLo; }
    public long getCapacityHi() { return capacityHi; }
    public int getBytesPerType() { return bytesPerType; }
    public int getMaxTypes() { return maxTypes; }
    public double getIdleDrain() { return idleDrain; }

    @Override
    public ResourceLocation storageType(ItemStack stack) {
        return STORAGE_TYPE;
    }

    @Override
    public String cellIdTag(ItemStack stack) {
        // Retain the original identity tag so old AE2LT versions can still read rolled-back worlds.
        return "ae2lt:cell_id";
    }

    @Override
    public ByteTracker createByteTracker(ItemStack stack, IndexedStorage storage) {
        var tracker = new ByteTracker(storage::getTotalTypes);
        tracker.configure(bytesPerType, maxTypes, capacityLo, capacityHi);
        return tracker;
    }

    @Override
    public double idleDrain(ItemStack stack) {
        return idleDrain;
    }

    @Override
    public boolean accepts(ItemStack stack, AEKey key, IActionSource source) {
        return true;
    }

    @Override
    public void writeSummary(ItemStack stack, IndexedCellSummary summary) {
        var tag = stack.getOrCreateTag();
        tag.putInt("ae2lt:types", summary.totalTypes());
        tag.putLong("ae2lt:bytes", summary.usedBytes());
    }

    /** Reads the Forge NBT summary without copying unrelated item data. */
    public static @Nullable IndexedCellSummary readSummary(ItemStack stack) {
        var tag = stack.getTag();
        if (tag == null || (!tag.contains("ae2lt:types") && !tag.contains("ae2lt:bytes"))) return null;
        return new IndexedCellSummary(tag.getInt("ae2lt:types"), tag.getLong("ae2lt:bytes"));
    }
}
