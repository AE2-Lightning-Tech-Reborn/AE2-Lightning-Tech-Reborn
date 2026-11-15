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
import appeng.client.gui.widgets.ValidationIcon;
import com.moakiee.ae2lt.client.gui.LightningStatusLines;
import com.moakiee.ae2lt.client.widgets.PageButton;
import com.moakiee.ae2lt.client.widgets.PageInput;
import com.moakiee.ae2lt.machine.largeoverload.LargeFactoryComponent;
import com.moakiee.ae2lt.machine.largeoverload.LargeFactoryControllerBlock;
import com.moakiee.ae2lt.machine.largeoverload.LargeFactoryMenu;
import com.moakiee.ae2lt.machine.largeoverload.LargeFactoryOperationBudget;
import com.moakiee.ae2lt.machine.largeoverload.LargeFactoryStructure;
import com.moakiee.ae2lt.integration.recipeviewer.multiblock.MultiblockStructureRecipes;
import com.moakiee.ae2lt.registry.ModBlocks;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** AE2 panels, native status icons and slots, with dedicated space for live factory state. */
public final class LargeFactoryScreen extends AEBaseScreen<LargeFactoryMenu> {
    private final PageButton pageButton;
    private final ToggleButton modeButton;
    private final ToggleButton powerButton;

