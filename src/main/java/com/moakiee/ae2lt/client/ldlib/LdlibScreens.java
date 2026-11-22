package com.moakiee.ae2lt.client.ldlib;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import appeng.menu.slot.AppEngSlot;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolderMenu;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.FillDirection;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;
import com.lowdragmc.lowdraglib2.gui.ui.elements.VirtualItemHeightMode;
import com.lowdragmc.lowdraglib2.gui.ui.elements.VirtualScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.utils.UIElementProvider;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;

import com.moakiee.ae2lt.mixin.client.AbstractContainerScreenAccessor;

import dev.vfyjxf.taffy.style.TaffyPosition;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;

/**
 * Small helpers for hosting LDLib2 UI trees inside existing (vanilla or AE2) screens.
 *
 * <p>The screens keep their menus, slots and packets; LDLib renders the chrome and
 * handles the custom widgets. All coordinates are GUI-local pixels.</p>
 */
public final class LdlibScreens {
    private LdlibScreens() {
    }

    /** Hook for {@code Screen#addRenderableWidget}, which is protected. */
    @FunctionalInterface
    public interface WidgetAdder {
        <W extends GuiEventListener & Renderable & NarratableEntry> W add(W widget);
    }

    /** Builds a ModularUI for {@code root}, binds it to {@code screen} and registers its widget. */
    public static ModularUI embed(Screen screen, UIElement root, WidgetAdder adder) {
        var modularUI = screenUI(screen, root);
        modularUI.setScreenAndInit(screen);
        adder.add(modularUI.getWidget());
        return modularUI;
    }

    private static ModularUI screenUI(Screen screen, UIElement root) {
        var ui = UI.of(root);
        if (screen instanceof AbstractContainerScreen<?> container) {
            // The native container owns its integer origin, including odd screen sizes.
            // LDLib's default centering rounds instead of using the native integer division.
            return new ModularUI(ui) {
                private ModularUIWidget alignedWidget;

                @Override
                public ModularUIWidget getWidget() {
                    if (alignedWidget == null) {
                        alignedWidget = new ModularUIWidget() {
                            @Override
                            public net.minecraft.client.gui.navigation.ScreenRectangle getRectangle() {
                                var rectangle = super.getRectangle();
                                return new net.minecraft.client.gui.navigation.ScreenRectangle(
                                        container.getGuiLeft(), container.getGuiTop(),
                                        rectangle.width(), rectangle.height());
                            }
                        };
                    }
                    return alignedWidget;
                }

                @Override
                public float getLeftPos() {
                    return container.getGuiLeft();
                }

                @Override
                public float getTopPos() {
                    return container.getGuiTop();
                }
            };
        }
        return ModularUI.of(ui);
    }

    /** Displays existing container slots using LDLib, retaining vanilla slot indices and packets. */
    public static void embedWithSlots(AbstractContainerScreen<?> screen, UIElement root,
            int width, int height, WidgetAdder adder) {
        var visibleSlots = screen.getMenu().slots.stream()
                .filter(slot -> slot.x >= 0 && slot.y >= 0 && slot.x + 16 <= width && slot.y + 16 <= height)
                .toList();
        embedWithSlots(screen, root, visibleSlots, adder);
    }

    public static void embedWithSlots(AbstractContainerScreen<?> screen, UIElement root,
            List<? extends Slot> boundSlots, WidgetAdder adder) {
        var menu = screen.getMenu();
        var holder = (IModularUIHolderMenu) menu;
        var previousUI = holder.getModularUI();
        if (previousUI != null) {
            var views = LdlibSlotRegistry.views(menu);
            views.values().removeIf(view -> view.getModularUI() == previousUI);
            if (!previousUI.isRemoved()) {
                previousUI.onRemoved();
            }
        }
        for (var slot : boundSlots) {
            var element = new ExistingMenuSlot(slot);
            element.layout(l -> l.paddingAll(0));
            element.style(s -> s.background(IGuiTexture.EMPTY));
            element.slotStyle(s -> s.showItemTooltips(false));
            root.addChild(at(element, slot.x, slot.y, 16, 16));
            // addSlot(ItemSlot) would append a duplicate to the actual menu.
            holder.ldlib2$addSlot(element);
        }
        var ui = screenUI(screen, root);
        ui.setMenu(menu);
        holder.setModularUI(ui);
        ui.setScreenAndInit(screen);
        adder.add(ui.getWidget());
    }

    /** Unregisters only this view, preserving any replacement already registered for its slot. */
    public static void unbindExistingSlot(net.minecraft.world.inventory.AbstractContainerMenu menu, ItemSlot view) {
        LdlibSlotRegistry.views(menu).remove(view.getSlot(), view);
    }

