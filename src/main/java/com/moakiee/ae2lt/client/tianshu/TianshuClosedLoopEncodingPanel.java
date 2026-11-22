/*
 * Layout derived from Applied Energistics 2's ProcessingEncodingPanel.
 * Copyright (c) Applied Energistics 2 contributors.
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */

package com.moakiee.ae2lt.client.tianshu;

import appeng.client.gui.style.PaletteColor;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.client.Point;
import appeng.client.gui.ICompositeWidget;
import appeng.client.gui.Icon;
import appeng.api.config.ActionItems;
import appeng.client.gui.WidgetContainer;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.LDLibFonts;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.moakiee.ae2lt.client.ldlib.LdlibAe2Button;
import com.moakiee.ae2lt.client.ldlib.LdlibAe2TextField;
import com.moakiee.ae2lt.client.ldlib.LdlibRowScroller;
import com.moakiee.ae2lt.item.ClosedLoopPatternItem;
import com.moakiee.ae2lt.logic.tianshu.terminal.SeedRefillSync;
import com.moakiee.ae2lt.menu.Ae2ltSlotSemantics;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import com.moakiee.ae2lt.client.ldlib.LdlibScreens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Inline closed-loop editor shown in the terminal's encoding area, mirroring the processing panel. */
final class TianshuClosedLoopEncodingPanel implements ICompositeWidget {
    private static final SpriteTexture BG =
            TianshuEncodingModePanel.PATTERN_MODES_ATLAS.slice(0, 70, 124, 66);
    // Reuse a blank part of AE2's processing panel to remove its input-to-output arrow.
    private static final SpriteTexture ARROW_COVER =
            TianshuEncodingModePanel.PATTERN_MODES_ATLAS.slice(73, 77, 23, 16);
    private static final int ARROW_X = 73;
    private static final int ARROW_Y = 25;
    private static final int PANEL_WIDTH = 124;
    private static final int PANEL_HEIGHT = 66;
    // Offsets relative to the panel anchor (JSON "closedLoopPanel": left 9 / bottom 166).
    private static final int MEMBER_X = 15;
    private static final int SLOT_Y = 8;
    private static final int OUTPUT_X = 100;
    private static final int CONTROL_X = 70;
    private static final int CONTROL_WIDTH = 26;
    private static final int EXEC_LABEL_Y = 9;
    private static final int STORED_LABEL_Y = 34;
    private static final int STATUS_Y = 58;
    private static final int STATUS_HEIGHT = 7;
    private static final int LABEL_HEIGHT = 7;
    private static final float LABEL_SCALE = 0.6F;
    private static final int LABEL_WIDTH = (int) (CONTROL_WIDTH / LABEL_SCALE);
    private static final int VISIBLE_ROWS = 3;

    private final TianshuPatternEncodingTermScreen<?> screen;
    private final TianshuPatternEncodingTermMenu menu;
    private final LdlibRowScroller scrollbar;
    private final LdlibAe2Button detailButton;
    private final LdlibAe2Button autoFillButton;
    private final LdlibAe2Button clearButton;
    private final LdlibAe2Button cycleOutputButton;
    private final LdlibAe2Button refillButton;
    private final LdlibAe2TextField executionField;
    private final LdlibAe2TextField storedField;
    private final ModularUI panelUi;
    private boolean visible;
    private boolean syncing;
    private int x;
    private int y;

