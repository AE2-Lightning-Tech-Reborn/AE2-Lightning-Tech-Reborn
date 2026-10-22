package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity;
import com.moakiee.ae2lt.blockentity.WirelessReceiverBlockEntity;
import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.grid.FrequencyAccessLevel;
import com.moakiee.ae2lt.grid.FrequencyBindingHelper;
import com.moakiee.ae2lt.grid.FrequencySecurityLevel;
import com.moakiee.ae2lt.grid.WirelessFrequency;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.grid.wirelesslink.WirelessLinkRegistry;
import com.moakiee.ae2lt.item.OverloadedWirelessConnectorItem;
import com.moakiee.ae2lt.logic.world.PlayerWorldActions;
import com.moakiee.ae2lt.menu.FrequencyMenu;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import java.util.function.Supplier;

@GameTestHolder("ae2lt_frequency_permissions")
@PrefixGameTestTemplate(false)
public final class FrequencyPermissionsGameTests {
    static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        player.getAbilities().mayBuild = true;
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        return player;
    }

    static Supplier<NetworkEvent.Context> context(ServerPlayer player) {
        try {
            var connection = new Connection(PacketFlow.SERVERBOUND);
            connection.setListener(player.connection);
            var constructor = NetworkEvent.Context.class.getDeclaredConstructor(
                    Connection.class, NetworkDirection.class, int.class);
            constructor.setAccessible(true);
            var context = constructor.newInstance(connection, NetworkDirection.PLAY_TO_SERVER, 0);
            return () -> context;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot construct Forge packet context", e);
        }
    }

    @GameTest(template = "empty")
    public static void ownerQuotaIncludesPromotionsDemotionsAndReload(GameTestHelper helper) throws Exception {
        var manager = WirelessFrequencyManager.get();
        var owner = player(helper, "QuotaOwner");
        var other = player(helper, "QuotaOther");
        var ids = new ArrayList<Integer>();
        ForgeConfigSpec.IntValue config = AE2LTCommonConfig.SPEC.getValues()
                .get("wirelessFrequency.maxOwnedFrequenciesPerPlayer");
        int original = config.get();
        try {
            require(original == 16, "default owner quota must be 16");
            WirelessFrequency first = null;
            for (int i = 0; i < 16; i++) {
                var frequency = manager.createFrequency(owner, "quota", 0, FrequencySecurityLevel.PUBLIC, "");
                require(frequency != null, "frequency below quota rejected");
                ids.add(frequency.getId());
                if (first == null) first = frequency;
            }
            require(manager.countOwnedFrequencies(owner.getUUID()) == 16, "creation count");
            require(manager.createFrequency(owner, "overflow", 0, FrequencySecurityLevel.PUBLIC, "") == null,
                    "17th owned frequency must be rejected");
            var otherFrequency = manager.createFrequency(other, "co-owner", 0, FrequencySecurityLevel.PUBLIC, "");
            ids.add(otherFrequency.getId());
            otherFrequency.enrollAsUser(owner);
            require(otherFrequency.changeMembership(other, owner.getUUID(), WirelessFrequency.MEMBERSHIP_TRANSFER_OWNERSHIP)
                    == WirelessFrequency.RESPONSE_OWNER_LIMIT, "promotion bypassed the quota");
            first.enrollAsUser(other);
            require(first.changeMembership(owner, other.getUUID(), WirelessFrequency.MEMBERSHIP_TRANSFER_OWNERSHIP)
                    == WirelessFrequency.RESPONSE_SUCCESS, "co-owner promotion below quota failed");
            require(first.changeMembership(other, owner.getUUID(), WirelessFrequency.MEMBERSHIP_SET_USER)
                    == WirelessFrequency.RESPONSE_SUCCESS, "co-owner could not demote original owner");
            require(manager.countOwnedFrequencies(owner.getUUID()) == 15, "count used stale creator UUID");
            require(otherFrequency.changeMembership(other, owner.getUUID(), WirelessFrequency.MEMBERSHIP_TRANSFER_OWNERSHIP)
                    == WirelessFrequency.RESPONSE_SUCCESS, "freed owner slot was not reusable");
            var saved = manager.save(new CompoundTag());
            var constructor = WirelessFrequencyManager.class.getDeclaredConstructor(CompoundTag.class);
            constructor.setAccessible(true);
            var restored = constructor.newInstance(saved);
            require(restored.countOwnedFrequencies(owner.getUUID()) == 16, "reload changed ownership quota");
            config.set(2);
            require(manager.countOwnedFrequencies(owner.getUUID()) == 16, "lowering config deleted existing ownership");
            require(!manager.canOwnAnotherFrequency(owner.getUUID()), "lowered config was ignored");
        } finally {
            config.set(original);
            for (int id : ids) manager.deleteFrequency(id, helper.getLevel().getServer());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void deniedDeviceBlocksMenuCardAndMemoryImport(GameTestHelper helper) {
        var level = helper.getLevel();
        var actor = player(helper, "DeniedDevice");
        var owner = player(helper, "DeviceOwner");
        var manager = WirelessFrequencyManager.get();
        var current = manager.createFrequency(owner, "public", 0, FrequencySecurityLevel.PUBLIC, "");
        var replacement = manager.createFrequency(actor, "replacement", 0, FrequencySecurityLevel.PUBLIC, "");
        var privateFrequency = manager.createFrequency(owner, "private", 0, FrequencySecurityLevel.PRIVATE, "");
        var pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlockAndUpdate(pos, ModBlocks.WIRELESS_RECEIVER.get().defaultBlockState());
        var receiver = (WirelessReceiverBlockEntity) level.getBlockEntity(pos);
        receiver.setFrequency(current.getId());
        actor.containerMenu = new FrequencyMenu(42, actor.getInventory(), receiver);
        Consumer<PlayerInteractEvent.RightClickBlock> deny = event -> {
            if (event.getEntity() == actor && event.getPos().equals(pos)) event.setCanceled(true);
        };
        MinecraftForge.EVENT_BUS.addListener(deny);
        try {
            SelectFrequencyPacket.handle(new SelectFrequencyPacket(42, pos, replacement.getId(), ""), context(actor));
            require(receiver.getFrequencyId() == current.getId(), "denied menu changed binding");
            var feedback = WirelessLinkRegistry.get(level.getServer()).handleManualUse(
                    actor, current.getId(), level, pos, Direction.UP, Vec3.atCenterOf(pos));
            require(receiver.getFrequencyId() == current.getId(), "denied card unlinked device");
            require(feedback.translationKey().equals("ae2lt.gui.error.no_permission"), "card did not reject target");
            var tag = new CompoundTag();
            FrequencyBindingHelper.writeMemoryFrequency(tag, replacement.getId());
            require(!FrequencyBindingHelper.importMemoryFrequency(tag, receiver::setFrequency, actor, receiver),
                    "denied memory import succeeded");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(deny);
        }
        try {
            require(current.getPlayerAccess(actor) == FrequencyAccessLevel.USER, "fixture must be a public outsider");
            SelectFrequencyPacket.handle(new SelectFrequencyPacket(42, pos, replacement.getId(), ""), context(actor));
            require(receiver.getFrequencyId() == replacement.getId(), "allowed public user required manager rank");
            var tag = new CompoundTag();
            FrequencyBindingHelper.writeMemoryFrequency(tag, privateFrequency.getId());
            require(!FrequencyBindingHelper.importMemoryFrequency(tag, receiver::setFrequency, actor, receiver),
                    "memory card bypassed private frequency access");
            FrequencyBindingHelper.writeMemoryFrequency(tag, current.getId());
            require(FrequencyBindingHelper.importMemoryFrequency(tag, receiver::setFrequency, actor, receiver),
                    "authorized public memory import failed");
        } finally {
            actor.containerMenu = actor.inventoryMenu;
            for (var frequency : new WirelessFrequency[] {current, replacement, privateFrequency}) {
                manager.deleteFrequency(frequency.getId(), level.getServer());
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void connectorRechecksSelectedHostAndEveryTarget(GameTestHelper helper) {
        var level = helper.getLevel();
        var actor = player(helper, "ConnectorRights");
        var hostPos = helper.absolutePos(new BlockPos(1, 1, 2));
        var targetPos = helper.absolutePos(new BlockPos(3, 1, 2));
        level.setBlockAndUpdate(hostPos, ModBlocks.OVERLOADED_INTERFACE.get().defaultBlockState());
        level.setBlockAndUpdate(targetPos, Blocks.CHEST.defaultBlockState());
        var host = (OverloadedInterfaceBlockEntity) level.getBlockEntity(hostPos);
        host.setInterfaceMode(OverloadedInterfaceBlockEntity.InterfaceMode.WIRELESS);
        var connector = new ItemStack(com.moakiee.ae2lt.registry.ModItems.OVERLOADED_WIRELESS_CONNECT_TOOL.get());
        actor.setItemInHand(InteractionHand.MAIN_HAND, connector);
        OverloadedWirelessConnectorItem.selectHost(connector, level, hostPos, OverloadedWirelessConnectorItem.HOST_INTERFACE);
        Consumer<PlayerInteractEvent.RightClickBlock> denyHost = event -> {
            if (event.getEntity() == actor && event.getPos().equals(hostPos)) event.setCanceled(true);
        };
        var packet = new WirelessConnectorUsePacket(InteractionHand.MAIN_HAND, targetPos, Direction.UP, false);
        MinecraftForge.EVENT_BUS.addListener(denyHost);
        try {
            WirelessConnectorUsePacket.handle(packet, context(actor));
            require(host.getConnections().isEmpty(), "selected host was not reauthorized");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(denyHost);
        }
        WirelessConnectorUsePacket.handle(packet, context(actor));
        require(host.getConnections().size() == 1, "allowed connector link failed");
        Consumer<PlayerInteractEvent.RightClickBlock> denyTarget = event -> {
            if (event.getEntity() == actor && event.getPos().equals(targetPos)) event.setCanceled(true);
        };
        MinecraftForge.EVENT_BUS.addListener(denyTarget);
        try {
            WirelessConnectorUsePacket.handle(packet, context(actor));
            require(host.getConnections().size() == 1, "denied target was disconnected");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(denyTarget);
        }
        WirelessConnectorUsePacket.handle(packet, context(actor));
        require(host.getConnections().isEmpty(), "allowed disconnect failed");
        var deniedNeighbor = targetPos.east();
        level.setBlockAndUpdate(deniedNeighbor, Blocks.CHEST.defaultBlockState());
        Consumer<PlayerInteractEvent.RightClickBlock> denyNeighbor = event -> {
            if (event.getEntity() == actor && event.getPos().equals(deniedNeighbor)) event.setCanceled(true);
        };
        MinecraftForge.EVENT_BUS.addListener(denyNeighbor);
        try {
            WirelessConnectorUsePacket.handle(new WirelessConnectorUsePacket(
                    InteractionHand.MAIN_HAND, targetPos, Direction.UP, true), context(actor));
            require(host.getConnections().size() == 1
                    && host.getConnections().get(0).pos().equals(targetPos),
                    "contiguous connection did not filter the denied neighboring target");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(denyNeighbor);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void placementUsesActorAndRollsBackDeniedPositions(GameTestHelper helper) {
        var level = helper.getLevel();
        var actor = player(helper, "AutoBuilder");
        var denied = helper.absolutePos(new BlockPos(2, 2, 2));
        var allowed = helper.absolutePos(new BlockPos(3, 2, 2));
        level.removeBlock(denied, false);
        level.removeBlock(allowed, false);
        var placed = ModBlocks.MATTER_WARPING_MATRIX_CONSTRAINT_FRAME.get().defaultBlockState();
        var observed = new AtomicInteger();
        Consumer<BlockEvent.EntityPlaceEvent> protection = event -> {
            if (event.getEntity() != actor) return;
            require(event.getPlacedBlock().equals(placed), "placement event contains the wrong new state");
            observed.incrementAndGet();
            if (event.getPos().equals(denied)) event.setCanceled(true);
        };
        MinecraftForge.EVENT_BUS.addListener(protection);
        try {
            require(!PlayerWorldActions.placeBlock(actor, level, denied, placed), "denied placement succeeded");
            require(level.isEmptyBlock(denied) && level.getBlockEntity(denied) == null, "denied placement was not rolled back");
            require(PlayerWorldActions.placeBlock(actor, level, allowed, placed), "allowed placement failed");
            require(level.getBlockState(allowed).equals(placed), "allowed fixed state changed");
            require(observed.get() == 2, "each position must receive an actor-aware placement event");
            require(!level.captureBlockSnapshots && !level.restoringBlockSnapshots && level.capturedBlockSnapshots.isEmpty(),
                    "placement leaked snapshot state");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(protection);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void passwordBudgetSkipsBrowsingAndAuthorizedUse(GameTestHelper helper) {
        var actor = player(helper, "PasswordTry");
        var owner = player(helper, "PasswordOwner");
        var frequency = new WirelessFrequency(123, "encrypted", 0, owner, FrequencySecurityLevel.ENCRYPTED, "correct");
        for (int i = 0; i < 20; i++) require(FrequencyPasswordAttempts.allow(actor, frequency, ""), "browsing counted as guessing");
        for (int i = 0; i < 5; i++) require(FrequencyPasswordAttempts.allow(actor, frequency, "wrong"), "one of five guesses rejected");
        require(!FrequencyPasswordAttempts.allow(actor, frequency, "correct"), "sixth guess must be rejected before verification");
        frequency.enrollAsUser(actor);
        for (int i = 0; i < 20; i++) require(FrequencyPasswordAttempts.allow(actor, frequency, "correct"), "authorized use counted as guessing");
        helper.succeed();
    }
}
