package com.moakiee.ae2lt.client.machine;

import java.util.ArrayList;
import java.util.List;
import com.moakiee.ae2lt.machine.largeoverload.LargeFactoryComponent;
import com.moakiee.ae2lt.machine.largeoverload.LargeFactoryMenu;
import com.moakiee.ae2lt.machine.largeoverload.LargeFactorySnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class LargeFactoryScreen extends AbstractContainerScreen<LargeFactoryMenu> {
    private final List<Button> rowButtons = new ArrayList<>();
    private Button mode, power, recovery;
    public LargeFactoryScreen(LargeFactoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 320; imageHeight = 218; inventoryLabelY = 124;
    }
    private boolean processingHatch() { return menu.component.isPatternHatch() || menu.component == LargeFactoryComponent.CRYSTAL_HATCH; }
    @Override protected void init() {
        super.init();
        // Keep the player hotbar above recipe-viewer search bars, including a 240px-high GUI.
        topPos = Math.max(0, (height - imageHeight - 22) / 2);
        rowButtons.clear();
        mode = button(12, 22, 72, "mode", LargeFactoryMenu.MODE);
        mode.visible = processingHatch();
        if (menu.component == LargeFactoryComponent.CONTROLLER) button(12, 22, 72, "build", LargeFactoryMenu.BUILD);
        power = button(87, 22, 91, "network_energy", LargeFactoryMenu.POWER);
        power.visible = menu.pages() == 1;
        button(182, 22, 61, "preview", LargeFactoryMenu.PREVIEW);
        button(246, 22, 61, "scan", LargeFactoryMenu.SCAN);
        if (menu.pages() > 1) {
            button(87, 22, 24, "previous", LargeFactoryMenu.PAGE_PREVIOUS);
            button(150, 22, 24, "next", LargeFactoryMenu.PAGE_NEXT);
        }
        if (processingHatch()) {
            button(184, 172, 24, "previous", LargeFactoryMenu.ENTRIES_PREVIOUS);
            button(284, 172, 24, "next", LargeFactoryMenu.ENTRIES_NEXT);
            recovery = button(184, 196, 124, "recover", LargeFactoryMenu.RECOVER);
            for (int i = 0; i < 6; i++) {
                final int row = i;
                rowButtons.add(addRenderableWidget(Button.builder(Component.empty(), ignored -> {
                    if (row < menu.snapshot.rows().size()) send(LargeFactoryMenu.TOGGLE_ENTRY + menu.snapshot.rows().get(row).index());
                }).bounds(leftPos + 184, topPos + 58 + i * 18, 124, 18).build()));
            }
        }
        updateButtons();
    }
    private Button button(int x, int y, int width, String label, int action) {
        return addRenderableWidget(Button.builder(text(label), ignored -> send(action))
                .bounds(leftPos + x, topPos + y, width, 18).build());
    }
    private void send(int action) {
        if (minecraft == null || minecraft.gameMode == null) return;
        if (action == LargeFactoryMenu.PAGE_NEXT) menu.setPage(menu.page() + 1);
        if (action == LargeFactoryMenu.PAGE_PREVIOUS) menu.setPage(menu.page() - 1);
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action);
    }
    @Override protected void containerTick() { super.containerTick(); updateButtons(); }
    private void updateButtons() {
        mode.setMessage(text(menu.snapshot.passive() ? "passive" : "active"));
        power.setMessage(text(menu.snapshot.networkEnergy() ? "network_energy" : "external_only"));
        if (recovery != null) recovery.setMessage(text("recover_count", menu.snapshot.pendingTypes()));
        for (int i = 0; i < rowButtons.size(); i++) {
            var button = rowButtons.get(i);
            button.visible = i < menu.snapshot.rows().size();
            if (button.visible) {
                var row = menu.snapshot.rows().get(i);
                var label = (row.enabled() ? "● " : "○ ") + row.output().what().getDisplayName().getString();
                button.setMessage(Component.literal(font.plainSubstrByWidth(label, 116)));
            }
        }
    }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff15232d);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + 18, 0xff233a47);
        graphics.fill(leftPos + 180, topPos + 43, leftPos + 181, topPos + 211, 0xff416775);
        for (var slot : menu.slots) if (slot.isActive() && slot.x >= 0) {
            int x = leftPos + slot.x, y = topPos + slot.y;
            graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xff46606c);
            graphics.fill(x, y, x + 16, y + 16, 0xff0c151d);
        }
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        var s = menu.snapshot;
        graphics.drawString(font, title, 10, 6, 0xffd8f1f5, false);
        graphics.drawString(font, font.plainSubstrByWidth(text("status." + s.status()).getString(), 162), 12, 46,
                s.formed() ? 0xff8fe8b5 : 0xffefb96d, false);
        graphics.drawString(font, text(processingHatch() ? "entries" : "factory_status"), 184, 46, 0xffa7c4d0, false);
        if (menu.machineSlots() <= 27) graphics.drawString(font, playerInventoryTitle, 12, 124, 0xffa7c4d0, false);
        if (menu.pages() > 1) graphics.drawCenteredString(font, (menu.page() + 1) + " / " + menu.pages(), 130, 27, 0xffd8f1f5);
        if (menu.machineSlots() == 0) {
            int y = 66;
            if (menu.machineSlots() > 0) y += 26;
            graphics.drawString(font, text("dimensions"), 12, y, 0xffd8f1f5, false);
            y += 12;
            for (var issue : s.issues()) {
                if (y > 112) break;
                String line = text("issue." + issue.problem()).getString() + " " + (issue.problem().startsWith("missing_") ? issue.expected() : issue.position().toShortString());
                graphics.drawString(font, font.plainSubstrByWidth(line, 160), 12, y, 0xffefb96d, false);
                y += 10;
            }
        }
        int y = 67;
        if (processingHatch()) {
            graphics.drawCenteredString(font, (s.entryPage() + 1) + " / " + Math.max(1, (s.entryCount() + 5) / 6), 246, 177, 0xffa7c4d0);
        } else {
            graphics.drawString(font, text("core." + s.core()), 184, y, 0xffa6daef, false); y += 18;
            graphics.drawString(font, text("operations"), 184, y, 0xffa7c4d0, false); y += 12;
            graphics.drawString(font, compact(s.remainingOperations()) + " / " + compact(s.operationsPerTick()), 184, y, 0xffd8f1f5, false); y += 18;
            graphics.drawString(font, text("energy"), 184, y, 0xffa7c4d0, false); y += 12;
            graphics.drawString(font, compact(s.storedEnergy()) + " / " + compact(s.energyCapacity()), 184, y, 0xffd8f1f5, false);
        }
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        for (int i = 0; i < rowButtons.size(); i++) if (rowButtons.get(i).visible && rowButtons.get(i).isHovered()) {
            var row = menu.snapshot.rows().get(i);
            var lines = new ArrayList<Component>();
            lines.add(row.output().what().getDisplayName().copy().append(" × " + row.output().amount()));
            lines.add(text("status." + row.status()));
            if (!row.recipe().isEmpty()) {
                lines.add(Component.literal(row.recipe()));
                lines.add(text("source_operations", row.operations()));
                lines.add(text("source_cost", row.energy(), row.high(), row.extreme()));
                lines.add(text("compensation"));
            }
            lines.add(text(row.enabled() ? "click_disable" : "click_enable"));
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
            break;
        }
    }
    private static Component text(String key, Object... arguments) { return Component.translatable("ae2lt.large_factory." + key, arguments); }
    private static String compact(long value) {
        if (value < 10_000) return Long.toString(value);
        if (value < 1_000_000) return String.format(java.util.Locale.ROOT, "%.1fk", value / 1_000d);
        if (value < 1_000_000_000) return String.format(java.util.Locale.ROOT, "%.1fM", value / 1_000_000d);
        return String.format(java.util.Locale.ROOT, "%.1fG", value / 1_000_000_000d);
    }
}
