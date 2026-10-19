package com.moakiee.ae2lt.debug;

import com.moakiee.ae2lt.event.NaturalLightningTransformationHandler;
import com.moakiee.ae2lt.util.NaturalLightningOrigin;
import com.moakiee.ae2lt.registry.ModFumos;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;

/** Real client renderer and server-to-client spawn acceptance, excluded from published jars. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class PigmeeLightningClientProbe {
    private static int ticks, phase;
    private static volatile int naturalId, artificialId;
    private static volatile Throwable serverFailure;
    private static boolean done;

    @SubscribeEvent
    public static void freezeFixtureBolts(EntityJoinLevelEvent event) {
        // Keep both test bolts visible long enough for framebuffer review in this disposable world.
        if (Boolean.getBoolean("ae2lt.pigmeeLightningClientProbe")
                && event.getEntity() instanceof LightningBolt) event.getEntity().canUpdate(false);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Boolean.getBoolean("ae2lt.pigmeeLightningClientProbe") || done) return;
        var mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.player == null || mc.getSingleplayerServer() == null || ++ticks % 20 != 0) return;
        try {
            System.out.println("PIGMEE_LIGHTNING_CLIENT_PROGRESS phase=" + phase + " ticks=" + ticks
                    + " natural=" + naturalId + " artificial=" + artificialId);
            if (serverFailure != null) throw new AssertionError("Server fixture", serverFailure);
            switch (phase) {
                case 0 -> {
                    mc.options.hideGui = true;
                    mc.options.fov().set(75);
                    mc.options.gamma().set(1.0);
                    onServer(PigmeeLightningClientProbe::setup);
                }
                case 1 -> {
                    if (!(mc.level.getEntity(naturalId) instanceof LightningBolt natural)
                            || !(mc.level.getEntity(artificialId) instanceof LightningBolt artificial)
                            || !mc.player.getItemBySlot(EquipmentSlot.HEAD).is(ModFumos.PIGMEE_FUMO_ITEM.get())) {
                        require(ticks < 600, "Server bolts/equipment did not reach the client");
                        return;
                    }
                    require(((NaturalLightningOrigin) natural).ae2lt$isNaturalWeatherLightning(),
                            "Natural origin did not sync over the real connection");
                    require(!((NaturalLightningOrigin) artificial).ae2lt$isNaturalWeatherLightning(), "Artificial origin changed");
                    checkRenderer(natural, artificial);
                }
                case 2 -> {
                    Screenshot.grab(mc.gameDirectory, "pigmee-equipped.png", mc.getMainRenderTarget(), ignored -> {});
                    onServer(() -> mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID())
                            .setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY));
                }
                case 3 -> {
                    if (!mc.player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) return;
                }
                case 4 -> Screenshot.grab(mc.gameDirectory, "pigmee-unequipped.png", mc.getMainRenderTarget(), ignored -> {});
                case 5 -> {
                    report("PASS: real spawn sync; natural pink only while wearing each of four Pigmees; "
                            + "artificial vanilla; hand/ordinary helmet/unequipped vanilla; unchanged geometry and alpha; "
                            + "Curios=" + ModList.get().isLoaded("curios") + "; framebuffer screenshots saved.");
                    done = true;
                    mc.stop();
                }
                default -> throw new AssertionError("Unexpected phase " + phase);
            }
            phase++;
        } catch (Throwable failure) {
            failure.printStackTrace();
            report("FAIL phase=" + phase + ": " + failure);
            done = true;
            mc.stop();
        }
    }

    private static void setup() {
        var mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        var player = server.getPlayerList().getPlayer(mc.player.getUUID());
        var level = player.serverLevel();
        level.setDayTime(18000);
        level.setWeatherParameters(6000, 0, false, false);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        player.setGameMode(GameType.CREATIVE);
        for (int x = -20; x <= 20; x++) for (int z = -8; z <= 28; z++) {
            level.setBlockAndUpdate(new BlockPos(x, 200, z), Blocks.SMOOTH_STONE.defaultBlockState());
            for (int y = 201; y <= 215; y++) {
                if (!level.isEmptyBlock(new BlockPos(x, y, z))) {
                    level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        player.teleportTo(level, 0.5, 201, 25.5, Set.of(), 180, -28);
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModFumos.PIGMEE_FUMO_ITEM.get()));
        naturalId = spawn(level, -8, true);
        artificialId = spawn(level, 8, false);
    }

    private static int spawn(net.minecraft.server.level.ServerLevel level, int x, boolean natural) {
        // Keep the server fixture alive; its spawn packet still creates a vanilla client bolt.
        var bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, level) {
            @Override
            public void tick() {
            }
        };
        bolt.moveTo(x + 0.5, 201, 0.5);
        bolt.seed = 123456L;
        bolt.setVisualOnly(true);
        if (natural) bolt.getPersistentData().putBoolean(
                NaturalLightningTransformationHandler.NATURAL_WEATHER_LIGHTNING_TAG, true);
        require(level.addFreshEntity(bolt), "Fixture bolt failed to spawn");
        return bolt.getId();
    }

    private static void checkRenderer(LightningBolt natural, LightningBolt artificial) {
        var player = Minecraft.getInstance().player;
        var originalHead = player.getItemBySlot(EquipmentSlot.HEAD).copy();
        var originalHand = player.getMainHandItem().copy();
        try {
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            var baseline = render(natural, false);
            for (var item : new net.minecraft.world.item.Item[] {ModFumos.PIGMEE_FUMO_ITEM.get(),
                    ModFumos.RAINBOW_PIGMEE_FUMO_ITEM.get(), ModFumos.CREATIVE_PIGMEE_FUMO_ITEM.get(),
                    ModFumos.HYPERDIMENSIONAL_PIGMEE_FUMO_ITEM.get()}) {
                player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(item));
                require(baseline.equals(render(natural, true)), "Tint changed lightning geometry");
                render(artificial, false);
            }
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModFumos.PIGMEE_FUMO_ITEM.get()));
            render(natural, false);
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
            render(natural, false);
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            if (ModList.get().isLoaded("curios")) CuriosChecks.run(natural, artificial);
        } finally {
            player.setItemSlot(EquipmentSlot.HEAD, originalHead);
            player.setItemSlot(EquipmentSlot.MAINHAND, originalHand);
        }
    }

    private static List<Float> render(LightningBolt bolt, boolean tinted) {
        var sink = new ColorCapture(tinted);
        Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(bolt)
                .render(bolt, 0, 0, new PoseStack(), ignored -> sink, 0xF000F0);
        require(sink.colors > 0 && sink.positions.size() == sink.colors * 3, "Renderer emitted no complete vertices");
        return sink.positions;
    }

    private static final class ColorCapture implements VertexConsumer {
        private final boolean tinted;
        private final List<Float> positions = new ArrayList<>();
        private int colors;
        private ColorCapture(boolean tinted) { this.tinted = tinted; }
        public VertexConsumer vertex(double x, double y, double z) {
            positions.add((float) x); positions.add((float) y); positions.add((float) z); return this;
        }
        public VertexConsumer color(int r, int g, int b, int a) {
            require(r == (tinted ? 255 : 114) && g == (tinted ? 186 : 114) && b == (tinted ? 211 : 127),
                    "Wrong RGB: " + r + "," + g + "," + b + "; tinted=" + tinted);
            require(a == 76, "Tint changed alpha");
            colors++; return this;
        }
        public VertexConsumer uv(float u, float v) { return this; }
        public VertexConsumer overlayCoords(int u, int v) { return this; }
        public VertexConsumer uv2(int u, int v) { return this; }
        public VertexConsumer normal(float x, float y, float z) { return this; }
        public void endVertex() {}
        public void defaultColor(int r, int g, int b, int a) {}
        public void unsetDefaultColor() {}
    }

    private static final class CuriosChecks {
        private static void run(LightningBolt natural, LightningBolt artificial) {
            var head = top.theillusivec4.curios.api.CuriosApi.getCuriosHelper().getCuriosHandler(Minecraft.getInstance().player).resolve()
                    .flatMap(inventory -> inventory.getStacksHandler("head")).orElseThrow();
            var original = head.getStacks().getStackInSlot(0).copy();
            try {
                head.getStacks().setStackInSlot(0, new ItemStack(ModFumos.PIGMEE_FUMO_ITEM.get()));
                render(natural, true);
                render(artificial, false);
                head.getStacks().setStackInSlot(0, ItemStack.EMPTY);
                render(natural, false);
            } finally {
                head.getStacks().setStackInSlot(0, original);
            }
        }
    }

    private static void onServer(Runnable task) {
        Minecraft.getInstance().getSingleplayerServer().execute(() -> {
            try { task.run(); } catch (Throwable failure) { serverFailure = failure; }
        });
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void report(String message) {
        System.out.println("PIGMEE_LIGHTNING_CLIENT_PROBE " + message);
        try {
            Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("pigmee-lightning-client-probe.txt"), message + "\n");
        } catch (Exception failure) { failure.printStackTrace(); }
    }
}
