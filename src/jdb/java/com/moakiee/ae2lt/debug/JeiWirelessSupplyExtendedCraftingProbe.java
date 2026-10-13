package com.moakiee.ae2lt.debug;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageCells;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import com.blakebr0.extendedcrafting.compat.jei.category.table.AdvancedTableCategory;
import com.blakebr0.extendedcrafting.container.AdvancedTableContainer;
import com.blakebr0.extendedcrafting.crafting.recipe.ShapedTableRecipe;
import com.moakiee.ae2lt.config.AE2LTClientConfig;
import com.moakiee.ae2lt.integration.ae2wtlib.Ae2wtlibIntegration;
import com.moakiee.ae2lt.integration.ae2wtlib.TianshuWirelessIngredientSource;
import de.mari_023.ae2wtlib.AE2wtlib;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import mezz.jei.common.Internal;
import mezz.jei.common.transfer.RecipeTransferUtil;
import mezz.jei.gui.recipes.RecipeTransferButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.network.NetworkHooks;

/** Disposable client/server verification of the real Extended Crafting transfer path. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class JeiWirelessSupplyExtendedCraftingProbe {
    private static final BlockPos NETWORK = new BlockPos(0, 100, 0);
    private static final BlockPos TABLE = NETWORK.south();
    private static int ticks, phase;
    private static boolean done;
    private static volatile Throwable failure;
    private static volatile boolean verified;
    private static mezz.jei.api.gui.IRecipeLayoutDrawable<com.blakebr0.extendedcrafting.api.crafting.ITableRecipe> layout;
    private static RecipeTransferButton button;
    private static AbstractContainerScreen<?> parentScreen;
    private static Object transferService;

    private static Class<?> transferEntry() throws Exception {
        try { return Class.forName("mezz.jei.common.transfer.RecipeTransferService"); }
        catch (ClassNotFoundException legacy) { return RecipeTransferUtil.class; }
    }

    private static RecipeTransferButton createButton() throws Exception {
        var entry = transferEntry();
        if (entry == RecipeTransferUtil.class) {
            return (RecipeTransferButton) RecipeTransferButton.class.getMethod("create",
                    mezz.jei.api.gui.IRecipeLayoutDrawable.class, Runnable.class).invoke(null, layout, (Runnable) () -> {});
        }
        transferService = entry.getConstructor(mezz.jei.api.recipe.transfer.IRecipeTransferManager.class)
                .newInstance(Internal.getJeiRuntime().getRecipeTransferManager());
        return (RecipeTransferButton) RecipeTransferButton.class.getMethod("create",
                mezz.jei.api.gui.IRecipeLayoutDrawable.class, entry, java.util.function.Supplier.class, Runnable.class)
                .invoke(null, layout, transferService, (java.util.function.Supplier<AbstractContainerScreen<?>>) () -> parentScreen,
                        (Runnable) () -> {});
    }

    private static boolean transfer() throws Exception {
        var mc = Minecraft.getInstance();
        if (transferService != null) {
            return (boolean) transferService.getClass().getMethod("transferRecipe", AbstractContainerScreen.class,
                    mezz.jei.api.gui.IRecipeLayoutDrawable.class, net.minecraft.world.entity.player.Player.class, boolean.class)
                    .invoke(transferService, parentScreen, layout, mc.player, false);
        }
        return (boolean) RecipeTransferUtil.class.getMethod("transferRecipe", mezz.jei.api.recipe.transfer.IRecipeTransferManager.class,
                net.minecraft.world.inventory.AbstractContainerMenu.class, mezz.jei.api.gui.IRecipeLayoutDrawable.class,
                net.minecraft.world.entity.player.Player.class, boolean.class).invoke(null,
                Internal.getJeiRuntime().getRecipeTransferManager(), mc.player.containerMenu, layout, mc.player, false);
    }

    private static ShapedTableRecipe recipe() {
        return new ShapedTableRecipe(new ResourceLocation("ae2lt_jei_supply", "advanced_refill"), 5, 5,
                NonNullList.withSize(25, Ingredient.of(Items.OAK_PLANKS)), new ItemStack(Items.DIAMOND), 2);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done || !Boolean.getBoolean("ae2lt.extendedCraftingSupplyProbe")) return;
        var mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.player == null || mc.getSingleplayerServer() == null) return;
        ticks++;
        if (ticks % 100 == 0) System.out.println("EXTENDED_CRAFTING_PROBE phase=" + phase + " ticks=" + ticks);
        if (phase != 3 && phase != 7 && phase != 9 && ticks % 20 != 0) return;
        try {
            if (failure != null) throw new AssertionError(failure);
            require(ticks < 1800, "probe timeout at phase " + phase);
            switch (phase) {
                case 0 -> {
                    AE2LTClientConfig.setJeiWirelessSupply(false);
                    require(Arrays.stream(transferEntry().getDeclaredMethods())
                            .anyMatch(method -> method.getName().contains("ae2lt$supply")), "wireless hook was not injected");
                    server(() -> {
                        var level = mc.getSingleplayerServer().overworld();
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        player.setGameMode(GameType.CREATIVE);
                        player.getAbilities().flying = true;
                        player.onUpdateAbilities();
                        player.teleportTo(level, .5, 101, 2.5, Set.of(), 180, 30);
                        level.setBlockAndUpdate(NETWORK, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
                        level.setBlockAndUpdate(NETWORK.east(), AEBlocks.DRIVE.block().defaultBlockState());
                        level.setBlockAndUpdate(NETWORK.north(), AEBlocks.WIRELESS_ACCESS_POINT.block().defaultBlockState());
                        level.setBlockAndUpdate(TABLE, com.blakebr0.extendedcrafting.init.ModBlocks.ADVANCED_TABLE.get().defaultBlockState());
                        var tableInventory = ((com.blakebr0.extendedcrafting.tileentity.AdvancedTableTileEntity)
                                level.getBlockEntity(TABLE)).getInventory();
                        for (int i = 0; i < 25; i++) tableInventory.setStackInSlot(i, ItemStack.EMPTY);
                        var cell = AEItems.ITEM_CELL_1K.stack();
                        var inventory = StorageCells.getCellInventory(cell, null);
                        inventory.insert(AEItemKey.of(Items.OAK_PLANKS), 256, Actionable.MODULATE, IActionSource.ofPlayer(player));
                        inventory.persist();
                        ((DriveBlockEntity) level.getBlockEntity(NETWORK.east())).getInternalInventory().setItemDirect(0, cell);
                        var universal = new ItemStack(AE2wtlib.UNIVERSAL_TERMINAL);
                        universal.getOrCreateTag().putBoolean(Ae2wtlibIntegration.TIANSHU_TERMINAL_NAME, true);
                        universal.getOrCreateTag().putDouble("internalCurrentPower", 1000000);
                        universal.getOrCreateTag().put("accessPoint", GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE,
                                GlobalPos.of(level.dimension(), NETWORK.north())).result().orElseThrow());
                        player.getInventory().clearContent();
                        player.getInventory().setItem(0, universal);
                        var recipes = new ArrayList<>(level.getRecipeManager().getRecipes());
                        recipes.add(recipe());
                        level.getRecipeManager().replaceRecipes(recipes);
                    });
                    phase++;
                    System.out.println("EXTENDED_CRAFTING_PROBE fixture ready");
                }
                case 1 -> {
                    if (ticks < 100) return;
                    mc.setScreen(null);
                    server(() -> {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        NetworkHooks.openScreen(player, (MenuProvider) player.level().getBlockEntity(TABLE), TABLE);
                    });
                    phase++;
                    System.out.println("EXTENDED_CRAFTING_PROBE opening advanced table");
                }
                case 2 -> {
                    if (!(mc.player.containerMenu instanceof AdvancedTableContainer)) return;
                    var runtime = Internal.getJeiRuntime();
                    var manager = runtime.getRecipeManager();
                    var category = manager.getRecipeCategory(AdvancedTableCategory.RECIPE_TYPE);
                    manager.addRecipes(AdvancedTableCategory.RECIPE_TYPE, List.of(recipe()));
                    layout = manager.createRecipeLayoutDrawable(category, recipe(),
                            runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).orElseThrow();
                    parentScreen = (AbstractContainerScreen<?>) mc.screen;
                    button = createButton();
                    button.update(mc.player.containerMenu, mc.player);
                    require(!active(), "disabled wireless option should leave the missing-material button inactive");
                    AE2LTClientConfig.setJeiWirelessSupply(true);
                    runtime.getRecipesGui().showRecipes(category, List.of(recipe()), List.of());
                    phase++;
                    System.out.println("EXTENDED_CRAFTING_PROBE waiting for wireless offer");
                }
                case 3 -> {
                    button.update(mc.player.containerMenu, mc.player);
                    if (!active()) return;
                    require(mc.player.getInventory().countItem(Items.OAK_PLANKS) == 0, "preview extracted materials");
                    boolean complete = transfer();
                    require(!complete, "async refill must wait for server acknowledgement");
                    phase++;
                    System.out.println("EXTENDED_CRAFTING_PROBE button active, transfer requested");
                }
                case 4 -> {
                    server(() -> {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        require(player.containerMenu instanceof AdvancedTableContainer, "advanced menu was replaced");
                        for (int i = 1; i <= 25; i++) {
                            var stack = player.containerMenu.getSlot(i).getItem();
                            require(stack.is(Items.OAK_PLANKS) && stack.getCount() == 1, "native transfer did not fill slot " + i);
                        }
                        require(player.containerMenu.getSlot(0).getItem().is(Items.DIAMOND), "real advanced crafting output missing");
                        require(player.getInventory().countItem(Items.OAK_PLANKS) == 0, "refill took excess materials");
                        var host = TianshuWirelessIngredientSource.open(player, TianshuWirelessIngredientSource.locate(player).get(0));
                        require(host.getInventory().extract(AEItemKey.of(Items.OAK_PLANKS), 999,
                                Actionable.SIMULATE, IActionSource.ofPlayer(player)) == 231, "network stock was not conserved");
                        require(player.getInventory().getItem(0).getOrCreateTag().getDouble("internalCurrentPower") < 1000000,
                                "terminal did not consume energy");
                        verified = true;
                    });
                    phase++;
                }
                case 5 -> {
                    if (!verified) return;
                    verified = false;
                    com.moakiee.ae2lt.client.compat.JeiWirelessSupplyClient.clear();
                    server(() -> {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        var level = player.serverLevel();
                        var tableInventory = ((com.blakebr0.extendedcrafting.tileentity.AdvancedTableTileEntity)
                                level.getBlockEntity(TABLE)).getInventory();
                        for (int i = 0; i < 25; i++) tableInventory.setStackInSlot(i, ItemStack.EMPTY);
                        level.setBlockAndUpdate(NETWORK.west(), AEBlocks.PATTERN_PROVIDER.block().defaultBlockState());
                        level.setBlockAndUpdate(NETWORK.west(2), AEBlocks.MOLECULAR_ASSEMBLER.block().defaultBlockState());
                        level.setBlockAndUpdate(NETWORK.below(), AEBlocks.CRAFTING_STORAGE_4K.block().defaultBlockState());
                        var ingredients = new ItemStack[9]; Arrays.fill(ingredients, ItemStack.EMPTY);
                        ingredients[0] = new ItemStack(Items.OAK_LOG);
                        var planks = (net.minecraft.world.item.crafting.CraftingRecipe) level.getRecipeManager()
                                .byKey(new ResourceLocation("minecraft", "oak_planks")).orElseThrow();
                        ((appeng.blockentity.crafting.PatternProviderBlockEntity) level.getBlockEntity(NETWORK.west()))
                                .getLogic().getPatternInv().setItemDirect(0, appeng.api.crafting.PatternDetailsHelper.encodeCraftingPattern(
                                        planks, ingredients, new ItemStack(Items.OAK_PLANKS, 4), false, false));
                        var host = TianshuWirelessIngredientSource.open(player, TianshuWirelessIngredientSource.locate(player).get(0));
                        var storage = host.getInventory();
                        storage.extract(AEItemKey.of(Items.OAK_PLANKS), 9999, Actionable.MODULATE, IActionSource.ofPlayer(player));
                        storage.insert(AEItemKey.of(Items.OAK_PLANKS), 23, Actionable.MODULATE, IActionSource.ofPlayer(player));
                        storage.insert(AEItemKey.of(Items.OAK_LOG), 64, Actionable.MODULATE, IActionSource.ofPlayer(player));
                        player.containerMenu.broadcastChanges();
                    });
                    phase++;
                    System.out.println("EXTENDED_CRAFTING_PROBE partial/crafting fixture ready");
                }
                case 6 -> {
                    var runtime = Internal.getJeiRuntime();
                    runtime.getRecipesGui().showRecipes(runtime.getRecipeManager().getRecipeCategory(AdvancedTableCategory.RECIPE_TYPE),
                            List.of(recipe()), List.of());
                    phase++;
                }
                case 7 -> {
                    button.update(mc.player.containerMenu, mc.player);
                    if (!active() || !missingReady()) return;
                    require(!net.minecraft.client.gui.screens.Screen.hasControlDown(), "ordinary click fixture needs Ctrl released");
                    require(!transfer(), "partial refill must wait for the real server");
                    phase++;
                }
                case 8 -> {
                    server(() -> {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        require(player.containerMenu instanceof AdvancedTableContainer, "ordinary + started crafting");
                        int filled = 0;
                        for (int i = 1; i <= 25; i++) if (player.containerMenu.getSlot(i).getItem().is(Items.OAK_PLANKS)) filled++;
                        require(filled == 23 && player.containerMenu.getSlot(0).getItem().isEmpty(), "ordinary + must transfer available ingredients");
                        require(player.getInventory().countItem(Items.OAK_PLANKS) == 0, "partial refill must not leave extra items");
                        verified = true;
                    });
                    var runtime = Internal.getJeiRuntime();
                    runtime.getRecipesGui().showRecipes(runtime.getRecipeManager().getRecipeCategory(AdvancedTableCategory.RECIPE_TYPE),
                            List.of(recipe()), List.of());
                    phase++;
                }
                case 9 -> {
                    if (!verified) return;
                    button.update(mc.player.containerMenu, mc.player);
                    if (!active() || !requestCrafting()) return;
                    verified = false;
                    phase++;
                }
                case 10 -> {
                    if (verified) { phase++; return; }
                    if (!(mc.player.containerMenu instanceof appeng.menu.me.crafting.CraftConfirmMenu)) return;
                    server(() -> {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        var confirm = (appeng.menu.me.crafting.CraftConfirmMenu) player.containerMenu;
                        try {
                            var amount = confirm.getClass().getDeclaredField("amount"); amount.setAccessible(true);
                            var result = confirm.getClass().getDeclaredField("result"); result.setAccessible(true);
                            require(amount.getInt(confirm) == 2 && !confirm.autoStart, "native confirmation must request the two missing items");
                            if (result.get(confirm) == null || confirm.noCPU) return;
                            require(!((appeng.api.networking.crafting.ICraftingPlan) result.get(confirm)).simulation(), "native plan must be executable");
                            confirm.startJob();
                            verified = true;
                        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
                    });
                }
                case 11 -> {
                    server(() -> {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        var host = TianshuWirelessIngredientSource.open(player, TianshuWirelessIngredientSource.locate(player).get(0));
                        if (host.getInventory().extract(AEItemKey.of(Items.OAK_PLANKS), 99,
                                Actionable.SIMULATE, IActionSource.ofPlayer(player)) < 2) return;
                        require(host.getInventory().extract(AEItemKey.of(Items.OAK_LOG), 999,
                                Actionable.SIMULATE, IActionSource.ofPlayer(player)) == 63, "native assembler must consume exactly one log");
                        verified = true;
                        phase = 12;
                    });
                }
                case 12 -> {
                    server(() -> {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        var tableInventory = ((com.blakebr0.extendedcrafting.tileentity.AdvancedTableTileEntity)
                                player.level().getBlockEntity(TABLE)).getInventory();
                        for (int i = 0; i < 25; i++) tableInventory.setStackInSlot(i, ItemStack.EMPTY);
                        var host = TianshuWirelessIngredientSource.open(player, TianshuWirelessIngredientSource.locate(player).get(0));
                        host.getInventory().extract(AEItemKey.of(Items.OAK_PLANKS), 9999, Actionable.MODULATE, IActionSource.ofPlayer(player));
                        host.getInventory().insert(AEItemKey.of(Items.OAK_PLANKS), 23, Actionable.MODULATE, IActionSource.ofPlayer(player));
                        NetworkHooks.openScreen(player, (MenuProvider) player.level().getBlockEntity(TABLE), TABLE);
                    });
                    verified = false;
                    com.moakiee.ae2lt.client.compat.JeiWirelessSupplyClient.clear();
                    phase++;
                }
                case 13 -> {
                    if (!(mc.player.containerMenu instanceof AdvancedTableContainer)) return;
                    parentScreen = (AbstractContainerScreen<?>) mc.screen;
                    button = createButton();
                    var runtime = Internal.getJeiRuntime();
                    runtime.getRecipesGui().showRecipes(runtime.getRecipeManager().getRecipeCategory(AdvancedTableCategory.RECIPE_TYPE),
                            List.of(recipe()), List.of());
                    phase++;
                }
                case 14 -> {
                    button.update(mc.player.containerMenu, mc.player);
                    if (!active() || !requestCrafting()) return;
                    phase++;
                }
                case 15 -> {
                    if (!(mc.player.containerMenu instanceof appeng.menu.me.crafting.CraftConfirmMenu)) return;
                    server(() -> {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        var tableInventory = ((com.blakebr0.extendedcrafting.tileentity.AdvancedTableTileEntity)
                                player.level().getBlockEntity(TABLE)).getInventory();
                        int filled = 0;
                        for (int i = 0; i < 25; i++) if (tableInventory.getStackInSlot(i).is(Items.OAK_PLANKS)) filled++;
                        require(filled == 23 && player.getInventory().countItem(Items.OAK_PLANKS) == 0,
                                "Ctrl must fill the available ingredients before opening confirmation");
                        try {
                            var confirm = player.containerMenu;
                            var amount = confirm.getClass().getDeclaredField("amount"); amount.setAccessible(true);
                            require(amount.getInt(confirm) == 2, "Ctrl prefill must request only the deficit");
                        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
                        verified = true;
                    });
                    phase++;
                }
                case 16 -> {
                    if (!verified) return;
                    Files.writeString(mc.gameDirectory.toPath().resolve("extended-crafting-supply-result.txt"),
                            "PASS: real WUT, advanced table, stock/energy conservation, ordinary partial +, Ctrl prefill, native crafting queue, exact deficit, CPU and molecular assembler output\n");
                    System.out.println("EXTENDED_CRAFTING_WIRELESS_SUPPLY_PASS");
                    done = true;
                    mc.setScreen(null);
                    mc.stop();
                }
            }
        } catch (Throwable error) {
            error.printStackTrace();
            try { Files.writeString(mc.gameDirectory.toPath().resolve("extended-crafting-supply-result.txt"), "FAIL: " + error); }
            catch (Exception ignored) {}
            done = true;
            mc.setScreen(null);
            mc.stop();
        }
    }

    private static boolean active() throws Exception {
        var field = RecipeTransferButton.class.getSuperclass().getDeclaredField("button");
        field.setAccessible(true);
        return ((Button) field.get(button)).active;
    }

    private static Object missingOffer() throws Exception {
        var type = com.moakiee.ae2lt.client.compat.JeiWirelessSupplyClient.class;
        var offers = type.getDeclaredField("OFFERS"); offers.setAccessible(true);
        for (var offer : ((java.util.Map<?, ?>) offers.get(null)).values()) {
            var plan = offer.getClass().getDeclaredField("missingPlan"); plan.setAccessible(true);
            if (plan.get(offer) != null) return offer;
        }
        return null;
    }

    private static boolean missingReady() throws Exception { return missingOffer() != null; }

    /** Supplies the sampled Ctrl flag without injecting operating-system keyboard events. */
    @SuppressWarnings("unchecked")
    private static boolean requestCrafting() throws Exception {
        var offer = missingOffer();
        if (offer == null) return false;
        var type = com.moakiee.ae2lt.client.compat.JeiWirelessSupplyClient.class;
        var offers = type.getDeclaredField("OFFERS"); offers.setAccessible(true);
        var map = (java.util.Map<?, ?>) offers.get(null);
        var key = map.entrySet().stream().filter(e -> e.getValue() == offer).findFirst().orElseThrow().getKey();
        var handlerMethod = key.getClass().getDeclaredMethod("handler"); handlerMethod.setAccessible(true);
        var handler = (mezz.jei.api.recipe.transfer.IRecipeTransferHandler<net.minecraft.world.inventory.AbstractContainerMenu, Object>) handlerMethod.invoke(key);
        var mc = Minecraft.getInstance();
        var pendingType = Arrays.stream(type.getDeclaredClasses()).filter(c -> c.getSimpleName().equals("Pending")).findFirst().orElseThrow();
        var constructor = pendingType.getDeclaredConstructors()[0]; constructor.setAccessible(true);
        com.moakiee.ae2lt.client.compat.JeiWirelessSupplyClient.NativeTransfer nativeTransfer = (maximum, take, slots) ->
                handler.transferRecipe(mc.player.containerMenu, layout.getRecipe(),
                        slots == null ? layout.getRecipeSlotsView() : slots, mc.player, maximum, take);
        var operation = constructor.newInstance(handler, mc.player.containerMenu, layout.getRecipe(), layout.getRecipeSlotsView(),
                mc.player, false, mc.screen, offer, false, true, true, nativeTransfer);
        var planField = offer.getClass().getDeclaredField("missingPlan"); planField.setAccessible(true);
        var plan = planField.get(offer);
        var storedMethod = plan.getClass().getDeclaredMethod("stored"); storedMethod.setAccessible(true);
        var stored = (List<ItemStack>) storedMethod.invoke(plan);
        if (stored.isEmpty()) {
            var finish = type.getDeclaredMethod("finishPartial", pendingType); finish.setAccessible(true);
            require(finish.invoke(null, operation) == null, "Ctrl request could not use the native partial transfer");
        } else {
            var take = type.getDeclaredMethod("beginTake", pendingType, List.class, boolean.class); take.setAccessible(true);
            take.invoke(null, operation, stored, true);
        }
        return true;
    }

    private static void server(Runnable action) {
        Minecraft.getInstance().getSingleplayerServer().execute(() -> {
            try { action.run(); } catch (Throwable error) { failure = error; }
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
