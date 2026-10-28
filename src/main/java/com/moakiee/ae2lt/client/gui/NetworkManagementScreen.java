package com.moakiee.ae2lt.client.gui;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.AETextField;
import appeng.client.gui.widgets.Scrollbar;
import appeng.client.gui.widgets.TabButton;
import com.moakiee.ae2lt.client.frequency.ManagementViewCache;
import com.moakiee.ae2lt.client.render.NetworkDeviceHighlight;
import com.moakiee.ae2lt.logic.management.DeviceLocation;
import com.moakiee.ae2lt.logic.management.ManagementMode;
import com.moakiee.ae2lt.logic.management.ManagementQuery;
import com.moakiee.ae2lt.logic.management.ManagementRow;
import com.moakiee.ae2lt.logic.management.ManagementSnapshot;
import com.moakiee.ae2lt.logic.management.PowerTelemetry;
import com.moakiee.ae2lt.menu.FrequencyPage;
import com.moakiee.ae2lt.menu.NetworkManagementMenu;
import com.moakiee.ae2lt.network.NetworkManagementRequestPacket;
import com.moakiee.ae2lt.network.NetworkManagementSyncPacket;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** AE2's generated chassis, text field, tabs, buttons, scrollbar and palette. */
public final class NetworkManagementScreen extends AEBaseScreen<NetworkManagementMenu> {
    private static final int COL_PRIMARY = 0xFF14161F;
    private static final int COL_SECONDARY = 0xFF3E445B;
    private static final int COL_MUTED = 0xFF4A5068;
    private static final int COL_WELL_HINT = 0xFF363C52;
    private static final int COL_RATE = 0xFF144673;
    private static final int COL_LOADED = 0xFF1C6B34;
    private static final int COL_UNLOADED = 0xFF8F2B23;

