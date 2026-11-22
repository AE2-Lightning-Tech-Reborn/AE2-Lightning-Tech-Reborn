package com.moakiee.ae2lt.client.tianshu;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.moakiee.ae2lt.client.ldlib.LdlibHorizontalTab;
import com.moakiee.ae2lt.client.ldlib.LdlibSettingButton;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.moakiee.ae2lt.client.ldlib.LdlibScreens;
import com.moakiee.ae2lt.client.ldlib.LdlibStyleSprite;
import appeng.core.localization.ButtonToolTips;
import appeng.api.config.FuzzyMode;
import appeng.api.config.CopyMode;
import appeng.api.config.Settings;
import appeng.client.gui.Icon;
import appeng.api.config.ActionItems;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import com.moakiee.ae2lt.client.ldlib.LdlibAe2TextField;
import com.moakiee.ae2lt.client.ldlib.LdlibAe2Button;
import com.moakiee.ae2lt.client.ldlib.LdlibAe2Icons;
import com.moakiee.ae2lt.client.ldlib.LdlibRowScroller;
import appeng.core.AEConfig;
import appeng.core.localization.GuiText;
import appeng.core.definitions.AEBlocks;
import appeng.core.network.serverbound.InventoryActionPacket;
import appeng.helpers.InventoryAction;
import com.mojang.blaze3d.platform.InputConstants;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWorkPage;
import com.moakiee.ae2lt.menu.Ae2ltSlotSemantics;
import com.moakiee.ae2lt.menu.TianshuCraftingTermMenu;
import com.moakiee.ae2lt.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ClickType;
import net.neoforged.neoforge.network.PacketDistributor;
import org.anti_ad.mc.ipn.api.IPNIgnore;
import org.lwjgl.glfw.GLFW;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.StonecutterRecipe;

@IPNIgnore
public class TianshuCraftingTermScreen<M extends TianshuCraftingTermMenu> extends TianshuMaintenanceTermScreen<M> {
    private static final LdlibStyleSprite CRAFTING_ATLAS = new LdlibStyleSprite(
            ResourceLocation.parse("ae2:textures/guis/crafting.png"), 256, 256);
    private static final SpriteTexture SMITHING_BACKGROUND =
            TianshuEncodingModePanel.PATTERN_MODES_ATLAS.slice(128, 70, 124, 66);
    private static final SpriteTexture STONE_BACKGROUND =
            TianshuEncodingModePanel.PATTERN_MODES_ATLAS.slice(0, 140, 124, 66);
    private static final SpriteTexture STONE_SCROLLBAR_TRACK =
            TianshuEncodingModePanel.PATTERN_MODES_ATLAS.slice(110, 150, 5, 46);
    private static final SpriteTexture WORK_SLOT = patternSprite(6, 164, 18, 18);
    private static final IGuiTexture CELL_SLOT = LdlibAe2Icons.texture(Icon.SLOT_BACKGROUND);
    private static final IGuiTexture CELL_SLOT_DISABLED = LdlibAe2Icons.sprite(Icon.SLOT_BACKGROUND)
            .setColor(0x33FFFFFF);
    private static final List<SpriteTexture> SMITHING_HINTS = List.of(
            patternSprite(135, 95, 16, 16), patternSprite(153, 95, 16, 16), patternSprite(171, 95, 16, 16));
    private static final SpriteTexture FRAME_INTERIOR = craftingSprite(9, 88, 1, 1);
    private static final SpriteTexture FRAME_TOP = craftingSprite(7, 85, 162, 1);
    private static final SpriteTexture FRAME_SHADOW = craftingSprite(8, 86, 160, 2);
    private static final SpriteTexture FRAME_BOTTOM = craftingSprite(7, 152, 162, 1);
    private static final SpriteTexture FRAME_LEFT = craftingSprite(7, 86, 1, 66);
    private static final SpriteTexture FRAME_RIGHT = craftingSprite(168, 86, 1, 66);
    private static final SpriteTexture WORK_ARROW = craftingSprite(110, 111, 16, 16);
    private static final IGuiTexture WORK_ARROW_DOWN = craftingSprite(110, 111, 16, 16).rotate(90);
    private static final List<SlotSemantic> WORK_SLOTS = List.of(SlotSemantics.CRAFTING_GRID, SlotSemantics.CRAFTING_RESULT,
            Ae2ltSlotSemantics.TIANSHU_SMITHING, Ae2ltSlotSemantics.TIANSHU_ANVIL,
            Ae2ltSlotSemantics.TIANSHU_STONECUTTING, Ae2ltSlotSemantics.TIANSHU_CELL,
            Ae2ltSlotSemantics.TIANSHU_CELL_UPGRADE, Ae2ltSlotSemantics.TIANSHU_CELL_CONFIG);
    private final List<LdlibHorizontalTab> tabs = new ArrayList<>();
    private final List<StoneRecipeFocusTarget> stoneFocusTargets = new ArrayList<>();
    private final LdlibAe2Button clearGrid;
    private final LdlibAe2Button clearToPlayer;
    private final LdlibAe2Button previousCell, nextCell, previousUpgrade, nextUpgrade;
    private final LdlibAe2Button fuzzy;
    private final LdlibAe2Button keepCellConfig;
    private final LdlibRowScroller stoneScrollbar;
    private final LdlibAe2TextField anvilName;
    private final TianshuAnvilCostView anvilCostView;
    private ItemStack observedAnvilInput = ItemStack.EMPTY;
    private ItemStack observedStoneInput = ItemStack.EMPTY;

