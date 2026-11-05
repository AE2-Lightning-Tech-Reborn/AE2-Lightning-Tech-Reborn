package com.moakiee.ae2lt.debug;

import com.moakiee.ae2lt.blockentity.PigmeeDimensionBlockEntity;
import com.moakiee.ae2lt.client.PigmeeDimensionScreen;
import com.moakiee.ae2lt.dimension.*;
import com.moakiee.ae2lt.menu.PigmeeDimensionMenu;
import com.moakiee.ae2lt.network.PigmeeDimensionActionPacket;
import com.moakiee.ae2lt.registry.ModBlocks;
import java.nio.file.Files;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Opt-in isolated client acceptance. Run twice to test an actual process restart. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class PigmeeDimensionClientProbe {
    private static final String WORLD = "PigmeeDimensionReview";
    private static final BlockPos ODD = new BlockPos(0, -59, 0), EVEN = new BlockPos(2, -59, 0), CUSTOM = new BlockPos(4, -59, 0);
    private static final BlockPos MARKER = new BlockPos(30, 21, 30);
    private static int phase, ticks;
    private static boolean opening, done, reloading;
    private static volatile Throwable failure;
    private static volatile boolean ready;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("ae2lt.pigmeeDimensionClientProbe") || done) return;
        var mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        try {
            if (!opening && mc.screen instanceof TitleScreen) {
                opening = true;
                reloading = Files.exists(mc.gameDirectory.toPath().resolve("pigmee-dimension-stage1.txt"));
                if (Files.exists(mc.gameDirectory.toPath().resolve("saves/" + WORLD + "/level.dat"))) {
                    mc.createWorldOpenFlows().openWorld(WORLD, () -> report("FAIL: world open"));
                } else {
                    var rules = new GameRules();
                    rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                    mc.createWorldOpenFlows().createFreshLevel(WORLD,
                            new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.NORMAL, true, rules, WorldDataConfiguration.DEFAULT),
                            new WorldOptions(71231, false, false),
                            registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                                    .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
                }
                return;
            }
            if (mc.player == null || mc.getSingleplayerServer() == null || ++ticks % 60 != 0) return;
            require(ticks < 2400, "client probe timed out");
            if (failure != null) throw new AssertionError("server acceptance", failure);
            if (reloading) {
                if (phase == 0) {
                    onServer(() -> {
                        var player = player();
                        var data = PigmeeDimensionData.get(player.server);
                        require(data.countOwned(player.getUUID()) == 3, "owner identities persisted with test-only quota 3");
                        require(player.server.getLevel(PigmeeDimensions.key(1)) != null, "odd restored before login");
                        var even = player.server.getLevel(PigmeeDimensions.key(2));
                        var custom = player.server.getLevel(PigmeeDimensions.key(3));
                        require(custom != null && player.serverLevel() == custom, "login restores custom dimension without calling getOrCreate");
                        require(even.getBlockState(MARKER).is(Blocks.EMERALD_BLOCK), "player building survived restart");
                        require(custom.getBlockState(MARKER).is(Blocks.EMERALD_BLOCK), "custom building survived restart");
                        verifyCustom(data.find(3).palette());
                        require(((PigmeeDimensionBlockEntity) player.server.overworld().getBlockEntity(EVEN)).dimensionId() == 2, "entrance binding persisted");
                        require(PigmeeDimensionTravel.leave(player), "persistent return point works after restart");
                        require(player.level().dimension() == Level.OVERWORLD && player.blockPosition().equals(new BlockPos(0, -59, 3)), "original return position restored");
                        PigmeeDimensionMenu.open(player, CUSTOM);
                        ready = true;
                    });
                    phase++;
                } else if (ready && mc.level.dimension() == Level.OVERWORLD && mc.player.containerMenu instanceof PigmeeDimensionMenu restoredMenu && restoredMenu.custom()) {
                    verifyCustom(restoredMenu.entries().getFirst().palette());
                    report("PASS: gap borders; three dimension kinds; actual 16x16 grid clicks, drag, color picker, fill, cancel and resize; malformed grid rejection; custom generation; process restart, in-dimension login, persisted custom pattern, building and entrance binding");
                    done = true;
                    mc.stop();
                }
                return;
            }
            switch (phase) {
                case 0 -> {
                    mc.options.guiScale().set(2);
                    mc.options.renderDistance().set(6);
                    mc.options.fov().set(60);
                    mc.getWindow().setWindowed(1000, 800);
                    mc.resizeDisplay();
                    onServer(() -> {
                        var player = player();
                        var level = player.serverLevel();
                        level.getGameRules().getRule(GameRules.RULE_SPAWN_RADIUS).set(0, player.server);
                        level.setBlockAndUpdate(ODD.below(), Blocks.STONE.defaultBlockState());
                        level.setBlockAndUpdate(EVEN.below(), Blocks.STONE.defaultBlockState());
                        level.setBlockAndUpdate(CUSTOM.below(), Blocks.STONE.defaultBlockState());
                        level.setBlockAndUpdate(new BlockPos(0, -60, 3), Blocks.STONE.defaultBlockState());
                        level.setBlockAndUpdate(ODD, ModBlocks.PIGMEE_ODD_DIMENSION.get().defaultBlockState());
                        level.setBlockAndUpdate(EVEN, ModBlocks.PIGMEE_EVEN_DIMENSION.get().defaultBlockState());
                        level.setBlockAndUpdate(CUSTOM, ModBlocks.PIGMEE_CUSTOM_DIMENSION.get().defaultBlockState());
                        ((PigmeeDimensionBlockEntity) level.getBlockEntity(ODD)).setPlacer(player.getUUID());
                        ((PigmeeDimensionBlockEntity) level.getBlockEntity(EVEN)).setPlacer(player.getUUID());
                        ((PigmeeDimensionBlockEntity) level.getBlockEntity(CUSTOM)).setPlacer(player.getUUID());
                        player.teleportTo(level, .5, -59, 3.5, 180, 0);
                        PigmeeDimensionMenu.open(player, ODD);
                    });
                }
                case 1, 6 -> {
                    if (!(mc.screen instanceof PigmeeDimensionScreen)) return;
                    send(PigmeeDimensionActionPacket.CREATE, 0, phase == 1 ? "奇数工厂" : "偶数工厂", 0);
                    if (phase == 1) { mc.getWindow().setWindowed(640, 480); mc.resizeDisplay(); }
                }
                case 2 -> {
                    require(menu().bound() == 1, "create and bind odd from client packet");
                    var screen = (PigmeeDimensionScreen) mc.screen;
                    require(screen.getGuiLeft() >= 0 && screen.getGuiTop() >= 0, "menu fits minimum 320x240 GUI");
                    capture("pigmee-odd-settings.png");
                    send(PigmeeDimensionActionPacket.CONFIGURE, 1, "forged", 100);
                }
                case 3 -> {
                    mc.getWindow().setWindowed(1000, 800); mc.resizeDisplay();
                    onServer(() -> require(PigmeeDimensionData.get(player().server).find(1).access().getName().equals("奇数工厂"), "stale menu packet ignored"));
                    send(PigmeeDimensionActionPacket.ENTER, 1, "", 0);
                }
                case 4, 8, 16 -> {
                    int id = phase == 4 ? 1 : phase == 8 ? 2 : 3;
                    if (!mc.level.dimension().equals(PigmeeDimensions.key(id))) return;
                    require(mc.level.effects() instanceof com.moakiee.ae2lt.client.PigmeeDimensionEffects, "custom visual lighting loaded");
                    var light = new org.joml.Vector3f();
                    mc.level.effects().adjustLightmapColors(mc.level, 0, 0, 0, 0, 0, 0, light);
                    require(light.x() == 1 && light.y() == 1 && light.z() == 1, "unlit cells render at full brightness");
                    mc.options.hideGui = true;
                    onServer(() -> {
                        var player = player();
                        require(player.blockPosition().getY() == 21, "safe natural surface arrival");
                        player.setGameMode(GameType.SPECTATOR);
                        player.teleportTo(player.serverLevel(), 24, 93, 24, 180, 90);
                        if (id >= 2) player.serverLevel().setBlockAndUpdate(MARKER, Blocks.EMERALD_BLOCK.defaultBlockState());
                        if (id == 3) {
                            verifyCustom(PigmeeDimensionData.get(player.server).find(3).palette());
                            require(player.serverLevel().getBlockState(new BlockPos(-14, 20, -14))
                                    .is(ModBlocks.PIGMEE_BUILDING_PANELS.get(net.minecraft.world.item.DyeColor.LIME).get()), "custom pattern repeats in negative chunks");
                        }
                    });
                }
                case 5 -> {
                    capture("pigmee-odd-world.png");
                    mc.options.hideGui = false;
                    onServer(() -> {
                        var player = player();
                        require(PigmeeDimensionTravel.leave(player), "return from odd");
                        player.setGameMode(GameType.CREATIVE);
                        PigmeeDimensionMenu.open(player, EVEN);
                    });
                }
                case 7 -> {
                    require(menu().bound() == 2, "create and bind even from client packet");
                    capture("pigmee-even-settings.png");
                    send(PigmeeDimensionActionPacket.ENTER, 2, "", 0);
                }
                case 9 -> {
                    capture("pigmee-even-world.png");
                    mc.options.hideGui = false;
                    onServer(() -> {
                        require(PigmeeDimensionTravel.leave(player()), "return from even");
                        player().setGameMode(GameType.CREATIVE);
                        PigmeeDimensionMenu.open(player(), CUSTOM);
                    });
                }
                case 10 -> {
                    if (!(mc.screen instanceof PigmeeDimensionScreen) || !menu().custom()) return;
                    press("edit_pattern");
                    mc.getWindow().setWindowed(640, 480); mc.resizeDisplay();
                }
                case 11 -> {
                    var screen = (PigmeeDimensionScreen) mc.screen;
                    require(screen.getGuiLeft() >= 0 && screen.getGuiTop() >= 0, "custom editor fits minimum GUI");
                    require(screen.children().stream().filter(widget -> widget instanceof net.minecraft.client.gui.components.AbstractButton button && button.visible).count() == 294,
                            "256 grid buttons + 33 material buttons + 5 editor actions visible");
                    choose(net.minecraft.world.item.DyeColor.WHITE);
                    press("fill");
                    choose(net.minecraft.world.item.DyeColor.CYAN);
                    stroke(0, 0, 15, 0); stroke(15, 0, 15, 15); stroke(15, 15, 0, 15); stroke(0, 15, 0, 0);
                    choose(net.minecraft.world.item.DyeColor.PINK);
                    String[] heart = {"........", ".PP..PP.", "PPPPPPPP", "PPPPPPPP", ".PPPPPP.", "..PPPP..", "...PP...", "........"};
                    for (int z = 0; z < 8; z++) for (int x = 0; x < 8; x++) if (heart[z].charAt(x) == 'P') stroke(x + 4, z + 4, x + 4, z + 4);
                    screen.mouseClicked(screen.getGuiLeft() + 13, screen.getGuiTop() + 47, 1);
                    screen.mouseReleased(screen.getGuiLeft() + 13, screen.getGuiTop() + 47, 1);
                    stroke(1, 2, 1, 2);
                    choose(net.minecraft.world.item.DyeColor.LIME); stroke(2, 2, 2, 2);
                }
                case 12 -> {
                    capture("pigmee-custom-editor.png");
                    press("done");
                    press("edit_pattern");
                    choose(net.minecraft.world.item.DyeColor.RED); stroke(3, 3, 3, 3);
                    press("cancel");
                }
                case 13 -> {
                    ((PigmeeDimensionScreen) mc.screen).children().stream()
                            .filter(widget -> widget instanceof net.minecraft.client.gui.components.EditBox box
                                    && box.getMessage().getString().equals(net.minecraft.network.chat.Component.translatable("gui.ae2lt.pigmee_dimension.name").getString()))
                            .map(net.minecraft.client.gui.components.EditBox.class::cast).findFirst().orElseThrow().setValue("自定义工厂");
                    press("create");
                }
                case 14 -> {
                    require(menu().bound() == 3, "custom created through actual editor controls");
                    verifyCustom(menu().entries().getFirst().palette());
                    var pattern = PigmeeDimensionPattern.encode(menu().entries().getFirst().palette().cells());
                    PacketDistributor.sendToServer(new PigmeeDimensionActionPacket(menu().containerId, PigmeeDimensionActionPacket.CONFIGURE,
                            3, "forged-grid", 0, 0, false, "", (byte) 0, new byte[255]));
                    pattern[255] = (byte) 255;
                    PacketDistributor.sendToServer(new PigmeeDimensionActionPacket(menu().containerId, PigmeeDimensionActionPacket.CONFIGURE,
                            3, "forged-grid", 0, 0, false, "", (byte) 0, pattern));
                }
                case 15 -> {
                    onServer(() -> {
                        var entry = PigmeeDimensionData.get(player().server).find(3);
                        require(entry.access().getName().equals("自定义工厂"), "malformed grid requests cannot mutate metadata");
                        verifyCustom(entry.palette());
                    });
                    capture("pigmee-custom-settings.png");
                    mc.getWindow().setWindowed(1000, 800); mc.resizeDisplay();
                    press("enter");
                }
                case 17 -> {
                    capture("pigmee-custom-world.png");
                    Files.writeString(mc.gameDirectory.toPath().resolve("pigmee-dimension-stage1.txt"), "PASS: stage one; restart required\n");
                    done = true;
                    mc.stop();
                }
            }
            phase++;
        } catch (Throwable error) {
            report("FAIL phase " + phase + ": " + error);
            error.printStackTrace();
            done = true;
            mc.stop();
        }
    }

    private static PigmeeDimensionMenu menu() { return (PigmeeDimensionMenu) Minecraft.getInstance().player.containerMenu; }

    private static void verifyCustom(PigmeeChunkGenerator.Palette palette) {
        require(palette.custom() && palette.cells().size() == 256, "custom surface has 256 cells");
        require(palette.cells().get(2 * 16 + 2).is(ModBlocks.PIGMEE_BUILDING_PANELS.get(net.minecraft.world.item.DyeColor.LIME).get()), "selected cell color retained");
        require(palette.cells().get(2 * 16 + 1).is(ModBlocks.PIGMEE_BUILDING_PANELS.get(net.minecraft.world.item.DyeColor.CYAN).get()), "right click picked the existing color");
        require(palette.cells().get(3 * 16 + 3).equals(PigmeeDimensionMaterials.floor()), "cancel discards unconfirmed edits");
        for (int x = 0; x < 16; x++) require(palette.cells().get(x).is(ModBlocks.PIGMEE_BUILDING_PANELS.get(net.minecraft.world.item.DyeColor.CYAN).get()), "drag covers every cell along the stroke");
    }

    private static void press(String key) {
        var mc = Minecraft.getInstance();
        String label = net.minecraft.network.chat.Component.translatable("gui.ae2lt.pigmee_dimension." + key).getString();
        var button = mc.screen.children().stream().filter(widget -> widget instanceof net.minecraft.client.gui.components.AbstractButton b
                && b.visible && b.active && b.getMessage().getString().equals(label))
                .map(net.minecraft.client.gui.components.AbstractButton.class::cast).findFirst().orElseThrow();
        mc.screen.mouseClicked(button.getX() + button.getWidth() / 2.0, button.getY() + button.getHeight() / 2.0, 0);
        mc.screen.mouseReleased(button.getX() + button.getWidth() / 2.0, button.getY() + button.getHeight() / 2.0, 0);
    }

    private static void choose(net.minecraft.world.item.DyeColor color) {
        var screen = (PigmeeDimensionScreen) Minecraft.getInstance().screen;
        int index = PigmeeDimensionMaterials.all().indexOf(ModBlocks.PIGMEE_BUILDING_PANELS.get(color).get());
        double x = screen.getGuiLeft() + 190 + index % 6 * 20, y = screen.getGuiTop() + 82 + index / 6 * 20;
        screen.mouseClicked(x, y, 0); screen.mouseReleased(x, y, 0);
    }

    private static void stroke(int x0, int z0, int x1, int z1) {
        var screen = (PigmeeDimensionScreen) Minecraft.getInstance().screen;
        int left = screen.getGuiLeft() + 13, top = screen.getGuiTop() + 47;
        screen.mouseClicked(left + x0 * 10, top + z0 * 10, 0);
        screen.mouseDragged(left + x1 * 10, top + z1 * 10, 0, (x1 - x0) * 10, (z1 - z0) * 10);
        screen.mouseReleased(left + x1 * 10, top + z1 * 10, 0);
    }
    private static ServerPlayer player() {
        var mc = Minecraft.getInstance();
        return mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
    }
    private static void send(int action, int id, String name, int offset) {
        var materials = PigmeeDimensionMaterials.all();
        PacketDistributor.sendToServer(new PigmeeDimensionActionPacket(menu().containerId + offset, action, id, name,
                materials.indexOf(PigmeeDimensionMaterials.defaultFrame().getBlock()),
                materials.indexOf(PigmeeDimensionMaterials.defaultCenter().getBlock()), false, "", (byte) 0));
    }
    private static void onServer(Runnable action) {
        Minecraft.getInstance().getSingleplayerServer().execute(() -> { try { action.run(); } catch (Throwable error) { failure = error; } });
    }
    private static void capture(String name) {
        var mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), text -> {});
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void report(String message) {
        System.out.println("PIGMEE_DIMENSION_CLIENT_PROBE " + message);
        try { Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("pigmee-dimension-client-probe.txt"), message + "\n"); }
        catch (Exception error) { error.printStackTrace(); }
    }
}
