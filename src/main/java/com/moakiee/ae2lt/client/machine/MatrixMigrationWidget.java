package com.moakiee.ae2lt.client.machine;

import com.moakiee.ae2lt.client.widgets.TextureToggleButton;
import com.moakiee.ae2lt.logic.craft.migration.PatternMigrationSnapshot;
import com.moakiee.ae2lt.menu.MatrixMigrationMenu;
import com.moakiee.ae2lt.network.MatrixPatternMigrationActionPacket;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.PacketDistributor;

/** Shared migration control for the controller and port screens. */
final class MatrixMigrationWidget {
    private final MatrixMigrationMenu menu;
    private PatternMigrationSnapshot displayed;
    private boolean showingDetails;
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
        MutableComponent text = Component.translatable("ae2lt.matrix.migration." + (s.active() ? "stop" : "start"));
        button.setMessage(text.copy());
        text.append("\n").append(Component.translatable("ae2lt.matrix.migration.description"));
        if (s.stage() != PatternMigrationSnapshot.Stage.IDLE) {
            text.append("\n").append(stage(s));
        }
        if (s.reason() != PatternMigrationSnapshot.Reason.NONE) {
            text.append("\n").append(Component.translatable("ae2lt.matrix.migration.reason." + s.reason().name().toLowerCase(Locale.ROOT)));
        }
        if (s.stage() != PatternMigrationSnapshot.Stage.IDLE) {
            text.append("\n").append(Component.translatable("ae2lt.matrix.migration.counts", s.moved(), s.recovered()));
            if (details) {
                text.append("\n").append(Component.translatable("ae2lt.matrix.migration.scan", s.tier(), s.scanned(), s.total()));
                text.append("\n").append(Component.translatable("ae2lt.matrix.migration.remaining",
                        s.incompatible(), s.noSpace(), s.refundBlocked(), s.unavailable(), s.unsupported(), s.disks()));
            } else {
                text.append("\n").append(Component.translatable("ae2lt.matrix.migration.details_hint"));
            }
        }
        button.setTooltip(Tooltip.create(text));
    }

    void render(GuiGraphics graphics, Font font, int x, int y, int width, int color) {
        var s = menu.getMigrationSnapshot();
        if (s.stage() == PatternMigrationSnapshot.Stage.IDLE) return;
        String line = s.reason() == PatternMigrationSnapshot.Reason.RECOVERY
                ? Component.translatable("ae2lt.matrix.migration.recovery_summary").getString()
                : stage(s).getString() + " · " + Component.translatable("ae2lt.matrix.migration.counts", s.moved(), s.recovered()).getString();
        graphics.drawString(font, font.plainSubstrByWidth(line, width), x, y, color, false);
    }

    private static Component stage(PatternMigrationSnapshot s) {
        return Component.translatable("ae2lt.matrix.migration.stage." + s.stage().name().toLowerCase(Locale.ROOT));
    }
}