    /** Rebinds a view without leaving the old physical slot mapped to it. */
    public static void bindExistingSlot(net.minecraft.world.inventory.AbstractContainerMenu menu,
            ItemSlot view, Slot slot) {
        if (view.getSlot() != slot) {
            unbindExistingSlot(menu, view);
            view.bind(slot);
        }
        var holder = (IModularUIHolderMenu) menu;
        if (holder.getItemSlot(slot) != view) {
            holder.ldlib2$addSlot(view);
        }
    }

    public static class ExistingMenuSlot extends ItemSlot {
        protected ExistingMenuSlot() {
            super();
        }

        private ExistingMenuSlot(Slot slot) {
            super(slot);
        }

        @Override
        public ItemStack getValue() {
            // AE2 applies this substitution in the native container's renderSlot.
            return getSlot() instanceof AppEngSlot slot ? slot.getDisplayStack() : super.getValue();
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            if (!(context.modularUI.getScreen() instanceof AbstractContainerScreen<?> screen)) {
                super.drawBackgroundAdditional(context);
                return;
            }
            var slot = getSlot();
            var stack = getValue();
            var carried = screen.getMenu().getCarried();
            var screenState = (AbstractContainerScreenAccessor) (Object) screen;
            boolean hideItem = slot == screenState.ae2lt$getClickedSlot() && !screenState.ae2lt$getDraggingItem().isEmpty()
                    && !screenState.ae2lt$isSplittingStack();
            boolean quickCraftBackground = false;
            boolean skipSlot = false;
            String countString = null;
            if (slot == screenState.ae2lt$getClickedSlot() && !screenState.ae2lt$getDraggingItem().isEmpty()
                    && screenState.ae2lt$isSplittingStack() && !stack.isEmpty()) {
                stack = stack.copyWithCount(stack.getCount() / 2);
            } else if (screenState.ae2lt$isQuickCrafting() && screenState.ae2lt$getQuickCraftSlots().contains(slot) && !carried.isEmpty()) {
                if (screenState.ae2lt$getQuickCraftSlots().size() == 1) {
                    // Native renderSlot returns, but its caller still renders the hover.
                    skipSlot = true;
                } else if (net.minecraft.world.inventory.AbstractContainerMenu.canItemQuickReplace(slot, carried, true)
                        && screen.getMenu().canDragTo(slot)) {
                    quickCraftBackground = true;
                    int limit = Math.min(carried.getMaxStackSize(), slot.getMaxStackSize(carried));
                    var current = slot.getItem();
                    int count = net.minecraft.world.inventory.AbstractContainerMenu.getQuickCraftPlaceCount(
                            screenState.ae2lt$getQuickCraftSlots(), screenState.ae2lt$getQuickCraftingType(), carried)
                            + (current.isEmpty() ? 0 : current.getCount());
                    if (count > limit) {
                        count = limit;
                        countString = net.minecraft.ChatFormatting.YELLOW.toString() + limit;
                    }
                    stack = carried.copyWithCount(count);
                } else {
                    screenState.ae2lt$getQuickCraftSlots().remove(slot);
                    screenState.ae2lt$recalculateQuickCraftRemaining();
                }
            }
            float width = getContentWidth();
            float height = getContentHeight();
            if (width <= 0 || height <= 0) {
                return;
            }
            context.pose.pushPose();
            try {
                context.pose.scale(width / 16, height / 16, 1);
                context.pose.translate(getContentX() * 16 / width, getContentY() * 16 / height, 0);
                if (!skipSlot) {
                    if (stack.isEmpty() && slot.isActive()) {
                        var icon = slot.getNoItemIcon();
                        if (icon != null) {
                            var sprite = context.mc.getTextureAtlas(icon.getFirst()).apply(icon.getSecond());
                            context.graphics.pose().pushPose();
                            try {
                                // Match the native empty-icon pass at GUI-local Z=100.
                                context.graphics.pose().translate(0, 0, 100);
                                drawAtlasIcon(context.graphics, 0, 0, 0, sprite);
                            } finally {
                                context.graphics.pose().popPose();
                            }
                            hideItem = true;
                        }
                    }
                    if (!hideItem) {
                        context.graphics.pose().pushPose();
                        try {
                            context.graphics.pose().translate(0, 0, 100);
                            if (quickCraftBackground) {
                                context.graphics.fill(0, 0, 16, 16, 0x80FFFFFF);
                            }
                            drawMenuItemStack(context, stack, countString);
                        } finally {
                            context.graphics.pose().popPose();
                        }
                    }
                }
                if ((isHover() || isSelfOrChildHover()) && slot.isHighlightable()) {
                    AbstractContainerScreen.renderSlotHighlight(
                            context.graphics, 0, 0, 0, screen.getSlotColor(slot.index));
                }
            } finally {
                context.pose.popPose();
            }
        }

