package com.moakiee.ae2lt.logic.management;

import com.moakiee.ae2lt.blockentity.WirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.logic.energy.AppFluxBridge;
import com.moakiee.ae2lt.logic.energy.WirelessEnergyEndpoints;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Nameable;

public record ManagementSnapshot(int total, int devices, int offset, double[] totals, boolean limited, List<ManagementRow> rows) {
    // Fixed-width hexadecimal UUID strings sort by unsigned bits, unlike UUID.compareTo.
    static final Comparator<ManagementRow> ID_ORDER = Comparator.comparing(ManagementRow::id, (left, right) -> {
        int most = Long.compareUnsigned(left.getMostSignificantBits(), right.getMostSignificantBits());
        return most != 0 ? most : Long.compareUnsigned(left.getLeastSignificantBits(), right.getLeastSignificantBits());
    });

    public static ManagementSnapshot build(ServerPlayer player, WirelessOverloadedControllerBlockEntity host, ManagementQuery query) {
        long now = player.server.getTickCount();
        var grid = host.getMainNode().getGrid();
        var devices = grid == null ? List.<NetworkDeviceIndex.Device>of() : grid.getService(NetworkDeviceIndex.class).devices(now);
        var result = switch (query.mode()) {
            case DEVICES -> devices(devices);
            case ENERGY -> power(player, host, devices, now);
            case CONNECTIONS -> connections(player, host, devices);
        };
        result.removeIf(row -> !query.matches(row.target(), row.source(), row.kind()));
        int deviceCount = result.size();
        double[] totals = new double[5];
        for (var row : result) for (int i = 0; i < 5; i++) totals[i] += row.rates()[i];
        Comparator<ManagementRow> comparator = switch (query.sort()) {
            case 1 -> Comparator.comparing(r -> r.target().dimension());
            case 2 -> Comparator.comparing(r -> r.target().type());
            case 3 -> Comparator.comparingDouble(r -> r.target().dimension().equals(player.level().dimension().location().toString())
                    ? r.target().pos().distToCenterSqr(player.position()) : Double.POSITIVE_INFINITY);
            case 4 -> Comparator.comparingDouble(r -> r.rates()[query.window()]);
            default -> Comparator.comparing(r -> (r.target().name().isEmpty() ? r.target().type() : r.target().name()).toLowerCase(Locale.ROOT));
        };
        if (query.descending()) comparator = comparator.reversed();
        result.sort(comparator.thenComparing(ID_ORDER));
        if (!query.energy() && query.grouped()) {
            // Flatten the tree before paging so every header keeps its children together.
            // Group order follows the first matching device in the selected sort order.
            Map<String, List<ManagementRow>> groups = new java.util.LinkedHashMap<>();
            for (var row : result) groups.computeIfAbsent(row.target().type(), ignored -> new ArrayList<>()).add(row);
            var expanded = new java.util.HashSet<>(query.expandedTypes());
            result.clear();
            groups.forEach((type, children) -> {
                boolean open = expanded.contains(type);
                result.add(new ManagementRow(UUID.nameUUIDFromBytes(type.getBytes(StandardCharsets.UTF_8)),
                        new DeviceLocation("", BlockPos.ZERO, -1, type, ""), null, query.connections() ? "connection" : "device", children.size(),
                        open ? "expanded" : "group", true, false, new double[5]));
                if (open) result.addAll(children);
            });
        }
        int offset = Math.min(query.offset(), Math.max(0, result.size() - ManagementQuery.PAGE_SIZE));
        return new ManagementSnapshot(result.size(), deviceCount, offset, totals, query.energy() && PowerTelemetry.limited(now),
                List.copyOf(result.subList(offset, Math.min(result.size(), offset + ManagementQuery.PAGE_SIZE))));
    }