    TianshuClosedLoopEncodingPanel(
            TianshuPatternEncodingTermScreen<?> screen,
            WidgetContainer widgets,
            Runnable openDetail) {
        this.screen = screen;
        this.menu = screen.getMenu();
        scrollbar = LdlibRowScroller.add(widgets, "closedLoopScrollbar");
        scrollbar.setRange(0,
                TianshuPatternEncodingTermMenu.CLOSED_LOOP_MEMBER_SLOTS / 3 - VISIBLE_ROWS, 3);
        scrollbar.setCaptureMouseWheel(false);

        detailButton = LdlibAe2Button.addText(widgets, screen.getStyle(), "closedLoopDetail",
                Component.translatable("ae2lt.tianshu.closed_loop.detail"), openDetail);
        detailButton.setTooltip(Tooltip.create(Component.translatable(
                "ae2lt.tianshu.closed_loop.detail.tooltip")));
        autoFillButton = LdlibAe2Button.addText(widgets, screen.getStyle(), "closedLoopAutoFill",
                Component.translatable("ae2lt.tianshu.closed_loop.auto_fill"),
                menu::autoFillClosedLoop);
        clearButton = LdlibAe2Button.small(Icon.S_CLEAR, ActionItems.S_CLOSE, menu::clearClosedLoopDraft);
        widgets.add("closedLoopClear", clearButton);

        cycleOutputButton = LdlibAe2Button.small(Icon.S_CYCLE,
                ActionItems.S_CYCLE_PROCESSING_OUTPUT, menu::cycleClosedLoopOutput);
        widgets.add("closedLoopCycleOutput", cycleOutputButton);

        refillButton = LdlibAe2Button.small(Icon.S_ARROW_DOWN,
                Component.translatable("ae2lt.tianshu.closed_loop.refill_seeds"),
                Component.translatable("ae2lt.tianshu.closed_loop.refill_seeds.tooltip").withStyle(ChatFormatting.GRAY),
                menu::refillClosedLoopSeeds);
        refillButton.setMessage(Component.empty());
        widgets.add("closedLoopSeedRefill", refillButton);

        executionField = LdlibAe2TextField.add(widgets, screen.getStyle(), "closedLoopExecMultiplier");
        storedField = LdlibAe2TextField.add(widgets, screen.getStyle(), "closedLoopStoredMultiplier");
        for (var field : List.of(executionField, storedField)) {
            field.setMaxLength(9);
            field.setFilter(TianshuClosedLoopEncodingPanel::isPositiveIntDraft);
            field.setResponder(ignored -> submitMultipliers());
        }
        updateMultiplierTooltips();
        panelUi = buildPanel();
    }

    @Override
    public void setPosition(Point position) {
        x = position.getX();
        y = position.getY();
    }

    @Override
    public void setSize(int width, int height) {
    }

