package com.moakiee.ae2lt.client.machine;

import java.util.List;
import java.util.ArrayList;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.StyleManager;
import appeng.client.gui.widgets.IconButton;
import appeng.client.gui.widgets.ToggleButton;
import com.moakiee.ae2lt.client.gui.LightningStatusIconWidget;
import com.moakiee.ae2lt.client.gui.LightningStatusLines;
import com.moakiee.ae2lt.client.widgets.PageButton;
import com.moakiee.ae2lt.client.widgets.PageInput;
import com.moakiee.ae2lt.client.TextureToggleButton;
import com.moakiee.ae2lt.machine.largeoverload.LargeFactoryComponent;
import com.moakiee.ae2lt.machine.largeoverload.LargeFactoryMenu;
import com.moakiee.ae2lt.machine.largeoverload.LargeFactoryOperationBudget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** Uses the expanded provider's AE2 layout, inventory slots and toolbar controls. */
public final class LargeFactoryScreen extends AEBaseScreen<LargeFactoryMenu> {
    private final PageButton pageButton;
    private final TextureToggleButton modeButton;
    private final ToggleButton powerButton;

    public LargeFactoryScreen(LargeFactoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, StyleManager.loadStyleDoc(menu.machineSlots() > 0
                ? "/screens/large_factory_pattern.json" : "/screens/large_factory.json"));
        widgets.add("lightningStatus", new LightningStatusIconWidget(this::statusLines));
        pageButton = new PageButton(this::isHandlingRightClick,
                () -> send(LargeFactoryMenu.PAGE_PREVIOUS), () -> send(LargeFactoryMenu.PAGE_NEXT));
        addToLeftToolbar(pageButton);
        modeButton = new TextureToggleButton(TextureToggleButton.ButtonType.MODE, ignored -> send(LargeFactoryMenu.MODE));
        modeButton.setTooltipOn(List.of(text("passive")));
        modeButton.setTooltipOff(List.of(text("active")));
        if (processingHatch()) addToLeftToolbar(modeButton);
        powerButton = new ToggleButton(Icon.POWER_UNIT_AE, Icon.POWER_UNIT_RF, ignored -> send(LargeFactoryMenu.POWER));
        powerButton.setTooltipOn(List.of(text("network_energy")));
        powerButton.setTooltipOff(List.of(text("external_only")));
        addToLeftToolbar(powerButton);
        if (menu.component == LargeFactoryComponent.CONTROLLER) {
            toolbar(Icon.CRAFT_HAMMER, "build", LargeFactoryMenu.BUILD);
            toolbar(Icon.OVERLAY_ON, "preview", LargeFactoryMenu.PREVIEW);
            toolbar(Icon.SCHEDULING_DEFAULT, "scan", LargeFactoryMenu.SCAN);
        }
    }
    private void toolbar(Icon icon, String label, int action) {
        var button = new IconButton(ignored -> send(action)) {
            @Override protected Icon getIcon() { return icon; }
            @Override public List<Component> getTooltipMessage() { return List.of(text(label)); }
        };
        button.setMessage(text(label));
        addToLeftToolbar(button);
    }
    boolean processingHatch() { return menu.component.isPatternHatch() || menu.component == LargeFactoryComponent.CRYSTAL_HATCH; }
    void send(int action) {
        if (minecraft == null || minecraft.gameMode == null) return;
        if (action == LargeFactoryMenu.PAGE_NEXT) menu.setPage(menu.page() + 1);
        if (action == LargeFactoryMenu.PAGE_PREVIOUS) menu.setPage(menu.page() - 1);
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action);
    }
    @Override protected void init() {
        super.init();
        // All four pages share the first page's coordinates; inactive slots cannot be clicked or shift-moved.
        for (int i = 36; i < menu.machineSlots(); i++) {
            menu.slots.get(i).x = menu.slots.get(i % 36).x;
            menu.slots.get(i).y = menu.slots.get(i % 36).y;
        }
    }
    @Override protected void updateBeforeRender() {
        super.updateBeforeRender();
        setTextContent("dialog_title", title);
        if (menu.machineSlots() > 0) setTextContent("interface_config", menu.component.isPatternHatch()
                ? Component.translatable("gui.ae2.Patterns")
                : text(menu.component == LargeFactoryComponent.CRYSTAL_HATCH ? "catalysts" : "process_cores"));
        pageButton.setPage(menu.page(), menu.pages());
        modeButton.setState(menu.snapshot.passive());
        powerButton.setState(menu.snapshot.networkEnergy());
    }
    @Override public void drawBG(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, float partialTick) {
        super.drawBG(graphics, x, y, mouseX, mouseY, partialTick);
        if (menu.machineSlots() > 0) {
            // The factory keeps pending outputs in its account, so reuse this row for status instead of return slots.
            Blitter.texture(new ResourceLocation("ae2", "textures/guis/ex_pattern_provider.png"), 256, 256)
                    .src(7, 20, 162, 1).dest(x + 7, y + 126, 162, 18).blit(graphics);
        } else {
            for (var slot : menu.slots) if (slot.isActive()) {
                Icon.SLOT_BACKGROUND.getBlitter().dest(x + slot.x - 1, y + slot.y - 1).blit(graphics);
            }
        }
    }
    @Override public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
        int color = style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB();
        if (menu.pages() > 1) {
            String page = (menu.page() + 1) + " / " + menu.pages();
            graphics.drawString(font, page, 168 - font.width(page), 31, color, false);
        }
        if (menu.component == LargeFactoryComponent.CONTROLLER) {
            graphics.drawString(font, text("dimensions"), 8, 36, color, false);
        }
    }
    private List<Component> statusLines() {
        var s = menu.snapshot;
        var lines = new ArrayList<Component>();
        lines.add(LightningStatusLines.title());
        lines.add(Component.translatable("ae2lt.gui.status.label", text("status." + s.status())));
        lines.add(text("core." + s.core()));
        if (processingHatch()) lines.add(text(s.passive() ? "passive" : "active"));
        lines.add(text(s.networkEnergy() ? "network_energy" : "external_only"));
        lines.add(LightningStatusLines.energy(s.storedEnergy(), s.energyCapacity()));
        String operations = s.operationsPerTick() == LargeFactoryOperationBudget.UNLIMITED ? text("unlimited").getString()
                : compact(s.remainingOperations()) + " / " + compact(s.operationsPerTick());
        lines.add(text("operations").copy().append(": " + operations));
        for (var issue : s.issues()) {
            lines.add(text("issue." + issue.problem()).copy().append(" "
                    + (issue.problem().startsWith("missing_") ? issue.expected() : issue.position().toShortString())));
        }
        return lines;
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (menu.pages() > 1 && PageInput.handleScroll(scrollY,
                () -> send(LargeFactoryMenu.PAGE_PREVIOUS), () -> send(LargeFactoryMenu.PAGE_NEXT))) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }
    static Component text(String key, Object... arguments) { return Component.translatable("ae2lt.large_factory." + key, arguments); }
    static String compact(long value) {
        if (value < 10_000) return Long.toString(value);
        if (value < 1_000_000) return String.format(java.util.Locale.ROOT, "%.1fk", value / 1_000d);
        if (value < 1_000_000_000) return String.format(java.util.Locale.ROOT, "%.1fM", value / 1_000_000d);
        return String.format(java.util.Locale.ROOT, "%.1fG", value / 1_000_000_000d);
    }
}
