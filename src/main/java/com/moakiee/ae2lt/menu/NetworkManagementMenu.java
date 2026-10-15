package com.moakiee.ae2lt.menu;

import appeng.menu.AEBaseMenu;
import com.moakiee.ae2lt.blockentity.WirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.logic.energy.WirelessEnergyEndpoints;
import com.moakiee.ae2lt.logic.management.ManagementQuery;
import com.moakiee.ae2lt.logic.management.ManagementRow;
import com.moakiee.ae2lt.logic.management.ManagementSnapshot;
import com.moakiee.ae2lt.network.NetworkManagementSyncPacket;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.PacketDistributor;

/** Two independent views of the live controller's network; queries never load chunks or create links. */
public final class NetworkManagementMenu extends AEBaseMenu {
    public static final MenuType<NetworkManagementMenu> TYPE = IMenuTypeExtension.create(NetworkManagementMenu::new);
    private final WirelessOverloadedControllerBlockEntity host;
    private final BlockPos pos;
    private final int frequency;
    private final boolean initialEnergy;
    private long lastQuery = Long.MIN_VALUE, lastEdit = Long.MIN_VALUE;
    private List<ManagementRow> displayed = List.of();

    private NetworkManagementMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, null, buffer.readBlockPos(), buffer.readVarInt(), buffer.readBoolean());
    }
    public NetworkManagementMenu(int id, Inventory inventory, WirelessOverloadedControllerBlockEntity host, boolean energy) {
        this(id, inventory, host, host.getBlockPos(), host.getFrequencyId(), energy);
    }
    private NetworkManagementMenu(int id, Inventory inventory, WirelessOverloadedControllerBlockEntity host,
            BlockPos pos, int frequency, boolean energy) {
        super(TYPE, id, inventory, host);
        this.host = host; this.pos = pos; this.frequency = frequency; this.initialEnergy = energy;
        registerClientAction("disconnect", UUID.class, this::disconnect);
        registerClientAction("back", this::back);
    }
    public static void open(ServerPlayer player, WirelessOverloadedControllerBlockEntity host, boolean energy) {
        var f = WirelessFrequencyManager.get(player.server).getFrequency(host.getFrequencyId());
        if (f == null || !f.getPlayerAccess(player).canUse()) return;
        player.openMenu(new MenuProvider() {
            public Component getDisplayName() { return Component.translatable("ae2lt.management." + (energy ? "energy" : "devices")); }
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new NetworkManagementMenu(id, inv, host, host.getBlockPos(), host.getFrequencyId(), energy);
            }
            public boolean shouldTriggerClientSideContainerClosingOnOpen() { return false; }
        }, b -> { b.writeBlockPos(host.getBlockPos()); b.writeVarInt(host.getFrequencyId()); b.writeBoolean(energy); });
    }
    public boolean initialEnergy() { return initialEnergy; }
    public BlockPos controllerPos() { return pos; }
    public void clientBack() { sendClientAction("back"); }
    public void clientDisconnect(UUID id) { sendClientAction("disconnect", id); }

    @Override public boolean stillValid(Player player) {
        if (isClientSide()) return true;
        return host != null && !host.isRemoved() && host.getLevel() == player.level()
                && host.getLevel().getBlockEntity(pos) == host && host.getFrequencyId() == frequency
                && player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64;
    }
    private boolean canRead(ServerPlayer player) {
        if (player.containerMenu != this || !stillValid(player)) return false;
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
            PacketDistributor.sendToPlayer(player, new NetworkManagementSyncPacket(containerId, query.serial(),
                    "no_permission", new ManagementSnapshot(0, 0, 0, new double[5], false, List.of())));
            return;
        }
        var snapshot = ManagementSnapshot.build(player, host, query);
        displayed = snapshot.rows();
        PacketDistributor.sendToPlayer(player, new NetworkManagementSyncPacket(containerId, query.serial(), "", snapshot));
    }
    private void disconnect(UUID id) {
        if (!(getPlayerInventory().player instanceof ServerPlayer player) || !acceptRequest(player, true)) return;
        // A stale or forged row cannot modify a different generation or a native ME device.
        if (displayed.stream().noneMatch(r -> r.id().equals(id) && r.canDisconnect() && r.kind().equals("wireless"))) return;
        String result = WirelessEnergyEndpoints.get(player.server).disconnect(player, frequency, id);
        player.displayClientMessage(Component.translatable("ae2lt.wireless_energy." + result), true);
        displayed = List.of();
    }
    private void back() {
        if (!(getPlayerInventory().player instanceof ServerPlayer player) || !stillValid(player)) return;
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new FrequencyMenu(id, inv, host),
                host.getBlockState().getBlock().getName()), b -> FrequencyMenu.writeExtraData(b, host, false));
    }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
