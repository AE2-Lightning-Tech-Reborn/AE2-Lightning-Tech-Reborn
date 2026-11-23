package com.moakiee.ae2lt.client.machine;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.moakiee.ae2lt.client.ldlib.LdlibToolbarButton;

import com.moakiee.ae2lt.logic.compute.ComputeTier;
import com.moakiee.ae2lt.logic.craft.MatrixCoreMode;
import com.moakiee.ae2lt.logic.craft.MatrixCraftingMath;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.logic.craft.MatrixMultiblockScanIssue;
import com.moakiee.ae2lt.menu.MatrixControllerMenu;
import com.moakiee.ae2lt.network.MatrixControllerActionPacket;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class MatrixControllerScreen extends MultiblockControllerScreen<MatrixControllerMenu> {
    private static final IGuiTexture BUILD = LdlibToolbarButton.glyph("quick_build");
    private static final IGuiTexture UPGRADE = LdlibToolbarButton.glyph("pattern_storage_upgrade");

    private MatrixMigrationWidget migration;

    public MatrixControllerScreen(MatrixControllerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, "matrix_controller", 4);
    }

    @Override
    protected void addControls(UIElement root) {
        root.addChild(LdlibToolbarButton.toolbar(-18, 0, () -> BUILD,
                () -> sendAction(MatrixControllerActionPacket.Action.AUTO_BUILD),
                () -> List.of(Component.translatable("ae2lt.matrix.gui.build"))));
        root.addChild(LdlibToolbarButton.toolbar(-18, 22, () -> UPGRADE,
                () -> sendAction(MatrixControllerActionPacket.Action.UPGRADE_PATTERN_STORAGE),
                () -> List.of(Component.translatable("ae2lt.matrix.gui.upgrade"))));
        migration = new MatrixMigrationWidget(menu, 0, 0);
        root.addChild(migration.ldlibButton(-18, 44));
        text(root, () -> {
            var summary = migration.summary();
            return summary == null ? null : summary.getString();
        }, 16, 206, 154, false);
    }

    private void sendAction(MatrixControllerActionPacket.Action action) {
        PacketDistributor.sendToServer(
                new MatrixControllerActionPacket(menu.token(), menu.getBlockPos(), action));
    }

    @Override
    protected View view() {
        var mode = menu.getMode();
        var tier = switch (mode) {
            case STABLE -> ComputeTier.BASELINE;
            case QUANTUM -> ComputeTier.QUANTUM;
            case OVERLOAD -> ComputeTier.OVERLOAD;
            case MULTIDIMENSIONAL -> ComputeTier.MULTIDIMENSIONAL;
            default -> null;
        };
        var core = switch (mode) {
            case STABLE -> ModBlocks.MATTER_WARPING_MATRIX_STABLE_MAIN_CORE.get();
            case QUANTUM -> ModBlocks.MATTER_WARPING_MATRIX_QUANTUM_MAIN_CORE.get();
            case OVERLOAD -> ModBlocks.MATTER_WARPING_MATRIX_OVERLOAD_MAIN_CORE.get();
            case MULTIDIMENSIONAL -> ModBlocks.MATTER_WARPING_MATRIX_MULTIDIMENSIONAL_MAIN_CORE.get();
            default -> null;
        };
        boolean formed = menu.isFormed();
        double heat = menu.getNormalizedHeat();
        boolean overload = mode == MatrixCoreMode.OVERLOAD;
        boolean multidimensional = mode == MatrixCoreMode.MULTIDIMENSIONAL;
        int heatColor = multidimensional ? BLUE : overload
                ? (heat < 0.42 ? BLUE : heat > 0.58 ? RED : GREEN)
                : (heat < 0.35 ? GREEN : heat < 0.70 ? AMBER : RED);
        boolean warning = !multidimensional && (heatColor == RED || heatColor == AMBER || overload && heatColor == BLUE);
        if (!formed || tier == null) {
            return new View(tier, core, modeName(), formed, warning, issueText(), List.of(), null, null, 195);
        }
        String heatState = switch (heatColor) {
            case BLUE -> "heat_cold";
            case RED -> "heat_hot";
            case AMBER -> "heat_warm";
            default -> overload ? "heat_sweet" : "heat_good";
        };
        String heatValue = multidimensional ? I18n.get("ae2lt.matrix.gui.heat_ignored")
                : percent(heat) + " · " + I18n.get("ae2lt.matrix.gui." + heatState);
        double efficiency = menu.getEfficiencyFactor();
        long cap = switch (tier) {
            case BASELINE -> MatrixCraftingMath.STABLE_OPERATION_CAP;
            case QUANTUM -> MatrixCraftingMath.QUANTUM_OPERATION_CAP;
            default -> tier.copyCap();
        };
        String throughput = multidimensional ? "∞ op/t"
                : formatCount(menu.getOperationsPerTick()) + " / " + formatCount(cap) + " op/t";
        var metrics = List.of(
                new Metric("ae2lt.matrix.gui.label_heat", heatValue, multidimensional ? 0 : heat, heatColor),
                new Metric("ae2lt.matrix.gui.label_efficiency", percent(efficiency), efficiency, GREEN),
                new Metric("ae2lt.matrix.gui.label_throughput", throughput,
                        multidimensional ? Double.POSITIVE_INFINITY : (double) menu.getOperationsPerTick() / cap, BLUE));
        String patterns = I18n.get("ae2lt.matrix.gui.label_patterns") + ": "
                + I18n.get("ae2lt.matrix.gui.value_patterns",
                        formatCount(menu.getPatternStorageCount()), formatCount(menu.getPatternSlotCount()));
        return new View(tier, core, modeName(), true, warning, null, metrics,
                overload ? new double[] {0.42, 0.58} : null, patterns, 195);
    }

    private Component modeName() {
        return Component.translatable("ae2lt.matrix.mode." + menu.getMode().name().toLowerCase(Locale.ROOT));
    }

    private Component issueText() {
        int ordinal = menu.getIssue();
        var values = MatrixMultiblockScanIssue.values();
        String name = ordinal >= 0 && ordinal < values.length
                ? values[ordinal].name().toLowerCase(Locale.ROOT)
                : "unknown";
        return Component.translatable("ae2lt.matrix.issue." + name);
    }
}
