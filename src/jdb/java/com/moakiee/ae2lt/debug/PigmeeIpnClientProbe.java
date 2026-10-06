package com.moakiee.ae2lt.debug;

import appeng.api.stacks.AEItemKey;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.RepoSlot;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import com.moakiee.ae2lt.blockentity.PigmeeSynthesisStationBlockEntity;
import com.moakiee.ae2lt.client.machine.PigmeeSynthesisStationScreen;
import com.moakiee.ae2lt.menu.PigmeeSynthesisStationMenu;
import com.moakiee.ae2lt.registry.ModBlocks;
import java.nio.file.Files;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in probe: real IPN swipe callbacks, AE2 screen dispatch, and integrated-server packets. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class PigmeeIpnClientProbe {
    private static final BlockPos POS = new BlockPos(0, 100, 0);
    private static final String MODE = System.getProperty("ae2lt.pigmeeIpnMode", "");
    private static int phase;
    private static int ticks;
    private static int startupTicks;
    private static boolean finished;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (MODE.isEmpty() || finished) return;
        var mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) {
            mc.screen.onClose();
        }
        if (mc.player == null || mc.getSingleplayerServer() == null) {
            if (++startupTicks > 2400) finish("FAIL waiting for test world: " + mc.screen);
            return;
        }
        if (++ticks % 30 != 0) return;
        try {
            if (phase == 0) {
                mc.options.pauseOnLostFocus = false;
                mc.getSingleplayerServer().execute(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    var level = player.serverLevel();
                    player.getInventory().clearContent();
                    player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 7));
                    player.containerMenu.setCarried(ItemStack.EMPTY);
                    level.setBlockAndUpdate(POS, Blocks.AIR.defaultBlockState());
                    level.setBlockAndUpdate(POS.below(), Blocks.CHEST.defaultBlockState());
                    var chest = (ChestBlockEntity) level.getBlockEntity(POS.below());
                    chest.clearContent();
                    chest.setItem(0, new ItemStack(Items.IRON_INGOT, 64));
                    chest.setItem(1, new ItemStack(Items.OAK_LOG, 16));
                    level.setBlockAndUpdate(POS, ModBlocks.PIGMEE_SYNTHESIS_STATION.get().defaultBlockState());
                    player.setGameMode(GameType.CREATIVE);
                    player.teleportTo(level, 0.5, 100, 3.5, Set.of(), 180, 15);
                });
            } else if (phase == 1) {
                if (!(mc.level.getBlockEntity(POS) instanceof PigmeeSynthesisStationBlockEntity)) {
                    require(ticks < 1200, "Timed out waiting for station chunk");
                    return;
                }
                mc.getSingleplayerServer().execute(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    var host = (PigmeeSynthesisStationBlockEntity) player.serverLevel().getBlockEntity(POS);
                    MenuOpener.open(PigmeeSynthesisStationMenu.TYPE, player, MenuLocators.forBlockEntity(host));
                });
            } else {
                require(mc.screen instanceof PigmeeSynthesisStationScreen, "Expected station screen: " + mc.screen);
                var screen = (PigmeeSynthesisStationScreen) mc.screen;
                var menu = screen.getMenu();
                switch (phase) {
                    case 2 -> {
                        require(amount(menu, Items.IRON_INGOT) == 64, "Storage synchronized");
                        require(mc.player.getInventory().getItem(0).is(Items.DIAMOND), "Hotbar starts with diamonds");
                        var virtual = repoSlot(menu, Items.IRON_INGOT);
                        require(virtual.index == 0 && menu.slots.get(0) != virtual, "Reproduce virtual/real index alias");
                        if (!MODE.equals("absent")) {
                            swipeCraftingOutput(false);
                            swipe(virtual, false);
                        }
                    }
                    case 3 -> {
                        if (MODE.equals("baseline")) {
                            require(mc.player.getInventory().getItem(0).isEmpty() && amount(menu, Items.DIAMOND) == 7,
                                    "Baseline must reproduce seven hotbar diamonds incorrectly deposited by IPN");
                            finish("PASS baseline: actual IPN swipe moved 7 hotbar diamonds into storage when iron RepoSlot was selected");
                            return;
                        }
                        require(mc.player.getInventory().getItem(0).getCount() == 7 && amount(menu, Items.DIAMOND) == 0,
                                "Virtual swipe must preserve hotbar diamonds");
                        var click = MEStorageScreen.class.getDeclaredMethod("slotClicked", Slot.class, int.class, int.class, ClickType.class);
                        click.setAccessible(true);
                        var virtual = repoSlot(menu, Items.IRON_INGOT);
                        click.invoke(screen, virtual, virtual.index, 0, ClickType.QUICK_MOVE);
                    }
                    case 4 -> {
                        require(amount(menu, Items.IRON_INGOT) == 0 && mc.player.getInventory().countItem(Items.IRON_INGOT) == 64,
                                "Native Shift click must extract exactly 64 iron");
                        require(mc.player.getInventory().getItem(0).is(Items.DIAMOND), "Native extraction preserves hotbar");
                        if (MODE.equals("absent")) {
                            finish("PASS absent: client and station load without IPN; native Shift click extracts 64 iron and preserves hotbar");
                            return;
                        }
                        // Exercise both swipe actions with crafting-result swiping enabled as well.
                        swipeCraftingOutput(true);
                        for (int i = 0; i < 10; i++) {
                            swipe(repoSlot(menu, Items.OAK_LOG), false);
                            swipe(repoSlot(menu, Items.OAK_LOG), true);
                        }
                    }
                    case 5 -> {
                        require(mc.player.getInventory().getItem(0).getCount() == 7 && amount(menu, Items.DIAMOND) == 0,
                                "Repeated Shift/Ctrl-Q swipes must preserve hotbar with crafting-result swiping enabled");
                        swipe(menu.slots.get(0), false);
                    }
                    case 6 -> {
                        require(mc.player.getInventory().getItem(0).isEmpty() && amount(menu, Items.DIAMOND) == 7,
                                "IPN swipe of a real hotbar slot must still deposit all seven diamonds");
                        finish("PASS fixed: native Shift extraction; virtual Shift/Ctrl-Q guard; repeated swipes; crafting-result swipe enabled; real hotbar IPN insertion");
                    }
                    default -> throw new AssertionError("Unexpected phase " + phase);
                }
            }
            phase++;
        } catch (Throwable error) {
            error.printStackTrace();
            finish("FAIL phase=" + phase + ": " + error);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void swipe(Slot slot, boolean drop) throws Exception {
        var handler = Class.forName("org.anti_ad.mc.ipnext.event.MiscHandler");
        var action = handler.getDeclaredMethod("swipeMoving$lambda$" + (drop ? 1 : 0), Slot.class, Screen.class, Set.class);
        action.setAccessible(true);
        var type = (Class<? extends Enum>) Class.forName("org.anti_ad.mc.ipnext.inventory.ContainerType");
        action.invoke(null, slot, Minecraft.getInstance().screen, Set.of(Enum.valueOf(type, "SORTABLE_STORAGE")));
    }

    private static void swipeCraftingOutput(boolean enabled) throws Exception {
        var tweaks = Class.forName("org.anti_ad.mc.ipnext.config.Tweaks");
        var setting = tweaks.getMethod("getSWIPE_MOVE_CRAFTING_RESULT_SLOT")
                .invoke(tweaks.getField("INSTANCE").get(null));
        setting.getClass().getMethod("setValue", boolean.class).invoke(setting, enabled);
    }

    private static RepoSlot repoSlot(PigmeeSynthesisStationMenu menu, Item item) {
        return menu.slots.stream().filter(s -> s instanceof RepoSlot && s.getItem().is(item))
                .map(s -> (RepoSlot) s).findFirst().orElseThrow();
    }

    private static long amount(PigmeeSynthesisStationMenu menu, Item item) {
        return menu.getClientRepo().getAllEntries().stream().filter(e -> e.getWhat().equals(AEItemKey.of(item)))
                .mapToLong(e -> e.getStoredAmount()).sum();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void finish(String result) {
        finished = true;
        System.out.println("PIGMEE_IPN_PROBE " + result);
        var mc = Minecraft.getInstance();
        try {
            Files.writeString(mc.gameDirectory.toPath().resolve("pigmee-ipn-" + MODE + ".txt"), result + "\n");
        } catch (Exception e) {
            e.printStackTrace();
        }
        mc.stop();
    }
}
