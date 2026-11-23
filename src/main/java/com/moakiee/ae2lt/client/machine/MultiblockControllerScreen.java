package com.moakiee.ae2lt.client.machine;

import com.lowdragmc.lowdraglib2.gui.LDLibFonts;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.moakiee.ae2lt.client.ldlib.LdlibScreens;
import com.moakiee.ae2lt.client.ldlib.LdlibStyleSprite;
import com.moakiee.ae2lt.client.ldlib.LdlibText;

import appeng.client.gui.style.PaletteColor;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.client.gui.AE2GuiPalette;

import com.moakiee.ae2lt.logic.compute.ComputeTier;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Block;

/**
 * Controller status screen drawn entirely by one LDLib root tree. Subclasses describe what to
 * show through {@link #view()} and add their side controls in {@link #addControls}; the tree
 * reads the latest view while drawing.
 */
public abstract class MultiblockControllerScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
    protected static final int BLUE = 221, RED = 228, AMBER = 235, GREEN = 242;
    private static final int BAR_WIDTH = 150;
    private static final int METRIC_ROWS = 3;
    private static final int METRIC_STEP = 39;

    /** One bar row: label, right-aligned value, and a fill fraction (infinity draws the striped bar). */
    public record Metric(String label, String value, double fraction, int barV) {
    }

    /**
     * Everything the status tree shows. A non-null {@code issue} means the multiblock is not
     * formed: the issue lines and the unformed hint replace the metrics and footer.
     */
    public record View(ComputeTier tier, Block core, Component name, boolean formed, boolean warning,
            Component issue, List<Metric> metrics, double[] heatMarks, String footer, int footerY) {
    }

    private final LdlibStyleSprite atlas;
    private final int machineIconV;
    private View view;

    protected MultiblockControllerScreen(T menu, Inventory inventory, Component title, String atlasName, int machineIconV) {
        super(menu, inventory, title);
        atlas = new LdlibStyleSprite(ResourceLocation.fromNamespaceAndPath(
                AE2LightningTech.MODID, "textures/guis/" + atlasName + ".png"), 256, 256);
        this.machineIconV = machineIconV;
        imageWidth = 186;
        imageHeight = 220;
        titleLabelY = 10_000;
        inventoryLabelY = 10_000;
    }

    protected static int textColor() {
        return AE2GuiPalette.color(PaletteColor.DEFAULT_TEXT_COLOR);
    }

    /** Reads the menu into what the tree should currently show. */
    protected abstract View view();

    /** Adds side controls to the root, in GUI-local coordinates (may lie outside the root bounds). */
    protected abstract void addControls(UIElement root);

    @Override
    protected void init() {
        super.init();
        view = view();
        LdlibScreens.embed(this, buildRoot(), this::addRenderableWidget);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        view = view();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // Drawn by the LDLib tree.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    private UIElement buildRoot() {
        var root = new UIElement();
        root.layout(l -> l.width(imageWidth).height(imageHeight));
        root.style(s -> s.background(atlas.slice(0, 0, imageWidth, imageHeight)));
        root.setAllowHitTest(false);
        root.setOverflowVisible(true);

        // Header: title, core frame, core block icon, machine icon, status icon and state texts.
        text(root, () -> title.getString(), 28, 8, 145, false);
        part(root, 20, 36, 22, 32, (graphics, mouseX, mouseY, x, y, width, height, ticks) -> {
            if (view.tier() != null && view.core() != null) {
                int frameV = switch (view.tier()) {
                    case BASELINE -> 105;
                    case QUANTUM -> 71;
                    case OVERLOAD -> 37;
                    case MULTIDIMENSIONAL -> 3;
                };
                atlas.draw(graphics, (int) x, (int) y, 188, frameV, 22, 32);
            }
        });
        part(root, 23, 44, 16, 16, (graphics, mouseX, mouseY, x, y, width, height, ticks) -> {
            if (view.tier() != null && view.core() != null) {
                var sprite = minecraft.getBlockRenderer().getBlockModelShaper()
                        .getParticleIcon(view.core().defaultBlockState());
                LdlibScreens.drawAtlasIcon(graphics, x, y, 0, sprite);
            }
        });
        part(root, 110, 44, 16, 16, atlas.slice(215, machineIconV, 16, 16));
        part(root, 56, 56, 8, 8, (graphics, mouseX, mouseY, x, y, width, height, ticks) ->
                atlas.draw(graphics, (int) x, (int) y, 216, !view.formed() ? 40 : view.warning() ? 50 : 60, 8, 8));
        text(root, () -> view.name().getString(), 55, 42, 46, false);
        text(root, () -> Component.translatable("ae2lt.controller."
                + (!view.formed() ? "unformed" : view.warning() ? "warning" : "ready")).getString(), 67, 56, 35, false);

        // Formed: bar rows and heat range marks.
        for (int row = 0; row < METRIC_ROWS; row++) {
            int index = row;
            int offset = row * METRIC_STEP;
            Supplier<Metric> metric = () -> view.issue() == null && index < view.metrics().size()
                    ? view.metrics().get(index) : null;
            text(root, () -> metric.get() == null ? null
                    : Component.translatable(metric.get().label()).getString(), 31, 82 + offset, 136, false);
            text(root, () -> metric.get() == null ? null : metric.get().value(), 18, 94 + offset, BAR_WIDTH, true);
            part(root, 18, 104 + offset, BAR_WIDTH, 5, (graphics, mouseX, mouseY, x, y, width, height, ticks) -> {
                var bar = metric.get();
                if (bar == null) {
                    return;
                }
                if (bar.fraction() == Double.POSITIVE_INFINITY) {
                    for (int start = 0; start < BAR_WIDTH; start += 12) {
                        atlas.draw(graphics, (int) x + start, (int) y, 1 + start, bar.barV(),
                                Math.min(6, BAR_WIDTH - start), 5);
                    }
                } else if (bar.fraction() > 0) {
                    int fill = Math.clamp(Math.round(BAR_WIDTH * bar.fraction()), 1, BAR_WIDTH);
                    atlas.draw(graphics, (int) x, (int) y, 1, bar.barV(), fill, 5);
                }
            });
        }
        part(root, 18, 103, BAR_WIDTH + 1, 7, (graphics, mouseX, mouseY, x, y, width, height, ticks) -> {
            if (view.issue() == null && view.heatMarks() != null) {
                for (double edge : view.heatMarks()) {
                    int marker = (int) x + (int) Math.round(BAR_WIDTH * edge);
                    LdlibScreens.fillRect(graphics, marker, (int) y, marker + 1, (int) y + 7, 0xFFF2F2F2);
                }
            }
        });
        // Footer lines: the formed footer sits at its own height, the unformed hint at 195.
        text(root, () -> view.issue() != null
                ? Component.translatable("ae2lt.matrix.gui.hint_unformed").getString()
                : view.footerY() == 195 ? view.footer() : null, 16, 195, 154, false);
        text(root, () -> view.issue() == null && view.footerY() != 195 ? view.footer() : null,
                16, 199, 154, false);

        // Unformed: the scan issue, wrapped into the three panels.
        part(root, 31, 82, 135, 3 * METRIC_STEP, (graphics, mouseX, mouseY, x, y, width, height, ticks) -> {
            if (view.issue() != null) {
                drawIssue(graphics, x, y, view.issue());
            }
        });

        addControls(root);
        return root;
    }

    private void drawIssue(GuiGraphics graphics, float left, float top, Component issue) {
        var lines = font.split(issue, 135);
        int linesPerPanel = Math.max(2, Math.ceilDiv(lines.size(), 3));
        float step = 21F / linesPerPanel;
        float scale = Math.min(1F, step / font.lineHeight);
        for (int index = 0; index < lines.size(); index++) {
            float y = (index / linesPerPanel) * METRIC_STEP + (index % linesPerPanel) * step;
            graphics.pose().pushPose();
            graphics.pose().translate(left, top + y, 0);
            graphics.pose().scale(scale, scale, 1F);
            LDLibFonts.drawText(graphics, font, lines.get(index), 0, 0, textColor(), false);
            graphics.pose().popPose();
        }
    }

    /** A noninteractive element drawn by {@code texture}. */
    protected static UIElement part(UIElement root, int x, int y, int width, int height, IGuiTexture texture) {
        var element = LdlibScreens.at(new UIElement().setAllowHitTest(false), x, y, width, height);
        element.style(s -> s.background(texture));
        root.addChild(element);
        return element;
    }

    /**
     * One line of text that shrinks to fit {@code width}. Null or empty text draws nothing; with
     * {@code alignRight} a shorter line is flush with the box's right edge.
     */
    protected UIElement text(UIElement root, Supplier<String> value, int x, int y, int width, boolean alignRight) {
        return part(root, x, y, width, 9, (graphics, mouseX, mouseY, left, top, boxWidth, boxHeight, ticks) -> {
            String line = value.get();
            if (line == null || line.isEmpty()) {
                return;
            }
            int lineWidth = font.width(line);
            float scale = Math.min(1F, (float) width / Math.max(1, lineWidth));
            float shift = alignRight ? width - Math.min(width, lineWidth) : 0;
            graphics.pose().pushPose();
            graphics.pose().translate(left + shift, top, 0);
            graphics.pose().scale(scale, scale, 1F);
            LdlibText.drawString(graphics, font, line, 0, 0, textColor(), false);
            graphics.pose().popPose();
        });
    }

    protected static String formatCount(long value) {
        return value == Long.MAX_VALUE ? "∞" : String.format(Locale.ROOT, "%,d", value);
    }

    protected static String percent(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value * 100.0D);
    }
}
