package qa;

import java.lang.reflect.*;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.*;
import appeng.api.config.Actionable;
import appeng.api.networking.*;
import appeng.api.networking.crafting.*;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import appeng.crafting.*;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import appeng.me.storage.NetworkStorage;
import appeng.me.storage.DelegatingMEInventory;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Either;
import com.moakiee.ae2lt.compat.gto.GtoCpuJobAccess;
import com.moakiee.ae2lt.gametest.*;
import com.moakiee.thunderbolt.api.crafting.*;
import com.moakiee.thunderbolt.api.storage.BigMEStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

@Mod("gto_compat_qa")
public final class GtoCompatQA {
    static { org.apache.logging.log4j.core.config.Configurator.setLevel("qa",org.apache.logging.log4j.Level.INFO); }
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private final GameTestTicker ticker = new GameTestTicker();
    private Collection<GameTestInfo> tests;
    private boolean reported;
    private int startupFailures;
    private List<TestFunction> pendingTests;
    private static final int MAX_TEST_CHUNKS_IN_FLIGHT = 32;
    private static final int TEST_CHUNK_REQUESTS_PER_TICK = 8;
    private final Deque<ChunkPos> pendingTestChunks = new ArrayDeque<>();
    private final Map<ChunkPos, CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> testChunks = new LinkedHashMap<>();
    private int expectedTestChunks;
    private int readyTestChunks;
    private CompletableFuture<Void> plannerCheck;
    private int preparationTicks;
    private static final net.minecraft.resources.ResourceLocation QA_ENGINE_ID = new net.minecraft.resources.ResourceLocation("gto_compat_qa","integration");
    private static final java.util.concurrent.atomic.AtomicInteger ENGINE_CALLS = new java.util.concurrent.atomic.AtomicInteger();
    public GtoCompatQA() {
        if (Boolean.getBoolean("ae2lt.gtoExceptionTrace")) {
            // Keep packet and protocol logging off: only a failure should write a stack.
            var context = (org.apache.logging.log4j.core.LoggerContext)
                    org.apache.logging.log4j.LogManager.getContext(false);
            var logger = new org.apache.logging.log4j.core.config.LoggerConfig(
                    "net.minecraft.network.Connection", org.apache.logging.log4j.Level.DEBUG, true);
            logger.addFilter(new org.apache.logging.log4j.core.filter.AbstractFilter() {
                @Override
                public org.apache.logging.log4j.core.Filter.Result filter(org.apache.logging.log4j.core.LogEvent event) {
                    return event.getThrown() != null || event.getLevel().isMoreSpecificThan(org.apache.logging.log4j.Level.INFO)
                            ? org.apache.logging.log4j.core.Filter.Result.NEUTRAL
                            : org.apache.logging.log4j.core.Filter.Result.DENY;
                }
            });
            context.getConfiguration().addLogger(logger.getName(), logger);
            context.updateLoggers();
        }
        if (Boolean.getBoolean("ae2lt.gtoLoginTrace") && !Boolean.getBoolean("ae2lt.gtoQuietLoginTrace")) {
            org.apache.logging.log4j.core.config.Configurator.setLevel("net.minecraftforge.network", org.apache.logging.log4j.Level.DEBUG);
            org.apache.logging.log4j.core.config.Configurator.setLevel("net.minecraft.network.Connection", org.apache.logging.log4j.Level.DEBUG);
        }
        if ("infinite".equals(System.getProperty("ae2lt.gtoQaSuite"))) {
            net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus()
                    .addListener((net.minecraftforge.registries.RegisterEvent event) -> {
                        var registry = appeng.api.stacks.AEKeyTypesInternal.getRegistry();
                        if (registry != null && java.util.Objects.equals(event.getForgeRegistry(), registry)) {
                            com.moakiee.ae2lt.debug.InfiniteCellRegressionGameTests.registerTestChannel();
                        }
                    });
        }
        if (Boolean.getBoolean("ae2lt.emiMultiblockProbe")) {
            try {
                Class.forName("com.moakiee.ae2lt.integration.emi.EmiMultiblockClientProbe")
                        .getMethod("register").invoke(null);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot register the EMI client probe", failure);
            }
        }
        CraftingPlanningEngines.register(new CraftingPlanningEngine() {
            public net.minecraft.resources.ResourceLocation id(){return QA_ENGINE_ID;}
            public boolean check(IGrid grid,PlanningRequest request){return true;}
            public PlanningEngineSession createSession(PlanningRequest request,Object captured,PlanningAttemptContext context){
                return new PlanningEngineSession(){
                    public PlanningAttempt attempt(long amount,boolean simulate,PlanningAttemptContext ctx){ENGINE_CALLS.incrementAndGet();return PlanningAttempt.handled(plan(request.output(),amount));}
                };
            }
        },0,false);
        MinecraftForge.EVENT_BUS.addListener(this::start);
        MinecraftForge.EVENT_BUS.addListener(this::tick);
    }
    private void start(ServerStartedEvent e) {
        try {
            var suite = System.getProperty("ae2lt.gtoQaSuite", "base");
            // Client/FX/restart probes have their own lifecycle. Do not launch an unrelated
            // planner before their first player joins when no GameTest suite was requested.
            if ("none".equals(suite)) return;
            // GTO skips all CraftingService ticks until a player has joined.
            // Headless fixtures must enable that scheduling gate to exercise
            // native waiting-key refresh and output routing without a client.
            if (!"none".equals(System.getProperty("ae2lt.gtoQaSuite", "base"))) {
                appeng.hooks.ticking.TickHandler.instance().playerJoined = true;
            }
            try { checkCpu(); } catch(Throwable failure) { startupFailures++; LOGGER.error("GTO_COMPAT_QA CPU FAIL",failure); }
            try { checkStorage(); } catch(Throwable failure) { startupFailures++; LOGGER.error("GTO_COMPAT_QA STORAGE FAIL",failure); }
            validateUpstreamSuite(suite);
            switch (suite) {
                case "eaep" -> {
                    if (!net.minecraftforge.fml.ModList.get().isLoaded("extendedae_plus"))
                        throw new IllegalStateException("EAEP runtime fixture requires its real Forge release");
                    GameTestRegistry.register(GtoEaepGameTests.class);
                }
                case "neoeco" -> GameTestRegistry.register(com.moakiee.ae2lt.crafting.big.NeoEcoGtoGameTests.class);
                case "core5-planning", "core5-ownership", "core5-boundaries" -> registerFixtures("crafting.big.CoreAlgorithmAcceptanceGameTests");
                case "permissions" -> GameTestRegistry.register(
                        com.moakiee.ae2lt.network.FrequencyPermissionsGameTests.class);
                case "machine" -> {
                    if (net.minecraftforge.fml.ModList.get().isLoaded("industrialforegoing"))
                        GameTestRegistry.register(com.moakiee.ae2lt.debug.ProviderMachineAdaptationGameTests.class);
                    else LOGGER.info("GTO_COMPAT_QA SKIP Industrial Foregoing machine tests (mod absent)");
                    GameTestRegistry.register(com.moakiee.ae2lt.debug.ProviderMekanismReturnGameTests.class);
                }
                case "useless" -> {
                    if (net.minecraftforge.fml.ModList.get().isLoaded("useless_mod")) {
                        GameTestRegistry.register(com.moakiee.ae2lt.crafting.big.UselessBigIntegerGameTests.class);
                        GameTestRegistry.register(GtoUselessCpuGameTests.class);
                    } else LOGGER.info("GTO_COMPAT_QA SKIP Useless Mod bigint tests (mod absent)");
                }
                case "dispatch" -> {
                    GameTestRegistry.register(com.moakiee.ae2lt.crafting.big.BigCandidateIndexGameTests.class);
                    GameTestRegistry.register(com.moakiee.ae2lt.crafting.big.BigZeroTickGameTests.class);
                    GameTestRegistry.register(com.moakiee.ae2lt.debug.TianshuDeferredReturnsGameTests.class);
                }
                case "base" -> {
                    GameTestRegistry.register(GtoCpuGameTests.class);
                    GameTestRegistry.register(TimeWheelCraftingStatusGameTests.class);
                    GameTestRegistry.register(ClosedLoopSharedSeedBatchGameTests.class);
                }
                case "exact" -> {
                    GameTestRegistry.register(com.moakiee.ae2lt.crafting.big.BigCraftingReportGameTests.class);
                    GameTestRegistry.register(com.moakiee.ae2lt.debug.BigIntegerPipelineGameTests.class);
                    GameTestRegistry.register(com.moakiee.ae2lt.crafting.big.BigMixedProviderGameTests.class);
                }
                case "interfaces" -> GameTestRegistry.register(
                        com.moakiee.ae2lt.blockentity.OverloadedInterfaceExportFairnessGameTests.class);
                case "io" -> GameTestRegistry.register(
                        com.moakiee.ae2lt.debug.OverloadedIOPortGameTests.class);
                case "infinite" -> GameTestRegistry.register(
                        com.moakiee.ae2lt.debug.InfiniteCellRegressionGameTests.class);
                case "recipes" -> GameTestRegistry.register(GtoRecipeGameTests.class);
                case "content" -> GameTestRegistry.register(GtoContentGameTests.class);
                case "content-recipes" -> {
                    GameTestRegistry.register(GtoContentGameTests.class);
                    GameTestRegistry.register(GtoRecipeGameTests.class);
                }
                case "batch" -> GameTestRegistry.register(com.moakiee.ae2lt.debug.GlobalBatchAdaptersGameTests.class);
                case "upstream-content" -> registerFixtures(
                        "gametest.UpstreamPortGameTests", "gametest.LightningCollectorGameTests",
                        "gametest.OverloadedIOPortUpgradeGameTests", "blockentity.LightningCollectorGameTests",
                        "debug.UpstreamReleaseRecipeGameTests");
                case "upstream-resources" -> registerFixtures(
                        "blockentity.AdjacentFluidExportReceiptGameTests", "debug.ManualInputTransferGameTests",
                        "debug.PigmeeAdjacentStorageGameTests", "logic.provider.ReturnInventoryLimitGameTests");
                case "upstream-nightvision" -> registerFixtures("debug.NightVisionArmorGameTests");
                // Resource reload changes the server-wide recipe/loot catalog: run it alone.
                case "upstream-reload" -> registerFixtures("blockentity.CrystalHarvestReloadGameTests");
                case "upstream-catalyzer" -> registerFixtures("blockentity.PigmeeCrystalCatalyzerGameTests");
                case "upstream-catalyzer-neoeco", "upstream-catalyzer-absent" -> registerFixtures("debug.NeoEcoCatalyzerGameTests");
                case "upstream-native-fixes" -> registerFixtures("blockentity.UpstreamFixGameTests");
                case "upstream-mining", "upstream-mining-benchmark" -> registerFixtures("blockentity.MiningFactoryStressGameTests");
                case "upstream-interface", "upstream-interface-slow" -> registerFixtures("blockentity.WirelessInterfaceGameTests");
                case "upstream-energy-api" -> registerFixtures("logic.energy.EnergyApiStressGameTests");
                case "upstream-energy-routing" -> registerFixtures("debug.InterfaceEnergyRoutingGameTests", "debug.ProviderEnergyOutputGameTests");
                case "upstream-wireless" -> registerFixtures("logic.energy.WirelessEnergyGameTests");
                case "upstream-node-serialization" -> registerFixtures("logic.energy.WirelessEnergyNodeSerializationGameTests");
                case "upstream-admission", "upstream-admission-slow" -> registerFixtures("logic.energy.WirelessEnergyAdmissionGameTests");
                case "upstream-wireless-boundary", "upstream-wireless-boundary-slow", "upstream-wireless-dispatch" ->
                        registerFixtures("logic.energy.WirelessEnergyBoundaryGameTests");
                case "upstream-fallback" -> registerFixtures("logic.energy.WirelessAeFallbackGameTests");
                case "upstream-management" -> registerFixtures("logic.energy.NetworkManagementGameTests");
                case "upstream-upload" -> registerFixtures("debug.TianshuPatternUploadGameTests",
                        "gametest.TianshuQuantumBridgeGameTests", "gametest.TianshuUploadRetentionGameTests",
                        "gametest.TianshuWirelessBindingGameTests", "gametest.WirelessJeiSupplyGameTests");
                case "upstream-request-routing" -> registerFixtures("crafting.big.CraftingRequestRoutingGameTests");
                case "upstream-omni", "upstream-omni-absent" -> registerFixtures("debug.OmniversalTerminalGameTests");
                case "upstream-omni-upgrades" -> registerFixtures("logic.energy.OmniUpgradesGameTests");
                case "upstream-migration" -> registerFixtures("logic.craft.migration.PatternMigrationGameTests");
                case "upstream-migration-slow" -> registerFixtures("logic.craft.migration.PatternMigrationGameTests", "debug.PatternMigrationPerformanceGameTests");
                case "upstream-actual" -> registerFixtures("debug.ActualCompatibilityGameTests");
                case "upstream-appgen" -> registerFixtures("debug.AppGenProcessorGameTests");
                case "upstream-mgu" -> registerFixtures("debug.MguConveyorPhaseLockGameTests");
                case "upstream-object-reuse" -> registerFixtures("gametest.ObjectReuseGameTests");
                case "upstream-batch-native" -> registerFixtures("debug.GlobalBatchAdaptersGameTests");
                case "none" -> { }
                default -> throw new IllegalArgumentException("Unknown GTO QA suite: " + suite);
            }
            // Some fixtures construct multiblocks beyond the small empty template. Give every
            // test its own area so concurrently ticking fixtures cannot overwrite one another.
            pendingTests = new ArrayList<>();
            for (var function : GameTestRegistry.getAllTestFunctions()) {
                var name = function.getTestName().toLowerCase(Locale.ROOT);
                if (!includeUpstreamTest(suite, function)) continue;
                var filter = System.getProperty("ae2lt.gtoQaTestFilter");
                if (filter != null && !name.contains(filter.toLowerCase(Locale.ROOT))) continue;
                if (name.contains("infinitereactionfactory") && !net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(
                        new net.minecraft.resources.ResourceLocation("mekanism_extras", "infinite_pressurised_reacting_factory"))) {
                    LOGGER.info("GTO_COMPAT_QA SKIP " + function.getTestName() + " (optional reaction factory absent)");
                    continue;
                }
                if (!"upstream-object-reuse".equals(suite) && !"upstream-mining-benchmark".equals(suite)
                        && !Boolean.getBoolean("ae2lt.exportFairnessBenchmark")
                        && function.getTestName().toLowerCase(Locale.ROOT).contains("benchmark")) continue;
                pendingTests.add(function);
            }
            if (!"none".equals(suite) && pendingTests.isEmpty()) {
                throw new IllegalStateException("No tests registered for suite " + suite
                        + " and filter " + System.getProperty("ae2lt.gtoQaTestFilter", "<all>"));
            }
            validateWirelessChunkCoverage(suite, pendingTests);
            LOGGER.info("GTO_COMPAT_QA PREPARE suite="+suite+" tests="+pendingTests.size());
        } catch(Throwable failure) {
            startupFailures++;
            reported = true;
            LOGGER.error("QA failure",failure);
            LOGGER.info("GTO_COMPAT_QA FAIL startup "+failure);
        }
    }
    private static void registerFixtures(String... classes) {
        for (var name : classes) {
            try {
                GameTestRegistry.register(Class.forName("com.moakiee.ae2lt." + name));
            } catch (ClassNotFoundException failure) {
                throw new IllegalStateException("Missing opt-in QA fixture " + name, failure);
            }
        }
    }

