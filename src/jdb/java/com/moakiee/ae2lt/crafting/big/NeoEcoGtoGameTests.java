package com.moakiee.ae2lt.crafting.big;

import appeng.api.config.Actionable;
import appeng.api.crafting.*;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import appeng.core.definitions.AEBlocks;
import appeng.me.storage.NetworkStorage;
import com.moakiee.ae2lt.compat.neoeco.NeoEcoCraftingCompat;
import com.moakiee.thunderbolt.api.crafting.batch.*;
import com.moakiee.thunderbolt.core.crafting.big.BigExecutionProgram;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.gametest.*;
import java.lang.reflect.*;
import java.math.BigInteger;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Uses the installed, unmodified NeoECO GTO release. Fixtures are excluded from production. */
@GameTestHolder("ae2lt_neoeco")
@PrefixGameTestTemplate(false)
public final class NeoEcoGtoGameTests {
    private static AEItemKey raw() { return AEItemKey.of(Items.OAK_LOG); }
    private static AEItemKey output() { return AEItemKey.of(Items.OAK_PLANKS); }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
    private static Object call(Object target, String name) {
        try { return target.getClass().getMethod(name).invoke(target); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(name, e); }
    }
    private static BooleanSupplier migrationProbe(IManagedGridNode node, Object provider) {
        try {
            var type = Class.forName("com.moakiee.ae2lt.logic.craft.migration.PatternMigrationSources");
            var probe = type.getDeclaredMethod("executionProbe", Object.class);
            probe.setAccessible(true);
            var constructor = type.getDeclaredConstructor(); constructor.setAccessible(true);
            var discover = type.getDeclaredMethod("discover", appeng.api.networking.IGridNode.class,
                    com.moakiee.ae2lt.blockentity.MatrixPortBlockEntity.class);
            discover.setAccessible(true);
            var sources = (List<?>) discover.invoke(constructor.newInstance(), node.getNode(), null);
            require(sources.size() == 1, "NeoECO migration source missing");
            var inventory = sources.get(0).getClass().getDeclaredField("inventory");
            inventory.setAccessible(true);
            require(inventory.get(sources.get(0)) == call(provider, "getPatternSlotInventory"),
                    "Migration must use physical NeoECO slots");
            return (BooleanSupplier) probe.invoke(null, provider);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("NeoECO migration probe", e); }
    }
    private static ItemStack encoded(GameTestHelper h) {
        // The QA datapack supplies this recipe independently of GTO's vanilla recipe pruning.
        var recipe = (CraftingRecipe) h.getLevel().getRecipeManager()
                .byKey(new ResourceLocation("ae2lt_qa", "oak_planks")).orElseThrow();
        var inputs = new ItemStack[9]; Arrays.fill(inputs, ItemStack.EMPTY);
        inputs[0] = new ItemStack(Items.OAK_LOG);
        return PatternDetailsHelper.encodeCraftingPattern(recipe, inputs,
                new ItemStack(Items.OAK_PLANKS, 4), false, false);
    }
    private static BlockState state(String name, Direction facing) {
        var id = new ResourceLocation("neoecoae", name);
        require(BuiltInRegistries.BLOCK.containsKey(id), "Missing NeoECO block " + id);
        var state = BuiltInRegistries.BLOCK.get(id).defaultBlockState();
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING))
            state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
        return state;
    }

    @GameTest(template = "empty", templateNamespace = "ae2lt_global_batch", timeoutTicks = 800)
    public static void realF4HostAcceptsLtBatchAndRetainsJobOwnedOutputAcrossReload(GameTestHelper h) {
        for (var p : BlockPos.betweenClosed(0, 1, 0, 4, 3, 1))
            h.setBlock(p, state("crafting_casing", Direction.NORTH));
        h.setBlock(new BlockPos(1, 2, 0), state("crafting_system_l4", Direction.NORTH));
        h.setBlock(new BlockPos(0, 2, 1), state("crafting_interface", Direction.NORTH));
        h.setBlock(new BlockPos(0, 3, 1), state("input_hatch", Direction.NORTH));
        h.setBlock(new BlockPos(0, 1, 1), state("output_hatch", Direction.NORTH));
        h.setBlock(new BlockPos(3, 2, 0), state("crafting_worker", Direction.NORTH));
        h.setBlock(new BlockPos(3, 3, 0), state("crafting_parallel_core_l4", Direction.NORTH));
        h.setBlock(new BlockPos(3, 1, 0), state("crafting_parallel_core_l4", Direction.NORTH));
        h.setBlock(new BlockPos(3, 2, 1), state("crafting_vent", Direction.SOUTH));
        h.setBlock(new BlockPos(3, 3, 1), state("crafting_pattern_bus", Direction.SOUTH));
        h.setBlock(new BlockPos(3, 1, 1), state("crafting_pattern_bus", Direction.SOUTH));
        h.setBlock(new BlockPos(-1, 2, 1), AEBlocks.CREATIVE_ENERGY_CELL.block());
        h.runAfterDelay(30, () -> {
            var controller = h.getBlockEntity(new BlockPos(1, 2, 0));
            call(controller, "rebuildMultiblock");
            require(Boolean.TRUE.equals(call(controller, "isFormed")), "Real F4 structure did not form");
            var provider = (ICraftingProvider) h.getBlockEntity(new BlockPos(3, 3, 1));
            var node = (IManagedGridNode) call(provider, "getMainNode");
            require(node.isActive(), "Real F4 grid inactive");
            ((InternalInventory) call(provider, "getPatternSlotInventory")).setItemDirect(0, encoded(h));
            h.runAfterDelay(5, () -> {
                require(provider.getAvailablePatterns().size() == 1, "Real FD bus did not publish pattern");
                var migration = migrationProbe(node, provider);
                require(!migration.getAsBoolean(), "Idle NeoECO bus blocked migration");
                var pattern = provider.getAvailablePatterns().get(0);
                var job = UUID.randomUUID();
                BatchJobView view = new BatchJobView() {
                    public UUID craftingId() { return job; }
                    public net.minecraft.world.level.Level level() { return h.getLevel(); }
                    public Iterator<BatchTaskHandle> taskIterator() { return Collections.emptyIterator(); }
                    public appeng.crafting.inv.ListCraftingInventory waitingFor() { return new appeng.crafting.inv.ListCraftingInventory(k -> {}); }
                    public void insertWaitingFor(AEKey key, long amount) {}
                    public void addContainerMaxItems(long count, AEKeyType type) {}
                };
                IBatchCraftingProvider adapter = null;
                for (var entry : BatchProviderAdapters.entries()) {
                    if (entry.id().toString().equals("ae2lt:neoeco_gto"))
                        adapter = entry.adapter().adapt(provider, pattern, view);
                }
                require(adapter != null, "LT did not register NeoECO adapter");
                var received = new KeyCounter();
                MEStorage sink = new MEStorage() {
                    public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
                        if (mode == Actionable.MODULATE) received.add(key, amount);
                        return amount;
                    }
                    public Component getDescription() { return Component.literal("NeoECO QA sink"); }
                };
                var network = (NetworkStorage) node.getGrid().getStorageService().getInventory();
                network.mount(0, sink);
                var template = new KeyCounter[]{new KeyCounter()}; template[0].add(raw(), 1);
                var warmup = new KeyCounter[]{new KeyCounter()}; warmup[0].add(raw(), 1);
                var warmupJob = UUID.randomUUID();
                require(NeoEcoCraftingCompat.pushForJob(provider, pattern, warmup, warmupJob),
                        "Real F4 rejected first ordinary craft for recipe verification");
                require(migration.getAsBoolean(), "Migration missed NeoECO's active worker");
                var selectedAdapter = adapter;
                h.runAfterDelay(20, () -> {
                NeoEcoCraftingCompat.cancel(h.getLevel(), warmupJob);
                h.runAfterDelay(5, () -> {
                var worker = h.getBlockEntity(new BlockPos(3, 2, 0));
                var prepared = selectedAdapter.prepareBatch(pattern, template, 16, view);
                require(prepared != null && prepared.capacity() > 1, "Real F4 rejected batch admission: capacity="
                        + (prepared == null ? null : prepared.capacity()) + ", slots=" + call(worker, "getAvailableThreadSlots")
                        + ", workers=" + call(worker, "getThreadSnapshots"));
                long copies = prepared.capacity();
                require(prepared.push(copies) == 0, "Real F4 rejected admitted batch");
                require(template[0].get(raw()) == 1, "Borrowed CPU template was consumed");
                var saved = worker.saveWithFullMetadata();
                worker.load(saved); // Persisted worker ownership must survive an actual NBT round trip.
                // Unknown CPUs retain ownership; cancel explicitly and verify physical recovery exactly once.
                NeoEcoCraftingCompat.cancel(h.getLevel(), job);
                h.succeedWhen(() -> {
                    long actual = received.get(output()) + received.get(raw()) * 4;
                    h.assertTrue(actual == (copies + 1) * 4, "Cancellation/reload did not recover input or finished output: " + received);
                    var snapshots = (List<?>) call(worker, "getThreadSnapshots");
                    h.assertTrue(snapshots.isEmpty(), "Worker still retains recovered job");
                    h.assertFalse(migration.getAsBoolean(), "Recovered NeoECO host still blocks migration");
                    network.unmount(sink);
                });
                });
                });
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "ae2lt_global_batch", timeoutTicks = 40)
    public static void publishedExactFacadePreserves101BitReservationSynchronousReturnAndRejection(GameTestHelper h) {
        var pattern = Objects.requireNonNull(PatternDetailsHelper.decodePattern(encoded(h), h.getLevel()));
        var recipe = Objects.requireNonNull(BigProviderRecipe.capture(pattern, List.of(Map.of(raw(), 1L)), h.getLevel()));
        var count = BigInteger.ONE.shiftLeft(100).add(BigInteger.valueOf(37));
        for (boolean accepts : new boolean[]{false, true}) {
            var program = BigExecutionProgram.of(List.of(BigExecutionProgram.block(
                    List.of(new BigExecutionProgram.Step<>(recipe.pattern(), count)), BigInteger.ONE)));
            var job = new BigCraftingJob(output(), count.multiply(BigInteger.valueOf(4)), program);
            job.escrow.putAll(program.required()); job.beginStreaming();
            int[] submissions = {0};
            try {
                var type = Class.forName("cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider");
                var preparation = Class.forName(type.getName() + "$ExactPreparation");
                var provider = (ICraftingProvider) Proxy.newProxyInstance(type.getClassLoader(),
                        new Class<?>[]{type, ICraftingProvider.class}, (p, m, args) -> switch (m.getName()) {
                            case "eco$prepareExactFastPath" -> preparation.getConstructor(BigInteger.class, BooleanSupplier.class)
                                    .newInstance(count, (BooleanSupplier) () -> {
                                        submissions[0]++;
                                        require(job.escrow.getOrDefault(raw(), BigInteger.ZERO).signum() == 0, "Exact input not reserved before submit");
                                        require(job.waiting.get(output()).equals(count.multiply(BigInteger.valueOf(4))), "Exact waiting not reserved");
                                        if (accepts) require(job.receiveBig(output(), count.multiply(BigInteger.valueOf(4)), Actionable.MODULATE)
                                                .equals(count.multiply(BigInteger.valueOf(4))), "Synchronous exact return lost");
                                        return accepts;
                                    });
                            case "isBusy" -> false;
                            case "getAvailablePatterns" -> List.of(pattern);
                            case "hashCode" -> System.identityHashCode(p);
                            case "equals" -> p == args[0];
                            case "toString" -> "NeoECO published facade QA provider";
                            default -> null;
                        });
                var power = (appeng.api.networking.energy.IEnergyService) Proxy.newProxyInstance(type.getClassLoader(),
                        new Class<?>[]{appeng.api.networking.energy.IEnergyService.class}, (p, m, args) -> args[0]);
                long dispatched = new BigProviderDispatcher().dispatch(job, recipe, pattern, provider,
                        h.getLevel(), power, 1, () -> {});
                require(submissions[0] == 1 && dispatched == (accepts ? 1 : 0), "Exact result not settled once");
                require(!job.dispatchFailed && job.waiting.isEmpty(), "Exact dispatch left incorrect waiting state");
                require(job.acceptedCopies.equals(accepts ? count : BigInteger.ZERO), "Exact count narrowed");
                require(job.escrow.equals(accepts ? Map.of(output(), count.multiply(BigInteger.valueOf(4))) : Map.of(raw(), count)),
                        "Exact resources duplicated or lost");
            } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        }
        h.succeed();
    }
}
