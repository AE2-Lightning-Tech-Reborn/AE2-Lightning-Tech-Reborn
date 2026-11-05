package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.dimension.PigmeeDimensionData;
import com.moakiee.ae2lt.dimension.PigmeeDimensionLayout;
import com.moakiee.ae2lt.dimension.PigmeeDimensionMaterials;
import com.moakiee.ae2lt.dimension.PigmeeDimensionPattern;
import com.moakiee.ae2lt.grid.FrequencySecurityLevel;
import com.moakiee.ae2lt.menu.PigmeeDimensionMenu;
import com.moakiee.ae2lt.network.PigmeeDimensionActionPacket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.PacketDistributor;

/** A small dimension list plus creation/settings form, using the same OWNER vocabulary as frequencies. */
public final class PigmeeDimensionScreen extends AbstractContainerScreen<PigmeeDimensionMenu> {
    private final List<PigmeeDimensionData.Entry> entries;
    private final List<Block> materials = PigmeeDimensionMaterials.all();
    private final List<Button> rows = new ArrayList<>();
    private final List<AbstractWidget> normalWidgets = new ArrayList<>(), editorWidgets = new ArrayList<>();
    private final List<MaterialButton> cells = new ArrayList<>();
    private int page, selected = -1, frame, center;
    private int brush, lastPainted = -1;
    private byte role;
    private boolean shared, initialized, editing, painting;
    private byte[] pattern = new byte[256], draft = new byte[256];
    private EditBox name, member;
    private Button frameButton, centerButton, patternButton, securityButton, roleButton, saveButton, bindButton, enterButton, memberButton;

    public PigmeeDimensionScreen(PigmeeDimensionMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        entries = menu.entries();
        imageWidth = 318;
        imageHeight = 238;
        inventoryLabelY = -1000;
    }

    private static Component text(String key) { return Component.translatable("gui.ae2lt.pigmee_dimension." + key); }