    public void write(FriendlyByteBuf b) {
        b.writeVarInt(total); b.writeVarInt(devices); b.writeVarInt(offset);
        for (double rate : totals) b.writeDouble(rate);
        b.writeBoolean(limited); b.writeVarInt(rows.size());
        for (var row : rows) row.write(b);
    }
    public static ManagementSnapshot read(FriendlyByteBuf b) {
        int total = b.readVarInt(), devices = b.readVarInt(), offset = b.readVarInt();
        double[] totals = new double[5]; for (int i = 0; i < 5; i++) totals[i] = b.readDouble();
        boolean limited = b.readBoolean(); int size = ManagementQuery.bounded(b.readVarInt(), ManagementQuery.PAGE_SIZE);
        var rows = new ArrayList<ManagementRow>(size);
        for (int i = 0; i < size; i++) rows.add(ManagementRow.read(b));
        return new ManagementSnapshot(total, devices, offset, totals, limited, List.copyOf(rows));
    }

    private static ArrayList<ManagementRow> devices(List<NetworkDeviceIndex.Device> devices) {
        var rows = new ArrayList<ManagementRow>(devices.size());
        for (var d : devices) rows.add(new ManagementRow(d.id(), d.location(), null, "device", 1,
                d.active() ? "active" : "offline", false, false, new double[5], RemoteDeviceSession.supports(d.node().getOwner())));
        return rows;
    }

    private static ArrayList<ManagementRow> connections(ServerPlayer player, WirelessOverloadedControllerBlockEntity host,
            List<NetworkDeviceIndex.Device> devices) {
        var inGrid = new HashMap<String, NetworkDeviceIndex.Device>();
        for (var device : devices) if (device.location().face() < 0)
            inGrid.put(device.location().dimension() + ":" + device.location().pos().asLong(), device);
        var rows = new ArrayList<ManagementRow>();
        for (var entry : WirelessFrequencyManager.get(player.server).getDevices(host.getFrequencyId())) {
            String dimension = entry.dimension().location().toString();
            String locationKey = dimension + ":" + entry.pos().asLong();
            var level = player.server.getLevel(entry.dimension());
            var chunk = level == null ? null : level.getChunkSource().getChunkNow(entry.pos().getX() >> 4, entry.pos().getZ() >> 4);
            var be = chunk == null ? null : chunk.getBlockEntity(entry.pos(), net.minecraft.world.level.chunk.LevelChunk.EntityCreationType.CHECK);
            String fallback = entry.isController() ? entry.advanced() ? "ae2lt:advanced_wireless_overloaded_controller"
                    : "ae2lt:wireless_overloaded_controller" : "ae2lt:wireless_receiver";
            String[] description = entry.deviceName().split("\\.", 3);
            if (description.length == 3) {
                var type = ResourceLocation.tryParse(description[1] + ":" + description[2]);
                if (type != null && BuiltInRegistries.ITEM.containsKey(type)) fallback = type.toString();
            }
            String type = be == null ? fallback : BuiltInRegistries.ITEM.getKey(be.getBlockState().getBlock().asItem()).toString();
            String name = be instanceof Nameable named && named.hasCustomName() ? named.getCustomName().getString() : "";
            var device = inGrid.get(locationKey);
            var id = device == null ? UUID.nameUUIDFromBytes(("connection:" + host.getFrequencyId() + ":" + locationKey)
                    .getBytes(StandardCharsets.UTF_8)) : device.id();
            rows.add(new ManagementRow(id, new DeviceLocation(dimension, entry.pos(), -1, type, name), null,
                    "connection", 1, chunk == null ? "unloaded" : "loaded", false, false, new double[5],
                    device != null && RemoteDeviceSession.supports(device.node().getOwner())));
        }
        return rows;
    }

