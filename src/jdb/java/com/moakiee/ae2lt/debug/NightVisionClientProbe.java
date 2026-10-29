package com.moakiee.ae2lt.debug;

import java.nio.file.Files;
import java.nio.file.Path;

import com.moakiee.ae2lt.celestweave.ArmorEnergyBuffer;
import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;
import com.moakiee.ae2lt.celestweave.module.NightVisionSubmodule;
import com.moakiee.ae2lt.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in real client packet/countdown/renderer regression; uses only its isolated QA world. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class NightVisionClientProbe {
    private static int phase, ticks, phaseTicks, samples, initialDuration, minimumDuration = Integer.MAX_VALUE;
    private static float minimumScale = 1;
    private static boolean done;
    private static volatile boolean pending, stalled, resumed;
    private static volatile Throwable serverFailure;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("ae2lt.nightVisionClientProbe") || done) return;
        var mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        try {
            require(++ticks < 2400, "client probe timed out");
            if (serverFailure != null) throw new AssertionError(serverFailure);
            if (mc.player == null || mc.getSingleplayerServer() == null) return;
            if (phase == 0) {
                require(mc.gameDirectory.toPath().toAbsolutePath().normalize().endsWith("night-vision-client"),
                        "probe requires its isolated game directory");
                mc.setScreen(null);
                onServer(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    player.getInventory().clearContent();
                    player.removeAllEffects();
                    player.setGameMode(GameType.CREATIVE);
                    var helmet = new ItemStack(ModItems.CELESTWEAVE_OCULUS.get());
                    var registries = player.registryAccess();
                    CelestweaveArmorState.setSlot(helmet, registries, CelestweaveArmorState.SLOT_CORE,
                            new ItemStack(ModItems.ULTIMATE_OVERLOAD_CORE.get()));
                    require(CelestweaveArmorState.installOneModule(helmet, registries,
                            new ItemStack(ModItems.ENERGY_MODULE_T3.get())), "energy module rejected");
                    require(CelestweaveArmorState.installOneModule(helmet, registries,
                            new ItemStack(ModItems.CELESTWEAVE_SUBMODULE_NIGHT_VISION.get())), "night vision rejected");
                    ArmorEnergyBuffer.write(helmet, registries, 20_000_000_000L);
                    player.setItemSlot(EquipmentSlot.HEAD, helmet);
                    player.inventoryMenu.broadcastChanges();
                });
                phase = 1;
                return;
            }
            if (phase == 1) {
                if (pending || !mc.player.hasEffect(MobEffects.NIGHT_VISION)) return;
                initialDuration = mc.player.getEffect(MobEffects.NIGHT_VISION).getDuration();
                // Deliberately pause ONLY the server thread, leaving the real client clock running.
                onServer(() -> {
                    stalled = true;
                    try {
                        Thread.sleep(6500);
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(failure);
                    } finally {
                        resumed = true;
                    }
                });
                phase = 2;
                return;
            }
            if (phase == 2) {
                if (stalled && !resumed) {
                    var effect = mc.player.getEffect(MobEffects.NIGHT_VISION);
                    require(effect != null, "night vision expired during the stall");
                    minimumDuration = Math.min(minimumDuration, effect.getDuration());
                    minimumScale = Math.min(minimumScale, GameRenderer.getNightVisionScale(mc.player, 0));
                    samples++;
                }
                if (!resumed || pending) return;
                require(samples >= 105, "insufficient client ticks during server stall: " + samples);
                boolean expectFlicker = Boolean.getBoolean("ae2lt.nightVisionExpectFlicker");
                require(expectFlicker ? minimumScale < 0.6F : minimumScale >= 0.999F,
                        "unexpected renderer scale: " + minimumScale + ", expected flicker=" + expectFlicker);
                onServer(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    var helmet = player.getItemBySlot(EquipmentSlot.HEAD);
                    require(ArmorEnergyBuffer.read(helmet) > 1_000_000_000L, "helmet unexpectedly ran out of energy");
                    CelestweaveArmorState.setSubmoduleEnabled(helmet, NightVisionSubmodule.INSTANCE, false);
                });
                phase = 3;
                return;
            }
            if (phase == 3 && !pending) {
                require(++phaseTicks < 100, "disabled night vision did not disappear from the client");
                if (!mc.player.hasEffect(MobEffects.NIGHT_VISION)) finish(true, "disabled effect removed");
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(false, failure.toString());
        }
    }

    private static void onServer(Runnable work) {
        pending = true;
        Minecraft.getInstance().getSingleplayerServer().execute(() -> {
            try {
                work.run();
            } catch (Throwable failure) {
                serverFailure = failure;
            } finally {
                pending = false;
            }
        });
    }

    private static void finish(boolean success, String message) {
        if (done) return;
        done = true;
        String report = "NIGHT_VISION_CLIENT_" + (success ? "PASS" : "FAIL")
                + " expectedFlicker=" + Boolean.getBoolean("ae2lt.nightVisionExpectFlicker")
                + " initialDuration=" + initialDuration + " samples=" + samples
                + " minimumDuration=" + minimumDuration + " minimumRendererScale=" + minimumScale
                + " " + message + "\n";
        System.out.println(report);
        try {
            Files.writeString(Path.of("night-vision-client-probe.txt"), report);
        } catch (Exception failure) {
            throw new RuntimeException(failure);
        } finally {
            Minecraft.getInstance().stop();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
