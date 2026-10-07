package com.moakiee.ae2lt.client.railgun;

import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import appeng.api.config.Actionable;
import appeng.api.ids.AEComponents;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.StorageCells;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.item.railgun.RailgunModuleStorage;
import com.moakiee.ae2lt.item.railgun.RailgunSettings;
import com.moakiee.ae2lt.item.railgun.RailgunStructuralCore;
import com.moakiee.ae2lt.logic.railgun.RailgunBinding;
import com.moakiee.ae2lt.logic.railgun.RailgunEnergyBuffer;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.network.railgun.RailgunBeamChainFxPacket;
import com.moakiee.ae2lt.network.railgun.RailgunBeamUpdatePacket;
import com.moakiee.ae2lt.registry.ModDataComponents;
import com.moakiee.ae2lt.registry.ModItems;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Opt-in disposable-world probe using real firing input, server settlement and client payload handlers. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class RailgunBeamClientProbe {
    private static final UUID REMOTE_HV = UUID.fromString("8c4b6f58-538b-47ee-9fca-365646b9be36");
    private static final UUID REMOTE_EHV = UUID.fromString("44837992-2b8d-4547-baa9-d4ac427718e7");
    private static int phase, ticks;
    private static boolean done;
    private static volatile Throwable failure;
    private static volatile long compensationHvBefore, compensationFeBefore;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("ae2lt.beamClientProbe") || done) return;
        var mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.player == null || mc.getSingleplayerServer() == null) return;
        if (++ticks < (phase >= 6 ? 3 : 35)) return;
        ticks = 0;
        try {
            if (failure != null) throw new AssertionError("server probe", failure);
            switch (phase) {
                case 0 -> {
                    mc.options.setCameraType(CameraType.FIRST_PERSON);
                    mc.options.bobView().set(false);
                    mc.options.hideGui = true;
                    mc.getWindow().setWindowed(1000, 700);
                    mc.resizeDisplay();
                    server(RailgunBeamClientProbe::setup);
                }
                case 1 -> { mc.setScreen(null); mc.options.keyAttack.setDown(true); }
                case 2 -> {
                    expectLocal(false);
                    capture("hv.png");
                    server(() -> setMode(true));
                }
                case 3 -> {
                    expectLocal(true);
                    capture("ehv.png");
                    server(() -> {
                        var p = player();
                        var gun = p.getMainHandItem();
                        var inv = RailgunBinding.resolve(gun, p).grid().getStorageService().getInventory();
                        var source = IActionSource.ofPlayer(p);
                        inv.extract(LightningKey.EXTREME_HIGH_VOLTAGE, Long.MAX_VALUE, Actionable.MODULATE, source);
                        compensationHvBefore = inv.extract(LightningKey.HIGH_VOLTAGE, Long.MAX_VALUE, Actionable.SIMULATE, source);
                        compensationFeBefore = RailgunEnergyBuffer.read(gun);
                    });
                }
                case 4 -> {
                    expectLocal(true);
                    capture("ehv-compensated.png");
                    server(() -> {
                        var p = player();
                        var gun = p.getMainHandItem();
                        var inv = RailgunBinding.resolve(gun, p).grid().getStorageService().getInventory();
                        var source = IActionSource.ofPlayer(p);
                        require(inv.extract(LightningKey.EXTREME_HIGH_VOLTAGE, Long.MAX_VALUE, Actionable.SIMULATE, source) == 0, "EHV remains empty");
                        require(inv.extract(LightningKey.HIGH_VOLTAGE, Long.MAX_VALUE, Actionable.SIMULATE, source) < compensationHvBefore, "server consumed HV compensation");
                        require(RailgunEnergyBuffer.read(gun) < compensationFeBefore, "server consumed beam FE");
                        setMode(false);
                    });
                }
                case 5 -> {
                    expectLocal(false);
                    capture("hv-restored.png");
                    mc.options.keyAttack.setDown(false);
                    server(() -> {
                        var p = player();
                        var hvFrom = new Vec3(-2, 101.2, 4);
                        var hvTo = new Vec3(-2, 101.2, 11);
                        var ehvFrom = new Vec3(3, 101.2, 4);
                        var ehvTo = new Vec3(3, 101.2, 11);
                        PacketDistributor.sendToPlayer(p, new RailgunBeamUpdatePacket(REMOTE_HV, hvFrom, hvTo, true, false));
                        PacketDistributor.sendToPlayer(p, new RailgunBeamUpdatePacket(REMOTE_EHV, ehvFrom, ehvTo, true, true));
                        PacketDistributor.sendToPlayer(p, new RailgunBeamChainFxPacket(REMOTE_HV, hvTo, java.util.List.of(hvTo, hvTo.add(-1, 1, 0)), false, false));
                        PacketDistributor.sendToPlayer(p, new RailgunBeamChainFxPacket(REMOTE_EHV, ehvTo, java.util.List.of(ehvTo, ehvTo.add(1, 1, 0)), false, true));
                    });
                }
                case 6 -> {
                    var states = states();
                    require(states.containsKey(REMOTE_HV) && !states.get(REMOTE_HV).ehv, "remote HV mode");
                    require(states.containsKey(REMOTE_EHV) && states.get(REMOTE_EHV).ehv, "remote EHV mode");
                    capture("remote-modes.png");
                    server(() -> {
                        PacketDistributor.sendToPlayer(player(), new RailgunBeamUpdatePacket(REMOTE_HV, Vec3.ZERO, Vec3.ZERO, false, false));
                        PacketDistributor.sendToPlayer(player(), new RailgunBeamUpdatePacket(REMOTE_EHV, Vec3.ZERO, Vec3.ZERO, false, true));
                    });
                }
                case 7 -> {
                    require(states().isEmpty(), "stop packets clear local and remote beams");
                    Files.writeString(mc.gameDirectory.toPath().resolve("beam-result.txt"),
                            "PASS: real input and S2C modes HV -> EHV -> compensated EHV -> HV; "
                                    + "server HV and FE consumed; remote HV/EHV coexist; stop clears states.\n");
                    done = true;
                    mc.stop();
                }
                default -> throw new AssertionError("phase " + phase);
            }
            phase++;
        } catch (Throwable ex) {
            ex.printStackTrace();
            try { Files.writeString(mc.gameDirectory.toPath().resolve("beam-result.txt"), "FAIL phase=" + phase + ": " + ex); }
            catch (Exception ignored) { }
            done = true;
            mc.options.keyAttack.setDown(false);
            mc.stop();
        }
    }

    private static void setup() {
        var p = player();
        var level = p.serverLevel();
        for (var pos : BlockPos.betweenClosed(-8, 99, -5, 8, 105, 12)) {
            level.setBlockAndUpdate(pos, (pos.getY() == 99 || pos.getZ() == 12 ? Blocks.BLACK_CONCRETE : Blocks.AIR).defaultBlockState());
        }
        level.setWeatherParameters(10000, 0, false, false);
        level.setDayTime(18000);
        var base = new BlockPos(-5, 100, 0);
        var wap = base.north();
        level.setBlockAndUpdate(base, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        level.setBlockAndUpdate(base.east(), AEBlocks.DRIVE.block().defaultBlockState());
        level.setBlockAndUpdate(wap, AEBlocks.WIRELESS_ACCESS_POINT.block().defaultBlockState());
        var cell = ModItems.LIGHTNING_STORAGE_COMPONENT_IV.toStack();
        var storage = StorageCells.getCellInventory(cell, null);
        var source = IActionSource.ofPlayer(p);
        require(storage.insert(LightningKey.EXTREME_HIGH_VOLTAGE, 1000, Actionable.MODULATE, source) == 1000, "seed EHV");
        require(storage.insert(LightningKey.HIGH_VOLTAGE, 12000, Actionable.MODULATE, source) == 12000, "seed HV");
        storage.persist();
        ((DriveBlockEntity) level.getBlockEntity(base.east())).getInternalInventory().setItemDirect(0, cell);
        p.setGameMode(GameType.CREATIVE);
        p.teleportTo(level, .5, 100, .5, Set.of(), 0, 0);
        p.getInventory().clearContent();
        var gun = ModItems.ELECTROMAGNETIC_RAILGUN.toStack();
        RailgunStructuralCore.setCore(gun, ModItems.ULTIMATE_OVERLOAD_CORE.toStack());
        for (var module : java.util.List.of(ModItems.ENERGY_MODULE_T3, ModItems.RAILGUN_MODULE_CORE, ModItems.RAILGUN_MODULE_EHV_BEAM)) {
            require(RailgunModuleStorage.INSTANCE.installOne(gun, module.toStack()), "install module");
        }
        gun.set(ModDataComponents.RAILGUN_SETTINGS.get(), RailgunSettings.DEFAULT.withChainDamage(false).withSound(false));
        gun.set(AEComponents.WIRELESS_LINK_TARGET, GlobalPos.of(level.dimension(), wap));
        RailgunEnergyBuffer.write(gun, 200_000_000);
        p.setItemInHand(InteractionHand.MAIN_HAND, gun);
        p.getInventory().selected = 0;
        p.inventoryMenu.broadcastChanges();
    }

    private static void setMode(boolean ehv) {
        var gun = player().getMainHandItem();
        gun.set(ModDataComponents.RAILGUN_SETTINGS.get(), gun.get(ModDataComponents.RAILGUN_SETTINGS.get()).withEhvBeam(ehv));
        player().inventoryMenu.broadcastChanges();
    }

    private static void expectLocal(boolean ehv) throws ReflectiveOperationException {
        var mc = Minecraft.getInstance();
        require(RailgunBeamRenderClient.isLocalFiring(), "local firing at phase " + phase);
        var beam = states().get(mc.player.getUUID());
        require(beam != null && beam.ehv == ehv, "local server mode at phase " + phase);
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, RailgunBeamRenderClient.BeamState> states() throws ReflectiveOperationException {
        var field = RailgunBeamRenderClient.class.getDeclaredField("ACTIVE");
        field.setAccessible(true);
        return (Map<UUID, RailgunBeamRenderClient.BeamState>) field.get(null);
    }

    private static ServerPlayer player() {
        var mc = Minecraft.getInstance();
        return mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
    }

    private static void server(Runnable action) {
        Minecraft.getInstance().getSingleplayerServer().execute(() -> {
            try { action.run(); } catch (Throwable ex) { failure = ex; }
        });
    }

    private static void capture(String name) throws Exception {
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            image.writeToFile(mc.gameDirectory.toPath().resolve(name));
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
