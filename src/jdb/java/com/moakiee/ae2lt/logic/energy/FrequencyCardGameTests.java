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
    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 100)
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
            use(f, seed, true);
            var views = endpoints.inspect(f.frequency, f.player.getUUID());
            h.assertTrue(views.size() == 10, "bulk missed remote member or included diagonal/different block: " + views.size());
            for (int i = 0; i < 10; i++) {
                final var pos = h.absolutePos(TARGET.east(i)); var direction = i % 2 == 0 ? Direction.SOUTH : Direction.UP;
                h.assertTrue(views.stream().anyMatch(v -> v.location().pos().equals(pos) && v.face() == direction), "per-machine input face");
                WirelessEnergyTestMachine m = h.getBlockEntity(TARGET.east(i));
                h.assertTrue(m.simulations == 0 && m.receiveCalls == 0, "manual cluster performed an FE probe");
            }
            h.assertTrue(f.controller.getMainNode().getGrid() == beforeGrid, "bulk modified ME topology");
            use(f, seed, true);
            h.assertTrue(endpoints.inspect(f.frequency, f.player.getUUID()).isEmpty(), "bulk disconnect left a member");
            h.succeed();
        });
    }
    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 100)
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
                    aim(f.player, seed); var result = endpoints.toggleConnected(f.player, f.frequency, level, seed, Direction.NORTH);
                    h.assertTrue(result.changed() == 1 && result.skipped() == 1 && endpoints.isBound(level, seed.east(), other.getId()), "foreign binding overwritten");
                    aim(f.player, seed.north(20)); result = endpoints.toggleConnected(f.player, f.frequency, level, seed, Direction.NORTH);
                    h.assertTrue(result.error().equals("no_permission") && endpoints.isBound(level, seed, f.frequency), "remote seed accepted");
                    h.succeed();
                });
            });
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
            use(f, target, true);
            h.assertTrue(!endpoints.isBound(h.getLevel(), target, f.frequency) && node.getGrid() == grid, "Ctrl changed ME connection");
            use(f, target, true);
            h.assertTrue(endpoints.isBound(h.getLevel(), target, f.frequency) && node.getGrid() == grid, "Ctrl failed to restore FE alone");
            grid.getStorageService().getInventory().insert(AppFluxBridge.FE_KEY, 50000, Actionable.MODULATE, IActionSource.empty());
        });
        h.runAfterDelay(65, () -> { h.assertTrue(ME_ENERGY.get(be).getEnergyStored() > 0, "same-grid ME machine received no FE"); h.succeed(); });
    }
    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 100)
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
    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 100)
    public static void bulkStopsAtUnloadedChunksWithoutCreatingTickets(GameTestHelper h) {
        var f = fixture(h); var level = h.getLevel();
        var remote = h.absolutePos(TARGET).offset(100000, 0, 100000);
        var seed = new BlockPos((remote.getX() & ~15) + 15, remote.getY(), (remote.getZ() & ~15) + 8);
        h.runAfterDelay(35, () -> {
            level.getChunk(seed.getX() >> 4, seed.getZ() >> 4);
            level.setBlock(seed, WirelessEnergyTestMachine.BLOCK.defaultBlockState(), 18);
            aim(f.player, seed); var next = seed.east();
            h.assertTrue(level.getChunkSource().getChunkNow(next.getX() >> 4, next.getZ() >> 4) == null, "unloaded fixture unexpectedly loaded");
            var result = WirelessEnergyEndpoints.get(level.getServer()).toggleConnected(f.player, f.frequency, level, seed, Direction.NORTH);
            h.assertTrue(result.changed() == 1 && result.unloaded(), "loaded boundary member not processed: " + result);
            h.assertTrue(level.getChunkSource().getChunkNow(next.getX() >> 4, next.getZ() >> 4) == null, "bulk loaded neighbouring chunk");
            level.setBlock(seed, Blocks.AIR.defaultBlockState(), 18); h.succeed();
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
    private record Fixture(AdvancedWirelessOverloadedControllerBlockEntity controller, MenuPlayer player, int frequency) {}
    private static final class MenuPlayer extends FakePlayer {
        private int nextId = 30; boolean encodedParent;
        MenuPlayer(ServerLevel level) { super(level, new GameProfile(UUID.randomUUID(), "FrequencyCardQA")); }
        @Override public OptionalInt openMenu(MenuProvider provider, Consumer<RegistryFriendlyByteBuf> writer) {
            int id = ++nextId; containerMenu = provider.createMenu(id, getInventory(), this);
            var b = new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess());
            try {
                writer.accept(b); b.readBoolean(); b.readBlockPos(); b.readBoolean(); b.readBoolean(); b.readUtf(256);
                b.readInt(); b.readBoolean(); b.readInt(); b.readInt(); b.readBoolean(); encodedParent = b.readBoolean();
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