        private void drawMenuItemStack(GUIContext context, ItemStack stack, String countString) {
            if (stack.isEmpty()) {
                return;
            }
            var slot = getSlot();
            int seed = context.modularUI.getScreen() instanceof AbstractContainerScreen<?> screen
                    ? slot.x + slot.y * screen.getXSize() : 0;
            var graphics = context.graphics;
            if (slot.isFake()) {
                graphics.renderFakeItem(stack, 0, 0, seed);
            } else {
                graphics.renderItem(stack, 0, 0, seed);
            }
            // Real menu slots use the screen's default font, not the carried-item font extension.
            // Keep native full counts, including the yellow quick-craft limit override.
            graphics.renderItemDecorations(context.mc.font, stack, 0, 0, countString);
        }

        @Override
        public void updateSlotPosition() {
            // The existing menu defines the physical slot coordinates and server slot ID.
        }

        @Override
        public boolean isVisible() {
            return super.isVisible() && getSlot().isActive();
        }

        @Override
        public boolean isAllowHitTest() {
            return super.isAllowHitTest() && getSlot().isActive();
        }

        @Override
        public boolean isIntersectWithPoint(double x, double y) {
            // Vanilla accepts one pixel around the 16 px slot without scaling its contents.
            float left = getPositionX();
            float top = getPositionY();
            return x >= left - 1 && x < left + getSizeWidth() + 1
                    && y >= top - 1 && y < top + getSizeHeight() + 1;
        }
    }

    public static <E extends UIElement> E at(E element, int x, int y, int width, int height) {
        element.layout(l -> l.positionType(TaffyPosition.ABSOLUTE).left(x).top(y).width(width).height(height));
        return element;
    }

    /** A GUI-local layer rendered within the owning screen's explicitly positioned draw pass. */
    public static ModularUI localLayer(UIElement root, int width, int height) {
        root.setAllowHitTest(false);
        root.layout(layout -> layout.width(width).height(height));
        var ui = ModularUI.of(UI.of(root));
        ui.init(width, height);
        return ui;
    }

    /** Fixed-height rows for screens with a separate AE2-positioned scrollbar. */
    public static <T> VirtualScrollerView<T> virtualRows(int rowHeight, UIElementProvider<T> rowProvider) {
        var list = new VirtualScrollerView<T>() {
            private double visibleStart;
            private double visibleEnd;

            @Override
            public void refreshVisibleItems(float scrollOffset, float viewportHeight) {
                // Normalized float arithmetic must not shift an integer row by a subpixel.
                int alignedOffset = Math.round(scrollOffset);
                visibleStart = Math.max(0, alignedOffset);
                visibleEnd = visibleStart + Math.max(0, viewportHeight);
                super.refreshVisibleItems(alignedOffset, viewportHeight);
            }

            @Override
            protected UIElement createMountedRow(int index) {
                var row = super.createMountedRow(index);
                long rowTop = (long) index * rowHeight;
                // LDLib includes the row whose start equals the exclusive viewport end.
                // Keep its layout wrapper, but do not draw or hit-test that extra row.
                row.setVisible(rowTop < visibleEnd && rowTop + rowHeight > visibleStart);
                return row;
            }
        };
        // Let the owning screen advance the external scrollbar in whole rows or pages.
        removeBubbleListeners(list, UIEvents.MOUSE_WHEEL);
        list.virtualScrollerViewStyle(s -> s.itemHeightMode(VirtualItemHeightMode.FIXED)
                .estimatedItemHeight(rowHeight).overscanPixels(0));
        list.scrollerStyle(s -> s.mode(ScrollerMode.VERTICAL).verticalScrollDisplay(ScrollDisplay.NEVER));
        list.viewPort(view -> {
            removeBubbleListeners(view, UIEvents.MOUSE_WHEEL);
            view.layout(l -> l.paddingAll(0));
            view.style(s -> s.background(IGuiTexture.EMPTY));
        });
        return list.setItemUIProvider(rowProvider);
    }

    private static void removeBubbleListeners(UIElement element, String event) {
        for (var listener : java.util.List.copyOf(element.getBubbleListeners(event))) {
            element.removeEventListener(event, listener);
        }
    }