    public TianshuCraftingTermScreen(M menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        anvilCostView = new TianshuAnvilCostView(menu, inventory);
        for (var page : TianshuWorkPage.values()) {
            var icon = switch (page) {
                case CRAFTING -> new ItemStack(Items.CRAFTING_TABLE);
                case SMITHING -> new ItemStack(Items.SMITHING_TABLE);
                case ANVIL -> new ItemStack(ModBlocks.OVERLOAD_ALLOY_ANVIL.get());
                case STONECUTTING -> new ItemStack(Items.STONECUTTER);
                case CELL -> AEBlocks.CELL_WORKBENCH.stack();
            };
            var label = Component.translatable(page.translationKey());
            if (page == TianshuWorkPage.CELL) label.append("\n").append(Component.translatable("ae2lt.tianshu.work.cell_mark_hint"));
            var tabIcon = switch (page) {
                case CRAFTING -> Icon.TAB_CRAFTING;
                case SMITHING -> Icon.TAB_SMITHING;
                case STONECUTTING -> Icon.TAB_STONECUTTING;
                default -> null;
            };
            var widgetId = "workTab" + page.ordinal();
            var bounds = style.getWidget(widgetId);
            var button = LdlibHorizontalTab.compact(bounds.getWidth(), bounds.getHeight(),
                    icon, tabIcon, label, () -> menu.setWorkPage(page));
            tabs.add(button);
            widgets.add(widgetId, button);
        }
        clearGrid = LdlibAe2Button.small(Icon.S_ARROW_UP, ActionItems.S_STASH,
                () -> menu.clearWorkInputs(false));
        clearToPlayer = LdlibAe2Button.small(Icon.S_ARROW_DOWN, ActionItems.S_STASH_TO_PLAYER_INV,
                () -> menu.clearWorkInputs(true));
        widgets.add("clearCraftingGrid", clearGrid);
        widgets.add("clearToPlayerInv", clearToPlayer);
        previousCell = LdlibAe2Button.small(Icon.S_ARROW_UP,
                Component.translatable("ae2lt.tianshu.work.previous_page"), () -> menu.setCellConfigRow(menu.cellConfigRow - 1));
        nextCell = LdlibAe2Button.small(Icon.S_ARROW_DOWN,
                Component.translatable("ae2lt.tianshu.work.next_page"), () -> menu.setCellConfigRow(menu.cellConfigRow + 1));
        previousUpgrade = LdlibAe2Button.small(Icon.S_ARROW_UP,
                Component.translatable("ae2lt.tianshu.work.previous_page"), () -> menu.setCellUpgradeRow(menu.cellUpgradeRow - 1));
        nextUpgrade = LdlibAe2Button.small(Icon.S_ARROW_DOWN,
                Component.translatable("ae2lt.tianshu.work.next_page"), () -> menu.setCellUpgradeRow(menu.cellUpgradeRow + 1));
        widgets.add("previousCell", previousCell);
        widgets.add("nextCell", nextCell);
        widgets.add("previousUpgrade", previousUpgrade);
        widgets.add("nextUpgrade", nextUpgrade);
        fuzzy = LdlibSettingButton.bareFuzzy(() -> menu.cellFuzzyMode, this::cycleCellFuzzyMode);
        widgets.add("cellFuzzy", fuzzy);
        keepCellConfig = LdlibAe2Button.bareIcon(
                () -> menu.cellCopyMode == CopyMode.CLEAR_ON_REMOVE ? Icon.COPY_MODE_ON : Icon.COPY_MODE_OFF,
                GuiText.CopyMode.text(),
                () -> menu.setCellCopyMode(menu.cellCopyMode == CopyMode.CLEAR_ON_REMOVE
                        ? CopyMode.KEEP_ON_REMOVE : CopyMode.CLEAR_ON_REMOVE),
                () -> List.of(GuiText.CopyMode.text(), GuiText.CopyModeDesc.text(),
                        Component.translatable(menu.cellCopyMode == CopyMode.CLEAR_ON_REMOVE
                                ? "ae2lt.tianshu.work.cell_copy_clear" : "ae2lt.tianshu.work.cell_copy_keep")));
        widgets.add("cellKeepConfig", keepCellConfig);
        var nameBounds = style.getWidget("anvilName");
        anvilName = LdlibAe2TextField.create(nameBounds.getWidth(), nameBounds.getHeight(), style);
        widgets.add("anvilName", anvilName);
        anvilName.setTooltipMessage(List.of(Component.translatable("ae2lt.tianshu.work.anvil_name")));
        anvilName.setMaxLength(50);
        anvilName.setResponder(menu::setAnvilName);
        stoneScrollbar = LdlibRowScroller.add(widgets, "stonecuttingPatternModeScrollbar");
        stoneScrollbar.setCaptureMouseWheel(false);
        for (int i = 0; i < 8; i++) {
            var button = new StoneRecipeFocusTarget(i);
            stoneFocusTargets.add(button);
            widgets.add("stoneRecipe" + i, button);
        }
        stoneScrollbar.setOnScrollChanged(this::refreshStoneFocusTargets);
    }

