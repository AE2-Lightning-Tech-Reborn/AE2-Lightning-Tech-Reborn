package com.moakiee.ae2lt.debug;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;
import com.moakiee.ae2lt.celestweave.module.MultidimensionalProtectionSubmodule;
import com.moakiee.ae2lt.client.hub.DeviceHubKeyMappings;
import com.moakiee.ae2lt.client.hub.DeviceHubScreen;
import com.moakiee.ae2lt.registry.ModItems;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Opt-in verification of the real hotkey, menu packets, rendering, actions and revocation. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class OwnedEntityArmorClientProbe {
    private static int ticks, phase;
    private static volatile int maidId;
    private static volatile boolean serverReady;
    private static volatile Throwable serverFailure;
    private static boolean done;
    private static String capture;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!enabled() || done) return;
        var mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.player == null || mc.getSingleplayerServer() == null || ++ticks % 20 != 0) return;
        try {
            require(ticks < 600, "client synchronization timed out at phase " + phase
                    + "; screen=" + mc.screen + "; menu=" + mc.player.containerMenu);
            if (serverFailure != null) throw new AssertionError("server fixture failed", serverFailure);
            if (capture != null) return;
            switch (phase) {
                case 0 -> {
                    mc.getWindow().setTitle("AE2LT Owned Entity Armor Test");
                    // Curios also defaults to G. Exercise the normal configurable key path with
                    // an unused key in this disposable instance instead of opening both menus.
                    DeviceHubKeyMappings.OPEN_CONFIG.setKey(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM
                            .getOrCreate(org.lwjgl.glfw.GLFW.GLFW_KEY_F8));
                    KeyMapping.resetMapping();
                    mc.options.guiScale().set(2);
                    mc.options.renderDistance().set(4);
                    mc.resizeDisplay();
                    mc.setScreen(null);
                    server(OwnedEntityArmorClientProbe::fixture);
                }
                case 1 -> {
                    if (!serverReady || !(mc.level.getEntity(maidId) instanceof TamableAnimal maid)
                            || maid.getItemBySlot(EquipmentSlot.CHEST).isEmpty() || mc.screen != null) return;
                    pressConfig(maid);
                }
                case 2 -> {
                    if (!(mc.screen instanceof DeviceHubScreen screen)
                            || screen.getMenu().getModuleEnabled().isEmpty()) return;
                    require(screen.getMenu().isEntityEquipment(), "hotkey opened player's own menu");
                    require(screen.getMenu().getEquipmentWearer(mc.player).getId() == maidId,
                            "client equipment icons use the wrong wearer");
                    require(screen.getTitle().getString().contains("Maid QA"), "target name missing from title");
                    require(screen.getMenu().getDeviceName().equals("Maid armor"), "wrong equipment status");
                    capture = "01-maid-equipment.png";
                }
                case 3 -> {
                    var screen = (DeviceHubScreen) mc.screen;
                    require(screen.mouseClicked((screen.width - 176) / 2.0 + 150,
                            (screen.height - 223) / 2.0 + 88, 0), "module toggle click rejected");
                }
                case 4 -> {
                    if (!(mc.screen instanceof DeviceHubScreen screen)
                            || screen.getMenu().getModuleEnabled().getFirst()) return;
                    server(player -> {
                        var maid = (TamableAnimal) player.level().getEntity(maidId);
                        require(!CelestweaveArmorState.isSubmoduleEnabled(maid.getItemBySlot(EquipmentSlot.CHEST),
                                        MultidimensionalProtectionSubmodule.INSTANCE), "click did not update maid armor");
                        require(CelestweaveArmorState.isSubmoduleEnabled(player.getItemBySlot(EquipmentSlot.CHEST),
                                        MultidimensionalProtectionSubmodule.INSTANCE), "click changed owner armor");
                        maid.setOwnerUUID(UUID.randomUUID());
                    });
                }
                case 5 -> {
                    if (!serverReady || mc.screen != null) return;
                    var maid = (TamableAnimal) mc.level.getEntity(maidId);
                    if (mc.player.getUUID().equals(maid.getOwnerUUID())) return;
                    pressConfig(maid);
                }
                case 6 -> {
                    if (!(mc.screen instanceof DeviceHubScreen screen)
                            || screen.getMenu().getModuleEnabled().isEmpty()) return;
                    require(!screen.getMenu().isEntityEquipment(), "foreign maid did not fall back to own equipment");
                    require(screen.getMenu().getDeviceName().equals("Owner armor"), "player menu regression");
                    capture = "02-player-fallback.png";
                }
                case 7 -> {
                    record("PASS: real maid hotkey/menu sync, target title and equipment, clickable module toggle, owner isolation, ownership revocation and player fallback.");
                    done = true;
                    mc.stop();
                }
                default -> throw new AssertionError(phase);
            }
            phase++;
        } catch (Throwable failure) {
            failure.printStackTrace();
            record("FAIL phase=" + phase + ": " + failure);
            done = true;
            mc.stop();
        }
    }

    private static void pressConfig(TamableAnimal maid) {
        var mc = Minecraft.getInstance();
        mc.hitResult = new EntityHitResult(maid);
        KeyMapping.click(DeviceHubKeyMappings.OPEN_CONFIG.getKey());
        DeviceHubKeyMappings.RuntimeHandler.onClientTick(new ClientTickEvent.Post());
    }

    private static void fixture(ServerPlayer player) {
        var level = player.serverLevel();
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, player.server);
        level.setDayTime(6000);
        for (var pos : BlockPos.betweenClosed(-4, 99, -4, 4, 104, 4)) {
            level.setBlockAndUpdate(pos, pos.getY() == 99
                    ? Blocks.WHITE_CONCRETE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        player.getInventory().clearContent();
        player.setItemSlot(EquipmentSlot.CHEST, armor(player, "Owner armor"));
        player.teleportTo(level, 0.5, 100, 0.5, Set.of(), 0, 0);
        var type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("touhou_little_maid:maid"));
        var maid = (TamableAnimal) type.create(level);
        require(maid != null, "maid mod is required");
        maid.setOwnerUUID(player.getUUID());
        maid.setTame(true, true);
        maid.setNoAi(true);
        maid.setCustomName(Component.literal("Maid QA"));
        maid.setPos(0.5, 100, 2.5);
        maid.setItemSlot(EquipmentSlot.CHEST, armor(player, "Maid armor"));
        level.addFreshEntity(maid);
        maidId = maid.getId();
    }

    private static ItemStack armor(ServerPlayer player, String name) {
        var armor = new ItemStack(ModItems.CELESTWEAVE_CORE.get());
        armor.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        CelestweaveArmorState.setSlot(armor, player.registryAccess(), CelestweaveArmorState.SLOT_CORE,
                new ItemStack(ModItems.ULTIMATE_OVERLOAD_CORE.get()));
        require(CelestweaveArmorState.installOneModule(armor, player.registryAccess(),
                new ItemStack(ModItems.CELESTWEAVE_SUBMODULE_MULTIDIMENSIONAL_PROTECTION.get())), "module rejected");
        return armor;
    }

    private static void server(Consumer<ServerPlayer> action) {
        var mc = Minecraft.getInstance();
        var id = mc.player.getUUID();
        var server = mc.getSingleplayerServer();
        serverReady = false;
        server.execute(() -> {
            try {
                action.accept(server.getPlayerList().getPlayer(id));
                serverReady = true;
            } catch (Throwable failure) {
                serverFailure = failure;
            }
        });
    }

    @SubscribeEvent
    public static void render(RenderFrameEvent.Post event) {
        if (!enabled() || done || capture == null) return;
        try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            Files.createDirectories(output());
            image.writeToFile(output().resolve(capture));
            capture = null;
        } catch (Exception failure) {
            throw new RuntimeException(failure);
        }
    }

    private static boolean enabled() { return Boolean.getBoolean("ae2lt.ownedArmorClientProbe"); }
    private static Path output() { return Path.of(System.getProperty("ae2lt.ownedArmorClientOutput")); }

    private static void record(String message) {
        System.out.println("OWNED_ARMOR_CLIENT " + message);
        try {
            Files.createDirectories(output());
            Files.writeString(output().resolve("result.txt"), message + System.lineSeparator());
        } catch (Exception failure) {
            throw new RuntimeException(failure);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
