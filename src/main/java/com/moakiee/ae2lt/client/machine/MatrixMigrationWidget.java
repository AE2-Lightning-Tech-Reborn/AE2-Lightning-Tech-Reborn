package com.moakiee.ae2lt.client.machine;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.moakiee.ae2lt.client.ldlib.LdlibToolbarButton;
import com.moakiee.ae2lt.client.widgets.TextureToggleButton;
import com.moakiee.ae2lt.logic.craft.migration.PatternMigrationSnapshot;
import com.moakiee.ae2lt.menu.MatrixMigrationMenu;
import com.moakiee.ae2lt.network.MatrixPatternMigrationActionPacket;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Shared migration control. The port screen uses the vanilla {@link #button}; the controller screen
 * places the LDLib {@link #ldlibButton} in its root tree. Both read one tooltip and summary.
 */
final class MatrixMigrationWidget {
    private final MatrixMigrationMenu menu;
    private PatternMigrationSnapshot displayed;
    private boolean showingDetails;
    private static final IGuiTexture START = LdlibToolbarButton.glyph("auto_input_off");
    private static final IGuiTexture STOP = LdlibToolbarButton.glyph("auto_input_ejection");
    final TextureToggleButton button;

    MatrixMigrationWidget(MatrixMigrationMenu menu, int x, int y) {
        this.menu = menu;
        button = new TextureToggleButton(TextureToggleButton.ButtonType.PATTERN_MIGRATION, ignored ->
                PacketDistributor.sendToServer(new MatrixPatternMigrationActionPacket(
                        ((AbstractContainerMenu) menu).containerId, menu.getMigrationMenuPos(), menu.getMigrationSnapshot().active())));
        button.setPosition(x, y);
        update();
    }

    void update() {
        var s = menu.getMigrationSnapshot();
        boolean details = Screen.hasShiftDown();
        if (s.equals(displayed) && details == showingDetails) return;
        displayed = s;
        showingDetails = details;
        button.setState(s.active());
        var lines = tooltipLines();
        button.setMessage(lines.get(0).copy());
        MutableComponent text = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) text.append("\n");
            text.append(lines.get(i));
        }
        button.setTooltip(Tooltip.create(text));
    }

    /** The same control as an LDLib element at GUI-local {@code x}, {@code y}, for LDLib-rooted screens. */
    UIElement ldlibButton(int x, int y) {
        return LdlibToolbarButton.toolbar(x, y,
                () -> menu.getMigrationSnapshot().active() ? STOP : START,
                () -> PacketDistributor.sendToServer(new MatrixPatternMigrationActionPacket(
                        ((AbstractContainerMenu) menu).containerId, menu.getMigrationMenuPos(),
                        menu.getMigrationSnapshot().active())),
                this::tooltipLines);
    }

    private List<Component> tooltipLines() {
        var s = menu.getMigrationSnapshot();
        boolean details = Screen.hasShiftDown();
        var lines = new ArrayList<Component>();
        lines.add(Component.translatable("ae2lt.matrix.migration." + (s.active() ? "stop" : "start")));
        lines.add(Component.translatable("ae2lt.matrix.migration.description"));
        if (s.stage() != PatternMigrationSnapshot.Stage.IDLE) {
            lines.add(stage(s));
        }
        if (s.reason() != PatternMigrationSnapshot.Reason.NONE) {
            lines.add(Component.translatable("ae2lt.matrix.migration.reason." + s.reason().name().toLowerCase(Locale.ROOT)));
        }
        if (s.stage() != PatternMigrationSnapshot.Stage.IDLE) {
            lines.add(Component.translatable("ae2lt.matrix.migration.counts", s.moved(), s.recovered()));
            if (details) {
                lines.add(Component.translatable("ae2lt.matrix.migration.scan", s.tier(), s.scanned(), s.total()));
                lines.add(Component.translatable("ae2lt.matrix.migration.remaining",
                        s.incompatible(), s.noSpace(), s.refundBlocked(), s.unavailable(), s.unsupported(), s.disks()));
            } else {
                lines.add(Component.translatable("ae2lt.matrix.migration.details_hint"));
            }
        }
        return lines;
    }

    /** One-line progress summary, or {@code null} while no migration is running. */
    Component summary() {
        var s = menu.getMigrationSnapshot();
        if (s.stage() == PatternMigrationSnapshot.Stage.IDLE) return null;
        return s.reason() == PatternMigrationSnapshot.Reason.RECOVERY
                ? Component.translatable("ae2lt.matrix.migration.recovery_summary")
                : stage(s).copy().append(" · ").append(
                        Component.translatable("ae2lt.matrix.migration.counts", s.moved(), s.recovered()));
    }

    private static Component stage(PatternMigrationSnapshot s) {
        return Component.translatable("ae2lt.matrix.migration.stage." + s.stage().name().toLowerCase(Locale.ROOT));
    }
}
