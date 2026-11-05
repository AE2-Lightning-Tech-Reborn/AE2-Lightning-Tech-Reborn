package com.moakiee.ae2lt.debug;

import com.moakiee.ae2lt.blockentity.PigmeeDimensionBlockEntity;
import com.moakiee.ae2lt.dimension.*;
import com.moakiee.ae2lt.grid.WirelessFrequency;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2lt")
@PrefixGameTestTemplate(false)
public final class PigmeeDimensionGameTests {
    private static ServerPlayer owner(GameTestHelper helper) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "PigmeeWorldQA"));
    }
    private static PigmeeChunkGenerator.Palette palette() {
        return new PigmeeChunkGenerator.Palette(PigmeeDimensionMaterials.defaultFrame(), PigmeeDimensionMaterials.defaultCenter());
    }
    private static PigmeeDimensionData.Entry create(GameTestHelper helper, ServerPlayer owner, boolean even) {
        var entry = PigmeeDimensionData.get(helper.getLevel().getServer()).create(owner, "QA " + (even ? "even" : "odd"), even, palette(), false);
        helper.assertTrue(entry != null, "create dimension within owner quota");
        return entry;
    }

    @GameTest(template = "pigmee_station_empty", timeoutTicks = 400)
    public static void actualOddAndEvenTerrainAndHeight(GameTestHelper helper) {
        var player = owner(helper);
        var odd = PigmeeDimensions.getOrCreate(player.server, create(helper, player, false));
        var even = PigmeeDimensions.getOrCreate(player.server, create(helper, player, true));
        for (var level : new ServerLevel[] {odd, even}) {
            helper.assertTrue(level.getMinBuildHeight() == -64 && level.getMaxBuildHeight() == 320, "overworld vertical bounds");
            helper.assertTrue(level.dimensionType().ambientLight() == 0.0F, "visual brightness must not alter server monster pathfinding/spawn rules");
            int stone = 0, dirt = 0;
            for (int y = -64; y <= 20; y++) {
                var state = level.getBlockState(new BlockPos(4, y, 4));
                if (state.is(Blocks.STONE)) stone++;
                if (state.is(Blocks.DIRT)) dirt++;
            }
            helper.assertTrue(stone == 80 && dirt == 4, "exactly 80 stone + 4 dirt beneath the surface");
            helper.assertTrue(level.getHeight(Heightmap.Types.WORLD_SURFACE, 4, 4) == 21, "surface heightmap");
            helper.assertTrue(level.getChunk(0, 0).getAllStarts().isEmpty(), "no generated structures");
            for (var category : MobCategory.values()) {
                helper.assertTrue(level.getChunkSource().getGenerator().getMobsAt(level.getBiome(BlockPos.ZERO),
                        level.structureManager(), category, BlockPos.ZERO).isEmpty(), "no natural spawn entries");
            }
        }
        helper.assertTrue(odd.getBlockState(new BlockPos(0, 20, 0)).equals(PigmeeDimensionMaterials.floor()), "odd interior includes chunk start");
        helper.assertTrue(odd.getBlockState(new BlockPos(15, 20, 7)).equals(palette().frame()), "odd border occupies the one-block gap");
        helper.assertTrue(odd.getBlockState(new BlockPos(7, 20, 7)).equals(palette().center()), "odd single center");
        helper.assertTrue(odd.getBlockState(new BlockPos(8, 20, 7)).equals(PigmeeDimensionMaterials.floor()), "odd center does not widen");
        helper.assertTrue(even.getBlockState(new BlockPos(1, 20, 1)).equals(PigmeeDimensionMaterials.floor()), "even interior keeps all fourteen rows and columns");
        for (int x : new int[] {-1, 0, 15, 16}) {
            helper.assertTrue(even.getBlockState(new BlockPos(x, 20, 7)).equals(palette().frame()), "border fills two-block gap across positive and negative chunk seams");
        }
        for (int x = 7; x <= 8; x++) for (int z = 7; z <= 8; z++) {
            helper.assertTrue(even.getBlockState(new BlockPos(x, 20, z)).equals(palette().center()), "even 2x2 center");
        }
        helper.succeed();
    }

    @GameTest(template = "pigmee_station_empty", timeoutTicks = 400)
    public static void ownerQuotaIsSeparateAndCoOwnershipConsumesQuota(GameTestHelper helper) {
        var owner = owner(helper);
        var guest = owner(helper);
        var third = owner(helper);
        var data = PigmeeDimensionData.get(owner.server);
        var first = create(helper, owner, false);
        var second = create(helper, owner, false);
        first.access().enrollAsUser(guest);
        second.access().enrollAsUser(guest);
        helper.assertTrue(data.countOwned(owner.getUUID()) == 2, "two same-kind worlds allowed");
        helper.assertTrue(data.create(owner, "over quota", true, palette(), false) == null, "third world rejected");
        helper.assertTrue(data.changeMember(owner, first.id(), guest.getUUID(), WirelessFrequency.MEMBERSHIP_TRANSFER_OWNERSHIP), "co-owner promotion accepted");
        helper.assertTrue(data.countOwned(guest.getUUID()) == 1 && data.countOwned(owner.getUUID()) == 2, "both owners count same world");
        create(helper, guest, true);
        helper.assertTrue(!data.changeMember(owner, second.id(), guest.getUUID(), WirelessFrequency.MEMBERSHIP_TRANSFER_OWNERSHIP), "co-owner cannot exceed quota");
        helper.assertTrue(!data.changeMember(third, first.id(), third.getUUID(), WirelessFrequency.MEMBERSHIP_TRANSFER_OWNERSHIP), "stranger cannot self-promote");
        helper.assertTrue(!data.configure(third, first.id(), "stolen", palette(), true), "stranger cannot change materials or privacy");
        helper.assertTrue(!data.changeMember(owner, second.id(), owner.getUUID(), WirelessFrequency.MEMBERSHIP_CANCEL), "last owner cannot leave an orphan");
        var restored = PigmeeDimensionData.load(data.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertTrue(restored.countOwned(guest.getUUID()) == 2 && restored.countOwned(owner.getUUID()) == 2, "ACL and owner quota survive saved data reload");
        helper.succeed();
    }

    @GameTest(template = "pigmee_station_empty", timeoutTicks = 400)
    public static void paletteChangesOnlyNewChunksAndSurvivesReload(GameTestHelper helper) {
        var player = owner(helper);
        var data = PigmeeDimensionData.get(player.server);
        var entry = create(helper, player, false);
        var level = PigmeeDimensions.getOrCreate(player.server, entry);
        var old = new BlockPos(15, 20, 0);
        helper.assertTrue(level.getBlockState(old).equals(palette().frame()), "original palette loaded");
        var replacement = new PigmeeChunkGenerator.Palette(ModBlocks.PIGMEE_BUILDING_BLOCK.get().defaultBlockState(),
                ModBlocks.PIGMEE_FRAMED_BUILDING_PANELS.get(DyeColor.RED).get().defaultBlockState());
        helper.assertTrue(data.configure(player, entry.id(), "custom palette", replacement, false), "owner configures arbitrary building types");
        helper.assertTrue(level.getBlockState(old).equals(palette().frame()), "existing blocks never rewritten");
        helper.assertTrue(level.getBlockState(new BlockPos(175, 20, -160)).equals(replacement.frame()), "new positive/negative chunk uses selected frame in its gap");
        helper.assertTrue(level.getBlockState(new BlockPos(167, 20, -153)).equals(replacement.center()), "new chunk uses selected center");
        var restored = PigmeeDimensionData.load(data.save(new CompoundTag(), level.registryAccess()), level.registryAccess()).find(entry.id());
        helper.assertTrue(restored.palette().equals(replacement) && restored.generated(), "settings and generated identity survive reload");
        helper.assertTrue(PigmeeDimensionMaterials.all().size() == 33, "all 33 Pigmee materials offered");
        helper.assertTrue(!PigmeeDimensionMaterials.accepts(Blocks.DIAMOND_BLOCK), "arbitrary world resources rejected");
        helper.succeed();
    }

    @GameTest(template = "pigmee_station_empty", timeoutTicks = 400)
    public static void customPatternRepeatsPersistsAndSharesOwnerQuota(GameTestHelper helper) {
        var player = owner(helper);
        var data = PigmeeDimensionData.get(player.server);
        var red = ModBlocks.PIGMEE_BUILDING_PANELS.get(DyeColor.RED).get().defaultBlockState();
        var blue = ModBlocks.PIGMEE_BUILDING_PANELS.get(DyeColor.BLUE).get().defaultBlockState();
        var green = ModBlocks.PIGMEE_BUILDING_PANELS.get(DyeColor.LIME).get().defaultBlockState();
        var cells = new java.util.ArrayList<>(java.util.Collections.nCopies(256, PigmeeDimensionMaterials.floor()));
        cells.set(0, red); cells.set(15, blue); cells.set(240, green); cells.set(255, palette().frame());
        cells.set(8 * 16 + 3, palette().center());
        var custom = new PigmeeChunkGenerator.Palette(palette().frame(), palette().center(), cells);
        var entry = data.create(player, "custom", PigmeeDimensionKind.CUSTOM, custom, false);
        helper.assertTrue(entry != null && entry.kind() == PigmeeDimensionKind.CUSTOM, "create custom dimension");
        create(helper, player, false);
        helper.assertTrue(data.countOwned(player.getUUID()) == 2
                && data.create(player, "too many", PigmeeDimensionKind.CUSTOM, custom, false) == null, "custom and preset dimensions share owner quota");
        cells.set(0, green);
        helper.assertTrue(custom.cells().getFirst().equals(red), "pattern retains an immutable copy of submitted cells");
        var level = PigmeeDimensions.getOrCreate(player.server, entry);
        for (int offset : new int[] {0, -16, 16}) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            helper.assertTrue(level.getBlockState(new BlockPos(offset + x, 20, offset + z)).equals(custom.cells().get(z * 16 + x)),
                    "all 256 custom cells repeat at negative and positive chunk coordinates");
        }
        var changed = new PigmeeChunkGenerator.Palette(palette().frame(), palette().center(), cells);
        helper.assertTrue(!data.configure(owner(helper), entry.id(), "intruder", changed, false), "non-owner cannot paint this dimension");
        helper.assertTrue(data.configure(player, entry.id(), "custom changed", changed, false), "owner may change pattern");
        helper.assertTrue(level.getBlockState(new BlockPos(0, 20, 0)).equals(red), "old custom chunks unchanged");
        helper.assertTrue(level.getBlockState(new BlockPos(160, 20, -160)).equals(green), "new chunks use changed cell");
        var restored = PigmeeDimensionData.load(data.save(new CompoundTag(), level.registryAccess()), level.registryAccess()).find(entry.id());
        helper.assertTrue(restored.kind() == PigmeeDimensionKind.CUSTOM && restored.palette().equals(changed), "all cell material IDs survive saved data reload");
        var compact = entry.saveForMenu();
        helper.assertTrue(compact.getByteArray("CellIndices").length == 256 && !compact.contains("Cells")
                && PigmeeDimensionData.Entry.load(compact).palette().equals(changed), "menu lists use 256-byte grids without losing material choices");
        var generator = (PigmeeChunkGenerator) level.getChunkSource().getGenerator();
        var ops = net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE, level.registryAccess());
        var encoded = PigmeeChunkGenerator.CODEC.codec().encodeStart(ops, generator).getOrThrow();
        var decoded = PigmeeChunkGenerator.CODEC.codec().parse(ops, encoded).getOrThrow();
        helper.assertTrue(decoded.palette().equals(changed) && decoded.surfaceAt(-16, -16).equals(green), "worldgen codec preserves the custom grid");
        helper.succeed();
    }

    @GameTest(template = "pigmee_station_empty", timeoutTicks = 100)
    public static void customPatternPayloadIsBoundedAndValidatesEveryCell(GameTestHelper helper) {
        var pattern = PigmeeDimensionPattern.encode(PigmeeDimensionPattern.template(false, palette().frame(), palette().center()));
        var request = new com.moakiee.ae2lt.network.PigmeeDimensionActionPacket(12, 0, 0, "custom", 0, 0, false, "", (byte) 0, pattern);
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            com.moakiee.ae2lt.network.PigmeeDimensionActionPacket.STREAM_CODEC.encode(buffer, request);
            var restored = com.moakiee.ae2lt.network.PigmeeDimensionActionPacket.STREAM_CODEC.decode(buffer);
            helper.assertTrue(java.util.Arrays.equals(pattern, restored.pattern()), "all 256 cells survive the network codec");
            pattern[0] = (byte) 255;
            helper.assertTrue(Byte.toUnsignedInt(request.pattern()[0]) < 33, "packet retains its own grid snapshot");
        } finally { buffer.release(); }
        for (byte[] invalid : new byte[][] {new byte[0], new byte[255], new byte[257], pattern}) {
            boolean rejected = false;
            try { PigmeeDimensionPattern.decode(invalid); } catch (IllegalArgumentException expected) { rejected = true; }
            helper.assertTrue(rejected, "reject malformed length or forbidden material index");
        }
        helper.succeed();
    }

    @GameTest(template = "pigmee_station_empty", timeoutTicks = 400)
    public static void spawnPolicyAllowsSpawnersAndOtherExplicitSources(GameTestHelper helper) {
        var player = owner(helper);
        var level = PigmeeDimensions.getOrCreate(player.server, create(helper, player, true));
        for (var reason : MobSpawnType.values()) {
            var check = new MobSpawnEvent.SpawnPlacementCheck(EntityType.ZOMBIE, level, reason,
                    new BlockPos(5, 21, 5), RandomSource.create(1), true);
            NeoForge.EVENT_BUS.post(check);
            boolean blocked = reason == MobSpawnType.NATURAL || reason == MobSpawnType.CHUNK_GENERATION
                    || reason == MobSpawnType.PATROL || reason == MobSpawnType.STRUCTURE;
            helper.assertTrue(check.getResult() == (blocked ? MobSpawnEvent.SpawnPlacementCheck.Result.FAIL
                    : MobSpawnEvent.SpawnPlacementCheck.Result.DEFAULT), "spawn reason policy: " + reason);
        }
        var overworld = new MobSpawnEvent.SpawnPlacementCheck(EntityType.ZOMBIE, helper.getLevel(), MobSpawnType.NATURAL,
                helper.absolutePos(BlockPos.ZERO), RandomSource.create(1), true);
        NeoForge.EVENT_BUS.post(overworld);
        helper.assertTrue(overworld.getResult() == MobSpawnEvent.SpawnPlacementCheck.Result.DEFAULT, "other dimensions unaffected");
        helper.succeed();
    }

    @GameTest(template = "pigmee_station_empty", timeoutTicks = 400)
    public static void actualSpawnerCanSpawnDespiteVisualBrightness(GameTestHelper helper) {
        var creator = owner(helper);
        var level = PigmeeDimensions.getOrCreate(creator.server, create(helper, creator, false));
        // Fake players do not keep entity sections ticking; this fixture owns a temporary ticket.
        level.setChunkForced(0, 0, true);
        creator.server.setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "SpawnerWitness"));
        player.moveTo(10.5, 21, 10.5, 0, 0);
        level.addNewPlayer(player);
        var pos = new BlockPos(4, 21, 4);
        for (var cell : BlockPos.betweenClosed(1, 21, 1, 7, 25, 7)) {
            if (cell.getX() == 1 || cell.getX() == 7 || cell.getZ() == 1 || cell.getZ() == 7 || cell.getY() == 25)
                level.setBlockAndUpdate(cell, Blocks.STONE.defaultBlockState());
        }
        level.setBlockAndUpdate(pos, Blocks.SPAWNER.defaultBlockState());
        var spawner = ((SpawnerBlockEntity) level.getBlockEntity(pos)).getSpawner();
        var tag = new CompoundTag();
        tag.putShort("Delay", (short) 0); tag.putShort("MinSpawnDelay", (short) 1); tag.putShort("MaxSpawnDelay", (short) 2);
        tag.putShort("SpawnCount", (short) 1); tag.putShort("SpawnRange", (short) 1);
        tag.putShort("RequiredPlayerRange", (short) 32); tag.putShort("MaxNearbyEntities", (short) 16);
        var entity = new CompoundTag(); entity.putString("id", "minecraft:zombie");
        var location = new ListTag(); location.add(DoubleTag.valueOf(5.5)); location.add(DoubleTag.valueOf(21)); location.add(DoubleTag.valueOf(4.5));
        entity.put("Pos", location); var spawnData = new CompoundTag(); spawnData.put("entity", entity); tag.put("SpawnData", spawnData);
        spawner.load(level, pos, tag);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(level.hasNearbyAlivePlayer(4.5, 21.5, 4.5, 32), "spawner has nearby living player");
            helper.assertTrue(level.getBrightness(LightLayer.BLOCK, new BlockPos(5, 21, 4)) == 0, "ambient light is visual, not block light");
            helper.succeedWhen(() -> {
                var spawnPos = new BlockPos(5, 21, 4);
                helper.assertTrue(level.getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL, "spawner non-peaceful difficulty");
                helper.assertTrue(net.minecraft.world.entity.SpawnPlacements.checkSpawnRules(EntityType.ZOMBIE, level,
                        MobSpawnType.SPAWNER, spawnPos, level.random), "ordinary spawner rules; sky=" + level.getBrightness(LightLayer.SKY, spawnPos));
                helper.assertTrue(level.noCollision(EntityType.ZOMBIE.getSpawnAABB(5.5, 21, 4.5)), "spawner spawn box clear");
                spawner.serverTick(level, pos);
                helper.assertTrue(!level.getEntities(EntityType.ZOMBIE, new AABB(1, 21, 1, 7, 25, 7), entity1 -> true).isEmpty(), "real spawner produces a zombie");
                level.removePlayerImmediately(player, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                level.setChunkForced(0, 0, false);
            });
        });
    }

    @GameTest(template = "pigmee_station_empty", timeoutTicks = 400)
    public static void entranceDropsRetainBinding(GameTestHelper helper) {
        var player = owner(helper);
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var origin = helper.getLevel();
        origin.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        origin.setBlockAndUpdate(pos, ModBlocks.PIGMEE_ODD_DIMENSION.get().defaultBlockState());
        var be = (PigmeeDimensionBlockEntity) origin.getBlockEntity(pos);
        var entry = create(helper, player, false);
        be.setPlacer(player.getUUID()); be.bind(entry.id());
        var drops = Block.getDrops(be.getBlockState(), origin, pos, be);
        helper.assertTrue(drops.size() == 1 && drops.getFirst().get(DataComponents.CUSTOM_DATA).copyTag().getInt("DimensionId") == entry.id(), "dropped entrance retains dimension ID");
        var replacement = pos.east();
        origin.setBlockAndUpdate(replacement, ModBlocks.PIGMEE_ODD_DIMENSION.get().defaultBlockState());
        ModBlocks.PIGMEE_ODD_DIMENSION.get().setPlacedBy(origin, replacement, origin.getBlockState(replacement), player, drops.getFirst());
        helper.assertTrue(((PigmeeDimensionBlockEntity) origin.getBlockEntity(replacement)).dimensionId() == entry.id(), "placing dropped entrance binds same dimension without consuming quota");
        helper.succeed();
    }
}
