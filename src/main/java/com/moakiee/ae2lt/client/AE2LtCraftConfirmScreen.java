package com.moakiee.ae2lt.client;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.NumberFormat;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.StackWithBounds;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.Scrollbar;
import appeng.core.localization.GuiText;
import appeng.menu.me.crafting.CraftConfirmMenu;
import appeng.menu.me.crafting.CraftingPlanSummary;
import appeng.util.ReadableNumberConverter;

import com.moakiee.ae2lt.crafting.report.CraftingReportMenuState;
import com.moakiee.thunderbolt.core.crafting.algorithm.menu.CraftingAlgorithmNameMenu;

public final class AE2LtCraftConfirmScreen extends AEBaseScreen<CraftConfirmMenu> {
    private static final int TEXT_COLOR = 0x403E53;
    private static final long TERA_BYTE = 1_000_000_000_000L;
    private static final MathContext TIME_PRECISION = new MathContext(4, RoundingMode.HALF_UP);
    private static final int INFO_X = 11;
    private static final int INFO_Y = 202;
    private static final int INFO_WIDTH = 114;
    private static final int INFO_MAX_LINES = 4;

    private final AE2LtCraftConfirmTableRenderer table;
    private final Button start;
    private final Button selectCpu;
    private final Scrollbar scrollbar;
    private Component reportInfo = Component.empty();

    public AE2LtCraftConfirmScreen(
            CraftConfirmMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
        table = new AE2LtCraftConfirmTableRenderer(this);
        scrollbar = widgets.addScrollBar("scrollbar", Scrollbar.BIG);
        start = widgets.addButton("start", GuiText.Start.text(), menu::startJob);
        start.active = false;
        selectCpu = widgets.addButton("selectCpu", getNextCpuButtonLabel(), this::selectNextCpu);
        selectCpu.active = false;
        widgets.addButton("cancel", GuiText.Cancel.text(), menu::goBack);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        var errorResult = menu.submitError.result();
        if (errorResult != null && errorResult.errorCode() != null) {
            reportInfo = Component.translatable(
                    "gui.ae2lt.crafting_report.submit_error", errorResult.errorCode().name());
        }

        CraftingPlanSummary plan = menu.getPlan();
        boolean startable = plan != null && !plan.isSimulation();
        start.active = !menu.hasNoCPU() && startable;
        selectCpu.active = startable;
        selectCpu.setMessage(getNextCpuButtonLabel());

        long calculationNanos = menu instanceof CraftingReportMenuState state
                ? state.ae2lt$getCalculationNanos() : 0L;
        setTextContent(TEXT_ID_DIALOG_TITLE, Component.empty());
        setTextContent("calculation_time", plan == null
                ? Component.literal("...")
                : Component.literal(formatDuration(calculationNanos)));
        setTextContent("bytes_used", plan == null
                ? Component.literal("-")
                : Component.literal(formatBytes(plan.getUsedBytes())));

        if (errorResult == null || errorResult.errorCode() == null) {
            reportInfo = buildReportInfo(plan);
        }
        int size = plan == null ? 0 : plan.getEntries().size();
        scrollbar.setRange(0, table.getScrollableRows(size), 1);
    }

    private Component buildReportInfo(@Nullable CraftingPlanSummary plan) {
        if (plan == null) {
            return Component.translatable("gui.ae2lt.crafting_report.calculating");
        }
        if (plan.isSimulation()) {
            long missingTypes = plan.getEntries().stream()
                    .filter(entry -> entry.getMissingAmount() > 0)
                    .count();
            return Component.translatable(
                    "gui.ae2lt.crafting_report.missing", missingTypes);
        }
        if (menu.hasNoCPU()) {
            return Component.translatable("gui.ae2lt.crafting_report.no_cpu");
        }
        Component algorithm = menu instanceof CraftingAlgorithmNameMenu named
                ? named.thunderbolt$getCraftingAlgorithmName() : Component.empty();
        if (!algorithm.getString().isEmpty()) {
            return Component.translatable("gui.ae2lt.crafting_report.ready_with_algorithm", algorithm);
        }
        return Component.translatable("gui.ae2lt.crafting_report.ready");
    }

    private static String formatBytes(long bytes) {
        if (bytes >= TERA_BYTE) {
            return ReadableNumberConverter.format(bytes, 4) + " B";
        }
        return NumberFormat.getIntegerInstance().format(bytes) + " B";
    }

    private static String formatDuration(long nanos) {
        if (nanos < 1_000_000L) {
            return formatDecimal(BigDecimal.valueOf(nanos, 3)) + " us";
        }
        if (nanos < 1_000_000_000L) {
            return formatDecimal(BigDecimal.valueOf(nanos, 6)) + " ms";
        }
        return formatDecimal(BigDecimal.valueOf(nanos, 9)) + " s";
    }

    private static String formatDecimal(BigDecimal value) {
        BigDecimal rounded = value.round(TIME_PRECISION);
        int integerDigits = rounded.precision() - rounded.scale();
        int scale = Math.max(0, TIME_PRECISION.getPrecision() - integerDigits);
        return rounded.setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private Component getNextCpuButtonLabel() {
        if (menu.hasNoCPU()) {
            return GuiText.NoCraftingCPUs.text();
        }
        Component cpuName = menu.cpuName == null ? GuiText.Automatic.text() : menu.cpuName;
        return GuiText.SelectedCraftingCPU.text(cpuName);
    }

    private void selectNextCpu() {
        menu.cycleSelectedCPU(!isHandlingRightClick());
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        CraftingPlanSummary plan = menu.getPlan();
        if (plan != null) {
            table.render(graphics, mouseX, mouseY, plan.getEntries(), scrollbar.getCurrentScroll());
        }
        var lines = font.split(reportInfo, INFO_WIDTH);
        int lineCount = Math.min(INFO_MAX_LINES, lines.size());
        for (int i = 0; i < lineCount; i++) {
            FormattedCharSequence line = lines.get(i);
            graphics.drawString(font, line, INFO_X, INFO_Y + i * 11, TEXT_COLOR, false);
        }
    }

    @Override
    public @Nullable StackWithBounds getStackUnderMouse(double mouseX, double mouseY) {
        StackWithBounds hovered = table.getHoveredStack();
        return hovered != null ? hovered : super.getStackUnderMouse(mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (start.active) {
                menu.startJob();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
