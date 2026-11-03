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
import com.moakiee.ae2lt.integration.jei.category.LightningAssemblyCategory;
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
import mezz.jei.api.runtime.IRecipesGui;
import mezz.jei.common.Internal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
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
        if (!(Boolean.getBoolean("ae2lt.jeiCompatibilityProbe") || serviceEmiRoute()) || done) return;
        var mc = Minecraft.getInstance();
        if (serviceEmiRoute() && Boolean.getBoolean("ae2lt.jeiCompatibilityProbe")) {
            finish(false, "Choose one recipe-viewer QA route");
            return;
        }
        if (++ticks > 2400) { finish(false, "Timed out at phase " + phase); return; }
        if (mc.player == null || mc.getSingleplayerServer() == null || pending || ticks % 30 != 0) return;
        try {
            if (failure != null) throw new AssertionError(failure);
            // In the supported GTO packs, JEMI publishes JEI's runtime before EMI's
            // asynchronous category index finishes. Keep the original timeout budget.
            if (phase == 2 && !viewerReady()) return;
            switch (phase++) {
                case 0 -> {
                    String isolatedDirectory = System.getProperty("ae2lt.clientProbeDirectory");
                    require(isolatedDirectory != null && mc.gameDirectory.toPath().toAbsolutePath().normalize()
                            .equals(Path.of(isolatedDirectory).toAbsolutePath().normalize()), "isolated game directory");
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
                case 4 -> {
                    net.minecraft.client.Screenshot.grab(mc.gameDirectory,
                            serviceEmiRoute() ? "jei-service-emi-terminal.png" : "jei-compatibility-terminal.png",
                            mc.getMainRenderTarget(), ignored -> {});
                    finish(true, serviceEmiRoute()
                            ? "Native EMI browsing, QA-driven JEI button/service simulation and transfer, metadata and server slots passed"
                            : "JEI API browsing, JEI button/service simulation and transfer, metadata and server slots passed");
                }
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
        IRecipesGui gui = runtime.getRecipesGui();
        report.append("INFO recipe API implementation: ").append(gui.getClass().getName()).append('\n');
        if (serviceEmiRoute()) {
            if (!isJemi(gui) || !net.minecraftforge.fml.ModList.get().isLoaded("emi")) {
                throw new AssertionError("The independent EMI/JEI service route requires the actual JEMI runtime");
            }
            report.append("INFO route: EMI_NATIVE_BROWSER_QA_JEI_BUTTON_SERVICE\n");
            report.append("INFO browsing backend: EmiApi.displayRecipeCategory / EMI RecipeScreen\n");
            report.append("INFO transfer backend: QA-driven JEI button/service\n");
            report.append("INFO raw JEMI.showTypes: not exercised by this route\n");
            report.append("INFO EMI category mapping: minecraft:furnace -> minecraft:smelting\n");
        } else {
            report.append("INFO recipe browsing route: ").append(isJemi(gui)
                    ? "JEI API via JEMI / EMI RecipeScreen; transfer uses a QA-driven JEI button/service"
                    : "JEI API / native recipe screen; transfer uses a QA-driven JEI button/service").append('\n');
        }
        for (var type : List.of(RecipeTypes.CRAFTING, RecipeTypes.SMELTING, MultiblockStructureCategory.TYPE)) {
            showType(gui, type);
            require(displays(gui, type, terminal), serviceEmiRoute()
                    ? "EMI category opens: " + EmiViewer.categoryId(type) : "recipe category opens: " + type.getUid());
            mc.screen.onClose();
            if (mc.screen != terminal) throw new AssertionError("Recipe category close did not restore terminal");
        }
        var category = runtime.getRecipeManager().getRecipeCategory(RecipeTypes.SMELTING);
        var recipe = mc.level.getRecipeManager().getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.SMELTING).get(0);
        var layout = runtime.getRecipeManager().createRecipeLayoutDrawable(category, recipe,
                runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).orElseThrow();
        showType(gui, RecipeTypes.SMELTING);
        if (!displays(gui, RecipeTypes.SMELTING, terminal)) throw new AssertionError("Smelting page did not open for transfer");
        var recipeScreen = mc.screen;
        var button = controller(layout, recipeScreen, terminal);
        TianshuRecipeTransferContext.clear(menu);
        require(transferReady(button) && press(button, true), "simulated transfer accepted");
        require(mc.screen == recipeScreen && TianshuRecipeTransferContext.snapshotFor(menu).sourceKey().isEmpty(), "simulation leaves screen and metadata unchanged");
        require(transferReady(button) && press(button, false), "real transfer accepted");
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
        var customAliasType = serviceEmiRoute() ? LightningAssemblyCategory.TYPE : MultiblockStructureCategory.TYPE;
        for (var type : List.of(RecipeTypes.SMELTING, customAliasType)) {
            var sourceCategory = runtime.getRecipeManager().getRecipeCategory((mezz.jei.api.recipe.RecipeType) type);
            var sourceRecipe = type == RecipeTypes.SMELTING ? recipe : runtime.getRecipeManager()
                    .createRecipeLookup((mezz.jei.api.recipe.RecipeType) customAliasType).get().findFirst().orElseThrow();
            var sourceLayout = (IRecipeLayoutDrawable<?>) runtime.getRecipeManager().createRecipeLayoutDrawable(sourceCategory, sourceRecipe,
                    runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).orElseThrow();
            source.setValue("probe:original");
            showType(gui, type);
            if (!displays(gui, type, editor)) throw new AssertionError("Alias source category did not open: " + type.getUid());
            var sourceButton = controller(sourceLayout, mc.screen, editor);
            require(transferReady(sourceButton) && press(sourceButton, true) && source.getValue().equals("probe:original"), "alias source simulation preserves draft");
            require(transferReady(sourceButton) && press(sourceButton, false) && mc.screen == editor, "alias source selection restores editor");
            String expectedSource = type == RecipeTypes.SMELTING ? "minecraft:smelting" : type.getUid().toString();
            require(source.getValue().equals(expectedSource), "alias source uses registered recipe identity: " + expectedSource);
        }
        require(context.equals(TianshuRecipeTransferContext.snapshotFor(menu)), "alias selection preserves encoded recipe context");
        switchScreen.invoke(editor, terminal);

    }

    private static boolean viewerReady() throws Exception {
        try {
            Internal.getJeiRuntime();
        } catch (IllegalStateException notStarted) {
            return false;
        }
        return !net.minecraftforge.fml.ModList.get().isLoaded("emi") || EmiViewer.ready();
    }

    private static boolean isJemi(IRecipesGui gui) {
        return gui.getClass().getName().equals("dev.emi.emi.jemi.runtime.JemiRecipesGui");
    }

    private static boolean serviceEmiRoute() {
        return Boolean.getBoolean("ae2lt.jeiServiceEmiProbe");
    }

    private static void showType(IRecipesGui gui, mezz.jei.api.recipe.RecipeType<?> type) {
        if (serviceEmiRoute()) EmiViewer.show(type);
        else gui.showTypes(List.of(type));
    }

    private static boolean displays(IRecipesGui gui, mezz.jei.api.recipe.RecipeType<?> type,
            net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> parent) throws Exception {
        var screen = Minecraft.getInstance().screen;
        if (gui instanceof Screen) return screen == gui;
        return isJemi(gui) && EmiViewer.displays(screen, type, parent);
    }

    /** The compile-only EMI API omits its real screen and reload classes. */
    private static final class EmiViewer {
        private static final Class<?> SCREEN;
        private static final java.lang.reflect.Method IS_LOADED, FOCUSED_CATEGORY;
        private static final java.lang.reflect.Field PARENT;

        static {
            try {
                SCREEN = Class.forName("dev.emi.emi.screen.RecipeScreen");
                IS_LOADED = Class.forName("dev.emi.emi.runtime.EmiReloadManager").getMethod("isLoaded");
                FOCUSED_CATEGORY = SCREEN.getMethod("getFocusedCategory");
                PARENT = SCREEN.getField("old");
            } catch (ReflectiveOperationException changedApi) {
                throw new ExceptionInInitializerError(changedApi);
            }
        }

        static boolean ready() throws Exception {
            return (boolean) IS_LOADED.invoke(null);
        }

        static boolean displays(Screen screen, mezz.jei.api.recipe.RecipeType<?> type,
                net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> parent) throws Exception {
            if (!SCREEN.isInstance(screen) || PARENT.get(screen) != parent) return false;
            var category = (dev.emi.emi.api.recipe.EmiRecipeCategory) FOCUSED_CATEGORY.invoke(screen);
            if (category != null && type == MultiblockStructureCategory.TYPE) {
                var manager = dev.emi.emi.api.EmiApi.getRecipeManager();
                if (manager.getCategories().stream().filter(c -> c.getId().equals(category.getId())).count() != 1
                        || manager.getRecipes().stream().filter(recipe -> recipe.getCategory().getId().equals(category.getId())).count()
                        != com.moakiee.ae2lt.integration.recipeviewer.multiblock.MultiblockStructureRecipes.all().size())
                    throw new AssertionError("The GTO browser must retain exactly its native multiblock pages");
            }
            return category != null && category.getId().equals(categoryId(type));
        }

        static void show(mezz.jei.api.recipe.RecipeType<?> type) {
            var id = categoryId(type);
            var category = dev.emi.emi.api.EmiApi.getRecipeManager().getCategories().stream()
                    .filter(candidate -> candidate.getId().equals(id)).findFirst()
                    .orElseThrow(() -> new AssertionError("Actual EMI category is missing: " + id));
            dev.emi.emi.api.EmiApi.displayRecipeCategory(category);
        }

        private static net.minecraft.resources.ResourceLocation categoryId(mezz.jei.api.recipe.RecipeType<?> type) {
            // Both routes must display EMI's real smelting category. The raw route still
            // calls IRecipesGui.showTypes and therefore exercises LT's production bridge.
            return type == RecipeTypes.SMELTING
                    ? new net.minecraft.resources.ResourceLocation("minecraft", "smelting") : type.getUid();
        }
    }

    private static IJeiUserInput input(boolean simulate) {
        return (IJeiUserInput) Proxy.newProxyInstance(IJeiUserInput.class.getClassLoader(), new Class<?>[] {IJeiUserInput.class},
                (proxy, method, args) -> method.getName().equals("isSimulate") ? simulate
                        : method.getReturnType() == boolean.class ? false : null);
    }

    private static Object controller(IRecipeLayoutDrawable<?> layout, Screen recipeScreen,
            net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> parent) throws Exception {
        Class<?> button;
        try { button = Class.forName("mezz.jei.gui.recipes.RecipeTransferButtonController"); }
        catch (ClassNotFoundException legacy) {
            button = Class.forName("mezz.jei.gui.recipes.RecipeTransferButton");
            Object instance;
            try {
                instance = button.getMethod("create", IRecipeLayoutDrawable.class, Runnable.class)
                        .invoke(null, layout, (Runnable) recipeScreen::onClose);
            } catch (NoSuchMethodException serviceLayout) {
                var factory = java.util.Arrays.stream(button.getMethods())
                        .filter(method -> method.getName().equals("create") && method.getParameterCount() == 4)
                        .findFirst().orElseThrow(() -> new AssertionError("Unsupported legacy JEI button factory"));
                instance = factory.invoke(null, layout, service(factory.getParameterTypes()[1]),
                        (java.util.function.Supplier<Object>) () -> parent, (Runnable) recipeScreen::onClose);
            }
            button.getMethod("update", net.minecraft.world.inventory.AbstractContainerMenu.class,
                    net.minecraft.world.entity.player.Player.class).invoke(instance,
                    parent.getMenu(), Minecraft.getInstance().player);
            return instance;
        }
        for (var constructor : button.getConstructors()) {
            if (constructor.getParameterCount() >= 2 && constructor.getParameterTypes()[1].isInstance(recipeScreen)) {
                if (constructor.getParameterCount() == 2) return constructor.newInstance(layout, recipeScreen);
                if (constructor.getParameterCount() == 3) {
                    return constructor.newInstance(layout, recipeScreen, service(constructor.getParameterTypes()[2]));
                }
            }
        }
        throw new AssertionError("Unsupported JEI button constructor");
    }

    private static boolean transferReady(Object button) throws Exception {
        var update = java.util.Arrays.stream(button.getClass().getMethods())
                .filter(method -> method.getName().equals("updateState") && method.getParameterCount() == 1)
                .findFirst();
        if (update.isPresent()) {
            boolean[] state = new boolean[2];
            var stateType = update.get().getParameterTypes()[0];
            var listener = Proxy.newProxyInstance(stateType.getClassLoader(), new Class<?>[] {stateType},
                    (ignored, method, arguments) -> {
                        if (method.getName().equals("setActive")) state[0] = (boolean) arguments[0];
                        if (method.getName().equals("setVisible")) state[1] = (boolean) arguments[0];
                        return null;
                    });
            update.get().invoke(button, listener);
            return state[0] && state[1];
        }
        for (Class<?> ancestor = button.getClass().getSuperclass(); ancestor != null; ancestor = ancestor.getSuperclass()) {
            try {
                var field = ancestor.getDeclaredField("button");
                field.setAccessible(true);
                var nativeButton = (net.minecraft.client.gui.components.Button) field.get(button);
                return nativeButton.active && nativeButton.visible;
            } catch (NoSuchFieldException notOwner) {
                // GuiIconToggleButton owns the native active/visible error state in JEI 15.59.
            }
        }
        throw new AssertionError("Cannot inspect real JEI transfer button error state");
    }

    private static Object service(Class<?> type) throws Exception {
        return type.getConstructor(mezz.jei.api.recipe.transfer.IRecipeTransferManager.class)
                .newInstance(Internal.getJeiRuntime().getRecipeTransferManager());
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
        String result = (serviceEmiRoute() ? "JEI_SERVICE_EMI_QA " : "JEI_CLIENT_QA ")
                + (passed ? "PASS" : "FAIL") + ": " + message + "\n" + report;
        System.out.println(result);
        try { Files.writeString(Path.of(serviceEmiRoute() ? "jei-service-emi-probe.txt" : "jei-compatibility-probe.txt"), result); }
        catch (Exception error) { error.printStackTrace(); }
        Minecraft.getInstance().stop();
    }
}
