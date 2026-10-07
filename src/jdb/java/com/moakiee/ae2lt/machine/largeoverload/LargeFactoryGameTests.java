package com.moakiee.ae2lt.machine.largeoverload;

import java.util.*;
import java.util.function.Consumer;
import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.blockentity.FirmamentConversionCoreBlockEntity;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.registry.ModBlocks;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.*;

/** Real blocks, structure scanner, AE2 grids and network storage; fixtures are excluded from the mod jar. */
@GameTestHolder("ae2lt_large_factory")
@PrefixGameTestTemplate(false)
public final class LargeFactoryGameTests {
    static final AEKey STONE = AEItemKey.of(Items.STONE), DIAMOND = AEItemKey.of(Items.DIAMOND);
    static final BlockPos CONTROLLER = new BlockPos(6, 4, 2);
    static final BlockPos PATTERN = new BlockPos(3, 2, 8), EXPANDED = new BlockPos(5, 2, 8);
    static final BlockPos PROCESS = new BlockPos(3, 4, 8), CRYSTAL = new BlockPos(5, 4, 8), ENERGY = new BlockPos(4, 3, 8);

    static final class Store implements MEStorage {
        final Map<AEKey, Long> items = new LinkedHashMap<>();
        boolean accept = true;
        long calls, enumerations, actualExtractions;
        AEKey shortKey;
        long actualLimit = Long.MAX_VALUE;
        Runnable callback;
        long get(AEKey key) { return items.getOrDefault(key, 0L); }
        void put(AEKey key, long amount) { items.put(key, amount); }
        @Override public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            calls++;
            if (!accept) return 0;
            long accepted = Math.min(amount, Long.MAX_VALUE - get(key));
            if (mode == Actionable.MODULATE) items.merge(key, accepted, Math::addExact);
            return accepted;
        }
        @Override public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            calls++;
            long taken = Math.min(amount, get(key));
            if (mode == Actionable.MODULATE) {
                actualExtractions++;
                if (key.equals(shortKey)) taken = Math.min(taken, actualLimit);
                items.put(key, get(key) - taken);
                if (callback != null) { var run = callback; callback = null; run.run(); }
            }
            return taken;
        }
        @Override public void getAvailableStacks(KeyCounter counter) { enumerations++; items.forEach(counter::add); }
        @Override public Component getDescription() { return Component.literal("Factory integration storage"); }
    }
    static final class Fixture {
        final GameTestHelper h;
        final BlockPos offset;
        LargeFactoryControllerBlockEntity controller;
        LargeFactoryHatchBlockEntity hatch, expanded, crystal;
        LargeFactoryAuxBlockEntity process, energy;
        final Store store = new Store();
        Fixture(GameTestHelper h, boolean firmament, boolean pretendStarship) {
            this(h, firmament, pretendStarship, BlockPos.ZERO);
        }
        Fixture(GameTestHelper h, boolean firmament, boolean pretendStarship, BlockPos offset) {
            this.h = h; this.offset = offset;
            for (var cell : LargeFactoryStructure.cells()) {
                var part = switch (cell.role()) {
                    case FRAME -> LargeFactoryComponent.FRAME;
                    case CASING, HATCH -> LargeFactoryComponent.CASING;
                    case CORE -> LargeFactoryComponent.CORE_T1;
                    case CONTROLLER -> LargeFactoryComponent.CONTROLLER;
                    case AIR -> LargeFactoryComponent.AIR;
                };
                var local = cell.localPosition();
                if (local.equals(PATTERN)) part = LargeFactoryComponent.PATTERN_HATCH;
                if (local.equals(EXPANDED)) part = LargeFactoryComponent.EXPANDED_PATTERN_HATCH;
                if (local.equals(PROCESS)) part = LargeFactoryComponent.PROCESS_CORE_HATCH;
                if (local.equals(CRYSTAL)) part = LargeFactoryComponent.CRYSTAL_HATCH;
                if (local.equals(ENERGY)) part = LargeFactoryComponent.ENERGY_HATCH;
                h.setBlock(pos(local), part == LargeFactoryComponent.AIR ? Blocks.AIR : LargeFactoryRegistration.block(part));
            }
            if (firmament) {
                h.setBlock(pos(LargeFactoryStructure.CORE), ModBlocks.FIRMAMENT_CONVERSION_CORE.get());
                if (pretendStarship) {
                    // Only world-generation discovery is replaced; takeover and all runtime paths are production code.
                    try {
                        var f = FirmamentConversionCoreBlockEntity.class.getDeclaredField("insideStarship");
                        f.setAccessible(true); f.set(h.getBlockEntity(pos(LargeFactoryStructure.CORE)), true);
                    } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
                }
            }
            for (var local : List.of(PATTERN, EXPANDED, CRYSTAL)) h.setBlock(pos(local).south(), AEBlocks.CREATIVE_ENERGY_CELL.block());
            controller = h.getBlockEntity(CONTROLLER.offset(offset));
            hatch = h.getBlockEntity(pos(PATTERN)); expanded = h.getBlockEntity(pos(EXPANDED)); crystal = h.getBlockEntity(pos(CRYSTAL));
            process = h.getBlockEntity(pos(PROCESS)); energy = h.getBlockEntity(pos(ENERGY));
        }
        BlockPos pos(BlockPos local) { return LargeFactoryStructure.worldPosition(CONTROLLER.offset(offset), local, Direction.NORTH); }
        void ready(Consumer<Fixture> run) {
            h.startSequence().thenWaitUntil(() -> {
                h.assertTrue(controller.formed(), "formation: " + controller.status() + " / " + controller.lastScan());
                h.assertTrue(hatch.ready() && expanded.ready() && crystal.ready(), "waiting for real powered AE2 hatch nodes");
            }).thenExecute(() -> {
                hatch.getMainNode().getGrid().getStorageService().addGlobalStorageProvider(m -> m.mount(store, 0));
                run.accept(this);
            });
        }
        LargeFactoryHatchBlockEntity.Entry pattern(long operations) {
            var item = PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(STONE, operations)), List.of(new GenericStack(DIAMOND, operations * 2)));
            hatch.inventory().setItemDirect(0, item);
            return hatch.entries().getFirst();
        }
        KeyCounter[] inputs(long count) { var counter = new KeyCounter(); counter.add(STONE, count); return new KeyCounter[]{counter}; }
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void rearHatchesStayIndependentAndInteriorChangesCloseImmediately(GameTestHelper h) {
        new Fixture(h, false, false).ready(f -> {
            h.assertTrue(f.hatch.inventory().size() == 36 && f.expanded.inventory().size() == 144, "36 and 144 real slots");
            h.assertTrue(f.hatch.getMainNode().getGrid() != f.expanded.getMainNode().getGrid(), "adjacent hatches must not bridge external networks");
            h.assertTrue(f.hatch.getGridConnectableSides(null).equals(Set.of(Direction.SOUTH)), "rear connection only");
            var staleEnergy = f.energy.energy();
            h.assertTrue(staleEnergy.receiveEnergy(100, false) == 100, "real external FE capability");
            h.setBlock(f.pos(new BlockPos(2, 2, 2)), Blocks.STONE);
            h.assertTrue(!f.controller.formed() && !f.hatch.ready(), "interior change must revoke execution synchronously");
            h.assertTrue(staleEnergy.receiveEnergy(100, false) == 0, "stale FE handle must stop immediately");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void batchCostsCountSourceOperationsAndReturnsFollowCommit(GameTestHelper h) {
        new Fixture(h, false, false).ready(f -> {
            var entry = f.pattern(4);
            f.store.put(LightningKey.HIGH_VOLTAGE, 100);
            f.controller.receiveEnergy(10_000, false); f.controller.toggleNetworkEnergy();
            var input = f.inputs(4);
            var output = new KeyCounter();
            long accepted = LargeFactoryExecutor.execute(f.hatch, entry, Map.of(STONE, 4L), 8, produced -> {
                h.assertTrue(f.store.get(LightningKey.HIGH_VOLTAGE) == 68, "cost already committed when returns are queued");
                h.assertTrue(f.controller.budget().remainingOperations(h.getLevel().getGameTime()) == 1024 - 32, "one factory-wide operation budget");
                produced.forEach(stack -> output.add(stack.getKey(), stack.getLongValue()));
                return true;
            }, false);
            h.assertTrue(accepted == 8, "eight copies accepted: " + entry.status + " / " + f.hatch.status());
            h.assertTrue(output.get(DIAMOND) == 64 && f.hatch.account().resources.isEmpty(), "exactly 64 deferred products");
            h.assertTrue(f.controller.energyStored() == 9360 && input[0].get(STONE) == 4, "2x source FE and immutable caller template");
            h.assertTrue(f.hatch.account().commitSequence == 1 && f.hatch.account().lastOperations == 32
                    && f.hatch.account().lastEnergyFE == 640 && f.hatch.account().lastHigh == 32, "saved commit audit records the actual accepted cost");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void rejectedSingleOutputSinkRetainsExactlyOneCommittedProduct(GameTestHelper h) {
        new Fixture(h, false, false).ready(f -> {
            var entry = f.pattern(1);
            f.store.put(LightningKey.HIGH_VOLTAGE, 4);
            f.store.accept = false;
            var sink = new com.moakiee.ae2lt.crafting.runtime.api.DeferredCraftingProvider.OutputSink() {
                @Override public boolean enqueue(KeyCounter outputs) { throw new AssertionError("single output must not need a counter"); }
                @Override public boolean enqueue(AEKey key, long amount) {
                    h.assertTrue(key.equals(DIAMOND) && amount == 2, "exact single output");
                    h.assertTrue(f.hatch.account().commitSequence == 1 && f.store.get(LightningKey.HIGH_VOLTAGE) == 3,
                            "ownership and payment committed before the sink callback");
                    return false;
                }
            };
            h.assertTrue(f.hatch.pushPattern(entry.pattern, f.inputs(1), sink), "processing committed despite rejected return");
            h.assertTrue(f.hatch.account().resources.getOrDefault(DIAMOND, 0L) == 2, "rejected output remains owned by factory");
            h.assertTrue(!f.hatch.pushPattern(entry.pattern, f.inputs(1), sink), "retained output blocks the next dispatch");
            h.assertTrue(f.store.get(LightningKey.HIGH_VOLTAGE) == 3, "blocked retry cannot charge again");
            f.store.accept = true;
            h.runAfterDelay(2, () -> {
                f.hatch.flushRetained();
                h.assertTrue(f.store.get(DIAMOND) == 2 && f.hatch.account().empty(), "exactly one later delivery");
                h.succeed();
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void passiveFirstSampleCountsOnceAndReturnsToItsOwnNetwork(GameTestHelper h) {
        new Fixture(h, false, false).ready(f -> {
            f.pattern(1); f.store.put(STONE, 10); f.store.put(LightningKey.HIGH_VOLTAGE, 10);
            f.controller.receiveEnergy(200, false); f.controller.toggleNetworkEnergy(); f.hatch.togglePassive();
            h.assertTrue(f.hatch.passiveStep(), "passive transaction: " + f.hatch.status());
            h.assertTrue(f.store.get(STONE) == 0 && f.store.get(DIAMOND) == 20, "sample is copy one, never an extra production");
            h.assertTrue(f.store.get(LightningKey.HIGH_VOLTAGE) == 0 && f.controller.energyStored() == 0, "ten source operations charged");
            h.assertTrue(f.hatch.account().empty() && f.hatch.getAvailablePatterns().isEmpty(), "passive hatch does not publish CPU patterns");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void cachedBindingSkipsUnfundedInputsAndPreservesPartialRealReceipt(GameTestHelper h) {
        new Fixture(h, false, false).ready(f -> {
            f.pattern(1); f.store.put(STONE, 1); f.store.put(LightningKey.HIGH_VOLTAGE, 1);
            f.controller.receiveEnergy(20, false); f.controller.toggleNetworkEnergy(); f.hatch.togglePassive();
            h.assertTrue(f.hatch.passiveStep(), "initialize binding from one real paid sample");
            f.store.put(STONE, 10); f.store.put(LightningKey.HIGH_VOLTAGE, 10);
            h.runAfterDelay(1, () -> {
                long extractions = f.store.actualExtractions;
                h.assertTrue(!f.hatch.passiveStep() && f.hatch.status().equals("missing_energy") && f.store.actualExtractions == extractions,
                        "known binding with no FE must not take and refund another sample");
                f.controller.receiveEnergy(200, false); f.store.shortKey = STONE; f.store.actualLimit = 2; f.store.accept = false;
                h.assertTrue(!f.hatch.passiveStep(), "cached availability never substitutes for the real receipt");
                h.assertTrue(f.hatch.account().resources.getOrDefault(STONE, 0L) == 2 && f.store.get(STONE) == 8,
                        "only the two actually extracted inputs belong to the retained account: owned=" + f.hatch.account().resources
                                + ", stored=" + f.store.get(STONE) + ", status=" + f.hatch.status());
                h.assertTrue(f.controller.energyStored() == 200 && f.store.get(LightningKey.HIGH_VOLTAGE) == 10
                        && f.store.get(DIAMOND) == 2 && f.hatch.account().commitSequence == 1, "failed batch creates no output and spends no costs");
                h.succeed();
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void changingAnAlreadyScannedCellRestartsFormationBeforeClaim(GameTestHelper h) {
        var f = new Fixture(h, false, false);
        h.runAfterDelay(5, () -> {
            h.setBlock(f.pos(BlockPos.ZERO), Blocks.AIR);
            h.startSequence().thenWaitUntil(() -> h.assertTrue(f.controller.lastScan() != null, "changed scan completes"))
                    .thenExecute(() -> {
                        h.assertTrue(!f.controller.formed() && f.controller.missing()[0] == 1, "stale scanned frame cannot form a factory");
                        h.setBlock(f.pos(BlockPos.ZERO), LargeFactoryRegistration.block(LargeFactoryComponent.FRAME));
                    }).thenWaitUntil(() -> h.assertTrue(f.hatch.ready(), "repaired structure forms through a fresh scan"))
                    .thenSucceed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void partialRealLightningReceiptBecomesOneRecoverableParcel(GameTestHelper h) {
        new Fixture(h, false, false).ready(f -> {
            var entry = f.pattern(4); f.store.put(LightningKey.HIGH_VOLTAGE, 100);
            f.store.shortKey = LightningKey.HIGH_VOLTAGE; f.store.actualLimit = 2; f.store.accept = false;
            f.controller.receiveEnergy(1000, false);
            h.assertTrue(f.hatch.pushPattern(entry.pattern, f.inputs(4)) == false, "partial actual payment must reject job");
            h.assertTrue(f.hatch.account().resources.getOrDefault(LightningKey.HIGH_VOLTAGE, 0L) == 2, "only actual receipt is owned");
            h.assertTrue(f.controller.energyStored() == 1000 && f.controller.budget().remainingOperations(h.getLevel().getGameTime()) == 1024, "failure consumes no FE or operation budget");
            UUID id = f.hatch.accountId(); f.hatch.releaseResources();
            var ledger = LargeFactoryLedger.get(h.getLevel());
            h.assertTrue(ledger.parcel(id) != null && ledger.parcel(id).resources.size() == 1, "single authoritative recovery account");
            f.store.accept = true;
            f.expanded.getMainNode().getGrid().getStorageService().addGlobalStorageProvider(m -> m.mount(new Store(), 0));
            h.assertTrue(f.expanded.recoverParcel(id), "explicit parcel recovery may choose a new network");
            h.assertTrue(ledger.parcel(id) == null && !f.expanded.recoverParcel(id), "duplicated capsule cannot redeem twice");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void synchronousBreakDuringPaymentPreservesTheActualReceipt(GameTestHelper h) {
        new Fixture(h, false, false).ready(f -> {
            var entry = f.pattern(1); f.store.put(LightningKey.HIGH_VOLTAGE, 4);
            f.controller.receiveEnergy(1000, false); UUID id = f.hatch.accountId();
            f.store.callback = () -> h.setBlock(f.pos(PATTERN), Blocks.AIR);
            h.assertTrue(!f.hatch.pushPattern(entry.pattern, f.inputs(1)), "a broken hatch cannot accept CPU inputs");
            var parcel = LargeFactoryLedger.get(h.getLevel()).parcel(id);
            h.assertTrue(parcel != null && parcel.resources.getOrDefault(LightningKey.HIGH_VOLTAGE, 0L) == 1,
                    "onRemove must defer parcel creation until the extraction receipt is recorded");
            h.assertTrue(!parcel.resources.containsKey(DIAMOND), "no product exists before a commit");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void residentCatalystCreatesFluidPatternAndCoreRemovalWithdrawsIt(GameTestHelper h) {
        new Fixture(h, false, false).ready(f -> {
            f.crystal.inventory().setItemDirect(0, new ItemStack(Items.BUDDING_AMETHYST));
            h.assertTrue(f.crystal.entries().isEmpty(), "catalyzer process is locked by default");
            f.process.inventory().setStackInSlot(0, new ItemStack(LargeFactoryRegistration.PROCESS_CORES.get(LargeFactoryRecipeAccess.Process.CATALYZER).get()));
            var entry = f.crystal.entries().stream().filter(e -> e.virtualRecipe.equals(ResourceLocation.parse("ae2lt:crystal_catalyzer/budding_amethyst"))).findFirst().orElseThrow();
            var storage = new Store(); storage.put(LightningKey.HIGH_VOLTAGE, 1);
            f.crystal.getMainNode().getGrid().getStorageService().addGlobalStorageProvider(m -> m.mount(storage, 0));
            f.controller.receiveEnergy(200_000, false);
            // Binding can resume across ticks when the catalogue exceeds its bounded matching quota.
            h.startSequence().thenWaitUntil(() -> {
                var water = AEFluidKey.of(Fluids.WATER);
                var input = new KeyCounter(); input.add(water, 1000);
                h.assertTrue(f.crystal.pushPattern(entry.pattern, new KeyCounter[]{input}, out -> true), "catalyzer binding: " + entry.status);
            }).thenExecute(() -> {
                h.assertTrue(f.crystal.inventory().getStackInSlot(0).is(Items.BUDDING_AMETHYST), "resident catalyst is not CPU input or consumed");
                h.assertTrue(storage.get(LightningKey.HIGH_VOLTAGE) == 0 && f.controller.energyStored() == 0, "one actual source operation");
                f.process.inventory().setStackInSlot(0, ItemStack.EMPTY);
                h.assertTrue(f.crystal.getAvailablePatterns().isEmpty(), "removing process core withdraws virtual recipes immediately");
            }).thenSucceed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void firmamentUses1024SharedOperationsAndOnlyHighToExtremeCompensation(GameTestHelper h) {
        new Fixture(h, true, true).ready(f -> {
            var output = Map.<AEKey, Long>of(DIAMOND, 1L, AEItemKey.of(Items.EMERALD), 2L, AEItemKey.of(Items.IRON_INGOT), 3L, AEItemKey.of(Items.GOLD_INGOT), 4L);
            var pattern = PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(STONE, 1)), LargeFactoryAmounts.stacks(output));
            f.hatch.inventory().setItemDirect(0, pattern); f.store.put(LightningKey.HIGH_VOLTAGE, 4096);
            h.startSequence().thenWaitUntil(() -> h.assertTrue(f.hatch.bindRecipe(f.hatch.entries().getFirst(), Map.of(STONE, 1L)) != null, "bounded firmament recipe binding"))
                    .thenExecute(() -> {
                        var entry = f.hatch.entries().getFirst(); var returned = new KeyCounter();
                        long accepted = LargeFactoryExecutor.execute(f.hatch, entry, Map.of(STONE, 1L), 1025, out -> { out.forEach(e -> returned.add(e.getKey(), e.getLongValue())); return true; }, false);
                        h.assertTrue(accepted == 1024 && returned.get(AEItemKey.of(Items.GOLD_INGOT)) == 4096, "1024 source operations and all four outputs");
                        h.assertTrue(f.store.get(LightningKey.HIGH_VOLTAGE) == 0 && f.controller.budget().remainingOperations(h.getLevel().getGameTime()) == 0, "4096 HV substitutes 1024 EHV, no extra allowance per hatch");
                        h.assertTrue(!f.controller.access().allows(LargeFactoryRecipeAccess.Process.OVERLOAD), "firmament does not inherit basic overload recipes");
                        FirmamentConversionCoreBlockEntity core = h.getBlockEntity(f.pos(LargeFactoryStructure.CORE));
                        h.assertTrue(core.isOwnedByLargeFactory() && core.getAutomationInventory().insertItem(0, new ItemStack(Items.STONE), false).getCount() == 1, "original core automation is closed during takeover");
                    }).thenSucceed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void relocatedFirmamentCoreCannotFormOutsideNaturalStarship(GameTestHelper h) {
        var f = new Fixture(h, true, false);
        h.startSequence().thenWaitUntil(() -> h.assertTrue(f.controller.lastScan() != null, "bounded formation scan finished"))
                .thenExecute(() -> {
            h.assertTrue(!f.controller.formed() && f.controller.lastScan().diagnostics().stream().anyMatch(d -> d.problem() == LargeFactoryStructure.Problem.FIRMAMENT_OUTSIDE_STARSHIP), "world structure restriction remains enforced");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void registeredOptionalRecipeAdaptersUseRealFinalManagerRecipes(GameTestHelper h) {
        var catalog = LargeFactoryRecipes.get(h.getLevel().getRecipeManager()).recipes();
        for (var process : LargeFactoryRecipeAccess.Process.values()) {
            var sourceCount = h.getLevel().getRecipeManager().getRecipes().stream().filter(r -> process.type().equals(net.minecraft.core.registries.BuiltInRegistries.RECIPE_TYPE.getKey(r.value().getType()))).count();
            if (!process.base() && sourceCount > 0) {
                long adapted = catalog.stream().filter(r -> r.process() == process).count();
                h.assertTrue(adapted == sourceCount, "all real " + process + " recipes adapted: " + adapted + "/" + sourceCount);
                com.mojang.logging.LogUtils.getLogger().info("Large factory real recipe adapter {}: {}/{}", process, adapted, sourceCount);
            }
        }
        h.assertTrue(catalog.stream().anyMatch(r -> r.process() == LargeFactoryRecipeAccess.Process.REACTION), "AdvancedAE reaction catalogue present");
        for (var recipe : catalog) {
            String getter = switch (recipe.process()) {
                case INTEGRATED_WORKSTATION -> "energy";
                case CRYSTAL_AGGREGATOR, CRYSTAL_PULVERIZER, CIRCUIT_ETCHER -> "energyCost";
                default -> null;
            };
            if (getter == null && recipe.process() != LargeFactoryRecipeAccess.Process.CRYSTAL_ASSEMBLER) continue;
            try {
                var source = h.getLevel().getRecipeManager().byKey(recipe.id()).orElseThrow().value();
                double nativeAE = getter == null ? 2000 : ((Number) source.getClass().getMethod(getter).invoke(source)).doubleValue();
                if (recipe.process() == LargeFactoryRecipeAccess.Process.INTEGRATED_WORKSTATION || getter == null)
                    nativeAE = appeng.api.config.PowerMultiplier.CONFIG.multiply(nativeAE);
                long expectedFE = (long) Math.ceil(appeng.api.config.PowerUnit.AE.convertTo(appeng.api.config.PowerUnit.FE, nativeAE));
                h.assertTrue(recipe.energy() == expectedFE, "native AE costs must be normalized to FE for " + recipe.id());
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void unlimitedT4AcceptsLargeBatchesWhileStillChargingRealResources(GameTestHelper h) {
        var fixture = new Fixture(h, false, false);
        h.setBlock(fixture.pos(LargeFactoryStructure.CORE), LargeFactoryRegistration.block(LargeFactoryComponent.CORE_T4));
        fixture.ready(f -> {
            h.assertTrue(LargeFactoryConfig.operations(LargeFactoryComponent.CORE_T1) == 1024
                    && LargeFactoryConfig.operations(LargeFactoryComponent.CORE_T2) == 16_384
                    && LargeFactoryConfig.operations(LargeFactoryComponent.CORE_T3) == 262_144
                    && LargeFactoryConfig.operations(LargeFactoryComponent.CORE_T4) == LargeFactoryOperationBudget.UNLIMITED,
                    "new default core capacities are loaded");
            var entry = f.pattern(1);
            f.controller.receiveEnergy(100_000_000, false); f.controller.toggleNetworkEnergy();
            f.store.put(LightningKey.HIGH_VOLTAGE, 2_000_000);
            var output = new KeyCounter();
            long accepted = LargeFactoryExecutor.execute(f.hatch, entry, Map.of(STONE, 1L), Long.MAX_VALUE, produced -> {
                produced.forEach(stack -> output.add(stack.getKey(), stack.getLongValue())); return true;
            }, false);
            h.assertTrue(accepted == 2_000_000 && output.get(DIAMOND) == 4_000_000,
                    "T4 exceeds the old cap and accepts only the resource-funded part");
            h.assertTrue(f.store.get(LightningKey.HIGH_VOLTAGE) == 0 && f.controller.energyStored() == 60_000_000,
                    "unlimited operation budget still pays every lightning and FE cost");
            h.assertTrue(f.controller.budget().remainingOperations(h.getLevel().getGameTime()) == LargeFactoryOperationBudget.UNLIMITED,
                    "completed work cannot exhaust an unlimited core");
            h.assertTrue(LargeFactoryExecutor.execute(f.hatch, entry, Map.of(STONE, 1L), 1, produced -> true, false) == 0,
                    "unlimited core refuses unpaid work");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void ordinaryRecipesUseOnlyTheirOwnMeEnergyAndExternalOnlyStopsNewWithdrawals(GameTestHelper h) {
        new Fixture(h, false, false).ready(f -> {
            var entry = f.pattern(1); f.store.put(LightningKey.HIGH_VOLTAGE, 2);
            f.controller.toggleNetworkEnergy();
            h.assertTrue(!f.hatch.pushPattern(entry.pattern, f.inputs(1)), "external-only mode cannot borrow idle ME energy");
            h.assertTrue(f.store.get(LightningKey.HIGH_VOLTAGE) == 2, "quote failure does not charge Lightning");
            f.controller.toggleNetworkEnergy();
            h.assertTrue(f.hatch.pushPattern(entry.pattern, f.inputs(1), out -> true), "automatic ME energy must power a recipe with no external FE");
            h.assertTrue(f.store.get(LightningKey.HIGH_VOLTAGE) == 1 && f.hatch.account().energyCreditAE == 0, "ME cost commits once without keeping a reusable credit");
            h.assertTrue(f.controller.energyStored() == 0 && f.expanded.account().empty(), "other hatch and external buffer remain separate");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void blockedProductsDoNotFollowAHatchIntoANewNetwork(GameTestHelper h) {
        new Fixture(h, false, false).ready(f -> {
            var entry = f.pattern(1); f.store.put(LightningKey.HIGH_VOLTAGE, 1); f.store.accept = false;
            h.assertTrue(f.hatch.pushPattern(entry.pattern, f.inputs(1)), "production accepted before normal CPU return");
            var original = f.hatch.account().origin;
            h.setBlock(f.pos(PATTERN).south(), Blocks.AIR);
            h.runAfterDelay(2, () -> h.setBlock(f.pos(PATTERN).south(), AEBlocks.CREATIVE_ENERGY_CELL.block()));
            h.startSequence().thenWaitUntil(() -> {
                h.assertTrue(h.getTick() > 25 && f.hatch.ready(), "new network reconnects");
                h.assertTrue(!f.hatch.getMainNode().getGrid().getService(LargeFactoryNetworkIdentity.class).contains(original), "replacement node is a different external anchor");
            }).thenExecute(() -> {
                var newStore = new Store(); f.hatch.getMainNode().getGrid().getStorageService().addGlobalStorageProvider(m -> m.mount(newStore, 0));
                f.hatch.flushRetained();
                h.assertTrue(newStore.get(DIAMOND) == 0 && f.hatch.account().resources.getOrDefault(DIAMOND, 0L) == 2,
                        "owned outputs stay with their original network until explicit recovery");
            }).thenSucceed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void failedStructureAndReformationKeepTheSameExternalReturnAnchor(GameTestHelper h) {
        new Fixture(h, false, false).ready(f -> {
            var entry = f.pattern(1); f.store.put(LightningKey.HIGH_VOLTAGE, 1); f.store.accept = false;
            h.assertTrue(f.hatch.pushPattern(entry.pattern, f.inputs(1)), "retained product fixture");
            var origin = f.hatch.account().origin;
            var corner = f.pos(BlockPos.ZERO);
            h.setBlock(corner, Blocks.AIR);
            h.runAfterDelay(3, () -> h.setBlock(corner, LargeFactoryRegistration.block(LargeFactoryComponent.FRAME)));
            h.startSequence().thenWaitUntil(() -> {
                h.assertTrue(h.getTick() > 25 && f.hatch.ready(), "same factory reconnects");
                h.assertTrue(f.hatch.getMainNode().getGrid().getService(LargeFactoryNetworkIdentity.class).contains(origin), "external anchor survives grid split and merge");
            }).thenExecute(() -> {
                f.store.accept = true;
                // Global test mounts belong to a particular grid instance; remount if AE2 chose the other grid during merge.
                var service = f.hatch.getMainNode().getGrid().getStorageService(); service.addGlobalStorageProvider(m -> m.mount(f.store, 0));
                f.hatch.flushRetained();
            }).thenWaitUntil(() -> {
                h.assertTrue(f.store.get(DIAMOND) == 2 && f.hatch.account().empty(), "return survives disassembly without re-execution");
            }).thenSucceed();
        });
    }

    @GameTest(template = "empty", batch = "large_factory_reload", timeoutTicks = 300)
    public static void recipeReplacementRevokesCachedSuccessBeforeAnyPayment(GameTestHelper h) {
        new Fixture(h, false, false).ready(f -> {
            var entry = f.pattern(1); f.store.put(LightningKey.HIGH_VOLTAGE, 10);
            h.assertTrue(f.hatch.bindRecipe(entry, Map.of(STONE, 1L)) != null, "initial successful binding");
            var manager = h.getLevel().getRecipeManager(); var original = List.copyOf(manager.getRecipes());
            try {
                manager.replaceRecipes(original.stream().filter(r -> !r.id().equals(ResourceLocation.parse("a_large_factory:base"))).toList());
                h.assertTrue(!f.hatch.pushPattern(entry.pattern, f.inputs(1)), "removed source cannot execute through cached pattern identity");
                h.assertTrue(f.store.get(LightningKey.HIGH_VOLTAGE) == 10 && f.hatch.account().empty(), "rejected stale recipe neither charges nor produces");
            } finally { manager.replaceRecipes(original); }
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void realTimeWheelCpuFinishesDependentProcessingChainInOneTick(GameTestHelper h) {
        new Fixture(h, false, false).ready(f -> {
            var first = f.pattern(1).pattern;
            var emerald = AEItemKey.of(Items.EMERALD);
            f.hatch.inventory().setItemDirect(1, PatternDetailsHelper.encodeProcessingPattern(
                    List.of(new GenericStack(DIAMOND, 1)), List.of(new GenericStack(emerald, 3))));
            var second = PatternDetailsHelper.decodePattern(f.hatch.inventory().getStackInSlot(1), h.getLevel());
            f.store.put(STONE, 2); f.store.put(LightningKey.HIGH_VOLTAGE, 6);
            f.controller.receiveEnergy(120, false); f.controller.toggleNetworkEnergy();
            var grid = f.hatch.getMainNode().getGrid();
            var host = new com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCpuHost() {
                @Override public boolean isCpuActive() { return true; }
                @Override public appeng.api.networking.IGrid getGrid() { return grid; }
                @Override public IActionSource getActionSource() { return IActionSource.empty(); }
                @Override public net.minecraft.world.level.Level getLevel() { return h.getLevel(); }
                @Override public void markCpuDirty() { }
                @Override public Component getDisplayName() { return Component.literal("Large factory CPU"); }
            };
            var cpu = new com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCPU(host, Long.MAX_VALUE, 31, Long.MAX_VALUE, false);
            var used = new KeyCounter(); used.add(STONE, 2);
            var plan = new appeng.crafting.CraftingPlan(new GenericStack(emerald, 12), 100, false, false,
                    used, new KeyCounter(), new KeyCounter(), Map.of(first, 2L, second, 4L));
            var crafting = (appeng.me.service.CraftingService) grid.getCraftingService();
            h.startSequence().thenWaitUntil(() -> h.assertTrue(crafting.getProviders(first).iterator().hasNext()
                            && crafting.getProviders(second).iterator().hasNext(), "real AE2 pattern publication"))
                    .thenExecute(() -> {
                        var result = cpu.getCraftingLogic().trySubmitJob(grid, plan, IActionSource.empty(), null);
                        h.assertTrue(result.successful(), "real CPU submit: " + result);
                        long tick = h.getLevel().getGameTime();
                        cpu.getCraftingLogic().tickCraftingLogic(grid.getEnergyService(), (appeng.me.service.CraftingService) grid.getCraftingService(), 32, Long.MAX_VALUE);
                        h.assertTrue(!cpu.getCraftingLogic().hasJob() && !cpu.isBusy(), "dependent chain must fully finish in one CPU pass");
                        h.assertTrue(h.getLevel().getGameTime() == tick && f.store.get(emerald) == 12, "same-tick physical products reach ME storage once");
                        h.assertTrue(f.store.get(STONE) == 0 && f.store.get(DIAMOND) == 0 && f.store.get(LightningKey.HIGH_VOLTAGE) == 0, "chain materials and six fees settle exactly");
                        h.assertTrue(f.hatch.account().empty() && f.controller.energyStored() == 0, "no duplicated deferred products or leftover FE");
                    }).thenSucceed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void ledgerRoundTripAndDuplicatedBlockNbtCannotCopyOwnedEnergy(GameTestHelper h) {
        new Fixture(h, false, false).ready(f -> {
            f.controller.receiveEnergy(1000, false);
            var ledger = LargeFactoryLedger.get(h.getLevel());
            var saved = ledger.save(new CompoundTag(), h.getLevel().registryAccess());
            try {
                var load = LargeFactoryLedger.class.getDeclaredMethod("load", CompoundTag.class, HolderLookup.Provider.class);
                load.setAccessible(true);
                var restored = (LargeFactoryLedger) load.invoke(null, saved, h.getLevel().registryAccess());
                var position = f.controller.getBlockPos();
                h.assertTrue(restored.claim(f.controller.machineId(), h.getLevel(), position).externalFE == 1000, "authoritative FE survives SavedData round trip");
                h.assertTrue(restored.claim(f.controller.machineId(), h.getLevel(), position.east()) == null, "same copied identity cannot claim resources at a new location");
                var block = f.controller.saveWithoutMetadata(h.getLevel().registryAccess());
                h.assertTrue(!block.contains("ExternalFE"), "block NBT must not contain a second spendable FE amount");
                f.controller.releaseEnergy();
                var parcel = ledger.parcel(f.controller.machineId());
                h.assertTrue(parcel != null && parcel.energyCreditAE == appeng.api.config.PowerUnit.FE.convertTo(appeng.api.config.PowerUnit.AE, 1000), "controller removal retains exact recoverable energy");
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    @SuppressWarnings("unchecked")
    public static void builderUsesActualInventoryAndHonorsCancelledPlacements(GameTestHelper h) {
        h.setBlock(CONTROLLER, LargeFactoryRegistration.block(LargeFactoryComponent.CONTROLLER));
        LargeFactoryControllerBlockEntity controller = h.getBlockEntity(CONTROLLER);
        var level = h.getLevel();
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "FactoryBuilderQA"));
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.setPos(controller.getBlockPos().getX() + .5, controller.getBlockPos().getY(), controller.getBlockPos().getZ() - 1.5);
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND));
        player.getInventory().setItem(8, new ItemStack(LargeFactoryRegistration.block(LargeFactoryComponent.FRAME), 2));
        var protectedPos = LargeFactoryStructure.worldPosition(controller.getBlockPos(), BlockPos.ZERO, Direction.NORTH);
        Consumer<net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent> denied = event -> {
            if (event.getLevel() == level && event.getPos().equals(protectedPos)) event.setCanceled(true);
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(denied);
        Map<UUID, net.minecraft.server.level.ServerPlayer> players;
        try {
            var field = net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID"); field.setAccessible(true);
            players = (Map<UUID, net.minecraft.server.level.ServerPlayer>) field.get(player.server.getPlayerList());
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        players.put(player.getUUID(), player);
        try {
            var builder = new LargeFactoryBuilder(); builder.start(player); builder.tick(controller);
            h.assertTrue(level.getBlockState(protectedPos).isAir(), "cancelled placement cannot bypass protection");
            int placed = 0;
            for (var cell : LargeFactoryStructure.cells()) if (level.getBlockState(LargeFactoryStructure.worldPosition(controller.getBlockPos(), cell.localPosition(), Direction.NORTH))
                    .is(LargeFactoryRegistration.block(LargeFactoryComponent.FRAME))) placed++;
            h.assertTrue(placed == 2 && player.getInventory().getItem(8).isEmpty(), "only two real frame items placed");
            h.assertTrue(player.getMainHandItem().is(Items.DIAMOND), "held item restored after building");
            h.assertTrue(LargeFactoryWorkBudget.snapshot(level)[LargeFactoryWorkBudget.Work.BUILD.ordinal()] <= 16, "bounded per-tick builder work");
        } finally { players.remove(player.getUUID()); net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(denied); }
        h.succeed();
    }

    @GameTest(template = "wide", batch = "large_factory_performance", timeoutTicks = 800)
    public static void boundedWorkOn16FactoriesAnd32768NetworkKeys(GameTestHelper h) {
        var factories = new ArrayList<Fixture>();
        for (int z = 0; z < 4; z++) for (int x = 0; x < 4; x++) factories.add(new Fixture(h, false, false, new BlockPos(x * 16, 0, z * 16)));
        h.startSequence().thenWaitUntil(() -> h.assertTrue(factories.stream().allMatch(f -> f.expanded.ready()), "16 independent factories formed and powered"))
                .thenExecute(() -> {
                    var irrelevant = new ArrayList<AEKey>();
                    for (int i = 0; i < 2048; i++) {
                        var item = new ItemStack(Items.PAPER); final int index = i;
                        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, item, tag -> tag.putInt("FactoryPerfKey", index));
                        irrelevant.add(AEItemKey.of(item));
                    }
                    for (var f : factories) {
                        for (var key : irrelevant) f.store.put(key, 64);
                        f.expanded.getMainNode().getGrid().getStorageService().addGlobalStorageProvider(m -> m.mount(f.store, 0));
                        for (int slot = 0; slot < 144; slot++) f.expanded.inventory().setItemDirect(slot, PatternDetailsHelper.encodeProcessingPattern(
                                List.of(new GenericStack(STONE, slot + 1)), List.of(new GenericStack(DIAMOND, 2L * (slot + 1)))));
                        f.expanded.togglePassive();
                    }
                }).thenWaitUntil(() -> h.assertTrue(factories.stream().noneMatch(f -> f.expanded.patternIndexing()), "all 2304 patterns indexed"))
                .thenExecute(() -> new PerformanceRun(h, factories).sample());
    }

    private static final class PerformanceRun {
        final GameTestHelper h; final List<Fixture> factories;
        final com.google.gson.JsonArray results = new com.google.gson.JsonArray();
        final List<Long> durations = new ArrayList<>();
        final List<Long> singleDurations = new ArrayList<>();
        final Map<String, Long> completeTickDurations = new java.util.HashMap<>();
        final Map<String, Long> sectionTotals = new java.util.HashMap<>();
        final Map<BlockPos, Integer> owners = new java.util.HashMap<>();
        long coldPeak;
        final java.lang.reflect.Field passiveFlag;
        int phase, tick; long calls, enumerations, produced, gcStart;
        PerformanceRun(GameTestHelper h, List<Fixture> factories) {
            this.h = h; this.factories = factories; gcStart = gc();
            try { passiveFlag = LargeFactoryHatchBlockEntity.class.getDeclaredField("passive"); passiveFlag.setAccessible(true); }
            catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            // Pause only automatic scheduling between samples so measured work includes the actual production.
            // The production passive executor, quotas, matching and every real AE2 call remain unchanged.
            factories.forEach(f -> mode(f, false));
            for (int index = 0; index < factories.size(); index++) {
                var f = factories.get(index);
                owners.put(f.controller.getBlockPos(), index);
                for (var hatch : f.controller.hatches()) owners.put(hatch.getBlockPos(), index);
            }
            LargeFactoryTiming.setReceiver((host, section, nanos) -> {
                var owner = owners.get(host.getBlockPos());
                if (owner == null || tick <= 5) return;
                String key = phase + ":" + host.getLevel().getGameTime() + ":" + owner;
                completeTickDurations.merge(key, nanos, Long::sum);
                sectionTotals.merge(section, nanos, Long::sum);
            });
        }
        void mode(Fixture f, boolean enabled) {
            try { passiveFlag.setBoolean(f.expanded, enabled); }
            catch (IllegalAccessException e) { throw new AssertionError(e); }
        }
        static long gc() { return java.lang.management.ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(bean -> Math.max(0, bean.getCollectionTime())).sum(); }
        void sample() {
            long beforeCalls = factories.stream().mapToLong(f -> f.store.calls).sum();
            long beforeEnumerations = factories.stream().mapToLong(f -> f.store.enumerations).sum();
            long beforeOutput = factories.stream().mapToLong(f -> f.store.get(DIAMOND)).sum();
            long start = System.nanoTime();
            for (var f : factories) {
                long singleStart = System.nanoTime();
                long diagnostic = LargeFactoryTiming.begin();
                mode(f, phase != 1);
                try { for (int i = 0; i < 64; i++) {
                    if (phase == 1) f.expanded.flushRetained(); else f.expanded.passiveStep();
                } } finally { mode(f, false); LargeFactoryTiming.end(f.controller, "stress_64_calls", diagnostic); }
                long singleElapsed = System.nanoTime() - singleStart;
                if (tick >= 5) singleDurations.add(singleElapsed); else coldPeak = Math.max(coldPeak, singleElapsed);
            }
            long elapsed = System.nanoTime() - start;
            long newCalls = factories.stream().mapToLong(f -> f.store.calls).sum() - beforeCalls;
            long newEnumerations = factories.stream().mapToLong(f -> f.store.enumerations).sum() - beforeEnumerations;
            h.assertTrue(newEnumerations == 0, "factory hot path must not enumerate the network");
            h.assertTrue(LargeFactoryWorkBudget.snapshot(h.getLevel())[LargeFactoryWorkBudget.Work.NETWORK.ordinal()] <= 4096, "global actual network-call quota");
            if (tick++ >= 5) { durations.add(elapsed); calls += newCalls; enumerations += newEnumerations;
                produced += factories.stream().mapToLong(f -> f.store.get(DIAMOND)).sum() - beforeOutput; }
            if (durations.size() == 32) {
                durations.sort(Long::compare);
                var result = new com.google.gson.JsonObject();
                result.addProperty("scenario", List.of("missing_inputs", "full_output", "continuous_completions").get(phase));
                result.addProperty("factories", 16); result.addProperty("patterns", 2304); result.addProperty("network_keys", 32768);
                result.addProperty("warmup_ticks", 5); result.addProperty("sample_ticks", 32);
                result.addProperty("incremental_mean_ms", durations.stream().mapToLong(Long::longValue).average().orElseThrow() / 1_000_000.0);
                result.addProperty("incremental_p95_ms", durations.get(30) / 1_000_000.0);
                result.addProperty("incremental_max_ms", durations.get(31) / 1_000_000.0);
                singleDurations.sort(Long::compare);
                result.addProperty("single_factory_64_calls_mean_us", singleDurations.stream().mapToLong(Long::longValue).average().orElseThrow() / 1000.0);
                result.addProperty("single_factory_64_calls_p95_us", singleDurations.get((int) (singleDurations.size() * .95)) / 1000.0);
                result.addProperty("single_factory_64_calls_max_us", singleDurations.getLast() / 1000.0);
                result.addProperty("cold_factory_64_calls_max_us", coldPeak / 1000.0);
                var totals = new ArrayList<>(completeTickDurations.values()); totals.sort(Long::compare);
                if (!totals.isEmpty()) {
                    result.addProperty("complete_factory_tick_mean_us", totals.stream().mapToLong(Long::longValue).average().orElseThrow() / 1000.0);
                    result.addProperty("complete_factory_tick_p95_us", totals.get((int) (totals.size() * .95)) / 1000.0);
                    result.addProperty("complete_factory_tick_max_us", totals.getLast() / 1000.0);
                    result.addProperty("complete_factory_ticks_over_50us", totals.stream().filter(n -> n > 50_000).count());
                }
                result.add("profile_sections_total_ns", new com.google.gson.Gson().toJsonTree(sectionTotals));
                result.addProperty("actual_storage_calls", calls); result.addProperty("full_inventory_enumerations", enumerations);
                result.addProperty("produced_items_during_measured_work", produced);
                if (phase == 2) h.assertTrue(produced > 0, "continuous completion samples must include actual production");
                result.addProperty("process_gc_ms", gc() - gcStart); results.add(result);
                if (++phase == 3) {
                    LargeFactoryTiming.setReceiver(null);
                    try { java.nio.file.Files.writeString(java.nio.file.Path.of("large-factory-performance.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(results) + "\n"); }
                    catch (java.io.IOException e) { throw new AssertionError(e); }
                    h.succeed(); return;
                }
                for (var f : factories) {
                    if (phase == 1) {
                        // Produce one real retained output before measuring blocked return attempts.
                        f.store.put(LightningKey.HIGH_VOLTAGE, 1); f.store.accept = false;
                    } else {
                        f.store.accept = true; f.expanded.flushRetained();
                        f.store.put(STONE, 10_000_000); f.store.put(LightningKey.HIGH_VOLTAGE, 10_000_000);
                    }
                }
                durations.clear(); singleDurations.clear(); completeTickDurations.clear(); sectionTotals.clear(); coldPeak = 0;
                tick = 0; calls = 0; enumerations = 0; produced = 0; gcStart = gc();
            }
            h.runAfterDelay(1, () -> {
                if (phase == 1) for (var f : factories) if (f.expanded.account().resources.isEmpty()) {
                    var entry = f.expanded.entries().getFirst();
                    f.expanded.pushPattern(entry.pattern, f.inputs(1));
                }
                sample();
            });
        }
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void passiveInputsCanComeFromAnActualAe2StorageBus(GameTestHelper h) {
        var f = new Fixture(h, false, false);
        var level = h.getLevel();
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
        var cable = h.absolutePos(f.pos(PATTERN).south());
        level.setBlockAndUpdate(cable, Blocks.AIR.defaultBlockState());
        appeng.api.parts.PartHelper.setPart(level, cable, null, player,
                appeng.core.definitions.AEParts.GLASS_CABLE.item(appeng.api.util.AEColor.TRANSPARENT));
        appeng.api.parts.PartHelper.setPart(level, cable, Direction.SOUTH, player, appeng.core.definitions.AEParts.STORAGE_BUS.asItem());
        level.setBlockAndUpdate(cable.below(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        level.setBlockAndUpdate(cable.south(), Blocks.BARREL.defaultBlockState());
        var barrel = (net.minecraft.world.level.block.entity.BarrelBlockEntity) level.getBlockEntity(cable.south());
        barrel.setItem(0, new ItemStack(Items.STONE, 10));
        f.ready(ignored -> {
            f.pattern(1); f.store.put(LightningKey.HIGH_VOLTAGE, 10); f.controller.receiveEnergy(200, false);
            f.controller.toggleNetworkEnergy(); f.hatch.togglePassive();
            h.startSequence().thenWaitUntil(() -> h.assertTrue(f.hatch.passiveStep(), "real storage bus material receipt"))
                    .thenExecute(() -> {
                        long stones = 0, diamonds = f.store.get(DIAMOND);
                        for (int slot = 0; slot < barrel.getContainerSize(); slot++) {
                            var item = barrel.getItem(slot);
                            if (item.is(Items.STONE)) stones += item.getCount();
                            if (item.is(Items.DIAMOND)) diamonds += item.getCount();
                        }
                        h.assertTrue(stones == 0 && diamonds == 20, "real bus withdrew ten inputs and returned twenty products once");
                        h.assertTrue(f.store.get(LightningKey.HIGH_VOLTAGE) == 0 && f.controller.energyStored() == 0, "bus path preserves recipe costs");
                    }).thenSucceed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void all144PatternSlotsSurviveSaveAndAreDroppedOnRemoval(GameTestHelper h) {
        new Fixture(h, false, false).ready(f -> {
            for (int slot = 0; slot < 144; slot++) f.expanded.inventory().setItemDirect(slot, PatternDetailsHelper.encodeProcessingPattern(
                    List.of(new GenericStack(STONE, slot + 1)), List.of(new GenericStack(DIAMOND, 2L * (slot + 1)))));
            var tag = f.expanded.saveWithoutMetadata(h.getLevel().registryAccess());
            var restored = new LargeFactoryHatchBlockEntity(f.expanded.getBlockPos(), f.expanded.getBlockState());
            restored.loadWithComponents(tag, h.getLevel().registryAccess());
            h.assertTrue(restored.inventory().size() == 144, "expanded capacity survives a normal block entity save");
            for (int slot = 0; slot < 144; slot++) h.assertTrue(ItemStack.matches(f.expanded.inventory().getStackInSlot(slot), restored.inventory().getStackInSlot(slot)), "saved page slot " + slot);
            var drops = new ArrayList<ItemStack>();
            f.expanded.addAdditionalDrops(h.getLevel(), f.expanded.getBlockPos(), drops);
            h.assertTrue(drops.stream().filter(PatternDetailsHelper::isEncodedPattern).count() == 144, "all hidden pages are included in removal drops");
            f.expanded.clearContent();
            h.assertTrue(f.expanded.getAvailablePatterns().isEmpty(), "cleared inventory withdraws every page");
            h.succeed();
        });
    }

    @GameTest(template = "wide", timeoutTicks = 300)
    public static void copiedControllerIdentityCannotFormAnotherSpendableFactory(GameTestHelper h) {
        var original = new Fixture(h, false, false);
        original.controller.energyStored(); // The original location owns its central energy account before either scan.
        var copied = new Fixture(h, false, false, new BlockPos(16, 0, 0));
        copied.controller.loadWithComponents(original.controller.saveWithoutMetadata(h.getLevel().registryAccess()), h.getLevel().registryAccess());
        h.startSequence().thenWaitUntil(() -> {
            h.assertTrue(original.hatch.ready(), "original factory remains operational");
            h.assertTrue(copied.controller.status().equals("identity_conflict"), "copied identity is detected at formation");
        }).thenExecute(() -> {
            h.assertTrue(!copied.controller.formed() && !copied.controller.enterExecution(), "duplicate controller cannot reach even zero-FE commit paths");
        }).thenSucceed();
    }
}
