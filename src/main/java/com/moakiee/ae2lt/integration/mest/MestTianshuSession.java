package com.moakiee.ae2lt.integration.mest;

import appeng.menu.SlotSemantics;
import appeng.menu.guisync.DataSynchronization;
import appeng.menu.me.common.MEStorageMenu;
import com.lhy.mest.api.MestModuleSlotContext;
import com.lhy.mest.terminal.MESTMenu;
import com.moakiee.ae2lt.menu.TianshuCraftingTermMenu;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.moakiee.ae2lt.menu.extension.EmbeddedTerminalMenu;
import com.moakiee.ae2lt.network.mest.MestMenuSyncPacket;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;

/** Lifetime belongs to one MEST menu, never to a player UUID or a reused container id. */
public final class MestTianshuSession {
    public static final String CRAFTING_MODULE = "ae2lt:tianshu_crafting";
    public static final String PATTERN_MODULE = "ae2lt:tianshu_pattern";
    public static final String MAINTENANCE_MODULE = "ae2lt:tianshu_maintenance";
    private final MESTMenu outer;
    private final MestTianshuMenus.Crafting crafting;
    private final MestTianshuMenus.Pattern pattern;
    private final List<Controller> controllers;
    private boolean broadcasting;
    private boolean closed;

    public MestTianshuSession(MESTMenu outer) {
        this.outer = outer;
        crafting = new MestTianshuMenus.Crafting(outer);
        pattern = new MestTianshuMenus.Pattern(outer, crafting);
        controllers = List.of(new Controller(crafting), new Controller(pattern));
    }

    public TianshuCraftingTermMenu crafting() { return crafting; }
    public TianshuPatternEncodingTermMenu pattern() { return pattern; }

    public void addSlots(MestModuleSlotContext context, boolean encoding) {
        var controller = controllers.get(encoding ? 1 : 0);
        if (!controller.slots.isEmpty()) throw new IllegalStateException("Tianshu module slots registered twice");
        for (Slot slot : controller.menu.slots) {
            var semantic = controller.menu.getSlotSemantic(slot);
            if (slot.container == controller.menu.getPlayerInventory() || semantic == SlotSemantics.UPGRADE
                    || semantic == SlotSemantics.VIEW_CELL || semantic == SlotSemantics.TOOLBOX
                    || semantic == com.moakiee.ae2lt.menu.Ae2ltSlotSemantics.TIANSHU_GLOBAL_RESERVE_MARK) continue;
            Slot proxy = MestSlot.wrap(controller.menu, slot);
            controller.slots.add(context.addSlot(proxy));
        }
    }

    public void addMaintenanceSlot(MestModuleSlotContext context) {
        context.addSlot(MestSlot.wrap(crafting, crafting.getGlobalReserveMarkSlot()));
    }

    public void broadcast() {
        if (closed || broadcasting || outer.isClientSide()) return;
        broadcasting = true;
        try {
            for (var controller : controllers) {
                controller.menu.setLinkStatus(outer.getLinkStatus());
                controller.menu.broadcastChanges();
                if (controller.firstSync || controller.sync.hasChanges()) {
                    var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), outer.getPlayer().registryAccess());
                    try {
                        if (controller.firstSync) controller.sync.writeFull(buf); else controller.sync.writeUpdate(buf);
                        byte[] bytes = new byte[buf.readableBytes()];
                        buf.readBytes(bytes);
                        PacketDistributor.sendToPlayer((ServerPlayer) outer.getPlayer(),
                                new MestMenuSyncPacket(outer.containerId,
                                        ((EmbeddedTerminalMenu) controller.menu).moduleName(), bytes));
                        controller.firstSync = false;
                    } finally { buf.release(); }
                }
            }
        } finally { broadcasting = false; }
    }

    public void close() {
        if (closed) return;
        closed = true;
        // Only native owned-state persistence runs here. These menus were never opened or synchronized separately.
        crafting.removed(outer.getPlayer());
        pattern.removed(outer.getPlayer());
    }

    private static final class Controller {
        final MEStorageMenu menu;
        final DataSynchronization sync;
        final List<Slot> slots = new ArrayList<>();
        boolean firstSync = true;
        Controller(MEStorageMenu menu) { this.menu = menu; sync = new DataSynchronization(menu); }
    }
}
