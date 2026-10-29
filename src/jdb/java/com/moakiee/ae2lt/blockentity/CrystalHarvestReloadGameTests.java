package com.moakiee.ae2lt.blockentity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.moakiee.ae2lt.machine.crystalcatalyzer.CrystalCatalyzerInventory;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerLockedRecipe;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerRecipe;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerRecipeService;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalHarvestOutput;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.registry.ModBlocks;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

/** Isolated reload suite; its temporary datapack never enters the published mod. */
@GameTestHolder("ae2lt_harvest_reload")
@PrefixGameTestTemplate(false)
public final class CrystalHarvestReloadGameTests {
    private static final String RECIPE = "budding_amethyst";
    private static final String PACK = "ae2lt-harvest-test";
    private static final ResourceLocation MODIFIER = new ResourceLocation("ae2lt:harvest_test");
    private static final BlockPos POS = new BlockPos(2, 2, 2);

    @GameTest(template = "empty", timeoutTicks = 800)
    public static void actualHarvestReloadAndClientSync(GameTestHelper h) throws Exception {
        var server = h.getLevel().getServer();
        var originalPacks = List.copyOf(server.getPackRepository().getSelectedIds());
        var original = recipe(h);
        var originalOutput = (CrystalHarvestOutput) original.outputSpec();
        check(original.getOutputTemplate().is(Items.AMETHYST_SHARD), "startup harvest was not resolved");
        var stale = new CrystalCatalyzerLockedRecipe(new ResourceLocation("ae2lt:crystal_catalyzer/" + RECIPE),
                original.getOutputTemplate(), 0, 1, 0, LightningKey.Tier.HIGH_VOLTAGE);

        var pack = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(PACK);
        write(pack, "pack.mcmeta", """
                {"pack":{"pack_format":15,"description":"Isolated mother rock harvest test"}}
                """);
        writeLoot(pack, "minecraft:quartz");
        writeModifiers(pack, false);
        server.getPackRepository().reload();
        var selected = new ArrayList<>(originalPacks);
        selected.add("file/" + PACK);
        server.reloadResources(selected).join();
        verify(h, Items.QUARTZ);
        originalOutput.resolveHarvest(h.getLevel());
        check(originalOutput.resolve().is(Items.QUARTZ), "harvest cache survived a loot reload");

        h.setBlock(POS, ModBlocks.PIGMEE_CRYSTAL_CATALYZER.get());
        CrystalCatalyzerBlockEntity host = (CrystalCatalyzerBlockEntity) h.getBlockEntity(POS);
        var tag = new CompoundTag();
        host.saveAdditional(tag);
        tag.put("LockedRecipe", stale.toTag());
        host.load(tag);
        host.getInventory().setItemDirect(CrystalCatalyzerInventory.SLOT_CATALYST,
                new ItemStack(Items.BUDDING_AMETHYST, 64));
        host.getTank().setFluid(recipe(h).fluidInput());
        host.getLogic().tickStandalone();
        check(!host.hasLockedRecipe(), "old harvest survived the reload");

        h.runAfterDelay(130, () -> {
            var output = host.getInventory().getStackInSlot(CrystalCatalyzerInventory.SLOT_OUTPUT);
            check(output.is(Items.QUARTZ) && output.getCount() == 16,
                    "Pigmee did not produce sixteen ordinary crystals after reload: " + output);
            check(host.getFluid().isEmpty()
                            && host.getInventory().getStackInSlot(CrystalCatalyzerInventory.SLOT_CATALYST).getCount() == 64,
                    "harvest reload changed water use or consumed mother rocks");
            try {
                writeModifiers(pack, true);
                server.reloadResources(selected).join();
                verify(h, Items.PRISMARINE_CRYSTALS);

                writeModifiers(pack, false);
                writeLoot(pack);
                server.reloadResources(selected).join();
                verifyDisabled(h, host);

                writeLoot(pack, "minecraft:quartz", "minecraft:amethyst_shard");
                server.reloadResources(selected).join();
                verifyDisabled(h, host);

                server.reloadResources(originalPacks).join();
                verify(h, Items.AMETHYST_SHARD);
                h.succeed();
            } catch (Exception e) {
                throw new RuntimeException("harvest reload fixture failed", e);
            }
        });
    }

