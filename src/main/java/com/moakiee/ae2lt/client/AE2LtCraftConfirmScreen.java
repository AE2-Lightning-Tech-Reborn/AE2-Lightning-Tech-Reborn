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
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.StackWithBounds;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.Scrollbar;
import appeng.core.localization.GuiText;
import appeng.menu.me.crafting.CraftConfirmMenu;
import appeng.menu.me.crafting.CraftingPlanSummary;

import com.moakiee.ae2lt.crafting.report.CraftingReportMenuState;
public final class AE2LtCraftConfirmScreen extends AEBaseScreen<CraftConfirmMenu> {
    private static final MathContext TIME_PRECISION = new MathContext(4, RoundingMode.HALF_UP);
    private final AE2LtCraftConfirmTableRenderer table;
    private final Button start;
    private final Button selectCpu;
    private final Scrollbar scrollbar;

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
        CraftingPlanSummary plan = menu.getPlan();
        boolean startable = plan != null && !plan.isSimulation();
        start.active = !menu.hasNoCPU() && startable;
        selectCpu.active = startable;
        selectCpu.setMessage(getNextCpuButtonLabel());

        long calculationNanos = menu instanceof CraftingReportMenuState state
                ? state.ae2lt$getCalculationNanos() : 0L;
        Component cpuDetails = Component.empty();
        if (errorResult != null && errorResult.errorCode() != null) {
            cpuDetails = Component.translatable(
                    "gui.ae2lt.crafting_report.submit_error", errorResult.errorCode().name());
        } else if (plan != null) {
            if (plan.isSimulation()) {
                cpuDetails = GuiText.PartialPlan.text();
            } else if (menu.getCpuAvailableBytes() > 0) {
                cpuDetails = GuiText.ConfirmCraftCpuStatus.text(
                        menu.getCpuAvailableBytes(), menu.getCpuCoProcessors());
            } else {
                cpuDetails = GuiText.ConfirmCraftNoCpu.text();
            }
        }
        setTextContent(TEXT_ID_DIALOG_TITLE, Component.empty());
        setTextContent("cpu_status", cpuDetails);
        setTextContent("calculation_time", plan == null
                ? Component.literal("...")
                : formatDuration(calculationNanos));
        setTextContent("bytes_used", plan == null
                ? Component.literal("-")
                : formatBytes(plan.getUsedBytes()));

        int size = plan == null ? 0 : plan.getEntries().size();
        scrollbar.setRange(0, table.getScrollableRows(size), 1);
    }


    private static Component formatBytes(long bytes) {
        return Component.translatable(
                "gui.ae2lt.crafting_report.bytes",
                NumberFormat.getIntegerInstance().format(bytes));
    }

    private static Component formatDuration(long nanos) {
        if (nanos < 1_000L) {
            return Component.translatable(
                    "gui.ae2lt.crafting_report.time.nanoseconds", nanos);
        }
        if (nanos < 1_000_000L) {
            return Component.translatable(
                    "gui.ae2lt.crafting_report.time.microseconds",
                    formatDecimal(BigDecimal.valueOf(nanos, 3)));
        }
        if (nanos < 1_000_000_000L) {
            return Component.translatable(
                    "gui.ae2lt.crafting_report.time.milliseconds",
                    formatDecimal(BigDecimal.valueOf(nanos, 6)));
        }
        return Component.translatable(
                "gui.ae2lt.crafting_report.time.seconds",
                formatDecimal(BigDecimal.valueOf(nanos, 9)));
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