    /** Native suites must fail setup instead of counting a fixture's absent-mod early return as a pass. */
    private static void validateUpstreamSuite(String suite) {
        switch (suite) {
            case "upstream-native-fixes" -> requireMods(suite, "appflux", "advanced_ae", "silentgear");
            case "upstream-mining", "upstream-mining-benchmark" -> requireMods(suite, "silentgear");
            case "upstream-energy-api", "upstream-energy-routing", "upstream-wireless", "upstream-fallback",
                    "upstream-management" -> requireMods(suite, "appflux");
            case "upstream-admission", "upstream-admission-slow", "upstream-wireless-boundary",
                    "upstream-wireless-boundary-slow", "upstream-wireless-dispatch" -> requireMods(suite, "appflux", "mekanism");
            case "upstream-upload" -> requireMods(suite, "ae2wtlib", "jei");
            case "upstream-omni" -> {
                requireMods(suite, "useless_mod", "ae2wtlib");
                if (!com.moakiee.ae2lt.integration.useless.UselessModCompat.isLoaded())
                    throw new IllegalStateException(suite + " requires the native omniversal pattern item");
            }
            case "upstream-omni-absent" -> requireAbsent(suite, "useless_mod");
            case "upstream-migration" -> requireMods(suite, "expatternprovider", "extendedae_plus", "neoecoae");
            case "upstream-migration-slow" -> requireMods(suite, "expatternprovider", "extendedae_plus");
            case "upstream-catalyzer-neoeco" -> {
                requireMods(suite, "neoecoae");
                requireProperty(suite, "ae2lt.neoecoTestPresent");
            }
            case "upstream-catalyzer-absent" -> {
                requireAbsent(suite, "neoecoae");
                if (Boolean.getBoolean("ae2lt.neoecoTestPresent"))
                    throw new IllegalStateException(suite + " requires ae2lt.neoecoTestPresent=false");
            }
            case "upstream-actual" -> {
                requireMods(suite, "neoecoae", "extendedae_plus", "ldlib", "expatternprovider");
                requireProperty(suite, "ae2lt.neoecoTestPresent");
            }
            case "upstream-appgen" -> requireMods(suite, "appgen", "advanced_ae");
            case "upstream-mgu" -> requireMods(suite, "mob_grinding_utils");
            case "upstream-omni-upgrades" -> {
                try {
                    Class.forName("com.sakurain.omni_upgrades.compat.CompatManager", false, GtoCompatQA.class.getClassLoader());
                } catch (ClassNotFoundException failure) {
                    throw new IllegalStateException(suite + " requires the real Omni Upgrades mod", failure);
                }
            }
            case "upstream-batch-native" -> {
                requireMods(suite, "neoecoae", "extendedae_plus");
                requireProperty(suite, "ae2lt.neoecoBatchTestRequired");
            }
            default -> { }
        }
    }

