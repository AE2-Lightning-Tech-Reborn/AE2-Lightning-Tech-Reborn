package com.moakiee.ae2lt.menu;

import com.moakiee.ae2lt.blockentity.MatrixControllerBlockEntity;
import com.moakiee.ae2lt.logic.craft.migration.PatternMigrationSnapshot;
import com.moakiee.ae2lt.network.MatrixPatternMigrationStatusPacket;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import com.moakiee.ae2lt.network.NetworkInit;

final class MatrixMigrationMenuState {
    PatternMigrationSnapshot snapshot = PatternMigrationSnapshot.IDLE;
    private PatternMigrationSnapshot sent;
    private ServerPlayer player;
    private Supplier<MatrixControllerBlockEntity> controller;
    private int nextTick;

    void bind(Inventory inventory, Supplier<MatrixControllerBlockEntity> controller) {
        this.controller = controller;
        if (inventory.player instanceof ServerPlayer serverPlayer) {
            player = serverPlayer;
            var host = controller.get();
            if (host != null) host.getPatternMigration().recover(player);
        }
    }

    void sync(AbstractContainerMenu menu) {
        if (player == null || controller == null) return;
        var host = controller.get();
        if (host == null) return;
        snapshot = host.getPatternMigration().snapshot();
        if (snapshot.equals(sent)) return;
        int now = player.server.getTickCount();
        if (sent != null && now < nextTick && snapshot.stage() == sent.stage() && snapshot.reason() == sent.reason()) return;
        NetworkInit.sendToPlayer(player, new MatrixPatternMigrationStatusPacket(menu.containerId, snapshot));
        sent = snapshot;
        nextTick = now + 4;
    }
}
