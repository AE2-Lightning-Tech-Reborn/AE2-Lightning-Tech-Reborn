package com.moakiee.ae2lt.integration.recipeviewer.multiblock;

import com.moakiee.ae2lt.client.ldlib.LdlibScreens;
import com.moakiee.ae2lt.client.ldlib.LdlibText;
import com.moakiee.ae2lt.client.gui.FittingText;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import appeng.client.render.overlay.OverlayRenderType;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.blaze3d.vertex.VertexConsumer;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.gui.LDLibFonts;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scene;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Tab;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TabView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.integration.xei.IngredientIO;

import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Viewer-neutral, interactive orthographic multiblock preview using LDLib2 layout
 * and input. The viewer adapters supply their original native slot drawing while
 * the shared {@link Scene} viewport retains the orthographic model renderer.
 */
public final class MultiblockPreviewUI {
    public static final int DEFAULT_WIDTH = 300;
    public static final int DEFAULT_HEIGHT = 184;
    public static final int MAX_ALTERNATIVE_SLOTS = 7;

    private static final int PANEL_WIDTH = 105;
    private static final int VIEWPORT_BORDER = 1;
    private static final int MATERIAL_ROW_HEIGHT = 26;
    private static final float DEFAULT_YAW = 225.0F;
    private static final float DEFAULT_PITCH = 30.0F;
    private static final float FRAME_MARGIN = 0.90F;
    private static final float MODEL_Z = 80.0F;
    private static final float DEPTH_SCALE_FACTOR = 0.1F;
    private static final float MIN_MAGNIFICATION = 0.35F;
    private static final float MAX_MAGNIFICATION = 3.5F;
    private static final float ROTATION_PER_PIXEL = 0.8F;
    private static final float PITCH_LIMIT = 85.0F;
    private static final float PAN_LIMIT = 0.65F;
    private static final float AUTO_ROTATION_PER_TICK = 0.35F;

    private static final int PANEL_BACKGROUND = 0xFF181818;
    private static final int VIEW_BACKGROUND = 0xFF101010;
    private static final int BORDER_COLOR = 0xFF626262;
    private static final int HOVER_COLOR = 0xFF353535;
    private static final int BUTTON_COLOR = 0xFF262626;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int MUTED_TEXT_COLOR = 0xFFD0D0D0;
    private static final int FOOTER_TEXT_COLOR = 0xFF000000;

    private final MultiblockStructureRecipe recipe;
    private final PreviewRoot root = new PreviewRoot();
    private final PreviewScene scene = new PreviewScene();

    private final TabView panel;
    private final Tab materialsTab = panelTab(Component.translatable("jei.ae2lt.multiblock.materials"), (PANEL_WIDTH - 2) / 2);
    private final Tab detailsTab = panelTab(Component.translatable("jei.ae2lt.multiblock.details"), PANEL_WIDTH / 2);
    private final UIElement hintLabel = new UIElement().setAllowHitTest(false);
    private final UIElement selectionGroup = new UIElement();
    private final DetailSlot selectedSlot = new DetailSlot();
    private final UIElement selectedName = selectedText(cell -> cell.state().getBlock().getName(),
            PANEL_WIDTH - 27, TEXT_COLOR);
    private final UIElement roleLabel = selectedText(
            cell -> Component.translatable("jei.ae2lt.multiblock.role_label", cell.role()),
            PANEL_WIDTH - 8, MUTED_TEXT_COLOR);
    private final UIElement positionLabel = selectedText(cell -> Component.translatable(
            "jei.ae2lt.multiblock.position", cell.localPos().getX(), cell.localPos().getY(), cell.localPos().getZ()),
            PANEL_WIDTH - 8, MUTED_TEXT_COLOR);
    private final UIElement fixedLabel = unboundedText(
            () -> Component.translatable("jei.ae2lt.multiblock.fixed"), MUTED_TEXT_COLOR);
    private final UIElement alternativesGrid = new UIElement();
    private final List<DetailSlot> alternativeSlots = new ArrayList<>(MAX_ALTERNATIVE_SLOTS);
    private final UIElement airMarker = new UIElement();
    private final UIElement ruleLabel = selectedText(
            cell -> cell.rules().isEmpty() ? Component.empty() : cell.rules().getFirst(),
            PANEL_WIDTH - 8, MUTED_TEXT_COLOR);

    private boolean autoRotate = true;
    private boolean hideShell;
    private LayerMode layerMode = LayerMode.FULL;
    private int layer;
    private MultiblockStructureRecipe.Cell selectedCell;
    private boolean renderTickClock;
    private long lastRenderTick = Long.MIN_VALUE;
    private UIElement transientPressedButton;
    private int transientPressedTicks;

    public static ModularUI create(MultiblockStructureRecipe recipe) {
        return create(recipe, false);
    }

    public static ModularUI createForEmi(MultiblockStructureRecipe recipe) {
        return create(recipe, true);
    }

    private static ModularUI create(MultiblockStructureRecipe recipe, boolean renderTickClock) {
        var preview = new MultiblockPreviewUI(recipe);
        preview.renderTickClock = renderTickClock;
        return ModularUI.of(UI.of(preview.root));
    }

    public static boolean owns(ModularUI ui) {
        return ui.ui.rootElement instanceof PreviewRoot;
    }

    public static boolean acceptsMouseClick(ModularUI ui, double x, double y, int button) {
        return ui.ui.rootElement instanceof PreviewRoot root && root.acceptsClick(x, y, button);
    }

    public static boolean mouseScrolled(ModularUI ui, double x, double y, double deltaY) {
        return ui.ui.rootElement instanceof PreviewRoot root && root.scroll(x, y, deltaY);
    }

