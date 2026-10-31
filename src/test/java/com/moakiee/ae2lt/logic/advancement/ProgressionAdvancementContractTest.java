package com.moakiee.ae2lt.logic.advancement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ProgressionAdvancementContractTest {
    private static final Path ADVANCEMENT_ROOT =
            Path.of("src/main/resources/data/ae2lt/advancement/main");

    @Test
    void documentProgressionUsesTheRequestedParentsFramesAndVisibility() throws Exception {
        assertDisplay("radiation_assimilation", "ae2lt:main/root", "challenge", true);
        assertDisplay("annihilation", "ae2lt:main/overload_singularity", "task", false);

        assertDisplay("neutralization", "ae2lt:main/lightning_collapse_matrix", "goal", true);
        assertDisplay("construct_fragment", "ae2lt:main/neutralization", "goal", false);
        assertDisplay("author_fufu", "ae2lt:main/neutralization", "goal", false);


    }

    @Test
    void overloadTntMustBeCraftedAndPersonallyIgnited() throws Exception {
        JsonObject advancement = advancement("annihilation");
        JsonObject criteria = advancement.getAsJsonObject("criteria");

        assertEquals(
                "ae2lt:overload_tnt_from_gunpowder",
                criteria.getAsJsonObject("craft_overload_tnt")
                        .getAsJsonObject("conditions")
                        .get("recipe_id")
                        .getAsString());
        assertEquals(
                "minecraft:impossible",
                criteria.getAsJsonObject("ignite_overload_tnt").get("trigger").getAsString());
        assertEquals(2, advancement.getAsJsonArray("requirements").size());

        String block = Files.readString(
                Path.of("src/main/java/com/moakiee/ae2lt/block/OverloadTntBlock.java"));
        assertTrue(block.contains("igniter instanceof ServerPlayer player"));
        assertTrue(block.contains("ProgressionAdvancementService.awardOverloadTntIgnited(player)"));
    }

    @Test
    void mysteriousCellVariantsAwardOnlyTheirMatchingBranches() throws Exception {
        String service = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/logic/advancement/ProgressionAdvancementService.java"));
        String item = Files.readString(
                Path.of("src/main/java/com/moakiee/ae2lt/item/FixedInfiniteCellItem.java"));

        assertTrue(service.contains("if (!FixedInfiniteCellItem.hasType(stack))"));
        assertTrue(service.contains("CONSTRUCT_FRAGMENT, \"obtain_infinite_lightning_rod\""));
        assertTrue(item.contains("ProgressionAdvancementService.inspectMysteriousCell(player, stack)"));

        assertEquals(1, advancement("construct_fragment").getAsJsonArray("requirements").size());
    }

    @Test
    void pairedCollectiblesRequireBothItems() throws Exception {
        JsonObject authors = advancement("author_fufu");
        assertTrue(authors.toString().contains("ae2lt:moakiee_fumo"));
        assertTrue(authors.toString().contains("ae2lt:cystrysu_fumo"));
        assertEquals(2, authors.getAsJsonArray("requirements").size());


    }

    @Test
    void chineseTextMatchesTheSelectedAchievementSet() throws Exception {
        String chinese = Files.readString(
                Path.of("src/main/resources/assets/ae2lt/lang/zh_cn.json"));

        for (String title : new String[]{
                "身在辐中不知福",
                "湮灭",
                "中和",
                "构像残片",
                "这是什么？可爱的fufu？rua一下",
}) {
            assertTrue(chinese.contains("\": \"" + title + "\""), title);
        }
    }

    private static void assertDisplay(
            String id,
            String parent,
            String frame,
            boolean hidden) throws Exception {
        JsonObject advancement = advancement(id);
        assertEquals(parent, advancement.get("parent").getAsString(), id);
        JsonObject display = advancement.getAsJsonObject("display");
        assertEquals(frame, display.get("frame").getAsString(), id);
        assertEquals(hidden, display.get("hidden").getAsBoolean(), id);
    }

    private static JsonObject advancement(String id) throws Exception {
        return JsonParser.parseString(
                        Files.readString(ADVANCEMENT_ROOT.resolve(id + ".json")))
                .getAsJsonObject();
    }
}
