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
    private static int ticks, phase = Boolean.getBoolean("ae2lt.portWorkstationOnly") ? 17 : 0, waitTicks, returnMode;
    private static boolean finished, enteredWorld;
    private static volatile boolean pending;
    private static volatile Throwable failure;
    private static IJeiRuntime jei;
    private static appeng.parts.AEBasePart craftingPart;

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
                || (!Boolean.getBoolean("ae2lt.portWorkstationOnly") && !BigIntegerNativeUiProbe.succeeded())) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.getSingleplayerServer() == null) {
            if (enteredWorld) {
                System.out.println("PORT_CLIENT_SMOKE_FAILED disconnected during phase=" + phase);
                finished = true;
                mc.stop();
            }
            return;
        }
        enteredWorld = true;
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
                    if (jei == null) {
                        var active = JEIPlugin.class.getDeclaredField("activePlugin");
                        active.setAccessible(true);
                        var runtime = JEIPlugin.class.getDeclaredField("runtime");
                        runtime.setAccessible(true);
                        if (active.get(null) == null) return;
                        jei = (IJeiRuntime) runtime.get(active.get(null));
                        if (jei == null) return;
                    }
                    if (!Boolean.getBoolean("ae2lt.portWorkstationOnly")) shot("alloy-anvil-preview");
                    server(player -> {
                        player.closeContainer();
                        player.teleportTo((ServerLevel) player.level(), 4.5, 110, 2, Set.of(), 0, 0, true);
                        MenuOpener.open(com.moakiee.ae2lt.menu.TianshuSupercomputerControllerMenu.TYPE, player,
                                MenuLocators.forBlockEntity(player.level().getBlockEntity(new BlockPos(4, 110, 4))));
                    });
                }
                case 18 -> {
                    require(mc.screen instanceof com.moakiee.ae2lt.client.TianshuSupercomputerControllerScreen,
                            "Tianshu controller screen missing");
                    shot("tianshu-controller-text");
                    server(player -> {
                        player.closeContainer();
                        var stack = new ItemStack(ModItems.TIANSHU_WIRELESS_CRAFTING_TERMINAL.get());
                        var drive = (appeng.blockentity.storage.DriveBlockEntity) player.level().getBlockEntity(new BlockPos(14, 110, 4));
                        var access = (appeng.blockentity.networking.WirelessAccessPointBlockEntity) player.level().getBlockEntity(new BlockPos(14, 110, 7));
                        if (drive.getMainNode().getNode().getGrid() != access.getMainNode().getNode().getGrid())
                            appeng.api.networking.GridHelper.createConnection(drive.getMainNode().getNode(), access.getMainNode().getNode());
                        stack.set(appeng.api.ids.AEComponents.WIRELESS_LINK_TARGET,
                                net.minecraft.core.GlobalPos.of(player.level().dimension(), access.getBlockPos()));
                        stack.set(appeng.api.ids.AEComponents.STORED_ENERGY, 1000000.0);
                        player.getInventory().setItem(1, stack);
                        player.getInventory().setItem(2, new ItemStack(Items.OAK_PLANKS, 4));
                        player.inventoryMenu.broadcastChanges();
                        MenuOpener.open(com.moakiee.ae2lt.menu.TianshuWirelessCraftingTermMenu.TYPE,
                                player, MenuLocators.forInventorySlot(1));
                    });
                }
                case 19 -> {
                    require(mc.screen instanceof com.moakiee.ae2lt.client.TianshuCraftingTermScreen<?>,
                            "Tianshu crafting screen missing");
                    require(craftingMenu().getLinkStatus().connected(), "wireless crafting terminal failed to connect");
                    require(java.math.BigInteger.TEN.pow(100).add(java.math.BigInteger.valueOf(12345))
                            .equals(craftingMenu().getBigStock(AEItemKey.of(Items.OAK_LOG))),
                            "wireless crafting terminal lost exact BigInteger stock");
                    transferCraftingTable(craftingMenu());
                }
                case 20 -> {
                    var menu = craftingMenu();
                    require(menu.getSlots(SlotSemantics.CRAFTING_RESULT).getFirst().getItem().is(Items.CRAFTING_TABLE),
                            "Tianshu JEI crafting result missing");
                    shot("tianshu-crafting-jei");
                    menu.setWorkPage(com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWorkPage.SMITHING);
                }
                case 21, 22, 23, 24 -> {
                    var menu = craftingMenu();
                    var page = switch (phase) {
                        case 21 -> com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWorkPage.SMITHING;
                        case 22 -> com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWorkPage.ANVIL;
                        case 23 -> com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWorkPage.STONECUTTING;
                        default -> com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWorkPage.CELL;
                    };
                    require(menu.workPage == page, "workstation page did not synchronize: " + page);
                    shot("tianshu-workstation-" + page.name().toLowerCase(java.util.Locale.ROOT));
                    if (phase < 24) menu.setWorkPage(switch (phase) {
                        case 21 -> com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWorkPage.ANVIL;
                        case 22 -> com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWorkPage.STONECUTTING;
                        default -> com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWorkPage.CELL;
                    });
                    else server(player -> {
                        player.closeContainer();
                        var level = (ServerLevel) player.level();
                        player.teleportTo(level, 14.5, 110, 7, Set.of(), 0, 0, true);
                        craftingPart = appeng.api.parts.PartHelper.setPart(level, new BlockPos(14, 110, 6),
                                net.minecraft.core.Direction.EAST, player, ModItems.TIANSHU_CRAFTING_TERMINAL.get());
                        require(craftingPart != null, "wired crafting terminal could not be placed");
                    });
                }
                case 25 -> server(player -> {
                    var drive = (appeng.blockentity.storage.DriveBlockEntity) player.level().getBlockEntity(new BlockPos(14, 110, 4));
                    if (drive.getMainNode().getNode().getGrid() != craftingPart.getMainNode().getNode().getGrid())
                        appeng.api.networking.GridHelper.createConnection(drive.getMainNode().getNode(), craftingPart.getMainNode().getNode());
                    MenuOpener.open(com.moakiee.ae2lt.menu.TianshuCraftingTermMenu.TYPE, player, MenuLocators.forPart(craftingPart));
                });
                case 26 -> {
                    require(craftingMenu().getClass() == com.moakiee.ae2lt.menu.TianshuCraftingTermMenu.class,
                            "wired terminal did not open");
                    shot("tianshu-wired-crafting");
                    finished = true;
                    System.out.println(Boolean.getBoolean("ae2lt.portWorkstationOnly")
                            ? "PORT_WORKSTATION_UI_CONFIRMED controller-text+crafting-jei+five-workstation-pages+wired-crafting"
                            : "PORT_CLIENT_SMOKE_CONFIRMED railgun+synced-recipes+jei-previews+pp-menus+return-mode-packets+pigmee-jei-transfer+tianshu-jei-transfer+seed-slot-icons+alloy-models+controller-text+crafting-jei+five-workstation-pages+wired-crafting");
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

    private static com.moakiee.ae2lt.menu.TianshuCraftingTermMenu craftingMenu() {
        var mc = Minecraft.getInstance();
        require(mc.player.containerMenu instanceof com.moakiee.ae2lt.menu.TianshuCraftingTermMenu,
                "crafting menu missing: " + mc.player.containerMenu);
        return (com.moakiee.ae2lt.menu.TianshuCraftingTermMenu) mc.player.containerMenu;
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
