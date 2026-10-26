package com.moakiee.ae2lt.logic.energy;

import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.networking.security.IActionSource;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.blockentity.AdvancedWirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.grid.FrequencySecurityLevel;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.item.OverloadedFrequencyCardItem;
import com.moakiee.ae2lt.menu.FrequencyMenu;
import com.moakiee.ae2lt.network.FrequencyCardUsePacket;
import com.moakiee.ae2lt.network.SelectFrequencyPacket;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@GameTestHolder("ae2lt_frequency_card")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "ae2lt", bus = EventBusSubscriber.Bus.MOD)
public final class FrequencyCardGameTests {
    private static final BlockPos C = new BlockPos(2, 2, 2), TARGET = new BlockPos(2, 2, 6);
    private static final Map<BlockEntity, EnergyStorage> ME_ENERGY = new WeakHashMap<>();
    @SubscribeEvent public static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(Capabilities.EnergyStorage.BLOCK, (level, pos, state, be, face) ->
                face == Direction.SOUTH && be != null ? ME_ENERGY.computeIfAbsent(be, ignored -> new EnergyStorage(100000)) : null,
                AEBlocks.CABLE_BUS.block(), AEBlocks.DRIVE.block(), AEBlocks.INTERFACE.block(), AEBlocks.PATTERN_PROVIDER.block(),
                ModBlocks.OVERLOADED_INTERFACE.get(), ModBlocks.OVERLOADED_PATTERN_PROVIDER.get());
    }
    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty")
    public static void autoConnectionPreferencesMigratePersistAndSelectIndependently(GameTestHelper h) {
        var player = new MenuPlayer(h.getLevel());
        var card = new ItemStack(ModItems.OVERLOADED_FREQUENCY_CARD.get());
        var legacy = new net.minecraft.nbt.CompoundTag();
        legacy.putInt("FrequencyId", 12);
        legacy.putBoolean("AutoConnect", true);
        legacy.putString("OwnerUuid", player.getUUID().toString());
        card.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(legacy));
        var migrated = OverloadedFrequencyCardItem.getData(card);
        h.assertTrue(migrated.autoConnect() && migrated.autoConnectEnergy(), "legacy combined setting must survive migration");
        player.setItemInHand(InteractionHand.MAIN_HAND, card);
        for (boolean channels : new boolean[] {false, true}) for (boolean energy : new boolean[] {false, true}) {
            var data = migrated.withAutoConnect(channels).withAutoConnectEnergy(energy);
            OverloadedFrequencyCardItem.setData(card, data);
            h.assertTrue(OverloadedFrequencyCardItem.getData(card.copy()).equals(data), "independent flags lost in item persistence");
            h.assertTrue(OverloadedFrequencyCardItem.findAutoConnectCard(player).isPresent() == channels,
                    "channel admission consulted the FE preference");
            h.assertTrue(OverloadedFrequencyCardItem.findAutoConnectEnergyCard(player).isPresent() == energy,
                    "FE admission consulted the channel preference");
            var rebound = data.clearFrequency().bindFrequency(13, "minecraft:overworld", 0, player.getUUID());
            h.assertTrue(rebound.autoConnect() == channels && rebound.autoConnectEnergy() == energy,
                    "rebinding reset independent preferences");
        }
        var foreign = migrated.bindFrequency(12, "minecraft:overworld", 0, UUID.randomUUID());
        OverloadedFrequencyCardItem.setData(card, foreign);
        h.assertTrue(OverloadedFrequencyCardItem.findAutoConnectCard(player).isEmpty()
                && OverloadedFrequencyCardItem.findAutoConnectEnergyCard(player).isEmpty(), "foreign card admitted automatic connections");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            for (boolean energy : new boolean[] {false, true}) {
                var packet = com.moakiee.ae2lt.network.ToggleFrequencyCardAutoConnectPacket.forTerminalCard(energy);
                packet.write(buffer);
                h.assertTrue(packet.equals(com.moakiee.ae2lt.network.ToggleFrequencyCardAutoConnectPacket.decode(buffer)),
                        "toggle target lost in packet roundtrip");
            }
        } finally { buffer.release(); }
        h.succeed();
    }

    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty")
    public static void airUseMainAndOffhandWritesSelectionAndInvalidatesReplacedCard(GameTestHelper h) {
        for (var hand : InteractionHand.values()) {
            var p = new MenuPlayer(h.getLevel()); var card = new ItemStack(ModItems.OVERLOADED_FREQUENCY_CARD.get());
            p.setItemInHand(hand, card);
            var frequency = WirelessFrequencyManager.get().createFrequency(p, "Held card", 0xffffff, FrequencySecurityLevel.PRIVATE, "");
            card.getItem().use(h.getLevel(), p, hand);
            h.assertTrue(p.containerMenu instanceof FrequencyMenu, "air use did not open menu: " + hand);
            var menu = (FrequencyMenu) p.containerMenu;
            h.assertTrue(menu.isCardMode() && !menu.hasParentMenu() && menu.stillValid(p) && !p.encodedParent, "held card has invalid parent/host");
            SelectFrequencyPacket.handle(new SelectFrequencyPacket(menu.containerId, BlockPos.ZERO, frequency.getId(), ""), context(p));
            h.assertTrue(OverloadedFrequencyCardItem.getData(card).frequencyId() == frequency.getId(), "selection was not written to held card");
            h.assertTrue(menu.updateCard(d -> d.toggleAutoConnect()), "held card auto update failed");
            var replacement = card.copy(); p.setItemInHand(hand, replacement);
            h.assertTrue(!menu.stillValid(p), "replacement retained stale GUI authority");
            SelectFrequencyPacket.handle(new SelectFrequencyPacket(menu.containerId, BlockPos.ZERO, -1, ""), context(p));
            h.assertTrue(OverloadedFrequencyCardItem.getData(replacement).isBound(), "stale packet changed replacement");
            p.setShiftKeyDown(true); replacement.getItem().use(h.getLevel(), p, hand);
            h.assertTrue(!OverloadedFrequencyCardItem.getData(replacement).isBound()
                    && OverloadedFrequencyCardItem.getData(replacement).autoConnect(), "shift clear lost auto preference");
        }
        h.succeed();
    }
    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty")
    public static void nonBlockEntityClicksMatchAirUseForBothHandsAndHooks(GameTestHelper h) {
        for (var block : List.of(Blocks.STONE, Blocks.CRAFTING_TABLE)) {
            h.setBlock(TARGET, block);
            var pos = h.absolutePos(TARGET);
            h.assertTrue(h.getLevel().getBlockEntity(pos) == null, "non-BE fixture has a block entity");
            for (var hand : InteractionHand.values()) for (boolean first : new boolean[]{true, false}) {
                var p = new MenuPlayer(h.getLevel());
                var card = new ItemStack(ModItems.OVERLOADED_FREQUENCY_CARD.get());
                var item = (OverloadedFrequencyCardItem)card.getItem();
                p.setItemInHand(hand, card);
                var use = new net.minecraft.world.item.context.UseOnContext(p, hand,
                        new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
                var result = first ? item.onItemUseFirst(card, use) : item.useOn(use);
                h.assertTrue(result.consumesAction() && p.containerMenu instanceof FrequencyMenu menu
                        && menu.isCardMode() && menu.stillValid(p), "non-BE click did not open held-card menu");
                OverloadedFrequencyCardItem.bindFrequency(card, 12, h.getLevel().dimension(), pos, p.getUUID());
                OverloadedFrequencyCardItem.setData(card, OverloadedFrequencyCardItem.getData(card).withAutoConnectEnergy(true));
                p.setShiftKeyDown(true);
                if (first) item.onItemUseFirst(card, use); else item.useOn(use);
                h.assertTrue(!OverloadedFrequencyCardItem.getData(card).isBound()
                        && OverloadedFrequencyCardItem.getData(card).autoConnectEnergy(), "shift non-BE use differs from shift-air clear");
                OverloadedFrequencyCardItem.bindFrequency(card, 12, h.getLevel().dimension(), pos, UUID.randomUUID());
                var foreign = OverloadedFrequencyCardItem.getData(card);
                result = first ? item.onItemUseFirst(card, use) : item.useOn(use);
                h.assertTrue(result == net.minecraft.world.InteractionResult.FAIL
                        && OverloadedFrequencyCardItem.getData(card).equals(foreign), "non-BE clear bypassed card ownership");
            }
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty")
    public static void foreignCardCannotOpenAndPacketPreservesModifier(GameTestHelper h) {
        var p = new MenuPlayer(h.getLevel()); var card = new ItemStack(ModItems.OVERLOADED_FREQUENCY_CARD.get());
        OverloadedFrequencyCardItem.bindFrequency(card, 12, h.getLevel().dimension(), BlockPos.ZERO, UUID.randomUUID());
        p.setItemInHand(InteractionHand.MAIN_HAND, card);
        h.assertTrue(!FrequencyMenu.openHeldCard(p, InteractionHand.MAIN_HAND), "foreign owner's card opened");
        var b = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            var packet = new FrequencyCardUsePacket(InteractionHand.OFF_HAND, TARGET, Direction.UP, 1, 2, 3, false, true);
            FrequencyCardUsePacket.STREAM_CODEC.encode(b, packet);
            h.assertTrue(packet.equals(FrequencyCardUsePacket.STREAM_CODEC.decode(b)), "bulk modifier packet roundtrip");
        } finally { b.release(); }
        h.succeed();
    }
    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 240)
    public static void bulkTraversesOnlyFaceAdjacentSameBlocksBeyondReachWithPerDeviceInputs(GameTestHelper h) {
        var f = fixture(h); var level = h.getLevel();
        for (int i = 0; i < 10; i++) {
            h.setBlock(TARGET.east(i), WirelessEnergyTestMachine.BLOCK);
            WirelessEnergyTestMachine m = h.getBlockEntity(TARGET.east(i)); m.inputFace = i % 2 == 0 ? Direction.SOUTH : Direction.UP;
        }
        h.setBlock(TARGET.offset(0, 1, 1), WirelessEnergyTestMachine.BLOCK); // diagonal only
        h.setBlock(TARGET.south(), Blocks.BARREL); // adjacent but different type
        h.runAfterDelay(35, () -> {
            var endpoints = WirelessEnergyEndpoints.get(level.getServer()); var seed = h.absolutePos(TARGET);
            var last = h.absolutePos(TARGET.east(9)); aim(f.player, last);
            h.assertTrue(endpoints.setBound(f.player, f.frequency, level, last, Direction.UP, true).equals("connected"), "mixed-state fixture");
            aim(f.player, seed);
            h.assertTrue(!f.player.canInteractWithBlock(last, 1), "last device must be beyond reach");
            var beforeGrid = f.controller.getMainNode().getGrid();
            useBulk(h, f, seed, () -> {
                var views = endpoints.inspect(f.frequency, f.player.getUUID());
                h.assertTrue(views.size() == 10, "bulk missed remote member or included diagonal/different block: " + views.size());
                for (int i = 0; i < 10; i++) {
                    final var pos = h.absolutePos(TARGET.east(i)); var direction = i % 2 == 0 ? Direction.SOUTH : Direction.UP;
                    h.assertTrue(views.stream().anyMatch(v -> v.location().pos().equals(pos) && v.face() == direction), "per-machine input face");
                    var admission = WirelessEnergyAdmissions.get(level.getServer()).inspect(level, pos, f.player.getUUID());
                    h.assertTrue(admission != null && admission.offered() == 0, "manual cluster performed an automatic FE probe");
                }
                h.assertTrue(f.controller.getMainNode().getGrid() == beforeGrid, "bulk modified ME topology");
                useBulk(h, f, seed, () -> {
                    h.assertTrue(endpoints.inspect(f.frequency, f.player.getUUID()).isEmpty(), "bulk disconnect left a member");
                    h.succeed();
                });
            });
        });
    }
    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 240)
    public static void bulkPreservesForeignFrequencyAndRejectsUnreachableSeed(GameTestHelper h) {
        var f = fixture(h); var level = h.getLevel();
        h.setBlock(TARGET, WirelessEnergyTestMachine.BLOCK); h.setBlock(TARGET.east(), WirelessEnergyTestMachine.BLOCK);
        h.runAfterDelay(35, () -> {
            var endpoints = WirelessEnergyEndpoints.get(level.getServer()); var seed = h.absolutePos(TARGET);
            var other = WirelessFrequencyManager.get().createFrequency(f.player, "Other", 0xffffff, FrequencySecurityLevel.PRIVATE, "");
            f.controller.setFrequency(other.getId());
            h.runAfterDelay(5, () -> {
                aim(f.player, seed.east());
                h.assertTrue(endpoints.setBound(f.player, other.getId(), level, seed.east(), Direction.NORTH, true).equals("connected"), "foreign binding fixture");
                f.controller.setFrequency(f.frequency);
                h.runAfterDelay(5, () -> {
                    aim(f.player, seed);
                    bulk(h, f, f.player, seed, result -> {
                        h.assertTrue(result.changed() == 1 && result.skippedReasons().equals(Map.of("other_frequency", 1))
                                && endpoints.isBound(level, seed.east(), other.getId()), "foreign binding overwritten");
                        useBulk(h, f, seed, () -> {
                            assertFeedback(h, f.player, "bulk_disconnected", net.minecraft.ChatFormatting.YELLOW,
                                    "ae2lt.wireless_energy.bulk_reason.other_frequency");
                            useBulk(h, f, seed, () -> {
                                assertFeedback(h, f.player, "bulk_connected", net.minecraft.ChatFormatting.YELLOW,
                                        "ae2lt.wireless_energy.bulk_reason.other_frequency");
                                aim(f.player, seed.north(20));
                                bulk(h, f, f.player, seed, denied -> {
                                    h.assertTrue(denied.error().equals("no_permission") && endpoints.isBound(level, seed, f.frequency), "remote seed accepted");
                                    h.succeed();
                                });
                            });
                        });
                    });
                });
            });
        });
    }
    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 110)
    public static void jadeShowsMeAndFeConnectionsIndependentlyWithoutEnergyIo(GameTestHelper h) {
        var f = fixture(h);
        h.setBlock(TARGET, AEBlocks.DRIVE.block());
        var target = h.absolutePos(TARGET);
        h.runAfterDelay(35, () -> {
            use(f, target, false);
            h.runAfterDelay(12, () -> {
                var storage = ME_ENERGY.get(h.getLevel().getBlockEntity(target));
                int before = storage.getEnergyStored();
                var data = jadeData(f, target, f.player);
                h.assertTrue(data.contains("AE2LTFrequencyCardWirelessNode")
                                && data.getCompound("AE2LTWirelessEnergy").getString("State").equals("connected"),
                        "ME+FE device did not expose separate connection rows: " + data);
                var rows = new ArrayList<net.minecraft.network.chat.Component>();
                var accessor = jadeAccessor(f, target, f.player, data);
                var tooltip = (snownee.jade.api.ITooltip) java.lang.reflect.Proxy.newProxyInstance(
                        snownee.jade.api.ITooltip.class.getClassLoader(), new Class<?>[] {snownee.jade.api.ITooltip.class},
                        (proxy, method, args) -> { rows.add((net.minecraft.network.chat.Component) args[0]); return null; });
                new com.moakiee.ae2lt.integration.jade.FrequencyCardWirelessNodeJadeProvider().appendTooltip(tooltip, accessor, null);
                new com.moakiee.ae2lt.integration.jade.WirelessEnergyJadeProvider().appendTooltip(tooltip, accessor, null);
                h.assertTrue(rows.size() == 2, "expected exactly one ME row and one FE row");
                h.assertTrue(storage.getEnergyStored() == before, "Jade performed energy IO");
                WirelessEnergyEndpoints.get(h.getLevel().getServer()).setBound(f.player, f.frequency, h.getLevel(), target, Direction.SOUTH, false);
                var disconnected = jadeData(f, target, f.player);
                h.assertTrue(disconnected.contains("AE2LTFrequencyCardWirelessNode")
                                && disconnected.getCompound("AE2LTWirelessEnergy").getString("State").equals("inactive"),
                        "disconnecting FE changed or hid the ME row");
                h.assertTrue(!jadeData(f, target, new MenuPlayer(h.getLevel())).contains("AE2LTWirelessEnergy"),
                        "Jade exposed private FE frequency to an unauthorized viewer");
                h.succeed();
            });
        });
    }
    private static net.minecraft.nbt.CompoundTag jadeData(Fixture f, BlockPos pos, MenuPlayer viewer) {
        var data = new net.minecraft.nbt.CompoundTag();
        var accessor = jadeAccessor(f, pos, viewer, data);
        new com.moakiee.ae2lt.integration.jade.FrequencyCardWirelessNodeJadeProvider().appendServerData(data, accessor);
        new com.moakiee.ae2lt.integration.jade.WirelessEnergyJadeProvider().appendServerData(data, accessor);
        return data;
    }
    private static snownee.jade.api.BlockAccessor jadeAccessor(Fixture f, BlockPos pos, MenuPlayer viewer, net.minecraft.nbt.CompoundTag data) {
        return (snownee.jade.api.BlockAccessor) java.lang.reflect.Proxy.newProxyInstance(
                snownee.jade.api.BlockAccessor.class.getClassLoader(), new Class<?>[] {snownee.jade.api.BlockAccessor.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getLevel" -> f.player.serverLevel();
                    case "getPlayer" -> viewer;
                    case "getPosition" -> pos;
                    case "getBlockEntity" -> f.player.serverLevel().getBlockEntity(pos);
                    case "getSide" -> Direction.NORTH;
                    case "getHitResult" -> new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false);
                    case "getServerData" -> data;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 110)
    public static void meDeviceReceivesFeThroughAnotherFaceAndBulkLeavesItsLinkAlone(GameTestHelper h) {
        var f = fixture(h); h.setBlock(TARGET, AEBlocks.DRIVE.block()); var target = h.absolutePos(TARGET);
        var be = h.getLevel().getBlockEntity(target);
        h.runAfterDelay(25, () -> GridHelper.createConnection(f.controller.getMainNode().getNode(), GridHelper.getNodeHost(h.getLevel(), target).getGridNode(Direction.UP)));
        h.runAfterDelay(40, () -> {
            var endpoints = WirelessEnergyEndpoints.get(h.getLevel().getServer()); aim(f.player, target);
            h.assertTrue(endpoints.findInputFace(h.getLevel(), target, Direction.NORTH) == Direction.SOUTH, "six-face fallback missed ME+FE machine");
            use(f, target, false);
            h.assertTrue(endpoints.isBound(h.getLevel(), target, f.frequency), "normal card did not bind ME+FE target");
            var node = GridHelper.getNodeHost(h.getLevel(), target).getGridNode(Direction.UP); var grid = node.getGrid();
            use(f, target, false);
            h.assertTrue(!endpoints.isBound(h.getLevel(), target, f.frequency) && node.getGrid() == grid,
                    "second ordinary click must disable FE without changing the physical ME grid");
            use(f, target, false);
            h.assertTrue(endpoints.isBound(h.getLevel(), target, f.frequency) && node.getGrid() == grid,
                    "third ordinary click must restore FE without changing the physical ME grid");
            useBulk(h, f, target, () -> {
                h.assertTrue(!endpoints.isBound(h.getLevel(), target, f.frequency) && node.getGrid() == grid, "Ctrl changed ME connection");
                useBulk(h, f, target, () -> {
                    h.assertTrue(endpoints.isBound(h.getLevel(), target, f.frequency) && node.getGrid() == grid, "Ctrl failed to restore FE alone");
                    grid.getStorageService().getInventory().insert(AppFluxBridge.FE_KEY, 50000, Actionable.MODULATE, IActionSource.empty());
                });
            });
        });
        h.runAfterDelay(65, () -> { h.assertTrue(ME_ENERGY.get(be).getEnergyStored() > 0, "same-grid ME machine received no FE"); h.succeed(); });
    }
    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 240)
    public static void bulkReportsAllFailureReasonsAndNeverShowsFalseSuccess(GameTestHelper h) {
        var f = fixture(h); var level = h.getLevel(); var seed = h.absolutePos(TARGET);
        for (int i = 0; i < 3; i++) h.setBlock(TARGET.east(i), WirelessEnergyTestMachine.BLOCK);
        WirelessEnergyTestMachine unsupported = h.getBlockEntity(TARGET.east(2)); unsupported.inputFace = null;
        h.runAfterDelay(35, () -> {
            f.controller.setFrequency(-1);
            h.runAfterDelay(5, () -> {
                var endpoints = WirelessEnergyEndpoints.get(level.getServer());
                bulk(h, f, f.player, seed, result -> {
                    h.assertTrue(result.changed() == 0 && result.skipped() == 3
                            && result.skippedReasons().equals(Map.of("no_controller", 2, "unsupported", 1)),
                            "all-failed reasons/counts lost: " + result);
                    useBulk(h, f, seed, () -> {
                        assertFeedback(h, f.player, "bulk_connected", net.minecraft.ChatFormatting.RED,
                                "ae2lt.wireless_energy.bulk_reason.no_controller", "ae2lt.wireless_energy.bulk_reason.unsupported");
                        h.assertTrue(endpoints.inspect(f.frequency, f.player.getUUID()).isEmpty(), "failed group created endpoints");
                        h.succeed();
                    });
                });
            });
        });
    }
    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 240)
    public static void bulkBindsAllDevicesAndRetainsPerDeviceOwnership(GameTestHelper h) {
        var f = fixture(h); var level = h.getLevel(); var seed = h.absolutePos(TARGET);
        h.setBlock(TARGET, WirelessEnergyTestMachine.BLOCK); h.setBlock(TARGET.east(), WirelessEnergyTestMachine.BLOCK);
        h.runAfterDelay(35, () -> {
            useBulk(h, f, seed, () -> {
                assertFeedback(h, f.player, "bulk_connected", net.minecraft.ChatFormatting.GREEN);
                var endpoints = WirelessEnergyEndpoints.get(level.getServer());
                h.assertTrue(endpoints.isBound(level, seed, f.frequency) && endpoints.isBound(level, seed.east(), f.frequency),
                        "bulk binding skipped a supported endpoint");
                var member = new MenuPlayer(level); aim(member, seed);
                WirelessFrequencyManager.get().getFrequency(f.frequency).enrollAsUser(member);
                bulk(h, f, member, seed, result -> {
                    h.assertTrue(result.changed() == 0 && result.unchanged() == 0
                            && result.skippedReasons().equals(Map.of("no_permission", 2)) && endpoints.isBound(level, seed, f.frequency),
                            "member disconnected owner's endpoint or lost permission reason: " + result);
                    useBulk(h, f, seed, () -> {
                        assertFeedback(h, f.player, "bulk_disconnected", net.minecraft.ChatFormatting.GREEN);
                        h.succeed();
                    });
                });
            });
        });
    }
    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 240)
    public static void interfacesAndProvidersCannotReceiveManuallyOrAutomatically(GameTestHelper h) {
        var f = fixture(h);
        var blocks = List.of(AEBlocks.INTERFACE.block(), AEBlocks.PATTERN_PROVIDER.block(),
                ModBlocks.OVERLOADED_INTERFACE.get(), ModBlocks.OVERLOADED_PATTERN_PROVIDER.get());
        for (int i = 0; i < blocks.size(); i++) h.setBlock(TARGET.east(i), blocks.get(i));
        h.runAfterDelay(35, () -> {
            var endpoints = WirelessEnergyEndpoints.get(h.getLevel().getServer());
            for (int i = 0; i < blocks.size(); i++) {
                var pos = h.absolutePos(TARGET.east(i)); aim(f.player, pos);
                h.assertTrue(h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, pos, Direction.SOUTH) != null, "exclusion fixture needs real FE capability");
                h.assertTrue(endpoints.findInputFace(h.getLevel(), pos, Direction.SOUTH) == null, "interface/provider exposed wireless input");
                WirelessEnergyAdmissions.get(h.getLevel().getServer()).queue(f.player, f.frequency, h.getLevel(), pos, null);
                h.assertTrue(WirelessEnergyAdmissions.get(h.getLevel().getServer()).inspect(h.getLevel(), pos, f.player.getUUID()) == null, "automatic admission bypassed exclusion");
                h.assertTrue(endpoints.setBound(f.player, f.frequency, h.getLevel(), pos, Direction.SOUTH, true).equals("unsupported"), "manual exclusion failed");
            }
            h.succeed();
        });
    }
    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty")
    public static void sidedInterfacesAndProvidersRejectFeOnTheirOwnFace(GameTestHelper h) {
        var p = new MenuPlayer(h.getLevel());
        var parts = List.of(appeng.core.definitions.AEParts.INTERFACE.asItem(), appeng.core.definitions.AEParts.PATTERN_PROVIDER.asItem());
        var endpoints = WirelessEnergyEndpoints.get(h.getLevel().getServer());
        for (int i = 0; i < parts.size(); i++) {
            var pos = h.absolutePos(TARGET.east(i * 2));
            appeng.api.parts.PartHelper.setPart(h.getLevel(), pos, null, p, appeng.core.definitions.AEParts.GLASS_CABLE.item(appeng.api.util.AEColor.TRANSPARENT));
            appeng.api.parts.PartHelper.setPart(h.getLevel(), pos, Direction.SOUTH, p, parts.get(i));
            h.assertTrue(h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, pos, Direction.SOUTH) != null, "part fixture lacks FE capability");
            h.assertTrue(WirelessEnergyEndpoints.energyInput(h.getLevel(), pos, Direction.SOUTH) == null, "excluded part accepted FE");
            h.assertTrue(endpoints.findInputFace(h.getLevel(), pos, Direction.NORTH) == null, "six-face fallback bypassed excluded part");
        }
        h.succeed();
    }
    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 240)
    public static void bulkStopsAtUnloadedChunksWithoutCreatingTickets(GameTestHelper h) {
        var f = fixture(h); var level = h.getLevel();
        var remote = h.absolutePos(TARGET).offset(100000, 0, 100000);
        var seed = new BlockPos((remote.getX() & ~15) + 15, remote.getY(), (remote.getZ() & ~15) + 8);
        h.runAfterDelay(35, () -> {
            level.getChunk(seed.getX() >> 4, seed.getZ() >> 4);
            level.setBlock(seed, WirelessEnergyTestMachine.BLOCK.defaultBlockState(), 18);
            aim(f.player, seed); var next = seed.east();
            h.assertTrue(level.getChunkSource().getChunkNow(next.getX() >> 4, next.getZ() >> 4) == null, "unloaded fixture unexpectedly loaded");
            bulk(h, f, f.player, seed, result -> {
                h.assertTrue(result.changed() == 1 && result.unloaded(), "loaded boundary member not processed: " + result);
                h.assertTrue(level.getChunkSource().getChunkNow(next.getX() >> 4, next.getZ() >> 4) == null, "bulk loaded neighbouring chunk");
                level.setBlock(seed, Blocks.AIR.defaultBlockState(), 18); h.succeed();
            });
        });
    }
    private static Fixture fixture(GameTestHelper h) {
        h.setBlock(C, ModBlocks.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER.get());
        h.setBlock(C.east(), AEBlocks.CREATIVE_ENERGY_CELL.block()); h.setBlock(C.west(), AEBlocks.DRIVE.block());
        DriveBlockEntity drive = h.getBlockEntity(C.west());
        drive.getInternalInventory().setItemDirect(0, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("appflux:fe_1k_cell"))));
        AdvancedWirelessOverloadedControllerBlockEntity controller = h.getBlockEntity(C);
        var p = new MenuPlayer(h.getLevel()); aim(p, h.absolutePos(TARGET));
        var f = WirelessFrequencyManager.get().createFrequency(p, "Card interaction", 0xffffff, FrequencySecurityLevel.PRIVATE, "");
        controller.setFrequency(f.getId());
        var card = new ItemStack(ModItems.OVERLOADED_FREQUENCY_CARD.get());
        OverloadedFrequencyCardItem.bindFrequency(card, f.getId(), h.getLevel().dimension(), h.absolutePos(C), p.getUUID());
        p.setItemInHand(InteractionHand.MAIN_HAND, card);
        return new Fixture(controller, p, f.getId());
    }
    private static void aim(MenuPlayer p, BlockPos target) { p.setPos(target.getX() + .5, target.getY() - 1, target.getZ() - 2); }
    private static void use(Fixture f, BlockPos pos, boolean bulk) {
        FrequencyCardUsePacket.handle(new FrequencyCardUsePacket(InteractionHand.MAIN_HAND, pos, Direction.NORTH,
                pos.getX() + .5, pos.getY() + .5, pos.getZ(), false, bulk), context(f.player));
    }
    private static void bulk(GameTestHelper h, Fixture f, net.minecraft.server.level.ServerPlayer player,
                             BlockPos pos, Consumer<WirelessEnergyEndpoints.BulkResult> completed) {
        var result = new java.util.concurrent.atomic.AtomicReference<WirelessEnergyEndpoints.BulkResult>();
        var queued = WirelessEnergyEndpoints.get(h.getLevel().getServer()).toggleConnected(
                player, f.frequency, h.getLevel(), pos, Direction.NORTH, result::set);
        if (!queued.error().equals("bulk_queued")) { completed.accept(queued); return; }
        awaitBulk(h, () -> result.get() != null, () -> completed.accept(result.get()));
    }
    private static void useBulk(GameTestHelper h, Fixture f, BlockPos pos, Runnable completed) {
        use(f, pos, true);
        h.assertTrue(findTranslation(f.player.feedback, "ae2lt.wireless_energy.bulk_queued") != null, "bulk was not queued");
        awaitBulk(h, () -> findTranslation(f.player.feedback, "ae2lt.wireless_energy.bulk_queued") == null, completed);
    }
    private static void awaitBulk(GameTestHelper h, java.util.function.BooleanSupplier done, Runnable completed) {
        h.runAfterDelay(1, () -> {
            if (done.getAsBoolean()) completed.run();
            else awaitBulk(h, done, completed);
        });
    }
    private record Fixture(AdvancedWirelessOverloadedControllerBlockEntity controller, MenuPlayer player, int frequency) {}
    private static void assertFeedback(GameTestHelper h, MenuPlayer player, String outcome,
            net.minecraft.ChatFormatting color, String... reasons) {
        var summary = findTranslation(player.feedback, "ae2lt.wireless_energy." + outcome);
        h.assertTrue(summary != null && net.minecraft.network.chat.TextColor.fromLegacyFormat(color).equals(summary.getStyle().getColor()),
                "wrong result/color: " + player.feedback);
        for (String reason : reasons) h.assertTrue(findTranslation(player.feedback, reason) != null, "missing feedback reason " + reason);
    }
    private static net.minecraft.network.chat.Component findTranslation(net.minecraft.network.chat.Component text, String key) {
        if (text.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translated) {
            if (translated.getKey().equals(key)) return text;
            for (var arg : translated.getArgs()) if (arg instanceof net.minecraft.network.chat.Component nested) {
                var match = findTranslation(nested, key); if (match != null) return match;
            }
        }
        for (var child : text.getSiblings()) { var match = findTranslation(child, key); if (match != null) return match; }
        return null;
    }
    private static final class MenuPlayer extends FakePlayer {
        private int nextId = 30; boolean encodedParent;
        net.minecraft.network.chat.Component feedback = net.minecraft.network.chat.Component.empty();
        MenuPlayer(ServerLevel level) { super(level, new GameProfile(UUID.randomUUID(), "FrequencyCardQA")); }
        @Override public void displayClientMessage(net.minecraft.network.chat.Component message, boolean actionBar) { feedback = message; }
        @Override public OptionalInt openMenu(MenuProvider provider, Consumer<RegistryFriendlyByteBuf> writer) {
            int id = ++nextId; containerMenu = provider.createMenu(id, getInventory(), this);
            var b = new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess());
            try {
                writer.accept(b); b.readBoolean(); b.readBlockPos(); b.readBoolean(); b.readBoolean(); b.readUtf(256);
                b.readInt(); b.readBoolean(); b.readInt(); b.readInt(); b.readBoolean(); b.readBoolean(); encodedParent = b.readBoolean();
            } finally { b.release(); }
            return OptionalInt.of(id);
        }
    }
    private static IPayloadContext context(MenuPlayer player) {
        return (IPayloadContext) java.lang.reflect.Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(), new Class<?>[]{IPayloadContext.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "player" -> player;
                    case "enqueueWork" -> { ((Runnable) args[0]).run(); yield java.util.concurrent.CompletableFuture.completedFuture(null); }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