    public static boolean mouseDragged(ModularUI ui, double x, double y, int button, double deltaX, double deltaY) {
        return ui.ui.rootElement instanceof PreviewRoot root && root.drag(x, y, button, deltaX, deltaY);
    }

    /** Uses viewer-native item, background and overlay rendering at each UI slot. */
    public static void setSlotRenderer(ModularUI ui, SlotRenderer renderer) {
        if (!(ui.ui.rootElement instanceof PreviewRoot root)) {
            throw new IllegalArgumentException("Not a multiblock preview UI");
        }
        root.slotRenderer = Objects.requireNonNull(renderer);
        for (var slot : root.slots) {
            slot.style(s -> s.background(IGuiTexture.EMPTY));
        }
    }

    @FunctionalInterface
    public interface SlotRenderer {
        void draw(GUIContext context, ItemSlot slot);
    }

    private final class PreviewRoot extends UIElement {
        private final List<UIElement> clickTargets = new ArrayList<>();

        private boolean insideViewport(double x, double y) {
            var mouse = scene.getLocalMouse((float) x, (float) y);
            return scene.isIntersectWithPoint(mouse.x, mouse.y);
        }

        private boolean drag(double x, double y, int button, double deltaX, double deltaY) {
            return scene.handlePreviewDrag(x, y, button, deltaX, deltaY);
        }

        private boolean acceptsClick(double x, double y, int button) {
            if (button != 0 && button != 2) return false;
            if (insideViewport(x, y)) return true;
            return button == 0 && (materialsTab.isSelfOrChildHover() || detailsTab.isSelfOrChildHover()
                    || clickTargets.stream().anyMatch(UIElement::isSelfOrChildHover));
        }

        private ScrollerView materialsScroller;

        private boolean scroll(double x, double y, double deltaY) {
            if (!acceptsScroll(x, y)) return false;
            if (insideViewport(x, y)) {
                autoRotate = false;
                scene.zoomAtCursor((float) x, (float) y, deltaY);
            } else {
                scrollMaterials(materialsScroller, deltaY);
            }
            return true;
        }

        private boolean acceptsScroll(double x, double y) {
            if (insideViewport(x, y)) return true;
            if (panel.getSelectedTab() != materialsTab) return false;
            var mouse = panel.getLocalMouse((float) x, (float) y);
            return mouse.x >= panel.getPositionX() && mouse.x < panel.getPositionX() + panel.getSizeWidth()
                    && mouse.y >= panel.getPositionY() + 17
                    && mouse.y < panel.getPositionY() + panel.getSizeHeight() - 1;
        }

        private final List<ItemSlot> slots = new ArrayList<>();
        private SlotRenderer slotRenderer;
    }

    private MultiblockPreviewUI(MultiblockStructureRecipe recipe) {
        this.recipe = recipe;
        this.layer = (recipe.sizeY() - 1) / 2;

        root.layout(l -> l.widthPercent(100).heightPercent(100).paddingHorizontal(2));
        root.addChildren(buildHeader(), buildToolbar());

        panel = buildPanel(materialsTab, buildMaterials(), detailsTab, buildDetails());
        var body = row().layout(l -> l.flex(1).marginTop(1).gapAll(4));
        body.addChildren(buildViewport(), panel);
        root.addChildren(body, buildFooter());

        refresh();
    }

    // ---- layout -------------------------------------------------------------------------

    private UIElement buildHeader() {
        String dimensions = recipe.sizeX() + "x" + recipe.sizeY() + "x" + recipe.sizeZ();
        return row().layout(l -> l.height(14)).style(s -> s.background(
                (graphics, mouseX, mouseY, x, y, width, height, ticks) -> {
                    var font = Minecraft.getInstance().font;
                    String title = FittingText.withEllipsis(font, recipe.title().getString(), Math.round(width) - 66);
                    LdlibText.drawString(graphics, font, title, x, y + 1, TEXT_COLOR, false);
                    LdlibText.drawString(graphics, font, dimensions,
                            x + width - font.width(dimensions), y + 1, MUTED_TEXT_COLOR, false);
                }));
    }

    private UIElement buildToolbar() {
        var toolbar = row().layout(l -> l.height(MultiblockPreviewControls.BUTTON_SIZE).gapAll(2));
        toolbar.addChildren(
                iconButton(
                        () -> MultiblockPreviewControls.PixelIcon.RESET_VIEW,
                        () -> false,
                        () -> true,
                        this::resetCamera,
                        () -> Component.translatable("jei.ae2lt.multiblock.tooltip.reset")),
                iconButton(
                        () -> autoRotate
                                ? MultiblockPreviewControls.PixelIcon.PAUSE
                                : MultiblockPreviewControls.PixelIcon.PLAY,
                        () -> autoRotate,
                        () -> true,
                        () -> autoRotate = !autoRotate,
                        () -> Component.translatable("jei.ae2lt.multiblock.tooltip.auto")),
                iconButton(
                        () -> hideShell
                                ? MultiblockPreviewControls.PixelIcon.SHELL_HIDDEN
                                : MultiblockPreviewControls.PixelIcon.SHELL,
                        () -> hideShell,
                        () -> true,
                        () -> hideShell = !hideShell,
                        () -> Component.translatable("jei.ae2lt.multiblock.tooltip.shell")),
                iconButton(
                        () -> layerMode.icon(),
                        () -> layerMode != LayerMode.FULL,
                        () -> true,
                        () -> layerMode = layerMode.next(),
                        () -> Component.translatable("jei.ae2lt.multiblock.tooltip.layer_mode")
                                .append(" · ")
                                .append(layerMode.label())));

        Supplier<Component> layerTooltip = () -> Component.translatable("jei.ae2lt.multiblock.tooltip.layer");
        var layerDown = iconButton(
                () -> MultiblockPreviewControls.PixelIcon.MINUS,
                () -> false,
                () -> layer > 0,
                () -> changeLayer(-1),
                layerTooltip);
        layerDown.layout(l -> l.marginLeft(2));
        var layerIndicator = new UIElement().layout(l -> l.width(49).heightPercent(100))
                .style(s -> s.background(IGuiTexture.group(MultiblockPreviewControls.insetFrame(),
                        centeredText(() -> Component.translatable("jei.ae2lt.multiblock.layer", layer + 1, recipe.sizeY()),
                                4, () -> MultiblockPreviewControls.ICON_COLOR))));
        withTooltip(layerIndicator, () -> List.of(layerTooltip.get()));
        var layerUp = iconButton(
                () -> MultiblockPreviewControls.PixelIcon.PLUS,
                () -> false,
                () -> layer < recipe.sizeY() - 1,
                () -> changeLayer(1),
                layerTooltip);
        toolbar.addChildren(layerDown, layerIndicator, layerUp);
        return toolbar;
    }

