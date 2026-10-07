package com.moakiee.ae2lt.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.block.FumoBlock;
import com.moakiee.ae2lt.blockentity.FumoBlockEntity;
import com.moakiee.ae2lt.lightning.RainbowPigmeeTransformation;
import com.moakiee.ae2lt.registry.ModFumos;

@GameTestHolder(AE2LightningTech.MODID)
@PrefixGameTestTemplate(false)
public final class RainbowPigmeeTransformationGameTests {
    private RainbowPigmeeTransformationGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void namedBlockTransformsAndKeepsFacing(GameTestHelper helper) {
        var position = new BlockPos(1, 1, 1);
        helper.setBlock(position, ModFumos.PIGMEE_FUMO.get().defaultBlockState()
                .setValue(FumoBlock.FACING, net.minecraft.core.Direction.WEST));
        var original = (FumoBlockEntity) helper.getBlockEntity(position);
        original.setCustomName(Component.literal("jeb_"));
        original.toggleSpinning();
        var level = helper.getLevel();
        var bolt = EntityType.LIGHTNING_BOLT.create(level);
        helper.assertTrue(bolt != null, "lightning bolt must be constructible");
        bolt.setPos(helper.absolutePos(position).getX() + 0.5,
                helper.absolutePos(position).getY(), helper.absolutePos(position).getZ() + 0.5);

        RainbowPigmeeTransformation.handleLightning(level, bolt);

        helper.assertTrue(helper.getBlockState(position).is(ModFumos.RAINBOW_PIGMEE_FUMO.get()),
                "named Pigmee block must transform");
        helper.assertTrue(helper.getBlockState(position).getValue(FumoBlock.FACING)
                == net.minecraft.core.Direction.WEST, "facing must survive conversion");
        var converted = (FumoBlockEntity) helper.getBlockEntity(position);
        helper.assertTrue(converted.isSpinning(), "spinning must survive conversion");
        helper.assertTrue(converted.getCustomName() == null, "trigger name must clear");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void namedItemTransformsWithoutLosingOtherTags(GameTestHelper helper) {
        var level = helper.getLevel();
        var position = helper.absolutePos(new BlockPos(1, 1, 1));
        var stack = new ItemStack(ModFumos.PIGMEE_FUMO_ITEM.get());
        stack.setHoverName(Component.literal("jeb_"));
        stack.getOrCreateTag().putInt("SavedValue", 42);
        var item = new ItemEntity(level, position.getX() + 0.5, position.getY(), position.getZ() + 0.5, stack);
        level.addFreshEntity(item);
        var bolt = EntityType.LIGHTNING_BOLT.create(level);
        helper.assertTrue(bolt != null, "lightning bolt must be constructible");
        bolt.setPos(item.getX(), item.getY(), item.getZ());

        RainbowPigmeeTransformation.handleLightning(level, bolt);

        helper.assertTrue(item.getItem().is(ModFumos.RAINBOW_PIGMEE_FUMO_ITEM.get()),
                "named Pigmee item must transform");
        helper.assertTrue(item.getItem().getTag().getInt("SavedValue") == 42,
                "other item data must survive conversion");
        helper.assertTrue(!item.getItem().hasCustomHoverName(), "trigger name must clear");
        helper.succeed();
    }
}