    public static void scrollVirtualRows(VirtualScrollerView<?> list, int row, int maxRow) {
        list.verticalScroller.setNormalizedValue(rowFraction(row, maxRow), true);
    }

    /** Aligns a visible LDLib thumb and its native wheel step with the current row window. */
    public static void syncRowThumb(Scroller scroller, int row, int maxRow) {
        scroller.getScrollerStyle().scrollDelta(maxRow <= 0 ? 0 : 1.0F / maxRow);
        scroller.setNormalizedValue(rowFraction(row, maxRow), false);
    }

    /** Keeps a legacy thumb's integer geometry after LDLib updates its percent layout. */
    public static void positionThumbPixels(Scroller scroller, int top, int height) {
        com.lowdragmc.lowdraglib2.gui.ui.Style.importantPipeline(scroller.scrollBar.getLayout(),
                layout -> layout.height(height).top(top));
    }

    public static int centeredThumbRow(double pointerY, int trackHeight, int thumbHeight, int maxRow) {
        double fraction = (pointerY - thumbHeight / 2.0) / (trackHeight - thumbHeight);
        return (int) Math.round(Math.clamp(fraction, 0.0, 1.0) * maxRow);
    }

    private static float rowFraction(int row, int maxRow) {
        return maxRow <= 0 ? 0 : Math.clamp(row, 0, maxRow) / (float) maxRow;
    }

    public static Label pageIndicator(UIElement root, int width, int y, int color) {
        var label = addLabel(root, Component.empty(), color, 0, y, width);
        label.textStyle(style -> style.textAlignHorizontal(Horizontal.CENTER));
        return label;
    }

    public static Label addLabel(UIElement root, Component text, int color, int x, int y, int width) {
        var label = label(text, color);
        label.setAllowHitTest(false);
        root.addChild(at(label, x, y, width, 9));
        return label;
    }

    public static Label label(Component text, int argb) {
        var label = new Label() {
            @Override
            public String name() {
                return "label";
            }

            @Override
            public net.minecraft.client.gui.Font getFont() {
                return Minecraft.getInstance().font;
            }
        };
        label.setText(text);
        label.textStyle(t -> t.font(net.minecraft.network.chat.Style.DEFAULT_FONT).fontSize(9)
                .textColor(argb).textShadow(false).textWrap(TextWrap.HIDE)
                .textAlignVertical(Vertical.TOP));
        return label;
    }

    /** Uses the normal GUI item depth without adding count, durability or cooldown decorations. */
    public static UIElement unadornedItemIcon(ItemStack stack) {
        return unadornedItemIcon(() -> stack);
    }

    public static UIElement unadornedItemIcon(Supplier<ItemStack> stack) {
        return new UIElement().setAllowHitTest(false).style(style -> style.background(
                (graphics, mouseX, mouseY, x, y, width, height, ticks) ->
                        graphics.renderItem(stack.get(), (int) x, (int) y)));
    }

    /** Chooses between two textures every frame. */
    public static IGuiTexture either(BooleanSupplier condition, IGuiTexture whenTrue, IGuiTexture whenFalse) {
        return IGuiTexture.dynamic(() -> condition.getAsBoolean() ? whenTrue : whenFalse);
    }

    public static IGuiTexture solid(IntSupplier argb) {
        return (graphics, mouseX, mouseY, x, y, width, height, partialTicks) -> {
            int color = argb.getAsInt();
            if (color != 0) {
                int left = Math.round(x);
                int top = Math.round(y);
                graphics.fill(left, top, left + Math.round(width), top + Math.round(height), color);
            }
        };
    }

    public static void fillRect(GuiGraphics graphics, int x0, int y0, int x1, int y1, int argb) {
        graphics.fill(x0, y0, x1, y1, argb);
    }

    /** Draws a full stitched sprite in a 16-by-16 GUI slot. */
    public static void drawAtlasIcon(GuiGraphics graphics, float x, float y, float z, TextureAtlasSprite sprite) {
        graphics.blit((int) x, (int) y, (int) z, 16, 16, sprite);
    }

    public static void drawAtlasIcon(GuiGraphics graphics, float x, float y, float z, TextureAtlasSprite sprite,
            RenderType renderType, int color) {
        var buffers = graphics.bufferSource();
        DrawerHelper.drawFluidTexture(buffers.getBuffer(renderType), graphics.pose().last(),
                x, y, sprite, 0, 0, z, color);
        buffers.endBatch(renderType);
    }

