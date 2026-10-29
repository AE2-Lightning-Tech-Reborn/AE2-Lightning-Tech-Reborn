package com.moakiee.ae2lt.crafting.big;

import appeng.api.networking.*;
import appeng.api.networking.crafting.*;
import appeng.api.networking.security.*;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.*;
import appeng.api.storage.ISubMenuHost;
import appeng.crafting.CraftingPlan;
import appeng.menu.ISubMenu;
import appeng.menu.me.crafting.*;
import com.google.common.collect.ImmutableSet;
import com.moakiee.ae2lt.blockentity.*;
import com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCpuPool;
import com.moakiee.ae2lt.logic.tianshu.*;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.thunderbolt.api.crafting.*;
import com.moakiee.thunderbolt.core.crafting.algorithm.CraftingAlgorithmCalculationStatus;
import com.moakiee.thunderbolt.core.crafting.planner.ThunderboltV2PlanningEngine;
import java.lang.reflect.*;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;

@GameTestHolder("ae2lt_request_routing")
@PrefixGameTestTemplate(false)
public final class CraftingRequestRoutingGameTests {
    private static final BigInteger OVER_LONG = BigInteger.ONE.shiftLeft(63);
    private static final AEKey TARGET = AEItemKey.of(Items.IRON_INGOT);

    @GameTest(templateNamespace = "ae2lt_io", template = "wireless_io_empty")
    public static void unselectedOrMissingCpuCannotEnableExtendedInputOrExactPlanning(GameTestHelper h) {
        var f = new Fixture(h);
        for (int state = 0; state < 3; state++) {
            f.hasCpu = state != 0;
            f.choices = state == 1 ? List.of(PlanningChoice.VANILLA)
                    : state == 2 ? List.of(PlanningChoice.engine(new ResourceLocation("test:preferred")),
                            PlanningChoice.engine(ThunderboltV2PlanningEngine.ID), PlanningChoice.VANILLA)
                    : List.of(PlanningChoice.engine(ThunderboltV2PlanningEngine.ID), PlanningChoice.VANILLA);
            var input = (BigAmountMenu) new CraftAmountMenu(100 + state, f.player.getInventory(), f.host);
            h.assertTrue(!input.ae2lt$longAvailable() && !input.ae2lt$bigAvailable(), "unauthorized input state " + state);
            var menu = f.menu();
            h.assertTrue(!((CraftingRequestMenu) menu).thunderbolt$planRequest(TARGET, OVER_LONG, CalculationStrategy.CRAFT_LESS),
                    "exact entry bypassed algorithm/CPU selection " + state);
            h.assertTrue(!((CraftingRequestMenu) menu).thunderbolt$isExactRequest(), "rejected request changed mode");
        }
        f.hasCpu = true;
        f.choices = List.of(PlanningChoice.engine(ThunderboltV2PlanningEngine.ID), PlanningChoice.VANILLA);
        var liveMenu = new CraftAmountMenu(109, f.player.getInventory(), f.host);
        var liveInput = (BigAmountMenu) liveMenu;
        h.assertTrue(liveInput.ae2lt$longAvailable() && liveInput.ae2lt$bigAvailable(), "eligible input unavailable");
        f.choices = List.of(PlanningChoice.VANILLA);
        liveMenu.broadcastChanges();
        h.assertTrue(!liveInput.ae2lt$longAvailable() && !liveInput.ae2lt$bigAvailable(),
                "open amount menu retained access after deselecting V2");
        f.choices = List.of(PlanningChoice.engine(ThunderboltV2PlanningEngine.ID), PlanningChoice.VANILLA);
        f.hasCpu = false;
        liveMenu.broadcastChanges();
        h.assertTrue(!liveInput.ae2lt$longAvailable() && !liveInput.ae2lt$bigAvailable(),
                "open amount menu retained access after removing Tianshu");
        f.hasCpu = true;
        liveMenu.broadcastChanges();
        h.assertTrue(liveInput.ae2lt$longAvailable() && liveInput.ae2lt$bigAvailable(),
                "open amount menu did not restore eligible access");
        h.succeed();
    }

