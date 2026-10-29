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
import mezz.jei.gui.recipes.RecipesGui;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;

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
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
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

    @SuppressWarnings({"rawtypes", "unchecked"})
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
        var recipe = mc.level.getRecipeManager().getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.SMELTING).get(0);
        var layout = runtime.getRecipeManager().createRecipeLayoutDrawable(category, recipe,
                runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).orElseThrow();
        gui.showTypes(List.of(RecipeTypes.SMELTING));
        var button = controller(layout, gui);
        TianshuRecipeTransferContext.clear(menu);
        require(press(button, true), "simulated transfer accepted");
        require(mc.screen == gui && TianshuRecipeTransferContext.snapshotFor(menu).sourceKey().isEmpty(), "simulation leaves screen and metadata unchanged");
        require(press(button, false), "real transfer accepted");
        require(mc.screen == terminal, "successful transfer restores terminal");
        require(TianshuRecipeTransferContext.snapshotFor(menu).sourceKey().equals("minecraft:smelting"), "recipe identity survives real transfer");
        require(JeiRecipeTransferMetadata.snapshotFor(menu).sourceKey().isEmpty(), "temporary metadata cleared after transfer");
        var context = TianshuRecipeTransferContext.snapshotFor(menu);
        var editorClass = com.moakiee.ae2lt.client.tianshu.TianshuUploadAliasEditScreen.class;
        var constructor = editorClass.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        var editor = (appeng.client.gui.AEBaseScreen<?>) constructor.newInstance(
                terminal, null, "probe:original", "keep alias", (java.util.function.Consumer<String>) ignored -> {});
        var switchScreen = appeng.client.gui.AEBaseScreen.class.getDeclaredMethod("switchToScreen", appeng.client.gui.AEBaseScreen.class);
        switchScreen.setAccessible(true);
        switchScreen.invoke(terminal, editor);
        var sourceField = editorClass.getDeclaredField("source"); sourceField.setAccessible(true);
        var source = (appeng.client.gui.widgets.AETextField) sourceField.get(editor);
        for (var type : List.of(RecipeTypes.SMELTING, MultiblockStructureCategory.TYPE)) {
            var sourceCategory = runtime.getRecipeManager().getRecipeCategory((mezz.jei.api.recipe.RecipeType) type);
            var sourceRecipe = type == RecipeTypes.SMELTING ? recipe : runtime.getRecipeManager()
                    .createRecipeLookup(MultiblockStructureCategory.TYPE).get().findFirst().orElseThrow();
            var sourceLayout = (IRecipeLayoutDrawable<?>) runtime.getRecipeManager().createRecipeLayoutDrawable(sourceCategory, sourceRecipe,
                    runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).orElseThrow();
            source.setValue("probe:original");
            gui.showTypes(List.of(type));
            var sourceButton = controller(sourceLayout, gui);
            require(press(sourceButton, true) && source.getValue().equals("probe:original"), "alias source simulation preserves draft");
            require(press(sourceButton, false) && mc.screen == editor, "alias source selection restores editor");
            String expectedSource = type == RecipeTypes.SMELTING ? "minecraft:smelting" : type.getUid().toString();
            require(source.getValue().equals(expectedSource), "alias source uses registered recipe identity: " + expectedSource);
        }
        require(context.equals(TianshuRecipeTransferContext.snapshotFor(menu)), "alias selection preserves encoded recipe context");
        switchScreen.invoke(editor, terminal);

    }

    private static IJeiUserInput input(boolean simulate) {
        return (IJeiUserInput) Proxy.newProxyInstance(IJeiUserInput.class.getClassLoader(), new Class<?>[] {IJeiUserInput.class},
                (proxy, method, args) -> method.getName().equals("isSimulate") ? simulate
                        : method.getReturnType() == boolean.class ? false : null);
    }

    private static Object controller(IRecipeLayoutDrawable<?> layout, RecipesGui gui) throws Exception {
        Class<?> button;
        try { button = Class.forName("mezz.jei.gui.recipes.RecipeTransferButtonController"); }
        catch (ClassNotFoundException legacy) {
            button = Class.forName("mezz.jei.gui.recipes.RecipeTransferButton");
            var instance = button.getMethod("create", IRecipeLayoutDrawable.class, Runnable.class)
                    .invoke(null, layout, (Runnable) gui::onClose);
            button.getMethod("update", net.minecraft.world.inventory.AbstractContainerMenu.class,
                    net.minecraft.world.entity.player.Player.class).invoke(instance,
                    Minecraft.getInstance().player.containerMenu, Minecraft.getInstance().player);
            return instance;
        }
        for (var constructor : button.getConstructors()) {
            if (constructor.getParameterCount() == 2) return constructor.newInstance(layout, gui);
            if (constructor.getParameterCount() == 3) {
                var logicField = RecipesGui.class.getDeclaredField("logic"); logicField.setAccessible(true);
                var logic = logicField.get(gui);
                var serviceField = logic.getClass().getDeclaredField("recipeTransferService"); serviceField.setAccessible(true);
                return constructor.newInstance(layout, gui, serviceField.get(logic));
            }
        }
        throw new AssertionError("Unsupported JEI button constructor");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean press(Object button, boolean simulate) throws Exception {
        try {
            return (boolean) button.getClass().getMethod("onPress", IJeiUserInput.class).invoke(button, input(simulate));
        } catch (NoSuchMethodException legacy) {
            var click = java.util.Arrays.stream(button.getClass().getDeclaredMethods())
                    .filter(method -> method.getName().equals("onMouseClicked")).findFirst().orElseThrow();
            var inputClass = click.getParameterTypes()[0];
            Class<? extends Enum> type = (Class<? extends Enum>) Class.forName(inputClass.getPackageName() + ".InputType");
            var userInput = inputClass.getConstructor(com.mojang.blaze3d.platform.InputConstants.Key.class,
                    double.class, double.class, int.class, type).newInstance(
                    com.mojang.blaze3d.platform.InputConstants.UNKNOWN, 0.0, 0.0, 0,
                    Enum.valueOf(type, simulate ? "SIMULATE" : "EXECUTE"));
            click.setAccessible(true);
            return (boolean) click.invoke(button, userInput);
        }
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
