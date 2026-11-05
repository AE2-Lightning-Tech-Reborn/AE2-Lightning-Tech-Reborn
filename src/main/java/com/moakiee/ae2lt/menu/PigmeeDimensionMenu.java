package com.moakiee.ae2lt.menu;

import com.moakiee.ae2lt.block.PigmeeDimensionBlock;
import com.moakiee.ae2lt.blockentity.PigmeeDimensionBlockEntity;
import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.dimension.PigmeeDimensionData;
import com.moakiee.ae2lt.dimension.PigmeeDimensionMaterials;
import com.moakiee.ae2lt.dimension.PigmeeDimensionTravel;
import com.moakiee.ae2lt.dimension.PigmeeChunkGenerator;
import com.moakiee.ae2lt.dimension.PigmeeDimensionKind;
import com.moakiee.ae2lt.dimension.PigmeeDimensionPattern;
import com.moakiee.ae2lt.network.PigmeeDimensionActionPacket;
import com.moakiee.ae2lt.registry.ModMenuTypes;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** No inventory slots: material selectors are samples, not copied or consumed items. */
public final class PigmeeDimensionMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final Level level;
    private final PigmeeDimensionBlockEntity host;
    private final CompoundTag snapshot;

    public PigmeeDimensionMenu(int id, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(id, inventory, data.readBlockPos(), data.readNbt());
    }

    private PigmeeDimensionMenu(int id, Inventory inventory, BlockPos pos, CompoundTag snapshot) {
        super(ModMenuTypes.PIGMEE_DIMENSION.get(), id);
        this.pos = pos.immutable();
        this.level = inventory.player.level();
        this.host = level.getBlockEntity(pos) instanceof PigmeeDimensionBlockEntity be ? be : null;
        this.snapshot = snapshot == null ? new CompoundTag() : snapshot;
    }

    public boolean even() { return snapshot.getBoolean("Even"); }
    public PigmeeDimensionKind kind() { return PigmeeDimensionKind.load(snapshot.getString("Kind"), even()); }
    public boolean custom() { return kind() == PigmeeDimensionKind.CUSTOM; }
    public int owned() { return snapshot.getInt("Owned"); }
    public int limit() { return snapshot.getInt("Limit"); }
    public boolean mayBind() { return snapshot.getBoolean("MayBind"); }
    public int bound() { return snapshot.getInt("Bound"); }
    public UUID viewer() { return snapshot.getUUID("Viewer"); }

    public List<PigmeeDimensionData.Entry> entries() {
        return snapshot.getList("Entries", Tag.TAG_COMPOUND).stream()
                .map(tag -> PigmeeDimensionData.Entry.load((CompoundTag) tag)).toList();
    }

    public static void open(ServerPlayer player, BlockPos pos) {
        var state = player.level().getBlockState(pos);
        if (!(state.getBlock() instanceof PigmeeDimensionBlock block)
                || !(player.level().getBlockEntity(pos) instanceof PigmeeDimensionBlockEntity be)) return;
        var data = PigmeeDimensionData.get(player.server);
        var snapshot = new CompoundTag();
        snapshot.putBoolean("Even", block.even());
        snapshot.putString("Kind", block.kind().name());
        snapshot.putInt("Owned", (int) data.countOwned(player.getUUID()));
        snapshot.putInt("Limit", AE2LTCommonConfig.maxOwnedPigmeeDimensionsPerPlayer());
        snapshot.putInt("Bound", be.dimensionId());
        snapshot.putBoolean("MayBind", player.hasPermissions(2) || be.placer() == null || player.getUUID().equals(be.placer()));
        snapshot.putUUID("Viewer", player.getUUID());
        var list = new ListTag();
        for (var entry : data.entries()) {
            if (entry.kind() == block.kind() && entry.access().getPlayerAccess(player).canUse()) list.add(entry.saveForMenu());
        }
        snapshot.put("Entries", list);
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, owner) -> new PigmeeDimensionMenu(id, inventory, pos, snapshot), state.getBlock().getName()),
                buf -> { buf.writeBlockPos(pos); buf.writeNbt(snapshot); });
    }

    public void handle(ServerPlayer player, PigmeeDimensionActionPacket packet) {
        if (player.containerMenu != this || packet.containerId() != containerId || !stillValid(player)) return;
        var data = PigmeeDimensionData.get(player.server);
        var entry = data.find(packet.dimensionId());
        var materials = PigmeeDimensionMaterials.all();
        if (packet.frame() < 0 || packet.frame() >= materials.size()
                || packet.center() < 0 || packet.center() >= materials.size()) return;
        PigmeeChunkGenerator.Palette palette;
        try {
            var cells = custom() ? PigmeeDimensionPattern.decode(packet.pattern()) : List.<net.minecraft.world.level.block.state.BlockState>of();
            if (!custom() && packet.pattern().length != 0) return;
            palette = new PigmeeChunkGenerator.Palette(materials.get(packet.frame()).defaultBlockState(),
                    materials.get(packet.center()).defaultBlockState(), cells);
        } catch (IllegalArgumentException ignored) { return; }
        boolean sameKind = entry != null && entry.kind() == kind();
        boolean mayBind = player.hasPermissions(2) || player.getUUID().equals(host.placer())
                || host.placer() == null;
        boolean changed = false;
        switch (packet.action()) {
            case PigmeeDimensionActionPacket.CREATE -> {
                if (mayBind) {
                    var created = data.create(player, packet.name(), kind(), palette, packet.shared());
                    if (created != null) { host.bind(created.id()); changed = true; }
                }
            }
            case PigmeeDimensionActionPacket.BIND -> {
                if (mayBind && sameKind && entry.access().getPlayerAccess(player).canUse()) {
                    host.bind(entry.id()); changed = true;
                }
            }
            case PigmeeDimensionActionPacket.ENTER -> {
                if (sameKind) PigmeeDimensionTravel.enter(player, entry.id());
                return;
            }
            case PigmeeDimensionActionPacket.CONFIGURE -> {
                if (sameKind) changed = data.configure(player, entry.id(), packet.name(), palette, packet.shared());
            }
            case PigmeeDimensionActionPacket.MEMBER -> {
                if (sameKind) {
                    try {
                        changed = data.changeMember(player, entry.id(), UUID.fromString(packet.target()), packet.role());
                    } catch (IllegalArgumentException ignored) {
                        var target = player.server.getPlayerList().getPlayerByName(packet.target());
                        if (target != null) changed = data.changeMember(player, entry.id(), target.getUUID(), packet.role());
                    }
                }
            }
            default -> { return; }
        }
        PigmeeDimensionTravel.message(player, changed ? "saved" : "rejected");
        open(player, pos);
    }

    @Override
    public boolean stillValid(Player player) {
        return host != null && player.level() == level && level.getBlockEntity(pos) == host
                && player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64;
    }

    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
}
