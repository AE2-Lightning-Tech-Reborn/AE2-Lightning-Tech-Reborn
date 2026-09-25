package com.moakiee.ae2lt.client;

import appeng.client.gui.Icon;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.Repo;
import appeng.client.gui.me.common.StackSizeRenderer;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ActionButton;
import appeng.client.gui.widgets.IconButton;
import appeng.client.gui.widgets.SettingToggleButton;
import appeng.client.gui.widgets.TabButton;
import appeng.client.gui.widgets.TabButton.Style;
import appeng.api.behaviors.ContainerItemStrategies;
import appeng.api.behaviors.EmptyingAction;
import appeng.api.config.ActionItems;
import appeng.api.config.Settings;
import appeng.api.config.ViewItems;
import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.GenericStack;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.Tooltips;
import appeng.core.definitions.AEItems;
import appeng.core.sync.packets.InventoryActionPacket;
import appeng.helpers.InventoryAction;
import appeng.menu.SlotSemantics;
import appeng.menu.me.common.GridInventoryEntry;
import appeng.menu.me.common.IClientRepo;
import appeng.parts.encoding.EncodingMode;
import appeng.api.stacks.AEItemKey;
import appeng.util.prioritylist.IPartitionList;
import com.moakiee.ae2lt.logic.tianshu.terminal.ProcessingPatternEncodingType;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuEncodingMode;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuPatternUploadRouting;
import com.moakiee.ae2lt.item.ClosedLoopPatternItem;
import com.moakiee.ae2lt.menu.Ae2ltSlotSemantics;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.moakiee.ae2lt.mixin.client.AEBaseScreenAccessor;
import com.moakiee.ae2lt.mixin.client.VerticalButtonBarAccessor;
import com.moakiee.ae2lt.registry.ModItems;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import appeng.client.gui.me.common.RepoSlot;
import org.lwjgl.glfw.GLFW;
import com.moakiee.ae2lt.logic.tianshu.maintenance.InventoryMaintenanceBadge;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import com.moakiee.ae2lt.network.PacketSender;
import com.moakiee.ae2lt.logic.AdvancedAECompat;
import org.anti_ad.mc.ipn.api.IPNIgnore;
import org.jetbrains.annotations.Nullable;

import com.moakiee.ae2lt.menu.TianshuMaintenanceMenu;
import appeng.menu.me.common.MEStorageMenu;