    @Override
    public Rect2i getBounds() {
        return new Rect2i(x, y, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    void setVisible(boolean visible) {
        this.visible = visible;
        scrollbar.setVisible(visible);
        detailButton.visible = visible;
        autoFillButton.visible = visible;
        clearButton.setVisibility(visible);
        cycleOutputButton.setVisibility(visible && menu.canCycleClosedLoopOutputs());
        refillButton.setVisibility(visible);
        executionField.setVisible(visible);
        storedField.setVisible(visible);
        screen.setSlotsHidden(Ae2ltSlotSemantics.TIANSHU_CLOSED_LOOP_MEMBER, !visible);
        screen.setSlotsHidden(Ae2ltSlotSemantics.TIANSHU_CLOSED_LOOP_OUTPUT_MARK, !visible);
    }

    @Override
    public void updateBeforeRender() {
        if (!visible) return;

        int scroll = scrollbar.getCurrentScroll();
        var memberSlots = menu.getClosedLoopMemberSlots();
        for (int i = 0; i < memberSlots.size(); i++) {
            var slot = memberSlots.get(i);
            int effectiveRow = i / 3 - scroll;
            boolean active = effectiveRow >= 0 && effectiveRow < VISIBLE_ROWS;
            slot.setActive(active);
            slot.x = x + MEMBER_X + i % 3 * 18;
            slot.y = y + SLOT_Y + effectiveRow * 18;
        }
        var outputSlots = menu.getClosedLoopOutputSlots();
        for (int i = 0; i < outputSlots.size(); i++) {
            var slot = outputSlots.get(i);
            int effectiveRow = i - scroll;
            boolean active = effectiveRow >= 0 && effectiveRow < VISIBLE_ROWS;
            slot.setActive(active);
            slot.x = x + OUTPUT_X;
            slot.y = y + SLOT_Y + effectiveRow * 18;
        }

        autoFillButton.active = menu.closedLoopCandidateCount > 0
                || menu.hasClosedLoopPrimaryOutputMark();
        cycleOutputButton.setVisibility(menu.canCycleClosedLoopOutputs());
        autoFillButton.setTooltip(Tooltip.create(Component.translatable(
                "ae2lt.tianshu.closed_loop.auto_fill.tooltip",
                menu.closedLoopCandidateCount == 0 ? 0 : menu.closedLoopCandidateIndex + 1,
                menu.closedLoopCandidateCount)));
        refillButton.active = menu.seedRefillAvailable;
        syncFields();
        updateMultiplierTooltips();
    }

    private ModularUI buildPanel() {
        var root = new UIElement();
        var background = new UIElement().setAllowHitTest(false).style(s -> s.background(BG));
        var arrowCover = new UIElement().setAllowHitTest(false).style(s -> s.background(ARROW_COVER));
        root.addChildren(LdlibScreens.at(background, -1, 1, PANEL_WIDTH, PANEL_HEIGHT),
                LdlibScreens.at(arrowCover, ARROW_X - 1, ARROW_Y + 1, 23, 16));
        var ui = LdlibScreens.localLayer(root, PANEL_WIDTH, PANEL_HEIGHT);
        ui.setDrawTooltips(false);
        return ui;
    }

    @Override
    public void drawBackgroundLayer(GuiGraphics graphics, Rect2i bounds, Point mouse) {
        var pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(bounds.getX() + x, bounds.getY() + y, 0);
            panelUi.getWidget().render(graphics, mouse.getX() - x, mouse.getY() - y, 0);
        } finally {
            pose.popPose();
        }
    }

    @Override
    public void drawForegroundLayer(GuiGraphics graphics, Rect2i bounds, Point mouse) {
        int color = screen.getStyle().getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB();
        drawScaledText(graphics, Component.translatable("ae2lt.tianshu.closed_loop.execution_multiplier.short"),
                EXEC_LABEL_Y, color, false);
        drawScaledText(graphics, Component.translatable("ae2lt.tianshu.closed_loop.stored_multiplier.short"),
                STORED_LABEL_Y, color, false);
        drawScaledText(graphics, statusText(), STATUS_Y, 0xFF000000 | statusColor(), true);
    }

    private void drawScaledText(GuiGraphics graphics, Component text, int top, int color, boolean centered) {
        var font = Minecraft.getInstance().font;
        String clipped = font.plainSubstrByWidth(text.getString(), LABEL_WIDTH);
        float inset = centered ? (CONTROL_WIDTH - font.width(clipped) * LABEL_SCALE) / 2.0F : 0;
        var pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(x + CONTROL_X + inset, y + top, 0);
            pose.scale(LABEL_SCALE, LABEL_SCALE, 1.0F);
            LDLibFonts.drawText(graphics, font, Component.literal(clipped), 0, 0, color, false);
        } finally {
            pose.popPose();
        }
    }

    @Override
    public boolean onMouseWheel(Point mousePosition, double delta) {
        return scrollbar.onMouseWheel(mousePosition, delta);
    }

    List<Component> tooltipAt(int mouseX, int mouseY) {
        int localX = mouseX - x;
        int localY = mouseY - y;
        if (!visible || localX < CONTROL_X || localX >= CONTROL_X + CONTROL_WIDTH) {
            return List.of();
        }
        if (localY >= EXEC_LABEL_Y && localY < EXEC_LABEL_Y + LABEL_HEIGHT) {
            return executionField.getTooltipMessage();
        }
        if (localY >= STORED_LABEL_Y && localY < STORED_LABEL_Y + LABEL_HEIGHT) {
            return storedField.getTooltipMessage();
        }
        return localY >= STATUS_Y && localY < STATUS_Y + STATUS_HEIGHT ? buildStatusTooltip() : List.of();
    }