    public LargeFactoryScreen(LargeFactoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, StyleManager.loadStyleDoc(menu.machineSlots() > 0
                ? "/screens/large_factory_pattern.json" : "/screens/large_factory.json"));
        var statusIcon = new ValidationIcon() {
            @Override protected Icon getIcon() { return menu.snapshot.formed() ? Icon.HELP : Icon.INVALID; }
            @Override public List<Component> getTooltipMessage() { return statusLines(); }
        };
        statusIcon.setHalfSize(false);
        widgets.add("factoryStatus", statusIcon);
        if (menu.machineSlots() == 0) {
            widgets.add("energyBar", new OverloadProcessingFactoryEnergyBar(
                    () -> menu.snapshot.storedEnergy(), () -> menu.snapshot.energyCapacity(), style.getImage("energyBar")));
        }
        pageButton = new PageButton(this::isHandlingRightClick,
                () -> send(LargeFactoryMenu.PAGE_PREVIOUS), () -> send(LargeFactoryMenu.PAGE_NEXT));
        if (menu.pages() > 1) addToLeftToolbar(pageButton);
        modeButton = new ToggleButton(Icon.VIEW_MODE_STORED, Icon.VIEW_MODE_CRAFTING, ignored -> send(LargeFactoryMenu.MODE));
        modeButton.setMessage(text("mode"));
        modeButton.setTooltipOn(List.of(text("passive"), text("passive_hint")));
        modeButton.setTooltipOff(List.of(text("active"), text("active_hint")));
        if (processingHatch()) addToLeftToolbar(modeButton);
        powerButton = new ToggleButton(Icon.POWER_UNIT_AE, Icon.POWER_UNIT_RF, ignored -> send(LargeFactoryMenu.POWER));
        powerButton.setTooltipOn(List.of(text("network_energy")));
        powerButton.setTooltipOff(List.of(text("external_only")));
        addToLeftToolbar(powerButton);
        if (processingHatch()) toolbar(Icon.ENTER, "recover", LargeFactoryMenu.RECOVER);
        if (menu.component == LargeFactoryComponent.CONTROLLER) {
            toolbar(Icon.CRAFT_HAMMER, "build", LargeFactoryMenu.BUILD);
            toolbar(Icon.OVERLAY_ON, "preview", LargeFactoryMenu.PREVIEW);
            toolbar(Icon.SCHEDULING_DEFAULT, "scan", LargeFactoryMenu.SCAN);
        }
    }
    private void toolbar(Icon icon, String label, int action) {
        var button = new IconButton(ignored -> send(action)) {
            @Override protected Icon getIcon() { return icon; }
            @Override public List<Component> getTooltipMessage() {
                return action == LargeFactoryMenu.BUILD ? List.of(text(label), text("build_hint")) : List.of(text(label));
            }
        };
        button.setMessage(text(label));
        addToLeftToolbar(button);
    }
    boolean processingHatch() { return menu.component.isPatternHatch() || menu.component == LargeFactoryComponent.CRYSTAL_HATCH; }
    void send(int action) {
        if (minecraft == null || minecraft.gameMode == null) return;
        if (action == LargeFactoryMenu.PREVIEW && minecraft.level != null) {
            var state = minecraft.level.getBlockState(menu.position());
            if (!(state.getBlock() instanceof LargeFactoryControllerBlock)) return;
            var facing = state.getValue(LargeFactoryControllerBlock.FACING);
            var center = LargeFactoryStructure.worldPosition(menu.position(), LargeFactoryStructure.CORE, facing);
            boolean firmament = minecraft.level.getBlockState(center).is(ModBlocks.FIRMAMENT_CONVERSION_CORE.get());
            minecraft.setScreen(new LargeFactoryPreviewScreen(this,
                    MultiblockStructureRecipes.largeFactory(firmament),
                    menu.position(), facing));
            return;
        }
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
        setTextContent("dialog_title", fit(title, imageWidth - 36));
        if (menu.machineSlots() > 0) setTextContent("interface_config", menu.component.isPatternHatch()
                ? Component.translatable("gui.ae2.Patterns")
                : text(menu.component == LargeFactoryComponent.CRYSTAL_HATCH ? "catalysts" : "process_cores"));
        pageButton.setPage(menu.page(), menu.pages());
        modeButton.setState(menu.snapshot.passive());
        powerButton.setState(menu.snapshot.networkEnergy());
        powerButton.active = menu.component == LargeFactoryComponent.CONTROLLER || menu.snapshot.formed();
    }
    @Override public void drawBG(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, float partialTick) {
        super.drawBG(graphics, x, y, mouseX, mouseY, partialTick);
        panel(graphics, x + 7, y + (menu.machineSlots() > 0 ? 105 : 21), 162,
                menu.machineSlots() > 0 ? 14 : 64);
        for (var slot : menu.slots) if (slot.isActive()) {
            Icon.SLOT_BACKGROUND.getBlitter().dest(x + slot.x - 1, y + slot.y - 1).blit(graphics);
        }
    }
    @Override public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
        int color = style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB();
        if (menu.pages() > 1) {
            String page = (menu.page() + 1) + " / " + menu.pages();
            graphics.drawString(font, page, 168 - font.width(page), 21, color, false);
        }
        var s = menu.snapshot;
        if (menu.machineSlots() > 0) {
            Icon.S_MACHINE.getBlitter().dest(11, 107).blit(graphics);
            graphics.drawString(font, fit(text("status." + s.status()), 46), 25, 108, color, false);
            var summary = fit(text("entry_summary_compact", s.entryCount(), s.pendingTypes()), 88);
            graphics.drawString(font, summary, 163 - font.width(summary), 108, color, false);
        } else {
            Icon.S_MACHINE.getBlitter().dest(12, 25).blit(graphics);
            graphics.drawString(font, fit(text("status." + s.status()), 138), 26, 26, color, false);
            Icon.S_PROCESSOR.getBlitter().dest(12, 38).blit(graphics);
            graphics.drawString(font, fit(text("core." + s.core()), 138), 26, 39, color, false);
            graphics.drawString(font, text("operations"), 12, 52, color, false);
            String limit = s.operationsPerTick() == LargeFactoryOperationBudget.UNLIMITED
                    ? text("unlimited").getString() : compact(s.operationsPerTick());
            graphics.drawString(font, limit, 163 - font.width(limit), 52, color, false);
            Icon.POWER_UNIT_RF.getBlitter().dest(10, 64).blit(graphics);
            graphics.drawString(font, fit(Component.literal(compact(s.storedEnergy()) + " / " + compact(s.energyCapacity())), 119),
                    30, 68, color, false);
            (s.networkEnergy() ? Icon.POWER_UNIT_AE : Icon.POWER_UNIT_RF).getBlitter().dest(8, 88).blit(graphics);
            graphics.drawString(font, fit(text(s.networkEnergy() ? "network_energy" : "external_only"), 140), 28, 92, color, false);
            graphics.drawString(font, fit(structureHint(), 160), 8, 106, color, false);
        }
    }
    private Component fit(Component value, int width) {
        String text = value.getString();
        return font.width(text) <= width ? value : Component.literal(font.plainSubstrByWidth(text, width - font.width("…")) + "…");
    }
    private Component structureHint() {
        if (menu.snapshot.formed() || menu.snapshot.issues().isEmpty()) return text("dimensions");
        var issue = menu.snapshot.issues().stream().filter(i -> i.problem().startsWith("missing_"))
                .findFirst().orElse(menu.snapshot.issues().getFirst());
        return text("issue." + issue.problem()).copy().append(" "
                + (issue.problem().startsWith("missing_") ? issue.expected() : issue.position().toShortString()));
    }
    private static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        // Slice the native network-status inset while keeping its borders one pixel wide.
        var source = Blitter.texture("guis/networkstatus.png", 256, 256);
        source.copy().src(8, 39, 162, 78).dest(x + 1, y + 1, width - 2, height - 2).blit(graphics);
        source.copy().src(7, 38, 164, 1).dest(x, y, width, 1).blit(graphics);
        source.copy().src(7, 117, 164, 1).dest(x, y + height - 1, width, 1).blit(graphics);
        source.copy().src(7, 39, 1, 78).dest(x, y + 1, 1, height - 2).blit(graphics);
        source.copy().src(170, 39, 1, 78).dest(x + width - 1, y + 1, 1, height - 2).blit(graphics);
    }
    private List<Component> statusLines() {
        var s = menu.snapshot;
        var lines = new ArrayList<Component>();
        lines.add(title);
        lines.add(LightningStatusLines.title());
        lines.add(Component.translatable("ae2lt.gui.status.label", text("status." + s.status())));
        if (menu.machineSlots() > 0) lines.add(text("entry_summary", s.entryCount(), s.pendingTypes()));
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
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (menu.pages() > 1 && PageInput.handleScroll(scrollY,
                () -> send(LargeFactoryMenu.PAGE_PREVIOUS), () -> send(LargeFactoryMenu.PAGE_NEXT))) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
    static Component text(String key, Object... arguments) { return Component.translatable("ae2lt.large_factory." + key, arguments); }
    static String compact(long value) {
        if (value < 10_000) return Long.toString(value);
        if (value < 1_000_000) return String.format(java.util.Locale.ROOT, "%.1fk", value / 1_000d);
        if (value < 1_000_000_000) return String.format(java.util.Locale.ROOT, "%.1fM", value / 1_000_000d);
        return String.format(java.util.Locale.ROOT, "%.1fG", value / 1_000_000_000d);
    }
}
