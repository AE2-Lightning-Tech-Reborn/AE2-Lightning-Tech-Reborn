package com.moakiee.ae2lt.logic.energy;

import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.networking.security.IActionSource;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.blockentity.misc.InterfaceBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import com.glodblock.github.appflux.common.caps.NetworkFEPower;
import com.glodblock.github.appflux.common.me.energy.EnergyCapCache;
import com.glodblock.github.appflux.common.me.energy.EnergyHandler;
import com.moakiee.ae2lt.blockentity.AdvancedWirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.grid.FrequencySecurityLevel;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.logic.management.*;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2lt_management")
@PrefixGameTestTemplate(false)
public final class NetworkManagementGameTests {
    private static final BlockPos C = new BlockPos(2, 2, 2);
    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 100)
    public static void frequencyLinksUseTheirRegistryAndNeverLoadRemoteChunks(GameTestHelper h) {
        var f = fixture(h);
        h.setBlock(C.south(), com.moakiee.ae2lt.registry.ModBlocks.WIRELESS_RECEIVER.get());
        var receiver = (com.moakiee.ae2lt.blockentity.WirelessReceiverBlockEntity) h.getBlockEntity(C.south());
        receiver.setFrequency(f.host.getFrequencyId());
        var remote = new BlockPos(2_000_000, 80, -2_000_000);
        WirelessFrequencyManager.get().registerDevice(f.host.getFrequencyId(), new WirelessFrequencyManager.DeviceEntry(
                h.getLevel().dimension(), remote, false, false));
        h.runAfterDelay(40, () -> {
            var view = ManagementQuery.initial(ManagementMode.CONNECTIONS);
            var snapshot = ManagementSnapshot.build(f.player, f.host, view);
            h.assertTrue(snapshot.total() == 3 && snapshot.rows().stream().allMatch(r -> r.kind().equals("connection") && !r.group()),
                    "frequency links mixed with ordinary ME devices");
            var unloaded = snapshot.rows().stream().filter(r -> r.target().pos().equals(remote)).findFirst().orElseThrow();
            h.assertTrue(unloaded.state().equals("unloaded") && !unloaded.canConfigure() && !unloaded.canDisconnect(),
                    "unloaded link acquired an unsafe operation");
            h.assertTrue(h.getLevel().getChunkSource().getChunkNow(remote.getX() >> 4, remote.getZ() >> 4) == null,
                    "reading frequency links loaded a remote chunk");
            var filter = new ManagementQuery(5, ManagementMode.CONNECTIONS, false, List.of(), "2000000",
                    List.of(new ManagementQuery.Token("2000000", List.of())), 1, false, 1, 0);
            h.assertTrue(ManagementSnapshot.build(f.player, f.host, filter).total() == 1, "link coordinates search");
            var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try {
                filter.write(buffer);
                h.assertTrue(ManagementQuery.read(buffer).equals(filter), "link mode lost in query codec");
            } finally { buffer.release(); }
            WirelessFrequencyManager.get().unregisterDevice(f.host.getFrequencyId(), h.getLevel().dimension(), remote);
            h.succeed();
        });
    }

    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 120)
    public static void nativeInductionReceiptsAndLocalizedDeviceSearch(GameTestHelper h) { nativeOutput(h, false); }
    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 120)
    public static void fluxAccessorReceiptsAreIncludedExactlyOnce(GameTestHelper h) { nativeOutput(h, true); }

    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 100)
    public static void expandingGroupsRetainsOtherTypesAndPagesChildrenInPlace(GameTestHelper h) {
        var f = fixture(h);
        h.setBlock(C.south(), AEBlocks.INTERFACE.block());
        h.setBlock(C.south(2), AEBlocks.INTERFACE.block());
        ((InterfaceBlockEntity) h.getBlockEntity(C.south())).setName("Tree Child One");
        h.runAfterDelay(40, () -> {
            var closed = query(false, true, List.of());
            var baseline = ManagementSnapshot.build(f.player, f.host, closed);
            h.assertTrue(baseline.total() == 4 && baseline.devices() == 5, "four types with five devices expected");
            var open = new ManagementQuery(2, false, true, List.of("ae2:interface"), "", List.of(), 2, false, 1, 0);
            var first = ManagementSnapshot.build(f.player, f.host, open);
            h.assertTrue(first.total() == 6 && first.devices() == baseline.devices(), "expansion filtered other types");
            var all = new java.util.LinkedHashMap<UUID, ManagementRow>();
            for (int offset = 0; offset < first.total(); offset += ManagementQuery.PAGE_SIZE) {
                var page = ManagementSnapshot.build(f.player, f.host, open.atOffset(offset));
                h.assertTrue(page.rows().size() <= ManagementQuery.PAGE_SIZE, "unbounded tree packet");
                for (var row : page.rows()) all.put(row.id(), row);
            }
            var rows = List.copyOf(all.values());
            h.assertTrue(rows.stream().filter(ManagementRow::group).map(ManagementRow::id).toList()
                    .equals(baseline.rows().stream().map(ManagementRow::id).toList()), "group headers disappeared or reordered");
            int header = java.util.stream.IntStream.range(0, rows.size()).filter(i -> rows.get(i).group()
                    && rows.get(i).target().type().equals("ae2:interface")).findFirst().orElseThrow();
            h.assertTrue(rows.get(header).state().equals("expanded") && rows.get(header).count() == 2
                    && !rows.get(header + 1).group() && !rows.get(header + 2).group(), "children are not below their header");
            var two = new ManagementQuery(3, false, true, List.of("ae2:interface", "ae2:drive"), "", List.of(), 2, false, 1, 0);
            h.assertTrue(ManagementSnapshot.build(f.player, f.host, two).total() == 7, "only one type can stay expanded");
            var collapse = new ManagementQuery(4, false, true, List.of("ae2:drive"), "", List.of(), 2, false, 1, 0);
            var collapsed = ManagementSnapshot.build(f.player, f.host, collapse);
            h.assertTrue(collapsed.total() == 5 && collapsed.rows().stream().filter(ManagementRow::group).count() == 4
                    && collapsed.rows().stream().anyMatch(r -> !r.group() && r.target().type().equals("ae2:drive")), "collapse affected another group");
            var searched = new ManagementQuery(5, false, true, open.expandedTypes(), "Tree Child One",
                    List.of(new ManagementQuery.Token("Tree Child One", List.of())), 2, false, 1, 0);
            var match = ManagementSnapshot.build(f.player, f.host, searched);
            h.assertTrue(match.total() == 2 && match.devices() == 1 && match.rows().getFirst().count() == 1, "search lost group/child relationship");
            var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try {
                two.write(buffer); first.write(buffer);
                h.assertTrue(ManagementQuery.read(buffer).equals(two), "expanded view codec lost state");
                var decoded = ManagementSnapshot.read(buffer);
                h.assertTrue(decoded.total() == first.total() && decoded.rows().stream().map(ManagementRow::id).toList()
                        .equals(first.rows().stream().map(ManagementRow::id).toList()), "initial tree snapshot codec lost rows");
            } finally { buffer.release(); }
            h.succeed();
        });
    }

    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 120)
    public static void partAccessorRetainsItsFaceAndActualReceipts(GameTestHelper h) {
        var f = fixture(h);
        var cable = h.absolutePos(C.south());
        appeng.api.parts.PartHelper.setPart(h.getLevel(), cable, null, f.player,
                appeng.core.definitions.AEParts.GLASS_CABLE.item(appeng.api.util.AEColor.TRANSPARENT));
        var item = (appeng.api.parts.IPartItem<?>) BuiltInRegistries.ITEM.get(ResourceLocation.parse("appflux:part_flux_accessor"));
        var part = appeng.api.parts.PartHelper.setPart(h.getLevel(), cable, Direction.SOUTH, f.player, item);
        h.setBlock(C.south(2), Blocks.BARREL);
        h.runAfterDelay(35, () -> {
            h.assertTrue(part.getGridNode().isActive(), "part accessor not active");
            f.host.getMainNode().getGrid().getStorageService().getInventory().insert(AppFluxBridge.FE_KEY, 18_000,
                    Actionable.MODULATE, IActionSource.empty());
        });
        h.runAfterDelay(80, () -> {
            var snapshot = ManagementSnapshot.build(f.player, f.host, query(true, false, List.of()));
            h.assertTrue(snapshot.total() == 1, "part output missing or duplicated");
            var row = snapshot.rows().getFirst();
            h.assertTrue(row.kind().equals("accessor") && row.source().face() == Direction.SOUTH.ordinal()
                    && row.source().type().equals("appflux:part_flux_accessor"), "part identity or face lost");
            h.assertTrue(Math.abs(row.rates()[4] * 1200 - 18_000) < .01, "part receipt mismatch");
            h.succeed();
        });
    }

    private static void nativeOutput(GameTestHelper h, boolean accessor) {
        var f = fixture(h);
        h.setBlock(C.south(), accessor ? BuiltInRegistries.BLOCK.get(ResourceLocation.parse("appflux:flux_accessor")) : AEBlocks.INTERFACE.block());
        h.setBlock(C.south(2), Blocks.BARREL);
        AENetworkedBlockEntity output = h.getBlockEntity(C.south());
        if (!accessor) ((InterfaceBlockEntity) output).getInterfaceLogic().getUpgrades().setItemDirect(0,
                new ItemStack(AppFluxBridge.getInductionCard()));
        var nameTag = new net.minecraft.nbt.CompoundTag();
        nameTag.putString("CustomName", Component.Serializer.toJson(Component.literal("Factory Pump 42"), h.getLevel().registryAccess()));
        h.getBlockEntity(C.south(2)).loadWithComponents(nameTag, h.getLevel().registryAccess());
        h.runAfterDelay(35, () -> {
            h.assertTrue(output.getMainNode().isActive(), "native output not active");
            h.assertTrue(f.host.getMainNode().getGrid().getStorageService().getInventory()
                    .insert(AppFluxBridge.FE_KEY, 50_000, Actionable.MODULATE, IActionSource.empty()) == 50_000, "FE source rejected");
        });
        h.runAfterDelay(80, () -> {
            var target = h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, h.absolutePos(C.south(2)), Direction.NORTH);
            h.assertTrue(target != null && target.getEnergyStored() == 50_000, "native path did not deliver exact amount");
            var snapshot = ManagementSnapshot.build(f.player, f.host, query(true, false, List.of()));
            h.assertTrue(snapshot.total() == 1, "duplicate native output path: " + snapshot.total());
            var row = snapshot.rows().getFirst();
            h.assertTrue(row.kind().equals(accessor ? "accessor" : "induction"), "wrong native source kind " + row.kind());
            h.assertTrue(row.target().name().equals("Factory Pump 42"), "custom machine name missing");
            h.assertTrue(Math.abs(row.rates()[4] * 1200 - 50_000) < .01, "receipt history duplicated or missed native FE");
            var match = ManagementSnapshot.build(f.player, f.host, query(false, true,
                    List.of(new ManagementQuery.Token("接口", List.of(accessor ? "appflux:flux_accessor" : "ae2:interface")))));
            h.assertTrue(match.devices() == 1 && match.rows().getFirst().group(), "localized type grouping/search failed");
            var search = ManagementSnapshot.build(f.player, f.host, query(true, false,
                    List.of(new ManagementQuery.Token("pump", List.of()), new ManagementQuery.Token("42", List.of()))));
            h.assertTrue(search.total() == 1, "multi-token custom name search failed");
            h.succeed();
        });
    }

    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 120)
    public static void passiveAccessorCountsOnlyModulatedExtraction(GameTestHelper h) {
        var f = fixture(h);
        h.setBlock(C.south(), BuiltInRegistries.BLOCK.get(ResourceLocation.parse("appflux:flux_accessor")));
        AENetworkedBlockEntity output = h.getBlockEntity(C.south());
        h.runAfterDelay(35, () -> {
            var storage = output.getMainNode().getGrid().getStorageService();
            storage.getInventory().insert(AppFluxBridge.FE_KEY, 50_000, Actionable.MODULATE, IActionSource.empty());
            var capability = new NetworkFEPower(storage, IActionSource.ofMachine(output));
            h.assertTrue(capability.extractEnergy(9000, true) == 9000, "simulate failed");
            h.assertTrue(capability.extractEnergy(3000, false) == 3000, "real extraction failed");
        });
        h.runAfterDelay(40, () -> {
            var snapshot = ManagementSnapshot.build(f.player, f.host, query(true, false, List.of()));
            h.assertTrue(snapshot.total() == 1 && snapshot.rows().getFirst().kind().equals("accessor_pull"), "passive accessor missing");
            h.assertTrue(Math.abs(snapshot.totals()[4] * 1200 - 3000) < .01, "simulate counted or receipt counted twice");
            h.succeed();
        });
    }

    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 120)
    public static void wirelessEndpointDoesNotAlsoBecomeLtOutput(GameTestHelper h) {
        var f = fixture(h);
        h.setBlock(C.south(3), Blocks.BARREL);
        h.runAfterDelay(35, () -> {
            var target = h.absolutePos(C.south(3));
            f.player.setPos(target.getX() + .5, target.getY(), target.getZ() - 2);
            h.assertTrue(WirelessEnergyEndpoints.get(f.player.server).setBound(f.player, f.frequency, h.getLevel(), target, Direction.NORTH, true).equals("connected"), "bind failed");
            f.host.getMainNode().getGrid().getStorageService().getInventory().insert(AppFluxBridge.FE_KEY, 7000 + WirelessEnergySettings.reserveFe.get(), Actionable.MODULATE, IActionSource.empty());
        });
        h.runAfterDelay(80, () -> {
            var snapshot = ManagementSnapshot.build(f.player, f.host, query(true, false, List.of()));
            h.assertTrue(snapshot.total() == 1 && snapshot.rows().getFirst().kind().equals("wireless"), "wireless output counted twice");
            h.assertTrue(Math.abs(snapshot.totals()[4] * 1200 - 7000) < .01, "wireless receipt mismatch");
            h.succeed();
        });
    }

    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 140)
    public static void nodeOwnedHistoryFollowsMergeAndSplitWithoutDuplication(GameTestHelper h) {
        var f = fixture(h);
        var farPos = C.east(5);
        h.setBlock(farPos, AEBlocks.INTERFACE.block());
        h.setBlock(farPos.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        InterfaceBlockEntity far = h.getBlockEntity(farPos);
        appeng.api.networking.IGridConnection[] link = new appeng.api.networking.IGridConnection[1];
        h.runAfterDelay(35, () -> {
            var node = far.getActionableNode();
            PowerTelemetry.record(node, PowerTelemetry.Kind.INDUCTION,
                    new PowerTelemetry.Target(h.getLevel().dimension().location().toString(), h.absolutePos(farPos.south()), Direction.NORTH.ordinal()), null, 1200);
            h.assertTrue(ManagementSnapshot.build(f.player, f.host, query(true, false, List.of())).total() == 0, "separate grid leaked telemetry");
            link[0] = GridHelper.createConnection(f.host.getActionableNode(), node);
        });
        h.runAfterDelay(55, () -> {
            var snapshot = ManagementSnapshot.build(f.player, f.host, query(true, false, List.of()));
            h.assertTrue(snapshot.total() == 1 && snapshot.totals()[4] == 1, "merge lost or duplicated node history");
            link[0].destroy();
        });
        h.runAfterDelay(80, () -> {
            var snapshot = ManagementSnapshot.build(f.player, f.host, query(true, false, List.of()));
            h.assertTrue(snapshot.total() == 0, "split retained unrelated grid history");
            h.assertTrue(PowerTelemetry.meters(far.getActionableNode()).size() == 1, "split destroyed source history");
            h.succeed();
        });
    }
    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 100)
    public static void wirelessReceiptsWithAndWithoutEndpointIdsShareOnlyTheBoundTarget(GameTestHelper h) {
        var f = fixture(h);
        h.setBlock(C.south(3), Blocks.BARREL);
        var target = h.absolutePos(C.south(3));
        h.runAfterDelay(35, () -> {
            f.player.setPos(target.getX() + .5, target.getY(), target.getZ() - 2);
            var endpoints = WirelessEnergyEndpoints.get(f.player.server);
            h.assertTrue(endpoints.setBound(f.player, f.frequency, h.getLevel(), target, Direction.NORTH, true)
                    .equals("connected"), "bind failed");
            var endpoint = endpoints.inspectTarget(h.getLevel(), target, f.player.getUUID());
            var location = new PowerTelemetry.Target(h.getLevel().dimension().location().toString(), target, Direction.NORTH.ordinal());
            var node = f.host.getActionableNode();
            PowerTelemetry.record(node, PowerTelemetry.Kind.WIRELESS, location, endpoint.id(), 2000);
            PowerTelemetry.record(node, PowerTelemetry.Kind.WIRELESS, location, null, 1000);
            PowerTelemetry.record(node, PowerTelemetry.Kind.WIRELESS,
                    new PowerTelemetry.Target(location.dimension(), target.east(), location.face()), null, 4000);
        });
        h.runAfterDelay(40, () -> {
            var snapshot = ManagementSnapshot.build(f.player, f.host, query(true, false, List.of()));
            h.assertTrue(snapshot.total() == 2, "bound and unbound wireless paths were merged or duplicated");
            var bound = snapshot.rows().stream().filter(row -> row.target().pos().equals(target)).findFirst().orElseThrow();
            h.assertTrue(bound.canDisconnect() && bound.source() != null && bound.rates()[4] == 2.5,
                    "bound receipts lost identity, source, permissions or exact aggregate");
            h.assertTrue(Math.abs(snapshot.totals()[4] * 1200 - 7000) < .01, "wireless totals changed");
            var repeated = ManagementSnapshot.build(f.player, f.host, query(true, false, List.of()));
            h.assertTrue(java.util.Arrays.equals(snapshot.totals(), repeated.totals()), "aggregation mutated shared meter snapshots");
            h.succeed();
        });
    }

    private static ManagementQuery query(boolean power, boolean group, List<ManagementQuery.Token> tokens) {
        return new ManagementQuery(1, power, group, List.of(), "", tokens, power ? 4 : 2, power, 4, 0);
    }
    private static Fixture fixture(GameTestHelper h) {
        h.setBlock(C, ModBlocks.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER.get());
        h.setBlock(C.east(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        h.setBlock(C.west(), AEBlocks.DRIVE.block());
        DriveBlockEntity drive = h.getBlockEntity(C.west());
        drive.getInternalInventory().setItemDirect(0, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("appflux:fe_1k_cell"))));
        AdvancedWirelessOverloadedControllerBlockEntity host = h.getBlockEntity(C);
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "ManagementOwner"));
        var pos = h.absolutePos(C); player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() - 2);
        var f = WirelessFrequencyManager.get().createFrequency(player, "ManagementTest", 0xffffff, FrequencySecurityLevel.PRIVATE, "");
        host.setFrequency(f.getId());
        return new Fixture(host, player, f.getId());
    }
    private record Fixture(AdvancedWirelessOverloadedControllerBlockEntity host, ServerPlayer player, int frequency) {}
}