    @Override
    protected void init() {
        String previousName = name == null ? "" : name.getValue();
        String previousMember = member == null ? "" : member.getValue();
        super.init();
        rows.clear();
        normalWidgets.clear();
        editorWidgets.clear();
        cells.clear();
        for (int i = 0; i < 5; i++) {
            final int row = i;
            rows.add(addRenderableWidget(Button.builder(Component.empty(), b -> select(page * 5 + row))
                    .bounds(leftPos + 8, topPos + 38 + i * 20, 96, 20).build()));
        }
        addRenderableWidget(Button.builder(Component.literal("<"), b -> { if (page > 0) page--; refresh(); })
                .bounds(leftPos + 8, topPos + 140, 44, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> { if ((page + 1) * 5 < entries.size()) page++; refresh(); })
                .bounds(leftPos + 60, topPos + 140, 44, 20).build());
        addRenderableWidget(Button.builder(text("new"), b -> select(-1))
                .bounds(leftPos + 8, topPos + 164, 96, 20).build());
        name = addRenderableWidget(new EditBox(font, leftPos + 114, topPos + 38, 196, 18, text("name")));
        name.setMaxLength(24);
        frameButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            frame = Math.floorMod(frame + (hasShiftDown() ? -1 : 1), materials.size()); refresh();
        }).bounds(leftPos + 114, topPos + 70, 140, 20).build());
        centerButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            center = Math.floorMod(center + (hasShiftDown() ? -1 : 1), materials.size()); refresh();
        }).bounds(leftPos + 114, topPos + 103, 140, 20).build());
        patternButton = addRenderableWidget(Button.builder(text("edit_pattern"), b -> {
            draft = pattern.clone();
            brush = frame;
            editing = true;
            updateCells();
            refresh();
        }).bounds(leftPos + 114, topPos + 70, 140, 20).build());
        securityButton = addRenderableWidget(Button.builder(Component.empty(), b -> { shared = !shared; refresh(); })
                .bounds(leftPos + 114, topPos + 128, 196, 20).build());
        member = addRenderableWidget(new EditBox(font, leftPos + 114, topPos + 164, 196, 18, text("member")));
        member.setMaxLength(36);
        roleButton = addRenderableWidget(Button.builder(Component.empty(), b -> { role = (byte) ((role + 1) % 4); refresh(); })
                .bounds(leftPos + 114, topPos + 187, 92, 20).build());
        memberButton = addRenderableWidget(Button.builder(text("apply_member"), b -> send(PigmeeDimensionActionPacket.MEMBER))
                .bounds(leftPos + 212, topPos + 187, 98, 20).build());
        saveButton = addRenderableWidget(Button.builder(Component.empty(), b -> send(selected < 0
                ? PigmeeDimensionActionPacket.CREATE : PigmeeDimensionActionPacket.CONFIGURE))
                .bounds(leftPos + 114, topPos + 214, 86, 20).build());
        enterButton = addRenderableWidget(Button.builder(text("enter"), b -> send(PigmeeDimensionActionPacket.ENTER))
                .bounds(leftPos + 204, topPos + 214, 48, 20).build());
        bindButton = addRenderableWidget(Button.builder(text("bind"), b -> send(PigmeeDimensionActionPacket.BIND))
                .bounds(leftPos + 256, topPos + 214, 54, 20).build());
        if (!initialized) {
            int bound = -1;
            for (int i = 0; i < entries.size(); i++) if (entries.get(i).id() == menu.bound()) bound = i;
            select(bound);
        } else {
            name.setValue(previousName);
            member.setValue(previousMember);
        }
        children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).forEach(normalWidgets::add);
        initEditor();
        initialized = true;
        refresh();
    }

    private void select(int index) {
        selected = index >= 0 && index < entries.size() ? index : -1;
        var entry = selected < 0 ? null : entries.get(selected);
        name.setValue(entry == null ? text("default_name").getString() : entry.access().getName());
        frame = materials.indexOf(entry == null ? PigmeeDimensionMaterials.defaultFrame().getBlock() : entry.palette().frame().getBlock());
        center = materials.indexOf(entry == null ? PigmeeDimensionMaterials.defaultCenter().getBlock() : entry.palette().center().getBlock());
        shared = entry != null && entry.access().getSecurity() == FrequencySecurityLevel.PUBLIC;
        pattern = PigmeeDimensionPattern.encode(entry != null && entry.palette().custom() ? entry.palette().cells()
                : PigmeeDimensionPattern.template(false, materials.get(frame).defaultBlockState(), materials.get(center).defaultBlockState()));
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < rows.size(); i++) {
            int index = page * 5 + i;
            var row = rows.get(i);
            row.visible = index < entries.size();
            if (row.visible) {
                var entry = entries.get(index);
                String label = (index == selected ? "> " : "") + "#" + entry.id() + " " + entry.access().getName();
                row.setMessage(Component.literal(font.plainSubstrByWidth(label, 86)));
                row.setTooltip(Tooltip.create(Component.literal(label + " · " + entry.access().getPlayerAccess(menu.viewer()))));
            }
        }
        frameButton.setMessage(materials.get(frame).getName());
        centerButton.setMessage(materials.get(center).getName());
        securityButton.setMessage(text(shared ? "public" : "private"));
        roleButton.setMessage(text("role_" + role));
        saveButton.setMessage(text(selected < 0 ? "create" : "save"));
        boolean owner = selected < 0 || entries.get(selected).access().getPlayerAccess(menu.viewer()).isOwner();
        saveButton.active = owner && (selected >= 0 || menu.mayBind() && menu.owned() < menu.limit());
        frameButton.active = centerButton.active = patternButton.active = securityButton.active = owner;
        name.setEditable(owner);
        enterButton.active = selected >= 0;
        bindButton.active = selected >= 0 && menu.mayBind();
        boolean manager = selected >= 0 && entries.get(selected).access().getPlayerAccess(menu.viewer()).isManager();
        roleButton.active = memberButton.active = manager;
        member.setEditable(manager);
        normalWidgets.forEach(widget -> widget.visible = !editing);
        editorWidgets.forEach(widget -> widget.visible = editing);
        for (int i = 0; i < rows.size(); i++) rows.get(i).visible = !editing && page * 5 + i < entries.size();
        frameButton.visible = centerButton.visible = !editing && !menu.custom();
        patternButton.visible = !editing && menu.custom();
    }

    private void send(int action) {
        PacketDistributor.sendToServer(new PigmeeDimensionActionPacket(menu.containerId, action,
                selected < 0 ? 0 : entries.get(selected).id(), name.getValue(), frame, center, shared, member.getValue(), role,
                menu.custom() ? pattern : new byte[0]));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF20252B);
        if (editing) return;
        graphics.fill(leftPos + 108, topPos + 34, leftPos + 109, topPos + 234, 0xFF616B74);
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                Block block = menu.custom() ? materials.get(Byte.toUnsignedInt(pattern[z * 16 + x]))
                        : switch (PigmeeDimensionLayout.cell(menu.even(), x, z)) {
                    case FRAME -> materials.get(frame);
                    case CENTER -> materials.get(center);
                    case FLOOR -> PigmeeDimensionMaterials.floor().getBlock();
                };
                var sprite = minecraft.getBlockRenderer().getBlockModel(block.defaultBlockState()).getParticleIcon();
                graphics.blit(leftPos + 262 + x * 3, topPos + 70 + z * 3, 0, 3, 3, sprite);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        if (editing) {
            graphics.drawString(font, text("editor_title"), 8, 8, 0xFFFFFF, false);
            graphics.drawString(font, text("paint_help"), 8, 24, 0xCAD2DA, false);
            graphics.drawWordWrap(font, materials.get(brush).getName(), 180, 42, 130, 0xFFFFFF);
            return;
        }
        graphics.drawString(font, title, 8, 8, 0xFFFFFF, false);
        graphics.drawString(font, Component.translatable("gui.ae2lt.pigmee_dimension.quota", menu.owned(), menu.limit()), 8, 23, 0xCAD2DA, false);
        if (menu.custom()) graphics.drawWordWrap(font, text("custom_help"), 114, 95, 140, 0xCAD2DA);
        else {
            graphics.drawString(font, text("frame"), 114, 59, 0xFFFFFF, false);
            graphics.drawString(font, text("center"), 114, 92, 0xFFFFFF, false);
        }
        graphics.drawString(font, text("member"), 114, 152, 0xFFFFFF, false);
        graphics.drawWordWrap(font, text(menu.custom() ? "custom_size" : menu.even() ? "even_size" : "odd_size"), 8, 190, 96, 0xCAD2DA);
        graphics.drawWordWrap(font, text("new_chunks"), 8, 215, 96, 0xCAD2DA);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    private void initEditor() {
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            var button = new MaterialButton(leftPos + 8 + x * 10, topPos + 42 + z * 10, 10, z * 16 + x, false);
            cells.add(button);
            editorWidgets.add(addRenderableWidget(button));
        }
        for (int i = 0; i < materials.size(); i++) {
            editorWidgets.add(addRenderableWidget(new MaterialButton(leftPos + 180 + i % 6 * 20,
                    topPos + 72 + i / 6 * 20, 20, i, true)));
        }
        editorButton("odd_template", 8, 74, () -> useTemplate(false));
        editorButton("even_template", 86, 74, () -> useTemplate(true));
        editorButton("fill", 164, 50, () -> { Arrays.fill(draft, (byte) brush); updateCells(); });
        editorButton("done", 218, 44, () -> closeEditor(true));
        editorButton("cancel", 266, 44, () -> closeEditor(false));
    }

    private void editorButton(String label, int x, int width, Runnable action) {
        editorWidgets.add(addRenderableWidget(Button.builder(text(label), b -> action.run())
                .bounds(leftPos + x, topPos + 208, width, 20).build()));
    }

    private void useTemplate(boolean even) {
        draft = PigmeeDimensionPattern.encode(PigmeeDimensionPattern.template(even,
                materials.get(frame).defaultBlockState(), materials.get(center).defaultBlockState()));
        updateCells();
    }

    private void closeEditor(boolean apply) {
        if (apply) pattern = draft.clone();
        editing = painting = false;
        lastPainted = -1;
        setFocused((net.minecraft.client.gui.components.events.GuiEventListener) null);
        refresh();
    }

    private void updateCells() { cells.forEach(MaterialButton::updateLabel); }

    private int cellAt(double mouseX, double mouseY) {
        double x = mouseX - leftPos - 8, z = mouseY - topPos - 42;
        return x < 0 || z < 0 || x >= 160 || z >= 160 ? -1 : (int) z / 10 * 16 + (int) x / 10;
    }

    private void paint(int cell) {
        draft[cell] = (byte) brush;
        cells.get(cell).updateLabel();
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        int cell = editing ? cellAt(x, y) : -1;
        if (cell >= 0 && (button == 0 || button == 1)) {
            if (button == 1) brush = Byte.toUnsignedInt(draft[cell]);
            else {
                paint(cell);
                painting = true;
                lastPainted = cell;
                setFocused(cells.get(cell));
            }
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (editing && painting && button == 0) {
            int cell = cellAt(x, y);
            if (cell >= 0) {
                int from = lastPainted < 0 ? cell : lastPainted;
                int x0 = from % 16, z0 = from / 16, x1 = cell % 16, z1 = cell / 16;
                int steps = Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0));
                for (int i = 0; i <= steps; i++) {
                    float t = steps == 0 ? 0 : (float) i / steps;
                    paint(Math.round(z0 + (z1 - z0) * t) * 16 + Math.round(x0 + (x1 - x0) * t));
                }
            }
            lastPainted = cell;
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (editing && painting && button == 0) { painting = false; lastPainted = -1; return true; }
        return super.mouseReleased(x, y, button);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (editing && key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) { closeEditor(false); return true; }
        return super.keyPressed(key, scanCode, modifiers);
    }

    private final class MaterialButton extends AbstractButton {
        private final int index;
        private final boolean selector;

        private MaterialButton(int x, int y, int size, int index, boolean selector) {
            super(x, y, size, size, Component.empty());
            this.index = index;
            this.selector = selector;
            updateLabel();
        }

        private int material() { return selector ? index : Byte.toUnsignedInt(draft[index]); }

        private void updateLabel() {
            var label = materials.get(material()).getName();
            if (!selector) label = Component.literal("(" + index % 16 + ", " + index / 16 + ") ").append(label);
            setMessage(label);
            setTooltip(Tooltip.create(label));
        }

        @Override public void onPress() { if (selector) brush = index; else paint(index); }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int color = selector && brush == index ? 0xFFFFD66A : isHoveredOrFocused() ? 0xFFFFFFFF : 0xFF59616B;
            graphics.fill(getX(), getY(), getX() + width, getY() + height, color);
            var sprite = minecraft.getBlockRenderer().getBlockModel(materials.get(material()).defaultBlockState()).getParticleIcon();
            graphics.blit(getX() + 1, getY() + 1, 0, width - 2, height - 2, sprite);
        }

        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }
}