    static void updateAnvilEditorAvailability(LdlibAe2TextField field, boolean visible, boolean hasInput) {
        field.visible = visible;
        field.active = visible && hasInput;
        if (!field.active && field.isFocused()) {
            field.setFocused(false);
        }
    }

    private int stoneColumns() { return menu.hasCompactWorkArea() ? 3 : 4; }

    @Override protected void slotClicked(Slot slot, int index, int mouseButton, ClickType type) {
        if (menu.isWorkResult(slot)) {
            // Match AEBaseScreen's CraftingTermSlot gestures; vanilla menus still own actual consumption.
            var action = hasShiftDown() ? InventoryAction.CRAFT_SHIFT
                    : InputConstants.isKeyDown(getMinecraft().getWindow().getWindow(), GLFW.GLFW_KEY_SPACE)
                    ? InventoryAction.CRAFT_ALL : mouseButton == 1 ? InventoryAction.CRAFT_STACK : InventoryAction.CRAFT_ITEM;
            PacketDistributor.sendToServer(new InventoryActionPacket(action, index, 0));
        } else super.slotClicked(slot, index, mouseButton, type);
    }

    @Override public List<Rect2i> getExclusionZones() {
        var zones = super.getExclusionZones();
        for (var tab : tabs) {
            if (tab.visible) zones.add(new Rect2i(tab.getX(), tab.getY(), tab.getWidth(), tab.getHeight()));
        }
        return zones;
    }

