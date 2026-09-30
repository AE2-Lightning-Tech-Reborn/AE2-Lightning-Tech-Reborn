package com.moakiee.ae2lt.client;

import java.text.NumberFormat;
import java.util.Locale;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
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
import com.moakiee.ae2lt.crafting.report.MissingMaterialBookmarks;
import com.moakiee.ae2lt.crafting.report.CraftingReportStartState;
import com.moakiee.ae2lt.integration.eaep.EaepForceCraftingAccess;
import com.moakiee.ae2lt.integration.jei.JeiBookmarkAccess;

public final class AE2LtCraftConfirmScreen extends AEBaseScreen<CraftConfirmMenu> {
    private final AE2LtCraftConfirmTableRenderer table;
    private final Button start;
    private final Button selectCpu;
    private final Button bookmarkMissing;
    private final Scrollbar scrollbar;

    public AE2LtCraftConfirmScreen(
            CraftConfirmMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
        table = new AE2LtCraftConfirmTableRenderer(this);
        scrollbar = widgets.addScrollBar("scrollbar", Scrollbar.BIG);
        start = widgets.addButton("start", GuiText.Start.text(), this::startJob);
        start.active = false;
        selectCpu = widgets.addButton("selectCpu", getNextCpuButtonLabel(), this::selectNextCpu);
        selectCpu.active = false;
        widgets.addButton("cancel", GuiText.Cancel.text(), menu::goBack);
        bookmarkMissing = widgets.addButton("bookmarkMissing",
                Component.translatable("gui.ae2lt.crafting_report.bookmark_missing"), this::bookmarkMissing);
        bookmarkMissing.active = false;
        bookmarkMissing.setTooltip(Tooltip.create(
                Component.translatable("gui.ae2lt.crafting_report.bookmark_missing.tooltip")));
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        var errorResult = menu.submitError.result();
        CraftingPlanSummary plan = menu.getPlan();
        var exact = ExactPlanReports.get(plan);
        boolean bigMode = menu instanceof com.moakiee.ae2lt.crafting.big.BigConfirmMenu big && big.ae2lt$isBig();
        String bigFailure = bigMode ? ((com.moakiee.ae2lt.crafting.big.BigConfirmMenu)menu).ae2lt$failure() : "";
        boolean startable = CraftingReportStartState.normallyStartable(plan, bigMode);
        boolean canForce = CraftingReportStartState.forceCandidate(plan, bigMode)
                && EaepForceCraftingAccess.isAvailable(menu);
        boolean forceStart = canForce && hasShiftDown();
        start.active = !menu.hasNoCPU() && (startable || forceStart);
        start.setMessage(forceStart ? Component.translatable("gui.ae2lt.crafting_report.force_start")
                : GuiText.Start.text());
        start.setTooltip(canForce ? Tooltip.create(Component.translatable(forceStart
                ? "gui.ae2lt.crafting_report.force_start.tooltip"
                : "gui.ae2lt.crafting_report.force_start.hint")) : null);
        selectCpu.active = startable || forceStart;
        selectCpu.setMessage(getNextCpuButtonLabel());
        bookmarkMissing.active = JeiBookmarkAccess.isAvailable() && MissingMaterialBookmarks.hasMissing(plan);

        Component cpuDetails = Component.empty();
        if (!bigFailure.isEmpty()) {
            cpuDetails = Component.translatable("gui.ae2lt.crafting_report." + bigFailure);
        } else if (errorResult != null && errorResult.errorCode() != null) {
            cpuDetails = Component.translatable(
                    "gui.ae2lt.crafting_report.submit_error", errorResult.errorCode().name());
        } else if (plan != null) {
            if (exact != null && !bigMode) {
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

    private void startJob() {
        var plan = menu.getPlan();
        boolean bigMode = menu instanceof com.moakiee.ae2lt.crafting.big.BigConfirmMenu big && big.ae2lt$isBig();
        boolean forceStart = hasShiftDown() && CraftingReportStartState.forceCandidate(plan, bigMode)
                && EaepForceCraftingAccess.isAvailable(menu);
        if (menu.hasNoCPU() || (!CraftingReportStartState.normallyStartable(plan, bigMode) && !forceStart)) {
            return;
        }
        if (EaepForceCraftingAccess.synchronize(menu, forceStart)) {
            menu.startJob();
        }
    }

    private Component getNextCpuButtonLabel() {
        if (menu.hasNoCPU()) {
            return GuiText.NoCraftingCPUs.text();
        }
        Component cpuName = menu.cpuName == null ? GuiText.Automatic.text() : menu.cpuName;
        return GuiText.SelectedCraftingCPU.text(cpuName);
    }

    private void bookmarkMissing() {
        if (bookmarkMissing.active) {
            JeiBookmarkAccess.addMissingToBookmarks(MissingMaterialBookmarks.keys(menu.getPlan()));
        }
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
            startJob();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
