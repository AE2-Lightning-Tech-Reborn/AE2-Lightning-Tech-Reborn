package com.moakiee.ae2lt.menu;

import appeng.menu.AEBaseMenu;
import com.moakiee.ae2lt.blockentity.WirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.logic.energy.WirelessEnergyEndpoints;
import com.moakiee.ae2lt.logic.management.ManagementQuery;
import com.moakiee.ae2lt.logic.management.ManagementMode;
import com.moakiee.ae2lt.logic.management.ManagementRow;
import com.moakiee.ae2lt.logic.management.ManagementSnapshot;
import com.moakiee.ae2lt.logic.management.NetworkDeviceIndex;
import com.moakiee.ae2lt.logic.management.RemoteDeviceSession;
import com.moakiee.ae2lt.network.NetworkManagementSyncPacket;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.PacketDistributor;

/** Three independent views of the live controller's network; queries never load chunks or create links. */
public final class NetworkManagementMenu extends AEBaseMenu {
    public static final MenuType<NetworkManagementMenu> TYPE = IMenuTypeExtension.create(NetworkManagementMenu::new);
    private final WirelessOverloadedControllerBlockEntity host;
    private final BlockPos pos;
    private final int frequency;
    private final ManagementMode initialMode;
    private long lastQuery = Long.MIN_VALUE, lastEdit = Long.MIN_VALUE;
    private List<ManagementRow> displayed = List.of();
    private ManagementQuery lastView;
    private ManagementQuery initialQuery;
    private ManagementSnapshot initialSnapshot;
    private ManagementQuery cachedQuery;
    private ManagementSnapshot cachedSnapshot;
    private long cachedBucket = Long.MIN_VALUE;
    private UUID initialSelection;