    @Override protected void updateBeforeRender() {
        super.updateBeforeRender();
        menu.updateSlotAccess();
        setSlotsHidden(Ae2ltSlotSemantics.TIANSHU_GLOBAL_RESERVE_MARK, true);
        var page = menu.workPage;
        boolean compact = menu.hasCompactWorkArea();
        setTextContent("crafting_grid_title", Component.translatable(page == TianshuWorkPage.CRAFTING
                ? "gui.ae2.CraftingTerminal" : page.translationKey()));
        setSlotsHidden(SlotSemantics.CRAFTING_GRID, page != TianshuWorkPage.CRAFTING);
        setSlotsHidden(SlotSemantics.CRAFTING_RESULT, page != TianshuWorkPage.CRAFTING);
        setSlotsHidden(Ae2ltSlotSemantics.TIANSHU_SMITHING, page != TianshuWorkPage.SMITHING);
        setSlotsHidden(Ae2ltSlotSemantics.TIANSHU_ANVIL, page != TianshuWorkPage.ANVIL);
        setSlotsHidden(Ae2ltSlotSemantics.TIANSHU_STONECUTTING, page != TianshuWorkPage.STONECUTTING);
        for (var semantic : List.of(Ae2ltSlotSemantics.TIANSHU_CELL, Ae2ltSlotSemantics.TIANSHU_CELL_CONFIG, Ae2ltSlotSemantics.TIANSHU_CELL_UPGRADE)) {
            setSlotsHidden(semantic, page != TianshuWorkPage.CELL);
        }
        if (page == TianshuWorkPage.SMITHING) positionRow(Ae2ltSlotSemantics.TIANSHU_SMITHING,
                compact ? new int[]{92, 110, 128, 165} : new int[]{15, 33, 51, 109}, imageHeight - (compact ? 142 : 140));
        if (page == TianshuWorkPage.ANVIL) positionRow(Ae2ltSlotSemantics.TIANSHU_ANVIL,
                compact ? new int[]{96, 124, 165} : new int[]{27, 76, 134}, imageHeight - (compact ? 137 : 133));
        if (page == TianshuWorkPage.STONECUTTING) {
            positionRow(Ae2ltSlotSemantics.TIANSHU_STONECUTTING,
                    compact ? new int[]{167, 167} : new int[]{15, 147}, imageHeight - (compact ? 156 : 140));
            if (compact) menu.getSlots(Ae2ltSlotSemantics.TIANSHU_STONECUTTING).getLast().y = imageHeight - 119;
        }
        if (page == TianshuWorkPage.CELL) {
            positionRow(Ae2ltSlotSemantics.TIANSHU_CELL, new int[]{compact ? 92 : 12}, imageHeight - 161);
            var marks = menu.getSlots(Ae2ltSlotSemantics.TIANSHU_CELL_CONFIG);
            for (int i = 0; i < marks.size(); i++) {
                var slot = marks.get(i);
                boolean visible = menu.isCellMarkVisible(i);
                slot.x = visible ? (compact ? 112 : 42) + (i % 3) * 18 : -10000;
                slot.y = visible ? imageHeight - 161 + (i / 3 - menu.cellConfigRow) * 18 : -10000;
            }
            var upgrades = menu.getSlots(Ae2ltSlotSemantics.TIANSHU_CELL_UPGRADE);
            for (int i = 0; i < upgrades.size(); i++) {
                boolean visible = menu.isCellUpgradeVisible(i);
                upgrades.get(i).x = visible ? (compact ? 168 : 119) : -10000;
                upgrades.get(i).y = visible ? imageHeight - 161 + (i - menu.cellUpgradeRow) * 18 : -10000;
            }
        }
        for (int i = 0; i < tabs.size(); i++) tabs.get(i).setSelected(i == page.ordinal());
        clearGrid.visible = clearToPlayer.visible = page != TianshuWorkPage.CELL;
        if (page == TianshuWorkPage.CRAFTING) {
            var bounds = new Rect2i(leftPos, topPos, imageWidth, imageHeight);
            var clearPosition = getStyle().getWidget("clearCraftingGrid").resolve(bounds);
            var playerPosition = getStyle().getWidget("clearToPlayerInv").resolve(bounds);
            clearGrid.setPosition(clearPosition.getX(), clearPosition.getY());
            clearToPlayer.setPosition(playerPosition.getX(), playerPosition.getY());
        } else if (clearGrid.visible) {
            var semantic = switch (page) {
                case SMITHING -> Ae2ltSlotSemantics.TIANSHU_SMITHING;
                case ANVIL -> Ae2ltSlotSemantics.TIANSHU_ANVIL;
                case STONECUTTING -> Ae2ltSlotSemantics.TIANSHU_STONECUTTING;
                default -> throw new IllegalStateException("No work slots for " + page);
            };
            var slots = menu.getSlots(semantic);
            // Two native 8px icons with a 2px gap span exactly one 18px slot frame.
            // The compact stonecutter puts its input above its output in the same column.
            var anchor = compact && page == TianshuWorkPage.STONECUTTING ? slots.getFirst() : slots.getLast();
            int clearX = leftPos + anchor.x - 1;
            int clearY = topPos + anchor.y - 10;
            clearGrid.setPosition(clearX, clearY);
            clearToPlayer.setPosition(clearX + 10, clearY);
        }
        previousCell.visible = nextCell.visible = page == TianshuWorkPage.CELL && menu.getMaxCellConfigRow() > 0;
        previousCell.active = menu.cellConfigRow > 0;
        nextCell.active = menu.cellConfigRow < menu.getMaxCellConfigRow();
        previousUpgrade.visible = nextUpgrade.visible = page == TianshuWorkPage.CELL && menu.getMaxCellUpgradeRow() > 0;
        previousUpgrade.active = menu.cellUpgradeRow > 0;
        nextUpgrade.active = menu.cellUpgradeRow < menu.getMaxCellUpgradeRow();
        fuzzy.setVisibility(page == TianshuWorkPage.CELL);
        fuzzy.active = !menu.getCell().isEmpty();
        keepCellConfig.setVisibility(page == TianshuWorkPage.CELL);
        updateAnvilEditorAvailability(anvilName, page == TianshuWorkPage.ANVIL,
                !menu.getAnvilInput().isEmpty());
        boolean inputChanged = !ItemStack.matches(observedAnvilInput, menu.getAnvilInput());
        if (inputChanged) observedAnvilInput = menu.getAnvilInput().copy();
        if ((inputChanged || !anvilName.isFocused()) && !anvilName.getValue().equals(menu.anvilItemName)) {
            // Synchronizing a reopened editor must not send the old input name back over a saved rename.
            anvilName.setResponder(value -> {});
            anvilName.setValue(menu.anvilItemName);
            anvilName.setResponder(menu::setAnvilName);
        }
        updateStonecutting(page == TianshuWorkPage.STONECUTTING);
    }