    private static CrystalCatalyzerRecipe recipe(GameTestHelper h) {
        return CrystalCatalyzerRecipeService.findRecipeById(h.getLevel(),
                new ResourceLocation("ae2lt:crystal_catalyzer/" + RECIPE)).orElseThrow().recipe();
    }

    private static void verify(GameTestHelper h, Item expected) {
        var current = recipe(h);
        check(current.getOutputTemplate().is(expected) && current.getOutputTemplate().getCount() == 16,
                "server output did not follow current harvest: " + current.getOutputTemplate());
        var drops = Block.getDrops(Blocks.AMETHYST_CLUSTER.defaultBlockState(), h.getLevel(),
                BlockPos.ZERO, null, null, new ItemStack(Items.DIAMOND_PICKAXE));
        check(drops.stream().anyMatch(stack -> stack.is(expected)), "fixture did not change actual harvest");
        verifyWire(h, current);
    }

    private static void verifyDisabled(GameTestHelper h, CrystalCatalyzerBlockEntity host) {
        var current = recipe(h);
        check(current.getOutputTemplate().isEmpty(), "ambiguous or empty harvest was substituted");
        host.getTank().setFluid(current.fluidInput());
        check(host.findProcessableRecipe().isEmpty(), "disabled harvest was selected by the machine");
        verifyWire(h, current);
    }

    private static void verifyWire(GameTestHelper h, CrystalCatalyzerRecipe recipe) {
        var serializer = new CrystalCatalyzerRecipe.Serializer();
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            serializer.toNetwork(buffer, recipe);
            var client = serializer.fromNetwork(recipe.getId(), buffer);
            check(ItemStack.matches(client.getOutputTemplate(), recipe.getOutputTemplate()),
                    "client recipe differs from actual harvest after reload");
        } finally { buffer.release(); }
    }

    private static void writeLoot(Path pack, String... products) throws Exception {
        var pools = new ArrayList<String>();
        for (var product : products) {
            pools.add("{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"" + product + "\"}]}");
        }
        write(pack, "data/minecraft/loot_tables/blocks/amethyst_cluster.json",
                "{\"type\":\"minecraft:block\",\"pools\":[" + String.join(",", pools) + "]}");
    }

    private static void writeModifiers(Path pack, boolean enabled) throws Exception {
        write(pack, "data/forge/loot_modifiers/global_loot_modifiers.json",
                "{\"replace\":true,\"entries\":" + (enabled ? "[\"ae2lt:harvest_test\"]" : "[]") + "}");
        write(pack, "data/ae2lt/loot_modifiers/harvest_test.json", """
                {"type":"ae2lt:harvest_test","conditions":[
                  {"condition":"forge:loot_table_id","loot_table_id":"minecraft:blocks/amethyst_cluster"}]}
                """);
    }

    private static void write(Path root, String path, String content) throws Exception {
        var file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private static void check(boolean valid, String message) {
        if (!valid) throw new net.minecraft.gametest.framework.GameTestAssertException(message);
    }

    @EventBusSubscriber(modid = "ae2lt", bus = EventBusSubscriber.Bus.MOD)
    public static final class TestLootModifier extends LootModifier {
        private static final Codec<TestLootModifier> CODEC = RecordCodecBuilder.create(
                instance -> codecStart(instance).apply(instance, TestLootModifier::new));

        public TestLootModifier(LootItemCondition[] conditions) {
            super(conditions);
        }

        @SubscribeEvent
        public static void register(RegisterEvent event) {
            event.register(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, MODIFIER, () -> CODEC);
        }

        @Override
        protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
            return new ObjectArrayList<>(List.of(new ItemStack(Items.PRISMARINE_CRYSTALS, 4)));
        }

        @Override
        public Codec<? extends IGlobalLootModifier> codec() {
            return CODEC;
        }
    }
}