/** ME inventory maintenance UI shared by crafting and pattern terminal screens. */
public abstract class TianshuMaintenanceTermScreen<M extends MEStorageMenu & TianshuMaintenanceMenu>
        extends MEStorageScreen<M> {
    private boolean awaitingMaintenanceEditor;
    private int requestedMaintenanceRevision;
    private int observedTianshuSelectionRevision = Integer.MIN_VALUE;
    private boolean observedMaintainableView;
    private long observedMaintenanceFilterRevision = Long.MIN_VALUE;
    private final Map<appeng.api.stacks.AEKey, Long> syntheticMaintenanceEntries = new HashMap<>();
    private long nextSyntheticMaintenanceSerial = -10_000_000L;
    private boolean syntheticMaintenanceDirty = true;
    private long syntheticMaintenanceRevision = Long.MIN_VALUE;

    protected TianshuMaintenanceTermScreen(M menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        // Only network inventory updates invalidate the synthetic-entry reconciliation.
        // Keep AE2's own Repo (and its scrollbar listener) as the screen's repository.
        menu.setClientRepo(new IClientRepo() {
            @Override
            public void handleUpdate(boolean fullUpdate, List<GridInventoryEntry> entries) {
                syntheticMaintenanceDirty = true;
                repo.handleUpdate(fullUpdate, entries);
            }

            @Override
            public Set<GridInventoryEntry> getAllEntries() {
                return repo.getAllEntries();
            }

            @Override
            public java.util.Collection<GridInventoryEntry> getByIngredient(
                    net.minecraft.world.item.crafting.Ingredient ingredient) {
                return repo.getByIngredient(ingredient);
            }
        });
        replaceViewModeButton();
        addToLeftToolbar(new MaintenanceOverviewButton());
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        if (observedTianshuSelectionRevision != menu.getTianshuSelectionRevision()) {
            observedTianshuSelectionRevision = menu.getTianshuSelectionRevision();
            awaitingMaintenanceEditor = false;
            removeSyntheticMaintenanceEntries();
            menu.resetClientTianshuScopedState();
        }
        syncSyntheticMaintenanceEntries();
        refreshMaintenancePartitionIfNeeded();
        if (awaitingMaintenanceEditor
                && menu.getMaintenanceEditorRevision() != requestedMaintenanceRevision
                && menu.getMaintenanceEditorData() != null) {
            awaitingMaintenanceEditor = false;
            switchToScreen(new TianshuMaintenanceRuleScreen<>(this, menu.getMaintenanceEditorData()));
            return;
        }
    }

    protected final boolean handleMaintenanceClick(int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && hasShiftDown()
                && getSlotUnderMouse() instanceof RepoSlot repoSlot) {
            if (!menu.isMaintenanceAvailable()) {
                if (minecraft.player != null) minecraft.player.displayClientMessage(
                        Component.translatable("ae2lt.tianshu.maintenance.unavailable"), true);
                return true;
            }
            var entry = repoSlot.getEntry();
            if (entry != null && entry.getWhat() != null) {
                var summary = menu.getMaintenanceSummaryEntry(entry.getWhat());
                if ((summary == null || !summary.ruleConfigured()) && !entry.isCraftable()) {
                    if (minecraft.player != null) minecraft.player.displayClientMessage(
                            Component.translatable("ae2lt.tianshu.maintenance.unsupported"), true);
                    return true;
                }
                requestMaintenanceEditorFor(entry.getWhat());
                return true;
            }
        }

        if (getSlotUnderMouse() instanceof RepoSlot repoSlot
                && isSyntheticMaintenanceEntry(repoSlot.getEntry())) {
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        return handleMaintenanceClick(button) || super.mouseClicked(x, y, button);
    }

    @Override
    public void renderSlot(GuiGraphics graphics, Slot slot) {
        super.renderSlot(graphics, slot);
        var repoEntry = slot instanceof RepoSlot repoSlot ? repoSlot.getEntry() : null;
        if (repoEntry == null) return;
        var summary = menu.getMaintenanceSummaryEntry(repoEntry.getWhat());
        if (summary == null || !summary.ruleConfigured()) return;
        int color = switch (InventoryMaintenanceBadge.from(summary.status())) {
            case GREEN -> 0xFF33CC44;
            case YELLOW -> 0xFFFFCC33;
            case RED -> 0xFFDD3333;
            case GRAY -> 0xFF888888;
        };
        graphics.fill(slot.x + 12, slot.y, slot.x + 16, slot.y + 4, color);
    }

    private TianshuViewModeButton replaceViewModeButton() {
        var toolbar = ((AEBaseScreenAccessor) this).ae2lt$getVerticalToolbar();
        var buttons = ((VerticalButtonBarAccessor) toolbar).ae2lt$getButtons();
        for (int i = 0; i < buttons.size(); i++) {
            if (buttons.get(i) instanceof SettingToggleButton<?> settingButton
                    && settingButton.getSetting() == Settings.VIEW_MODE) {
                var replacement = new TianshuViewModeButton();
                buttons.set(i, replacement);
                return replacement;
            }
        }
        throw new IllegalStateException("AE2 view-mode button is missing");
    }

    private void cycleViewMode(boolean reverse) {
        menu.setTerminalViewMode(menu.getTerminalViewMode().next(reverse));
        syncSyntheticMaintenanceEntries();
        refreshMaintenancePartitionIfNeeded();
        // Shift also freezes AE2's repository. Explicit filter changes must replace that
        // frozen view immediately, then keep normal inventory updates frozen as before.
        boolean paused = repo.isPaused();
        if (paused) repo.setPaused(false);
        else repo.updateView();
        repo.setPaused(paused);
    }

    @Override
    public ViewItems getSortDisplay() {
        return menu.getTerminalViewMode().viewItems();
    }

    @Override
    protected void renderGridInventoryEntryTooltip(
            GuiGraphics graphics, GridInventoryEntry entry, int x, int y) {
        var summary = entry != null ? menu.getMaintenanceSummaryEntry(entry.getWhat()) : null;
        if (summary == null || !summary.ruleConfigured()) {
            super.renderGridInventoryEntryTooltip(graphics, entry, x, y);
            return;
        }

        var lines = AEKeyRendering.getTooltip(entry.getWhat());
        if (Tooltips.shouldShowAmountTooltip(entry.getWhat(), summary.storedAmount())) {
            var precise = menu.getBigStock(entry.getWhat());
            lines.add(precise == null ? Tooltips.getAmountTooltip(
                    ButtonToolTips.StoredAmount, entry.getWhat(), summary.storedAmount()) :
                    ButtonToolTips.StoredAmount.text(com.moakiee.thunderbolt.ae2.crafting.ExactAmountFormatter.full(precise, entry.getWhat().getAmountPerUnit())));
        }
        lines.add(Component.translatable("ae2lt.tianshu.maintenance.tooltip.thresholds",
                summary.lowerThreshold(), summary.upperThreshold())
                .withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable("ae2lt.tianshu.maintenance.tooltip.batch",
                summary.amountPerJob()).withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable("ae2lt.tianshu.maintenance.status."
                + summary.status().name().toLowerCase(java.util.Locale.ROOT))
                .withStyle(statusFormatting(summary.status())));
        if (summary.globalReserve() != 0L) {
            lines.add(Component.translatable("ae2lt.tianshu.maintenance.tooltip.reserve",
                    formatReserve(summary.globalReserve()),
                    Component.translatable(summary.globalMode()
                            == com.moakiee.ae2lt.logic.tianshu.maintenance.ReservedStockMatchMode.EXACT
                                    ? "ae2lt.tianshu.reserve.exact"
                                    : "ae2lt.tianshu.reserve.ignore_nbt"))
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
        lines.add(Component.translatable("ae2lt.tianshu.maintenance.tooltip.edit")
                .withStyle(ChatFormatting.GRAY));

        if (entry.getWhat() instanceof AEItemKey itemKey) {
            var stack = itemKey.getReadOnlyStack();
            graphics.renderTooltip(font, lines, stack.getTooltipImage(), stack, x, y);
        } else {
            graphics.renderComponentTooltip(font, lines, x, y);
        }
    }

    public void requestMaintenanceEditorFor(appeng.api.stacks.AEKey key) {
        if (key == null) return;
        requestedMaintenanceRevision = menu.getMaintenanceEditorRevision();
        awaitingMaintenanceEditor = true;
        menu.requestMaintenanceEditor(key);
    }

    List<GridInventoryEntry> getNetworkEntriesForMaintenance() {
        return repo.getAllEntries().stream()
                .filter(entry -> entry.getWhat() != null && !isSyntheticMaintenanceEntry(entry))
                .toList();
    }

    private void syncSyntheticMaintenanceEntries() {
        if (!menu.isMaintainableView()) {
            removeSyntheticMaintenanceEntries();
            syntheticMaintenanceDirty = true;
            return;
        }

        long revision = menu.getMaintenanceSummaryRevision();
        if (!syntheticMaintenanceDirty && syntheticMaintenanceRevision == revision) return;
        syntheticMaintenanceDirty = false;
        syntheticMaintenanceRevision = revision;

        var repoEntries = List.copyOf(repo.getAllEntries());
        var presentEntries = new HashMap<Long, GridInventoryEntry>();
        var updates = new ArrayList<GridInventoryEntry>();
        var realKeys = new HashSet<appeng.api.stacks.AEKey>();
        var knownSyntheticSerials = new HashSet<>(syntheticMaintenanceEntries.values());
        for (var entry : repoEntries) {
            presentEntries.put(entry.getSerial(), entry);
            if (!knownSyntheticSerials.contains(entry.getSerial()) && entry.getWhat() != null) {
                realKeys.add(entry.getWhat());
            }
        }

        var summaries = menu.getMaintenanceSummary();
        for (var iterator = syntheticMaintenanceEntries.entrySet().iterator(); iterator.hasNext();) {
            var synthetic = iterator.next();
            var summary = summaries.get(synthetic.getKey());
            if (summary == null || !summary.ruleConfigured() || realKeys.contains(synthetic.getKey())) {
                if (presentEntries.containsKey(synthetic.getValue())) {
                    updates.add(new GridInventoryEntry(synthetic.getValue(), null, 0L, 0L, false));
                }
                iterator.remove();
            }
        }

        for (var summary : summaries.values()) {
            if (!summary.ruleConfigured() || realKeys.contains(summary.key())) continue;
            long serial = syntheticMaintenanceEntries.computeIfAbsent(
                    summary.key(), ignored -> nextSyntheticMaintenanceSerial--);
            var previous = presentEntries.get(serial);
            long requestable = summary.craftable() ? 0L : 1L;
            if (previous == null || previous.getStoredAmount() != summary.storedAmount()
                    || previous.getRequestableAmount() != requestable
                    || previous.isCraftable() != summary.craftable()) {
                // requestable=1 keeps an unavailable zero-stock entry meaningful to AE2's Repo.
                updates.add(new GridInventoryEntry(
                        serial, summary.key(), summary.storedAmount(),
                        requestable, summary.craftable()));
            }
        }
        if (!updates.isEmpty()) repo.handleUpdate(false, updates);
    }

    private void refreshMaintenancePartitionIfNeeded() {
        long summaryRevision = menu.getMaintenanceSummaryRevision();
        if (observedMaintainableView == menu.isMaintainableView()
                && (!menu.isMaintainableView()
                        || observedMaintenanceFilterRevision == summaryRevision)) {
            return;
        }
        observedMaintainableView = menu.isMaintainableView();
        observedMaintenanceFilterRevision = summaryRevision;
        repo.setPartitionList(createPartitionList(menu.getViewCells()));
    }

    @Nullable
    @Override
    protected IPartitionList createPartitionList(List<ItemStack> viewCells) {
        // Filters the visible view without deleting entries from the shared item repository.
        var viewCellFilter = super.createPartitionList(viewCells);
        if (!menu.isMaintainableView()) return viewCellFilter;

        Set<appeng.api.stacks.AEKey> maintainedKeys = menu.getMaintenanceSummary().values().stream()
                .filter(entry -> entry.ruleConfigured())
                .map(entry -> entry.key())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new IPartitionList() {
            @Override
            public boolean isListed(appeng.api.stacks.AEKey key) {
                return maintainedKeys.contains(key)
                        && (viewCellFilter == null || viewCellFilter.isListed(key));
            }

            @Override
            public boolean isEmpty() {
                return maintainedKeys.isEmpty();
            }

            @Override
            public Iterable<appeng.api.stacks.AEKey> getItems() {
                return maintainedKeys;
            }
        };
    }

    private void removeSyntheticMaintenanceEntries() {
        if (syntheticMaintenanceEntries.isEmpty()) return;
        var removals = syntheticMaintenanceEntries.values().stream()
                .map(serial -> new GridInventoryEntry(serial, null, 0L, 0L, false))
                .toList();
        syntheticMaintenanceEntries.clear();
        repo.handleUpdate(false, removals);
    }

    private boolean isSyntheticMaintenanceEntry(GridInventoryEntry entry) {
        return entry != null && syntheticMaintenanceEntries.containsValue(entry.getSerial());
    }

    private static String formatReserve(long amount) {
        return amount < 0L ? "∞" : Long.toString(amount);
    }

    private static ChatFormatting statusFormatting(
            com.moakiee.ae2lt.logic.tianshu.maintenance.InventoryMaintenanceStatus status) {
        return switch (InventoryMaintenanceBadge.from(status)) {
            case GREEN -> ChatFormatting.GREEN;
            case YELLOW -> ChatFormatting.GOLD;
            case RED -> ChatFormatting.RED;
            case GRAY -> ChatFormatting.GRAY;
        };
    }

    private final class TianshuViewModeButton extends IconButton {
        private TianshuViewModeButton() {
            super(ignored -> cycleViewMode(hasShiftDown()));
        }

        @Override
        protected Icon getIcon() {
            if (menu.isMaintainableView()) return Icon.VIEW_MODE_CRAFTING;
            return switch (getSortDisplay()) {
                case ALL -> Icon.VIEW_MODE_ALL;
                case STORED -> Icon.VIEW_MODE_STORED;
                case CRAFTABLE -> Icon.VIEW_MODE_CRAFTING;
            };
        }

        @Override
        public java.util.List<Component> getTooltipMessage() {
            var value = menu.isMaintainableView()
                    ? Component.translatable("ae2lt.tianshu.maintenance.view")
                    : switch (getSortDisplay()) {
                        case ALL -> Component.translatable(ButtonToolTips.StoredCraftable.getTranslationKey());
                        case STORED -> Component.translatable(ButtonToolTips.StoredItems.getTranslationKey());
                        case CRAFTABLE -> Component.translatable(ButtonToolTips.Craftable.getTranslationKey());
                    };
            return java.util.List.of(
                    Component.translatable(ButtonToolTips.View.getTranslationKey()), value,
                    Component.translatable("ae2lt.tianshu.maintenance.view.modes")
                            .withStyle(ChatFormatting.GRAY));
        }
    }

    private final class MaintenanceOverviewButton extends TextureToggleButton {
        private MaintenanceOverviewButton() {
            super(ButtonType.INVENTORY_MAINTENANCE,
                    ignored -> switchToScreen(new TianshuGlobalReserveScreen<>(
                            TianshuMaintenanceTermScreen.this)));
            var label = Component.translatable("ae2lt.tianshu.maintenance.overview_button");
            setMessage(label);
            setTooltipAt(0, List.of(label));
        }
    }

    @Override
    protected void slotClicked(Slot slot, int slotIndex, int mouseButton, ClickType clickType) {
        if (slot instanceof RepoSlot repoSlot && isSyntheticMaintenanceEntry(repoSlot.getEntry())) {
            return;
        }
        super.slotClicked(slot, slotIndex, mouseButton, clickType);
    }
}
