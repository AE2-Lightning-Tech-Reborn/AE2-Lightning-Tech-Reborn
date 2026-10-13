package com.moakiee.ae2lt.debug;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import com.moakiee.ae2lt.celestweave.ArmorEnergyBuffer;
import com.moakiee.ae2lt.celestweave.BaseCelestweaveArmorItem;
import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;
import com.moakiee.ae2lt.celestweave.module.PhaseLockSubmodule;
import com.moakiee.ae2lt.celestweave.phase.CelestweaveEquipmentAccess;
import com.moakiee.ae2lt.celestweave.phase.PhaseLockService;
import com.moakiee.ae2lt.item.PhaseLockProjectionItem;
import com.moakiee.ae2lt.registry.ModDataComponents;
import com.moakiee.ae2lt.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ByteTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;

/** Opt-in real client/network/render verification, in an isolated disposable save. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class PhaseLockHudClientProbe {
    private static final List<EquipmentSlot> SLOTS = List.of(
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
    private static int ticks, phase, layerHeightBefore, hudX, hudY, renderedAt;
    private static boolean done, drewEnergyBar;
    private static volatile boolean serverReady;
    private static volatile Throwable serverFailure;
    private static String pendingCapture;

    private PhaseLockHudClientProbe() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !enabled() || done) return;
        var mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.player == null || mc.getSingleplayerServer() == null || ++ticks % 40 != 0) return;
        try {
            require(ticks < 1600, "client state did not synchronize before timeout");
            if (serverFailure != null) throw new AssertionError("server fixture failed", serverFailure);
            if (pendingCapture != null) return;
            switch (phase) {
                case 0 -> {
                    mc.getWindow().setTitle("AE2LT Phase Lock HUD Test");
                    mc.options.guiScale().set(2);
                    mc.options.renderDistance().set(4);
                    mc.options.hideGui = false;
                    mc.resizeDisplay();
                    mc.setScreen(null);
                    server(PhaseLockHudClientProbe::fixture);
                }
                case 1 -> {
                    if (!serverReady || !clientEnergy(false, 5_000_000L)) return;
                    captureHud("01-unlocked-50.png");
                }
                case 2 -> server(player -> setArmorLock(player, true));
                case 3 -> {
                    if (!serverReady || !clientEnergy(true, 5_000_000L)) return;
                    captureHud("02-locked-50.png");
                }
                case 4 -> server(player -> setEnergy(player, 2_500_000L));
                case 5 -> {
                    if (!serverReady || !clientEnergy(true, 2_500_000L)) return;
                    captureHud("03-locked-25.png");
                }
                case 6 -> mc.setScreen(new InventoryScreen(mc.player));
                case 7 -> {
                    require(mc.screen instanceof InventoryScreen, "inventory screen did not open");
                    require(clientEnergy(true, 2_500_000L), "inventory lost locked armor state");
                    pendingCapture = "04-projection-inventory.png";
                }
                case 8 -> {
                    mc.setScreen(null);
                    server(player -> setEnergy(player, 0L));
                }
                case 9 -> {
                    if (!serverReady || !clientEnergy(true, 0L)) return;
                    captureHud("05-locked-empty.png");
                }
                case 10 -> server(player -> {
                    setEnergy(player, 2_500_000L);
                    setArmorLock(player, false);
                });
                case 11 -> {
                    if (!serverReady || !clientEnergy(false, 2_500_000L)) return;
                    captureHud("06-unlocked-25.png");
                }
                case 12 -> {
                    record("PASS: live client HUD remains visible before/after armor lock, updates from 50% to 25% to zero, restores on unlock; projection item bars stay hidden.");
                    done = true;
                    mc.stop();
                }
                default -> throw new AssertionError("unexpected phase " + phase);
            }
            phase++;
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    @SubscribeEvent
    public static void beforeLayer(RenderGuiOverlayEvent.Pre event) {
        if (enabled() && event.getOverlay().id().toString().equals("ae2lt:celestweave_energy_level")) {
            var mc = Minecraft.getInstance();
            layerHeightBefore = ((ForgeGui) mc.gui).leftHeight;
            hudX = event.getGuiGraphics().guiWidth() / 2 - 91;
            hudY = event.getGuiGraphics().guiHeight() - layerHeightBefore + 2;
        }
    }

    @SubscribeEvent
    public static void afterLayer(RenderGuiOverlayEvent.Post event) {
        if (enabled() && event.getOverlay().id().toString().equals("ae2lt:celestweave_energy_level")) {
            drewEnergyBar = ((ForgeGui) Minecraft.getInstance().gui).leftHeight == layerHeightBefore + 8;
            renderedAt = ticks;
        }
    }

    @SubscribeEvent
    public static void afterFrame(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !enabled() || done || pendingCapture == null) return;
        try {
            var output = outputDirectory();
            Files.createDirectories(output);
            try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
                image.writeToFile(output.resolve(pendingCapture));
            }
            record("CAPTURE " + pendingCapture + " HUD origin=" + hudX + "," + hudY);
            pendingCapture = null;
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    private static void captureHud(String filename) {
        require(drewEnergyBar && ticks - renderedAt <= 2, "real HUD layer skipped energy rendering");
        require(Minecraft.getInstance().screen == null, "HUD capture has an open screen");
        pendingCapture = filename;
    }

    private static boolean clientEnergy(boolean locked, long expectedStored) {
        var mc = Minecraft.getInstance();
        if (!mc.gameMode.canHurtPlayer()) return false;
        for (EquipmentSlot slot : SLOTS) {
            var stack = mc.player.getItemBySlot(slot);
            if (locked) {
                if (!(stack.getItem() instanceof PhaseLockProjectionItem)) return false;
                var snapshot = ModDataComponents.PHASE_LOCK_ENERGY_SNAPSHOT.get(stack);
                if (snapshot == null || snapshot.capacity() != 10_000_000L || snapshot.stored() != expectedStored) {
                    return false;
                }
                require(!stack.isBarVisible(), "projection item bar is visible: " + slot);
            } else {
                if (!(stack.getItem() instanceof BaseCelestweaveArmorItem)
                        || ArmorEnergyBuffer.read(stack) != expectedStored) return false;
            }
        }
        return true;
    }

    private static void fixture(ServerPlayer player) {
        var level = player.serverLevel();
        var server = player.getServer();
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        level.setDayTime(6000);
        level.setWeatherParameters(6000, 0, false, false);
        for (var pos : BlockPos.betweenClosed(-5, 99, -5, 5, 105, 5)) {
            level.setBlockAndUpdate(pos, pos.getY() == 99
                    ? Blocks.WHITE_CONCRETE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        PhaseLockService.release(player);
        player.getInventory().clearContent();
        for (EquipmentSlot slot : SLOTS) {
            var armor = new ItemStack(switch (slot) {
                case HEAD -> ModItems.CELESTWEAVE_OCULUS.get();
                case CHEST -> ModItems.CELESTWEAVE_CORE.get();
                case LEGS -> ModItems.CELESTWEAVE_CONDUIT.get();
                case FEET -> ModItems.CELESTWEAVE_STRIDE.get();
                default -> throw new AssertionError(slot);
            });
            CelestweaveArmorState.setSlot(armor, player.level().registryAccess(), CelestweaveArmorState.SLOT_CORE,
                    new ItemStack(ModItems.ULTIMATE_OVERLOAD_CORE.get()));
            if (slot == EquipmentSlot.CHEST) {
                require(CelestweaveArmorState.installOneModule(armor, player.level().registryAccess(),
                        new ItemStack(ModItems.CELESTWEAVE_SUBMODULE_PHASE_LOCK.get())), "cannot install phase lock");
                configureArmorLock(armor, false);
                PhaseLockSubmodule.INSTANCE.setConfig(armor, PhaseLockSubmodule.FLIGHT_LOCK_CONFIG_KEY,
                        ByteTag.valueOf(false));
            }
            ArmorEnergyBuffer.write(armor, player.level().registryAccess(), 5_000_000L);
            player.setItemSlot(slot, armor);
        }
        player.setGameMode(GameType.SURVIVAL);
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.teleportTo(level, 0.5, 100, 0.5, Set.of(), 180, 0);
    }

    private static void setEnergy(ServerPlayer player, long energy) {
        for (EquipmentSlot slot : SLOTS) {
            ArmorEnergyBuffer.write(CelestweaveEquipmentAccess.findArmor(player, slot), player.level().registryAccess(), energy);
        }
    }

    private static void setArmorLock(ServerPlayer player, boolean enabled) {
        configureArmorLock(CelestweaveEquipmentAccess.findArmor(player, EquipmentSlot.CHEST), enabled);
        PhaseLockService.tick(player);
    }

    private static void configureArmorLock(ItemStack armor, boolean enabled) {
        require(PhaseLockSubmodule.INSTANCE.setConfig(armor, PhaseLockSubmodule.ARMOR_LOCK_CONFIG_KEY,
                ByteTag.valueOf(enabled)), "cannot set armor lock");
    }

    private static void server(Consumer<ServerPlayer> action) {
        var mc = Minecraft.getInstance();
        var playerId = mc.player.getUUID();
        var server = mc.getSingleplayerServer();
        serverReady = false;
        server.execute(() -> {
            try {
                action.accept(server.getPlayerList().getPlayer(playerId));
                serverReady = true;
            } catch (Throwable failure) {
                serverFailure = failure;
            }
        });
    }

    private static boolean enabled() { return Boolean.getBoolean("ae2lt.phaseLockHudClientProbe"); }
    private static Path outputDirectory() { return Path.of(System.getProperty("ae2lt.phaseLockHudOutput")); }

    private static void record(String message) {
        System.out.println("PHASE_LOCK_HUD_CLIENT " + message);
        try {
            Files.createDirectories(outputDirectory());
            Files.writeString(outputDirectory().resolve("result.txt"), message + System.lineSeparator(),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (java.io.IOException failure) {
            throw new RuntimeException(failure);
        }
    }

    private static void fail(Throwable failure) {
        failure.printStackTrace();
        record("FAIL phase=" + phase + ": " + failure);
        done = true;
        Minecraft.getInstance().stop();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