    private UIElement buildViewport() {
        scene.setIntractable(true);
        scene.setDraggable(true);
        scene.setScalable(true);
        scene.setOverflowVisible(true);
        scene.layout(l -> l.widthPercent(100).heightPercent(100));
        scene.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            var hovered = scene.hoveredCell;
            if (hovered != null && isVisible(hovered)) {
                appendTooltips(event,
                        hovered.state().getBlock().getName(),
                        Component.translatable("jei.ae2lt.multiblock.role_label", hovered.role()),
                        Component.translatable("jei.ae2lt.multiblock.click_for_details"));
            }
        });
        return new UIElement()
                .layout(l -> l.flex(1).heightPercent(100).paddingAll(VIEWPORT_BORDER))
                .style(s -> s.background(framed(VIEW_BACKGROUND, BORDER_COLOR)))
                .addChild(scene);
    }

    private static TabView buildPanel(Tab materialsTab, UIElement materialsView, Tab detailsTab, UIElement detailsView) {
        var panel = new TabView();
        panel.layout(l -> l.width(PANEL_WIDTH).heightPercent(100).paddingAll(1));
        panel.style(s -> s.background(framed(PANEL_BACKGROUND, BORDER_COLOR)));
        panel.tabHeaderContainer(header -> header.layout(l -> l.paddingHorizontal(0).height(15).flexShrink(0)));
        panel.tabScroller(scroller -> {
            scroller.layout(l -> l.heightPercent(100).marginBottom(0));
            scroller.scrollerStyle(s -> s.adaptiveHeight(false));
            scroller.viewContainer(tabs -> tabs.layout(l -> l.widthPercent(100).heightPercent(100)));
        });
        panel.tabContentContainer(content -> {
            content.layout(l -> l.paddingAll(0).flex(1).flexDirection(FlexDirection.COLUMN));
            content.style(s -> s.background(IGuiTexture.EMPTY));
        });
        return panel.addTab(materialsTab, materialsView).addTab(detailsTab, detailsView);
    }

    private static void scrollMaterials(ScrollerView scroller, double deltaY) {
        int max = Math.max(0, Math.round(scroller.getContainerHeight() - scroller.viewPort.getContentHeight()));
        if (max > 0) {
            int offset = Math.round(scroller.verticalScroller.getNormalizedValue() * max);
            int next = offset - (int) Math.round(deltaY * 14.0D);
            scroller.verticalScroller.setNormalizedValue(Mth.clamp(next, 0, max) / (float) max, true);
        }
    }

    private UIElement buildMaterials() {
        var scroller = new ScrollerView() {
            @Override
            protected void onScrollWheel(UIEvent event) {
                scrollMaterials(this, event.deltaY);
                event.stopPropagation();
            }
        };
        root.materialsScroller = scroller;
        scroller.layout(l -> l.flex(1));
        scroller.scrollerStyle(s -> s.mode(ScrollerMode.VERTICAL)
                .verticalScrollDisplay(ScrollDisplay.NEVER)
                .horizontalScrollDisplay(ScrollDisplay.NEVER));
        scroller.viewPort(port -> {
            port.layout(l -> l.paddingAll(0));
            port.style(s -> s.background(IGuiTexture.EMPTY));
        });

        List<MultiblockStructureRecipe.MaterialEntry> materials = recipe.materials();
        for (int i = 0; i < materials.size(); i++) {
            var material = materials.get(i);
            var stack = new ItemStack(material.block());
            int baseColor = i % 2 == 0 ? 0xFF202020 : 0xFF1C1C1C;

            var tooltips = new ArrayList<Component>();
            tooltips.add(Component.translatable("jei.ae2lt.multiblock.count", material.count()));
            if (!material.note().getString().isEmpty()) {
                tooltips.add(material.note());
            }
            var slot = itemSlot()
                    .setItem(stack)
                    .xeiRecipeSlot(IngredientIO.CATALYST, 1.0F, material.count(), () -> Stream.of(stack.copy()));
            slot.style(s -> s.tooltips(tooltips.toArray(Component[]::new)));

            int textWidth = PANEL_WIDTH - 34;
            var name = fittedText(() -> material.block().getName(), textWidth, TEXT_COLOR);
            var count = fittedText(
                    () -> Component.translatable("jei.ae2lt.multiblock.count", material.count()),
                    textWidth, MUTED_TEXT_COLOR);
            var materialRow = row();
            materialRow.layout(l -> l.height(MATERIAL_ROW_HEIGHT).flexShrink(0).paddingLeft(3).paddingRight(3)
                    .alignItems(AlignItems.CENTER));
            materialRow.style(s -> s.background(LdlibScreens.solid(
                    () -> materialRow.isSelfOrChildHover() ? HOVER_COLOR : baseColor)));
            materialRow.addChildren(slot,
                    LdlibScreens.at(name, 26, 3, textWidth, 9),
                    LdlibScreens.at(count, 26, 14, textWidth, 9));
            scroller.addScrollViewChild(materialRow);
        }
        var track = new UIElement().setAllowHitTest(false).setVisible(false);
        track.layout(l -> l.positionType(TaffyPosition.ABSOLUTE).right(1).top(1).bottom(1).width(2));
        track.style(s -> s.background(LdlibScreens.solid(() -> 0xFF303030)));
        var thumb = new UIElement().setAllowHitTest(false);
        thumb.style(s -> s.background(LdlibScreens.solid(() -> 0xFFB0B0B0)));
        track.addChild(LdlibScreens.at(thumb, 0, 0, 2, 0));

        Runnable updateIndicator = () -> {
            int viewportHeight = Math.round(scroller.viewPort.getContentHeight());
            int contentHeight = Math.round(scroller.getContainerHeight());
            int trackHeight = Math.round(track.getSizeHeight());
            boolean scrollable = viewportHeight > 0 && contentHeight > viewportHeight && trackHeight > 0;
            track.setVisible(scrollable);
            if (scrollable) {
                int thumbHeight = Math.max(12, trackHeight * viewportHeight / contentHeight);
                int maxScroll = contentHeight - viewportHeight;
                int offset = Math.round(scroller.verticalScroller.getNormalizedValue() * maxScroll);
                int thumbTop = (int) Math.round((double) offset / maxScroll * Math.max(0, trackHeight - thumbHeight));
                thumb.layout(l -> l.top(thumbTop).height(thumbHeight));
            }
        };
        track.addEventListener(UIEvents.LAYOUT_CHANGED, event -> updateIndicator.run());
        scroller.viewPort.addEventListener(UIEvents.LAYOUT_CHANGED, event -> updateIndicator.run());
        scroller.viewContainer.addEventListener(UIEvents.LAYOUT_CHANGED, event -> updateIndicator.run());
        scroller.verticalScroller.registerValueListener(value -> updateIndicator.run());
        return column().layout(l -> l.flex(1).marginTop(1)).addChildren(scroller, track);
    }

    private UIElement buildDetails() {
        hintLabel.layout(l -> l.positionType(TaffyPosition.ABSOLUTE).left(4).top(5)
                .width(PANEL_WIDTH - 10).heightPercent(100));
        hintLabel.style(s -> s.background((graphics, mouseX, mouseY, x, y, width, height, ticks) -> {
            var font = Minecraft.getInstance().font;
            int lineY = Math.round(y);
            for (var line : font.split(Component.translatable("jei.ae2lt.multiblock.select_hint"), PANEL_WIDTH - 10)) {
                LDLibFonts.drawText(graphics, font, line, Math.round(x), lineY, MUTED_TEXT_COLOR, false);
                lineY += font.lineHeight + 2;
            }
        }));

        bindDetailSlot(selectedSlot);
        var replacements = unboundedText(
                () -> Component.translatable("jei.ae2lt.multiblock.replacements"), TEXT_COLOR);

        LdlibScreens.at(alternativesGrid, 0, 63, 87, 39);
        for (int i = 0; i < MAX_ALTERNATIVE_SLOTS; i++) {
            var slot = new DetailSlot();
            bindDetailSlot(slot);
            alternativeSlots.add(slot);
            alternativesGrid.addChild(placeAlternative(slot, i));
        }
        airMarker.layout(l -> l.width(18).height(18));
        airMarker.style(s -> s.background(framed(BUTTON_COLOR, BORDER_COLOR))
                .tooltips(Component.translatable("jei.ae2lt.multiblock.air")));
        var airText = new UIElement().setAllowHitTest(false);
        airText.style(s -> s.background((graphics, mouseX, mouseY, x, y, width, height, ticks) -> {
            var font = Minecraft.getInstance().font;
            int left = (Math.round(width) - font.width("X")) / 2;
            LdlibText.drawString(graphics, font, "X", Math.round(x) + left, Math.round(y), MUTED_TEXT_COLOR, false);
        }));
        airMarker.addChild(LdlibScreens.at(airText, 0, 5, 18, 9));
        alternativesGrid.addChild(placeAlternative(airMarker, MAX_ALTERNATIVE_SLOTS));

        ruleLabel.layout(l -> l.positionType(TaffyPosition.ABSOLUTE).left(0).bottom(2).widthPercent(100));
        var ruleTooltipArea = new UIElement();
        ruleTooltipArea.layout(l -> l.positionType(TaffyPosition.ABSOLUTE).left(0).bottom(0)
                .widthPercent(100).height(14));
        withTooltip(ruleTooltipArea, () -> selectedCell == null ? null : selectedCell.rules());

        selectionGroup.layout(l -> l.flex(1).marginHorizontal(3));
        selectionGroup.addChildren(
                LdlibScreens.at(selectedSlot, 0, 2, 18, 18),
                LdlibScreens.at(selectedName, 21, 3, PANEL_WIDTH - 27, 9),
                LdlibScreens.at(roleLabel, 0, 24, PANEL_WIDTH - 8, 9),
                LdlibScreens.at(positionLabel, 0, 35, PANEL_WIDTH - 8, 9),
                LdlibScreens.at(replacements, 0, 48, PANEL_WIDTH - 8, 9),
                LdlibScreens.at(fixedLabel, 0, 62, PANEL_WIDTH - 8, 9),
                alternativesGrid,
                ruleLabel);

        return column()
                .layout(l -> l.flex(1).marginTop(1))
                .addChildren(hintLabel, selectionGroup, ruleTooltipArea);
    }

    private UIElement buildFooter() {
        var controls = new UIElement().setAllowHitTest(false)
                .layout(l -> l.widthPercent(100).height(9))
                .style(s -> s.background(centeredText(
                        () -> Component.translatable("jei.ae2lt.multiblock.controls"), 4, () -> FOOTER_TEXT_COLOR)));
        return row().layout(l -> l.height(17).paddingTop(3)).addChild(controls);
    }

    private static UIElement placeAlternative(UIElement element, int index) {
        return LdlibScreens.at(element, index % 4 * 23, index / 4 * 21, 18, 18);
    }

    // ---- state --------------------------------------------------------------------------

    private void refresh() {
        boolean hasSelection = selectedCell != null;
        hintLabel.setDisplay(!hasSelection);
        selectionGroup.setDisplay(hasSelection);
        if (!hasSelection) {
            return;
        }

        Block block = selectedCell.state().getBlock();
        selectedSlot.showItem(new ItemStack(block));


        List<Block> alternatives = selectedCell.alternatives().stream()
                .filter(alternative -> alternative != Blocks.AIR)
                .toList();
        boolean allowsAir = selectedCell.alternatives().contains(Blocks.AIR);
        boolean fixed = alternatives.size() == 1 && !allowsAir;
        fixedLabel.setDisplay(fixed);
        alternativesGrid.setDisplay(!fixed);
        for (int i = 0; i < alternativeSlots.size(); i++) {
            var slot = alternativeSlots.get(i);
            boolean shown = !fixed && i < alternatives.size();
            slot.setDisplay(shown);
            slot.showItem(shown ? new ItemStack(alternatives.get(i)) : ItemStack.EMPTY);
        }
        airMarker.setDisplay(!fixed && allowsAir);
        placeAlternative(airMarker, Math.min(alternatives.size(), alternativeSlots.size()));

        boolean hasRules = !selectedCell.rules().isEmpty();
        ruleLabel.setDisplay(hasRules);

    }

    private void select(MultiblockStructureRecipe.Cell cell) {
        selectedCell = cell;
        refresh();
        panel.selectTab(cell == null ? materialsTab : detailsTab);
    }

    private void changeLayer(int offset) {
        layer += offset;
    }

    private boolean isVisible(MultiblockStructureRecipe.Cell cell) {
        if (cell.state().isAir() || (hideShell && cell.shell())) {
            return false;
        }
        int y = cell.localPos().getY();
        return switch (layerMode) {
            case FULL -> true;
            case UP_TO -> y <= layer;
            case SINGLE -> y == layer;
        };
    }

    private void tickPreview() {
        if (transientPressedTicks > 0 && --transientPressedTicks == 0) {
            transientPressedButton = null;
        }
        if (autoRotate) {
            scene.yaw = wrapDegrees(scene.yaw + AUTO_ROTATION_PER_TICK);
        }
    }

    private void resetCamera() {
        scene.yaw = DEFAULT_YAW;
        scene.pitch = DEFAULT_PITCH;
        scene.zoom = 1.0F;
        scene.panX = 0.0F;
        scene.panY = 0.0F;
    }

    private static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0F;
        return wrapped < 0.0F ? wrapped + 360.0F : wrapped;
    }

    private final class PreviewScene extends Scene {
        private float yaw = DEFAULT_YAW;
        private float pitch = DEFAULT_PITCH;
        private float zoom = 1.0F;
        private MultiblockStructureRecipe.Cell hoveredCell;
        private float panX;
        private float panY;

        @Override
        public boolean isIntersectWithPoint(double x, double y) {
            // The border stays outside the rendered scene and inside its original input rectangle.
            float left = getPositionX() - VIEWPORT_BORDER;
            float top = getPositionY() - VIEWPORT_BORDER;
            return isMouseOverRect(left, top, getSizeWidth() + 2 * VIEWPORT_BORDER,
                    getSizeHeight() + 2 * VIEWPORT_BORDER, x, y);
        }

        @Override
        public void screenTick() {
            super.screenTick();
            if (!renderTickClock) {
                tickPreview();
            }
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            if (renderTickClock) {
                long tick = Util.getMillis() / 50L;
                if (tick != lastRenderTick) {
                    tickPreview();
                    lastRenderTick = tick;
                }
            }
            hoveredCell = pickLocal(context.localMouseX, context.localMouseY);
            var graphics = context.graphics;
            int x = Math.round(getContentX());
            int y = Math.round(getContentY());
            int w = Math.round(getContentWidth());
            int h = Math.round(getContentHeight());
            if (w <= 0 || h <= 0) {
                return;
            }
            enableLocalScissor(graphics, x, y, x + w, y + h);
            var client = Minecraft.getInstance();
            var buffers = client.renderBuffers().bufferSource();
            var pose = graphics.pose();
            var glow = OverlayRenderType.getBlockHilightFace();
            pose.pushPose();
            try {
                float scale = renderScale();
                pose.translate(centerX() + panX, centerY() + panY, MODEL_Z);
                pose.scale(scale, -scale, scale * DEPTH_SCALE_FACTOR);
                pose.mulPose(Axis.XP.rotationDegrees(pitch));
                pose.mulPose(Axis.YP.rotationDegrees(yaw));
                pose.translate(-recipe.sizeX() / 2.0F, -recipe.sizeY() / 2.0F, -recipe.sizeZ() / 2.0F);
                for (var cell : recipe.cells()) {
                    if (!MultiblockPreviewUI.this.isVisible(cell) || cell.state().getRenderShape() == RenderShape.ENTITYBLOCK_ANIMATED) {
                        continue;
                    }
                    var pos = cell.localPos();
                    pose.pushPose();
                    try {
                        pose.translate(pos.getX(), pos.getY(), pos.getZ());
                        client.getBlockRenderer().renderSingleBlock(cell.state(), pose, buffers,
                                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
                    } finally {
                        pose.popPose();
                    }
                }
                buffers.endBatch();
                if (selectedCell != null && selectedCell != hoveredCell && MultiblockPreviewUI.this.isVisible(selectedCell)) {
                    renderGlowCube(pose, buffers.getBuffer(glow), selectedCell, 132);
                }
                if (hoveredCell != null) {
                    renderGlowCube(pose, buffers.getBuffer(glow), hoveredCell, 190);
                }
            } finally {
                pose.popPose();
                buffers.endBatch(glow);
                Lighting.setupFor3DItems();
                graphics.disableScissor();
            }
        }

        private static void enableLocalScissor(GuiGraphics graphics, int left, int top, int right, int bottom) {
            var matrix = graphics.pose().last().pose();
            var first = matrix.transformPosition(new Vector3f(left, top, 0));
            var second = matrix.transformPosition(new Vector3f(right, bottom, 0));
            graphics.enableScissor((int) Math.floor(Math.min(first.x, second.x)),
                    (int) Math.floor(Math.min(first.y, second.y)),
                    (int) Math.ceil(Math.max(first.x, second.x)), (int) Math.ceil(Math.max(first.y, second.y)));
        }

        private static void renderGlowCube(PoseStack pose, VertexConsumer consumer,
                MultiblockStructureRecipe.Cell cell, int alpha) {
            pose.pushPose();
            var pos = cell.localPos();
            pose.translate(pos.getX(), pos.getY(), pos.getZ());
            Matrix4f matrix = pose.last().pose();
            float low = -0.025F;
            float high = 1.025F;
            glowQuad(consumer, matrix, alpha,
                    low, low, low, high, low, low, high, low, high, low, low, high, 0, -1, 0);
            glowQuad(consumer, matrix, alpha,
                    low, high, high, high, high, high, high, high, low, low, high, low, 0, 1, 0);
            glowQuad(consumer, matrix, alpha,
                    low, low, low, low, high, low, high, high, low, high, low, low, 0, 0, -1);
            glowQuad(consumer, matrix, alpha,
                    high, low, high, high, high, high, low, high, high, low, low, high, 0, 0, 1);
            glowQuad(consumer, matrix, alpha,
                    low, low, high, low, high, high, low, high, low, low, low, low, -1, 0, 0);
            glowQuad(consumer, matrix, alpha,
                    high, low, low, high, high, low, high, high, high, high, low, high, 1, 0, 0);
            pose.popPose();
        }

        private static void glowQuad(VertexConsumer consumer, Matrix4f matrix, int alpha,
                float x1, float y1, float z1, float x2, float y2, float z2,
                float x3, float y3, float z3, float x4, float y4, float z4,
                float normalX, float normalY, float normalZ) {
            consumer.addVertex(matrix, x1, y1, z1).setColor(255, 220, 0, alpha).setNormal(normalX, normalY, normalZ);
            consumer.addVertex(matrix, x2, y2, z2).setColor(255, 220, 0, alpha).setNormal(normalX, normalY, normalZ);
            consumer.addVertex(matrix, x3, y3, z3).setColor(255, 220, 0, alpha).setNormal(normalX, normalY, normalZ);
            consumer.addVertex(matrix, x4, y4, z4).setColor(255, 220, 0, alpha).setNormal(normalX, normalY, normalZ);
        }

        private float centerX() {
            return getContentX() + getContentWidth() / 2;
        }

        private float centerY() {
            return getContentY() + getContentHeight() / 2;
        }

        private float renderScale() {
            float horizontal = (float) Math.hypot(recipe.sizeX(), recipe.sizeZ());
            float vertical = recipe.sizeY() * 0.866F + horizontal * 0.5F;
            float widthScale = (getSizeWidth() + 2 * VIEWPORT_BORDER) * FRAME_MARGIN / Math.max(1.0F, horizontal);
            float heightScale = (getSizeHeight() + 2 * VIEWPORT_BORDER) * FRAME_MARGIN / Math.max(1.0F, vertical);
            return Math.min(widthScale, heightScale) * zoom;
        }

        private Matrix4f modelTransform(float scale) {
            return new Matrix4f()
                    .translation(centerX() + panX, centerY() + panY, MODEL_Z)
                    .scale(scale, -scale, scale * DEPTH_SCALE_FACTOR)
                    .rotateX((float) Math.toRadians(pitch))
                    .rotateY((float) Math.toRadians(yaw))
                    .translate(-recipe.sizeX() / 2.0F, -recipe.sizeY() / 2.0F, -recipe.sizeZ() / 2.0F);
        }

        @Override
        protected void onMouseDown(UIEvent event) {
            int button = event.button;
            if (!isIntractable() || !isHover()
                    || (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_MIDDLE)) {
                return;
            }
            autoRotate = false;
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                // The original preview selected the hovered block on press, before any drag.
                var pointer = getLocalMouse(event.x, event.y);
                MultiblockPreviewUI.this.select(pickLocal(pointer.x, pointer.y));
                lastClickPosFace = null;
            }
            event.stopPropagation();
        }

        @Override
        protected void onMouseWheel(UIEvent event) {
            if (event.target != this || !isIntractable() || !isScalable()) {
                return;
            }
            autoRotate = false;
            zoomAtCursor(event.x, event.y, event.deltaY);
            event.stopPropagation();
        }

        private void zoomAtCursor(float mouseX, float mouseY, double deltaY) {
            float previousZoom = zoom;
            zoom = Mth.clamp((float) (zoom * Math.exp(deltaY * 0.12D)),
                    MIN_MAGNIFICATION, MAX_MAGNIFICATION);
            var cursor = getLocalMouse(mouseX, mouseY);
            float x = cursor.x - centerX();
            float y = cursor.y - centerY();
            float ratio = zoom / previousZoom;
            panTo(x - ratio * (x - panX), y - ratio * (y - panY));
        }

        private void panTo(float x, float y) {
            float width = (getSizeWidth() + 2 * VIEWPORT_BORDER) * PAN_LIMIT;
            float height = (getSizeHeight() + 2 * VIEWPORT_BORDER) * PAN_LIMIT;
            panX = Mth.clamp(x, -width, width);
            panY = Mth.clamp(y, -height, height);
        }

        private MultiblockStructureRecipe.Cell pickLocal(float x, float y) {
            float left = getContentX();
            float top = getContentY();
            float width = getContentWidth();
            float height = getContentHeight();
            if (width <= 0 || height <= 0 || x < left - VIEWPORT_BORDER || y < top - VIEWPORT_BORDER
                    || x >= left + width + VIEWPORT_BORDER || y >= top + height + VIEWPORT_BORDER) {
                return null;
            }
            float scale = renderScale();
            float padding = Math.max(1.0F, scale * 0.08F);
            var transform = modelTransform(scale);
            var point = new Vector3f();
            MultiblockStructureRecipe.Cell closest = null;
            float closestDepth = Float.NEGATIVE_INFINITY;
            for (var cell : recipe.cells()) {
                if (!MultiblockPreviewUI.this.isVisible(cell)) {
                    continue;
                }
                var pos = cell.localPos();
                float minX = Float.POSITIVE_INFINITY;
                float minY = Float.POSITIVE_INFINITY;
                float maxX = Float.NEGATIVE_INFINITY;
                float maxY = Float.NEGATIVE_INFINITY;
                for (int dx = 0; dx <= 1; dx++) {
                    for (int dy = 0; dy <= 1; dy++) {
                        for (int dz = 0; dz <= 1; dz++) {
                            transform.transformPosition(point.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz));
                            minX = Math.min(minX, point.x);
                            minY = Math.min(minY, point.y);
                            maxX = Math.max(maxX, point.x);
                            maxY = Math.max(maxY, point.y);
                        }
                    }
                }
                transform.transformPosition(point.set(pos.getX() + 0.5F, pos.getY() + 0.5F, pos.getZ() + 0.5F));
                if (x >= minX - padding && x <= maxX + padding && y >= minY - padding && y <= maxY + padding
                        && point.z > closestDepth) {
                    closest = cell;
                    closestDepth = point.z;
                }
            }
            return closest;
        }

        private boolean handlePreviewDrag(double x, double y, int button, double deltaX, double deltaY) {
            var mouse = getLocalMouse((float) x, (float) y);
            if (!isIntractable() || !isIntersectWithPoint(mouse.x, mouse.y)
                    || (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_MIDDLE)) {
                return false;
            }
            var delta = getLocalMouseNormal((float) deltaX, (float) deltaY);
            if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE || net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
                panTo(panX + delta.x, panY + delta.y);
            } else {
                yaw = wrapDegrees(yaw + delta.x * ROTATION_PER_PIXEL);
                pitch = Mth.clamp(pitch + delta.y * ROTATION_PER_PIXEL, -PITCH_LIMIT, PITCH_LIMIT);
            }
            autoRotate = false;
            return true;
        }
    }

    // ---- widgets ------------------------------------------------------------------------

    private UIElement iconButton(
            Supplier<MultiblockPreviewControls.PixelIcon> icon,
            BooleanSupplier pressed,
            BooleanSupplier enabled,
            Runnable action,
            Supplier<Component> tooltip) {
        var button = new Button() {
            @Override
            protected void onMouseDown(UIEvent event) {
                if (event.button == 0) {
                    event.stopPropagation();
                    if (enabled.getAsBoolean()) {
                        super.onMouseDown(event);
                    }
                }
            }
        }.noText();
        button.layout(l -> l.width(MultiblockPreviewControls.BUTTON_SIZE).heightPercent(100)
                .paddingAll(0).alignItems(AlignItems.CENTER).justifyContent(AlignContent.CENTER));
        BooleanSupplier flashing = () -> transientPressedTicks > 0 && transientPressedButton == button;
        var face = MultiblockPreviewControls.iconButtonFrame(pressed, flashing, enabled, button::isHover);
        button.buttonStyle(style -> style.baseTexture(face).hoverTexture(face).pressedTexture(face));
        button.addChild(MultiblockPreviewControls.iconGlyph(icon, flashing, enabled, button::isHover));
        button.setOnClick(event -> {
            transientPressedButton = button;
            transientPressedTicks = 2;
            action.run();
        });
        withTooltip(button, () -> List.of(tooltip.get()));
        root.clickTargets.add(button);
        return button;
    }

    private static Tab panelTab(Component text, int width) {
        var tab = new Tab();
        tab.layout(l -> l.width(width).flexShrink(0).heightPercent(100).paddingAll(0));
        tab.text.setDisplay(false);
        var caption = new UIElement().setAllowHitTest(false).layout(l -> l.widthPercent(100).heightPercent(100));
        caption.style(s -> s.background(centeredText(() -> text, 4,
                () -> tab.isHover() ? MultiblockPreviewControls.HOVER_ICON_COLOR : MultiblockPreviewControls.ICON_COLOR)));
        tab.addChild(caption);
        tab.tabStyle(style -> style
                .baseTexture(MultiblockPreviewControls.tabFrame(false, () -> false))
                .hoverTexture(MultiblockPreviewControls.tabFrame(false, () -> true))
                .selectedTexture(MultiblockPreviewControls.tabFrame(true, tab::isHover)));
        return tab;
    }

    private UIElement selectedText(Function<MultiblockStructureRecipe.Cell, Component> text, int width, int color) {
        return fittedText(() -> selectedCell == null ? Component.empty() : text.apply(selectedCell), width, color);
    }

    private static UIElement fittedText(Supplier<Component> text, int width, int color) {
        return new UIElement().setAllowHitTest(false).layout(l -> l.height(9))
                .style(s -> s.background((graphics, mouseX, mouseY, x, y, drawWidth, height, ticks) -> {
                    var font = Minecraft.getInstance().font;
                    String value = FittingText.withEllipsis(font, text.get().getString(), width);
                    LdlibText.drawString(graphics, font, value, Math.round(x), Math.round(y), color, false);
                }));
    }

    private static UIElement unboundedText(Supplier<Component> text, int color) {
        return new UIElement().setAllowHitTest(false).layout(l -> l.height(9))
                .style(s -> s.background((graphics, mouseX, mouseY, x, y, width, height, ticks) ->
                        LDLibFonts.drawText(graphics, Minecraft.getInstance().font, text.get().getVisualOrderText(),
                                Math.round(x), Math.round(y), color, false)));
    }

    private static IGuiTexture centeredText(Supplier<Component> text, int horizontalInset, IntSupplier color) {
        return (graphics, mouseX, mouseY, x, y, width, height, ticks) -> {
            var font = Minecraft.getInstance().font;
            int available = Math.round(width);
            String value = text.get().getString();
            int maxWidth = Math.max(0, available - horizontalInset);
            if (font.width(value) > maxWidth) {
                value = font.plainSubstrByWidth(value, maxWidth);
            }
            int left = (available - font.width(value)) / 2;
            int top = (Math.round(height) - font.lineHeight) / 2 + 1;
            LdlibText.drawString(graphics, font, value, x + left, y + top, color.getAsInt(), false);
        };
    }

    private void bindDetailSlot(DetailSlot slot) {
        // JEI and REI build their ingredient lists once. EMI can read the live displayed stack at hover time.
        Supplier<Stream<ItemStack>> focus = () -> recipe.focusStacks().stream();
        if (LDLib2.isJeiLoaded()) {
            ItemSlot.JEISupport.recipeSlot(slot, IngredientIO.CATALYST, focus);
        }
        if (LDLib2.isReiLoaded()) {
            ItemSlot.REISupport.recipeSlot(slot, IngredientIO.CATALYST, focus);
        }
        if (LDLib2.isEmiLoaded()) {
            ItemSlot.EMISupport.recipeSlot(slot, 1.0F);
        }
    }

    private class PreviewSlot extends ItemSlot {
        private PreviewSlot() {
            layout(l -> l.width(18).height(18));
            root.slots.add(this);
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            if (root.slotRenderer == null) {
                super.drawBackgroundAdditional(context);
            } else {
                root.slotRenderer.draw(context, this);
            }
        }
    }

    private final class DetailSlot extends PreviewSlot {
        private void showItem(ItemStack stack) {
            setItem(stack, false);
            // A selection that matches JEI's current item still needs to stop its automatic cycling.
            notifyListeners();
        }
    }

    private ItemSlot itemSlot() {
        return new PreviewSlot();
    }


    private static UIElement row() {
        return new UIElement().layout(l -> l.flexDirection(FlexDirection.ROW).widthPercent(100));
    }

    private static UIElement column() {
        return new UIElement().layout(l -> l.flexDirection(FlexDirection.COLUMN));
    }

    private static void withTooltip(UIElement element, Supplier<List<Component>> tooltip) {
        element.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            List<Component> lines = tooltip.get();
            if (lines != null && !lines.isEmpty()) {
                appendTooltips(event, lines.toArray(Component[]::new));
            }
        });
    }

    private static void appendTooltips(UIEvent event, Component... lines) {
        HoverTooltips current = event.hoverTooltips == null ? HoverTooltips.empty() : event.hoverTooltips;
        event.hoverTooltips = current.append(lines);
    }

    private static IGuiTexture framed(int fill, int border) {
        return (graphics, mouseX, mouseY, x, y, width, height, ticks) -> {
            int left = Math.round(x);
            int top = Math.round(y);
            int w = Math.round(width);
            int h = Math.round(height);
            graphics.fill(left, top, left + w, top + h, fill);
            graphics.renderOutline(left, top, w, h, border);
        };
    }

    private enum LayerMode {
        FULL("jei.ae2lt.multiblock.mode.full"),
        UP_TO("jei.ae2lt.multiblock.mode.up_to"),
        SINGLE("jei.ae2lt.multiblock.mode.single");

        private final String translationKey;

        LayerMode(String translationKey) {
            this.translationKey = translationKey;
        }

        Component label() {
            return Component.translatable(translationKey);
        }

        MultiblockPreviewControls.PixelIcon icon() {
            return switch (this) {
                case FULL -> MultiblockPreviewControls.PixelIcon.LAYERS_FULL;
                case UP_TO -> MultiblockPreviewControls.PixelIcon.LAYERS_UP_TO;
                case SINGLE -> MultiblockPreviewControls.PixelIcon.LAYERS_SINGLE;
            };
        }

        LayerMode next() {
            return switch (this) {
                case FULL -> UP_TO;
                case UP_TO -> SINGLE;
                case SINGLE -> FULL;
            };
        }
    }

}
