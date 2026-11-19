package com.moakiee.ae2lt.debug;

import appeng.api.parts.PartHelper;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import appeng.menu.MenuOpener;
import appeng.menu.SlotSemantics;
import appeng.menu.locator.MenuLocators;
import com.moakiee.ae2lt.client.compat.JeiRecipeTransferMetadata;
import com.moakiee.ae2lt.client.tianshu.TianshuPatternEncodingTermScreen;
import com.moakiee.ae2lt.client.tianshu.TianshuRecipeTransferContext;
import com.moakiee.ae2lt.integration.jei.category.MultiblockStructureCategory;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.moakiee.ae2lt.registry.ModItems;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.common.Internal;
import mezz.jei.gui.recipes.RecipeTransferButtonController;
import mezz.jei.gui.recipes.RecipesGui;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in end-to-end regression in a disposable world; excluded from production jars. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class JeiCompatibilityClientProbe {
    private static final BlockPos CABLE = new BlockPos(0, 100, 0);
    private static int phase, ticks;
    private static boolean done;
    private static volatile boolean pending;
    private static volatile Throwable failure;
    private static final StringBuilder report = new StringBuilder();

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("ae2lt.jeiCompatibilityProbe") || done) return;
        var mc = Minecraft.getInstance();
        if (++ticks > 2400) { finish(false, "Timed out at phase " + phase); return; }
        if (mc.player == null || mc.getSingleplayerServer() == null || pending || ticks % 30 != 0) return;
        try {
            if (failure != null) throw new AssertionError(failure);
            switch (phase++) {
                case 0 -> {
                    require(mc.gameDirectory.toPath().toAbsolutePath().normalize().endsWith("jei-compat-client"), "isolated game directory");
                    mc.options.pauseOnLostFocus = false;
                    server(() -> {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        player.closeContainer();
                        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                        player.getAbilities().flying = true;
                        player.onUpdateAbilities();
                        player.teleportTo(.5, 101, 2.5);
                        PartHelper.setPart(player.serverLevel(), CABLE, null, player, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
                        PartHelper.setPart(player.serverLevel(), CABLE, Direction.SOUTH, player, ModItems.TIANSHU_PATTERN_ENCODING_TERMINAL.get());
                        player.serverLevel().setBlockAndUpdate(CABLE.below(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
                    });
                }
                case 1 -> server(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    var terminal = (appeng.parts.AEBasePart) PartHelper.getPart(player.serverLevel(), CABLE, Direction.SOUTH);
                    MenuOpener.open(TianshuPatternEncodingTermMenu.TYPE, player, MenuLocators.forPart(terminal));
                });
                case 2 -> exerciseTransfer();
                case 3 -> server(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    var menu = (TianshuPatternEncodingTermMenu) player.containerMenu;
                    require(menu.getSlots(SlotSemantics.PROCESSING_INPUTS).stream().anyMatch(slot -> !slot.getItem().isEmpty()), "server received processing inputs");
                    require(menu.getSlots(SlotSemantics.PROCESSING_OUTPUTS).stream().anyMatch(slot -> !slot.getItem().isEmpty()), "server received processing outputs");
                });
                case 4 -> finish(true, "Recipe browsing, simulation, transfer, metadata and server slots passed");
                default -> throw new AssertionError("Unexpected phase");
            }
        } catch (Throwable error) { error.printStackTrace(); finish(false, error.toString()); }
    }

    private static void exerciseTransfer() throws Exception {
        var mc = Minecraft.getInstance();
        require(mc.screen instanceof TianshuPatternEncodingTermScreen<?>, "native terminal opened");
        var terminal = (TianshuPatternEncodingTermScreen<?>) mc.screen;
        var menu = terminal.getMenu();
        var runtime = Internal.getJeiRuntime();
        var gui = (RecipesGui) runtime.getRecipesGui();
        for (var type : List.of(RecipeTypes.CRAFTING, RecipeTypes.SMELTING, MultiblockStructureCategory.TYPE)) {
            gui.showTypes(List.of(type));
            require(mc.screen == gui, "recipe category opens: " + type.getUid());
            gui.onClose();
        }
        var category = runtime.getRecipeManager().getRecipeCategory(RecipeTypes.SMELTING);
        var recipe = mc.level.getRecipeManager().getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.SMELTING).getFirst();
        var layout = runtime.getRecipeManager().createRecipeLayoutDrawable(category, recipe,
                runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).orElseThrow();
        gui.showTypes(List.of(RecipeTypes.SMELTING));
        var button = controller(layout, gui);
        TianshuRecipeTransferContext.clear(menu);
        require(button.onPress(input(true)), "simulated transfer accepted");
        require(mc.screen == gui && TianshuRecipeTransferContext.snapshotFor(menu).sourceKey().isEmpty(), "simulation leaves screen and metadata unchanged");
        require(button.onPress(input(false)), "real transfer accepted");
        require(mc.screen == terminal, "successful transfer restores terminal");
        require(TianshuRecipeTransferContext.snapshotFor(menu).sourceKey().equals("minecraft:smelting"), "recipe identity survives real transfer");
        require(JeiRecipeTransferMetadata.snapshotFor(menu).sourceKey().isEmpty(), "temporary metadata cleared after transfer");
    }

    private static IJeiUserInput input(boolean simulate) {
        return (IJeiUserInput) Proxy.newProxyInstance(IJeiUserInput.class.getClassLoader(), new Class<?>[] {IJeiUserInput.class},
                (proxy, method, args) -> method.getName().equals("isSimulate") ? simulate
                        : method.getReturnType() == boolean.class ? false : null);
    }

    private static RecipeTransferButtonController controller(IRecipeLayoutDrawable<?> layout, RecipesGui gui) throws Exception {
        for (var constructor : RecipeTransferButtonController.class.getConstructors()) {
            if (constructor.getParameterCount() == 2) return (RecipeTransferButtonController) constructor.newInstance(layout, gui);
            if (constructor.getParameterCount() == 3) {
                var logicField = RecipesGui.class.getDeclaredField("logic"); logicField.setAccessible(true);
                var logic = logicField.get(gui);
                var serviceField = logic.getClass().getDeclaredField("recipeTransferService"); serviceField.setAccessible(true);
                return (RecipeTransferButtonController) constructor.newInstance(layout, gui, serviceField.get(logic));
            }
        }
        throw new AssertionError("Unsupported JEI controller constructor");
    }

    private static void server(Runnable action) {
        pending = true;
        Minecraft.getInstance().getSingleplayerServer().execute(() -> {
            try { action.run(); } catch (Throwable error) { failure = error; }
            finally { pending = false; }
        });
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        report.append("PASS ").append(message).append('\n');
    }
    private static void finish(boolean passed, String message) {
        done = true;
        String result = "JEI_CLIENT_QA " + (passed ? "PASS" : "FAIL") + ": " + message + "\n" + report;
        System.out.println(result);
        try { Files.writeString(Path.of("jei-compatibility-probe.txt"), result); }
        catch (Exception error) { error.printStackTrace(); }
        Minecraft.getInstance().stop();
    }
}
