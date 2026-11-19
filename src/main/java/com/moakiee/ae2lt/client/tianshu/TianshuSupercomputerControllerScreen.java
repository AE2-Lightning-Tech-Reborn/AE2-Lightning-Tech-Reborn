package com.moakiee.ae2lt.client.tianshu;

import com.moakiee.ae2lt.client.machine.MultiblockControllerScreen;
import com.moakiee.ae2lt.client.widgets.TextureToggleButton;

import com.moakiee.ae2lt.logic.tianshu.CpuInternalCoreCalculator;
import com.moakiee.ae2lt.logic.tianshu.CpuMainCoreTier;
import com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockScanIssue;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.menu.TianshuSupercomputerControllerMenu;
import com.moakiee.ae2lt.network.TianshuControllerActionPacket;

import java.util.EnumMap;
import java.util.Locale;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import appeng.client.gui.Icon;
import appeng.client.gui.widgets.TabButton;
import appeng.core.network.serverbound.SwitchGuisPacket;
import appeng.menu.implementations.PriorityMenu;

public class TianshuSupercomputerControllerScreen
        extends MultiblockControllerScreen<TianshuSupercomputerControllerMenu> {

    private static final EnumMap<CpuMainCoreTier, Long> STORAGE_LIMITS = new EnumMap<>(CpuMainCoreTier.class);

    public TianshuSupercomputerControllerScreen(
            TianshuSupercomputerControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "tianshu_supercomputer_controller", 22);
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos - 18;
        int y = topPos;

        var build = new TextureToggleButton(
                TextureToggleButton.ButtonType.QUICK_BUILD,
                state -> sendAction(TianshuControllerActionPacket.Action.AUTO_BUILD));
        build.setTooltip(Tooltip.create(Component.translatable("ae2lt.tianshu.gui.build")));
        build.setPosition(x, y);
        addRenderableWidget(build);

        var selection = new TextureToggleButton(
                TextureToggleButton.ButtonType.CPU_SELECTION,
                state -> sendAction(TianshuControllerActionPacket.Action.OPEN_ALGORITHM_SELECTION));
        selection.setTooltip(Tooltip.create(Component.translatable(
                "ae2lt.tianshu.gui.algorithm_selection")));
        selection.setPosition(x, y + 22);
        addRenderableWidget(selection);

        var cpuPriorityLabel = Component.translatable("ae2lt.tianshu.gui.cpu_priority");
        var priority = new TabButton(
                Icon.PRIORITY,
                cpuPriorityLabel,
                ignored -> PacketDistributor.sendToServer(
                        SwitchGuisPacket.openSubMenu(PriorityMenu.TYPE)));
        priority.setTooltip(Tooltip.create(cpuPriorityLabel));
        priority.setSize(20, 20);
        priority.setPosition(leftPos + imageWidth, topPos);
        addRenderableWidget(priority);
    }

    private void sendAction(TianshuControllerActionPacket.Action action) {
        PacketDistributor.sendToServer(
                new TianshuControllerActionPacket(menu.token(), menu.getBlockPos(), action));
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        var tier = menu.getTier();
        var core = switch (tier) {
            case null -> null;
            case BASELINE -> ModBlocks.BASELINE_SUPERCOMPUTING_UNIT.get();
            case QUANTUM -> ModBlocks.QUANTUM_SUPERCOMPUTING_UNIT.get();
            case OVERLOAD -> ModBlocks.OVERLOAD_SUPERCOMPUTING_UNIT.get();
            case MULTIDIMENSIONAL -> ModBlocks.MULTIDIMENSIONAL_SUPERCOMPUTING_UNIT.get();
        };
        boolean capped = menu.isCapped();
        drawHeader(graphics, tier == null ? null : tier.computeTier(), core, tierName(), menu.isFormed(), capped);
        if (!menu.isFormed() || tier == null) {
            drawUnformed(graphics, issueText());
            return;
        }
        var limits = tier.computeTier();
        long storage = menu.getStorageBytes();
        long maxStorage = STORAGE_LIMITS.computeIfAbsent(tier, CpuInternalCoreCalculator::maximumStorage);
        metric(graphics, 0, "ae2lt.tianshu.gui.label_storage",
                limits.multidimensional() ? "∞" : formatStorage(storage) + " / " + formatStorage(maxStorage),
                limits.multidimensional() ? Double.POSITIVE_INFINITY : (double) storage / maxStorage, BLUE);
        int dispatches = menu.getSuccessfulDispatchesPerTick();
        metric(graphics, 1, "ae2lt.tianshu.gui.label_dispatches",
                formatCount(dispatches) + " / " + formatCount(limits.dispatchCap()) + "/t" + (capped ? I18n.get("ae2lt.tianshu.gui.capped") : ""),
                (double) dispatches / limits.dispatchCap(), capped ? AMBER : GREEN);
        long copies = menu.getMaxCopiesPerTick();
        metric(graphics, 2, "ae2lt.tianshu.gui.label_batch",
                limits.multidimensional() ? "∞/t" : formatCount(copies) + " / " + formatCount(limits.copyCap()) + "/t",
                limits.multidimensional() ? Double.POSITIVE_INFINITY : (double) copies / limits.copyCap(), BLUE);
        text(graphics, I18n.get("ae2lt.tianshu.gui.label_loop") + ": " + I18n.get("ae2lt.tianshu.gui.value_loop",
                formatCount(menu.getClosedLoopPatternStorages()), formatCount(menu.getClosedLoopSeedStorages())), 16, 199, 154);
    }

    private Component issueText() {
        int ordinal = menu.getIssue();
        var values = TianshuMultiblockScanIssue.values();
        String name = ordinal >= 0 && ordinal < values.length
                ? values[ordinal].name().toLowerCase(Locale.ROOT) : "unknown";
        return Component.translatable("ae2lt.tianshu.issue." + name);
    }

    private Component tierName() {
        var tier = menu.getTier();
        return tier == null ? Component.literal("—")
                : Component.translatable("ae2lt.tianshu.tier." + tier.name().toLowerCase(Locale.ROOT));
    }

    private static String formatStorage(long bytes) {
        if (bytes == Long.MAX_VALUE) return "∞";
        if (bytes >= 1024L * 1024L * 1024L) return String.format(Locale.ROOT, "%.2f GiB", bytes / (1024.0 * 1024 * 1024));
        return String.format(Locale.ROOT, "%.0f MiB", bytes / (1024.0 * 1024));
    }

}