    private static ArrayList<ManagementRow> power(ServerPlayer player, WirelessOverloadedControllerBlockEntity host,
            List<NetworkDeviceIndex.Device> devices, long now) {
        var rows = new ArrayList<ManagementRow>();
        var endpoints = WirelessEnergyEndpoints.get(player.server).inspect(host.getFrequencyId(), player.getUUID());
        Map<PowerTelemetry.Target, UUID> boundLocations = null;
        Map<UUID, double[]> wireless = new HashMap<>();
        Map<UUID, DeviceLocation> sources = new HashMap<>();
        for (var d : devices) {
            boolean recorded = false;
            for (var entry : PowerTelemetry.meters(d.node()).entrySet()) {
                var key = entry.getKey();
                double[] rates = entry.getValue().rates(now);
                UUID endpointId = key.endpoint();
                if (endpointId == null && key.kind() == PowerTelemetry.Kind.WIRELESS) {
                    // Explicit endpoint IDs need no secondary location index.
                    if (boundLocations == null) {
                        boundLocations = new HashMap<>();
                        for (var e : endpoints) boundLocations.put(new PowerTelemetry.Target(
                                e.location().dimension().location().toString(), e.location().pos(), e.face().ordinal()), e.id());
                    }
                    endpointId = boundLocations.get(key.target());
                }
                if (endpointId != null) {
                    var total = wireless.putIfAbsent(endpointId, rates);
                    if (total != null) {
                        // Meter snapshots are shared; only a combined path needs its own array.
                        total = total.clone();
                        for (int i = 0; i < 5; i++) total[i] += rates[i];
                        wireless.put(endpointId, total);
                    }
                    sources.put(endpointId, d.location());
                    continue;
                }
                recorded = true;
                rows.add(new ManagementRow(entry.getValue().id, target(player, key.target(), "minecraft:air"), d.location(),
                        key.kind().name().toLowerCase(Locale.ROOT), 1, d.active() ? "active" : "offline", false, false, rates));
            }
            // Keep installed but idle outputs visible even before the first successful receipt.
            if (!recorded) {
                String kind = idleKind(d);
                if (kind != null) rows.add(new ManagementRow(d.id(), d.location(), d.location(), kind, 1,
                        d.active() ? "idle" : "offline", false, false, new double[5]));
            }
        }
        var frequency = WirelessFrequencyManager.get(player.server).getFrequency(host.getFrequencyId());
        for (var endpoint : endpoints) {
            var location = new PowerTelemetry.Target(endpoint.location().dimension().location().toString(), endpoint.location().pos(), endpoint.face().ordinal());
            boolean mayDisconnect = !player.isSpectator() && player.mayBuild() && frequency != null
                    && (endpoint.actor().equals(player.getUUID()) || frequency.getPlayerAccess(player).isManager());
            var rates = wireless.get(endpoint.id());
            rows.add(new ManagementRow(endpoint.id(), target(player, location, endpoint.block()), sources.get(endpoint.id()),
                    "wireless", 1, endpoint.state().name().toLowerCase(Locale.ROOT), false, mayDisconnect,
                    rates == null ? new double[5] : rates));
        }
        return rows;
    }

    private static String idleKind(NetworkDeviceIndex.Device device) {
        String type = device.location().type();
        if (type.contains("flux_accessor")) return "accessor";
        if (type.equals("ae2lt:overloaded_power_supply")) return "lt_output";
        if (device.node().getOwner() instanceof appeng.api.upgrades.IUpgradeableObject upgraded) {
            var card = AppFluxBridge.getInductionCard();
            if (card != null && upgraded.getUpgrades().getInstalledUpgrades(card) > 0)
                return type.startsWith("ae2lt:") ? "lt_output" : "induction";
        }
        return null;
    }

    private static DeviceLocation target(ServerPlayer player, PowerTelemetry.Target target, String fallback) {
        var dimension = ResourceLocation.tryParse(target.dimension());
        var level = dimension == null ? null : player.server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        var chunk = level == null ? null : level.getChunkSource().getChunkNow(target.pos().getX() >> 4, target.pos().getZ() >> 4);
        var be = chunk == null ? null : chunk.getBlockEntity(target.pos(), net.minecraft.world.level.chunk.LevelChunk.EntityCreationType.CHECK);
        String type = be == null ? fallback : BuiltInRegistries.ITEM.getKey(be.getBlockState().getBlock().asItem()).toString();
        String name = be instanceof Nameable named && named.hasCustomName() ? named.getCustomName().getString() : "";
        return new DeviceLocation(target.dimension(), target.pos(), target.face(), type, name);
    }
}
