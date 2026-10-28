package com.moakiee.ae2lt.gametest;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.block.FumoBlock;
import com.moakiee.ae2lt.blockentity.FumoBlockEntity;
import com.moakiee.ae2lt.blockentity.MiningFactoryBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedIOPortBlockEntity;
import com.moakiee.ae2lt.lightning.ProtectedItemEntityHelper;
import com.moakiee.ae2lt.lightning.RainbowPigmeeTransformation;
import com.moakiee.ae2lt.machine.miningfactory.MiningFactoryInventory;
import com.moakiee.ae2lt.recipe.RainbowPigmeeDyeRecipe;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModFumos;
import com.moakiee.ae2lt.registry.ModRecipeTypes;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AE2LightningTech.MODID)
@PrefixGameTestTemplate(false)
public final class UpstreamPortGameTests {
    private UpstreamPortGameTests() {}

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void machineRegistrationsAndAutomationLoad(GameTestHelper helper) {
        var miningPos = new BlockPos(2, 2, 2);
        helper.setBlock(miningPos, ModBlocks.MINING_FACTORY.get());
        helper.setBlock(new BlockPos(4, 2, 2), ModBlocks.OVERLOADED_IO_PORT.get());
        helper.assertTrue(helper.getBlockEntity(miningPos) instanceof MiningFactoryBlockEntity,
                "Mining factory block entity must be registered");
        helper.assertTrue(helper.getBlockEntity(new BlockPos(4, 2, 2)) instanceof OverloadedIOPortBlockEntity,
                "IO port block entity must be registered");
        var mining = (MiningFactoryBlockEntity) helper.getBlockEntity(miningPos);
        var automation = mining.getAutomationInventory();
        helper.assertTrue(automation.insertItem(MiningFactoryInventory.INPUT,
                new ItemStack(Items.STONE, 1000), false).isEmpty(), "Automation must accept large input stacks");
        helper.assertTrue(automation.extractItem(MiningFactoryInventory.INPUT, 1000, false).isEmpty(),
                "Automation must not extract inputs");
        mining.getInventory().insertOutput(new ItemStack(Items.DIAMOND, 1000));
        helper.assertTrue(automation.extractItem(MiningFactoryInventory.OUTPUT, 1000, false).getCount() == 1000,
                "Automation must extract generated outputs");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void namedPigmeeTransformsInBothForms(GameTestHelper helper) {
        var relativePos = new BlockPos(2, 2, 2);
        helper.setBlock(relativePos, ModFumos.PIGMEE_FUMO.get().defaultBlockState()
                .setValue(FumoBlock.FACING, Direction.WEST));
        var fumo = (FumoBlockEntity) helper.getBlockEntity(relativePos);
        fumo.setCustomName(Component.literal("jeb_"));
        fumo.toggleSpinning();
        var stack = new ItemStack(ModFumos.PIGMEE_FUMO_ITEM.get(), 3);
        stack.setHoverName(Component.literal("jeb_"));
        stack.setRepairCost(7);
        var absolute = helper.absolutePos(relativePos);
        var item = new ItemEntity(helper.getLevel(), absolute.getX() + 0.5,
                absolute.getY() + 0.5, absolute.getZ() + 0.5, stack);
        helper.getLevel().addFreshEntity(item);
        var bolt = EntityType.LIGHTNING_BOLT.create(helper.getLevel());
        bolt.moveTo(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5);
        RainbowPigmeeTransformation.handleLightning(helper.getLevel(), bolt);
        helper.assertTrue(item.getItem().is(ModFumos.RAINBOW_PIGMEE_FUMO_ITEM.get())
                && item.getItem().getCount() == 3 && item.getItem().getBaseRepairCost() == 7,
                "Dropped Pigmee must preserve count and unrelated NBT");
        helper.assertTrue(!item.getItem().hasCustomHoverName() && ProtectedItemEntityHelper.isProtectedItem(item),
                "Transformed drops must clear trigger name and gain lightning protection");
        var converted = (FumoBlockEntity) helper.getBlockEntity(relativePos);
        helper.assertTrue(converted.getBlockState().is(ModFumos.RAINBOW_PIGMEE_FUMO.get())
                && converted.getBlockState().getValue(FumoBlock.FACING) == Direction.WEST
                && converted.isSpinning() && converted.getCustomName() == null,
                "Placed conversion must preserve orientation and spin, clearing trigger name");
        item.discard();
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void pigmeeBuildingRecipeReturnsNamedCatalyst(GameTestHelper helper) {
        var level = helper.getLevel();
        var id = new ResourceLocation(AE2LightningTech.MODID, "pigmee_building_block");
        var recipe = (com.moakiee.ae2lt.recipe.PigmeeBuildingRecipe)
                level.getRecipeManager().byKey(id).orElseThrow();
        var menu = new AbstractContainerMenu(null, 0) {
            @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(Player player) { return true; }
        };
        var grid = new TransientCraftingContainer(menu, 3, 3);
        for (int slot = 0; slot < 9; slot++) {
            grid.setItem(slot, slot == 4 ? ItemStack.EMPTY : new ItemStack(Items.COBBLESTONE));
        }
        var named = new ItemStack(ModFumos.RAINBOW_PIGMEE_FUMO_ITEM.get(), 2);
        named.setHoverName(Component.literal("Reusable"));
        named.setRepairCost(7);
        grid.setItem(4, named);
        helper.assertTrue(recipe.matches(grid, level), "The catalyst ring must match");
        helper.assertTrue(recipe.assemble(grid, level.registryAccess()).is(ModBlocks.PIGMEE_BUILDING_BLOCK.get().asItem())
                && recipe.assemble(grid, level.registryAccess()).getCount() == 8,
                "The recipe must yield eight blocks");
        var remaining = recipe.getRemainingItems(grid).get(4);
        helper.assertTrue(ItemStack.isSameItemSameTags(remaining, named) && remaining.getCount() == 1,
                "The Pigmee catalyst must retain its custom name and NBT");
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var serializer = ModRecipeTypes.PIGMEE_BUILDING_SERIALIZER.get();
            serializer.toNetwork(buffer, recipe);
            var decoded = serializer.fromNetwork(id, buffer);
            helper.assertTrue(decoded.matches(grid, level), "The recipe must survive network sync");
        } finally {
            buffer.release();
        }
        grid.setItem(4, new ItemStack(ModFumos.CREATIVE_PIGMEE_FUMO_ITEM.get()));
        helper.assertTrue(!recipe.matches(grid, level), "Only Rainbow Pigmee is a catalyst");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void pigmeeBuildingSlabsAndPanelsRegister(GameTestHelper helper) {
        helper.assertTrue(ModBlocks.PIGMEE_BUILDING_PANELS.size() == 16
                && ModBlocks.PIGMEE_FRAMED_BUILDING_PANELS.size() == 16
                && ModBlocks.PIGMEE_BUILDING_SLABS.size() == 16
                && ModBlocks.PIGMEE_FRAMED_BUILDING_SLABS.size() == 16,
                "Each material family must register all sixteen colours");
        for (var color : DyeColor.values()) {
            helper.assertTrue(ModBlocks.PIGMEE_BUILDING_PANELS.get(color).isPresent()
                    && ModBlocks.PIGMEE_FRAMED_BUILDING_PANELS.get(color).isPresent()
                    && ModBlocks.PIGMEE_BUILDING_SLABS.get(color).isPresent()
                    && ModBlocks.PIGMEE_FRAMED_BUILDING_SLABS.get(color).isPresent(),
                    "Missing building material for " + color.getName());
        }
        var pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ModBlocks.PIGMEE_BUILDING_BLOCK.get());
        helper.assertTrue(helper.getBlockState(pos).is(ModBlocks.PIGMEE_BUILDING_BLOCK.get()),
                "Building block must be placeable");
        helper.setBlock(pos, ModBlocks.PIGMEE_BUILDING_SLAB.get());
        helper.assertTrue(helper.getBlockState(pos).is(ModBlocks.PIGMEE_BUILDING_SLAB.get()),
                "Slab must be placeable");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void dyeRecipesLoadSerializeAndReturnCatalyst(GameTestHelper helper) {
        var menu = new AbstractContainerMenu(null, 0) {
            @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(Player player) { return true; }
        };
        var input = new TransientCraftingContainer(menu, 3, 3);
        for (var color : DyeColor.values()) {
            var id = new ResourceLocation(AE2LightningTech.MODID, "rainbow_dye/" + color.getName());
            var recipe = (RainbowPigmeeDyeRecipe) helper.getLevel().getRecipeManager().byKey(id).orElseThrow();
            for (int slot = 0; slot < 9; slot++) {
                var ingredient = recipe.getIngredients().get(slot);
                input.setItem(slot, ingredient.isEmpty() ? ItemStack.EMPTY : ingredient.getItems()[0].copy());
            }
            input.getItem(4).setHoverName(Component.literal("Reusable catalyst"));
            input.getItem(4).setRepairCost(7);
            helper.assertTrue(recipe.matches(input, helper.getLevel()), "Dye layout must match: " + color);
            helper.assertTrue(ItemStack.matches(input.getItem(4), recipe.getRemainingItems(input).get(4)),
                    "Catalyst NBT must survive crafting: " + color);
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                var serializer = ModRecipeTypes.RAINBOW_PIGMEE_DYE_SERIALIZER.get();
                serializer.toNetwork(buffer, recipe);
                var decoded = serializer.fromNetwork(id, buffer);
                helper.assertTrue(decoded.matches(input, helper.getLevel())
                        && ItemStack.matches(recipe.getResultItem(helper.getLevel().registryAccess()),
                                decoded.getResultItem(helper.getLevel().registryAccess())),
                        "Network recipe must preserve layout and result: " + color);
            } finally {
                buffer.release();
            }
        }
        helper.succeed();
    }
}
