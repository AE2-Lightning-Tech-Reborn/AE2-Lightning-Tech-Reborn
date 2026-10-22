package com.moakiee.ae2lt.debug;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import com.moakiee.ae2lt.celestweave.ArmorEnergyBuffer;
import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;
import com.moakiee.ae2lt.celestweave.PhaseFlightMovementGuard;
import com.moakiee.ae2lt.celestweave.PhaseWingFlight;
import com.moakiee.ae2lt.celestweave.module.PhaseFlightSubmodule;
import com.moakiee.ae2lt.celestweave.module.PhaseLockSubmodule;
import com.moakiee.ae2lt.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ByteTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;

/** Opt-in regression against actual mixed-in entities and the optional, unmodified Tweakerge jar. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class PhaseFreecamClientProbe {
    private static int ticks;
    private static boolean started, done;
    private static volatile boolean serverReady;
    private static volatile Throwable serverFailure;

    private PhaseFreecamClientProbe() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Boolean.getBoolean("ae2lt.phaseFreecamClientProbe") || done) return;
        var mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (++ticks % 20 != 0) return;
        try {
            require(ticks < 1200, "phase-lock network state did not synchronize");
            if (mc.player == null || mc.getSingleplayerServer() == null) return;
            if (serverFailure != null) throw new AssertionError("server fixture failed", serverFailure);
            if (!started) {
                started = true;
                mc.setScreen(null);
                mc.options.renderDistance().set(3);
                var id = mc.player.getUUID();
                mc.getSingleplayerServer().execute(() -> {
                    try {
                        fixture(mc.getSingleplayerServer().getPlayerList().getPlayer(id));
                        serverReady = true;
                    } catch (Throwable failure) {
                        serverFailure = failure;
                    }
                });
                return;
            }
            if (!serverReady || !CelestweaveArmorState.getClientPhaseLockBlockExternalForces()
                    || !CelestweaveArmorState.isAnyClientFlightControlActive()) {
                if (ticks % 100 == 0) record("WAIT ready=" + serverReady
                        + " protection=" + CelestweaveArmorState.getClientPhaseLockBlockExternalForces()
                        + " flight=" + CelestweaveArmorState.isAnyClientFlightControlActive());
                return;
            }

            assertBodyProtection(mc.player);
            if (ModList.get().isLoaded("tweakerge")) testTweakerge(mc);
            testIndependentPlayers(mc);
            assertBodyProtection(mc.player);
            // Exercise the same authorized movement boundary used by vanilla Player.travel.
            var before = mc.player.position();
            PhaseFlightMovementGuard.runAsSelfMovement(mc.player,
                    () -> mc.player.move(MoverType.SELF, new Vec3(0.25, 0, 0)));
            require(mc.player.position().distanceTo(before) > 0.2, "real player's own movement was blocked");
            record("PASS: body protected; own movement allowed; independent client players retain movement"
                    + "; Tweakerge=" + ModList.get().isLoaded("tweakerge"));
        } catch (Throwable failure) {
            failure.printStackTrace();
            record("FAIL: " + failure);
        }
        done = true;
        mc.stop();
    }

    private static void testTweakerge(Minecraft mc) throws ReflectiveOperationException {
        var featureClass = Class.forName("fi.dy.masa.tweakeroo.config.FeatureToggle");
        var toggle = featureClass.getField("TWEAK_FREE_CAMERA").get(null);
        var setEnabled = featureClass.getMethod("setBooleanValue", boolean.class);
        var cameraClass = Class.forName("fi.dy.masa.tweakeroo.util.CameraEntity");
        var movementTick = cameraClass.getMethod("movementTick");
        for (int cycle = 0; cycle < 2; cycle++) {
            setEnabled.invoke(toggle, true);
            try {
                var camera = (Player) mc.getCameraEntity();
                require(camera != mc.player, "Tweakerge did not detach the camera");
                require(camera.getUUID().equals(mc.player.getUUID()), "expected same-profile camera fixture");
                var before = camera.position();
                mc.options.keyUp.setDown(true);
                mc.options.keyJump.setDown(true);
                for (int step = 0; step < 5; step++) movementTick.invoke(null);
                record("Tweakerge cycle=" + cycle + " camera displacement=" + camera.position().subtract(before));
                require(camera.position().distanceTo(before) > 0.1, "Tweakerge camera movement was blocked");
                require(camera.getY() > before.y + 0.1, "Tweakerge camera vertical movement was blocked");
                assertIndependent(camera, "Tweakerge camera");
                assertBodyProtection(mc.player);
            } finally {
                mc.options.keyUp.setDown(false);
                mc.options.keyJump.setDown(false);
                setEnabled.invoke(toggle, false);
            }
            require(mc.getCameraEntity() == mc.player, "camera failed to return to body");
        }
    }

    private static void testIndependentPlayers(Minecraft mc) {
        var body = mc.player;
        var proxy = new LocalPlayer(mc, mc.level, body.connection, body.getStats(), body.getRecipeBook(),
                false, false);
        require(proxy.isLocalPlayer(), "fixture must expose vanilla's broad local-player predicate");
        require(proxy.getUUID().equals(body.getUUID()), "fixture must share the body's UUID");
        assertIndependent(proxy, "same-profile LocalPlayer proxy");
        assertIndependent(new RemotePlayer(mc.level, body.getGameProfile()), "remote player");
    }

    private static void assertIndependent(Player player, String label) {
        require(!PhaseFlightMovementGuard.blocksExternalForces(player), label + " inherited body force guard");
        require(!PhaseFlightMovementGuard.isPhaseModeEnabled(player), label + " inherited body phase mode");
        require(!PhaseWingFlight.canUse(player), label + " inherited body wing authorization");
        player.noPhysics = true;
        player.setPosRaw(1, 104, 1);
        var movement = new Vec3(0.25, 0.5, -0.25);
        player.setDeltaMovement(movement);
        require(player.getDeltaMovement().equals(movement), label + " lost velocity");
        player.move(MoverType.SELF, movement);
        require(player.position().distanceTo(new Vec3(1.25, 104.5, 0.75)) < 1.0E-6,
                label + " lost movement");
    }

    private static void assertBodyProtection(LocalPlayer body) {
        require(PhaseFlightMovementGuard.blocksExternalForces(body), "real body lost force protection");
        require(PhaseWingFlight.canUse(body), "real body lost wing authorization");
        var position = body.position();
        var velocity = body.getDeltaMovement();
        body.setDeltaMovement(new Vec3(5, 5, 5));
        body.move(MoverType.SELF, new Vec3(2, 2, 2));
        require(body.position().equals(position), "external movement moved the body");
        require(body.getDeltaMovement().equals(velocity), "external velocity moved the body");
    }

    private static void fixture(ServerPlayer player) {
        var level = player.serverLevel();
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, player.getServer());
        for (var pos : BlockPos.betweenClosed(-4, 99, -4, 4, 106, 4)) {
            level.setBlockAndUpdate(pos, pos.getY() == 99
                    ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        player.getInventory().clearContent();
        player.setGameMode(GameType.SURVIVAL);
        player.teleportTo(level, 0.5, 100, 0.5, Set.of(), 0, 0);
        var armor = new ItemStack(ModItems.CELESTWEAVE_CORE.get());
        CelestweaveArmorState.setSlot(armor, player.level().registryAccess(), CelestweaveArmorState.SLOT_CORE,
                new ItemStack(ModItems.ULTIMATE_OVERLOAD_CORE.get()));
        require(CelestweaveArmorState.installOneModule(armor, player.level().registryAccess(),
                new ItemStack(ModItems.CELESTWEAVE_SUBMODULE_PHASE_LOCK.get())), "phase lock install failed");
        PhaseLockSubmodule.INSTANCE.setConfig(armor, PhaseLockSubmodule.ARMOR_LOCK_CONFIG_KEY, ByteTag.valueOf(false));
        PhaseLockSubmodule.INSTANCE.setConfig(armor, PhaseLockSubmodule.BLOCK_EXTERNAL_FORCES_CONFIG_KEY, ByteTag.valueOf(true));
        ArmorEnergyBuffer.write(armor, player.level().registryAccess(), 5_000_000L);
        player.setItemSlot(EquipmentSlot.CHEST, armor);
        var legs = new ItemStack(ModItems.CELESTWEAVE_CONDUIT.get());
        CelestweaveArmorState.setSlot(legs, player.level().registryAccess(), CelestweaveArmorState.SLOT_CORE,
                new ItemStack(ModItems.ULTIMATE_OVERLOAD_CORE.get()));
        require(CelestweaveArmorState.installOneModule(legs, player.level().registryAccess(),
                new ItemStack(ModItems.CELESTWEAVE_SUBMODULE_PHASE_FLIGHT.get())), "phase flight install failed");
        CelestweaveArmorState.setSubmoduleEnabled(legs, PhaseFlightSubmodule.INSTANCE, true);
        ArmorEnergyBuffer.write(legs, player.level().registryAccess(), 5_000_000L);
        player.setItemSlot(EquipmentSlot.LEGS, legs);
    }

    private static void record(String message) {
        System.out.println("PHASE_FREECAM_CLIENT " + message);
        try {
            var output = Path.of(System.getProperty("ae2lt.phaseFreecamOutput"));
            Files.createDirectories(output);
            Files.writeString(output.resolve("result.txt"), message + System.lineSeparator(),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (java.io.IOException failure) {
            throw new RuntimeException(failure);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
