package com.moakiee.ae2lt.client.tianshu;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.moakiee.ae2lt.client.ldlib.LdlibToolbarButton;
import com.moakiee.ae2lt.client.machine.MultiblockControllerScreen;

import com.moakiee.ae2lt.logic.tianshu.CpuInternalCoreCalculator;
import com.moakiee.ae2lt.logic.tianshu.CpuMainCoreTier;
import com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockScanIssue;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.menu.TianshuSupercomputerControllerMenu;
import com.moakiee.ae2lt.network.TianshuControllerActionPacket;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import appeng.client.gui.Icon;
import appeng.core.network.serverbound.SwitchGuisPacket;
import appeng.menu.implementations.PriorityMenu;

public class TianshuSupercomputerControllerScreen
        extends MultiblockControllerScreen<TianshuSupercomputerControllerMenu> {

    private static final IGuiTexture BUILD = LdlibToolbarButton.glyph("quick_build");
    private static final IGuiTexture SELECTION = LdlibToolbarButton.glyph("algorithm_settings");
    private static final EnumMap<CpuMainCoreTier, Long> STORAGE_LIMITS = new EnumMap<>(CpuMainCoreTier.class);

    public TianshuSupercomputerControllerScreen(
            TianshuSupercomputerControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "tianshu_supercomputer_controller", 22);
    }

    @Override
    protected void addControls(UIElement root) {
        root.addChild(LdlibToolbarButton.toolbar(-18, 0, () -> BUILD,
                () -> sendAction(TianshuControllerActionPacket.Action.AUTO_BUILD),
                () -> List.of(Component.translatable("ae2lt.tianshu.gui.build"))));
        root.addChild(LdlibToolbarButton.toolbar(-18, 22, () -> SELECTION,
                () -> sendAction(TianshuControllerActionPacket.Action.OPEN_ALGORITHM_SELECTION),
                () -> List.of(Component.translatable("ae2lt.tianshu.gui.algorithm_selection"))));
        root.addChild(LdlibToolbarButton.tab(imageWidth, 0, Icon.PRIORITY,
                () -> PacketDistributor.sendToServer(SwitchGuisPacket.openSubMenu(PriorityMenu.TYPE)),
                Component.translatable("ae2lt.tianshu.gui.cpu_priority")));
    }

    private void sendAction(TianshuControllerActionPacket.Action action) {
        PacketDistributor.sendToServer(
                new TianshuControllerActionPacket(menu.token(), menu.getBlockPos(), action));
    }

    @Override
    protected View view() {
        var tier = menu.getTier();
        var core = switch (tier) {
            case null -> null;
            case BASELINE -> ModBlocks.BASELINE_SUPERCOMPUTING_UNIT.get();
            case QUANTUM -> ModBlocks.QUANTUM_SUPERCOMPUTING_UNIT.get();
            case OVERLOAD -> ModBlocks.OVERLOAD_SUPERCOMPUTING_UNIT.get();
            case MULTIDIMENSIONAL -> ModBlocks.MULTIDIMENSIONAL_SUPERCOMPUTING_UNIT.get();
        };
        boolean capped = menu.isCapped();
        var computeTier = tier == null ? null : tier.computeTier();
        if (!menu.isFormed() || tier == null) {
            return new View(computeTier, core, tierName(), menu.isFormed(), capped, issueText(),
                    List.of(), null, null, 195);
        }
        var limits = tier.computeTier();
        long storage = menu.getStorageBytes();
        long maxStorage = STORAGE_LIMITS.computeIfAbsent(tier, CpuInternalCoreCalculator::maximumStorage);
        int dispatches = menu.getSuccessfulDispatchesPerTick();
        long copies = menu.getMaxCopiesPerTick();
        var metrics = List.of(
                new Metric("ae2lt.tianshu.gui.label_storage",
                        limits.multidimensional() ? "∞" : formatStorage(storage) + " / " + formatStorage(maxStorage),
                        limits.multidimensional() ? Double.POSITIVE_INFINITY : (double) storage / maxStorage, BLUE),
                new Metric("ae2lt.tianshu.gui.label_dispatches",
                        formatCount(dispatches) + " / " + formatCount(limits.dispatchCap()) + "/t"
                                + (capped ? I18n.get("ae2lt.tianshu.gui.capped") : ""),
                        (double) dispatches / limits.dispatchCap(), capped ? AMBER : GREEN),
                new Metric("ae2lt.tianshu.gui.label_batch",
                        limits.multidimensional() ? "∞/t"
                                : formatCount(copies) + " / " + formatCount(limits.copyCap()) + "/t",
                        limits.multidimensional() ? Double.POSITIVE_INFINITY : (double) copies / limits.copyCap(), BLUE));
        String loop = I18n.get("ae2lt.tianshu.gui.label_loop") + ": " + I18n.get("ae2lt.tianshu.gui.value_loop",
                formatCount(menu.getClosedLoopPatternStorages()), formatCount(menu.getClosedLoopSeedStorages()));
        return new View(computeTier, core, tierName(), true, capped, null, metrics, null, loop, 199);
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
