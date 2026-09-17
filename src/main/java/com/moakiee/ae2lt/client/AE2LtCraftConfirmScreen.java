package com.moakiee.ae2lt.client;

import java.text.NumberFormat;
import java.util.Locale;

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
import appeng.util.ReadableNumberConverter;

import com.moakiee.thunderbolt.ae2.crafting.ExactAmountFormatter;
import com.moakiee.thunderbolt.ae2.crafting.ExactPlanReports;

public final class AE2LtCraftConfirmScreen extends AEBaseScreen<CraftConfirmMenu> {
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
        var exact = ExactPlanReports.get(plan);
        boolean startable = plan != null && !plan.isSimulation() && exact == null;
        start.active = !menu.hasNoCPU() && startable;
        selectCpu.active = startable;
        selectCpu.setMessage(getNextCpuButtonLabel());

        Component cpuDetails = Component.empty();
        if (errorResult != null && errorResult.errorCode() != null) {
            cpuDetails = Component.translatable(
                    "gui.ae2lt.crafting_report.submit_error", errorResult.errorCode().name());
        } else if (plan != null) {
            if (exact != null) {
                cpuDetails = Component.translatable("gui.ae2lt.crafting_report.exact_preview");
            } else if (plan.isSimulation()) {
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
        setTextContent("bytes_used", plan == null
                ? Component.literal("-")
                : exact == null ? formatBytes(plan.getUsedBytes())
                        : Component.translatable("gui.ae2lt.crafting_report.exact_bytes",
                                ExactAmountFormatter.compact(exact.bytes(), 1)));

        int size = plan == null ? 0 : plan.getEntries().size();
        scrollbar.setRange(0, table.getScrollableRows(size), 1);
    }


    private static Component formatBytes(long bytes) {
        return Component.translatable(
                "gui.ae2lt.crafting_report.bytes",
                NumberFormat.getIntegerInstance(Locale.US).format(bytes),
                ReadableNumberConverter.format(bytes, 4));
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
            var exact = ExactPlanReports.get(plan);
            if (exact != null && mouseX >= leftPos + 8 && mouseX < leftPos + 214
                    && mouseY >= topPos + 7 && mouseY < topPos + 24) {
                var lines = new java.util.ArrayList<Component>();
                lines.add(Component.translatable("gui.ae2lt.crafting_report.exact_preview"));
                String bytes = ExactAmountFormatter.full(exact.bytes(), 1);
                for (int i = 0; i < bytes.length(); i += 64) {
                    lines.add(Component.literal(bytes.substring(i, Math.min(i + 64, bytes.length()))));
                }
                if (exact.incomplete()) lines.add(Component.translatable("gui.ae2lt.crafting_report.exact_route"));
                graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
            }
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
