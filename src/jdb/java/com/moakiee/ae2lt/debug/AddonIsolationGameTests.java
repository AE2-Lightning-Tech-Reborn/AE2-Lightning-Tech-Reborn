package com.moakiee.ae2lt.debug;

import com.moakiee.ae2lt.api.cell.MysteriousCellRewards;
import com.moakiee.ae2lt.item.FixedInfiniteCellItem;
import com.moakiee.ae2lt.registry.ModFumos;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2lt")
@PrefixGameTestTemplate(false)
public final class AddonIsolationGameTests {
    @GameTest(template = "pigmee_station_empty")
    public static void coreHasOnlyTheOriginalPigmee(GameTestHelper h) {
        h.assertTrue(BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2lt:pigmee_fumo"))
                == ModFumos.PIGMEE_FUMO_ITEM.get(), "original pigmee disappeared");
        for (var id : BuiltInRegistries.ITEM.keySet()) {
            h.assertTrue(!id.getNamespace().equals("pigmeetech"), "addon accidentally loaded");
            h.assertTrue(!id.getNamespace().equals("ae2lt") || !id.getPath().contains("pigmee")
                    || id.getPath().equals("pigmee_fumo"), "core still registers " + id);
        }
        h.assertTrue(!BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse("ae2lt:research_note")), "research note still registered");
        h.succeed();
    }

    @GameTest(template = "pigmee_station_empty")
    public static void allDrawsExcludeTheAbsentResearchReward(GameTestHelper h) {
        h.assertTrue(!MysteriousCellRewards.hasRareReward() && MysteriousCellRewards.rareWeight() == 0,
                "rare prize available without addon");
        for (int roll = 0; roll < 10000; roll++) {
            var result = FixedInfiniteCellItem.resolveOutcome(new UUID(0, roll), 0);
            h.assertTrue(result != FixedInfiniteCellItem.CellOutcome.ADDON_REWARD, "research prize drawn");
        }
        h.succeed();
    }
}
