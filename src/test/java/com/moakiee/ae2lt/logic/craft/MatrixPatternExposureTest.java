package com.moakiee.ae2lt.logic.craft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.moakiee.ae2lt.crafting.matrix.core.CopyAssembler;
import com.moakiee.ae2lt.crafting.matrix.core.CraftingCoreHost;
import com.moakiee.ae2lt.crafting.matrix.core.CraftingCoreRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;

class MatrixPatternExposureTest {
    private static final FakePattern PATTERN = new FakePattern("encoded");
    private static final AEKey OUTPUT = new TestKey("interface-output");

    @Test
    void clusterExposesRepositoryPatternsAndBatchCapacity() {
        var host = new FakeHost();
        var assembler = new FakeAssembler();
        var cluster = cluster(host, List.of(new FakeCraftCore(
                MatrixCraftingUnit.stableCore(),
                MatrixCraftingUnit.t1Threader())), assembler);
        var patternUnit = unit(PATTERN);
        var repository = new MatrixPatternRepository(List.of(patternUnit));

        cluster.addPatternCore(repository);

        assertEquals(List.of(PATTERN), cluster.getAvailablePatterns());
        assertSame(patternUnit, repository.exposedUnit());
        assertTrue(cluster.getBatchCapacity(PATTERN) >= 12);
        assertEquals(0, cluster.pushBatch(PATTERN, emptyInputs(), 12));
        assertEquals(1, assembler.calls);
    }

    @Test
    void clusterRejectsNonMolecularPatterns() {
        var assembler = new FakeAssembler();
        var cluster = cluster(new FakeHost(), List.of(new FakeCraftCore(
                MatrixCraftingUnit.quantumCore(),
                MatrixCraftingUnit.t1Threader(),
                MatrixCraftingUnit.amplifier())), assembler);
        var plainPattern = new FakePlainPattern("plain");
        var repository = new MatrixPatternRepository(List.of(unit(PATTERN, plainPattern)));

        cluster.addPatternCore(repository);

        assertEquals(List.of(PATTERN), cluster.getAvailablePatterns());
        assertEquals(0, cluster.getBatchCapacity(plainPattern));
        assertEquals(7, cluster.pushBatch(plainPattern, emptyInputs(), 7));
        assertEquals(0, assembler.calls);
    }

    @Test
    void repositoryBackpressureLimitsClusterPatterns() {
        var cluster = cluster(new FakeHost(), List.of(new FakeCraftCore(MatrixCraftingUnit.quantumCore())),
                (details, oneCopyInputs) -> null);
        var repository = new MatrixPatternRepository(List.of(new MatrixPatternStorageUnit(1)));
        cluster.addPatternCore(repository);
        var first = new FakePattern("first");
        var overflow = new FakePattern("overflow");

        assertTrue(repository.insert(first));
        assertFalse(repository.insert(overflow));
        assertEquals(List.of(first), cluster.getAvailablePatterns());
        assertSame(first, repository.exposedUnit().get(0));
    }

    @Test
    void repositoryRejectsNonMolecularPatternInsertion() {
        var cluster = cluster(new FakeHost(), List.of(new FakeCraftCore(MatrixCraftingUnit.quantumCore())),
                (details, oneCopyInputs) -> null);
        var repository = new MatrixPatternRepository(List.of(new MatrixPatternStorageUnit(2)));
        cluster.addPatternCore(repository);
        var plain = new FakePlainPattern("plain");

        assertFalse(repository.insert(plain));
        assertEquals(List.of(plain), repository.insertAll(List.of(plain)));
        assertEquals(0, repository.usedSlots());
        assertEquals(List.of(), cluster.getAvailablePatterns());
    }

    private static MatrixCraftingCluster cluster(FakeHost host, List<FakeCraftCore> cores, CopyAssembler assembler) {
        return new MatrixCraftingCluster(
                () -> true,
                List.of(),
                cores,
                host,
                assembler,
                new CraftingCoreRegistry(),
                MatrixCraftingEnergy.UNLIMITED);
    }

