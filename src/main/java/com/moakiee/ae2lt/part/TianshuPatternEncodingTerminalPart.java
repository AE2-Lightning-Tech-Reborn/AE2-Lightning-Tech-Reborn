package com.moakiee.ae2lt.part;

import appeng.api.parts.IPartItem;
import appeng.parts.encoding.PatternEncodingTerminalPart;
import appeng.util.inv.AppEngInternalInventory;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.logic.tianshu.terminal.ClosedLoopTerminalDraft;
import com.moakiee.ae2lt.logic.tianshu.terminal.ProcessingPatternTerminalDraft;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuEncodingMode;
import com.moakiee.ae2lt.logic.tianshu.terminal.OmniversalPatternDraft;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuPatternTerminalHost;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

public final class TianshuPatternEncodingTerminalPart extends PatternEncodingTerminalPart
        implements TianshuPatternTerminalHost {
    private static final String TAG_MODE = "TianshuEncodingMode";
    private static final String TAG_CLOSED_LOOP_DRAFT = "ClosedLoopDraft";
    private static final String TAG_PROCESSING_DRAFT = "ProcessingDraft";
    private static final String TAG_OMNIVERSAL_DRAFT = "OmniversalDraft";
    private OmniversalPatternDraft omniversalDraft = OmniversalPatternDraft.empty();
    private TianshuEncodingMode tianshuMode = TianshuEncodingMode.CRAFTING;
    @Nullable private ClosedLoopTerminalDraft closedLoopDraft;
    @Nullable private ProcessingPatternTerminalDraft processingDraft;

    public TianshuPatternEncodingTerminalPart(IPartItem<?> partItem) {
        super(partItem);

        // This terminal sources blank patterns exclusively from ME storage. Keep AE2's inherited
        // physical slot at zero capacity so parent-menu integrations cannot pull the first 64
        // blanks out of the network. The menu stages one extracted blank directly in the encoded
        // result inventory for the duration of an encoding request.
        var blankPatternInventory = getLogic().getBlankPatternInv();
        if (blankPatternInventory instanceof AppEngInternalInventory inventory) {
            inventory.setMaxStackSize(0, 0);
        }
    }

    @Override
    public MenuType<?> getMenuType(Player player) {
        return TianshuPatternEncodingTermMenu.TYPE;
    }

    @Override
    public TianshuEncodingMode getTianshuEncodingMode() {
        return tianshuMode;
    }

    @Override
    public OmniversalPatternDraft getOmniversalPatternDraft() {
        return omniversalDraft;
    }

    @Override
    public void setOmniversalPatternDraft(OmniversalPatternDraft draft) {
        if (omniversalDraft.equals(draft)) return;
        omniversalDraft = draft;
        markForSave();
    }

    @Override
    public void setTianshuEncodingMode(TianshuEncodingMode mode) {
        if (mode != null && tianshuMode != mode) {
            tianshuMode = mode;
            markForSave();
        }
    }

    @Nullable
    @Override
    public ClosedLoopTerminalDraft getClosedLoopTerminalDraft() {
        return closedLoopDraft;
    }

    @Override
    public void setClosedLoopTerminalDraft(@Nullable ClosedLoopTerminalDraft draft) {
        if (ClosedLoopTerminalDraft.sameState(closedLoopDraft, draft)) return;
        closedLoopDraft = draft;
        markForSave();
    }

    @Nullable
    @Override
    public ProcessingPatternTerminalDraft getProcessingPatternTerminalDraft() {
        return processingDraft;
    }

    @Override
    public void setProcessingPatternTerminalDraft(
            @Nullable ProcessingPatternTerminalDraft draft) {
        if (ProcessingPatternTerminalDraft.sameState(processingDraft, draft)) return;
        processingDraft = draft;
        markForSave();
    }

    @Override
    public void readFromNBT(ValueInput data) {
        super.readFromNBT(data);
        omniversalDraft = OmniversalPatternDraft.read(data.read(TAG_OMNIVERSAL_DRAFT, CompoundTag.CODEC).orElseGet(CompoundTag::new), getLevel().registryAccess());
        try {
            tianshuMode = TianshuEncodingMode.valueOf(data.getStringOr(TAG_MODE, "CRAFTING"));
        } catch (IllegalArgumentException ignored) {
            tianshuMode = TianshuEncodingMode.CRAFTING;
        }
        closedLoopDraft = data.read(TAG_CLOSED_LOOP_DRAFT, CompoundTag.CODEC).isPresent()
                ? ClosedLoopTerminalDraft.read(data.read(TAG_CLOSED_LOOP_DRAFT, CompoundTag.CODEC).orElseThrow(), data.lookup())
                : null;
        processingDraft = data.read(TAG_PROCESSING_DRAFT, CompoundTag.CODEC).isPresent()
                ? ProcessingPatternTerminalDraft.read(
                        data.read(TAG_PROCESSING_DRAFT, CompoundTag.CODEC).orElseThrow(), data.lookup())
                : null;
    }

    @Override
    public void writeToNBT(ValueOutput data) {
        super.writeToNBT(data);
        if (omniversalDraft.isEmpty()) data.discard(TAG_OMNIVERSAL_DRAFT);
        else data.store(TAG_OMNIVERSAL_DRAFT, CompoundTag.CODEC, omniversalDraft.write(getLevel().registryAccess()));
        data.putString(TAG_MODE, tianshuMode.name());
        if (closedLoopDraft != null) {
            data.store(TAG_CLOSED_LOOP_DRAFT, CompoundTag.CODEC, closedLoopDraft.write(getLevel().registryAccess()));
        } else {
            data.discard(TAG_CLOSED_LOOP_DRAFT);
        }
        if (processingDraft != null) {
            data.store(TAG_PROCESSING_DRAFT, CompoundTag.CODEC, processingDraft.write(getLevel().registryAccess()));
        } else {
            data.discard(TAG_PROCESSING_DRAFT);
        }
    }
}
