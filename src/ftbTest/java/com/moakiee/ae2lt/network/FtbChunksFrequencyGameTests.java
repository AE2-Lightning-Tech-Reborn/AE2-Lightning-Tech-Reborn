package com.moakiee.ae2lt.network;

import static com.moakiee.ae2lt.network.FrequencyPermissionsGameTests.context;
import static com.moakiee.ae2lt.network.FrequencyPermissionsGameTests.require;

import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity;
import com.moakiee.ae2lt.blockentity.WirelessReceiverBlockEntity;
import com.moakiee.ae2lt.grid.FrequencyAccessLevel;
import com.moakiee.ae2lt.grid.FrequencyBindingHelper;
import com.moakiee.ae2lt.grid.FrequencySecurityLevel;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.grid.wirelesslink.WirelessLinkRegistry;
import com.moakiee.ae2lt.item.OverloadedWirelessConnectorItem;
import com.moakiee.ae2lt.logic.world.PlayerWorldActions;
import com.moakiee.ae2lt.menu.FrequencyMenu;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;
import com.mojang.authlib.GameProfile;
import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.FTBChunksProperties;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.TeamRank;
import dev.ftb.mods.ftbteams.api.property.PrivacyMode;
import dev.ftb.mods.ftbteams.data.AbstractTeam;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Opt-in tests against unmodified real FTB jars; no synthetic protection listener. */
@GameTestHolder("ae2lt_frequency_ftb")
@PrefixGameTestTemplate(false)
public final class FtbChunksFrequencyGameTests {
    private static ServerPlayer player(GameTestHelper helper, String name, BlockPos near) {
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), name), ClientInformation.createDefault());
        new ServerGamePacketListenerImpl(helper.getLevel().getServer(), new Connection(PacketFlow.SERVERBOUND),
                player, CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
            @Override public void send(Packet<?> packet) {} // No client needed for server-side assertions.
        };
        player.getAbilities().mayBuild = true;
        player.setPos(near.getX() + 0.5, near.getY() + 0.5, near.getZ() + 0.5);
        return player;
    }

    private static final class Claim implements AutoCloseable {
        final GameTestHelper helper;
        final AbstractTeam team;
        final ChunkTeamData data;
        final ChunkDimPos chunk;

        Claim(GameTestHelper helper, BlockPos pos) throws Exception {
            this.helper = helper;
            require(ModList.get().isLoaded("ftbchunks") && ModList.get().isLoaded("ftbteams"),
                    "real FTB Chunks and Teams must be loaded");
            var manager = FTBChunksAPI.api().getManager();
            chunk = new ChunkDimPos(helper.getLevel(), pos);
            require(manager.getChunk(chunk) == null, "test chunk already claimed");
            team = (AbstractTeam) FTBTeamsAPI.api().getManager().createServerTeam(
                    helper.getLevel().getServer().createCommandSourceStack(),
                    "lt-test-" + UUID.randomUUID(), "LT permission integration", Color4I.WHITE);
            team.setProperty(FTBChunksProperties.BLOCK_INTERACT_MODE, PrivacyMode.PRIVATE);
            team.setProperty(FTBChunksProperties.BLOCK_EDIT_MODE, PrivacyMode.PRIVATE);
            team.setProperty(FTBChunksProperties.ALLOW_ALL_FAKE_PLAYERS, false);
            team.setProperty(FTBChunksProperties.ALLOW_FAKE_PLAYERS_BY_ID, false);
            data = manager.getOrCreateData(team);
            var result = data.claim(helper.getLevel().getServer().createCommandSourceStack(), chunk, false);
            require(result.isSuccess(), "real FTB claim failed: " + result.getResultId());
            require(manager.getChunk(chunk) != null, "claim was not registered");
        }

        void allow(ServerPlayer player) { team.addMember(player.getUUID(), TeamRank.MEMBER); }
        void deny(ServerPlayer player) { team.removeMember(player.getUUID()); }

        @Override public void close() {
            var result = data.unclaim(helper.getLevel().getServer().createCommandSourceStack(), chunk, false);
            require(result.isSuccess(), "test claim cleanup failed");
            require(FTBChunksAPI.api().getManager().getChunk(chunk) == null, "test claim was not removed");
        }
    }

    @GameTest(templateNamespace = "ae2lt", template = "workstation_test")
    public static void realClaimProtectsMenuCardAndMemoryImport(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 1, 2));
        var actor = player(helper, "FtbOutsider", pos);
        var owner = player(helper, "FrequencyOwner", pos);
        var manager = WirelessFrequencyManager.get();
        var current = manager.createFrequency(owner, "public", 0, FrequencySecurityLevel.PUBLIC, "");
        var replacement = manager.createFrequency(actor, "replacement", 0, FrequencySecurityLevel.PUBLIC, "");
        level.setBlockAndUpdate(pos, ModBlocks.WIRELESS_RECEIVER.get().defaultBlockState());
        var receiver = (WirelessReceiverBlockEntity) level.getBlockEntity(pos);
        receiver.setFrequency(current.getId());
        actor.containerMenu = new FrequencyMenu(42, actor.getInventory(), receiver);
        try (var claim = new Claim(helper, pos)) {
            require(!PlayerWorldActions.mayInteract(actor, level, pos), "real FTB did not reject outsider");
            SelectFrequencyPacket.handle(new SelectFrequencyPacket(42, pos, replacement.getId(), ""), context(actor));
            require(receiver.getFrequencyId() == current.getId(), "menu bypassed real claim");
            var feedback = WirelessLinkRegistry.get(level.getServer()).handleManualUse(
                    actor, current.getId(), level, pos, Direction.UP, Vec3.atCenterOf(pos));
            require(receiver.getFrequencyId() == current.getId(), "frequency card unlinked claimed receiver");
            require(feedback.translationKey().equals("ae2lt.gui.error.no_permission"), "card did not reject claim");
            var tag = new CompoundTag();
            FrequencyBindingHelper.writeMemoryFrequency(tag, replacement.getId());
            require(!FrequencyBindingHelper.importMemoryFrequency(tag, receiver::setFrequency, actor, receiver),
                    "memory import bypassed real claim");

            claim.allow(actor);
            require(PlayerWorldActions.mayInteract(actor, level, pos), "FTB member could not interact");
            require(current.getPlayerAccess(actor) == FrequencyAccessLevel.USER, "public outsider fixture changed");
            SelectFrequencyPacket.handle(new SelectFrequencyPacket(42, pos, replacement.getId(), ""), context(actor));
            require(receiver.getFrequencyId() == replacement.getId(), "FTB member menu was denied");
            FrequencyBindingHelper.writeMemoryFrequency(tag, current.getId());
            require(FrequencyBindingHelper.importMemoryFrequency(tag, receiver::setFrequency, actor, receiver),
                    "FTB member memory import was denied");
            WirelessLinkRegistry.get(level.getServer()).handleManualUse(
                    actor, current.getId(), level, pos, Direction.UP, Vec3.atCenterOf(pos));
            require(receiver.getFrequencyId() != current.getId(), "FTB member card could not unlink");
        } finally {
            actor.containerMenu = actor.inventoryMenu;
            manager.deleteFrequency(current.getId(), level.getServer());
            manager.deleteFrequency(replacement.getId(), level.getServer());
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "ae2lt", template = "workstation_test")
    public static void realClaimRechecksPreviouslySelectedHost(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        // Put the selected host across a chunk boundary from an otherwise usable target.
        var anchor = helper.absolutePos(new BlockPos(2, 2, 2));
        int boundary = ((anchor.getX() >> 4) + 1) << 4;
        var hostPos = new BlockPos(boundary, anchor.getY(), anchor.getZ());
        var targetPos = hostPos.west();
        level.getChunkAt(hostPos);
        level.getChunkAt(targetPos);
        level.setBlockAndUpdate(hostPos, ModBlocks.OVERLOADED_INTERFACE.get().defaultBlockState());
        level.setBlockAndUpdate(targetPos, Blocks.BARREL.defaultBlockState());
        var host = (OverloadedInterfaceBlockEntity) level.getBlockEntity(hostPos);
        host.setInterfaceMode(OverloadedInterfaceBlockEntity.InterfaceMode.WIRELESS);
        var actor = player(helper, "FtbSelectedHost", targetPos);
        var connector = new ItemStack(ModItems.OVERLOADED_WIRELESS_CONNECT_TOOL.get());
        actor.setItemInHand(InteractionHand.MAIN_HAND, connector);
        OverloadedWirelessConnectorItem.selectHost(connector, level, hostPos, OverloadedWirelessConnectorItem.HOST_INTERFACE);
        var packet = new WirelessConnectorUsePacket(InteractionHand.MAIN_HAND, targetPos, Direction.UP, false);
        try (var claim = new Claim(helper, hostPos)) {
            require(PlayerWorldActions.mayInteract(actor, level, targetPos), "target must be usable outside claim");
            WirelessConnectorUsePacket.handle(packet, context(actor));
            require(host.getConnections().isEmpty(), "stored host selection bypassed real claim");
            claim.allow(actor);
            WirelessConnectorUsePacket.handle(packet, context(actor));
            require(host.getConnections().size() == 1, "FTB member connector could not connect");
            claim.deny(actor);
            WirelessConnectorUsePacket.handle(packet, context(actor));
            require(host.getConnections().size() == 1, "revoked member disconnected claimed host");
        } finally {
            level.removeBlock(hostPos, false);
            level.removeBlock(targetPos, false);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "ae2lt", template = "workstation_test")
    public static void realClaimFiltersEachContiguousTarget(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var anchor = helper.absolutePos(new BlockPos(2, 2, 2));
        int boundary = ((anchor.getX() >> 4) + 1) << 4;
        var denied = new BlockPos(boundary, anchor.getY(), anchor.getZ());
        var allowed = denied.west();
        var hostPos = allowed.west(2);
        level.getChunkAt(denied);
        level.getChunkAt(hostPos);
        level.setBlockAndUpdate(hostPos, ModBlocks.OVERLOADED_INTERFACE.get().defaultBlockState());
        level.setBlockAndUpdate(allowed, Blocks.BARREL.defaultBlockState());
        level.setBlockAndUpdate(denied, Blocks.BARREL.defaultBlockState());
        var host = (OverloadedInterfaceBlockEntity) level.getBlockEntity(hostPos);
        host.setInterfaceMode(OverloadedInterfaceBlockEntity.InterfaceMode.WIRELESS);
        var actor = player(helper, "FtbBatch", allowed);
        var connector = new ItemStack(ModItems.OVERLOADED_WIRELESS_CONNECT_TOOL.get());
        actor.setItemInHand(InteractionHand.MAIN_HAND, connector);
        OverloadedWirelessConnectorItem.selectHost(connector, level, hostPos, OverloadedWirelessConnectorItem.HOST_INTERFACE);
        try (var claim = new Claim(helper, denied)) {
            require(PlayerWorldActions.mayInteract(actor, level, allowed), "batch origin must be usable");
            require(!PlayerWorldActions.mayInteract(actor, level, denied), "neighbor must be protected");
            WirelessConnectorUsePacket.handle(new WirelessConnectorUsePacket(
                    InteractionHand.MAIN_HAND, allowed, Direction.UP, true), context(actor));
            require(host.getConnections().size() == 1 && host.getConnections().getFirst().pos().equals(allowed),
                    "contiguous batch crossed real claim boundary");
            var packet = new WirelessConnectorUsePacket(InteractionHand.MAIN_HAND, denied, Direction.UP, false);
            claim.allow(actor);
            WirelessConnectorUsePacket.handle(packet, context(actor));
            require(host.getConnections().size() == 2, "FTB member could not link protected neighbor");
            claim.deny(actor);
            WirelessConnectorUsePacket.handle(packet, context(actor));
            require(host.getConnections().size() == 2, "revoked member disconnected protected neighbor");
        } finally {
            level.removeBlock(hostPos, false);
            level.removeBlock(allowed, false);
            level.removeBlock(denied, false);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "ae2lt", template = "workstation_test")
    public static void realClaimProtectsPlayerAndFakePlayerPlacement(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var actor = player(helper, "FtbBuilder", pos);
        var fake = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "FtbFakeBuilder"));
        fake.getAbilities().mayBuild = true;
        var state = ModBlocks.MATTER_WARPING_MATRIX_CONSTRAINT_FRAME.get().defaultBlockState();
        level.removeBlock(pos, false);
        try (var claim = new Claim(helper, pos)) {
            require(!PlayerWorldActions.placeBlock(actor, level, pos, state), "outsider placed inside real claim");
            require(level.isEmptyBlock(pos) && level.getBlockEntity(pos) == null, "denied player placement did not roll back");
            require(!PlayerWorldActions.placeBlock(fake, level, pos, state), "fake player bypassed real claim");
            require(level.isEmptyBlock(pos) && level.getBlockEntity(pos) == null, "denied fake placement did not roll back");
            claim.allow(actor);
            require(PlayerWorldActions.placeBlock(actor, level, pos, state), "member player placement failed");
            require(level.getBlockState(pos).equals(state), "member placement changed requested state");
            level.removeBlock(pos, false);
            claim.allow(fake);
            claim.team.setProperty(FTBChunksProperties.ALLOW_FAKE_PLAYERS_BY_ID, true);
            require(PlayerWorldActions.placeBlock(fake, level, pos, state), "explicitly allowed fake player placement failed");
            require(level.getBlockState(pos).equals(state), "fake placement changed requested state");
            require(!level.captureBlockSnapshots && !level.restoringBlockSnapshots && level.capturedBlockSnapshots.isEmpty(),
                    "real FTB placement leaked snapshot state");
        }
        helper.succeed();
    }
}
