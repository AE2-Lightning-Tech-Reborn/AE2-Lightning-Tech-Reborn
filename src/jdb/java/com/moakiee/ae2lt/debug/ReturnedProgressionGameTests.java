package com.moakiee.ae2lt.debug;

import com.moakiee.ae2lt.item.FixedInfiniteCellItem;
import com.moakiee.ae2lt.logic.advancement.ProgressionAdvancementService;
import com.moakiee.ae2lt.registry.ModItems;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2lt")
@PrefixGameTestTemplate(false)
public final class ReturnedProgressionGameTests {
    @GameTest(template = "pigmee_station_empty")
    public static void progressionTreeLoadsWithoutPigmee(GameTestHelper h) {
        h.assertTrue(!ModList.get().isLoaded("pigmeecraft"), "This fixture must run without Pigmee");
        var advancements = h.getLevel().getServer().getAdvancements();
        for (String path : List.of("thunderstorm_generator", "observable_black_hole", "multidimensional_expansion")) {
            var advancement = advancement(h, path);
            var parent = advancement.value().parent().orElseThrow();
            h.assertTrue(parent.getNamespace().equals("ae2lt") && advancements.get(parent) != null,
                    "LT progression must have an available LT parent: " + path);
            h.assertTrue(advancements.get(ResourceLocation.fromNamespaceAndPath("pigmeecraft", "main/" + path)) == null,
                    "Duplicate Pigmee progression: " + path);
        }
        h.succeed();
    }

    @GameTest(template = "pigmee_station_empty")
    public static void inspectingCellsAwardsOnlyTheirOwnCriteria(GameTestHelper h) {
        var player = player(h);
        var storm = advancement(h, "thunderstorm_generator");
        var blackHole = advancement(h, "observable_black_hole");
        ProgressionAdvancementService.inspectMysteriousCell(player, new ItemStack(Items.STICK));
        h.assertTrue(!player.getAdvancements().getOrStartProgress(storm).isDone(), "Unrelated item awarded progression");
        ProgressionAdvancementService.inspectMysteriousCell(player, new ItemStack(ModItems.MYSTERIOUS_CELL.get()));
        h.assertTrue(player.getAdvancements().getOrStartProgress(advancement(h, "neutralization")).isDone(),
                "Unresolved cell must still award the existing neutralization criterion");
        ProgressionAdvancementService.inspectMysteriousCell(player,
                FixedInfiniteCellItem.createDisplayedResultStack(FixedInfiniteCellItem.CellOutcome.HIGH_VOLTAGE));
        var progress = player.getAdvancements().getOrStartProgress(storm);
        h.assertTrue(progress.getCriterion("obtain_infinite_high_voltage").isDone()
                && !progress.getCriterion("obtain_infinite_extreme_high_voltage").isDone() && !progress.isDone(),
                "High voltage alone must not complete the two-part advancement");
        ProgressionAdvancementService.inspectMysteriousCell(player,
                FixedInfiniteCellItem.createDisplayedResultStack(FixedInfiniteCellItem.CellOutcome.EXTREME_HIGH_VOLTAGE));
        h.assertTrue(progress.isDone(), "Both voltage tiers must complete thunderstorm generator");
        h.assertTrue(!player.getAdvancements().getOrStartProgress(blackHole).isDone(),
                "Voltage outcomes must not award the collapse-matrix advancement");
        ProgressionAdvancementService.inspectMysteriousCell(player,
                FixedInfiniteCellItem.createDisplayedResultStack(FixedInfiniteCellItem.CellOutcome.LIGHTNING_COLLAPSE_MATRIX));
        h.assertTrue(player.getAdvancements().getOrStartProgress(blackHole).isDone(),
                "Inspecting the infinite collapse matrix must complete observable black hole");
        player.getAdvancements().stopListening();
        h.succeed();
    }

    @GameTest(template = "pigmee_station_empty")
    public static void bothMultidimensionalCoresAreRequired(GameTestHelper h) {
        var player = player(h);
        var advancement = advancement(h, "multidimensional_expansion");
        int slot = 0;
        for (String path : List.of("tianshu_multidimensional_main_core", "matter_warping_matrix_multidimensional_main_core")) {
            var item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("ae2lt", path));
            h.assertTrue(item != Items.AIR, "Missing LT multidimensional core: " + path);
            var stack = new ItemStack(item);
            player.getInventory().setItem(slot++, stack);
            CriteriaTriggers.INVENTORY_CHANGED.trigger(player, player.getInventory(), stack);
            h.assertTrue(player.getAdvancements().getOrStartProgress(advancement).isDone() == (slot == 2),
                    "Multidimensional expansion must require both cores");
        }
        player.getAdvancements().stopListening();
        h.succeed();
    }

    private static AdvancementHolder advancement(GameTestHelper h, String path) {
        var advancement = h.getLevel().getServer().getAdvancements()
                .get(ResourceLocation.fromNamespaceAndPath("ae2lt", "main/" + path));
        h.assertTrue(advancement != null, "Missing LT advancement: " + path);
        return advancement;
    }

    private static ServerPlayer player(GameTestHelper h) {
        return new ServerPlayer(h.getLevel().getServer(), h.getLevel(),
                new GameProfile(UUID.randomUUID(), "ReturnedProgression"), ClientInformation.createDefault());
    }
}
