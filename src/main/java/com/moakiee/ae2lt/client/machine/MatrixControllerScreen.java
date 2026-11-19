package com.moakiee.ae2lt.client.machine;

import com.moakiee.ae2lt.client.widgets.TextureToggleButton;

import com.moakiee.ae2lt.logic.compute.ComputeTier;
import com.moakiee.ae2lt.logic.craft.MatrixCoreMode;
import com.moakiee.ae2lt.logic.craft.MatrixCraftingMath;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.logic.craft.MatrixMultiblockScanIssue;
import com.moakiee.ae2lt.menu.MatrixControllerMenu;
import com.moakiee.ae2lt.network.MatrixControllerActionPacket;

import java.util.Locale;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class MatrixControllerScreen extends MultiblockControllerScreen<MatrixControllerMenu> {
    private MatrixMigrationWidget migration;

    public MatrixControllerScreen(MatrixControllerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, "matrix_controller", 4);
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos - 18;
        int y = topPos;

        var build = new TextureToggleButton(
                TextureToggleButton.ButtonType.QUICK_BUILD,
                state -> sendAction(MatrixControllerActionPacket.Action.AUTO_BUILD));
        build.setTooltip(Tooltip.create(Component.translatable("ae2lt.matrix.gui.build")));
        build.setPosition(x, y);
        addRenderableWidget(build);

        var upgrade = new TextureToggleButton(
                TextureToggleButton.ButtonType.PATTERN_STORAGE_UPGRADE,
                state -> sendAction(MatrixControllerActionPacket.Action.UPGRADE_PATTERN_STORAGE));
        upgrade.setTooltip(Tooltip.create(Component.translatable("ae2lt.matrix.gui.upgrade")));
        upgrade.setPosition(x, y + 22);
        addRenderableWidget(upgrade);
        migration = new MatrixMigrationWidget(menu, x, y + 44);
        addRenderableWidget(migration.button);
    }

    @Override protected void containerTick() {
        super.containerTick();
        if (migration != null) migration.update();
    }

    private void sendAction(MatrixControllerActionPacket.Action action) {
        PacketDistributor.sendToServer(
                new MatrixControllerActionPacket(menu.token(), menu.getBlockPos(), action));
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
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
        drawHeader(graphics, tier, core, modeName(), formed, warning);
        if (!formed || tier == null) {
            drawUnformed(graphics, issueText());
        } else {
            String heatState = switch (heatColor) {
                case BLUE -> "heat_cold";
                case RED -> "heat_hot";
                case AMBER -> "heat_warm";
                default -> overload ? "heat_sweet" : "heat_good";
            };
            String heatValue = multidimensional ? I18n.get("ae2lt.matrix.gui.heat_ignored")
                    : percent(heat) + " · " + I18n.get("ae2lt.matrix.gui." + heatState);
            metric(graphics, 0, "ae2lt.matrix.gui.label_heat", heatValue, multidimensional ? 0 : heat, heatColor);
            if (overload) markHeatRange(graphics, 0.42, 0.58);
            double efficiency = menu.getEfficiencyFactor();
            metric(graphics, 1, "ae2lt.matrix.gui.label_efficiency", percent(efficiency), efficiency, GREEN);
            long cap = switch (tier) {
                case BASELINE -> MatrixCraftingMath.STABLE_OPERATION_CAP;
                case QUANTUM -> MatrixCraftingMath.QUANTUM_OPERATION_CAP;
                default -> tier.copyCap();
            };
            String throughput = multidimensional ? "∞ op/t" : formatCount(menu.getOperationsPerTick()) + " / " + formatCount(cap) + " op/t";
            metric(graphics, 2, "ae2lt.matrix.gui.label_throughput", throughput,
                    multidimensional ? Double.POSITIVE_INFINITY : (double) menu.getOperationsPerTick() / cap, BLUE);
            text(graphics, I18n.get("ae2lt.matrix.gui.label_patterns") + ": " + I18n.get("ae2lt.matrix.gui.value_patterns",
                    formatCount(menu.getPatternStorageCount()), formatCount(menu.getPatternSlotCount())), 16, 195, 154);
        }
        if (migration != null) migration.render(graphics, font, 16, 206, 154, textColor());
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