    /** Adds dynamic text tooltip lines; {@code null} or empty means no tooltip. */
    public static <E extends UIElement> E tooltip(E element, Supplier<List<Component>> lines) {
        element.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            List<Component> value = lines.get();
            if (value != null && !value.isEmpty()) {
                event.hoverTooltips = tooltips(event).append(value.toArray(Component[]::new));
            }
        });
        return element;
    }

    /** Shows the full item tooltip of a stack while hovered. */
    public static <E extends UIElement> E itemTooltip(E element, Supplier<ItemStack> stack) {
        element.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            ItemStack value = stack.get();
            if (value != null && !value.isEmpty()) {
                event.hoverTooltips = itemTooltip(value, tooltips(event));
            }
        });
        return element;
    }

    public static HoverTooltips itemTooltip(ItemStack stack, HoverTooltips existing) {
        return existing.append(DrawerHelper.getItemToolTip(stack).toArray(Component[]::new))
                .tooltipComponent(stack.getTooltipImage().orElse(existing.tooltipComponent()))
                .stack(stack);
    }

    private static HoverTooltips tooltips(UIEvent event) {
        return event.hoverTooltips == null ? HoverTooltips.empty() : event.hoverTooltips;
    }

    /** Left-click handler that consumes the event. */
    public static <E extends UIElement> E onLeftClick(E element, Runnable action) {
        element.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button == 0) {
                event.stopPropagation();
                action.run();
            }
        });
        return element;
    }

    /**
     * A bare vertical scroller drawn only as a textured thumb, as used by vanilla-style
     * GUIs whose track is part of the background texture.
     */
    public static Scroller.Vertical thumbScroller(IGuiTexture thumb, IGuiTexture thumbHover, int thumbHeight,
            int trackHeight) {
        var scroller = new Scroller.Vertical();
        scroller.headButton(button -> button.setDisplay(false));
        scroller.tailButton(button -> button.setDisplay(false));
        scroller.scrollContainer(container -> container.style(s -> s.background(IGuiTexture.EMPTY)));
        scroller.scrollBar(bar -> bar.buttonStyle(s -> s.baseTexture(thumb).hoverTexture(thumbHover)
                .pressedTexture(thumbHover)));
        scroller.scrollerStyle(s -> s.scrollBarSize(thumbHeight * 100.0F / trackHeight));
        scroller.style(s -> s.background(IGuiTexture.EMPTY));
        scroller.setRange(0.0F, 1.0F);
        return scroller;
    }

    /** Lets the screen own input while the scroller handles thumb layout and hover rendering. */
    public static Scroller.Vertical displayThumbScroller(IGuiTexture thumb, IGuiTexture thumbHover, int thumbHeight,
            int trackHeight) {
        var scroller = thumbScroller(thumb, thumbHover, thumbHeight, trackHeight);
        removeBubbleListeners(scroller.scrollBar, UIEvents.MOUSE_DOWN);
        removeBubbleListeners(scroller.scrollContainer, UIEvents.MOUSE_DOWN);
        removeBubbleListeners(scroller.scrollContainer, UIEvents.MOUSE_WHEEL);
        return scroller;
    }

    /** Uses LDLib's fill layout and samples a pixel-aligned sprite at each step. */
    public static ProgressBar spriteProgress(FillDirection direction, IGuiTexture[] slices) {
        var progress = pixelProgress(direction, slices.length - 1);
        progress.bar(bar -> bar.style(style -> style.background(
                IGuiTexture.dynamic(() -> {
                    int pixels = progress.getValue().intValue();
                    return pixels == 0 ? IGuiTexture.EMPTY : slices[pixels];
                }))));
        return progress;
    }

    public static ProgressBar pixelProgress(FillDirection direction, int length) {
        var progress = new ProgressBar() {
            @Override
            protected void updateProgressBarStyle(float normalizedValue) {
                super.updateProgressBarStyle(normalizedValue);
                int pixels = getValue().intValue();
                com.lowdragmc.lowdraglib2.gui.ui.Style.importantPipeline(bar.getLayout(), layout -> {
                    switch (getProgressBarStyle().fillDirection()) {
                        case LEFT_TO_RIGHT, RIGHT_TO_LEFT -> layout.width(pixels).heightPercent(100);
                        case UP_TO_DOWN, DOWN_TO_UP -> layout.height(pixels).widthPercent(100);
                    }
                });
            }
        }.setRange(0, length);
        progress.progressBarStyle(style -> style.fillDirection(direction).interpolate(false));
        progress.setAllowHitTest(false);
        progress.label(label -> label.setDisplay(false));
        progress.barContainer(container -> {
            container.layout(layout -> layout.paddingAll(0));
            container.style(style -> style.background(IGuiTexture.EMPTY));
        });
        return progress;
    }
}
