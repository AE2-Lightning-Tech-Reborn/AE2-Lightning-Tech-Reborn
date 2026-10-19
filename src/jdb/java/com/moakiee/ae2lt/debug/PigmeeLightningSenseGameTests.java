package com.moakiee.ae2lt.debug;

import com.moakiee.ae2lt.event.NaturalLightningTransformationHandler;
import com.moakiee.ae2lt.item.FumoBlockItem;
import com.moakiee.ae2lt.registry.ModFumos;
import com.moakiee.ae2lt.util.NaturalLightningOrigin;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2lt_pigmee_lightning")
@PrefixGameTestTemplate(false)
public final class PigmeeLightningSenseGameTests {
    @GameTest(templateNamespace = "ae2lt_mining", template = "empty")
    public static void naturalOriginTravelsInTheSpawnBundle(GameTestHelper h) {
        var bolt = bolt(h, true);
        h.assertTrue(((NaturalLightningOrigin) bolt).ae2lt$isNaturalWeatherLightning(),
                "Natural marker was not copied by the real entity join event");
        var packets = new ArrayList<Packet<ClientGamePacketListener>>();
        new ServerEntity(h.getLevel(), bolt, Integer.MAX_VALUE, false, ignored -> {})
                .sendPairingData(player(h), packets::add);
        h.assertTrue(packets.get(0) instanceof ClientboundAddEntityPacket,
                "Entity must spawn before its metadata");
        var metadata = packets.stream().filter(packet -> packet instanceof ClientboundSetEntityDataPacket)
                .map(packet -> (ClientboundSetEntityDataPacket) packet)
                .findFirst().orElseThrow();
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            metadata.write(buffer);
            var decoded = new ClientboundSetEntityDataPacket(buffer);
            h.assertTrue(decoded.id() == bolt.getId(), "Origin metadata belongs to a different bolt");
            var recipient = EntityType.LIGHTNING_BOLT.create(h.getLevel());
            h.assertTrue(!((NaturalLightningOrigin) recipient).ae2lt$isNaturalWeatherLightning(),
                    "New bolts must default to artificial");
            recipient.getEntityData().assignValues(decoded.packedItems());
            h.assertTrue(((NaturalLightningOrigin) recipient).ae2lt$isNaturalWeatherLightning(),
                    "Encoded spawn metadata did not carry the natural origin");
            h.assertTrue(!recipient.getPersistentData().getBoolean(
                    NaturalLightningTransformationHandler.NATURAL_WEATHER_LIGHTNING_TAG),
                    "Sync must work without copying server persistent NBT");
        } finally {
            buffer.release();
            bolt.discard();
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "ae2lt_mining", template = "empty")
    public static void artificialLightningWithoutPlayerCauseStaysArtificial(GameTestHelper h) {
        var bolt = bolt(h, false);
        h.assertTrue(bolt.getCause() == null, "Command-like fixture must have no player cause");
        h.assertTrue(!((NaturalLightningOrigin) bolt).ae2lt$isNaturalWeatherLightning(),
                "Null cause is not natural lightning");
        bolt.discard();
        h.succeed();
    }

    @GameTest(templateNamespace = "ae2lt_mining", template = "empty")
    public static void allPigmeesWorkOnlyWhenWorn(GameTestHelper h) {
        var player = player(h);
        for (var item : new net.minecraft.world.item.Item[] {ModFumos.PIGMEE_FUMO_ITEM.get(),
                ModFumos.RAINBOW_PIGMEE_FUMO_ITEM.get(), ModFumos.CREATIVE_PIGMEE_FUMO_ITEM.get(),
                ModFumos.HYPERDIMENSIONAL_PIGMEE_FUMO_ITEM.get()}) {
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(item));
            h.assertTrue(FumoBlockItem.isWearingPigmee(player), "Pigmee head variant missing: " + item);
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(item));
            h.assertTrue(!FumoBlockItem.isWearingPigmee(player), "Holding Pigmee must not enable the effect");
        }
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModFumos.MOAKIEE_FUMO_ITEM.get()));
        h.assertTrue(!FumoBlockItem.isWearingPigmee(player), "Unrelated Fumo must not enable the effect");
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        h.assertTrue(!FumoBlockItem.isWearingPigmee(player), "Normal helmets must not enable the effect");
        h.assertTrue(ModList.get().isLoaded("curios") == Boolean.getBoolean("ae2lt.pigmeeSenseWithCurios"),
                "Optional dependency test did not load the requested environment");
        h.succeed();
    }

    @GameTest(templateNamespace = "ae2lt_mining", template = "empty")
    public static void curiosHeadSlotWorksWhenInstalled(GameTestHelper h) {
        if (ModList.get().isLoaded("curios")) CuriosChecks.run(h);
        h.succeed();
    }

    private static LightningBolt bolt(GameTestHelper h, boolean natural) {
        var bolt = EntityType.LIGHTNING_BOLT.create(h.getLevel());
        bolt.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(1, 2, 1)));
        bolt.setVisualOnly(true);
        if (natural) bolt.getPersistentData().putBoolean(
                NaturalLightningTransformationHandler.NATURAL_WEATHER_LIGHTNING_TAG, true);
        h.assertTrue(h.getLevel().addFreshEntity(bolt), "Could not spawn test bolt");
        return bolt;
    }

    private static FakePlayer player(GameTestHelper h) {
        return new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "PigmeeLightningQA"));
    }

    private static final class CuriosChecks {
        private static void run(GameTestHelper h) {
            var player = player(h);
            var head = top.theillusivec4.curios.api.CuriosApi.getCuriosHelper().getCuriosHandler(player).resolve()
                    .flatMap(inventory -> inventory.getStacksHandler("head")).orElseThrow();
            h.assertTrue(head.getSlots() > 0, "Pigmee Curios head slot must exist");
            head.getStacks().setStackInSlot(0, new ItemStack(ModFumos.PIGMEE_FUMO_ITEM.get()));
            h.assertTrue(FumoBlockItem.isWearingPigmee(player), "Functional Curios head slot must enable the effect");
            head.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            h.assertTrue(!FumoBlockItem.isWearingPigmee(player), "Unequipping Curios must disable the effect");
            if (head.getCosmeticStacks().getSlots() > 0) {
                head.getCosmeticStacks().setStackInSlot(0, new ItemStack(ModFumos.PIGMEE_FUMO_ITEM.get()));
                h.assertTrue(!FumoBlockItem.isWearingPigmee(player), "Cosmetic slot must not grant the effect");
            }
        }
    }
}
