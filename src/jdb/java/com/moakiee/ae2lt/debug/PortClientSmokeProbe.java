package com.moakiee.ae2lt.debug;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.menu.SlotSemantics;
import appeng.api.stacks.AEItemKey;
import com.moakiee.ae2lt.blockentity.PigmeeSynthesisStationBlockEntity;
import com.moakiee.ae2lt.client.PigmeeSynthesisStationScreen;
import com.moakiee.ae2lt.client.TianshuPatternEncodingTermScreen;
import com.moakiee.ae2lt.client.TianshuRecipeTransferContext;
import com.moakiee.ae2lt.menu.PigmeeSynthesisStationMenu;
import com.moakiee.ae2lt.menu.TianshuWirelessPatternEncodingTermMenu;
import com.moakiee.ae2lt.registry.ModBlocks;
import mezz.jei.api.constants.RecipeTypes;
import com.moakiee.ae2lt.client.railgun.RailgunClientExtensions;
import com.moakiee.ae2lt.integration.jei.JEIPlugin;
import com.moakiee.ae2lt.integration.jei.category.MultiblockStructureCategory;
import com.moakiee.ae2lt.registry.ModItems;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in integration smoke test; only runs after the isolated native UI fixture passes. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class PortClientSmokeProbe {
    private static final BlockPos PROVIDER = new BlockPos(14, 110, 9);
    private static final BlockPos STATION = new BlockPos(14, 110, 10);
    private static int ticks, phase, waitTicks, returnMode;
    private static boolean finished;
    private static volatile boolean pending;
    private static volatile Throwable failure;
    private static IJeiRuntime jei;

    private PortClientSmokeProbe() {}

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void server(Consumer<ServerPlayer> action) {
        var mc = Minecraft.getInstance();
        var uuid = mc.player.getUUID();
        pending = true;
        mc.getSingleplayerServer().execute(() -> {
            try {
                action.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(uuid));
            } catch (Throwable error) {
                failure = error;
            } finally {
                pending = false;
            }
        });
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("ae2lt.portClientSmokeProbe") || finished
                || !BigIntegerNativeUiProbe.succeeded()) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.getSingleplayerServer() == null) {
            if (phase > 0) {
                System.out.println("PORT_CLIENT_SMOKE_FAILED disconnected during phase=" + phase);
                finished = true;
                mc.stop();
            }
            return;
        }
        try {
            if (pending) {
                require(++waitTicks <= 1200, "server action timed out");
                return;
            }
            waitTicks = 0;
            if (++ticks % 30 != 0) return;
            if (failure != null) throw new AssertionError("server fixture failed", failure);
            switch (phase) {
                case 0 -> {
                    verifyRailgunPose();
                    var active = JEIPlugin.class.getDeclaredField("activePlugin");
                    active.setAccessible(true);
                    var runtime = JEIPlugin.class.getDeclaredField("runtime");
                    runtime.setAccessible(true);
                    jei = (IJeiRuntime) runtime.get(active.get(null));
                    require(jei != null, "JEI runtime unavailable");
                    for (String id : List.of("crystal_catalyzer", "lightning_assembly", "lightning_simulation",
                            "lightning_transform", "lightning_strike", "overload_processing", "firmament_conversion")) {
                        var type = jei.getRecipeManager().getRecipeType(Identifier.fromNamespaceAndPath("ae2lt", id))
                                .orElseThrow();
                        long count = jei.getRecipeManager().createRecipeLookup(type).get().count();
                        require(count > 0, "no synchronized recipes for " + id);
                        System.out.println("PORT_CLIENT_RECIPES " + id + "=" + count);
                    }
                    jei.getRecipesGui().showTypes(List.of(jei.getRecipeManager()
                            .getRecipeType(Identifier.fromNamespaceAndPath("ae2lt", "crystal_catalyzer"))
                            .orElseThrow()));
                }
                case 1 -> {
                    requireJeiScreen();
                    shot("jei-catalyzer");
                    showStructure(0);
                }
                case 2 -> {
                    requireJeiScreen();
                    shot("jei-matrix-preview");
                    showStructure(1);
                }
                case 3 -> {
                    requireJeiScreen();
                    shot("jei-tianshu-preview");
                    mc.setScreen(null);
                    server(player -> prepareProvider(player, "packaged_pattern_provider"));
                }
                case 4, 7 -> server(PortClientSmokeProbe::openProvider);
                case 5, 8 -> {
                    var menu = providerMenu();
                    shot(phase == 5 ? "pp-menu" : "wireless-pp-menu");
                    returnMode = menu.getClass().getField("returnMode").getInt(menu);
                    menu.getClass().getMethod("clientToggleAutoReturn").invoke(menu);
                }
                case 6 -> {
                    verifyReturnMode();
                    shot("pp-menu");
                    server(player -> {
                        player.closeContainer();
                        prepareProvider(player, "wireless_packaged_pattern_provider");
                    });
                }
                case 9 -> {
                    verifyReturnMode();
                    server(player -> {
                        player.closeContainer();
                        var level = (ServerLevel) player.level();
                        level.setBlockAndUpdate(STATION.below(), Blocks.CHEST.defaultBlockState());
                        var chest = (ChestBlockEntity) level.getBlockEntity(STATION.below());
                        chest.clearContent();
                        chest.setItem(0, new ItemStack(Items.OAK_PLANKS, 4));
                        level.setBlockAndUpdate(STATION, ModBlocks.PIGMEE_SYNTHESIS_STATION.get().defaultBlockState());
                        var station = (PigmeeSynthesisStationBlockEntity) level.getBlockEntity(STATION);
                        station.getSubInventory(PigmeeSynthesisStationBlockEntity.INV_CRAFTING).clear();
                    });
                }
                case 10 -> server(player -> MenuOpener.open(PigmeeSynthesisStationMenu.TYPE, player,
                        MenuLocators.forBlockEntity(player.level().getBlockEntity(STATION))));
                case 11 -> {
                    require(mc.screen instanceof PigmeeSynthesisStationScreen, "Pigmee screen missing");
                    var menu = ((PigmeeSynthesisStationScreen) mc.screen).getMenu();
                    require(menu.getLinkStatus().connected(), "Pigmee adjacent inventory not connected");
                    transferCraftingTable(menu);
                }
                case 12 -> {
                    var menu = ((PigmeeSynthesisStationScreen) mc.screen).getMenu();
                    for (int slot : new int[] {0, 1, 3, 4}) {
                        var stack = menu.getCraftingMatrix().getStackInSlot(slot);
                        require(stack.is(Items.OAK_PLANKS) && stack.getCount() == 1,
                                "JEI did not fill Pigmee crafting slot " + slot);
                    }
                    require(menu.getSlots(SlotSemantics.CRAFTING_RESULT).getFirst().getItem().is(Items.CRAFTING_TABLE),
                            "Pigmee crafting result not synchronized");
                    require(menu.getClientRepo().getAllEntries().stream()
                            .filter(entry -> entry.getWhat().equals(AEItemKey.of(Items.OAK_PLANKS)))
                            .mapToLong(entry -> entry.getStoredAmount()).sum() == 0,
                            "JEI transfer did not debit four planks from adjacent inventory");
                    shot("pigmee-jei-transfer");
                    server(player -> {
                        player.closeContainer();
                        MenuOpener.open(TianshuWirelessPatternEncodingTermMenu.TYPE, player,
                                MenuLocators.forInventorySlot(0));
                    });
                }
                case 13 -> {
                    require(mc.screen instanceof TianshuPatternEncodingTermScreen<?>, "Tianshu screen missing");
                    transferCraftingTable(((TianshuPatternEncodingTermScreen<?>) mc.screen).getMenu());
                }
                case 14 -> {
                    var menu = ((TianshuPatternEncodingTermScreen<?>) mc.screen).getMenu();
                    for (int slot : new int[] {0, 1, 3, 4}) {
                        require(menu.getCraftingGridSlots()[slot].getItem().is(Items.OAK_PLANKS),
                                "JEI did not fill Tianshu pattern slot " + slot);
                    }
                    var metadata = TianshuRecipeTransferContext.snapshotFor(menu);
                    require(metadata.sourceKey().equals("minecraft:crafting")
                                    && metadata.recipeId().equals("minecraft:crafting_table"),
                            "Tianshu recipe-transfer identity changed: " + metadata);
                    if (Boolean.getBoolean("ae2lt.portIpnProbe")) {
                        var hintsType = Class.forName("org.anti_ad.mc.ipnext.integration.HintsManagerNG");
                        var hintsManager = hintsType.getField("INSTANCE").get(null);
                        for (var type : new Class<?>[] {mc.screen.getClass(), menu.getClass()}) {
                            var hints = hintsType.getMethod("getHints", Class.class).invoke(hintsManager, type);
                            require(Boolean.TRUE.equals(hints.getClass().getMethod("getIgnore").invoke(hints)),
                                    "IPN must ignore Tianshu screen/menu: " + type.getName());
                        }
                        System.out.println("PORT_IPN_CONFIRMED tianshu-screen+wireless-menu-ignore");
                    }
                    shot("tianshu-jei-transfer");
                    server(player -> {
                        player.closeContainer();
                        var pos = STATION.above(3);
                        player.level().setBlockAndUpdate(pos, ModBlocks.CLOSED_LOOP_SEED_STORAGE.get().defaultBlockState());

                    });
                }
                case 15 -> {
                    require(mc.level.getBlockEntity(STATION.above(3)) instanceof com.moakiee.ae2lt.blockentity.TianshuSeedStorageBlockEntity,
                            "seed block update must arrive before opening its menu");
                    server(player -> MenuOpener.open(com.moakiee.ae2lt.menu.TianshuSeedStorageMenu.TYPE, player,
                            MenuLocators.forBlockEntity(player.level().getBlockEntity(STATION.above(3)))));
                }
                case 16 -> {
                    require(mc.screen instanceof com.moakiee.ae2lt.client.TianshuSeedStorageScreen, "seed storage screen missing");
                    var menu = ((com.moakiee.ae2lt.client.TianshuSeedStorageScreen) mc.screen).getMenu();
                    var cells = menu.getSlots(SlotSemantics.STORAGE_CELL);
                    require(cells.size() == 10, "seed storage cell count changed");
                    for (var slot : cells) require(slot instanceof appeng.menu.slot.AppEngSlot aeSlot
                            && aeSlot.getIcon() == appeng.util.Icon.BACKGROUND_STORAGE_CELL, "missing cell background");
                    shot("seed-storage-slots");
                    com.moakiee.ae2lt.client.OverloadAlloyAnvilClientProbe.verifyModels();
                    mc.setScreen(new com.moakiee.ae2lt.client.OverloadAlloyAnvilClientProbe.Preview());
                }
                case 17 -> {
                    shot("alloy-anvil-preview");
                    finished = true;
                    System.out.println("PORT_CLIENT_SMOKE_CONFIRMED railgun+synced-recipes+jei-previews+pp-menus+return-mode-packets+pigmee-jei-transfer+tianshu-jei-transfer+seed-slot-icons+alloy-models");
                    mc.stop();
                }
                default -> throw new AssertionError("unexpected phase " + phase);
            }
            phase++;
        } catch (Throwable error) {
            finished = true;
            error.printStackTrace();
            System.out.println("PORT_CLIENT_SMOKE_FAILED phase=" + phase);
            mc.stop();
        }
    }

    private static void showStructure(int index) {
        var manager = jei.getRecipeManager();
        var recipes = manager.createRecipeLookup(MultiblockStructureCategory.TYPE).get().toList();
        require(recipes.size() == 2, "expected both multiblock previews");
        jei.getRecipesGui().showRecipes(manager.getRecipeCategory(MultiblockStructureCategory.TYPE),
                List.of(recipes.get(index)), List.of());
    }

    private static void transferCraftingTable(AbstractContainerMenu menu) {
        var manager = jei.getRecipeManager();
        var category = manager.getRecipeCategory(RecipeTypes.CRAFTING);
        var recipe = manager.createRecipeLookup(RecipeTypes.CRAFTING).get()
                .filter(holder -> holder.id().identifier().equals(Identifier.withDefaultNamespace("crafting_table")))
                .findFirst().orElseThrow();
        var layout = manager.createRecipeLayoutDrawable(category, recipe,
                jei.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).orElseThrow();
        var handler = jei.getRecipeTransferManager().getRecipeTransferHandler(menu, category).orElseThrow();
        var error = handler.transferRecipe(menu, recipe, layout.getRecipeSlotsView(),
                Minecraft.getInstance().player, false, true);
        require(error == null, "JEI rejected crafting-table transfer: " + error);
    }

    private static void requireJeiScreen() {
        var screen = Minecraft.getInstance().screen;
        require(screen != null && screen.getClass().getName().startsWith("mezz.jei."),
                "JEI screen missing: " + screen);
    }

    private static void prepareProvider(ServerPlayer player, String blockId) {
        var level = (ServerLevel) player.level();
        level.setBlockAndUpdate(PROVIDER, BuiltInRegistries.BLOCK
                .getValue(Identifier.fromNamespaceAndPath("ae2ltpp", blockId)).defaultBlockState());
        player.teleportTo(level, 14.5, 110, 7, Set.of(), 0, 0, true);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void openProvider(ServerPlayer player) {
        var level = (ServerLevel) player.level();
        var menuType = (MenuType) BuiltInRegistries.MENU
                .getValue(Identifier.fromNamespaceAndPath("ae2ltpp", "packaged_pattern_provider"));
        MenuOpener.open(menuType, player, MenuLocators.forBlockEntity(level.getBlockEntity(PROVIDER)));
    }

    private static Object providerMenu() {
        var screen = Minecraft.getInstance().screen;
        require(screen instanceof AbstractContainerScreen<?>
                && screen.getClass().getSimpleName().equals("PackagedPatternProviderScreen"),
                "PP screen missing: " + screen);
        return ((AbstractContainerScreen<?>) screen).getMenu();
    }

    private static void verifyReturnMode() throws ReflectiveOperationException {
        var menu = providerMenu();
        require(menu.getClass().getField("returnMode").getInt(menu) != returnMode,
                "PP return mode did not synchronize after client action");
    }

    private static void verifyRailgunPose() {
        var model = new HumanoidModel<HumanoidRenderState>(Minecraft.getInstance().getEntityModels()
                .bakeLayer(ModelLayers.PLAYER));
        var state = new HumanoidRenderState();
        state.mainArm = HumanoidArm.RIGHT;
        state.rightHandItemStack = new ItemStack(ModItems.ELECTROMAGNETIC_RAILGUN.get());
        model.setupAnim(state);
        require(Math.abs(model.rightArm.xRot - RailgunClientExtensions.MAIN_ARM_X_ROT_BASE) < 0.0001F,
                "main-hand railgun pose not applied");
        state.rightHandItemStack = ItemStack.EMPTY;
        state.leftHandItemStack = new ItemStack(ModItems.ELECTROMAGNETIC_RAILGUN.get());
        model.setupAnim(state);
        require(Math.abs(model.leftArm.xRot - RailgunClientExtensions.MAIN_ARM_X_ROT_BASE) < 0.0001F,
                "off-hand railgun pose not applied");
    }

    private static void shot(String name) {
        var mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, "port-" + name + ".png", mc.getMainRenderTarget(), 1, message -> {});
    }
}
