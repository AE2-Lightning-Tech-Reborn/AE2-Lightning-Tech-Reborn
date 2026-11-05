package com.moakiee.ae2lt.dimension;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureSet;

/** No caves, features, structures, or natural spawn tables, including biome modifiers. */
public final class PigmeeChunkGenerator extends ChunkGenerator {
    public static final MapCodec<PigmeeChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Biome.CODEC.fieldOf("biome").forGetter(generator -> generator.biome),
            com.mojang.serialization.Codec.BOOL.fieldOf("even").forGetter(generator -> generator.even),
            BlockState.CODEC.fieldOf("frame").forGetter(generator -> generator.palette.frame()),
            BlockState.CODEC.fieldOf("center").forGetter(generator -> generator.palette.center()),
            PigmeeDimensionPattern.CODEC.optionalFieldOf("cells", List.of()).forGetter(generator -> generator.palette.cells())
    ).apply(instance, PigmeeChunkGenerator::new));

    public record Palette(BlockState frame, BlockState center, List<BlockState> cells) {
        public Palette(BlockState frame, BlockState center) { this(frame, center, List.of()); }

        public Palette {
            if (!PigmeeDimensionMaterials.accepts(frame.getBlock())
                    || !PigmeeDimensionMaterials.accepts(center.getBlock()) || !PigmeeDimensionPattern.valid(cells)) {
                throw new IllegalArgumentException("Pigmee dimensions require Pigmee building materials");
            }
            cells = List.copyOf(cells);
        }

        public boolean custom() { return !cells.isEmpty(); }
    }

    private final Holder<Biome> biome;
    private final boolean even;
    private volatile Palette palette;

    public PigmeeChunkGenerator(Holder<Biome> biome, boolean even, BlockState frame, BlockState center) {
        this(biome, even, frame, center, List.of());
    }

    public PigmeeChunkGenerator(Holder<Biome> biome, boolean even, BlockState frame, BlockState center, List<BlockState> cells) {
        super(new FixedBiomeSource(biome));
        this.biome = biome;
        this.even = even;
        this.palette = new Palette(frame, center, cells);
    }

    public boolean even() { return even; }
    public Palette palette() { return palette; }
    public void setPalette(Palette palette) { this.palette = palette; }

    @Override protected MapCodec<? extends ChunkGenerator> codec() { return CODEC; }

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> lookup, RandomState random, long seed) {
        return ChunkGeneratorStructureState.createForFlat(random, seed, biomeSource, Stream.empty());
    }

    public BlockState surfaceAt(int x, int z) { return surfaceAt(palette, x, z); }

    private BlockState surfaceAt(Palette snapshot, int x, int z) {
        if (snapshot.custom()) return snapshot.cells().get(Math.floorMod(z, 16) * 16 + Math.floorMod(x, 16));
        return switch (PigmeeDimensionLayout.cell(even, x, z)) {
            case FRAME -> snapshot.frame();
            case CENTER -> snapshot.center();
            case FLOOR -> PigmeeDimensionMaterials.floor();
        };
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random,
            StructureManager structures, ChunkAccess chunk) {
        // One immutable snapshot per chunk; a concurrent palette edit cannot split its surface.
        var snapshot = palette;
        var pos = new BlockPos.MutableBlockPos();
        var ocean = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        var surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        for (int y = Math.max(getMinY(), chunk.getMinBuildHeight());
                y <= PigmeeDimensionLayout.SURFACE_Y && y < chunk.getMaxBuildHeight(); y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    BlockState block = y == PigmeeDimensionLayout.SURFACE_Y ? surfaceAt(snapshot, x, z)
                            : y < getMinY() + PigmeeDimensionLayout.STONE_LAYERS
                                    ? Blocks.STONE.defaultBlockState() : Blocks.DIRT.defaultBlockState();
                    chunk.setBlockState(pos.set(x, y, z), block, false);
                    ocean.update(x, y, z, block);
                    surface.update(x, y, z, block);
                }
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    @Override public int getMinY() { return PigmeeDimensionLayout.MIN_Y; }
    @Override public int getGenDepth() { return PigmeeDimensionLayout.HEIGHT; }
    @Override public int getSeaLevel() { return getMinY() - 1; }
    @Override public int getSpawnHeight(LevelHeightAccessor level) { return PigmeeDimensionLayout.SPAWN_Y; }
    @Override public void buildSurface(WorldGenRegion level, StructureManager structures, RandomState random, ChunkAccess chunk) {}
    @Override public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structures) {}
    @Override public void spawnOriginalMobs(WorldGenRegion level) {}
    @Override public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {}
    @Override public void applyCarvers(WorldGenRegion level, long seed, RandomState random,
            BiomeManager biomes, StructureManager structures, ChunkAccess chunk, GenerationStep.Carving step) {}

    @Override
    public WeightedRandomList<MobSpawnSettings.SpawnerData> getMobsAt(Holder<Biome> biome,
            StructureManager structures, MobCategory category, BlockPos pos) {
        return WeightedRandomList.create();
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        return Math.min(PigmeeDimensionLayout.SPAWN_Y, level.getMaxBuildHeight());
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        int min = Math.max(getMinY(), level.getMinBuildHeight());
        int end = Math.min(PigmeeDimensionLayout.SPAWN_Y, level.getMaxBuildHeight());
        var states = new BlockState[Math.max(0, end - min)];
        for (int i = 0; i < states.length; i++) {
            int y = min + i;
            states[i] = y == PigmeeDimensionLayout.SURFACE_Y ? surfaceAt(x, z)
                    : y < getMinY() + PigmeeDimensionLayout.STONE_LAYERS
                            ? Blocks.STONE.defaultBlockState() : Blocks.DIRT.defaultBlockState();
        }
        return new NoiseColumn(min, states);
    }
}
