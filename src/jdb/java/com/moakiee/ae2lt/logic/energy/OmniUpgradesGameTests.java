package com.moakiee.ae2lt.logic.energy;

import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import com.moakiee.ae2lt.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2lt")
@PrefixGameTestTemplate(false)
public final class OmniUpgradesGameTests {
    @GameTest(templateNamespace = "ae2lt_catalyzer", template = "empty")
    public static void frequencyCardRetainsNativeBlockUse(GameTestHelper helper) throws Exception {
        var manager = Class.forName("com.sakurain.omni_upgrades.compat.CompatManager");
        var lookup = manager.getMethod("getCompatFor", ItemStack.class);
        var card = new ItemStack(ModItems.OVERLOADED_FREQUENCY_CARD.get());
        helper.assertTrue(lookup.invoke(null, card) == null, "Frequency card must bypass batch installation");
        helper.assertTrue(lookup.invoke(null, AEItems.SPEED_CARD.stack()) != null,
                "Ordinary AE2 upgrade cards retain native batch installation");

        var local = new BlockPos(2, 2, 2);
        helper.setBlock(local, AEBlocks.PATTERN_PROVIDER.block());
        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.setItemInHand(InteractionHand.MAIN_HAND, card);
        var pos = helper.absolutePos(local);
        var event = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        MinecraftForge.EVENT_BUS.post(event);
        helper.assertTrue(!event.isCanceled(), "Omni Upgrades must leave frequency-card block interactions available");
        helper.succeed();
    }
}
