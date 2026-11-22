package com.moakiee.ae2lt.client.tianshu;

import com.moakiee.ae2lt.client.ldlib.LdlibAe2Button;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.AESubScreen;
import appeng.client.gui.widgets.AE2Button;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.texture.VanillaSpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.moakiee.ae2lt.client.ldlib.BackgroundLayer;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.moakiee.ae2lt.client.gui.LargeStackCountRenderer;
import com.moakiee.ae2lt.client.ldlib.LdlibScreens;
import com.moakiee.ae2lt.client.ldlib.LdlibToolbarHost;
import appeng.client.gui.Icon;
import com.moakiee.ae2lt.client.ldlib.LdlibAe2TextField;
import com.moakiee.ae2lt.client.ldlib.LdlibRowScroller;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.AppEngSlot;
import appeng.client.gui.style.PaletteColor;
import com.moakiee.ae2lt.logic.tianshu.terminal.ClosedLoopDraftStatus;
import com.moakiee.ae2lt.logic.tianshu.terminal.ClosedLoopResultPage;
import com.moakiee.ae2lt.menu.Ae2ltSlotSemantics;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/** Server-backed, paged editor for one closed-loop pattern draft. */
final class TianshuClosedLoopPatternConfigScreen<M extends TianshuPatternEncodingTermMenu>
        extends AESubScreen<M, TianshuPatternEncodingTermScreen<M>> implements LdlibToolbarHost {
    private static final int SLOT_X = 13;
    private static final int ROW_SLOT_Y_OFFSET = 4;
    private static final int HIDDEN_SLOT = -10000;
    private static final int NAME_X = 34;
    private static final int MEMBER_NAME_WIDTH = 45;
    private static final int RESULT_NAME_WIDTH = 130;
    private static final int MEMBER_AMOUNT_X = 96;
    private static final int MEMBER_AMOUNT_WIDTH = 36;
    private static final int MEMBER_AMOUNT_Y_OFFSET = 6;
    private static final int NUMBER_FIELD_HEIGHT = 12;
    private static final int MEMBER_UP_X = 134;
    private static final int MEMBER_DOWN_X = 150;
    private static final int ROLE_X = 111;
    private static final int ROLE_WIDTH = 54;
    private static final VanillaSpriteTexture ROLE_BACKGROUND = VanillaSpriteTexture.of("ae2:button_disabled");
    private static final int FOOTER_TEXT_WIDTH = 168;
    private static final int SETTINGS_FIELD_X = 96;
    private static final int SETTINGS_FIELD_WIDTH = 69;
    private static final int SETTINGS_EXECUTION_ROW_Y = TianshuPatternConfigLayout.HEADER_HEIGHT + 32;
    private static final int SETTINGS_STORED_ROW_Y =
            SETTINGS_EXECUTION_ROW_Y + TianshuPatternConfigLayout.ROW_HEIGHT;

    private final BackgroundLayer background = TianshuPatternConfigLayout.createBackground();
    private final LdlibRowScroller scrollbar;
    private final List<LdlibAe2Button> pageButtons;
    private final LdlibAe2TextField[] memberAmounts = new LdlibAe2TextField[TianshuPatternConfigLayout.VISIBLE_ROWS];
    private final int[] memberRows = new int[TianshuPatternConfigLayout.VISIBLE_ROWS];
    private final LdlibAe2Button[] memberUp = new LdlibAe2Button[TianshuPatternConfigLayout.VISIBLE_ROWS];
    private final LdlibAe2Button[] memberDown = new LdlibAe2Button[TianshuPatternConfigLayout.VISIBLE_ROWS];
    private Page page = Page.MEMBERS;
    private LdlibAe2TextField executionMultiplier;
    private LdlibAe2TextField storedMultiplier;
    private LdlibAe2Button previousCandidate;
    private LdlibAe2Button nextCandidate;
    private boolean syncingFields;
    @Nullable private ClosedLoopResultPage.Kind requestedResultKind;
    private int requestedResultRevision = Integer.MIN_VALUE;
    private int requestedResultOffset = -1;
    private final SlotRow[] slotRows = new SlotRow[TianshuPatternConfigLayout.VISIBLE_ROWS];
    private UIElement detailsRoot;
    private UIElement slotRowsRoot;
    private UIElement resultRowsRoot;
    private UIElement settingsRoot;
    private ModularUI detailsUi;
    private Label draftStatus;
    private Label pageCount;
    private Label candidate;
    @Nullable private ClosedLoopResultPage displayedResultPage;

    TianshuClosedLoopPatternConfigScreen(TianshuPatternEncodingTermScreen<M> parent) {
        super(parent, "/screens/tianshu_closed_loop_pattern_config.json");
        imageWidth = TianshuPatternConfigLayout.GUI_WIDTH;
        imageHeight = TianshuPatternConfigLayout.GUI_HEIGHT;

        widgets.add("button_back", LdlibAe2Button.back(Component.translatable("gui.back"), this::closeEditor));
        pageButtons = List.of(
                LdlibAe2Button.addText(widgets, style, "page_members", pageLabel(Page.MEMBERS),
                        () -> setPage(Page.MEMBERS)),
                LdlibAe2Button.addText(widgets, style, "page_outputs", pageLabel(Page.OUTPUTS),
                        () -> setPage(Page.OUTPUTS)),
                LdlibAe2Button.addText(widgets, style, "page_inputs", pageLabel(Page.EXTERNAL_INPUTS),
                        () -> setPage(Page.EXTERNAL_INPUTS)),
                LdlibAe2Button.addText(widgets, style, "page_seeds", pageLabel(Page.SEEDS),
                        () -> setPage(Page.SEEDS)),
                LdlibAe2Button.addText(widgets, style, "page_settings", pageLabel(Page.SETTINGS),
                        () -> setPage(Page.SETTINGS)));
        scrollbar = LdlibRowScroller.add(widgets, "scrollbar");
    }

    private void releaseEditorLayers() {
        var previous = detailsUi;
        detailsUi = null;
        try {
            if (previous != null) {
                previous.onRemoved();
            }
        } finally {
            background.onRemoved();
        }
    }

    @Override
    protected void onReturnToParent() {
        try {
            releaseEditorLayers();
        } finally {
            super.onReturnToParent();
        }
    }

    @Override
    public void removed() {
        try {
            releaseEditorLayers();
        } finally {
            super.removed();
        }
    }

    @Override
    protected void init() {
        for (var field : memberAmounts) {
            if (field != null) field.dispose();
        }
        for (var buttons : List.of(memberUp, memberDown)) {
            for (var button : buttons) {
                if (button != null) button.dispose();
            }
        }
        if (executionMultiplier != null) executionMultiplier.dispose();
        if (storedMultiplier != null) storedMultiplier.dispose();
        if (previousCandidate != null) previousCandidate.dispose();
        if (nextCandidate != null) nextCandidate.dispose();
        releaseEditorLayers();
        super.init();
        initDetails();
        scrollbar.setHeight(TianshuPatternConfigLayout.SCROLLBAR_HEIGHT);
        for (int row = 0; row < TianshuPatternConfigLayout.VISIBLE_ROWS; row++) {
            final int visibleRow = row;
            int rowY = TianshuPatternConfigLayout.HEADER_HEIGHT
                    + row * TianshuPatternConfigLayout.ROW_HEIGHT;
            var amount = numberField(MEMBER_AMOUNT_X, rowY + MEMBER_AMOUNT_Y_OFFSET,
                    MEMBER_AMOUNT_WIDTH);
            amount.setMaxLength(19);
            amount.setFilter(TianshuClosedLoopPatternConfigScreen::isPositiveLongDraft);
            amount.setResponder(value -> submitMemberAmount(visibleRow, value));
            memberAmounts[row] = amount;

            memberUp[row] = addRenderableWidget(LdlibAe2Button.toolbar(
                    Icon.ARROW_UP,
                    Component.translatable("ae2lt.tianshu.closed_loop.move_up"),
                    () -> moveMember(visibleRow, -1)));
            memberUp[row].setMessage(Component.empty());
            memberUp[row].setPosition(leftPos + MEMBER_UP_X, topPos + rowY + 4);
            memberDown[row] = addRenderableWidget(LdlibAe2Button.toolbar(
                    Icon.ARROW_DOWN,
                    Component.translatable("ae2lt.tianshu.closed_loop.move_down"),
                    () -> moveMember(visibleRow, 1)));
            memberDown[row].setMessage(Component.empty());
            memberDown[row].setPosition(leftPos + MEMBER_DOWN_X, topPos + rowY + 4);

        }

        executionMultiplier = numberBox(SETTINGS_FIELD_X, SETTINGS_EXECUTION_ROW_Y,
                menu.closedLoopExecutionSeedMultiplier);
        storedMultiplier = numberBox(SETTINGS_FIELD_X, SETTINGS_STORED_ROW_Y,
                menu.closedLoopStoredTaskMultiplier);
        executionMultiplier.setResponder(ignored -> submitMultipliers());
        storedMultiplier.setResponder(ignored -> submitMultipliers());
        executionMultiplier.setTooltipMessage(List.of(Component.translatable(
                "ae2lt.tianshu.terminal.closed_loop.execution_seed_multiplier.tooltip")));
        storedMultiplier.setTooltipMessage(List.of(Component.translatable(
                "ae2lt.tianshu.terminal.closed_loop.stored_task_multiplier.tooltip")));

        previousCandidate = addRenderableWidget(LdlibAe2Button.toolbar(
                Icon.ARROW_LEFT,
                Component.translatable("ae2lt.tianshu.closed_loop.previous_candidate"),
                () -> menu.selectClosedLoopCandidate(-1)));
        previousCandidate.setMessage(Component.empty());
        previousCandidate.setPosition(leftPos + 116, topPos + TianshuPatternConfigLayout.HEADER_HEIGHT + 4);
        nextCandidate = addRenderableWidget(LdlibAe2Button.toolbar(
                Icon.ARROW_RIGHT,
                Component.translatable("ae2lt.tianshu.closed_loop.next_candidate"),
                () -> menu.selectClosedLoopCandidate(1)));
        nextCandidate.setMessage(Component.empty());
        nextCandidate.setPosition(leftPos + 145, topPos + TianshuPatternConfigLayout.HEADER_HEIGHT + 4);

        hideParentSlots();
        configurePage();
        scrollbar.setOnScrollChanged(this::refreshVisiblePage);
    }

    private LdlibAe2TextField numberBox(int x, int y, int value) {
        var field = numberField(x, y, SETTINGS_FIELD_WIDTH);
        field.setMaxLength(9);
        field.setFilter(TianshuClosedLoopPatternConfigScreen::isPositiveIntDraft);
        syncingFields = true;
        field.setValue(Integer.toString(value));
        syncingFields = false;
        return field;
    }

    private LdlibAe2TextField numberField(int x, int y, int width) {
        var field = LdlibAe2TextField.create(width, NUMBER_FIELD_HEIGHT, style);
        field.setPosition(leftPos + x, topPos + y);
        return addRenderableWidget(field);
    }

    private void setPage(Page newPage) {
        if (page == newPage) return;
        page = newPage;
        scrollbar.setCurrentScroll(0);
        configurePage();
    }

    private void configurePage() {
        int rows = switch (page) {
            case MEMBERS -> TianshuPatternEncodingTermMenu.CLOSED_LOOP_MEMBER_SLOTS;
            case OUTPUTS -> TianshuPatternEncodingTermMenu.CLOSED_LOOP_OUTPUT_SLOTS;
            case EXTERNAL_INPUTS -> Math.min(menu.closedLoopExternalInputCount,
                    TianshuPatternEncodingTermMenu.CLOSED_LOOP_RESULT_SLOTS);
            case SEEDS -> Math.min(menu.closedLoopSeedInputCount,
                    TianshuPatternEncodingTermMenu.CLOSED_LOOP_RESULT_SLOTS);
            case SETTINGS -> 0;
        };
        scrollbar.setRange(0,
                Math.max(0, rows - TianshuPatternConfigLayout.VISIBLE_ROWS), 1);
        scrollbar.setVisible(page != Page.SETTINGS && rows > TianshuPatternConfigLayout.VISIBLE_ROWS);
        for (int i = 0; i < pageButtons.size(); i++) {
            var value = Page.values()[i];
            pageButtons.get(i).setMessage(pageLabel(value).copy().withStyle(
                    value == page ? ChatFormatting.GREEN : ChatFormatting.WHITE));
        }
        refreshVisiblePage();
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        refreshVisiblePage();
    }

    private void refreshVisiblePage() {
        updateVisibleSlots();
        updateControls();
        requestVisibleResultPage();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        syncFields();
    }

    private void updateVisibleSlots() {
        hideSlots(menu.getClosedLoopMemberSlots());
        hideSlots(menu.getClosedLoopOutputSlots());
        setSlotsHidden(Ae2ltSlotSemantics.TIANSHU_CLOSED_LOOP_MEMBER, page != Page.MEMBERS);
        setSlotsHidden(Ae2ltSlotSemantics.TIANSHU_CLOSED_LOOP_OUTPUT_MARK, page != Page.OUTPUTS);

        var slots = switch (page) {
            case MEMBERS -> menu.getClosedLoopMemberSlots();
            case OUTPUTS -> menu.getClosedLoopOutputSlots();
            case EXTERNAL_INPUTS, SEEDS, SETTINGS -> List.<AppEngSlot>of();
        };
        int scroll = scrollbar.getCurrentScroll();
        for (int visible = 0; visible < TianshuPatternConfigLayout.VISIBLE_ROWS; visible++) {
            int index = scroll + visible;
            if (index >= slots.size()) break;
            var slot = slots.get(index);
            slot.x = SLOT_X;
            slot.y = TianshuPatternConfigLayout.HEADER_HEIGHT
                    + visible * TianshuPatternConfigLayout.ROW_HEIGHT + ROW_SLOT_Y_OFFSET;
            slot.setActive(true);
        }
    }

    private void updateControls() {
        int scroll = scrollbar.getCurrentScroll();
        for (int visible = 0; visible < TianshuPatternConfigLayout.VISIBLE_ROWS; visible++) {
            int memberIndex = scroll + visible;
            boolean memberPage = page == Page.MEMBERS
                    && memberIndex < TianshuPatternEncodingTermMenu.CLOSED_LOOP_MEMBER_SLOTS;
            var amount = memberAmounts[visible];
            memberRows[visible] = memberIndex;
            var memberSlot = memberPage ? menu.getClosedLoopMemberSlots().get(memberIndex) : null;
            boolean memberPresent = memberSlot != null && !memberSlot.getItem().isEmpty();
            amount.setVisible(memberPage);
            amount.active = memberPresent;
            amount.setEditable(memberPresent);
            memberUp[visible].visible = memberPage;
            memberDown[visible].visible = memberPage;
            memberUp[visible].active = memberPresent && memberIndex > 0;
            memberDown[visible].active = memberPresent
                    && memberIndex + 1 < TianshuPatternEncodingTermMenu.CLOSED_LOOP_MEMBER_SLOTS;

        }
        boolean settings = page == Page.SETTINGS;
        executionMultiplier.setVisible(settings);
        storedMultiplier.setVisible(settings);
        previousCandidate.visible = settings;
        nextCandidate.visible = settings;
        previousCandidate.active = menu.closedLoopCandidateCount > 1;
        nextCandidate.active = menu.closedLoopCandidateCount > 1;
        syncFields();
    }

    private void syncFields() {
        syncingFields = true;
        try {
            if (page == Page.MEMBERS) {
                for (int visible = 0; visible < memberAmounts.length; visible++) {
                    var field = memberAmounts[visible];
                    int index = memberRows[visible];
                    if (field.isFocused() || index < 0
                            || index >= TianshuPatternEncodingTermMenu.CLOSED_LOOP_MEMBER_SLOTS) continue;
                    boolean present = !menu.getClosedLoopMemberSlots().get(index).getItem().isEmpty();
                    String value = present
                            ? Long.toString(Math.max(1L, menu.closedLoopDraftSync.copies(index))) : "";
                    if (!field.getValue().equals(value)) field.setValue(value);
                }
            }
            if (executionMultiplier != null && !executionMultiplier.isFocused()) {
                String value = Integer.toString(menu.closedLoopExecutionSeedMultiplier);
                if (!executionMultiplier.getValue().equals(value)) executionMultiplier.setValue(value);
            }
            if (storedMultiplier != null && !storedMultiplier.isFocused()) {
                String value = Integer.toString(menu.closedLoopStoredTaskMultiplier);
                if (!storedMultiplier.getValue().equals(value)) storedMultiplier.setValue(value);
            }
        } finally {
            syncingFields = false;
        }
    }

    private void submitMemberAmount(int visibleRow, String value) {
        if (syncingFields || page != Page.MEMBERS || value.isEmpty()) return;
        try {
            long parsed = Long.parseLong(value);
            if (parsed >= 1L) menu.setClosedLoopMemberCopies(memberRows[visibleRow], parsed);
        } catch (NumberFormatException ignored) {
        }
    }

    private void moveMember(int visibleRow, int direction) {
        if (page == Page.MEMBERS) menu.moveClosedLoopMember(memberRows[visibleRow], direction);
    }

    private void submitMultipliers() {
        if (syncingFields || executionMultiplier == null || storedMultiplier == null
                || executionMultiplier.getValue().isEmpty() || storedMultiplier.getValue().isEmpty()) return;
        try {
            int execution = Integer.parseInt(executionMultiplier.getValue());
            int stored = Integer.parseInt(storedMultiplier.getValue());
            if (execution >= 1 && stored >= 1) menu.setClosedLoopMultipliers(execution, stored);
        } catch (NumberFormatException ignored) {
        }
    }

    private void initDetails() {
        int textColor = style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB();
        detailsRoot = new UIElement();
        slotRowsRoot = addRowsGroup();
        resultRowsRoot = addRowsGroup();
        settingsRoot = addDetailsGroup();
        for (int visible = 0; visible < slotRows.length; visible++) {
            var slotRow = new SlotRow(textColor);
            slotRows[visible] = slotRow;
            slotRowsRoot.addChild(slotRow);
        }
        draftStatus = addLabel(detailsRoot, Component.empty(), 0xFF666666, 8, 161, FOOTER_TEXT_WIDTH);
        pageCount = addLabel(detailsRoot, Component.empty(), textColor, 8, 173, FOOTER_TEXT_WIDTH);
        candidate = addLabel(settingsRoot, Component.empty(), textColor,
                12, TianshuPatternConfigLayout.HEADER_HEIGHT + 8, FOOTER_TEXT_WIDTH);
        addLabel(settingsRoot, Component.translatable("ae2lt.tianshu.closed_loop.execution_multiplier"),
                textColor, 12, SETTINGS_EXECUTION_ROW_Y + 2, FOOTER_TEXT_WIDTH);
        addLabel(settingsRoot, Component.translatable("ae2lt.tianshu.closed_loop.stored_multiplier"),
                textColor, 12, SETTINGS_STORED_ROW_Y + 2, FOOTER_TEXT_WIDTH);
        detailsUi = LdlibScreens.localLayer(detailsRoot, imageWidth, imageHeight);
        // AE2 renders tooltips after restoring the screen-space pose.
        detailsUi.setDrawTooltips(false);
        displayedResultPage = null;
    }

    private UIElement addDetailsGroup() {
        var group = new UIElement().setAllowHitTest(false);
        detailsRoot.addChild(LdlibScreens.at(group, 0, 0, imageWidth, imageHeight));
        return group;
    }

    private UIElement addRowsGroup() {
        var group = new UIElement().setAllowHitTest(false).layout(l -> l.flexDirection(FlexDirection.COLUMN));
        detailsRoot.addChild(LdlibScreens.at(group, 0, TianshuPatternConfigLayout.HEADER_HEIGHT, imageWidth,
                TianshuPatternConfigLayout.VISIBLE_ROWS * TianshuPatternConfigLayout.ROW_HEIGHT));
        return group;
    }

    private static void styleRow(UIElement row) {
        row.setAllowHitTest(false);
        row.layout(l -> l.widthPercent(100).height(TianshuPatternConfigLayout.ROW_HEIGHT).flexShrink(0));
    }

    private static Label addLabel(UIElement parent, Component text, int color, int x, int y, int width) {
        var label = LdlibScreens.label(text, color);
        label.setAllowHitTest(false);
        label.textStyle(style -> style.textWrap(TextWrap.NONE));
        parent.addChild(LdlibScreens.at(label, x, y, width, 9));
        return label;
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        boolean settings = page == Page.SETTINGS;
        slotRowsRoot.setVisible(page == Page.MEMBERS || page == Page.OUTPUTS);
        resultRowsRoot.setVisible(page == Page.EXTERNAL_INPUTS || page == Page.SEEDS);
        settingsRoot.setVisible(settings);
        switch (page) {
            case MEMBERS -> updateSlotRows(menu.getClosedLoopMemberSlots(), true);
            case OUTPUTS -> updateSlotRows(menu.getClosedLoopOutputSlots(), false);
            case EXTERNAL_INPUTS -> drawResultRows(ClosedLoopResultPage.Kind.EXTERNAL_INPUTS);
            case SEEDS -> drawResultRows(ClosedLoopResultPage.Kind.SEEDS);
            case SETTINGS -> candidate.setText(Component.translatable("ae2lt.tianshu.closed_loop.candidate",
                    menu.closedLoopCandidateCount == 0 ? 0 : menu.closedLoopCandidateIndex + 1,
                    menu.closedLoopCandidateCount));
        }
        var status = Component.translatable("ae2lt.tianshu.closed_loop.status."
                + menu.closedLoopDraftStatus.name().toLowerCase(Locale.ROOT));
        draftStatus.setText(Component.literal(font.plainSubstrByWidth(status.getString(), FOOTER_TEXT_WIDTH)));
        draftStatus.getTextStyle().textColor(0xFF000000 | statusColor(menu.closedLoopDraftStatus));
        pageCount.setVisible(!settings);
        if (!settings) {
            int rows = currentRowCount();
            pageCount.setText(Component.translatable("ae2lt.tianshu.closed_loop.page_count",
                    rows == 0 ? 0 : scrollbar.getCurrentScroll() + 1, rows));
        }
        detailsUi.getWidget().render(graphics, mouseX, mouseY, 0);
    }

    private void updateSlotRows(List<AppEngSlot> slots, boolean members) {
        int scroll = scrollbar.getCurrentScroll();
        for (int visible = 0; visible < slotRows.length; visible++) {
            int index = scroll + visible;
            var row = slotRows[visible];
            row.setVisible(index < slots.size());
            if (index < slots.size()) {
                var stack = slots.get(index).getItem();
                var role = members || stack.isEmpty() ? null : roleLabel(menu.closedLoopDraftSync.outputRole(index));
                row.update(font, index, stack, members, role);
            }
        }
    }

    private static final class SlotRow extends UIElement {
        private static final int OUTPUT_NAME_WIDTH = ROLE_X - NAME_X - 4;
        private final Label number;
        private final Label memberName;
        private final Label outputName;
        private Component roleMessage = Component.empty();
        private final Button role = new Button() {
            @Override
            public void drawBackgroundAdditional(GUIContext context) {
                super.drawBackgroundAdditional(context);
                float x = getPositionX();
                float y = getPositionY();
                var message = roleMessage;
                context.postRendering(post -> renderRoleText(post, message, x, y));
            }
        };

        private SlotRow(int textColor) {
            number = addLabel(this, Component.empty(), textColor, NAME_X, 8, 13);
            memberName = addLabel(this, Component.empty(), textColor, NAME_X + 13, 8, MEMBER_NAME_WIDTH);
            outputName = addLabel(this, Component.empty(), textColor, NAME_X, 8, OUTPUT_NAME_WIDTH);
            styleRow(this);
            role.setActive(false);
            role.setAllowHitTest(false);
            role.setFocusable(false);
            role.noText();
            role.buttonStyle(s -> s.baseTexture(ROLE_BACKGROUND).hoverTexture(ROLE_BACKGROUND)
                    .pressedTexture(ROLE_BACKGROUND));
            addChild(LdlibScreens.at(role, ROLE_X, 4, ROLE_WIDTH, 16));
        }

        private static void renderRoleText(GUIContext context, Component message, float x, float y) {
            var graphics = context.graphics;
            graphics.flush();
            var pose = graphics.pose();
            var origin = pose.last().pose().transformPosition(x, y, 0, new org.joml.Vector3f());
            pose.pushPose();
            try {
                // Native button text clips in screen coordinates, without applying the current pose.
                pose.last().pose().identity();
                pose.translate(0, 0, origin.z());
                int left = (int) origin.x();
                int top = (int) origin.y();
                AE2Button.renderButtonText(graphics, context.mc.font, message,
                        left + 2, top, left + ROLE_WIDTH - 2, top + 16, -1, 0xFF413F54);
            } finally {
                try {
                    graphics.flush();
                } finally {
                    pose.popPose();
                }
            }
        }

        private void update(Font font, int index, ItemStack stack, boolean members, @Nullable Component outputRole) {
            number.setVisible(members);
            memberName.setVisible(members && !stack.isEmpty());
            outputName.setVisible(!members && !stack.isEmpty());
            role.setVisible(outputRole != null);
            if (outputRole != null) {
                roleMessage = outputRole;
            }
            if (members) {
                number.setText(Component.literal(Integer.toString(index + 1)));
                if (!stack.isEmpty()) {
                    memberName.setText(Component.literal(
                            font.plainSubstrByWidth(stack.getHoverName().getString(), MEMBER_NAME_WIDTH)));
                }
            } else if (!stack.isEmpty()) {
                var generic = GenericStack.fromItemStack(stack);
                var name = generic != null && generic.what() != null
                        ? generic.what().getDisplayName() : stack.getHoverName();
                outputName.setText(Component.literal(font.plainSubstrByWidth(name.getString(), OUTPUT_NAME_WIDTH)));
            }
        }
    }

    private void drawResultRows(ClosedLoopResultPage.Kind kind) {
        var resultPage = menu.getClosedLoopResultPage(kind, scrollbar.getCurrentScroll());
        if (resultPage == displayedResultPage) return;
        displayedResultPage = resultPage;
        resultRowsRoot.clearAllChildren();
        if (resultPage != null) {
            for (var entry : resultPage.entries()) {
                resultRowsRoot.addChild(resultRow(font, entry));
            }
        }
    }

    private UIElement resultRow(Font font, GenericStack entry) {
        int textColor = style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB();
        var row = new UIElement();
        styleRow(row);
        var stack = GenericStack.wrapInItemStack(entry);
        String formatted = entry.what().formatAmount(entry.amount(), AmountFormat.SLOT);
        var icon = new UIElement().setAllowHitTest(false).style(s -> s.background(
                (graphics, mouseX, mouseY, x, y, width, height, ticks) -> {
                    graphics.renderItem(stack, (int) x, (int) y);
                    var pose = graphics.pose();
                    pose.pushPose();
                    try {
                        pose.translate(0, 0, 100);
                        LargeStackCountRenderer.renderTerminalLabel(graphics, font, x, y, formatted, false);
                    } finally {
                        pose.popPose();
                    }
                }));
        row.addChild(LdlibScreens.at(icon, SLOT_X, ROW_SLOT_Y_OFFSET, 16, 16));

        var name = Component.literal(font.plainSubstrByWidth(entry.what().getDisplayName().getString(), RESULT_NAME_WIDTH));
        addLabel(row, name, textColor, NAME_X, 8, RESULT_NAME_WIDTH);
        return row;
    }

    private void requestVisibleResultPage() {
        ClosedLoopResultPage.Kind kind = switch (page) {
            case EXTERNAL_INPUTS -> ClosedLoopResultPage.Kind.EXTERNAL_INPUTS;
            case SEEDS -> ClosedLoopResultPage.Kind.SEEDS;
            default -> null;
        };
        if (kind == null) return;
        int offset = scrollbar.getCurrentScroll();
        if (menu.getClosedLoopResultPage(kind, offset) != null) return;
        if (requestedResultKind == kind
                && requestedResultRevision == menu.closedLoopResultRevision
                && requestedResultOffset == offset) return;
        requestedResultKind = kind;
        requestedResultRevision = menu.closedLoopResultRevision;
        requestedResultOffset = offset;
        menu.requestClosedLoopResultPage(kind, offset);
    }

    @Override
    public void drawBG(GuiGraphics graphics, int offsetX, int offsetY,
                       int mouseX, int mouseY, float partialTicks) {
        background.render(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (deltaY != 0 && page != Page.SETTINGS) {
            scrollbar.setCurrentScroll(scrollbar.getCurrentScroll() + (deltaY > 0 ? -1 : 1));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closeEditor();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        closeEditor();
    }

    private void closeEditor() {
        hideSlots(menu.getClosedLoopMemberSlots());
        hideSlots(menu.getClosedLoopOutputSlots());
        returnToParent();
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int x, int y) {
        ClosedLoopResultPage.Kind kind = switch (page) {
            case EXTERNAL_INPUTS -> ClosedLoopResultPage.Kind.EXTERNAL_INPUTS;
            case SEEDS -> ClosedLoopResultPage.Kind.SEEDS;
            default -> null;
        };
        int localX = x - leftPos;
        int relativeY = y - topPos - TianshuPatternConfigLayout.HEADER_HEIGHT - ROW_SLOT_Y_OFFSET;
        int row = relativeY / TianshuPatternConfigLayout.ROW_HEIGHT;
        if (kind != null && localX >= SLOT_X && localX < SLOT_X + 16 && relativeY >= 0
                && row < TianshuPatternConfigLayout.VISIBLE_ROWS
                && relativeY % TianshuPatternConfigLayout.ROW_HEIGHT < 16) {
            var resultPage = menu.getClosedLoopResultPage(kind, scrollbar.getCurrentScroll());
            if (resultPage != null && row < resultPage.entries().size()) {
                graphics.renderTooltip(font, GenericStack.wrapInItemStack(resultPage.entries().get(row)), x, y);
                return;
            }
        }
        super.renderTooltip(graphics, x, y);
    }

    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        var lines = new ArrayList<>(super.getTooltipFromContainerItem(stack));
        int index = hoveredSlot == null ? -1 : menu.getClosedLoopMemberSlots().indexOf(hoveredSlot);
        if (index < 0 || stack.isEmpty()) return lines;
        TianshuClosedLoopEncodingPanel.appendMemberTooltip(lines, menu, index, stack, minecraft.level);
        return lines;
    }

    private void hideParentSlots() {
        for (var semantic : List.of(
                SlotSemantics.CRAFTING_GRID,
                SlotSemantics.CRAFTING_RESULT,
                SlotSemantics.PROCESSING_INPUTS,
                SlotSemantics.PROCESSING_OUTPUTS,
                SlotSemantics.SMITHING_TABLE_TEMPLATE,
                SlotSemantics.SMITHING_TABLE_BASE,
                SlotSemantics.SMITHING_TABLE_ADDITION,
                SlotSemantics.SMITHING_TABLE_RESULT,
                SlotSemantics.STONECUTTING_INPUT,
                SlotSemantics.BLANK_PATTERN,
                SlotSemantics.ENCODED_PATTERN,
                SlotSemantics.PLAYER_INVENTORY,
                SlotSemantics.PLAYER_HOTBAR,
                SlotSemantics.TOOLBOX)) {
            setSlotsHidden(semantic, true);
        }
    }

    private static void hideSlots(List<? extends AppEngSlot> slots) {
        for (var slot : slots) {
            slot.setActive(false);
            slot.x = HIDDEN_SLOT;
            slot.y = HIDDEN_SLOT;
        }
    }

    private int currentRowCount() {
        return switch (page) {
            case MEMBERS -> TianshuPatternEncodingTermMenu.CLOSED_LOOP_MEMBER_SLOTS;
            case OUTPUTS -> TianshuPatternEncodingTermMenu.CLOSED_LOOP_OUTPUT_SLOTS;
            case EXTERNAL_INPUTS -> Math.min(menu.closedLoopExternalInputCount,
                    TianshuPatternEncodingTermMenu.CLOSED_LOOP_RESULT_SLOTS);
            case SEEDS -> Math.min(menu.closedLoopSeedInputCount,
                    TianshuPatternEncodingTermMenu.CLOSED_LOOP_RESULT_SLOTS);
            case SETTINGS -> 1;
        };
    }

    private static boolean isPositiveLongDraft(String value) {
        if (value.isEmpty()) return true;
        try {
            return Long.parseLong(value) >= 1L;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static boolean isPositiveIntDraft(String value) {
        if (value.isEmpty()) return true;
        try {
            return Integer.parseInt(value) >= 1;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static Component pageLabel(Page page) {
        return Component.translatable("ae2lt.tianshu.closed_loop.page."
                + page.name().toLowerCase(Locale.ROOT));
    }

    private static Component roleLabel(int role) {
        return switch (role) {
            case 1 -> Component.translatable("ae2lt.tianshu.closed_loop.role.primary")
                    .withStyle(ChatFormatting.GREEN);
            case 2 -> Component.translatable("ae2lt.tianshu.closed_loop.role.secondary");
            default -> Component.translatable("ae2lt.tianshu.closed_loop.role.none")
                    .withStyle(ChatFormatting.GRAY);
        };
    }

    private int statusColor(ClosedLoopDraftStatus status) {
        return switch (status) {
            case VALID, ENCODED -> 0x228822;
            case EMPTY, NO_CANDIDATE -> style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB();
            case MISSING_PRIMARY_OUTPUT -> 0xAA7700;
            default -> 0xAA3333;
        };
    }

    private enum Page {
        MEMBERS,
        OUTPUTS,
        EXTERNAL_INPUTS,
        SEEDS,
        SETTINGS
    }

}
