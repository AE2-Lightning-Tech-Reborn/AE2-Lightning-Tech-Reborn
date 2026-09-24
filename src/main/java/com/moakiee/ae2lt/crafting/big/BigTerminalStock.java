package com.moakiee.ae2lt.crafting.big;

import appeng.api.stacks.AEKey;
import appeng.menu.AEBaseMenu;

import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuTerminalHost;
import com.moakiee.ae2lt.network.tianshu.BigStockPacket;
import com.moakiee.thunderbolt.core.storage.big.BigStorageOps;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Per-container exact display cache, synchronized as bounded deltas on the native terminal. */
public final class BigTerminalStock {
    private final Map<AEKey, BigInteger> bigStock = new LinkedHashMap<>();
    private long nextBigStockSync;

    public BigInteger get(AEKey key) {
        return bigStock.get(key);
    }

    public void apply(Map<AEKey, BigInteger> changes) {
        changes.forEach(
                (k, n) -> {
                    if (n.signum() == 0) bigStock.remove(k);
                    else bigStock.put(k, n);
                });
    }

    public void sync(AEBaseMenu menu, TianshuTerminalHost tianshuHost) {
        if (!(menu.getPlayer() instanceof ServerPlayer player)) return;
        long now = player.level().getGameTime();
        if (now < nextBigStockSync) return;
        nextBigStockSync = now + 20;
        var node = tianshuHost.getActionableNode();
        var snapshot =
                node == null
                        ? new HashMap<AEKey, BigInteger>()
                        : new HashMap<>(
                                BigStorageOps.snapshot(
                                        node.getGrid().getStorageService().getInventory(),
                                        menu.getActionSource()));
        snapshot.entrySet()
                .removeIf(e -> e.getValue().compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0);
        var delta = new LinkedHashMap<AEKey, BigInteger>();
        snapshot.forEach(
                (k, n) -> {
                    if (!n.equals(bigStock.get(k))) delta.put(k, n);
                });
        bigStock.keySet().stream()
                .filter(k -> !snapshot.containsKey(k))
                .forEach(k -> delta.put(k, BigInteger.ZERO));
        var page = new LinkedHashMap<AEKey, BigInteger>();
        for (var e : delta.entrySet()) {
            page.put(e.getKey(), e.getValue());
            if (page.size() == 64) {
                PacketDistributor.sendToPlayer(
                        player, new BigStockPacket(menu.containerId, Map.copyOf(page)));
                page.clear();
            }
        }
        if (!page.isEmpty())
            PacketDistributor.sendToPlayer(
                    player, new BigStockPacket(menu.containerId, Map.copyOf(page)));
        bigStock.clear();
        bigStock.putAll(snapshot);
    }
}