    private static void requireMods(String suite, String... mods) {
        for (var mod : mods) {
            if (!net.minecraftforge.fml.ModList.get().isLoaded(mod))
                throw new IllegalStateException(suite + " requires real mod " + mod + "; no tests were run");
            if ("appflux".equals(mod) && !com.moakiee.ae2lt.logic.energy.AppFluxBridge.canUseEnergyHandler())
                throw new IllegalStateException(suite + " requires a working native Applied Flux energy API");
        }
    }

    private static void requireAbsent(String suite, String mod) {
        if (net.minecraftforge.fml.ModList.get().isLoaded(mod))
            throw new IllegalStateException(suite + " tests the absent-mod boundary and requires " + mod + " to be removed");
    }

    private static void requireProperty(String suite, String property) {
        if (!Boolean.getBoolean(property)) throw new IllegalStateException(suite + " requires -D" + property + "=true");
    }

    /** Renaming the fast boundary must not silently drop it or the real disk lifecycle coverage. */
    private static void validateWirelessChunkCoverage(String suite, List<TestFunction> functions) {
        if (!System.getProperty("ae2lt.gtoQaTestFilter", "").isBlank()) return;
        int count, ticks;
        String method;
        switch (suite) {
            case "upstream-wireless" -> {
                count = 19;
                ticks = 120;
                method = "chunkInaccessibilityPreservesBindingAndResumesSupply";
            }
            case "upstream-admission-slow" -> {
                count = 3;
                ticks = 1600;
                method = "repeatedDiskChunkUnloadReloadPreservesBindingWithoutProbe";
            }
            default -> { return; }
        }
        long matching = functions.stream().filter(function -> function.getMaxTicks() == ticks
                && matchesTestMethod(function.getTestName().toLowerCase(Locale.ROOT), method.toLowerCase(Locale.ROOT))).count();
        if (functions.size() != count || matching != 1)
            throw new IllegalStateException(suite + " requires " + count + " tests including " + method
                    + " at " + ticks + " ticks; selected=" + functions.size() + ", matching=" + matching);
    }

