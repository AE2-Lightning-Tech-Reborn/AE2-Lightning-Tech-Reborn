package com.moakiee.ae2lt.logic.management;

import appeng.api.networking.IGridNode;
import appeng.api.storage.ISubMenuHost;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.menu.AEBaseMenu;
import appeng.menu.ISubMenu;
import appeng.parts.AEBasePart;
import com.moakiee.ae2lt.api.frequency.FrequencyBindingHost;
import com.moakiee.ae2lt.blockentity.WirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.menu.FrequencyMenu;
import com.moakiee.ae2lt.menu.NetworkManagementMenu;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/** A server-created capability tied to one live device and the controller that authorized access. */
public final class RemoteDeviceSession implements ISubMenuHost {
    private final NetworkManagementMenu parent;
    private final IGridNode node;
    private final UUID deviceId;
    private final ManagementQuery returnQuery;
    private static final ThreadLocal<RemoteDeviceLocator> OPENING = new ThreadLocal<>();

    public RemoteDeviceSession(NetworkManagementMenu parent, IGridNode node, UUID deviceId, ManagementQuery returnQuery) {
        this.parent = parent; this.node = node; this.deviceId = deviceId; this.returnQuery = returnQuery;
    }
    public static boolean supports(Object owner) {
        return blockEntity(owner) instanceof AEBaseBlockEntity
                && (owner instanceof ISubMenuHost || owner instanceof WirelessOverloadedControllerBlockEntity
                    || owner instanceof FrequencyBindingHost);
    }
    public static BlockEntity blockEntity(Object owner) {
        return owner instanceof BlockEntity be ? be : owner instanceof AEBasePart part ? part.getBlockEntity() : null;
    }
    public Object owner() { return node.getOwner(); }
    public boolean valid(Player player) {
        if (!(player instanceof ServerPlayer sp) || !parent.canAccessFromSubMenu(sp)
                || player.isSpectator() || !player.mayBuild() || !parent.containsDevice(node)) return false;
        var be = blockEntity(owner());
        if (be == null || be.isRemoved() || be.getLevel() == null) return false;
        var level = node.getLevel();
        var chunk = level.getChunkSource().getChunkNow(be.getBlockPos().getX() >> 4, be.getBlockPos().getZ() >> 4);
        if (chunk == null || chunk.getBlockEntity(be.getBlockPos(), LevelChunk.EntityCreationType.CHECK) != be
                || !level.mayInteract(player, be.getBlockPos())) return false;
        return !(owner() instanceof AEBasePart part)
                || part.getHost() != null && part.getHost().getPart(part.getSide()) == part;
    }
    public boolean open(ServerPlayer player) {
        if (!valid(player) || !supports(owner())) return false;
        var locator = new RemoteDeviceLocator(this);
        if (!locator.canSerialize()) return false;
        if (owner() instanceof ISubMenuHost host) {
            OPENING.set(locator);
            try {
                host.returnToMainMenu(player, new ISubMenu() {
                    public appeng.menu.locator.MenuHostLocator getLocator() { return locator; }
                    public ISubMenuHost getHost() { return host; }
                });
            } finally { OPENING.remove(); }
        } else {
            FrequencyMenu.openRemote(player, blockEntity(owner()), locator);
        }
        return player.containerMenu instanceof AEBaseMenu menu && menu.getLocator() == locator;
    }
    public static RemoteDeviceLocator opening() { return OPENING.get(); }
    public static RemoteDeviceLocator locator(Player player) {
        return player.containerMenu instanceof AEBaseMenu menu && menu.getLocator() instanceof RemoteDeviceLocator remote ? remote : null;
    }
    /** AE2 actions re-check the session on the server thread before touching the native menu. */
    public static boolean acceptPacket(ServerPlayer player) {
        var remote = locator(player);
        if (remote == null || remote.valid(player)) return true;
        player.closeContainer();
        return false;
    }
    @Override public ItemStack getMainMenuIcon() { return appeng.core.definitions.AEBlocks.DRIVE.stack(); }
    @Override public void returnToMainMenu(Player player, ISubMenu subMenu) {
        if (player instanceof ServerPlayer sp) returnToDevices(sp);
    }
    public void returnToDevices(ServerPlayer player) {
        if (parent.canAccessFromSubMenu(player)) parent.reopen(player, returnQuery, deviceId);
        else player.closeContainer();
    }
}