    private static KeyCounter[] emptyInputs() {
        return new KeyCounter[0];
    }

    private static MatrixPatternStorageUnit unit(IPatternDetails... patterns) {
        var unit = new MatrixPatternStorageUnit(patterns.length);
        for (var pattern : patterns) {
            unit.insert(pattern);
        }
        return unit;
    }

    private static final class FakeAssembler implements CopyAssembler {
        int calls;

        @Override
        public AssembledCopy assembleOneCopy(IPatternDetails details, KeyCounter[] oneCopyInputs) {
            calls++;
            return new AssembledCopy(OUTPUT, 1, List.of());
        }
    }

    private static final class FakeCraftCore implements MatrixCraftCore {
        private final List<MatrixCraftingUnit> units;

        FakeCraftCore(MatrixCraftingUnit... units) {
            this.units = List.of(units);
        }

        @Override
        public List<MatrixCraftingUnit> craftingUnits() {
            return units;
        }
    }

    private static final class FakeHost implements CraftingCoreHost {
        @Override
        public long getGameTime() {
            return 0;
        }

        @Override
        public boolean isRemoved() {
            return false;
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public long insertToNetwork(AEKey key, long amount) {
            return amount;
        }

        @Override
        public void spawnToWorld(AEKey key, long amount) {
        }
    }

    private static final class FakePattern implements IMolecularAssemblerSupportedPattern {
        private final String id;

        private FakePattern(String id) {
            this.id = id;
        }

        @Override
        public ItemStack assemble(Container input, Level level) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean isItemValid(int slotIndex, AEItemKey key, Level level) {
            return true;
        }

        @Override
        public boolean isSlotEnabled(int slot) {
            return true;
        }

        @Override
        public void fillCraftingGrid(KeyCounter[] inputHolder, CraftingGridAccessor accessor) {
        }

        @Override
        public AEItemKey getDefinition() {
            return null;
        }

        @Override
        public IInput[] getInputs() {
            return new IInput[0];
        }

        @Override
        public GenericStack[] getOutputs() {
            return new GenericStack[0];
        }

        @Override
        public NonNullList<ItemStack> getRemainingItems(CraftingContainer input) {
            return NonNullList.create();
        }
    }

    private static final class FakePlainPattern implements IPatternDetails {
        private final String id;

        private FakePlainPattern(String id) {
            this.id = id;
        }

        @Override
        public AEItemKey getDefinition() {
            return null;
        }

        @Override
        public IInput[] getInputs() {
            return new IInput[0];
        }

        @Override
        public GenericStack[] getOutputs() {
            return new GenericStack[0];
        }
    }

    private static final class TestKey extends AEKey {
        private static final TestKeyType TYPE = new TestKeyType();
        private final String id;

        private TestKey(String id) {
            this.id = id;
        }

        @Override
        public AEKeyType getType() {
            return TYPE;
        }

        @Override
        public AEKey dropSecondary() {
            return this;
        }

        @Override
        public CompoundTag toTag() {
            var tag = new CompoundTag();
            tag.putString("id", id);
            return tag;
        }

        @Override
        public Object getPrimaryKey() {
            return id;
        }

        @Override
        public ResourceLocation getId() {
            return new ResourceLocation("ae2lt_test", id);
        }

        @Override
        public void writeToPacket(FriendlyByteBuf data) {
        }

        @Override
        protected Component computeDisplayName() {
            return Component.literal(id);
        }

        @Override
        public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof TestKey other && id.equals(other.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }

    private static final class TestKeyType extends AEKeyType {
        private TestKeyType() {
            super(new ResourceLocation("ae2lt_test", "key"), TestKey.class,
                    Component.literal("test key"));
        }

        @Override
        public AEKey loadKeyFromTag(CompoundTag tag) {
            return null;
        }

        @Override
        public AEKey readFromPacket(FriendlyByteBuf input) {
            return null;
        }
    }
}