    @GameTest(templateNamespace = "ae2lt_io", template = "wireless_io_empty")
    public static void longRequestsKeepNormalServiceAndCpuAdmissionAcrossIntBoundary(GameTestHelper h) throws Exception {
        var f = new Fixture(h);
        f.multidimensional = false;
        var input = (BigAmountMenu) new CraftAmountMenu(110, f.player.getInventory(), f.host);
        h.assertTrue(input.ae2lt$longAvailable() && !input.ae2lt$bigAvailable(), "ordinary Tianshu range incorrect");
        for (long amount : new long[]{2_100_000_000L, Integer.MAX_VALUE, 2_147_483_648L, 3_000_000_000L, Long.MAX_VALUE}) {
            var menu = f.menu();
            h.assertTrue(((CraftingRequestMenu) menu).thunderbolt$planRequest(TARGET, BigInteger.valueOf(amount),
                    CalculationStrategy.CRAFT_LESS), "request rejected: " + amount);
            h.assertTrue(f.observed == amount, "quantity truncated at service boundary: " + amount + " -> " + f.observed);
            h.assertTrue(!((CraftingRequestMenu) menu).thunderbolt$isExactRequest(), "long request forced exact mode");
            var future = (Future<ICraftingPlan>) field(menu, "job");
            h.assertTrue(ThunderboltV2PlanningEngine.ID.equals(CraftingAlgorithmCalculationStatus.selected(future)),
                    "normal algorithm tracking was bypassed");
            set(menu, "result", future.get());
            h.assertTrue(cpuMatches(menu, f.ordinaryCpu), "ordinary CPU filtered out for long request");
            menu.replan();
            h.assertTrue(f.observed == amount, "replan truncated long request");
            menu.planJob(TARGET, 17, CalculationStrategy.CRAFT_LESS);
            h.assertTrue(f.observed == 17, "fresh int request reused a previous long quantity");
            menu.removed(f.player);
        }
        h.assertTrue(!((CraftingRequestMenu) f.menu()).thunderbolt$planRequest(TARGET, OVER_LONG, CalculationStrategy.CRAFT_LESS),
                "ordinary Tianshu accepted an unsupported exact request");
        h.succeed();
    }

