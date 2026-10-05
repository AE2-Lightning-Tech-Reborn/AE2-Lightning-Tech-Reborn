package com.moakiee.ae2lt.client;

import java.text.NumberFormat;
import java.util.Locale;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.api.config.Settings;
import appeng.api.config.TerminalStyle;
import appeng.client.gui.StackWithBounds;
import appeng.client.gui.me.crafting.CraftConfirmScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.Scrollbar;
import appeng.client.gui.widgets.SettingToggleButton;
import appeng.core.localization.GuiText;
import appeng.menu.me.crafting.CraftConfirmMenu;
import appeng.menu.me.crafting.CraftingPlanSummary;
import appeng.util.ReadableNumberConverter;

import com.moakiee.thunderbolt.ae2.crafting.ExactAmountFormatter;
import com.moakiee.thunderbolt.ae2.crafting.ExactPlanReports;
import com.moakiee.ae2lt.mixin.client.CraftConfirmScreenAccessor;

/** Keeps native confirmation-screen integrations, including AE2 Crafting Tree's toolbar. */
public final class AE2LtCraftConfirmScreen extends CraftConfirmScreen {
    private static final int ROW_HEIGHT = 23;
    private static final int HEADER_HEIGHT = 27;
    private static final int FOOTER_Y = 188;
    private static final int FOOTER_HEIGHT = 64;
    private static final int VERTICAL_PADDING = 28;

    private AE2LtCraftConfirmTableRenderer table;
    private final Button start;
    private final Button selectCpu;
    private final Scrollbar scrollbar;
    private int visibleRows = 7;

    public AE2LtCraftConfirmScreen(
            CraftConfirmMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
        // Reuse the native widgets so integrations and this report control the same buttons.
        var nativeScreen = (CraftConfirmScreenAccessor) (Object) this;
        scrollbar = nativeScreen.ae2lt$getScrollbar();
        start = nativeScreen.ae2lt$getStart();
        selectCpu = nativeScreen.ae2lt$getSelectCpu();
        addToLeftToolbar(new SettingToggleButton<>(Settings.TERMINAL_STYLE,
                config.getTerminalStyle(), this::toggleTerminalStyle));
    }

    @Override
    protected void init() {
        int maxRows = Math.max(1,
                (height - HEADER_HEIGHT - FOOTER_HEIGHT - VERTICAL_PADDING) / ROW_HEIGHT);
        int preferredRows = switch (config.getTerminalStyle()) {
            case SMALL -> 3;
            case MEDIUM -> 5;
            case TALL -> 7;
            case FULL -> maxRows;
        };
        visibleRows = Math.min(maxRows, preferredRows);
        imageHeight = HEADER_HEIGHT + visibleRows * ROW_HEIGHT + FOOTER_HEIGHT;
        table = new AE2LtCraftConfirmTableRenderer(this, visibleRows);
        scrollbar.setHeight(visibleRows * ROW_HEIGHT);
        super.init();
    }

    private void toggleTerminalStyle(SettingToggleButton<TerminalStyle> button, boolean backwards) {
        var next = button.getNextValue(backwards);
        config.setTerminalStyle(next);
        button.set(next);
        rebuildWidgets();
    }

    @Override
    protected void updateBeforeRender() {
        int scroll = scrollbar.getCurrentScroll();
        super.updateBeforeRender();

        // The native implementation opens CraftErrorScreen, which already offers cancel,
        // replan and retry and returns to this same report instance.
        if (minecraft.screen != this) {
            return;
        }

        var errorResult = menu.submitError.result();
        CraftingPlanSummary plan = menu.getPlan();
        var exact = ExactPlanReports.get(plan);
        boolean bigMode = menu instanceof com.moakiee.ae2lt.crafting.big.BigConfirmMenu big && big.ae2lt$isBig();
        String bigFailure = bigMode ? ((com.moakiee.ae2lt.crafting.big.BigConfirmMenu)menu).ae2lt$failure() : "";
        boolean startable = plan != null && !plan.isSimulation() && (exact == null || bigMode);
        start.active = !menu.hasNoCPU() && startable;
        selectCpu.active = startable;
        selectCpu.setMessage(getNextCpuButtonLabel());

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
        // The native screen briefly applies its fixed five-row range. Preserve the smaller
        // report's last rows instead of letting that range clamp its scroll on every frame.
        scrollbar.setCurrentScroll(scroll);
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

    @Override
    public void drawBG(GuiGraphics graphics, int offsetX, int offsetY,
            int mouseX, int mouseY, float partialTicks) {
        var background = style.getBackground().copy();
        background.src(0, 0, imageWidth, HEADER_HEIGHT).dest(offsetX, offsetY).blit(graphics);
        for (int row = 0; row < visibleRows; row++) {
            // Only the first row includes the two-pixel top bevel. Repeat an interior row.
            background.src(0, row == 0 ? HEADER_HEIGHT : HEADER_HEIGHT + ROW_HEIGHT, imageWidth, ROW_HEIGHT)
                    .dest(offsetX, offsetY + HEADER_HEIGHT + row * ROW_HEIGHT).blit(graphics);
        }
        background.src(0, FOOTER_Y - 1, imageWidth, 1)
                .dest(offsetX, offsetY + HEADER_HEIGHT + visibleRows * ROW_HEIGHT - 1).blit(graphics);
        background.src(0, FOOTER_Y, imageWidth, FOOTER_HEIGHT - 8)
                .dest(offsetX, offsetY + HEADER_HEIGHT + visibleRows * ROW_HEIGHT).blit(graphics);
        background.src(0, 252, imageWidth, 8)
                .dest(offsetX, offsetY + imageHeight - 8).blit(graphics);
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
