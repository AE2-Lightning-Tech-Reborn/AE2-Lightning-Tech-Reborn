package com.moakiee.ae2lt.client.crafting;

import java.text.NumberFormat;
import java.util.Locale;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.StackWithBounds;
import appeng.client.gui.me.crafting.CraftConfirmScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.Scrollbar;
import com.moakiee.ae2lt.client.ldlib.BackgroundLayer;
import com.moakiee.ae2lt.client.ldlib.LdlibAe2Button;
import com.moakiee.ae2lt.client.ldlib.LdlibRowScroller;
import com.moakiee.ae2lt.client.ldlib.LdlibStyleSprite;
import com.moakiee.ae2lt.client.ldlib.LdlibTerminalStyleButton;
import com.moakiee.ae2lt.client.ldlib.LdlibToolbarHost;
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
import com.moakiee.ae2lt.mixin.client.CraftConfirmScreenAccessor;
import com.moakiee.ae2lt.mixin.client.WidgetContainerAccessor;

/** Keeps native confirmation-screen integrations, including AE2 Crafting Tree's toolbar. */
public final class AE2LtCraftConfirmScreen extends CraftConfirmScreen implements LdlibToolbarHost {
    private static final int ROW_HEIGHT = 23;
    private static final int HEADER_HEIGHT = 27;
    private static final int FOOTER_Y = 188;
    private static final int FOOTER_HEIGHT = 64;
    private static final int VERTICAL_PADDING = 28;

    private AE2LtCraftConfirmTableRenderer table;
    private BackgroundLayer reportBackground;
    private final Button start;
    private final Button selectCpu;
    private final Button bookmarkMissing;
    private final LdlibRowScroller scrollbar;
    private int visibleRows = 7;

    public AE2LtCraftConfirmScreen(
            CraftConfirmMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
        // Reuse the native widgets so integrations and this report control the same buttons.
        var nativeScreen = (CraftConfirmScreenAccessor) (Object) this;
        scrollbar = new LdlibRowScroller(Scrollbar.BIG);
        ((WidgetContainerAccessor) widgets).ae2lt$getCompositeWidgets().put("scrollbar", scrollbar);
        start = nativeScreen.ae2lt$getStart();
        selectCpu = nativeScreen.ae2lt$getSelectCpu();
        bookmarkMissing = LdlibAe2Button.addText(widgets, style, "bookmarkMissing",
                Component.translatable("gui.ae2lt.crafting_report.bookmark_missing"), this::bookmarkMissing);
        bookmarkMissing.active = false;
        bookmarkMissing.setTooltip(Tooltip.create(
                Component.translatable("gui.ae2lt.crafting_report.bookmark_missing.tooltip")));
        addToLeftToolbar(new LdlibTerminalStyleButton(config, this::isHandlingRightClick, this::rebuildWidgets));
    }

    private void releaseReportLayers() {
        var previousTable = table;
        table = null;
        try {
            if (previousTable != null) {
                previousTable.onRemoved();
            }
        } finally {
            var previousBackground = reportBackground;
            reportBackground = null;
            if (previousBackground != null) {
                previousBackground.onRemoved();
            }
        }
    }

    @Override
    public void removed() {
        try {
            releaseReportLayers();
        } finally {
            super.removed();
        }
    }

    @Override
    protected void init() {
        releaseReportLayers();
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
        reportBackground = buildReportBackground(LdlibStyleSprite.from(style.getBackground()), imageWidth, visibleRows);
        super.init();
        scrollbar.setHeight(visibleRows * ROW_HEIGHT);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        // The native implementation opens CraftErrorScreen, which already offers cancel,
        // replan and retry and returns to this same report instance.
        if (minecraft.screen != this) {
            return;
        }

        var errorResult = menu.submitError.result();
        CraftingPlanSummary plan = menu.getPlan();
        var exact = ExactPlanReports.get(plan);
        boolean bigMode = menu instanceof com.moakiee.thunderbolt.api.crafting.CraftingRequestMenu big && big.thunderbolt$isExactRequest();
        Component bigFailure = bigMode ? ((com.moakiee.thunderbolt.api.crafting.CraftingRequestMenu)menu).thunderbolt$getRequestError() : Component.empty();
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
        if (!bigFailure.getString().isEmpty()) {
            cpuDetails = bigFailure;
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

    public void startJob() {
        var plan = menu.getPlan();
        boolean bigMode = menu instanceof com.moakiee.thunderbolt.api.crafting.CraftingRequestMenu big && big.thunderbolt$isExactRequest();
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

    @Override
    public void drawBG(GuiGraphics graphics, int offsetX, int offsetY,
            int mouseX, int mouseY, float partialTicks) {
        reportBackground.render(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
    }

    private static BackgroundLayer buildReportBackground(LdlibStyleSprite atlas, int width, int rows) {
        int footerTop = HEADER_HEIGHT + rows * ROW_HEIGHT;
        int height = footerTop + FOOTER_HEIGHT;
        var background = new BackgroundLayer(width, height)
                .add(atlas.slice(0, 0, width, HEADER_HEIGHT), 0, 0, width, HEADER_HEIGHT);
        // Only the first row includes the top bevel.
        background.add(atlas.slice(0, HEADER_HEIGHT, width, ROW_HEIGHT), 0, HEADER_HEIGHT, width, ROW_HEIGHT);
        if (rows > 1) {
            int bodyTop = HEADER_HEIGHT + ROW_HEIGHT;
            for (int y = bodyTop; y < footerTop; y += ROW_HEIGHT) {
                int heightLeft = Math.min(ROW_HEIGHT, footerTop - y);
                background.add(atlas.slice(0, bodyTop, width, heightLeft), 0, y, width, heightLeft);
            }
        }
        return background
                .add(atlas.slice(0, FOOTER_Y - 1, width, 1), 0, footerTop - 1, width, 1)
                .add(atlas.slice(0, FOOTER_Y, width, FOOTER_HEIGHT - 8),
                        0, footerTop, width, FOOTER_HEIGHT - 8)
                .add(atlas.slice(0, FOOTER_Y + FOOTER_HEIGHT, width, 8), 0, height - 8, width, 8);
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        CraftingPlanSummary plan = menu.getPlan();
        table.render(graphics, mouseX, mouseY, plan == null ? java.util.List.of() : plan.getEntries(),
                scrollbar.getCurrentScroll());
    }

    @Override
    public @Nullable StackWithBounds getStackUnderMouse(double mouseX, double mouseY) {
        var hovered = table == null ? null : table.getHoveredStack();
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