    private static boolean includeUpstreamTest(String suite, TestFunction function) {
        var name = function.getTestName().toLowerCase(Locale.ROOT);
        boolean slow = function.getMaxTicks() > 300;
        return switch (suite) {
            case "core5-planning" -> name.startsWith("planning") || name.contains(".planning");
            case "core5-ownership" -> name.startsWith("ownership") || name.contains(".ownership");
            case "upstream-interface", "upstream-admission", "upstream-migration" -> !slow;
            case "upstream-interface-slow", "upstream-admission-slow", "upstream-migration-slow",
                    "upstream-wireless-boundary-slow" -> slow;
            case "upstream-wireless-boundary" -> !slow && !matchesTestMethod(name, "slowsimulationdefersworkbutbothreceiverseventuallygetpower");
            // This fixture temporarily replaces the world-wide endpoints data and must run alone.
            case "upstream-wireless-dispatch" -> matchesTestMethod(name, "slowsimulationdefersworkbutbothreceiverseventuallygetpower");
            case "upstream-mining" -> !matchesTestMethod(name, "controlledminingthroughputbenchmark");
            case "upstream-mining-benchmark" -> matchesTestMethod(name, "controlledminingthroughputbenchmark");
            case "upstream-omni" -> !matchesTestMethod(name, "optionalboundary");
            case "upstream-omni-absent" -> matchesTestMethod(name, "optionalboundary");
            default -> true;
        };
    }