    private NetworkManagementMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, null, buffer.readBlockPos(), buffer.readVarInt(), buffer.readEnum(ManagementMode.class));
        initialQuery = buffer.readNullable(ManagementQuery::read);
        initialSelection = buffer.readNullable(b -> b.readUUID());
        initialSnapshot = ManagementSnapshot.read(buffer);
    }
    public NetworkManagementMenu(int id, Inventory inventory, WirelessOverloadedControllerBlockEntity host, boolean energy) {
        this(id, inventory, host, host.getBlockPos(), host.getFrequencyId(), energy ? ManagementMode.ENERGY : ManagementMode.DEVICES);
    }
    private NetworkManagementMenu(int id, Inventory inventory, WirelessOverloadedControllerBlockEntity host,
            BlockPos pos, int frequency, ManagementMode mode) {
        super(TYPE, id, inventory, host);
        this.host = host; this.pos = pos; this.frequency = frequency; this.initialMode = mode;
        registerClientAction("disconnect", UUID.class, this::disconnect);
        registerClientAction("frequencyPage", Integer.class, this::openFrequencyPage);
        registerClientAction("configureDevice", UUID.class, this::openConfiguration);
    }
    public static void open(ServerPlayer player, WirelessOverloadedControllerBlockEntity host, boolean energy) {
        open(player, host, energy, null, null);
    }
    public static void open(ServerPlayer player, WirelessOverloadedControllerBlockEntity host, boolean energy,
            ManagementQuery query, UUID selection) {
        open(player, host, energy ? ManagementMode.ENERGY : ManagementMode.DEVICES, query, selection);
    }
    public static void open(ServerPlayer player, WirelessOverloadedControllerBlockEntity host, ManagementMode mode,
            ManagementQuery query, UUID selection) {
        if (query != null && (!query.valid() || query.mode() != mode)) return;
        var f = WirelessFrequencyManager.get(player.server).getFrequency(host.getFrequencyId());
        if (f == null || !f.getPlayerAccess(player).canUse()) return;
        var view = query == null ? ManagementQuery.initial(mode) : query;
        var snapshot = ManagementSnapshot.build(player, host, view);
        var initialView = view.atOffset(snapshot.offset());
        player.openMenu(new MenuProvider() {
            public Component getDisplayName() { return Component.translatable("ae2lt.management.network"); }
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                var menu = new NetworkManagementMenu(id, inv, host, host.getBlockPos(), host.getFrequencyId(), mode);
                menu.initialQuery = initialView; menu.initialSelection = selection; menu.initialSnapshot = snapshot;
                menu.lastView = initialView; menu.displayed = snapshot.rows();
                menu.cachedQuery = initialView; menu.cachedSnapshot = snapshot; menu.cachedBucket = player.server.getTickCount() / 20;
                return menu;
            }
            public boolean shouldTriggerClientSideContainerClosingOnOpen() { return false; }
        }, b -> {
            b.writeBlockPos(host.getBlockPos()); b.writeVarInt(host.getFrequencyId()); b.writeEnum(mode);
            b.writeNullable(initialView, (buffer, state) -> state.write(buffer));
            b.writeNullable(selection, (buffer, id) -> buffer.writeUUID(id));
            snapshot.write(b);
        });
    }
    public ManagementMode initialMode() { return initialMode; }
    public BlockPos controllerPos() { return pos; }
    public int frequency() { return frequency; }
    public void clientFrequencyPage(FrequencyPage page) { sendClientAction("frequencyPage", page.ordinal()); }
    public void clientDisconnect(UUID id) { sendClientAction("disconnect", id); }
    public void clientConfigure(UUID id) { sendClientAction("configureDevice", id); }
    public ManagementQuery initialQuery() { return initialQuery; }
    public ManagementSnapshot initialSnapshot() { return initialSnapshot; }
    public UUID initialSelection() { return initialSelection; }
    public void reopen(ServerPlayer player, ManagementQuery query, UUID selection) { open(player, host, query.mode(), query, selection); }
    public boolean containsDevice(appeng.api.networking.IGridNode node) {
        var grid = host == null ? null : host.getMainNode().getGrid();
        return grid != null && grid.getService(NetworkDeviceIndex.class).contains(node) && grid == node.getGrid();
    }

    @Override public boolean stillValid(Player player) {
        if (isClientSide()) return true;
        return host != null && !host.isRemoved() && host.getLevel() == player.level()
                && host.getLevel().getBlockEntity(pos) == host && host.getFrequencyId() == frequency
                && player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64;
    }
    private boolean canRead(ServerPlayer player) {
        return player.containerMenu == this && canAccessFromSubMenu(player);
    }
    public boolean canAccessFromSubMenu(ServerPlayer player) {
        if (getPlayerInventory().player != player || !stillValid(player)) return false;
        var f = WirelessFrequencyManager.get(player.server).getFrequency(frequency);
        return f != null && f.getPlayerAccess(player).canUse();
    }
    public boolean acceptRequest(ServerPlayer player, boolean edit) {
        return canRead(player) && acceptRate(player.server.getTickCount(), edit);
    }
    private boolean acceptRate(long now, boolean edit) {
        long last = edit ? lastEdit : lastQuery;
        if (last != Long.MIN_VALUE && now - last < (edit ? 1 : 2)) return false;
        if (edit) lastEdit = now; else lastQuery = now;
        return true;
    }
    public void query(ServerPlayer player, ManagementQuery query) {
        if (!query.valid() || player.containerMenu != this) return;
        long now = player.server.getTickCount();
        if (!acceptRate(now, false)) return;
        if (!canRead(player)) {
            displayed = List.of();
            cachedQuery = null; cachedSnapshot = null;
            PacketDistributor.sendToPlayer(player, new NetworkManagementSyncPacket(containerId, query.serial(),
                    "no_permission", new ManagementSnapshot(0, 0, 0, new double[5], false, List.of())));
            return;
        }
        long bucket = now / 20;
        var snapshot = query.equals(cachedQuery) && cachedSnapshot != null && cachedBucket == bucket
                ? cachedSnapshot : ManagementSnapshot.build(player, host, query);
        cachedQuery = query; cachedSnapshot = snapshot; cachedBucket = bucket;
        lastView = query.atOffset(snapshot.offset());
        displayed = snapshot.rows();
        PacketDistributor.sendToPlayer(player, new NetworkManagementSyncPacket(containerId, query.serial(), "", snapshot));
    }
    public void openConfiguration(UUID id) {
        if (!(getPlayerInventory().player instanceof ServerPlayer player) || !acceptRequest(player, true)
                || lastView == null || lastView.energy() || displayed.stream().noneMatch(r -> r.id().equals(id)
                && !r.group() && r.canConfigure() && (r.kind().equals("device") || r.kind().equals("connection")))) return;
        var grid = host.getMainNode().getGrid();
        var node = grid == null ? null : grid.getService(NetworkDeviceIndex.class).resolve(id);
        if (node == null || !new RemoteDeviceSession(this, node, id, lastView).open(player))
            player.displayClientMessage(Component.translatable("ae2lt.management.configure_unavailable"), true);
    }
    private void disconnect(UUID id) {
        if (!(getPlayerInventory().player instanceof ServerPlayer player) || !acceptRequest(player, true)) return;
        // A stale or forged row cannot modify a different generation or a native ME device.
        if (displayed.stream().noneMatch(r -> r.id().equals(id) && r.canDisconnect() && r.kind().equals("wireless"))) return;
        String result = WirelessEnergyEndpoints.get(player.server).disconnect(player, frequency, id);
        player.displayClientMessage(Component.translatable("ae2lt.wireless_energy." + result), true);
        displayed = List.of();
        cachedQuery = null; cachedSnapshot = null;
    }
    public void openFrequencyPage(int pageId) {
        if (pageId < 0 || pageId >= FrequencyPage.VALUES.length || FrequencyPage.VALUES[pageId].isManagement()
                || !(getPlayerInventory().player instanceof ServerPlayer player) || player.containerMenu != this
                || !stillValid(player) || !acceptRate(player.server.getTickCount(), true)) return;
        FrequencyMenu.openPage(player, host, FrequencyPage.VALUES[pageId]);
    }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
