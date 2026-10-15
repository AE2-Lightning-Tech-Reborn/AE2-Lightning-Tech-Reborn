package com.moakiee.ae2lt.client.gui;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.AETextField;
import appeng.client.gui.widgets.Scrollbar;
import appeng.client.gui.widgets.TabButton;
import com.moakiee.ae2lt.client.render.NetworkDeviceHighlight;
import com.moakiee.ae2lt.logic.management.DeviceLocation;
import com.moakiee.ae2lt.logic.management.ManagementQuery;
import com.moakiee.ae2lt.logic.management.ManagementRow;
import com.moakiee.ae2lt.logic.management.ManagementSnapshot;
import com.moakiee.ae2lt.logic.management.PowerTelemetry;
import com.moakiee.ae2lt.menu.NetworkManagementMenu;
import com.moakiee.ae2lt.network.NetworkManagementRequestPacket;
import com.moakiee.ae2lt.network.NetworkManagementSyncPacket;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** AE2's generated chassis, text field, tabs, buttons, scrollbar and palette. */
public final class NetworkManagementScreen extends AEBaseScreen<NetworkManagementMenu> {
    private static final String[] WINDOWS = {"1t", "1s", "5s", "30s", "1min"};
    private static final String[] SORTS = {"name", "dimension", "type", "distance", "power"};
    private static final DecimalFormat RATE = new DecimalFormat("#,##0.##");
    private final AETextField search;
    private final Scrollbar scrollbar;
    private final AE2Button sortButton, directionButton, groupButton, windowButton, locateButton, copyButton, disconnectButton;
    private final TabButton deviceTab, powerTab;
    private final RowButton[] rowButtons = new RowButton[ManagementQuery.PAGE_SIZE];
    private final List<ItemName> itemNames = new ArrayList<>();
    private record ItemName(String id, String name) {}
    private boolean energy, grouped = true, descending;
    private int sort, window = 1, serial, refresh, debounce;
    private int requestedOffset;
    private String type = "", error = "";
    private UUID selected;
    private boolean searchTooBroad;
    private ManagementSnapshot snapshot;