    private static final String[] WINDOWS = {"1t", "1s", "5s", "30s", "1min"};
    private static final String[] SORTS = {"name", "dimension", "type", "distance", "power"};
    private static final DecimalFormat RATE = new DecimalFormat("#,##0.##");
    private final AETextField search;
    private final Scrollbar scrollbar;
    private final AE2Button sortButton, directionButton, groupButton, windowButton, disconnectButton, configureButton;
    private final TabButton managementTab;
    private final java.util.EnumMap<ManagementMode, AE2Button> modeButtons = new java.util.EnumMap<>(ManagementMode.class);
    private final RowButton[] rowButtons = new RowButton[ManagementQuery.PAGE_SIZE];
    private final LocateButton[] locateButtons = new LocateButton[ManagementQuery.PAGE_SIZE];
    private final List<ItemName> itemNames = new ArrayList<>();
    private record ItemName(String id, String name) {}
    private ManagementMode mode;
    private boolean grouped = true, descending;
    private int sort, window = 1, serial, refresh, debounce;
    private int requestedOffset;
    private String error = "";
    private final TreeSet<String> expandedTypes = new TreeSet<>();
    private UUID selected;
    private boolean searchTooBroad, awaitingResponse;
    private String parsedSearch;
    private List<ManagementQuery.Token> parsedTokens = List.of();
    private ManagementSnapshot snapshot;
    private ViewKey displayedView;
    private record ViewKey(ManagementMode mode, boolean grouped, List<String> expandedTypes, String search,
            int sort, boolean descending, int window, int offset) {}
    private final LinkedHashMap<ViewKey, ManagementSnapshot> pages = new LinkedHashMap<>(32, .75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<ViewKey, ManagementSnapshot> entry) { return size() > 32; }
    };

    public NetworkManagementScreen(NetworkManagementMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        mode = menu.initialMode(); sort = energy() ? 4 : 2; descending = energy();
        for (var item : BuiltInRegistries.ITEM) itemNames.add(new ItemName(BuiltInRegistries.ITEM.getKey(item).toString(),
                item.getName(new ItemStack(item)).getString().toLowerCase(Locale.ROOT)));
        search = widgets.addTextField("search");
        search.setMaxLength(128);
        search.setPlaceholder(tr("search"));
        search.setTooltipMessage(List.of(tr("search_help")));
        search.setResponder(value -> changed(5));
        scrollbar = widgets.addScrollBar("scrollbar", Scrollbar.SMALL);
        scrollbar.setRange(0, 0, 1);
        var navigation = FrequencyNavigation.create(0, 0, FrequencyPage.TAB_DEVICES, true, true, page -> {
            if (!page.isManagement()) { rememberView(); menu.clientFrequencyPage(page); }
        });
        for (var button : navigation) {
            // FrequencyScreen needs vanilla tooltips; this AEBaseScreen renders TabButton's ITooltip itself.
            button.setTooltip(null);
            widgets.add(button.page().widgetId(), button);
        }
        managementTab = navigation.stream().filter(b -> b.page() == FrequencyPage.TAB_DEVICES).findFirst().orElseThrow();
        if (menu.hasParentMenu()) {
            var back = new TabButton(Icon.BACK,
                    Component.translatable("ae2lt.gui.button.return_to_terminal"), button -> PacketDistributor.sendToServer(
                            appeng.core.network.serverbound.SwitchGuisPacket.returnToParentMenu()));
            back.setStyle(TabButton.Style.BOX);
            widgets.add("return_to_terminal", back);
        }
        for (var view : ManagementMode.values()) {
            var button = new ModeButton(view);
            widgets.add("mode_" + view.name().toLowerCase(Locale.ROOT), button);
            button.setTooltip(Tooltip.create(tr("view_help." + view.name().toLowerCase(Locale.ROOT))));
            modeButtons.put(view, button);
        }
        sortButton = widgets.addButton("sort", Component.empty(), () -> { sort = (sort + 1) % (energy() ? 5 : 4); changed(0); });
        directionButton = new DirectionButton(() -> { descending = !descending; changed(0); });
        widgets.add("direction", directionButton);
        groupButton = widgets.addButton("group", Component.empty(), () -> {
            grouped = !grouped;
            changed(0);
        });
        windowButton = widgets.addButton("window", Component.empty(), () -> { window = (window + 1) % 5; changed(0); });
        windowButton.setTooltip(Tooltip.create(tr("window_help")));
        disconnectButton = widgets.addButton("disconnect", tr("disconnect"), () -> {
            var row = selection();
            if (!awaitingResponse && row != null && row.canDisconnect()) {
                menu.clientDisconnect(row.id()); selected = null; pages.clear(); changed(3, false);
            }
        });
        disconnectButton.setTooltip(Tooltip.create(tr("disconnect_help")));
        configureButton = widgets.addButton("configure", tr("configure"), () -> {
            var row = selection();
            if (!awaitingResponse && !energy() && row != null && row.canConfigure()) menu.clientConfigure(row.id());
        });
        configureButton.setTooltip(Tooltip.create(tr("configure_help")));
        for (int i = 0; i < rowButtons.length; i++) {
            final int index = i;
            rowButtons[i] = new RowButton(() -> select(index));
            widgets.add("row" + i, rowButtons[i]);
            locateButtons[i] = new LocateButton();
            widgets.add("locate" + i, locateButtons[i]);
        }
        if (menu.initialQuery() != null) {
            var view = menu.initialQuery();
            search.setValue(view.search());
            mode = view.mode(); grouped = view.grouped(); expandedTypes.addAll(view.expandedTypes()); sort = view.sort();
            descending = view.descending(); window = view.window(); requestedOffset = view.offset();
            serial = view.serial(); selected = menu.initialSelection() != null ? menu.initialSelection()
                    : ManagementViewCache.get(menu.viewCachePos(), menu.frequency(), mode).selection();
        }
        if (menu.initialSnapshot() != null) {
            showSnapshot(menu.initialSnapshot(), viewKey());
            pages.put(viewKey(), snapshot);
            awaitingResponse = false; debounce = 0; refresh = 20;
        }
    }
    private boolean energy() { return mode == ManagementMode.ENERGY; }
    private void switchMode(ManagementMode next) {
        if (mode == next) return;
        rememberView();
        var saved = ManagementViewCache.get(menu.viewCachePos(), menu.frequency(), next);
        var view = saved.query();
        mode = next; grouped = view.grouped(); expandedTypes.clear(); expandedTypes.addAll(view.expandedTypes());
        search.setValue(view.search()); sort = view.sort(); descending = view.descending(); window = view.window();
        requestedOffset = view.offset(); selected = saved.selection();
        changed(0, false);
    }
    private void rememberView() {
        var terms = tokens();
        if (!searchTooBroad) ManagementViewCache.remember(menu.viewCachePos(), menu.frequency(),
                new ManagementQuery(serial, mode, grouped, List.copyOf(expandedTypes), search.getValue(), terms,
                        sort, descending, window, requestedOffset), selected);
    }
    @Override public void removed() { rememberView(); super.removed(); }
    private void changed(int delay) {
        changed(delay, true);
    }
    private void changed(int delay, boolean resetScroll) {
        serial++; error = "";
        if (resetScroll) {
            selected = null; requestedOffset = 0;
            if (scrollbar != null) scrollbar.setCurrentScroll(0);
        }
        var key = viewKey();
        var cached = pages.get(key);
        if (cached != null) showSnapshot(cached, key);
        else if (displayedView != null && displayedView.mode() != mode) { snapshot = null; displayedView = null; }
        // Keep the last visible page during edits within this view until a complete response replaces it.
        awaitingResponse = true; debounce = delay; refresh = 0;
    }
    private ViewKey viewKey() {
        return new ViewKey(mode, grouped, List.copyOf(expandedTypes), search.getValue(), sort, descending, window, requestedOffset);
    }
    private void showSnapshot(ManagementSnapshot next, ViewKey key) {
        snapshot = next; displayedView = key;
        scrollbar.setRange(0, Math.max(0, next.total() - rowButtons.length), 1);
        scrollbar.setCurrentScroll(next.offset());
    }
    private List<ManagementQuery.Token> tokens() {
        String value = search.getValue().strip().toLowerCase(Locale.ROOT);
        if (value.equals(parsedSearch)) return parsedTokens;
        parsedSearch = value; parsedTokens = List.of();
        var result = new ArrayList<ManagementQuery.Token>();
        searchTooBroad = false;
        int payloadBytes = 0;
        if (value.isEmpty()) return List.of();
        // A complete localized label such as "ME Interface" must not expand "ME" into hundreds of unrelated items.
        if (value.contains(" ") && itemNames.stream().anyMatch(item -> item.name.equals(value))) {
            var ids = new ArrayList<>(itemNames.stream().filter(item -> item.name.contains(value)).map(ItemName::id).toList());
            addSearchAliases(ids, value);
            if (ids.size() <= 240 && ids.stream().mapToInt(id -> id.getBytes(java.nio.charset.StandardCharsets.UTF_8).length + 3).sum() <= 16_000) {
                return parsedTokens = List.of(new ManagementQuery.Token(value, ids));
            }
        }
        for (String word : value.split("\\s+", 8)) {
            var ids = new ArrayList<String>();
            for (var item : itemNames) if (item.name.contains(word)) {
                if (ids.size() >= 240) { searchTooBroad = true; return List.of(); }
                ids.add(item.id);
            }
            addSearchAliases(ids, word);
            for (String id : ids) payloadBytes += id.getBytes(java.nio.charset.StandardCharsets.UTF_8).length + 3;
            if (payloadBytes > 16_000) { searchTooBroad = true; return List.of(); }
            result.add(new ManagementQuery.Token(word, List.copyOf(ids)));
        }
        return parsedTokens = List.copyOf(result);
    }
    private static void addSearchAliases(List<String> ids, String term) {
        for (var kind : PowerTelemetry.Kind.values()) {
            String key = kind.name().toLowerCase(Locale.ROOT);
            if (tr("kind." + key).getString().toLowerCase(Locale.ROOT).contains(term)) ids.add("kind:" + key);
        }
        for (String dimension : List.of("overworld", "the_nether", "the_end"))
            if (tr("dimension." + dimension).getString().toLowerCase(Locale.ROOT).contains(term)) ids.add("dimension:minecraft:" + dimension);
    }
    @Override public void containerTick() {
        super.containerTick();
        readScrollbarChange();
        if (debounce > 0) { debounce--; return; }
        if (--refresh <= 0) {
            refresh = awaitingResponse ? 3 : 20;
            var terms = tokens();
            if (searchTooBroad) return;
            PacketDistributor.sendToServer(new NetworkManagementRequestPacket(menu.containerId,
                    new ManagementQuery(serial, mode, grouped, List.copyOf(expandedTypes), search.getValue(), terms, sort, descending, window, requestedOffset)));
        }
    }
    public void receive(NetworkManagementSyncPacket packet) {
        if (packet.menuId() != menu.containerId || packet.serial() != serial) return;
        // Mouse input may move the scrollbar before the next container tick. Do not let an
        // in-flight refresh restore the old offset before that input becomes a new query.
        readScrollbarChange();
        if (packet.serial() != serial) return;
        error = packet.error(); requestedOffset = packet.snapshot().offset();
        showSnapshot(packet.snapshot(), viewKey());
        if (error.isEmpty()) pages.put(viewKey(), snapshot); else pages.clear();
        awaitingResponse = false; refresh = 20;
    }
    private void readScrollbarChange() {
        if (scrollbar.getCurrentScroll() != requestedOffset && snapshot != null) {
            requestedOffset = scrollbar.getCurrentScroll(); changed(0, false);
        }
    }
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && search.isMouseOver(mouseX, mouseY)) {
            search.setValue("");
            setFocused(search);
            search.setFocused(true);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    private void select(int index) {
        if (snapshot == null || awaitingResponse || index >= snapshot.rows().size()) return;
        var row = snapshot.rows().get(index);
        if (row.group()) {
            String type = row.target().type();
            if (!expandedTypes.remove(type)) {
                expandedTypes.add(type);
                if (!ManagementQuery.validExpandedTypes(List.copyOf(expandedTypes))) {
                    expandedTypes.remove(type);
                    minecraft.player.displayClientMessage(tr("expansion_limit"), true);
                    return;
                }
            }
            changed(0, false);
        }
        else {
            selected = row.id();
            disconnectButton.active = !awaitingResponse && row.canDisconnect();
            configureButton.active = !awaitingResponse && !energy() && row.canConfigure();
        }
    }
    private ManagementRow selection() {
        return snapshot == null || selected == null ? null : snapshot.rows().stream().filter(r -> r.id().equals(selected)).findFirst().orElse(null);
    }
    @Override protected void updateBeforeRender() {
        super.updateBeforeRender();
        managementTab.setSelected(true);
        setTextContent("dialog_title", tr("network"));
        for (var entry : modeButtons.entrySet()) {
            var label = tr("view." + entry.getKey().name().toLowerCase(Locale.ROOT));
            if (entry.getKey() == mode) label.withStyle(net.minecraft.ChatFormatting.BOLD);
            entry.getValue().setMessage(label);
        }
        sortButton.setMessage(tr("sort." + SORTS[sort]));
        directionButton.setTooltip(Tooltip.create(tr(descending ? "descending" : "ascending")));
        groupButton.visible = !energy();
        groupButton.setMessage(tr(grouped ? "grouped" : "individual"));
        windowButton.visible = energy();
        windowButton.setMessage(Component.literal(WINDOWS[window] + " · FE/t"));
        var selectedRow = selection();
        disconnectButton.visible = energy();
        disconnectButton.active = !awaitingResponse && selectedRow != null && selectedRow.canDisconnect();
        configureButton.visible = !energy();
        configureButton.active = !awaitingResponse && !energy() && selectedRow != null && !selectedRow.group() && selectedRow.canConfigure();
        for (int i = 0; i < rowButtons.length; i++) {
            var row = snapshot != null && i < snapshot.rows().size() ? snapshot.rows().get(i) : null;
            rowButtons[i].row = row;
            rowButtons[i].visible = row != null;
            rowButtons[i].active = row != null && !awaitingResponse;
            rowButtons[i].setWidth(row != null && row.group() ? imageWidth - 34 : imageWidth - 52);
            rowButtons[i].setMessage(row == null ? Component.empty() : displayName(row.target()));
            rowButtons[i].setTooltip(row == null ? null : Tooltip.create(tooltip(row)));
            locateButtons[i].row = row;
            locateButtons[i].visible = row != null && !row.group();
            locateButtons[i].active = row != null && !row.group() && !awaitingResponse;
            locateButtons[i].setTooltip(row == null ? null : Tooltip.create(tr("locate_help").append("\n").append(address(row.target()))));
        }
    }
    @Override public void drawBG(GuiGraphics graphics, int ox, int oy, int mx, int my, float partialTick) {
        super.drawBG(graphics, ox, oy, mx, my, partialTick);
        // AE2 19's text-field painter only fills 126 inner pixels. Tile its own middle texture for this wide field.
        var bounds = search.getTooltipArea();
        int variant = search.isFocused() ? 24 : 0;
        for (int x = 1; x < bounds.getWidth() - 1; x += 126) {
            int width = Math.min(126, bounds.getWidth() - 1 - x);
            Blitter.texture("guis/text_field.png", 128, 128)
                    .src(1, variant, width, 12).dest(bounds.getX() + x, bounds.getY()).blit(graphics);
        }
    }
    @Override public void drawFG(GuiGraphics graphics, int ox, int oy, int mx, int my) {
        int wellCenterX = 8 + 206 / 2;
        int wellCenterY = 66 + (105 - 8) / 2;
        if (searchTooBroad) {
            Component msg = tr("search_too_broad");
            graphics.drawString(font, msg, wellCenterX - font.width(msg) / 2, wellCenterY, COL_WELL_HINT, false);
        } else if (!error.isEmpty()) {
            Component msg = Component.translatable("ae2lt.wireless_energy." + error);
            graphics.drawString(font, msg, wellCenterX - font.width(msg) / 2, wellCenterY, COL_UNLOADED, false);
        } else if (snapshot != null && snapshot.rows().isEmpty()) {
            Component msg = tr("empty");
            graphics.drawString(font, msg, wellCenterX - font.width(msg) / 2, wellCenterY, COL_WELL_HINT, false);
        }

        var selectedRow = selection();
        Component selectionLabel = selectedRow == null
                ? tr(energy() ? "disconnect_help" : "select_device")
                : displayName(selectedRow.target());
        int footerTop = configureButton.getY() - oy;
        int selectionBoxWidth = 138;
        if (selectedRow != null) {
            graphics.fill(9, footerTop + 5, 14, footerTop + 10, 0xFF1864AB);
            graphics.drawString(font, fit(selectionLabel, selectionBoxWidth - 14), 18, footerTop + 4, COL_PRIMARY, false);
        } else {
            graphics.drawString(font, fit(selectionLabel, selectionBoxWidth - 4), 9, footerTop + 4, COL_MUTED, false);
        }
        if (selectedRow != null && isHovering(8, footerTop, selectionBoxWidth, 16, mx, my))
            drawTooltip(graphics, mx - ox, my - oy, List.of(tooltip(selectedRow)));

        if (snapshot != null) {
            Component summary = energy() ? tr("summary_power", snapshot.total(), rate(snapshot.totals()[window]))
                    : tr(mode == ManagementMode.CONNECTIONS ? "summary_connections" : "summary_devices", snapshot.devices(), snapshot.total());
            if (snapshot.limited()) summary = tr("incomplete").append(summary);
            graphics.drawString(font, fit(summary, imageWidth - 18), 9, footerTop - 10, COL_MUTED, false);
            if (snapshot.limited() && isHovering(8, footerTop - 12, imageWidth - 16, 12, mx, my))
                drawTooltip(graphics, mx - ox, my - oy, List.of(tr("history_limit")));
        }
    }
    private Component tooltip(ManagementRow row) {
        var text = displayName(row.target()).copy().append("\n").append(row.target().type());
        if (row.group()) return text.append("\n").append(tr(row.state().equals("expanded") ? "collapse" : "expand", row.count()));
        text.append("\n").append(address(row.target()));
        if (row.target().face() >= 0) text.append("\n").append(tr("face", net.minecraft.core.Direction.values()[row.target().face()].getName()));
        text.append("\n").append(tr("state." + row.state()));
        if (energy()) {
            text.append("\n").append(tr("kind." + row.kind()));
            if (row.source() != null) text.append("\n").append(tr("source", displayName(row.source()), address(row.source())));
            for (int i = 0; i < 5; i++) text.append("\n" + WINDOWS[i] + ": " + rate(row.rates()[i]) + " FE/t");
        }
        return text;
    }
    private final class DirectionButton extends AE2Button {
        private DirectionButton(Runnable action) {
            super(Component.empty(), button -> action.run());
        }
        @Override public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            var icon = descending ? Icon.S_ARROW_DOWN : Icon.S_ARROW_UP;
            icon.getBlitter()
                    .dest(getX() + (width - icon.width) / 2, getY() + (height - icon.height) / 2)
                    .blit(graphics);
        }
    }
    private final class ModeButton extends AE2Button {
        private final ManagementMode view;
        private ModeButton(ManagementMode view) {
            super(tr("view." + view.name().toLowerCase(Locale.ROOT)), button -> switchMode(view));
            this.view = view;
        }
        @Override public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            // Use AE2's highlighted button sprite for the current view, entirely within its bounds.
            boolean hovered = isHovered;
            isHovered |= view == mode;
            try {
                super.renderWidget(graphics, mouseX, mouseY, partialTick);
            } finally {
                isHovered = hovered;
            }
        }
    }
    private final class LocateButton extends AE2Button {
        private ManagementRow row;
        private LocateButton() {
            super(tr("locate"), button -> {
                var target = ((LocateButton) button).row;
                if (target != null && !target.group() && !awaitingResponse) NetworkDeviceHighlight.locate(target.target());
            });
        }
        @Override public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (row == null || row.group()) return;
            int x = getX(), y = getY(), cx = x + 9, cy = y + 10;
            boolean hovered = active && isHoveredOrFocused();
            boolean rowSelected = row.id().equals(selected);
            if (rowSelected) {
                graphics.fill(x, y, x + width, y + height, 0xFF78B0E4);
                graphics.fill(x, y, x + width, y + 1, 0xFFDAFFFF);
                graphics.fill(x, y + height - 1, x + width, y + height, 0xFF5282B8);
                graphics.fill(x, y + 2, x + 1, y + height - 2, 0xFF5282B8);
            } else {
                graphics.fill(x, y, x + width, y + height, 0xFF878FA5);
                graphics.fill(x, y, x + width, y + 1, 0xFFADB4C8);
                graphics.fill(x, y + height - 1, x + width, y + height, 0xFF565B70);
                graphics.fill(x, y + 2, x + 1, y + height - 2, 0xFF696D88);
            }
            if (hovered) {
                graphics.fill(cx - 7, cy - 7, cx + 8, cy + 8, 0xFF3E5C82);
                graphics.fill(cx - 6, cy - 6, cx + 7, cy + 7, 0xFFB8E2FF);
                graphics.fill(cx - 6, cy - 6, cx + 7, cy - 5, 0xFFE8F8FF);
            }
            int color = hovered ? 0xFF0A2E52 : 0xFF242838;
            // Crisp locator crosshair inside the row action cell.
            graphics.fill(cx - 3, cy - 3, cx + 4, cy - 2, color);
            graphics.fill(cx - 3, cy + 3, cx + 4, cy + 4, color);
            graphics.fill(cx - 3, cy - 2, cx - 2, cy + 3, color);
            graphics.fill(cx + 3, cy - 2, cx + 4, cy + 3, color);
            graphics.fill(cx, cy - 5, cx + 1, cy - 2, color);
            graphics.fill(cx, cy + 3, cx + 1, cy + 6, color);
            graphics.fill(cx - 5, cy, cx - 2, cy + 1, color);
            graphics.fill(cx + 3, cy, cx + 6, cy + 1, color);
            graphics.fill(cx, cy, cx + 1, cy + 1, color);
        }
    }
    private final class RowButton extends AE2Button {
        private ManagementRow row;
        private RowButton(Runnable action) { super(Component.empty(), b -> action.run()); }
        @Override public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (row == null) return;
            int x = getX(), y = getY();
            boolean hovered = active && isHoveredOrFocused();
            boolean rowSelected = row.id().equals(selected);
            if (rowSelected) {
                graphics.fill(x, y, x + width, y + height, hovered ? 0xFF88C0F4 : 0xFF78B0E4);
                graphics.fill(x, y, x + width, y + 1, 0xFFDAFFFF);
                graphics.fill(x, y + height - 1, x + width, y + height, 0xFF5282B8);
                graphics.fill(x, y, x + 2, y + height, 0xFF1864AB);
            } else if (hovered) {
                graphics.fill(x, y, x + width, y + height, 0xFF96C2EC);
                graphics.fill(x, y, x + width, y + 1, 0xFFDAFFFF);
                graphics.fill(x, y + height - 1, x + width, y + height, 0xFF5282B8);
            } else {
                graphics.fill(x, y, x + width, y + height, 0xFF878FA5);
                graphics.fill(x, y, x + width, y + 1, 0xFFADB4C8);
                graphics.fill(x, y + height - 1, x + width, y + height, 0xFF565B70);
            }

            boolean tree = displayedView != null && displayedView.mode() != ManagementMode.ENERGY && displayedView.grouped();
            int iconX = x + (tree ? row.group() ? 16 : 24 : 4);
            int textX = iconX + 19;
            if (row.group()) {
                (row.state().equals("expanded") ? Icon.ARROW_DOWN : Icon.ARROW_RIGHT)
                        .getBlitter().dest(x + 1, y + 2).blit(graphics);
            } else if (tree) {
                graphics.fill(x + 15, y + 2, x + 16, y + height - 2, COL_MUTED);
                graphics.fill(x + 15, y + 11, x + 21, y + 12, COL_MUTED);
            }
            var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(row.target().type()));
            if (item != net.minecraft.world.item.Items.AIR) graphics.renderItem(new ItemStack(item), iconX, y + 2);
            else Icon.LEVEL_ENERGY.getBlitter().dest(iconX, y + 2).blit(graphics);

            Component name = displayName(row.target());
            boolean power = displayedView != null && displayedView.mode() == ManagementMode.ENERGY;
            String right = row.group() ? "×" + row.count() : power ? compactRate(row.rates()[window]) + " FE/t"
                    : row.kind().equals("connection") ? tr("state." + row.state()).getString() : "";
            int rightColor = row.group() ? COL_PRIMARY
                    : power ? (row.rates()[window] > 0 ? COL_RATE : COL_SECONDARY)
                    : row.state().equals("loaded") ? COL_LOADED : COL_UNLOADED;
            int rightX = x + width - 5 - font.width(right);
            int leftMaxW = right.isEmpty() ? (x + width - textX - 4) : Math.max(24, rightX - textX - 6);
            graphics.drawString(font, fit(name, leftMaxW), textX, y + 2, COL_PRIMARY, false);
            if (!right.isEmpty()) {
                graphics.drawString(font, right, rightX, y + (height - 8) / 2, rightColor, false);
            }
            Component secondary = row.group()
                    ? tr(row.state().equals("expanded") ? "collapse" : "expand", row.count())
                    : Component.literal(shortAddress(row.target()));
            graphics.drawString(font, fit(secondary, leftMaxW), textX, y + 11, COL_SECONDARY, false);
        }
    }
    private Component fit(Component value, int max) {
        String text = value.getString();
        return Component.literal(font.width(text) <= max ? text : font.plainSubstrByWidth(text, Math.max(0, max - font.width("…"))) + "…");
    }
    private static String compactRate(double amount) {
        if (amount >= 1e18) return String.format(Locale.ROOT, "%.3g", amount);
        String[] units = {"", "k", "M", "G", "T", "P"};
        int unit = 0;
        while (amount >= 1000 && unit < units.length - 1) { amount /= 1000; unit++; }
        return RATE.format(amount) + units[unit];
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
        return dimension + " · " + d.pos().getX() + ", " + d.pos().getY() + ", " + d.pos().getZ();
    }
    public static String address(DeviceLocation d) { return d.dimension() + " " + d.pos().getX() + " " + d.pos().getY() + " " + d.pos().getZ(); }
    private static net.minecraft.network.chat.MutableComponent tr(String key, Object... args) { return Component.translatable("ae2lt.management." + key, args); }
}
