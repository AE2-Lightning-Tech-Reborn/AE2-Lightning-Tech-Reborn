package com.moakiee.ae2lt.client.machine;

import appeng.client.gui.style.PaletteColor;
import static com.moakiee.ae2lt.client.ldlib.LdlibScreens.at;

import java.util.ArrayList;
import java.util.List;

import com.lowdragmc.lowdraglib2.gui.LDLibFonts;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.FillDirection;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;
import com.lowdragmc.lowdraglib2.gui.ui.elements.VirtualScrollerView;
import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.client.gui.FittingText;
import com.moakiee.ae2lt.client.ldlib.LdlibScreens;
import com.moakiee.ae2lt.client.ldlib.LdlibStyleSprite;
import com.moakiee.ae2lt.menu.OverloadDeviceWorkbenchMenu;
import com.moakiee.ae2lt.client.gui.AE2GuiPalette;

/**
 * Overload device workbench with an LDLib module list and scrollbar, and native inventory slots.
 */
public class OverloadDeviceWorkbenchScreen extends AbstractContainerScreen<OverloadDeviceWorkbenchMenu> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            AE2LightningTech.MODID, "textures/gui/overload_workplace_gui.png");
    private static final LdlibStyleSprite ATLAS = new LdlibStyleSprite(TEXTURE, 320, 256);

    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 245;

    private static final int TEXT_ON_DARK_BG = 0xFFFFFFFF;

    private static final int STATUS_X = 44;
    private static final int STATUS_Y = 22;
    private static final int STATUS_SECOND_LINE_Y = 14;

    private static final int MODULE_HEADER_X = 42;
    private static final int MODULE_HEADER_Y = 49;
    private static final int MODULE_ROW_X = 42;
    private static final int MODULE_ROW_Y = 60;
    private static final int MODULE_ROW_WIDTH = 118;
    private static final int MODULE_ROW_HEIGHT = 17;
    private static final int MODULE_ICON_X = 44;
    private static final int MODULE_NAME_X = 67;
    private static final int MODULE_TEXT_Y_OFFSET = 5;
    private static final int VISIBLE_ROWS = 5;
    private static final int MODULE_ROW_SRC_X = 180;
    private static final int MODULE_ROW_SRC_Y = 90;
    private static final int MODULE_ROW_SELECTED_SRC_Y = 111;

    private static final int REMOVE_BUTTON_SIZE = 10;
    private static final int REMOVE_BUTTON_X = 148;
    private static final int REMOVE_BUTTON_Y_OFFSET = 3;
    private static final int REMOVE_BUTTON_SRC_X = 191;
    private static final int REMOVE_BUTTON_SRC_Y = 5;
    private static final int REMOVE_BUTTON_HOVER_SRC_X = 202;
    private static final int REMOVE_BUTTON_HOVER_SRC_Y = 6;
    private static final int REMOVE_BUTTON_WIDTH = 9;
    private static final int REMOVE_BUTTON_HEIGHT = 10;
    private static final int REMOVE_BUTTON_HOVER_HEIGHT = 9;

    private static final int SCROLLBAR_X = 164;
    private static final int SCROLLBAR_Y = 61;
    private static final int SCROLLBAR_WIDTH = 7;
    private static final int SCROLLBAR_HEIGHT = 82;
    private static final int SCROLLBAR_THUMB_SRC_X = 180;
    private static final int SCROLLBAR_THUMB_SRC_Y = 0;
    private static final int SCROLLBAR_THUMB_HOVER_SRC_Y = 17;
    private static final int SCROLLBAR_THUMB_HEIGHT = 15;
    private static final int SCROLLBAR_THUMB_HOVER_HEIGHT = 14;

    private static final int ARROW_PROGRESS_X = 8;
    private static final int ARROW_PROGRESS_Y = 97;
    private static final int ARROW_PROGRESS_WIDTH = 28;
    private static final int ARROW_PROGRESS_HEIGHT = 38;
    private static final int ARROW_PROGRESS_SRC_X = 180;
    private static final int ARROW_PROGRESS_SRC_Y = 48;
    private static final int ARROW_PROGRESS_VISIBLE_OFFSET_X = 9;
    private static final int ARROW_PROGRESS_VISIBLE_WIDTH = 16;
    private static final IGuiTexture[] INSTALL_PROGRESS = installProgressSlices();

    private static final IGuiTexture ROW = tex(MODULE_ROW_SRC_X, MODULE_ROW_SRC_Y, MODULE_ROW_WIDTH, MODULE_ROW_HEIGHT);
    private static final IGuiTexture ROW_SELECTED = tex(
            MODULE_ROW_SRC_X, MODULE_ROW_SELECTED_SRC_Y, MODULE_ROW_WIDTH, MODULE_ROW_HEIGHT);
    private static final IGuiTexture REMOVE = tex(
            REMOVE_BUTTON_SRC_X, REMOVE_BUTTON_SRC_Y, REMOVE_BUTTON_WIDTH, REMOVE_BUTTON_HEIGHT);
    private static final IGuiTexture REMOVE_HOVER = tex(
            REMOVE_BUTTON_HOVER_SRC_X, REMOVE_BUTTON_HOVER_SRC_Y, REMOVE_BUTTON_WIDTH, REMOVE_BUTTON_HOVER_HEIGHT);
    private static final IGuiTexture THUMB = tex(
            SCROLLBAR_THUMB_SRC_X, SCROLLBAR_THUMB_SRC_Y, SCROLLBAR_WIDTH, SCROLLBAR_THUMB_HEIGHT);
    private static final IGuiTexture THUMB_HOVER = tex(
            SCROLLBAR_THUMB_SRC_X, SCROLLBAR_THUMB_HOVER_SRC_Y, SCROLLBAR_WIDTH, SCROLLBAR_THUMB_HOVER_HEIGHT);

    private ModularUI backgroundUI;
    private Label statusLine;
    private Label networkLine;
    private Label noDeviceLine;
    private Label header;
    private Scroller scroller;
    private VirtualScrollerView<InstalledModule> moduleList;
    private ProgressBar installProgress;
    private int scrollOffset;
    private List<InstalledModule> modules = List.of();

    public OverloadDeviceWorkbenchScreen(OverloadDeviceWorkbenchMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
        this.inventoryLabelX = OverloadDeviceWorkbenchMenu.INVENTORY_X;
        this.inventoryLabelY = OverloadDeviceWorkbenchMenu.INVENTORY_Y - 10;
    }

    @Override
    protected void init() {
        super.init();
        releaseBackgroundUI();
        modules = List.of();
        backgroundUI = LdlibScreens.localLayer(buildRoot(), imageWidth, imageHeight);
        backgroundUI.setDrawTooltips(false);
        refresh();
    }

    @Override
    public void removed() {
        try {
            super.removed();
        } finally {
            releaseBackgroundUI();
        }
    }

    private void releaseBackgroundUI() {
        if (backgroundUI != null) {
            if (!backgroundUI.isRemoved()) {
                backgroundUI.onRemoved();
            }
            backgroundUI = null;
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refresh();
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        refresh();
        installProgress.setProgress(installProgressPixels());
        var pose = gfx.pose();
        pose.pushPose();
        try {
            pose.translate(leftPos, topPos, 0);
            backgroundUI.getWidget().render(gfx, mouseX, mouseY, partialTick);
        } finally {
            pose.popPose();
        }
    }

    @Override
    protected void renderLabels(GuiGraphics gfx, int mouseX, int mouseY) {
        gfx.drawString(font, Component.translatable("block.ae2lt.overload_device_workbench"),
                42, 6, AE2GuiPalette.color(PaletteColor.DEFAULT_TEXT_COLOR), false);
        gfx.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY,
                AE2GuiPalette.color(PaletteColor.DEFAULT_TEXT_COLOR), false);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        renderBackground(gfx, mouseX, mouseY, partialTick);
        super.render(gfx, mouseX, mouseY, partialTick);
        renderModuleRowTooltip(gfx, mouseX, mouseY);
        renderTooltip(gfx, mouseX, mouseY);
    }

    private void renderModuleRowTooltip(GuiGraphics gfx, int mouseX, int mouseY) {
        var currentModules = menu.getInstalledModuleList();
        int listLeft = leftPos + MODULE_ROW_X;
        int listTop = topPos + MODULE_ROW_Y;
        int listRight = listLeft + MODULE_ROW_WIDTH;
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int moduleIndex = scrollOffset + row;
            if (moduleIndex >= currentModules.size()) {
                break;
            }
            int rowY = listTop + row * MODULE_ROW_HEIGHT;
            int buttonX = leftPos + REMOVE_BUTTON_X;
            int buttonY = rowY + REMOVE_BUTTON_Y_OFFSET;
            if (mouseX >= buttonX && mouseX < buttonX + REMOVE_BUTTON_SIZE
                    && mouseY >= buttonY && mouseY < buttonY + REMOVE_BUTTON_SIZE) {
                gfx.renderComponentTooltip(font, List.of(
                        Component.translatable("ae2lt.overload_device_workbench.screen.uninstall_one"),
                        Component.translatable("ae2lt.overload_device_workbench.screen.uninstall_all")),
                        mouseX, mouseY);
                return;
            }
            if (mouseX >= listLeft && mouseX < listRight
                    && mouseY >= rowY && mouseY < rowY + MODULE_ROW_HEIGHT) {
                gfx.renderTooltip(font, currentModules.get(moduleIndex), mouseX, mouseY);
                return;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            refreshModules();
            int row = removeRowAt(mouseX - leftPos, mouseY - topPos);
            int index = scrollOffset + row;
            if (row >= 0 && index < modules.size()) {
                var current = menu.getInstalledModuleList();
                // The menu may have received a new list since the visible rows were prepared.
                if (index < current.size() && ItemStack.isSameItemSameComponents(
                        modules.get(index).stack(), current.get(index))) {
                    menu.requestUninstall(index, hasShiftDown());
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static int removeRowAt(double x, double y) {
        double rowY = y - MODULE_ROW_Y;
        if (x < REMOVE_BUTTON_X || x >= REMOVE_BUTTON_X + REMOVE_BUTTON_SIZE
                || rowY < 0 || rowY >= MODULE_ROW_HEIGHT * VISIBLE_ROWS) {
            return -1;
        }
        int row = (int) (rowY / MODULE_ROW_HEIGHT);
        double buttonY = row * MODULE_ROW_HEIGHT + REMOVE_BUTTON_Y_OFFSET;
        return rowY >= buttonY && rowY < buttonY + REMOVE_BUTTON_SIZE ? row : -1;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double x = mouseX - leftPos;
        double y = mouseY - topPos;
        if (x >= MODULE_ROW_X && x < SCROLLBAR_X + SCROLLBAR_WIDTH
                && y >= MODULE_ROW_Y && y < MODULE_ROW_Y + MODULE_ROW_HEIGHT * VISIBLE_ROWS) {
            refreshModules();
            setScroll(scrollOffset - (int) Math.signum(scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // ---- LDLib tree ---------------------------------------------------------------------

    private UIElement buildRoot() {
        var root = new UIElement();
        root.layout(l -> l.width(GUI_WIDTH).height(GUI_HEIGHT));
        root.style(s -> s.background(tex(0, 0, GUI_WIDTH, GUI_HEIGHT)));

        noDeviceLine = at(label(Component.translatable("ae2lt.overload_device_workbench.status.no_device"),
                TEXT_ON_DARK_BG), STATUS_X, STATUS_Y + 7, GUI_WIDTH - STATUS_X - 8, 9);
        statusLine = at(label(Component.empty(), TEXT_ON_DARK_BG), STATUS_X, STATUS_Y, GUI_WIDTH - STATUS_X - 8, 9);
        networkLine = at(label(Component.empty(), TEXT_ON_DARK_BG),
                STATUS_X, STATUS_Y + STATUS_SECOND_LINE_Y, GUI_WIDTH - STATUS_X - 8, 9);
        root.addChildren(noDeviceLine, statusLine, networkLine);

        installProgress = LdlibScreens.spriteProgress(FillDirection.LEFT_TO_RIGHT, INSTALL_PROGRESS);
        root.addChild(at(installProgress,
                ARROW_PROGRESS_X, ARROW_PROGRESS_Y, ARROW_PROGRESS_WIDTH, ARROW_PROGRESS_HEIGHT));

        header = at(label(Component.empty(),
                AE2GuiPalette.color(PaletteColor.DEFAULT_TEXT_COLOR)),
                MODULE_HEADER_X, MODULE_HEADER_Y, MODULE_ROW_WIDTH, 9);
        root.addChild(header);

        moduleList = LdlibScreens.virtualRows(MODULE_ROW_HEIGHT, ModuleRow::new);
        // The native five-row loop has no scissor; keep overhanging item models and text intact.
        moduleList.style(style -> style.overflowVisible(true));
        moduleList.verticalContainer.style(style -> style.overflowVisible(true));
        moduleList.viewPort(view -> view.style(style -> style.overflowVisible(true)));
        moduleList.viewContainer.style(style -> style.overflowVisible(true));
        root.addChild(at(moduleList, MODULE_ROW_X, MODULE_ROW_Y, MODULE_ROW_WIDTH, MODULE_ROW_HEIGHT * VISIBLE_ROWS));

        scroller = LdlibScreens.displayThumbScroller(THUMB, THUMB_HOVER, SCROLLBAR_THUMB_HEIGHT, SCROLLBAR_HEIGHT);
        root.addChild(at(scroller, SCROLLBAR_X, SCROLLBAR_Y, SCROLLBAR_WIDTH, SCROLLBAR_HEIGHT));
        return root;
    }

    private int installProgressPixels() {
        if (menu.installProgress <= 0) {
            return 0;
        }
        double ratio = (double) menu.installProgress / OverloadDeviceWorkbenchMenu.INSTALL_TICKS;
        int visible = Math.max(1, (int) Math.ceil(ARROW_PROGRESS_VISIBLE_WIDTH * ratio));
        return Math.min(ARROW_PROGRESS_WIDTH, ARROW_PROGRESS_VISIBLE_OFFSET_X + visible);
    }

    private void refresh() {
        if (scroller == null) {
            return;
        }
        boolean device = menu.hasDeviceInserted();
        noDeviceLine.setDisplay(!device);
        statusLine.setDisplay(device);
        networkLine.setDisplay(device);
        if (device) {
            statusLine.setText(menu.getStatusText());
            networkLine.setText(menu.gridConnected != 0
                    ? Component.translatable("ae2lt.overload_device_workbench.screen.network.online")
                    : Component.translatable("ae2lt.overload_device_workbench.screen.network.offline"));
        }

        refreshModules();
        header.setText(Component.translatable("ae2lt.overload_device_workbench.screen.module_types", modules.size()));
        header.textStyle(style -> style.textColor(AE2GuiPalette.color(PaletteColor.DEFAULT_TEXT_COLOR)));
        scroller.setDisplay(modules.size() > VISIBLE_ROWS);
        setScroll(scrollOffset);
    }

    private void setScroll(int offset) {
        int max = maxScroll();
        scrollOffset = Mth.clamp(offset, 0, max);
        LdlibScreens.syncRowThumb(scroller, scrollOffset, max);
        LdlibScreens.positionThumbPixels(scroller,
                (SCROLLBAR_HEIGHT - SCROLLBAR_THUMB_HEIGHT) * scrollOffset / Math.max(1, max),
                SCROLLBAR_THUMB_HEIGHT);
        LdlibScreens.scrollVirtualRows(moduleList, scrollOffset, max);
    }

    private void refreshModules() {
        var stacks = menu.getInstalledModuleList();
        var updated = new ArrayList<InstalledModule>(stacks.size());
        boolean changed = stacks.size() != modules.size();
        for (int index = 0; index < stacks.size(); index++) {
            var stack = stacks.get(index);
            int cap = menu.getModuleMaxInstallAmount(stack);
            if (index < modules.size() && modules.get(index).matches(stack, cap)) {
                updated.add(modules.get(index));
            } else {
                updated.add(new InstalledModule(stack.copy(), cap));
                changed = true;
            }
        }
        if (changed) {
            modules = List.copyOf(updated);
            moduleList.setItems(modules);
        }
    }

    private int maxScroll() {
        return Math.max(0, modules.size() - VISIBLE_ROWS);
    }

    private static Label label(Component text, int color) {
        var label = LdlibScreens.label(text, color);
        label.textStyle(s -> s.textWrap(com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap.NONE));
        return label;
    }

    private static IGuiTexture tex(int u, int v, int width, int height) {
        // Keep the native fixed pixel size, inherited render state, and immediate submission.
        return (graphics, mouseX, mouseY, x, y, boxWidth, boxHeight, ticks) ->
                ATLAS.draw(graphics, (int) x, (int) y, u, v, width, height);
    }

    private static IGuiTexture[] installProgressSlices() {
        var slices = new IGuiTexture[ARROW_PROGRESS_WIDTH + 1];
        slices[0] = IGuiTexture.EMPTY;
        for (int pixels = 1; pixels <= ARROW_PROGRESS_WIDTH; pixels++) {
            slices[pixels] = tex(ARROW_PROGRESS_SRC_X, ARROW_PROGRESS_SRC_Y, pixels, ARROW_PROGRESS_HEIGHT);
        }
        return slices;
    }

    private record InstalledModule(ItemStack stack, int cap) {
        private boolean matches(ItemStack other, int otherCap) {
            return cap == otherCap && ItemStack.matches(stack, other);
        }

        private String amount() {
            String count = "x" + stack.getCount();
            return cap > 0 ? count + "/" + cap : count;
        }
    }

    private static boolean within(float mouseX, float mouseY, float x, float y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    /** One installed-module row, preserving the native frame/icon/amount/name/button order. */
    private final class ModuleRow extends UIElement {
        private ModuleRow(InstalledModule module) {
            layout(l -> l.widthPercent(100).height(MODULE_ROW_HEIGHT));
            style(s -> s.background((graphics, mouseX, mouseY, x, y, width, height, ticks) -> {
                var frame = within(mouseX, mouseY, x, y, MODULE_ROW_WIDTH, MODULE_ROW_HEIGHT) ? ROW_SELECTED : ROW;
                frame.draw(graphics, mouseX, mouseY, x, y, width, height, ticks);
            }));

            var iconElement = LdlibScreens.unadornedItemIcon(module.stack());
            addChild(at(iconElement, MODULE_ICON_X - MODULE_ROW_X, 1, 16, 16));
            var text = new UIElement().setAllowHitTest(false).style(s -> s.background(
                    (graphics, mouseX, mouseY, x, y, width, height, ticks) -> {
                        String amount = module.amount();
                        int amountX = REMOVE_BUTTON_X - MODULE_ROW_X - font.width(amount) - 4;
                        int color = within(mouseX, mouseY, x, y, MODULE_ROW_WIDTH, MODULE_ROW_HEIGHT)
                                ? AE2GuiPalette.color(PaletteColor.DEFAULT_TEXT_COLOR) : TEXT_ON_DARK_BG;
                        LDLibFonts.drawText(graphics, font, Component.literal(amount).getVisualOrderText(),
                                x + amountX, y + MODULE_TEXT_Y_OFFSET, color, false);
                        int nameX = MODULE_NAME_X - MODULE_ROW_X;
                        String fitted = FittingText.withEllipsis(font, module.stack().getHoverName().getString(),
                                amountX - nameX - 3);
                        LDLibFonts.drawText(graphics, font, Component.literal(fitted).getVisualOrderText(),
                                x + nameX, y + MODULE_TEXT_Y_OFFSET, color, false);
                    }));
            addChild(at(text, 0, 0, MODULE_ROW_WIDTH, MODULE_ROW_HEIGHT));

            var remove = new UIElement();
            remove.style(s -> s.background((graphics, mouseX, mouseY, x, y, width, height, ticks) -> {
                var texture = within(mouseX, mouseY, x, y, REMOVE_BUTTON_SIZE, REMOVE_BUTTON_SIZE)
                        ? REMOVE_HOVER : REMOVE;
                texture.draw(graphics, mouseX, mouseY, x, y, width, height, ticks);
            }));
            addChild(at(remove, REMOVE_BUTTON_X - MODULE_ROW_X, REMOVE_BUTTON_Y_OFFSET,
                    REMOVE_BUTTON_SIZE, REMOVE_BUTTON_SIZE));
        }
    }
}
