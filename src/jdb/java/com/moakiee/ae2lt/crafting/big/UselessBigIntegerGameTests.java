package com.moakiee.ae2lt.crafting.big;

import appeng.api.crafting.*;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.GridHelper;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.*;
import appeng.blockentity.networking.CreativeEnergyCellBlockEntity;
import appeng.core.definitions.AEBlocks;
import com.moakiee.thunderbolt.core.crafting.big.BigExecutionProgram;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.*;
import java.lang.reflect.*;
import java.math.BigInteger;
import java.util.*;

/** Public API ownership tests plus a naturally ticking, fully formed upstream machine. */
@GameTestHolder("ae2lt_bigint")
@PrefixGameTestTemplate(false)
public final class UselessBigIntegerGameTests {
    private static final BigInteger N = BigInteger.TEN.pow(100);
    private static final String API = "com.sorrowmist.useless.api.crafting.bigint.";

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void formedOmniversalMachineReturnsExactBatchOnItsOwnTick(GameTestHelper h) throws Exception {
        formedMachine(h, false);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void formedOmniversalMachineExecutesItsOwnEncodedPattern(GameTestHelper h) throws Exception {
        formedMachine(h, true);
    }

    private static void formedMachine(GameTestHelper h, boolean omniversal) throws Exception {
        var level = h.getLevel();
        var corePos = h.absolutePos(new BlockPos(6, 8, omniversal ? 400 : 300));
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                level.setChunkForced((corePos.getX() >> 4) + dx, (corePos.getZ() >> 4) + dz, true);
        var structure = Class.forName("com.sorrowmist.useless.content.blocks.multiblock.OmniversalAlloyFurnaceStructure");
        for (var entry : (List<?>) structure.getMethod("entries").invoke(null)) {
            var local = (BlockPos) call(entry, "localPos");
            var pos = (BlockPos) entry.getClass().getMethod("worldPos", BlockPos.class, Direction.class)
                    .invoke(entry, corePos, Direction.NORTH);
            String name = switch (call(entry, "part").toString()) {
                case "CORE" -> "multiblock_alloy_furnace_core";
                case "COIL" -> "useful_coil";
                case "CASING" -> local.equals(new BlockPos(-1, 0, 0)) ? "me_pattern_assembly"
                        : local.equals(new BlockPos(1, 0, 0)) ? "omniversal_mold_hub" : "omniversal_furnace_casing";
                default -> null;
            };
            var state = name == null ? Blocks.AIR.defaultBlockState() : block(name);
            if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING))
                state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
            level.setBlock(pos, state, 3);
        }
        var core = level.getBlockEntity(corePos);
        call(core, "validateStructure");
        h.assertTrue((boolean) call(core, "isFormed"), "real Omniversal structure did not form");
        var energy = call(core, "getEnergyStorage");
        energy.getClass().getMethod("receiveEnergy", int.class, boolean.class).invoke(energy, 1_000_000, false);
        var holder = level.getRecipeManager().byKey(ResourceLocation.withDefaultNamespace("oak_planks")).orElseThrow();
        var slots = new ItemStack[9]; Arrays.fill(slots, ItemStack.EMPTY); slots[0] = new ItemStack(Items.OAK_LOG);
        var craftingPattern = PatternDetailsHelper.encodeCraftingPattern(
                new RecipeHolder<>(holder.id(), (CraftingRecipe) holder.value()), slots,
                new ItemStack(Items.OAK_PLANKS, 4), false, false);
        var encoded = omniversal ? omniversalPattern(h, corePos) : craftingPattern;
        var assembly = level.getBlockEntity(corePos.west());
        var inventory = (net.neoforged.neoforge.items.IItemHandlerModifiable) call(assembly, "getPatterns");
        inventory.setStackInSlot(0, encoded);
        var powerPos = corePos.offset(-4, 0, 0);
        level.setBlock(powerPos, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), 3);
        h.runAfterDelay(10, () -> {
            try {
                var node = (IManagedGridNode) call(assembly, "getMainNode");
                var power = (CreativeEnergyCellBlockEntity) level.getBlockEntity(powerPos);
                GridHelper.createConnection(node.getNode(), power.getMainNode().getNode());
                h.runAfterDelay(20, () -> {
                    try {
                        var grid = (IGrid) call(core, "getAeGrid");
                        var service = BigServiceTestFixture.serviceFor(level, grid);
                        var raw = AEItemKey.of(omniversal ? Items.RAW_IRON : Items.OAK_LOG);
                        var output = AEItemKey.of(omniversal ? Items.IRON_INGOT : Items.OAK_PLANKS);
                        var perCopy = BigInteger.valueOf(omniversal ? 1 : 4);
                        var details = PatternDetailsHelper.decodePattern(encoded, level);
                        var recipe = Objects.requireNonNull(BigProviderRecipe.capture(details, List.of(Map.of(raw, 1L)), level));
                        var program = BigExecutionProgram.of(List.of(BigExecutionProgram.block(
                                List.of(new BigExecutionProgram.Step<>(recipe.pattern(), N)), BigInteger.ONE)));
                        var job = new BigCraftingJob(output, N.multiply(perCopy), program);
                        job.escrow.putAll(program.required());
                        var snapshot = new CompoundTag();
                        snapshot.put("job", job.save(level.registryAccess()));
                        service.load(snapshot, level.registryAccess());
                        h.assertTrue(UselessBigIntegerCompat.targets(grid).size() == 1, "real target not discovered");
                        service.tick();
                        var accepted = service.job().acceptedCopies;
                        h.assertTrue(accepted.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0
                                        && accepted.compareTo(N) <= 0,
                                "real machine did not accept exact batches: " + accepted);
                        // Default upstream configuration keeps its per-thread material window.
                        // Respect the actual admission, including its adaptive tick budget.
                        var expected = new HashMap<AEKey, BigInteger>();
                        if (accepted.compareTo(N) < 0) expected.put(raw, N.subtract(accepted));
                        expected.put(output, accepted.multiply(perCopy));
                        h.assertTrue(!service.job().escrow.containsKey(output), "machine output arrived before its own tick");
                        service.load(service.save(level.registryAccess()), level.registryAccess());
                        h.succeedWhen(() -> {
                            h.assertTrue(service.job().waiting.isEmpty(), "waiting for real machine output");
                            h.assertTrue(service.job().escrow.equals(expected),
                                    "real machine output truncated, duplicated, or lost after CPU reload");
                        });
                    } catch (ReflectiveOperationException failure) {
                        throw new RuntimeException(failure);
                    }
                });
            } catch (ReflectiveOperationException failure) {
                throw new RuntimeException(failure);
            }
        });
    }

    private static ItemStack omniversalPattern(GameTestHelper h, BlockPos corePos) throws Exception {
        var level = h.getLevel();
        var processing = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(Items.RAW_IRON), 1)),
                List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1))), level);
        var catalog = Class.forName("com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog");
        var entries = (List<?>) catalog.getMethod("findPatternCandidates",
                net.minecraft.world.level.Level.class, IPatternDetails.class).invoke(null, level, processing);
        h.assertTrue(!entries.isEmpty(), "missing upstream iron smelting recipe");
        var entry = entries.getFirst();
        var encoding = Class.forName("com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalPatternEncoding");
        var encoded = (ItemStack) encoding.getMethod("reencodeFromRecipe",
                entry.getClass(), net.minecraft.world.level.Level.class).invoke(null, entry, level);
        h.assertTrue(!encoded.isEmpty(), "upstream recipe did not encode");
        var molds = (net.neoforged.neoforge.items.IItemHandlerModifiable) call(
                level.getBlockEntity(corePos.east()), "getMolds");
        int slot = 0;
        for (var mold : (List<?>) call(call(entry, "recipe"), "molds"))
            molds.setStackInSlot(slot++, ((Ingredient) mold).getItems()[0].copy());
        h.assertTrue(PatternDetailsHelper.decodePattern(encoded, level).getClass().getSimpleName()
                .equals("OmniversalPatternDetails"), "test did not use the upstream pattern kind");
        return encoded;
    }

    private static Object call(Object target, String method) throws ReflectiveOperationException {
        return target.getClass().getMethod(method).invoke(target);
    }

    private static BlockState block(String name) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("useless_mod", name)).defaultBlockState();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void publishedApiDiscoversAndHandsOverHugeOutputsAfterCpuReload(GameTestHelper h) throws Exception {
        var f = new Fixture(h);
        f.service.tick();
        h.assertTrue(f.machine.calls == 1, "exact batch was split or native fallback used");
        h.assertTrue(f.service.job().acceptedCopies.equals(N), "exact accepted count truncated");
        h.assertTrue(f.service.job().waiting.get(f.output).equals(N.multiply(BigInteger.valueOf(4))), "missing wait ledger");
        h.assertTrue(!f.service.job().escrow.containsKey(f.output), "generated output without a receipt");
        f.service.load(f.service.save(h.getLevel().registryAccess()), h.getLevel().registryAccess());
        var claimed = f.machine.claim();
        h.assertTrue(claimed.equals(Map.of(f.output, N.multiply(BigInteger.valueOf(4)))), "bulk claim truncated after reload");
        f.machine.notifyCompleted();
        f.service.tick();
        h.assertTrue(!f.service.busy(), "claimed output did not finish job");
        h.assertTrue(f.disk.contents.equals(claimed), "notification duplicated actual output");
        h.assertTrue(f.machine.claim().isEmpty(), "stale callback claimed completed output twice");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void exactApiRejectionRefundsAndThrowingCommitStopsRetries(GameTestHelper h) throws Exception {
        var f = new Fixture(h);
        f.machine.reject = true;
        f.service.tick();
        h.assertTrue(f.service.job().escrow.equals(Map.of(f.raw, N)) && f.service.job().waiting.isEmpty(),
                "rejected exact batch lost inputs");
        f.machine.reject = false;
        f.machine.fail = true;
        f.service.tick();
        h.assertTrue(f.service.job().dispatchFailed && !f.service.job().escrow.containsKey(f.raw),
                "uncertain exact commit was refunded");
        int calls = f.machine.calls;
        f.service.tick();
        h.assertTrue(f.machine.calls == calls, "uncertain exact commit retried");
        f.service.cancel();
        h.assertTrue(f.disk.contents.isEmpty(), "cancel fabricated refund of uncertain machine input");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void synchronousExactClaimWaitsForSettlementAndCancelledCallbacksDecline(GameTestHelper h) throws Exception {
        var f = new Fixture(h);
        f.machine.synchronous = true;
        f.service.tick();
        h.assertTrue(!f.service.busy() && f.disk.contents.get(f.output).equals(N.multiply(BigInteger.valueOf(4))),
                "synchronous exact handover did not finish in same tick");
        var cancelled = new Fixture(h);
        cancelled.service.tick();
        cancelled.service.cancel();
        h.assertTrue(cancelled.machine.claim().isEmpty(), "cancelled task claimed machine-owned output");
        h.succeed();
    }

    private static final class Fixture extends BigServiceTestFixture {
        final AEKey raw = AEItemKey.of(Items.OAK_LOG), output = AEItemKey.of(Items.OAK_PLANKS);
        final IPatternDetails details;
        final FakeMachine machine;

        Fixture(GameTestHelper h) throws Exception {
            super(h.getLevel());
            var holder = level.getRecipeManager().byKey(ResourceLocation.withDefaultNamespace("oak_planks")).orElseThrow();
            var slots = new ItemStack[9]; Arrays.fill(slots, ItemStack.EMPTY); slots[0] = new ItemStack(Items.OAK_LOG);
            details = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeCraftingPattern(
                    new RecipeHolder<>(holder.id(), (CraftingRecipe) holder.value()), slots,
                    new ItemStack(Items.OAK_PLANKS, 4), false, false), level);
            var recipe = Objects.requireNonNull(BigProviderRecipe.capture(details, List.of(Map.of(raw, 1L)), level));
            var program = BigExecutionProgram.of(List.of(BigExecutionProgram.block(
                    List.of(new BigExecutionProgram.Step<>(recipe.pattern(), N)), BigInteger.ONE)));
            var job = new BigCraftingJob(output, N.multiply(BigInteger.valueOf(4)), program);
            job.escrow.putAll(program.required());
            load(job);
            crafting.addGlobalCraftingProvider(new ICraftingProvider() {
                public List<IPatternDetails> getAvailablePatterns() { return List.of(details); }
                public boolean isBusy() { return true; }
                public boolean pushPattern(IPatternDetails p, KeyCounter[] inputs) {
                    throw new AssertionError("bigint-capable target fell back to single copies");
                }
            });
            machine = new FakeMachine(this, h);
            var marker = Class.forName(API + "AlloyFurnaceBigIntegerProvider");
            var owner = Proxy.newProxyInstance(marker.getClassLoader(), new Class<?>[]{marker},
                    (p, m, a) -> m.getName().equals("bigIntegerTarget") ? machine.target : null);
            gridValues.put("getNodes", Set.of(proxy(IGridNode.class, Map.of("getOwner", owner))));
            h.assertTrue(UselessBigIntegerCompat.targets(grid).size() == 1, "public API discovery/registration failed");
        }
    }

    private static final class FakeMachine {
        final Fixture f;
        final GameTestHelper h;
        final Object target;
        final Class<?> adapterType = Class.forName(API + "cpu.AlloyFurnaceBigIntegerCpuAdapter");
        final Class<?> contextType = Class.forName(API + "cpu.AlloyFurnaceBigIntegerBatchContext");
        final Class<?> resultType = Class.forName(API + "cpu.AlloyFurnaceBigIntegerBatchResult");
        final Object adapter;
        Object context;
        List<?> outputs;
        boolean reject, fail, synchronous;
        int calls;

        FakeMachine(Fixture f, GameTestHelper h) throws Exception {
            this.f = f; this.h = h;
            var registry = Class.forName(API + "cpu.AlloyFurnaceBigIntegerCpuAdapters");
            adapter = ((Optional<?>) registry.getMethod("find", ResourceLocation.class).invoke(null,
                    ResourceLocation.fromNamespaceAndPath("ae2lt", "exact_cpu"))).orElseThrow();
            var type = Class.forName(API + "AlloyFurnaceBigIntegerTarget");
            target = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, m, a) -> switch (m.getName()) {
                case "grid" -> f.grid;
                case "machineIdentity", "routeIdentity" -> "test:" + System.identityHashCode(f);
                case "acceptedPatternKinds" -> Set.of(BuiltInRegistries.ITEM.getKey(f.details.getDefinition().getItem()));
                case "admit" -> batch((KeyCounter[]) a[1], (BigInteger) a[2], a[3]);
                default -> throw new AssertionError("unexpected public target call " + m);
            });
        }

        Object batch(KeyCounter[] prototype, BigInteger count, Object binding) throws Exception {
            var type = Class.forName(API + "AlloyFurnaceBigIntegerBatch");
            return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, m, a) -> switch (m.getName()) {
                case "count" -> count;
                case "commit" -> {
                    calls++;
                    h.assertTrue(a[0] == prototype && prototype[0].get(f.raw) == 1,
                            "API prototype identity/single-copy contract violated");
                    if (fail) throw new IllegalStateException("uncertain exact provider");
                    if (reject) yield false;
                    prototype[0].clear();
                    var outputType = Class.forName(API + "AlloyFurnaceBigIntegerOutput");
                    outputs = List.of(outputType.getConstructor(AEKey.class, BigInteger.class)
                            .newInstance(f.output, count.multiply(BigInteger.valueOf(4))));
                    var token = binding.getClass().getMethod("cpuToken").invoke(binding);
                    context = contextType.getConstructors()[0].newInstance(UUID.randomUUID(), token,
                            "test:machine", f.details, count, outputs, 0L);
                    adapterType.getMethod("onBatchAdmitted", contextType).invoke(adapter, context);
                    if (synchronous) {
                        h.assertTrue(claim().get(f.output).equals(count.multiply(BigInteger.valueOf(4))), "sync claim failed");
                        h.assertTrue(!f.service.job().escrow.containsKey(f.output), "claim credited before commit settled");
                    }
                    yield true;
                }
                default -> throw new AssertionError("unexpected batch call " + m);
            });
        }

        @SuppressWarnings("unchecked")
        Map<AEKey, BigInteger> claim() throws Exception {
            return (Map<AEKey, BigInteger>) adapterType.getMethod("claimOutputs", contextType, List.class)
                    .invoke(adapter, context, outputs);
        }

        void notifyCompleted() throws Exception {
            adapterType.getMethod("onBatchOutputs", contextType, List.class).invoke(adapter, context, outputs);
            Object success = Arrays.stream(resultType.getEnumConstants()).filter(v -> v.toString().equals("SUCCESS")).findFirst().orElseThrow();
            adapterType.getMethod("onBatchFinished", contextType, resultType).invoke(adapter, context, success);
        }
    }
}
