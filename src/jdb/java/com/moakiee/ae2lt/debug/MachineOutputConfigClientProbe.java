package com.moakiee.ae2lt.debug;

import appeng.api.orientation.RelativeSide;
import appeng.client.gui.widgets.ActionButton;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import com.moakiee.ae2lt.client.machine.*;
import com.moakiee.ae2lt.menu.*;
import com.moakiee.ae2lt.registry.ModBlocks;
import java.nio.file.Files;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import appeng.client.gui.AEBaseScreen;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

/** Opt-in native UI regression; never included in the release JAR. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class MachineOutputConfigClientProbe {
    private static final BlockPos POS = new BlockPos(0, 100, 0);
    private static final String[] CLEAR_KEYS = {"crystal_catalyzer", "lightning_assembly", "lightning_simulation", "overload_factory"};
    private static boolean started, done, previousFront;
    private static int ticks, machine, step;
    private static Screen parent;
    private static int[][] parentSlotPositions;
    private static volatile Throwable serverFailure;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("ae2lt.machineOutputClientProbe") || done) return;
        var mc = Minecraft.getInstance();
        try {
            if (!started) {
                if (!(mc.screen instanceof TitleScreen || mc.screen instanceof AccessibilityOnboardingScreen)
                        || mc.getOverlay() != null) return;
                mc.options.pauseOnLostFocus = false;
                mc.options.renderDistance().set(4);
                mc.options.guiScale().set(2);
                mc.getWindow().setWindowed(1000, 800);
                mc.resizeDisplay();
                started = true;
                var rules = new GameRules();
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                mc.createWorldOpenFlows().createFreshLevel("MachineOutput-" + System.currentTimeMillis(),
                        new LevelSettings("Machine output validation", GameType.CREATIVE, false,
                                Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT),
                        new WorldOptions(0, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET)
                                .getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
                return;
            }
            if (++ticks > 2400) throw new AssertionError("Client validation timed out");
            if (serverFailure != null) throw new AssertionError("Server fixture", serverFailure);
            if (mc.player == null || mc.getSingleplayerServer() == null || ticks % 20 != 0) return;
            if (machine == 4) {
                report(mc, "PASS: four native output screens, hidden inventory/upgrade/machine slots after resize, restored parent slots, six side buttons, translation keys, toggle/clear packets and screenshots");
                return;
            }
            switch (step) {
                case 0 -> server(mc, () -> {
                    var level = mc.getSingleplayerServer().overworld();
                    var player = level.getServer().getPlayerList().getPlayer(mc.player.getUUID());
                    player.teleportTo(level, .5, 101, 2.5, Set.of(), 180, 25);
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();
                    player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 16));
                    player.getInventory().setItem(9, new ItemStack(Items.EMERALD, 16));
                    var block = switch (machine) {
                        case 0 -> ModBlocks.CRYSTAL_CATALYZER.get();
                        case 1 -> ModBlocks.LIGHTNING_ASSEMBLY_CHAMBER.get();
                        case 2 -> ModBlocks.LIGHTNING_SIMULATION_CHAMBER.get();
                        default -> ModBlocks.OVERLOAD_PROCESSING_FACTORY.get();
                    };
                    level.setBlockAndUpdate(POS, block.defaultBlockState());
                });
                case 1 -> server(mc, () -> {
                    var level = mc.getSingleplayerServer().overworld();
                    var player = level.getServer().getPlayerList().getPlayer(mc.player.getUUID());
                    var locator = MenuLocators.forBlockEntity(level.getBlockEntity(POS));
                    switch (machine) {
                        case 0 -> MenuOpener.open(CrystalCatalyzerMenu.TYPE, player, locator);
                        case 1 -> MenuOpener.open(LightningAssemblyChamberMenu.TYPE, player, locator);
                        case 2 -> MenuOpener.open(LightningSimulationChamberMenu.TYPE, player, locator);
                        default -> MenuOpener.open(OverloadProcessingFactoryMenu.TYPE, player, locator);
                    }
                });
                case 2 -> {
                    parent = mc.screen;
                    Screen output = switch (machine) {
                        case 0 -> new CrystalCatalyzerOutputConfigScreen((CrystalCatalyzerScreen) parent);
                        case 1 -> new LightningAssemblyOutputConfigScreen((LightningAssemblyChamberScreen) parent);
                        case 2 -> new LightningSimulationOutputConfigScreen((LightningSimulationChamberScreen) parent);
                        default -> new OverloadProcessingFactoryOutputConfigScreen((OverloadProcessingFactoryScreen) parent);
                    };
                    parentSlotPositions = mc.player.containerMenu.slots.stream()
                            .map(slot -> new int[] {slot.x, slot.y}).toArray(int[][]::new);
                    ((AEBaseScreen<?>) parent).switchToScreen((AEBaseScreen<?>) output);
                    requireHiddenSlots(mc);
                    mc.resizeDisplay();
                    requireHiddenSlots(mc);
                    require(output.children().stream().filter(OverloadProcessingFactoryOutputButton.class::isInstance).count() == 6,
                            "Expected six output side buttons");
                    previousFront = menu(mc).isOutputSideEnabled(RelativeSide.FRONT);
                    frontButton(mc).onPress();
                }
                case 3 -> {
                    require(menu(mc).isOutputSideEnabled(RelativeSide.FRONT) != previousFront, "Toggle packet did not synchronize");
                    String prefix = machine == 3 ? "overload_factory" : "lightning_simulation";
                    var expected = Component.translatable("ae2lt.gui." + prefix + ".output_side."
                            + (previousFront ? "disabled" : "enabled"));
                    require(frontButton(mc).getTooltipMessage().get(1).getString().equals(expected.getString()), "Wrong side tooltip");
                    Screenshot.grab(mc.gameDirectory, "machine-output-" + machine + ".png", mc.getMainRenderTarget(), ignored -> {});
                    mc.screen.children().stream().filter(ActionButton.class::isInstance).map(ActionButton.class::cast)
                            .filter(button -> button.getMessage().getString().equals(Component.translatable(
                                    "ae2lt.gui." + CLEAR_KEYS[machine] + ".output_side.clear").getString()))
                            .findFirst().orElseThrow().onPress();
                }
                case 4 -> {
                    for (var side : RelativeSide.values()) require(!menu(mc).isOutputSideEnabled(side), "Clear packet left a side enabled");
                    require(mc.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0), "Escape was not handled");
                    require(mc.screen == parent, "Escape did not return to the parent screen");
                    for (int i = 0; i < parentSlotPositions.length; i++) {
                        var slot = mc.player.containerMenu.slots.get(i);
                        require(slot.x == parentSlotPositions[i][0] && slot.y == parentSlotPositions[i][1],
                                "Parent slot position was not restored: " + i);
                    }
                    mc.player.closeContainer();
                    machine++;
                    step = -1;
                }
                default -> throw new AssertionError("Unexpected validation step");
            }
            step++;
        } catch (Throwable failure) {
            failure.printStackTrace();
            report(mc, "FAIL machine=" + machine + " step=" + step + ": " + failure);
        }
    }

    private static MachineOutputConfigMenu menu(Minecraft mc) {
        return (MachineOutputConfigMenu) mc.player.containerMenu;
    }

    private static void requireHiddenSlots(Minecraft mc) {
        for (var slot : mc.player.containerMenu.slots) {
            require(slot.x < -9000 && slot.y < -9000, "Output screen exposed slot " + slot.index);
        }
    }

    private static OverloadProcessingFactoryOutputButton frontButton(Minecraft mc) {
        return mc.screen.children().stream().filter(OverloadProcessingFactoryOutputButton.class::isInstance)
                .map(OverloadProcessingFactoryOutputButton.class::cast)
                .filter(button -> button.getTooltipMessage().getFirst().getString().equals(
                        Component.translatable("gui.tooltips.ae2.SideFront").getString())).findFirst().orElseThrow();
    }

    private static void server(Minecraft mc, Runnable action) {
        mc.getSingleplayerServer().execute(() -> { try { action.run(); } catch (Throwable failure) { serverFailure = failure; } });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void report(Minecraft mc, String result) {
        System.out.println("MACHINE_OUTPUT_CLIENT " + result);
        try { Files.writeString(mc.gameDirectory.toPath().resolve("probe-result.txt"), result); }
        catch (Exception failure) { throw new RuntimeException(failure); }
        done = true;
        mc.stop();
    }
}