    private void updateStonecutting(boolean visible) {
        var stonecutter = menu.getStonecutter();
        var stoneInput = stonecutter.getSlot(0).getItem();
        if (!ItemStack.isSameItemSameComponents(observedStoneInput, stoneInput)) {
            observedStoneInput = stoneInput.copy();
            stoneScrollbar.setCurrentScroll(0);
        }
        int columns = stoneColumns();
        int totalRows = (stonecutter.getNumRecipes() + columns - 1) / columns;
        int visibleRows = TianshuStonecuttingRecipeGrid.VISIBLE_ROWS;
        stoneScrollbar.setRange(0, totalRows - visibleRows, visibleRows);
        stoneScrollbar.setVisible(visible);
        refreshStoneFocusTargets();
    }

    private void refreshStoneFocusTargets() {
        boolean stonecutting = menu.workPage == TianshuWorkPage.STONECUTTING;
        var recipes = menu.getStonecutter().getRecipes();
        for (var target : stoneFocusTargets) {
            int index = stonecutting ? target.currentRecipeIndex() : -1;
            target.bind(index < 0 ? null : recipes.get(index));
        }
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (menu.workPage == TianshuWorkPage.CELL && vertical != 0) {
            int delta = -(int) Math.signum(vertical);
            boolean compact = menu.hasCompactWorkArea();
            if (new Rect2i(leftPos + (compact ? 111 : 41), topPos + imageHeight - 162, 54, 54).contains((int) x, (int) y)) {
                menu.setCellConfigRow(menu.cellConfigRow + delta);
                return true;
            }
            if (new Rect2i(leftPos + (compact ? 167 : 118), topPos + imageHeight - 162, 18, 54).contains((int) x, (int) y)) {
                menu.setCellUpgradeRow(menu.cellUpgradeRow + delta);
                return true;
            }
        }
        if (menu.workPage == TianshuWorkPage.STONECUTTING && vertical != 0) {
            var recipes = menu.hasCompactWorkArea()
                    ? new Rect2i(leftPos + 93, topPos + imageHeight - 157, 72, 48)
                    : new Rect2i(leftPos + 33, topPos + imageHeight - 155, 93, 46);
            if (recipes.contains((int) x, (int) y)) {
                stoneScrollbar.setCurrentScroll(stoneScrollbar.getCurrentScroll() - (int) Math.signum(vertical));
                return true;
            }
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    private void positionRow(SlotSemantic semantic, int[] xs, int y) {
        var slots = menu.getSlots(semantic);
        for (int i = 0; i < slots.size(); i++) { slots.get(i).x = xs[i]; slots.get(i).y = y; }
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (handleMaintenanceClick(button)) return true;
        if (menu.workPage == TianshuWorkPage.CELL && hasControlDown()
                && button == org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_MIDDLE
                && getSlotUnderMouse() instanceof appeng.client.gui.me.common.RepoSlot repoSlot) {
            var entry = repoSlot.getEntry();
            if (entry != null && entry.getWhat() != null) {
                var mark = appeng.api.stacks.GenericStack.wrapInItemStack(entry.getWhat(), 1);
                for (var slot : menu.getSlots(Ae2ltSlotSemantics.TIANSHU_CELL_CONFIG)) {
                    if (slot instanceof appeng.menu.slot.FakeSlot fake && fake.isSlotEnabled()
                            && fake.getItem().isEmpty() && fake.canSetFilterTo(mark)) {
                        fake.setFilterTo(mark);
                        break;
                    }
                }
            }
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override public void drawBG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
        super.drawBG(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        // The terminal style already supplies the crafting page's arrow and result frame.
        if (menu.workPage == TianshuWorkPage.CRAFTING) return;
        workAreaSpriteCount = 0;
        layoutWorkArea();
        var root = workArea.ui.rootElement;
        for (int i = workAreaSpriteCount; i < root.getChildren().size(); i++) {
            root.getChildren().get(i).setVisible(false);
        }
        var pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(offsetX, offsetY, 0);
            workArea.getWidget().render(graphics, mouseX, mouseY, partialTicks);
        } finally {
            pose.popPose();
        }
    }

    private void layoutWorkArea() {
        var frame = workAreaBounds();
        workAreaFrame(frame.getX(), frame.getY(), frame.getWidth(), frame.getHeight());
        boolean compact = menu.hasCompactWorkArea();
        if (menu.workPage == TianshuWorkPage.SMITHING && !compact) {
            workAreaSprite(SMITHING_BACKGROUND, 8, imageHeight - 165, 124, 66);
            return;
        }
        if (menu.workPage == TianshuWorkPage.STONECUTTING && !compact) {
            workAreaSprite(STONE_BACKGROUND, 8, imageHeight - 165, 124, 66);
        }
        if (compact && menu.workPage == TianshuWorkPage.STONECUTTING) {
            workAreaFrame(93, imageHeight - 157, 72, 48);
            // Match AE2's baked track: the 7px handle overhangs the 5px groove by one pixel on each side.
            var track = stoneScrollbar.getBounds();
            workAreaSprite(STONE_SCROLLBAR_TRACK, track.getX() + 1, track.getY() - 1,
                    5, track.getHeight() + 2);
            workAreaSprite(WORK_ARROW_DOWN, 167, imageHeight - 138, 16, 16);
        }
        if (menu.workPage == TianshuWorkPage.CELL) {
            cellSlot(compact ? 92 : 12, imageHeight - 143, fuzzy.active);
            workSlot(compact ? 92 : 12, imageHeight - 125);
            for (int i = 0; i < 9; i++) {
                cellSlot((compact ? 112 : 42) + i % 3 * 18, imageHeight - 161 + i / 3 * 18,
                        menu.isCellMarkVisible(menu.cellConfigRow * 3 + i));
            }
            for (int i = 0; i < 3; i++) {
                cellSlot(compact ? 168 : 119, imageHeight - 161 + i * 18,
                        menu.isCellUpgradeVisible(menu.cellUpgradeRow + i));
            }
        }
        for (var semantic : WORK_SLOTS) {
            if (semantic == Ae2ltSlotSemantics.TIANSHU_CELL_CONFIG || semantic == Ae2ltSlotSemantics.TIANSHU_CELL_UPGRADE) continue;
            for (var slot : menu.getSlots(semantic)) {
                if (slot.x < 0 || slot.y < 0 || !slot.isActive()) continue;
                workSlot(slot.x, slot.y);
            }
        }
        if (compact && menu.workPage == TianshuWorkPage.SMITHING) {
            // Keep the native template/base/addition empty-slot hints at full icon size.
            var smithingSlots = menu.getSlots(Ae2ltSlotSemantics.TIANSHU_SMITHING);
            for (int i = 0; i < 3; i++) {
                var slot = smithingSlots.get(i);
                workAreaSprite(SMITHING_HINTS.get(i), slot.x, slot.y, 16, 16);
            }
            workAreaSprite(WORK_ARROW, 146, imageHeight - 142, 16, 16);
        } else if (menu.workPage == TianshuWorkPage.ANVIL) {
            workAreaSprite(WORK_ARROW, compact ? 146 : 106, imageHeight - (compact ? 137 : 133), 16, 16);
        }
    }

    protected Rect2i workAreaBounds() {
        return new Rect2i(7, imageHeight - 166, 162, 68);
    }

    private void workAreaFrame(int x, int y, int width, int height) {
        // Reuse the terminal's frame and panel texture, including changes supplied by resource packs.
        workAreaSprite(FRAME_INTERIOR, x + 1, y + 1, width - 2, height - 2);
        workAreaSprite(FRAME_TOP, x, y, width, 1);
        // The native inset has a two-pixel shadow immediately below its white top edge.
        workAreaSprite(FRAME_SHADOW, x + 1, y + 1, width - 2, 2);
        workAreaSprite(FRAME_BOTTOM, x, y + height - 1, width, 1);
        workAreaSprite(FRAME_LEFT, x, y + 1, 1, height - 2);
        workAreaSprite(FRAME_RIGHT, x + width - 1, y + 1, 1, height - 2);
    }

    private void workAreaSprite(IGuiTexture texture, int x, int y, int width, int height) {
        var root = workArea.ui.rootElement;
        int index = workAreaSpriteCount++;
        UIElement sprite;
        if (index == root.getChildren().size()) {
            sprite = new UIElement().setAllowHitTest(false);
            root.addChild(sprite);
        } else {
            sprite = root.getChildren().get(index);
        }
        sprite.setVisible(true);
        if (sprite.getStyle().backgroundTexture() != texture) {
            sprite.style(style -> style.background(texture));
        }
        LdlibScreens.at(sprite, x, y, width, height);
    }

    private static SpriteTexture patternSprite(int x, int y, int width, int height) {
        return TianshuEncodingModePanel.PATTERN_MODES_ATLAS.slice(x, y, width, height);
    }

    private static SpriteTexture craftingSprite(int x, int y, int width, int height) {
        return CRAFTING_ATLAS.slice(x, y, width, height);
    }

    private void workSlot(int x, int y) {
        // Use AE2's standalone slot; a grid cell includes its neighbours' shared border colors.
        workAreaSprite(WORK_SLOT, x - 1, y - 1, 18, 18);
    }

    private void cellSlot(int x, int y, boolean enabled) {
        // Same optional-slot texture and disabled opacity used by AE2's cell workbench.
        workAreaSprite(enabled ? CELL_SLOT : CELL_SLOT_DISABLED, x - 1, y - 1,
                Icon.SLOT_BACKGROUND.width, Icon.SLOT_BACKGROUND.height);
    }

    private ModularUI workArea;
    private int workAreaSpriteCount;
    private ModularUI workPageLabels;
    private Label cellRange;
    private Label anvilPlus;

    @Override
    public void init() {
        super.init();
        workArea = LdlibScreens.localLayer(new UIElement(), imageWidth, imageHeight);
        workArea.setDrawTooltips(false);
        var root = new UIElement();
        int textColor = getStyle().getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB();
        cellRange = LdlibScreens.label(Component.empty(), textColor);
        cellRange.setAllowHitTest(false);
        cellRange.textStyle(style -> style.textAlignHorizontal(Horizontal.CENTER).textWrap(TextWrap.NONE));
        root.addChild(LdlibScreens.at(cellRange, 0, 0, 0, 9));
        anvilPlus = LdlibScreens.label(Component.literal("+"), textColor);
        anvilPlus.setAllowHitTest(false);
        boolean compact = menu.hasCompactWorkArea();
        root.addChild(LdlibScreens.at(anvilPlus, compact ? 115 : 58,
                imageHeight - (compact ? 133 : 129), font.width("+") + 1, 9));
        workPageLabels = LdlibScreens.localLayer(root, imageWidth, imageHeight);
    }

    @Override public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
        boolean compact = menu.hasCompactWorkArea();
        boolean showRange = menu.workPage == TianshuWorkPage.CELL && menu.getMaxCellConfigRow() > 0;
        boolean showAnvil = menu.workPage == TianshuWorkPage.ANVIL;
        cellRange.setVisible(showRange);
        anvilPlus.setVisible(showAnvil);
        if (showRange) {
            var rows = (menu.cellConfigRow + 1) + "–" + Math.min(menu.cellConfigRow + 3, menu.getCellConfigRows());
            int left = previousCell.getX() + previousCell.getWidth() - leftPos;
            int right = nextCell.getX() - leftPos;
            int textY = previousCell.getY() - topPos + (previousCell.getHeight() - font.lineHeight) / 2;
            cellRange.setText(Component.literal(rows));
            LdlibScreens.at(cellRange, left, textY, Math.max(1, right - left), 9);
        } else if (showAnvil) {
            // The anvil cost line is vanilla AnvilScreen label rendering, reused as-is.
            anvilCostView.renderCost(graphics, compact ? 92 : 8, imageHeight - 109, compact ? 93 : 160, mouseX, mouseY);
        }
        workPageLabels.getWidget().render(graphics, mouseX, mouseY, 0);
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (anvilName.visible && anvilName.active && anvilName.isFocused() && key != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            anvilName.keyPressed(key, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override public boolean charTyped(char character, int modifiers) {
        // MEStorageScreen consumes spaces while its search is empty; anvil names have their own editor.
        if (anvilName.visible && anvilName.active && anvilName.isFocused()) return anvilName.charTyped(character, modifiers);
        return super.charTyped(character, modifiers);
    }

    @Override public void onClose() {
        if (AEConfig.instance().isClearGridOnClose()) {
            // AE2's close preference applies to its original 3x3 grid, even while another page is shown.
            menu.setWorkPage(TianshuWorkPage.CRAFTING);
            menu.clearCraftingGrid();
        }
        super.onClose();
    }

    private ItemStack stoneResult(RecipeHolder<StonecutterRecipe> recipe) {
        return recipe.value().getResultItem(menu.getPlayer().registryAccess());
    }

    /** The AE2 style positions each recipe button, with the same LDLib cell used by the encoding panel. */
    private final class StoneRecipeFocusTarget extends AbstractButton {
        private final int position;
        private RecipeHolder<StonecutterRecipe> recipe;
        private ModularUI cellUi;

        private StoneRecipeFocusTarget(int position) {
            super(0, 0, TianshuStonecuttingRecipeGrid.CELL_WIDTH, TianshuStonecuttingRecipeGrid.CELL_HEIGHT,
                    Component.empty());
            this.position = position;
            visible = false;
        }

        private void bind(RecipeHolder<StonecutterRecipe> next) {
            visible = next != null;
            if (!visible && TianshuCraftingTermScreen.this.getFocused() == this) {
                TianshuCraftingTermScreen.this.setFocused(null);
            }
            if (!Objects.equals(recipe, next)) {
                recipe = next;
                var name = next == null ? Component.empty() : stoneResult(next).getHoverName();
                setMessage(name);
                setTooltip(next == null ? null : Tooltip.create(name));
            }
        }

        private int currentRecipeIndex() {
            int columns = stoneColumns();
            int index = stoneScrollbar.getCurrentScroll() * columns + position;
            return position < columns * TianshuStonecuttingRecipeGrid.VISIBLE_ROWS
                    && index < menu.getStonecutter().getNumRecipes() ? index : -1;
        }

        @Override
        public boolean mouseClicked(double x, double y, int button) {
            return menu.workPage == TianshuWorkPage.STONECUTTING && currentRecipeIndex() >= 0
                    && super.mouseClicked(x, y, button);
        }

        @Override
        public void onPress() {
            if (visible && menu.workPage == TianshuWorkPage.STONECUTTING) {
                int index = currentRecipeIndex();
                if (index >= 0) {
                    menu.selectStoneRecipe(index);
                }
            }
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (recipe == null) return;
            if (cellUi == null) {
                var cell = new TianshuStonecuttingRecipeGrid.Cell(() -> stoneResult(recipe),
                        () -> currentRecipeIndex() == menu.stoneRecipe, this::isHovered);
                cellUi = LdlibScreens.localLayer(cell, TianshuStonecuttingRecipeGrid.CELL_WIDTH,
                        TianshuStonecuttingRecipeGrid.CELL_HEIGHT);
                cellUi.setDrawTooltips(false);
            }
            var pose = graphics.pose();
            pose.pushPose();
            try {
                pose.translate(getX(), getY(), 0);
                cellUi.getWidget().render(graphics, mouseX, mouseY, partialTick);
            } finally {
                pose.popPose();
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private void cycleCellFuzzyMode() {
        List<FuzzyMode> modes = List.copyOf(Settings.FUZZY_MODE.getValues());
        int step = isHandlingRightClick() ? -1 : 1;
        int next = Math.floorMod(modes.indexOf(menu.cellFuzzyMode) + step, modes.size());
        menu.setCellFuzzyMode(modes.get(next));
    }
}