    private static boolean matchesTestMethod(String name, String method) {
        // Accept GTO's bare method names as well as class-qualified Forge test names.
        return name.equals(method) || name.endsWith("." + method);
    }
    private int loginTraceTicks;
    private void tick(TickEvent.ServerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && ++loginTraceTicks % 100 == 0)
            GtoNetworkDiagnostics.inspect(e.getServer().getConnection());
        if(e.phase!=TickEvent.Phase.END || reported) return;
        if (tests == null && pendingTests != null) {
            try {
                prepareTests(e.getServer());
            } catch (Throwable failure) {
                reported = true;
                LOGGER.error("QA preparation failure", failure);
                LOGGER.info("GTO_COMPAT_QA FAIL preparation " + failure);
            }
        }
        if (tests == null || reported) return;
        ticker.tick();
        if(tests.stream().allMatch(GameTestInfo::isDone)) {
            reported=true;
            long failed=tests.stream().filter(GameTestInfo::hasFailed).count();
            for(var test:tests) {
                LOGGER.info("GTO_COMPAT_QA TEST "+test.getTestName()+" "+(test.hasSucceeded()?"PASS":"FAIL "+test.getError()));
                if(test.getError()!=null) LOGGER.error("Game test failure",test.getError());
            }
            LOGGER.info("GTO_COMPAT_QA "+(failed==0 && startupFailures==0?"PASS":"FAIL")+" total="+tests.size()+" failed="+failed+" startupFailures="+startupFailures);
        }
    }
    private void prepareTests(MinecraftServer server) {
        if (++preparationTicks > 1000) throw new IllegalStateException("QA chunks/planner did not settle: ready="
                + readyTestChunks + "/" + expectedTestChunks + ", queued=" + pendingTestChunks.size()
                + ", inFlight=" + testChunks.size());
        var level = server.overworld();
        var chunkSource = level.getChunkSource();
        if (preparationTicks == 1) {
            for (int index = 0; index < pendingTests.size(); index++) {
                // Keep each test's original 5x5 area and the extra useless-mod fixture areas.
                for (int z : new int[] {0, 300, 400}) {
                    if (z != 0 && !"useless".equals(System.getProperty("ae2lt.gtoQaSuite"))) continue;
                    for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                        pendingTestChunks.addLast(new ChunkPos(index * 64 + dx, (z >> 4) + dz));
                    }
                }
            }
            expectedTestChunks = pendingTestChunks.size();
        }
        for (var iterator = testChunks.entrySet().iterator(); iterator.hasNext();) {
            var request = iterator.next();
            var future = request.getValue();
            if (!future.isDone()) continue;
            Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure> loaded;
            try {
                loaded = future.getNow(null);
            } catch (CompletionException | CancellationException failure) {
                throw new IllegalStateException("QA chunk request failed at " + request.getKey(), failure);
            }
            if (loaded == null || loaded.left().isEmpty())
                throw new IllegalStateException("QA chunk did not load at " + request.getKey() + ": " + loaded);
            var pos = request.getKey();
            // A completed FULL future must also be visible to synchronous structure placement.
            if (chunkSource.getChunkNow(pos.x, pos.z) == null) continue;
            readyTestChunks++;
            iterator.remove();
        }
        for (int submitted = 0; submitted < TEST_CHUNK_REQUESTS_PER_TICK
                && testChunks.size() < MAX_TEST_CHUNKS_IN_FLIGHT && !pendingTestChunks.isEmpty(); submitted++) {
            var pos = pendingTestChunks.removeFirst();
            chunkSource.addRegionTicket(TicketType.FORCED, pos, 2, pos);
            // On the server thread getChunkFuture itself calls managedBlock. Its public
            // off-thread branch schedules on the server executor and returns without waiting.
            testChunks.put(pos, CompletableFuture.supplyAsync(() ->
                    chunkSource.getChunkFuture(pos.x, pos.z, ChunkStatus.FULL, true))
                    .thenCompose(future -> future));
        }
        if (!pendingTestChunks.isEmpty() || !testChunks.isEmpty()) return;
        check(readyTestChunks == expectedTestChunks, "QA chunk preparation count mismatch");
        if (plannerCheck == null) {
            LOGGER.info("GTO_COMPAT_QA CHUNKS ready={} ticks={}", readyTestChunks, preparationTicks);
            plannerCheck = CompletableFuture.runAsync(() -> {
                try { checkPlanner(server); }
                catch (Exception failure) { throw new CompletionException(failure); }
            });
            return;
        }
        if (!plannerCheck.isDone()) return;
        try { plannerCheck.getNow(null); }
        catch (CompletionException failure) {
            startupFailures++;
            LOGGER.error("GTO_COMPAT_QA PLANNER FAIL", failure.getCause());
        }
        tests = new ArrayList<>();
        int index = 0;
        for (var function : pendingTests) {
            tests.addAll(GameTestRunner.runTests(List.of(function), new BlockPos(index++ * 1024, 100, 0),
                    Rotation.NONE, level, ticker, 1));
        }
        LOGGER.info("GTO_COMPAT_QA START suite=" + System.getProperty("ae2lt.gtoQaSuite", "base")
                + " tests=" + tests.size());
    }
    private static void check(boolean condition,String message) {if(!condition)throw new AssertionError(message);}
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.util.function.Function<Method,Object> function) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(p,m,a)->function.apply(m));
    }
    private static CraftingPlan plan(AEKey key,long count) {
        return new CraftingPlan(new GenericStack(key,count),1,false,false,new KeyCounter(),new KeyCounter(),new KeyCounter(),new Object2LongOpenHashMap<>());
    }
    private static void checkCpu() throws Exception {
        var key=AEItemKey.of(Items.DIAMOND);
        var cpu=new CraftingCPUCluster(BlockPos.ZERO,BlockPos.ZERO);
        check(cpu.craftingLogic instanceof com.moakiee.ae2lt.compat.gto.GtoCompatibleCraftingCpuLogic,"compatible GTO CPU not installed");
        Object nativeLogic = ((com.moakiee.ae2lt.compat.gto.GtoCompatibleCraftingCpuLogic) cpu.craftingLogic).nativeDelegate();
        if (net.minecraftforge.fml.ModList.get().isLoaded("neoecoae")) {
            check(nativeLogic.getClass().getName().equals("cn.dancingsnow.neoecoae.compat.gto.ECOGtoCraftingCpuLogic"),
                    "LT bridge displaced the NeoECO GTO CPU delegate");
        }
        if (nativeLogic.getClass().getName().equals("cn.dancingsnow.neoecoae.compat.gto.ECOGtoCraftingCpuLogic")) {
            var delegate = nativeLogic.getClass().getDeclaredField("delegate");
            delegate.setAccessible(true); nativeLogic = delegate.get(nativeLogic);
        }
        Field field = null;
        for (var candidate : nativeLogic.getClass().getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(candidate.getModifiers())) continue;
            for (var member : candidate.getType().getDeclaredFields()) {
                if (member.getType() == appeng.crafting.CraftingLink.class) { field = candidate; break; }
            }
        }
        check(field != null, "native GTO job ledger missing");
        var jobType = field.getType();
        var ctor=Arrays.stream(jobType.getDeclaredConstructors()).filter(c->c.getParameterCount()==4).findFirst().orElseThrow();
        ctor.setAccessible(true);
        var job=ctor.newInstance(plan(key,100),(ListCraftingInventory.ChangeListener)changed->{},null,null);
        field.setAccessible(true);field.set(nativeLogic,job);
        Field remaining = null;
        try (var input = jobType.getResourceAsStream("/"+jobType.getName().replace('.', '/')+".class")) {
            var node = new org.objectweb.asm.tree.ClassNode();
            new org.objectweb.asm.ClassReader(input).accept(node, 0);
            for (var method : node.methods) for (var instruction : method.instructions) {
                if (!(instruction instanceof org.objectweb.asm.tree.LdcInsnNode constant)
                        || !"remainingAmount".equals(constant.cst)) continue;
                for (var next=instruction.getNext();next!=null;next=next.getNext()) {
                    if (next instanceof org.objectweb.asm.tree.MethodInsnNode) break;
                    if (next instanceof org.objectweb.asm.tree.FieldInsnNode member
                            && member.getOpcode()==org.objectweb.asm.Opcodes.GETFIELD && member.desc.equals("J")) {
                        remaining=jobType.getDeclaredField(member.name); break;
                    }
                }
            }
        }
        check(remaining!=null,"persisted remaining output contract missing");
        remaining.setAccessible(true);remaining.setLong(job,30);
        check(GtoCpuJobAccess.remaining(cpu.craftingLogic)==30,"remaining output bridge failed");
        var service=proxy(ICraftingService.class,m->m.getName().equals("getCpus")?ImmutableSet.of(cpu):null);
        var inflight=Class.forName("com.moakiee.ae2lt.logic.interfaces.OverloadedInterfaceCrafting").getDeclaredMethod("inFlightAmounts",ICraftingService.class);
        inflight.setAccessible(true);
        check(((KeyCounter)inflight.invoke(null,service)).get(key)==30,"stocking used original total instead of remaining output");
        LOGGER.info("GTO_COMPAT_QA CPU PASS native job/remaining-output bridge");
    }
    private static void checkStorage() {
        var key=AEItemKey.of(Items.DIAMOND);var source=IActionSource.empty();
        var leaf=new MEStorage(){
            long amount=19;
            public Component getDescription(){return Component.literal("QA stock");}
            public long extract(AEKey k,long n,Actionable mode,IActionSource src){long got=k==key?Math.min(n,amount):0;if(mode==Actionable.MODULATE)amount-=got;return got;}
            public void getAvailableStacks(KeyCounter out){out.add(key,amount);}
            public Object getResourceIdentity(){return this;}
        };
        var base=new NetworkStorage();base.mount(0,leaf);
        var left=new NetworkStorage();left.mount(0,new DelegatingMEInventory(base));
        var right=new NetworkStorage();right.mount(0,new DelegatingMEInventory(base));
        var root=new NetworkStorage();root.mount(0,new DelegatingMEInventory(left));root.mount(0,new DelegatingMEInventory(right));
        var big=(BigMEStorage)(Object)root;
        check(big.extractBig(key,BigInteger.valueOf(100),Actionable.SIMULATE,source).longValueExact()==19,"shared simulation double counted");
        check(big.snapshotBig(source).get(key).longValueExact()==19,"shared snapshot double counted");
        check(big.extractBig(key,BigInteger.valueOf(100),Actionable.MODULATE,source).longValueExact()==19,"shared extraction mismatch");
        check(big.snapshotBig(source).isEmpty(),"extraction did not drain shared storage");
        var first=new BigCell(key);var second=new BigCell(key);
        var direct=new NetworkStorage();direct.mount(0,first);direct.mount(0,second);
        var directBig=(BigMEStorage)(Object)direct;var huge=BigInteger.TEN.pow(30);
        check(directBig.snapshotBig(source).get(key).equals(huge.multiply(BigInteger.TWO)),"direct exact storage was truncated");
        check(directBig.extractBig(key,BigInteger.ONE,Actionable.MODULATE,source).equals(BigInteger.ONE),"direct extraction failed");
        check(first.amount.equals(huge) && second.amount.equals(huge.subtract(BigInteger.ONE)),"equal-priority extraction order differs from GTO");
        check(directBig.insertBig(key,BigInteger.ONE,Actionable.MODULATE,source).equals(BigInteger.ONE),"direct insertion failed");
        check(first.amount.equals(huge.add(BigInteger.ONE)),"equal-priority insertion order differs from GTO");
        LOGGER.info("GTO_COMPAT_QA STORAGE PASS shared graph deduplication, direct exact amounts, and native mount order");
    }
    private static final class BigCell implements MEStorage,BigMEStorage {
        final AEKey key;BigInteger amount=BigInteger.TEN.pow(30);
        BigCell(AEKey key){this.key=key;}
        public Component getDescription(){return Component.literal("QA exact storage");}
        public BigInteger insertBig(AEKey key,BigInteger count,Actionable mode,IActionSource source){if(key!=this.key)return BigInteger.ZERO;if(mode==Actionable.MODULATE)amount=amount.add(count);return count;}
        public BigInteger extractBig(AEKey key,BigInteger count,Actionable mode,IActionSource source){if(key!=this.key)return BigInteger.ZERO;var taken=count.min(amount);if(mode==Actionable.MODULATE)amount=amount.subtract(taken);return taken;}
        public Map<AEKey,BigInteger> snapshotBig(IActionSource source){return Map.of(key,amount);}
    }
    private static void checkPlanner(MinecraftServer server) throws Exception {
        var storage=proxy(IStorageService.class,m->m.getName().equals("getCachedInventory")?new KeyCounter():m.getName().equals("getInventory")?new NetworkStorage():null);
        var energy=proxy(IEnergyService.class,m->null);
        var resolutions=new java.util.concurrent.atomic.AtomicInteger();
        var selected=new java.util.concurrent.atomic.AtomicReference<List<PlanningChoice>>(List.of(PlanningChoice.VANILLA));
        var choices=proxy(ICraftingPlanningService.class,m->{resolutions.incrementAndGet();return selected.get();});
        var service=new CraftingService[1];
        var grid=(IGrid)Proxy.newProxyInstance(IGrid.class.getClassLoader(),new Class<?>[]{IGrid.class},(p,m,a)->switch(m.getName()){
            case "getStorageService"->storage;case "getEnergyService"->energy;case "getCraftingService"->service[0];
            case "getService"->a[0]==ICraftingPlanningService.class?choices:null;
            case "getMachines"->List.of();case "getPivot"->proxy(IGridNode.class,m2->m2.getName().equals("getLevel")?server.overworld():null);default->null;});
        service[0]=new CraftingService(grid,storage,energy);
        var future=server.submit(() -> service[0].beginCraftingCalculation(server.overworld(),()->null,
                AEItemKey.of(Items.DIAMOND),1,CalculationStrategy.REPORT_MISSING_ITEMS)).get(10,TimeUnit.SECONDS);
        var result=future.get(10,TimeUnit.SECONDS);
        check(result!=null,"native fallback returned no plan");
        check(resolutions.get()==1,"TB calculation wrapper did not run");
        check(result.simulation(),"empty network should report native missing-items simulation");
        selected.set(List.of(PlanningChoice.engine(QA_ENGINE_ID),PlanningChoice.VANILLA));
        var engineFuture=server.submit(() -> service[0].beginCraftingCalculation(server.overworld(),()->null,
                AEItemKey.of(Items.DIAMOND),7,CalculationStrategy.REPORT_MISSING_ITEMS)).get(10,TimeUnit.SECONDS);
        var engineResult=engineFuture.get(10,TimeUnit.SECONDS);
        check(!engineResult.simulation() && engineResult.finalOutput().amount()==7 && ENGINE_CALLS.get()==1,"TB candidate was not selected through GTO service");
        check(engineResult.patternTimes() instanceof Object2LongOpenHashMap,"native CPU needs concrete fastutil map");
        LOGGER.info("GTO_COMPAT_QA PLANNER PASS transformed service, selected TB candidate, and native GTO fallback");
    }
}
