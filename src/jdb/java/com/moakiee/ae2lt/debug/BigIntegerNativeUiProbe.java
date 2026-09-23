package com.moakiee.ae2lt.debug;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.GridHelper;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.*;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.client.gui.me.crafting.*;
import appeng.core.definitions.AEBlocks;
import appeng.menu.MenuOpener;
import appeng.menu.locator.*;
import appeng.menu.me.crafting.*;

import com.moakiee.ae2lt.block.*;
import com.moakiee.ae2lt.blockentity.*;
import com.moakiee.ae2lt.client.*;
import com.moakiee.ae2lt.crafting.big.*;
import com.moakiee.ae2lt.logic.craft.*;
import com.moakiee.ae2lt.logic.tianshu.*;
import com.moakiee.ae2lt.registry.*;
import com.moakiee.thunderbolt.ae2.crafting.ExactPlanReports;
import com.moakiee.thunderbolt.core.storage.big.*;

import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.math.BigInteger;
import java.util.*;

/** Opt-in real native UI + packet test. Run only in the isolated BigIntProbe world. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class BigIntegerNativeUiProbe {
    private static final BlockPos BASE = new BlockPos(0, 100, 0);
    private static final BigInteger N = BigInteger.TEN.pow(100).add(BigInteger.valueOf(12345));
    private static DriveBlockEntity drive;
    private static TianshuSupercomputerControllerBlockEntity cpu;
    private static MatrixControllerBlockEntity matrix;
    private static MatrixPatternStorageBlockEntity patterns;
    private static BigMatrixRecipe recipe;
    private static appeng.parts.AEBasePart terminal;
    private static appeng.blockentity.networking.WirelessAccessPointBlockEntity access;
    private static int phase, ticks;
    private static boolean wireless, finished, checkedBack;
    private static volatile Throwable failure;
    private static volatile boolean serverActionPending;
    private static int serverWaitTicks;
    private static boolean succeeded;

    public static boolean succeeded() {
        return succeeded;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void server(java.util.function.Consumer<ServerPlayer> action) {
        var mc = Minecraft.getInstance();
        var uuid = mc.player.getUUID();
        serverActionPending = true;
        mc.getSingleplayerServer()
                .execute(
                        () -> {
                            try {
                                action.accept(
                                        mc.getSingleplayerServer().getPlayerList().getPlayer(uuid));
                            } catch (Throwable e) {
                                failure = e;
                            } finally {
                                serverActionPending = false;
                            }
                        });
    }

    private static MenuHostLocator locator() {
        return wireless ? MenuLocators.forInventorySlot(0) : MenuLocators.forPart(terminal);
    }

    @SubscribeEvent
    public static void pause(ServerTickEvent.Post event) {
        if (Boolean.getBoolean("ae2lt.bigNativeProbe")
                && cpu != null
                && cpu.bigCrafting().job() != null) cpu.bigCrafting().job().suspended = true;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("ae2lt.bigNativeProbe") || finished) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.getSingleplayerServer() == null) return;
        // World/fixture creation may take longer than thirty client ticks on a cold load.
        // Advance only after the preceding server action has actually completed.
        if (serverActionPending) {
            if (++serverWaitTicks > 1200) {
                finished = true;
                System.out.println("BIGINT_NATIVE_UI_FAILED server action timeout phase=" + phase);
            }
            return;
        }
        serverWaitTicks = 0;
        if (++ticks % 30 != 0) return;
        try {
            if (failure != null) throw new AssertionError("Server probe failed", failure);
            switch (phase) {
                case 0 ->
                        server(
                                p -> {
                                    build((ServerLevel) p.level());
                                    p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                                    p.getAbilities().flying = true;
                                    p.onUpdateAbilities();
                                    p.teleportTo((ServerLevel) p.level(), 15, 111, 5, Set.of(), 90, 0, true);
                                });
                case 1 ->
                        server(
                                p -> {
                                    cpu.scanNow();
                                    matrix.scanAndForm(p);
                                    require(
                                            cpu.isFormed() && matrix.isBigCraftingAvailable(),
                                            "fixture formation failed");
                                    var cp =
                                            (TianshuSupercomputerPortBlockEntity)
                                                    ((ServerLevel) p.level())
                                                            .getBlockEntity(cpu.getPortPos());
                                    var mp =
                                            (MatrixPortBlockEntity)
                                                    ((ServerLevel) p.level())
                                                            .getBlockEntity(
                                                                    MatrixMultiblockScanner
                                                                            .worldPos(
                                                                                    BASE.offset(
                                                                                            22, 12,
                                                                                            4),
                                                                                    new BlockPos(
                                                                                            6, 5,
                                                                                            3),
                                                                                    Direction
                                                                                            .EAST));
                                    var node = drive.getMainNode().getNode();
                                    GridHelper.createConnection(node, cp.getMainNode().getNode());
                                    GridHelper.createConnection(node, mp.getMainNode().getNode());
                                    terminal =
                                            PartHelper.setPart(
                                                    ((ServerLevel) p.level()),
                                                    BASE.offset(14, 10, 6),
                                                    Direction.NORTH,
                                                    p,
                                                    ModItems.TIANSHU_PATTERN_ENCODING_TERMINAL
                                                            .get());
                                    place(
                                            ((ServerLevel) p.level()),
                                            BASE.offset(14, 10, 7),
                                            AEBlocks.WIRELESS_ACCESS_POINT
                                                    .block()
                                                    .defaultBlockState());
                                    access =
                                            (appeng.blockentity.networking
                                                            .WirelessAccessPointBlockEntity)
                                                    ((ServerLevel) p.level())
                                                            .getBlockEntity(BASE.offset(14, 10, 7));
                                });
                case 2 ->
                        server(
                                p -> {
                                    GridHelper.createConnection(
                                            drive.getMainNode().getNode(),
                                            terminal.getMainNode().getNode());
                                    GridHelper.createConnection(
                                            drive.getMainNode().getNode(),
                                            access.getMainNode().getNode());
                                    var item =
                                            ModItems.TIANSHU_WIRELESS_PATTERN_ENCODING_TERMINAL
                                                    .get();
                                    var stack = new ItemStack(item);
                                    appeng.items.tools.powered.WirelessTerminalItem.LINKABLE_HANDLER
                                            .link(
                                                    stack,
                                                    GlobalPos.of(
                                                            p.level().dimension(),
                                                            access.getBlockPos()));
                                    item.injectAEPower(
                                            stack, item.getAEMaxPower(stack), Actionable.MODULATE);
                                    p.getInventory().setItem(0, stack);
                                    BigStorageOps.insert(
                                            cpu.getGrid().getStorageService().getInventory(),
                                            AEItemKey.of(Items.OAK_LOG),
                                            N,
                                            Actionable.MODULATE,
                                            appeng.api.networking.security.IActionSource.empty());
                                });
                case 3 ->
                        server(
                                p -> {
                                    if (wireless) {
                                        var item =
                                                ModItems.TIANSHU_WIRELESS_PATTERN_ENCODING_TERMINAL
                                                        .get();
                                        var h =
                                                item.getMenuHost(
                                                        p, MenuLocators.forInventorySlot(0), null);
                                        System.out.println(
                                                "WIRELESS_DIAG host="
                                                        + h.getClass()
                                                        + " node="
                                                        + h.getActionableNode()
                                                        + " grid="
                                                        + cpu.getGrid()
                                                        + " accessActive="
                                                        + access.isActive()
                                                        + " player="
                                                        + p.position()
                                                        + " range="
                                                        + access.getRange()
                                                        + " link="
                                                        + item.getLinkedPosition(
                                                                p.getInventory().getItem(0)));
                                    }
                                    CraftAmountMenu.open(
                                            p, locator(), AEItemKey.of(Items.OAK_PLANKS), 1);
                                });
                case 4 -> {
                    require(
                            mc.screen instanceof CraftAmountScreen,
                            "native amount screen missing: " + mc.screen);
                    var screen = (CraftAmountScreen) mc.screen;
                    var menu = screen.getMenu();
                    require(
                            ((BigAmountMenu) menu).ae2lt$bigAvailable(),
                            "exact input not available");
                    var f = CraftAmountScreen.class.getDeclaredField("amountToCraft");
                    f.setAccessible(true);
                    var widget = (BigNumberEntry) f.get(screen);
                    if (checkedBack)
                        require(
                                widget.ae2lt$getBig()
                                        .orElseThrow()
                                        .equals(N.multiply(BigInteger.valueOf(4))),
                                "Back changed exact input");
                    widget.ae2lt$setBig(N.multiply(BigInteger.valueOf(4)));
                    require(
                            widget.ae2lt$getBig()
                                    .orElseThrow()
                                    .equals(N.multiply(BigInteger.valueOf(4))),
                            "amount rounded");
                    var add =
                            appeng.client.gui.widgets.NumberEntryWidget.class.getDeclaredMethod(
                                    "addQty", long.class);
                    add.setAccessible(true);
                    add.invoke(widget, 1L);
                    require(
                            widget.ae2lt$getBig()
                                    .orElseThrow()
                                    .equals(N.multiply(BigInteger.valueOf(4)).add(BigInteger.ONE)),
                            "plus button rounded");
                    widget.ae2lt$setBig(N.multiply(BigInteger.valueOf(4)));
                }
                case 5 -> {
                    shot("amount");
                    var confirm = CraftAmountScreen.class.getDeclaredMethod("confirm");
                    confirm.setAccessible(true);
                    confirm.invoke(mc.screen);
                }
                case 6 -> {
                    require(
                            mc.screen instanceof AE2LtCraftConfirmScreen,
                            "existing confirmation report missing: " + mc.screen);
                    var menu = ((AE2LtCraftConfirmScreen) mc.screen).getMenu();
                    require(((BigConfirmMenu) menu).ae2lt$isBig(), "big plan flag missing");
                    require(
                            menu.getPlan() != null && !menu.getPlan().isSimulation(),
                            "exact plan not executable");
                    var report = ExactPlanReports.get(menu.getPlan());
                    require(
                            report.entries().get(AEItemKey.of(Items.OAK_LOG)).stored().equals(N),
                            "confirmation stock rounded");
                    require(
                            report.entries()
                                    .get(AEItemKey.of(Items.OAK_PLANKS))
                                    .crafting()
                                    .equals(N.multiply(BigInteger.valueOf(4))),
                            "confirmation output rounded");
                    shot("confirm");
                    if (!checkedBack) {
                        checkedBack = true;
                        menu.goBack();
                        phase = 3;
                    } else {
                        menu.cycleSelectedCPU(true);
                        menu.startJob();
                    }
                }
                case 7 ->
                        server(
                                p -> {
                                    require(
                                            cpu.bigCrafting().busy(),
                                            "native Start did not submit exact job");
                                    require(
                                            cpu.getGrid()
                                                    .getCraftingService()
                                                    .getCpus()
                                                    .contains(cpu.bigCrafting().cpu()),
                                            "exact job missing from native CPU list");
                                    MenuOpener.open(CraftingStatusMenu.TYPE, p, locator());
                                });
                case 8 -> {
                    require(
                            mc.screen instanceof CraftingStatusScreen,
                            "native status screen missing");
                    var m = ((CraftingStatusScreen) mc.screen).getMenu();
                    var entry =
                            m.cpuList.cpus().stream()
                                    .filter(c -> c.currentJob() != null)
                                    .findFirst()
                                    .orElseThrow();
                    m.selectCpu(entry.serial());
                }
                case 9 -> {
                    var statusField = CraftingCPUScreen.class.getDeclaredField("status");
                    statusField.setAccessible(true);
                    var status = (CraftingStatus) statusField.get(mc.screen);
                    require(
                            status != null
                                    && status.getEntries().stream()
                                            .anyMatch(
                                                    e ->
                                                            ((BigStatusEntry) e).ae2lt$amounts()
                                                                    != null),
                            "native status lost exact counts");
                    shot("status");
                    ((CraftingStatusScreen) mc.screen).getMenu().cancelCrafting();
                }
                case 10 ->
                        server(
                                p -> {
                                    require(!cpu.bigCrafting().busy(), "native Cancel failed");
                                    require(
                                            BigStorageOps.snapshot(
                                                            cpu.getGrid()
                                                                    .getStorageService()
                                                                    .getInventory(),
                                                            appeng.api.networking.security
                                                                    .IActionSource.empty())
                                                    .get(AEItemKey.of(Items.OAK_LOG))
                                                    .equals(N),
                                            "native cancel did not refund exact raw");
                                    MenuOpener.open(
                                            wireless
                                                    ? com.moakiee.ae2lt.menu
                                                            .TianshuWirelessPatternEncodingTermMenu
                                                            .TYPE
                                                    : com.moakiee.ae2lt.menu
                                                            .TianshuPatternEncodingTermMenu.TYPE,
                                            p,
                                            locator());
                                });
                case 11 -> {
                    require(
                            mc.screen instanceof TianshuPatternEncodingTermScreen,
                            "original terminal missing");
                    var m = ((TianshuPatternEncodingTermScreen<?>) mc.screen).getMenu();
                    require(
                            N.equals(m.getBigStock(AEItemKey.of(Items.OAK_LOG))),
                            "terminal stock display lost precision");
                    shot("terminal");
                    if (!wireless) {
                        wireless = true;
                        checkedBack = false;
                        phase = 2;
                    } else {
                        finished = true;
                        succeeded = true;
                        System.out.println(
                                "BIGINT_NATIVE_UI_CONFIRMED wired+wireless"
                                        + " amount+confirm+start+status+cancel+stock");
                        server(p -> p.closeContainer());
                    }
                }
            }
            phase++;
        } catch (Throwable e) {
            finished = true;
            e.printStackTrace();
            System.out.println("BIGINT_NATIVE_UI_FAILED phase=" + phase + " wireless=" + wireless);
        }
    }

    private static void shot(String name) {
        var mc = Minecraft.getInstance();
        Screenshot.grab(
                mc.gameDirectory,
                "native-big-" + (wireless ? "wireless-" : "wired-") + name + ".png",
                mc.getMainRenderTarget(),
                1,
                t -> {});
    }

    private static void build(ServerLevel level) {
        for (int x = -8; x <= 38; x++)
            for (int y = 94; y <= 120; y++)
                for (int z = -8; z <= 16; z++)
                    if (!level.getBlockState(new BlockPos(x, y, z)).isAir())
                        level.setBlockAndUpdate(
                                new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
        var direction = Direction.EAST;
        var cpuPos = BASE.offset(4, 10, 4);
        var matrixPos = BASE.offset(22, 12, 4);
        for (int x = 0; x < 7; x++)
            for (int y = 0; y < 7; y++)
                for (int z = 0; z < 7; z++) {
                    var local = new BlockPos(x, y, z);
                    var state =
                            switch (TianshuMultiblockTemplate.roleAt(local)) {
                                case CASING ->
                                        ModBlocks.TIANSHU_SUPERCOMPUTER_CASING
                                                .get()
                                                .defaultBlockState();
                                case COOLING ->
                                        ModBlocks.PHASE_CHANGE_COOLING_UNIT
                                                .get()
                                                .defaultBlockState();
                                case GLASS ->
                                        ModBlocks.TIANSHU_SUPERCOMPUTER_GLASS
                                                .get()
                                                .defaultBlockState();
                                case CONTROLLER ->
                                        ModBlocks.TIANSHU_SUPERCOMPUTER_CONTROLLER
                                                .get()
                                                .defaultBlockState()
                                                .setValue(
                                                        TianshuSupercomputerControllerBlock.FACING,
                                                        direction);
                                case PORT_CANDIDATE ->
                                        (local.equals(TianshuMultiblockTemplate.LOWER_PORT)
                                                        ? ModBlocks.TIANSHU_SUPERCOMPUTER_PORT.get()
                                                        : ModBlocks.PHASE_CHANGE_COOLING_UNIT.get())
                                                .defaultBlockState();
                                case CORE_RESERVED ->
                                        (local.equals(new BlockPos(3, 3, 3))
                                                        ? ModBlocks
                                                                .MULTIDIMENSIONAL_SUPERCOMPUTING_UNIT
                                                                .get()
                                                        : ModBlocks.TIANSHU_BLANK_UNIT.get())
                                                .defaultBlockState();
                                case IGNORED -> Blocks.AIR.defaultBlockState();
                            };
                    place(
                            level,
                            TianshuMultiblockScanner.worldPos(cpuPos, local, direction),
                            state);
                }
        BlockPos patternPos = null;
        var portLocal = new BlockPos(6, 5, 3);
        for (var entry : MatrixMultiblockTemplate.entries()) {
            var local = entry.localPos();
            var world = MatrixMultiblockScanner.worldPos(matrixPos, local, direction);
            var state =
                    switch (entry.role()) {
                        case CASING ->
                                ModBlocks.MATTER_WARPING_MATRIX_CASING.get().defaultBlockState();
                        case CONSTRAINT_FRAME ->
                                ModBlocks.MATTER_WARPING_MATRIX_CONSTRAINT_FRAME
                                        .get()
                                        .defaultBlockState();
                        case GLASS ->
                                ModBlocks.MATTER_WARPING_MATRIX_GLASS.get().defaultBlockState();
                        case CONTROLLER ->
                                ModBlocks.MATTER_WARPING_MATRIX_CONTROLLER
                                        .get()
                                        .defaultBlockState()
                                        .setValue(MatrixControllerBlock.FACING, direction);
                        case PORT_CANDIDATE ->
                                (local.equals(portLocal)
                                                ? ModBlocks.MATTER_WARPING_MATRIX_PORT.get()
                                                : ModBlocks.MATTER_WARPING_MATRIX_CONSTRAINT_FRAME
                                                        .get())
                                        .defaultBlockState();
                        case PATTERN_BAY -> {
                            if (patternPos == null) {
                                patternPos = world;
                                yield ModBlocks.MATTER_WARPING_MATRIX_PATTERN_STORAGE_T1
                                        .get()
                                        .defaultBlockState();
                            }
                            yield Blocks.AIR.defaultBlockState();
                        }
                        case CRAFTING_BAY ->
                                (local.equals(MatrixMultiblockTemplate.CRAFTING_CENTER_LOCAL)
                                                ? ModBlocks
                                                        .MATTER_WARPING_MATRIX_MULTIDIMENSIONAL_MAIN_CORE
                                                        .get()
                                                : ModBlocks.TIANSHU_BLANK_UNIT.get())
                                        .defaultBlockState();
                        case EMPTY -> Blocks.AIR.defaultBlockState();
                    };
            place(level, world, state);
        }
        var drivePos = BASE.offset(new BlockPos(14, 10, 4));
        var powerPos = drivePos.above();
        place(level, drivePos, AEBlocks.DRIVE.block().defaultBlockState());
        place(level, powerPos, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        drive = (DriveBlockEntity) level.getBlockEntity(drivePos);
        drive.getInternalInventory()
                .setItemDirect(0, new ItemStack(ModItems.INFINITE_STORAGE_CELL.get()));
        cpu = (TianshuSupercomputerControllerBlockEntity) level.getBlockEntity(cpuPos);
        matrix = (MatrixControllerBlockEntity) level.getBlockEntity(matrixPos);
        patterns = (MatrixPatternStorageBlockEntity) level.getBlockEntity(patternPos);
        recipe = planks(level);
        patterns.getInventory().setStackInSlot(0, recipe.definition().toStack());
    }

    private static void place(ServerLevel level, BlockPos pos, BlockState state) {
        level.getChunkAt(pos);
        level.setBlock(pos, state, 3);
    }

    private static BigMatrixRecipe planks(ServerLevel level) {
        var holder =
                com.moakiee.ae2lt.recipe.compat.LegacyRecipeAccess.byId(
                        com.moakiee.ae2lt.recipe.compat.LegacyRecipeAccess.manager(level),
                        Identifier.parse("minecraft:oak_planks"))
                        .orElseThrow();
        var recipe = new RecipeHolder<CraftingRecipe>(holder.id(), (CraftingRecipe) holder.value());
        var input = new ItemStack[9];
        Arrays.fill(input, ItemStack.EMPTY);
        input[0] = new ItemStack(Items.OAK_LOG);
        var encoded =
                PatternDetailsHelper.encodeCraftingPattern(
                        recipe, input, new ItemStack(Items.OAK_PLANKS, 4), false, false);
        var decoded = PatternDetailsHelper.decodePattern(encoded, level);
        return Objects.requireNonNull(
                BigMatrixRecipe.capture(
                        decoded, List.of(Map.of(AEItemKey.of(Items.OAK_LOG), 1L)), level));
    }
}
