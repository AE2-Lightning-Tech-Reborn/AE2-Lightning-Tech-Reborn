package com.moakiee.ae2lt.blockentity;

import com.moakiee.ae2lt.machine.miningfactory.MiningLoot;
import dev.shadowsoffire.apotheosis.Apoth;
import dev.shadowsoffire.apotheosis.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.affix.AffixInstance;
import dev.shadowsoffire.apotheosis.affix.AffixRegistry;
import dev.shadowsoffire.apotheosis.affix.effect.RadialAffix;
import dev.shadowsoffire.apotheosis.loot.RarityRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Opt-in tests compiled against actual optional mods; never shipped. */
@GameTestHolder("ae2lt_mining")
@PrefixGameTestTemplate(false)
public final class MiningFactoryCompatGameTests {
    private static final BlockPos POS = new BlockPos(2, 2, 2);
    private static ItemStack affixed(String path) {
        var tool = new ItemStack(Items.DIAMOND_PICKAXE);
        var rarity = RarityRegistry.INSTANCE.holder(ResourceLocation.parse("apotheosis:mythic"));
        AffixHelper.setRarity(tool, rarity.get());
        AffixHelper.applyAffix(tool, new AffixInstance(AffixRegistry.INSTANCE.holder(
                ResourceLocation.parse("apotheosis:" + path)), 1, rarity, tool));
        return tool;
    }
    private static void enchant(GameTestHelper h, ItemStack tool, String id, int level) {
        tool.enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.parse(id))), level);
    }
    @GameTest(template = "empty")
    public static void apotheosisStoneformingPreservesSelectedTarget(GameTestHelper h) {
        var tool = affixed("breaker/ability/stoneforming");
        tool.set(Apoth.Components.STONEFORMING_TARGET, Blocks.ANDESITE);
        tool.enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.SILK_TOUCH), 1);
        var result = MiningLoot.roll(h.getLevel(), h.absolutePos(POS), Blocks.STONE.defaultBlockState(), tool, 256, 8);
        h.assertTrue(result.drops().stream().allMatch(d -> d.stack().is(Items.ANDESITE))
                && result.drops().stream().mapToLong(MiningLoot.Drop::count).sum() == 256,
                "Stoneforming must convert all weighted drops to the selected andesite");
        h.assertTrue(tool.getDamageValue() == 0 && result.tool().getDamageValue() == 256,
                "Affix must not mutate installed tool or change durability cost");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void apotheosisRadialNeverBreaksWorldBlocks(GameTestHelper h) {
        var tool = affixed("breaker/effect/radial");
        h.assertTrue(RadialAffix.getRadialData(tool).x() > 1, "Fixture does not have active area mining");
        assertNoWorldMining(h, tool, Blocks.IRON_ORE.defaultBlockState());
    }
    @GameTest(template = "empty")
    public static void chainsawNeverBreaksWorldTrees(GameTestHelper h) {
        var tool = new ItemStack(Items.DIAMOND_AXE);
        enchant(h, tool, "apothic_enchanting:chainsaw", 1);
        assertNoWorldMining(h, tool, Blocks.OAK_LOG.defaultBlockState());
    }
    private static void assertNoWorldMining(GameTestHelper h, ItemStack tool,
            net.minecraft.world.level.block.state.BlockState state) {
        for (var p : BlockPos.betweenClosed(POS.offset(-2, -1, -2), POS.offset(2, 2, 2))) h.setBlock(p, state);
        var events = new AtomicInteger();
        Consumer<BlockEvent.BreakEvent> breaks = e -> events.incrementAndGet();
        Consumer<BlockDropsEvent> drops = e -> events.incrementAndGet();
        NeoForge.EVENT_BUS.addListener(breaks);
        NeoForge.EVENT_BUS.addListener(drops);
        try {
            var result = MiningLoot.roll(h.getLevel(), h.absolutePos(POS), state, tool, 256, 8);
            h.assertTrue(result.processed() == 256 && result.samples() == 8, "Invalid sampled batch");
            h.assertTrue(events.get() == 0, "Virtual loot fired real mining events");
            for (var p : BlockPos.betweenClosed(POS.offset(-2, -1, -2), POS.offset(2, 2, 2)))
                h.assertTrue(h.getBlockState(p).equals(state), "Area mining modified the real world");
        } finally {
            NeoForge.EVENT_BUS.unregister(breaks);
            NeoForge.EVENT_BUS.unregister(drops);
        }
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void boonOfEarthAddsWeightedOres(GameTestHelper h) {
        var tool = new ItemStack(Items.DIAMOND_PICKAXE);
        // 100% chance isolates integration correctness from random failures.
        enchant(h, tool, "apothic_enchanting:boon_of_the_earth", 100);
        var result = MiningLoot.roll(h.getLevel(), h.absolutePos(POS), Blocks.STONE.defaultBlockState(), tool, 256, 8);
        long ore = result.drops().stream().filter(d -> !d.stack().is(Items.COBBLESTONE)).mapToLong(MiningLoot.Drop::count).sum();
        h.assertTrue(ore > 0, "Boon of the Earth added no ore");
        h.assertTrue(result.samples() == 8 && result.drops().stream().filter(d -> d.stack().is(Items.COBBLESTONE))
                .mapToLong(MiningLoot.Drop::count).sum() == 256, "Boon replaced baseline drops or sample budget");
        h.succeed();
    }

    private static ItemStack anointed(GameTestHelper h, int fortune, int charges) {
        var tool = new ItemStack(Items.DIAMOND_PICKAXE);
        var holder = new wayoftime.bloodmagic.anointment.AnointmentHolder();
        h.assertTrue(holder.applyAnointment(tool, wayoftime.bloodmagic.core.AnointmentRegistrar.ANOINTMENT_FORTUNE.get(),
                new wayoftime.bloodmagic.anointment.AnointmentData(fortune, 0, charges)), "Failed to apply actual Fortune anointment");
        holder.toItemStack(tool);
        return tool;
    }
    private static int oilUsed(ItemStack tool) {
        var holder = wayoftime.bloodmagic.anointment.AnointmentHolder.fromItemStack(tool);
        var data = holder.getAnointments().get(wayoftime.bloodmagic.core.AnointmentRegistrar.ANOINTMENT_FORTUNE.get());
        return data == null ? -1 : data.getDamage();
    }
    @GameTest(template = "empty")
    public static void bloodMagicFortuneStacksAndPaysPerBlock(GameTestHelper h) {
        long coated = 0, plain = 0;
        for (int i = 0; i < 64; i++) {
            var tool = anointed(h, 3, 256);
            enchant(h, tool, "minecraft:fortune", 3);
            var result = MiningLoot.roll(h.getLevel(), h.absolutePos(POS), Blocks.IRON_ORE.defaultBlockState(), tool, 64, 8);
            coated += result.drops().stream().mapToLong(MiningLoot.Drop::count).sum();
            h.assertTrue(oilUsed(tool) == 0 && oilUsed(result.tool()) == 64,
                    "Anointment must cost 64 actual blocks, not eight samples, and preserve the installed copy");
            h.assertTrue(!tool.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA).contains("bloodmagic:checked_fortune")
                    && !result.tool().get(net.minecraft.core.component.DataComponents.CUSTOM_DATA).contains("bloodmagic:checked_fortune"),
                    "Blood Magic recursion marker leaked into installed or committed tool");
            var base = new ItemStack(Items.DIAMOND_PICKAXE);
            enchant(h, base, "minecraft:fortune", 3);
            plain += MiningLoot.roll(h.getLevel(), h.absolutePos(POS), Blocks.IRON_ORE.defaultBlockState(), base, 64, 8)
                    .drops().stream().mapToLong(MiningLoot.Drop::count).sum();
        }
        h.assertTrue(coated > plain * 1.3 && coated <= 64L * 64 * 8, "Fortune III oil failed to stack with Fortune III enchantment");
        System.out.println("MINING_COMPAT fortune3_plus_oil3=" + coated + " fortune3=" + plain + " input_each=4096");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void bloodMagicOilExpiryEndsBatchBeforeUnpaidBonus(GameTestHelper h) {
        var tool = anointed(h, 3, 3);
        var first = MiningLoot.roll(h.getLevel(), h.absolutePos(POS), Blocks.IRON_ORE.defaultBlockState(), tool, 64, 8);
        h.assertTrue(first.processed() == 3 && first.samples() == 3 && oilUsed(first.tool()) == -1,
                "Three remaining charges must end the batch after three blocks and remove the effect");
        var second = MiningLoot.roll(h.getLevel(), h.absolutePos(POS), Blocks.IRON_ORE.defaultBlockState(), first.tool(), 61, 8);
        h.assertTrue(second.processed() == 61 && second.tool().getDamageValue() == 64
                && second.drops().stream().mapToLong(MiningLoot.Drop::count).sum() == 61,
                "Expired oil kept granting Fortune or lost unprocessed blocks");
        h.assertTrue(oilUsed(tool) == 0, "Prospective expiry mutated the original tool");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void bloodMagicBlockedFactoryDoesNotSpendOil(GameTestHelper h) {
        var be = MiningFactoryGameTests.fixture(h, anointed(h, 3, 256), 64, 1_000_000);
        for (int i = 2; i < 11; i++) be.getInventory().setItemDirect(i, new ItemStack(Items.STONE, 4096));
        MiningFactoryGameTests.completeCycle(h, be, () -> {
            h.assertTrue(oilUsed(be.getInventory().getStackInSlot(1)) == 0 && be.getAvailableLightning() == 1000,
                    "Blocked output spent oil or lightning");
            h.succeed();
        });
    }
    @GameTest(template = "empty")
    public static void bloodMagicFactoryCommitsOilWithBatchCosts(GameTestHelper h) {
        var be = MiningFactoryGameTests.fixture(h, anointed(h, 3, 256), 64, 1_000_000);
        MiningFactoryGameTests.completeCycle(h, be, () -> {
            h.assertTrue(oilUsed(be.getInventory().getStackInSlot(1)) == 64
                    && be.getAvailableLightning() == 999 && be.getInventory().getStackInSlot(0).isEmpty(),
                    "Completed batch must atomically commit all 64 oil uses and one lightning");
            h.succeed();
        });
    }
    @GameTest(template = "empty")
    public static void boonRealLevelFiveAndPlane(GameTestHelper h) {
        var tool = new ItemStack(Items.DIAMOND_PICKAXE);
        enchant(h, tool, "apothic_enchanting:boon_of_the_earth", 5);
        long ore = 0;
        for (int i = 0; i < 128; i++) {
            var result = MiningLoot.roll(h.getLevel(), h.absolutePos(POS), Blocks.STONE.defaultBlockState(), tool, 64, 8);
            ore += result.drops().stream().filter(d -> !d.stack().is(Items.COBBLESTONE)).mapToLong(MiningLoot.Drop::count).sum();
        }
        h.assertTrue(ore > 0, "Normal level V Boon never produced ore");
        var plane = new ItemStack(appeng.core.definitions.AEParts.ANNIHILATION_PLANE.asItem());
        enchant(h, plane, "apothic_enchanting:boon_of_the_earth", 100);
        var result = MiningLoot.roll(h.getLevel(), h.absolutePos(POS), Blocks.STONE.defaultBlockState(), plane, 64, 8);
        h.assertTrue(result.drops().stream().anyMatch(d -> !d.stack().is(Items.COBBLESTONE)), "Plane lost Boon enchantment");
        System.out.println("MINING_COMPAT boon5_ore=" + ore + " stone_input=8192 samples=1024");
        h.succeed();
    }
}