    public NetworkManagementScreen(NetworkManagementMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        energy = menu.initialEnergy(); sort = energy ? 4 : 2; descending = energy;
        for (var item : BuiltInRegistries.ITEM) itemNames.add(new ItemName(BuiltInRegistries.ITEM.getKey(item).toString(),
                item.getName(new ItemStack(item)).getString().toLowerCase(Locale.ROOT)));
        search = widgets.addTextField("search");
        search.setMaxLength(128);
        search.setPlaceholder(tr("search"));
        search.setTooltipMessage(List.of(tr("search_help")));
        search.setResponder(value -> changed(5));
        scrollbar = widgets.addScrollBar("scrollbar", Scrollbar.BIG);
        scrollbar.setRange(0, 0, 1);
        deviceTab = new TabButton(new ItemStack(appeng.core.definitions.AEBlocks.DRIVE.block()), tr("devices"), button -> switchMode(false));
        powerTab = new TabButton(new ItemStack(appeng.core.definitions.AEBlocks.ENERGY_CELL.block()), tr("energy"), button -> switchMode(true));
        widgets.add("devices", deviceTab); widgets.add("energy", powerTab);
        widgets.add("back", new TabButton(Icon.BACK, tr("back"), button -> menu.clientBack()));
        sortButton = widgets.addButton("sort", Component.empty(), () -> { sort = (sort + 1) % (energy ? 5 : 4); changed(0); });
        directionButton = widgets.addButton("direction", Component.empty(), () -> { descending = !descending; changed(0); });
        groupButton = widgets.addButton("group", Component.empty(), () -> {
            if (!type.isEmpty()) type = ""; else grouped = !grouped;
            changed(0);
        });
        windowButton = widgets.addButton("window", Component.empty(), () -> { window = (window + 1) % 5; changed(0); });
        windowButton.setTooltip(Tooltip.create(tr("window_help")));
        locateButton = widgets.addButton("locate", tr("locate"), () -> {
            var row = selection();
            if (row != null && !row.group() && NetworkDeviceHighlight.locate(row.target())) onClose();
        });
        copyButton = widgets.addButton("copy", tr("copy"), () -> {
            var row = selection();
            if (row != null) minecraft.keyboardHandler.setClipboard(address(row.target()));
        });
        disconnectButton = widgets.addButton("disconnect", tr("disconnect"), () -> {
            var row = selection();
            if (row != null && row.canDisconnect()) {
                menu.clientDisconnect(row.id()); selected = null; changed(3);
            }
        });
        disconnectButton.setTooltip(Tooltip.create(tr("disconnect_help")));
        for (int i = 0; i < rowButtons.length; i++) {
            final int index = i;
            rowButtons[i] = new RowButton(() -> select(index));
            widgets.add("row" + i, rowButtons[i]);
        }
    }
    private void switchMode(boolean power) {
        if (energy == power) return;
        energy = power; type = ""; sort = energy ? 4 : 2; descending = energy;
        changed(0);
    }
    private void changed(int delay) {
        serial++; snapshot = null; selected = null; error = "";
        requestedOffset = 0;
        if (scrollbar != null) scrollbar.setCurrentScroll(0);
        debounce = delay; refresh = 0;
    }
    private List<ManagementQuery.Token> tokens() {
        var result = new ArrayList<ManagementQuery.Token>();
        searchTooBroad = false;
        int payloadBytes = 0;
        String value = search.getValue().strip().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) return List.of();
        for (String word : value.split("\\s+", 8)) {
            var ids = new ArrayList<String>();
            for (var item : itemNames) if (item.name.contains(word)) {
                if (ids.size() >= 240) { searchTooBroad = true; return List.of(); }
                ids.add(item.id);
            }
            for (var kind : PowerTelemetry.Kind.values()) {
                String key = kind.name().toLowerCase(Locale.ROOT);
                if (tr("kind." + key).getString().toLowerCase(Locale.ROOT).contains(word)) ids.add("kind:" + key);
            }
            for (String dimension : List.of("overworld", "the_nether", "the_end"))
                if (tr("dimension." + dimension).getString().toLowerCase(Locale.ROOT).contains(word)) ids.add("dimension:minecraft:" + dimension);
            for (String id : ids) payloadBytes += id.getBytes(java.nio.charset.StandardCharsets.UTF_8).length + 3;
            if (payloadBytes > 16_000) { searchTooBroad = true; return List.of(); }
            result.add(new ManagementQuery.Token(word, List.copyOf(ids)));
        }
        return List.copyOf(result);
    }
    @Override public void containerTick() {
        super.containerTick();
        if (scrollbar.getCurrentScroll() != requestedOffset && snapshot != null) {
            requestedOffset = scrollbar.getCurrentScroll(); serial++; snapshot = null; refresh = 0;
        }
        if (debounce > 0) { debounce--; return; }
        if (--refresh <= 0) {
            refresh = 20;
            var terms = tokens();
            if (searchTooBroad) return;
            PacketDistributor.sendToServer(new NetworkManagementRequestPacket(menu.containerId,
                    new ManagementQuery(serial, energy, grouped, type, search.getValue(), terms, sort, descending, window, requestedOffset)));
        }
    }
    public void receive(NetworkManagementSyncPacket packet) {
        if (packet.menuId() != menu.containerId || packet.serial() != serial) return;
        snapshot = packet.snapshot(); error = packet.error();
        requestedOffset = snapshot.offset();
        scrollbar.setRange(0, Math.max(0, snapshot.total() - rowButtons.length), 1);
        scrollbar.setCurrentScroll(requestedOffset);
    }
    private void select(int index) {
        if (snapshot == null || index >= snapshot.rows().size()) return;
        var row = snapshot.rows().get(index);
        if (row.group()) { type = row.target().type(); changed(0); }
        else {
            selected = row.id();
            locateButton.active = copyButton.active = true;
            disconnectButton.active = row.canDisconnect();
        }
    }
    private ManagementRow selection() {
        return snapshot == null || selected == null ? null : snapshot.rows().stream().filter(r -> r.id().equals(selected)).findFirst().orElse(null);
    }
    @Override protected void updateBeforeRender() {
        super.updateBeforeRender();
        deviceTab.setSelected(!energy); powerTab.setSelected(energy);
        setTextContent("dialog_title", tr(energy ? "energy" : "devices"));
        sortButton.setMessage(tr("sort." + SORTS[sort]));
        directionButton.setMessage(Component.literal(descending ? "v" : "^"));
        directionButton.setTooltip(Tooltip.create(tr(descending ? "descending" : "ascending")));
        groupButton.visible = !energy;
        groupButton.setMessage(tr(!type.isEmpty() ? "all_types" : grouped ? "grouped" : "individual"));
        windowButton.visible = energy;
        windowButton.setMessage(Component.literal(WINDOWS[window] + " · FE/t"));
        var selectedRow = selection();
        locateButton.active = copyButton.active = selectedRow != null && !selectedRow.group();
        disconnectButton.visible = energy;
        disconnectButton.active = selectedRow != null && selectedRow.canDisconnect();
        for (int i = 0; i < rowButtons.length; i++) {
            var row = snapshot != null && i < snapshot.rows().size() ? snapshot.rows().get(i) : null;
            rowButtons[i].row = row;
            rowButtons[i].visible = row != null;
            rowButtons[i].setMessage(row == null ? Component.empty() : displayName(row.target()));
            rowButtons[i].setTooltip(row == null ? null : Tooltip.create(tooltip(row)));
        }
    }
    @Override public void drawBG(GuiGraphics graphics, int ox, int oy, int mx, int my, float partialTick) {
        super.drawBG(graphics, ox, oy, mx, my, partialTick);
        // AE2 19's text-field painter only fills 126 inner pixels. Tile its own middle texture for this wide field.
        var bounds = search.getTooltipArea();
        int variant = search.isFocused() ? 24 : 0;
        for (int x = 1; x < bounds.getWidth() - 1; x += 126) {
            int width = Math.min(126, bounds.getWidth() - 1 - x);
            appeng.client.gui.style.Blitter.texture("guis/text_field.png", 128, 128)
                    .src(1, variant, width, 12).dest(bounds.getX() + x, bounds.getY()).blit(graphics);
        }
    }
    @Override public void drawFG(GuiGraphics graphics, int ox, int oy, int mx, int my) {
        int color = style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB();
        if (searchTooBroad) graphics.drawString(font, tr("search_too_broad"), 10, 76, color, false);
        else if (!error.isEmpty()) graphics.drawString(font, Component.translatable("ae2lt.wireless_energy." + error), 10, 76, color, false);
        else if (snapshot == null) graphics.drawString(font, tr("loading"), 10, 76, color, false);
        else if (snapshot.rows().isEmpty()) graphics.drawString(font, tr("empty"), 10, 76, color, false);
        if (snapshot != null) {
            Component summary = energy ? tr("summary_power", snapshot.total(), rate(snapshot.totals()[window]))
                    : tr("summary_devices", snapshot.devices(), snapshot.total());
            if (snapshot.limited()) summary = tr("incomplete").append(summary);
            graphics.drawString(font, fit(summary, 284), 9, 215, color, false);
            if (snapshot.limited() && isHovering(8, 212, 286, 12, mx, my))
                drawTooltip(graphics, mx - ox, my - oy, List.of(tr("history_limit")));
        }
    }
    private Component tooltip(ManagementRow row) {
        var text = displayName(row.target()).copy().append("\n").append(row.target().type());
        if (row.group()) return text.append("\n").append(tr("expand", row.count()));
        text.append("\n").append(address(row.target()));
        if (row.target().face() >= 0) text.append("\n").append(tr("face", net.minecraft.core.Direction.values()[row.target().face()].getName()));
        text.append("\n").append(tr("state." + row.state()));
        if (energy) {
            text.append("\n").append(tr("kind." + row.kind()));
            if (row.source() != null) text.append("\n").append(tr("source", displayName(row.source()), address(row.source())));
            for (int i = 0; i < 5; i++) text.append("\n" + WINDOWS[i] + ": " + rate(row.rates()[i]) + " FE/t");
        }
        return text;
    }
    private final class RowButton extends AE2Button {
        private ManagementRow row;
        private RowButton(Runnable action) { super(Component.empty(), b -> action.run()); }
        @Override public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (row == null) return;
            int x = getX(), y = getY();
            appeng.client.gui.style.BackgroundGenerator.draw(width, height, graphics, x, y);
            if (isHoveredOrFocused() || row.id().equals(selected))
                graphics.fill(x + 3, y + 3, x + width - 3, y + height - 3, style.getColor(PaletteColor.SELECTION_COLOR).toARGB());
            var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(row.target().type()));
            if (item != net.minecraft.world.item.Items.AIR) graphics.renderItem(new ItemStack(item), x + 4, y + 6);
            else Icon.LEVEL_ENERGY.getBlitter().dest(x + 4, y + 6).blit(graphics);
            Component name = displayName(row.target());
            String right = row.group() ? "×" + row.count() + " >" : energy ? rate(row.rates()[window]) + " FE/t" : "";
            int nameWidth = width - 30 - font.width(right);
            graphics.drawString(font, fit(name, nameWidth), x + 24, y + 4, style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB(), false);
            graphics.drawString(font, right, x + width - 5 - font.width(right), y + 4, style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB(), false);
            Component secondary = row.group() ? tr("expand", row.count()) : energy
                    ? tr("kind." + row.kind()).append(" · ").append(shortAddress(row.target())) : Component.literal(shortAddress(row.target()));
            graphics.drawString(font, fit(secondary, width - 29), x + 24, y + 15, style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB(), false);
        }
    }
    private Component fit(Component value, int max) {
        String text = value.getString();
        return Component.literal(font.width(text) <= max ? text : font.plainSubstrByWidth(text, Math.max(0, max - font.width("…"))) + "…");
    }
    public static String rate(double amount) {
        if (amount >= 1e15) return String.format(Locale.ROOT, "%.3g", amount);
        return RATE.format(amount);
    }
    private static Component displayName(DeviceLocation location) {
        if (!location.name().isEmpty()) return Component.literal(location.name());
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(location.type()));
        return item == net.minecraft.world.item.Items.AIR ? tr("external_target") : item.getName(new ItemStack(item));
    }
    private static String shortAddress(DeviceLocation d) {
        String dimension = d.dimension();
        if (dimension.startsWith("minecraft:")) dimension = tr("dimension." + dimension.substring(10)).getString();
        return dimension + " · " + d.pos().getX() + " " + d.pos().getY() + " " + d.pos().getZ();
    }
    public static String address(DeviceLocation d) { return d.dimension() + " " + d.pos().getX() + " " + d.pos().getY() + " " + d.pos().getZ(); }
    private static net.minecraft.network.chat.MutableComponent tr(String key, Object... args) { return Component.translatable("ae2lt.management." + key, args); }
}