    @GameTest(templateNamespace = "ae2lt_io", template = "wireless_io_empty", timeoutTicks = 100)
    public static void overLongUsesRegisteredBackendAndOnlyItsSupportedCpu(GameTestHelper h) throws Exception {
        var f = new Fixture(h);
        var menu = f.menu();
        var input = (BigAmountMenu) new CraftAmountMenu(120, f.player.getInventory(), f.host);
        h.assertTrue(input.ae2lt$longAvailable() && input.ae2lt$bigAvailable(), "eligible exact input unavailable");
        h.assertTrue(((CraftingRequestMenu) menu).thunderbolt$planRequest(TARGET, OVER_LONG, CalculationStrategy.CRAFT_LESS),
                "selected exact backend was not used");
        h.assertTrue(((CraftingRequestMenu) menu).thunderbolt$isExactRequest(), "over-long request stayed native");
        h.assertTrue(f.normalCalls == 0, "exact amount was projected into long planner");
        h.assertTrue(!cpuMatches(menu, f.ordinaryCpu) && cpuMatches(menu, f.pool), "exact CPU admission incorrect");
        var future = (Future<ICraftingPlan>) field(menu, "job");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone(), "waiting for exact planner");
            try {
                var plan = (BigCraftingPlan) future.get();
                h.assertTrue(plan.amount().equals(OVER_LONG), "backend received a truncated exact quantity");
                h.assertTrue(ThunderboltV2PlanningEngine.ID.equals(CraftingAlgorithmCalculationStatus.selected(future)),
                        "exact backend did not retain selected algorithm tracking");
                menu.removed(f.player);
            } catch (Exception e) { throw new AssertionError(e); }
        });
    }

    private static boolean cpuMatches(CraftConfirmMenu menu, ICraftingCPU cpu) throws Exception {
        var method = CraftConfirmMenu.class.getDeclaredMethod("cpuMatches", ICraftingCPU.class);
        method.setAccessible(true);
        return (boolean) method.invoke(menu, cpu);
    }

    private static Object field(CraftConfirmMenu menu, String name) throws Exception {
        var field = CraftConfirmMenu.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(menu);
    }

    private static void set(CraftConfirmMenu menu, String name, Object value) throws Exception {
        var field = CraftConfirmMenu.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(menu, value);
    }

    private static <T> T proxy(Class<T> type, BiFunction<String, Object[], Object> handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, m, a) -> {
            Object result = handler.apply(m.getName(), a);
            return result != null ? result : BigServiceTestFixture.defaultValue(m.getReturnType());
        }));
    }

    private static final class Fixture {
        boolean hasCpu = true, multidimensional = true;
        long observed;
        int normalCalls;
        List<PlanningChoice> choices = List.of(PlanningChoice.engine(ThunderboltV2PlanningEngine.ID), PlanningChoice.VANILLA);
        final ServerPlayer player;
        final IGrid grid;
        final Host host;
        final TimeWheelCraftingCpuPool pool;
        final ICraftingCPU ordinaryCpu = proxy(ICraftingCPU.class, (name, args) -> switch (name) {
            case "getAvailableStorage" -> Long.MAX_VALUE;
            case "isActive" -> true;
            default -> null;
        });

        Fixture(GameTestHelper h) {
            player = FakePlayerFactory.getMinecraft(h.getLevel());
            var values = new HashMap<String, Object>();
            var planning = proxy(ICraftingPlanningService.class, (name, args) -> name.equals("resolve") ? choices : null);
            var disk = new BigServiceTestFixture.Disk();
            var storage = proxy(IStorageService.class, (name, args) -> name.equals("getInventory") ? disk : null);
            grid = proxy(IGrid.class, (name, args) -> name.equals("getService") ? planning
                    : name.equals("getActiveMachines") ? values.get(((Class<?>) args[0]).getName()) : values.get(name));
            var controller = new TianshuSupercomputerControllerBlockEntity(BlockPos.ZERO,
                    ModBlocks.TIANSHU_SUPERCOMPUTER_CONTROLLER.get().defaultBlockState()) {
                public boolean isCpuActive() { return hasCpu; }
                public IGrid getGrid() { return grid; }
                public CpuInternalCoreProfile getCoreProfile() {
                    return CpuInternalCoreCalculator.calculate(multidimensional
                            ? CpuMainCoreTier.MULTIDIMENSIONAL : CpuMainCoreTier.BASELINE, 0, 0);
                }
                public void markCpuDirty() {}
            };
            controller.setLevel(h.getLevel());
            pool = new TimeWheelCraftingCpuPool(controller, Long.MAX_VALUE, 1, Long.MAX_VALUE, true);
            var port = new TianshuSupercomputerPortBlockEntity(BlockPos.ZERO,
                    ModBlocks.TIANSHU_SUPERCOMPUTER_PORT.get().defaultBlockState()) {
                public TianshuSupercomputerControllerBlockEntity getController() { return controller; }
                public boolean isLinkActive() { return hasCpu; }
            };
            port.setLevel(h.getLevel());
            values.put(TianshuSupercomputerPortBlockEntity.class.getName(), Set.of(port));
            values.put(MatrixPortBlockEntity.class.getName(), Set.of());
            values.put("getStorageService", storage);
            values.put("getCraftingService", proxy(ICraftingService.class, (name, args) -> switch (name) {
                case "getCpus" -> hasCpu ? ImmutableSet.of(pool) : ImmutableSet.of();
                case "getCraftables" -> Set.of();
                case "getCraftingFor" -> List.of();
                case "beginCraftingCalculation" -> {
                    observed = (long) args[3]; normalCalls++;
                    CraftingAlgorithmCalculationStatus.select((ICraftingSimulationRequester) args[1],
                            ThunderboltV2PlanningEngine.ID);
                    yield CompletableFuture.completedFuture(new CraftingPlan(new GenericStack(TARGET, observed), 1,
                            false, false, new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of()));
                }
                default -> null;
            }));
            var node = proxy(IGridNode.class, (name, args) -> switch (name) {
                case "getGrid" -> grid;
                case "isActive", "isOnline" -> true;
                case "getLevel" -> h.getLevel();
                default -> null;
            });
            host = new Host(node);
            host.setLevel(h.getLevel());
        }

        CraftConfirmMenu menu() { return new CraftConfirmMenu(150, player.getInventory(), host); }
    }

    private static final class Host extends BlockEntity implements ISubMenuHost, IActionHost {
        private final IGridNode node;
        Host(IGridNode node) { super(BlockEntityType.CHEST, BlockPos.ZERO, Blocks.CHEST.defaultBlockState()); this.node = node; }
        public IGridNode getActionableNode() { return node; }
        public void returnToMainMenu(Player player, ISubMenu menu) {}
        public ItemStack getMainMenuIcon() { return ItemStack.EMPTY; }
    }
}