    private List<Component> buildStatusTooltip() {
        var lines = new ArrayList<Component>();
        lines.add(statusText());
        if (menu.uploadState == 0 && isRefillProblem(menu.seedRefillSync.state())) {
            for (var entry : menu.seedRefillSync.problems()) {
                if (entry.networkMissing() > 0) {
                    lines.add(Component.translatable(
                            "ae2lt.tianshu.closed_loop.refill.network_missing_entry",
                            entry.what().getDisplayName(), entry.networkMissing())
                            .withStyle(ChatFormatting.GOLD));
                }
                if (entry.storageBlocked() > 0) {
                    lines.add(Component.translatable(
                            "ae2lt.tianshu.closed_loop.refill.storage_blocked_entry",
                            entry.what().getDisplayName(), entry.storageBlocked())
                            .withStyle(ChatFormatting.RED));
                }
                if (entry.returnBlocked() > 0) {
                    lines.add(Component.translatable(
                            "ae2lt.tianshu.closed_loop.refill.return_blocked_entry",
                            entry.what().getDisplayName(), entry.returnBlocked())
                            .withStyle(ChatFormatting.RED));
                }
            }
            if (menu.seedRefillSync.problems().stream().anyMatch(entry -> entry.networkMissing() > 0)) {
                lines.add(Component.translatable(
                        "ae2lt.tianshu.closed_loop.refill.network_missing_hint")
                        .withStyle(ChatFormatting.GRAY));
            }
            if (menu.seedRefillSync.problems().stream().anyMatch(entry -> entry.storageBlocked() > 0)) {
                lines.add(Component.translatable(
                        "ae2lt.tianshu.closed_loop.refill.storage_blocked_hint")
                        .withStyle(ChatFormatting.GRAY));
            }
        }
        if (menu.uploadState == 0
                && menu.seedRefillSync.problems().stream().anyMatch(entry -> entry.returnBlocked() > 0)) {
            lines.add(Component.translatable("ae2lt.tianshu.closed_loop.refill.return_blocked_hint")
                    .withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("ae2lt.tianshu.terminal.closed_loop.candidate",
                menu.closedLoopCandidateCount == 0 ? 0 : menu.closedLoopCandidateIndex + 1,
                menu.closedLoopCandidateCount).withStyle(ChatFormatting.GRAY));
        return lines;
    }

    private Component statusText() {
        if (menu.uploadState != 0) {
            return Component.translatable(menu.uploadState == 1
                    ? "ae2lt.tianshu.terminal.upload.success"
                    : "ae2lt.tianshu.terminal.upload.failed");
        }
        return switch (menu.seedRefillSync.state()) {
            case SeedRefillSync.STATE_COMPLETE ->
                Component.translatable("ae2lt.tianshu.closed_loop.refill.status.complete");
            case SeedRefillSync.STATE_NETWORK_MISSING ->
                Component.translatable("ae2lt.tianshu.closed_loop.refill.status.network_missing");
            case SeedRefillSync.STATE_STORAGE_BLOCKED ->
                Component.translatable("ae2lt.tianshu.closed_loop.refill.status.storage_blocked");
            case SeedRefillSync.STATE_MIXED ->
                Component.translatable("ae2lt.tianshu.closed_loop.refill.status.mixed");
            case SeedRefillSync.STATE_RETURN_BLOCKED ->
                Component.translatable("ae2lt.tianshu.closed_loop.refill.status.return_blocked");
            case SeedRefillSync.STATE_UNAVAILABLE ->
                Component.translatable("ae2lt.tianshu.closed_loop.refill.status.unavailable");
            default -> Component.translatable("ae2lt.tianshu.closed_loop.status."
                    + menu.closedLoopDraftStatus.name().toLowerCase(Locale.ROOT));
        };
    }

    private int statusColor() {
        if (menu.uploadState != 0) return menu.uploadState == 1 ? 0x228822 : 0xAA2222;
        return switch (menu.seedRefillSync.state()) {
            case SeedRefillSync.STATE_COMPLETE -> 0x228822;
            case SeedRefillSync.STATE_NETWORK_MISSING -> 0xAA7700;
            case SeedRefillSync.STATE_STORAGE_BLOCKED, SeedRefillSync.STATE_RETURN_BLOCKED,
                    SeedRefillSync.STATE_MIXED -> 0xAA2222;
            case SeedRefillSync.STATE_UNAVAILABLE -> 0xAA2222;
            default -> switch (menu.closedLoopDraftStatus) {
                case VALID, ENCODED -> 0x228822;
                case EMPTY, NO_CANDIDATE -> screen.getStyle().getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB();
                case MISSING_PRIMARY_OUTPUT -> 0xAA7700;
                default -> 0xAA2222;
            };
        };
    }

    private static boolean isRefillProblem(int state) {
        return state == SeedRefillSync.STATE_NETWORK_MISSING
                || state == SeedRefillSync.STATE_STORAGE_BLOCKED
                || state == SeedRefillSync.STATE_MIXED
                || state == SeedRefillSync.STATE_RETURN_BLOCKED;
    }

    private void syncFields() {
        syncing = true;
        try {
            if (!executionField.isFocused()) {
                var value = Integer.toString(menu.closedLoopExecutionSeedMultiplier);
                if (!executionField.getValue().equals(value)) executionField.setValue(value);
            }
            if (!storedField.isFocused()) {
                var value = Integer.toString(menu.closedLoopStoredTaskMultiplier);
                if (!storedField.getValue().equals(value)) storedField.setValue(value);
            }
        } finally {
            syncing = false;
        }
    }

    private void submitMultipliers() {
        if (syncing || executionField.getValue().isEmpty() || storedField.getValue().isEmpty()) return;
        try {
            int execution = Integer.parseInt(executionField.getValue());
            int stored = Integer.parseInt(storedField.getValue());
            if (execution >= 1 && stored >= 1) menu.setClosedLoopMultipliers(execution, stored);
        } catch (NumberFormatException ignored) {
        }
    }

    private void updateMultiplierTooltips() {
        executionField.setTooltipMessage(multiplierTooltip(
                executionField,
                "ae2lt.tianshu.closed_loop.execution_multiplier",
                "ae2lt.tianshu.terminal.closed_loop.execution_seed_multiplier.tooltip"));
        storedField.setTooltipMessage(multiplierTooltip(
                storedField,
                "ae2lt.tianshu.closed_loop.stored_multiplier",
                "ae2lt.tianshu.terminal.closed_loop.stored_task_multiplier.tooltip"));
    }

    private static List<Component> multiplierTooltip(
            LdlibAe2TextField field, String titleKey, String descriptionKey) {
        return List.of(
                Component.translatable(titleKey),
                Component.translatable(
                        "ae2lt.tianshu.terminal.closed_loop.multiplier.current_value",
                        field.getValue()).withStyle(ChatFormatting.AQUA),
                Component.translatable(descriptionKey).withStyle(ChatFormatting.GRAY));
    }

    private static boolean isPositiveIntDraft(String value) {
        if (value.isEmpty()) return true;
        try {
            return Integer.parseInt(value) >= 1;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    /** Shared member tooltip lines used by the terminal and the detail sub-screen. */
    static void appendMemberTooltip(List<Component> lines, TianshuPatternEncodingTermMenu menu,
                                    int index, ItemStack stack, Level level) {
        lines.add(Component.translatable("ae2lt.tianshu.closed_loop.tooltip.member",
                index + 1).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("ae2lt.tianshu.closed_loop.tooltip.copies",
                menu.closedLoopDraftSync.copies(index)).withStyle(ChatFormatting.GRAY));
        boolean macro = stack.getItem() instanceof ClosedLoopPatternItem item
                && item.hasPayload(stack) && item.readExecutionMember(stack) < 0;
        lines.add(Component.translatable("ae2lt.tianshu.closed_loop.tooltip.macro",
                Component.translatable(macro ? "gui.yes" : "gui.no"))
                .withStyle(ChatFormatting.GRAY));
        if (level == null) return;
        var details = PatternDetailsHelper.decodePattern(stack, level);
        if (details == null) return;
        lines.add(Component.translatable("ae2lt.tianshu.closed_loop.tooltip.inputs")
                .withStyle(ChatFormatting.DARK_GRAY));
        for (var input : details.getInputs()) {
            var possible = input.getPossibleInputs();
            if (possible.length == 0 || possible[0] == null || possible[0].what() == null) continue;
            long amount;
            try {
                amount = Math.multiplyExact(possible[0].amount(), input.getMultiplier());
            } catch (ArithmeticException ignored) {
                amount = Long.MAX_VALUE;
            }
            lines.add(Component.translatable("ae2lt.tianshu.closed_loop.tooltip.stack",
                    possible[0].what().getDisplayName(), amount).withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("ae2lt.tianshu.closed_loop.tooltip.outputs")
                .withStyle(ChatFormatting.DARK_GRAY));
        for (var output : details.getOutputs()) {
            if (output == null || output.what() == null) continue;
            lines.add(Component.translatable("ae2lt.tianshu.closed_loop.tooltip.stack",
                    output.what().getDisplayName(), output.amount()).withStyle(ChatFormatting.GRAY));
        }
    }
}
