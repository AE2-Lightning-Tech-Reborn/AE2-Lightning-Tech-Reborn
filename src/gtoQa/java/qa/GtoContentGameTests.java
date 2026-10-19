package qa;

import appeng.recipes.handlers.InscriberRecipe;
import com.moakiee.ae2lt.machine.lightningassembly.LightningAssemblyChamberInventory;
import com.moakiee.ae2lt.machine.lightningchamber.LargeStackItemHandler;
import com.moakiee.ae2lt.machine.lightningchamber.LightningSimulationChamberInventory;
import com.moakiee.ae2lt.util.LargeStackStreamCodecs;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2lt")
@PrefixGameTestTemplate(false)
public final class GtoContentGameTests {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 400)
    public static void loadedLtRecipesAreComplete(GameTestHelper helper) {
        var invalid = new ArrayList<String>();
        int count = 0;
        int dynamic = 0;
        for (var recipe : helper.getLevel().getRecipeManager().getRecipes()) {
            if (!recipe.getId().getNamespace().equals("ae2lt")) continue;
            count++;
            // AE2 includes intentional empty top/bottom slots in its display list.
            // Validate the mandatory middle and every declared optional ingredient.
            if (recipe instanceof InscriberRecipe inscriber) {
                if (inscriber.getMiddleInput().getItems().length == 0
                        || List.of(inscriber.getTopOptional(), inscriber.getBottomOptional()).stream()
                                .anyMatch(ingredient -> ingredient != Ingredient.EMPTY && ingredient.getItems().length == 0)) {
                    invalid.add(recipe.getId().toString());
                }
                continue;
            }
            // Special crafting recipes can define their matching entirely in code.
            // Their default empty display-ingredient list is not an unresolved tag.
            if (recipe.isSpecial() && recipe.getIngredients().isEmpty()) {
                dynamic++;
                continue;
            }
            if (recipe.isIncomplete()) invalid.add(recipe.getId().toString());
        }
        helper.assertTrue(count > 0, "No LT recipes loaded");
        helper.assertTrue(invalid.isEmpty(), "Incomplete LT recipes: " + invalid);
        LOGGER.info("GTO_CONTENT_QA loaded LT recipes={} dynamic ingredient views={}", count, dynamic);
        helper.succeed();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 400)
    public static void allLtRecipeNetworkCodecsRoundTrip(GameTestHelper helper) {
        var failures = new ArrayList<String>();
        int count = 0;
        for (var recipe : helper.getLevel().getRecipeManager().getRecipes()) {
            if (!recipe.getId().getNamespace().equals("ae2lt")) continue;
            var original = new FriendlyByteBuf(Unpooled.buffer());
            var encodedAgain = new FriendlyByteBuf(Unpooled.buffer());
            try {
                RecipeSerializer serializer = recipe.getSerializer();
                serializer.toNetwork(original, recipe);
                byte[] expected = new byte[original.readableBytes()];
                original.getBytes(original.readerIndex(), expected);
                Recipe decoded = serializer.fromNetwork(recipe.getId(), original);
                if (decoded == null || original.isReadable()) throw new AssertionError("Incomplete decode");
                ItemStack before = recipe.getResultItem(helper.getLevel().registryAccess());
                ItemStack after = decoded.getResultItem(helper.getLevel().registryAccess());
                if (before.getCount() != after.getCount() || !ItemStack.isSameItemSameTags(before, after)) {
                    throw new AssertionError("Result item/count/NBT changed");
                }
                serializer.toNetwork(encodedAgain, decoded);
                byte[] actual = new byte[encodedAgain.readableBytes()];
                encodedAgain.getBytes(encodedAgain.readerIndex(), actual);
                if (!Arrays.equals(expected, actual)) throw new AssertionError("Recipe wire data changed");
                count++;
            } catch (Exception | AssertionError failure) {
                failures.add(recipe.getId() + ": " + failure);
            } finally {
                original.release();
                encodedAgain.release();
            }
        }
        helper.assertTrue(count > 0, "No LT recipe codecs tested");
        helper.assertTrue(failures.isEmpty(), "LT recipe network round-trip failures: " + failures);
        LOGGER.info("GTO_CONTENT_QA recipe codec round-trips={}", count);
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void machineInventoriesPreserveLargeNamedStacksAcrossSave(GameTestHelper helper) {
        List<LargeStackItemHandler> inventories = List.of(new LightningAssemblyChamberInventory(null),
                new LightningSimulationChamberInventory(null));
        for (var inventory : inventories) {
            var expected = new ItemStack(Items.DIAMOND, 8192);
            expected.setHoverName(Component.literal("Large stack audit"));
            inventory.setItemDirect(0, expected);
            var saved = new CompoundTag();
            inventory.saveToTag(saved, "items");
            inventory.clear();
            inventory.loadFromTag(saved, "items");
            helper.assertTrue(inventory.getStackInSlot(0).getCount() == 8192, "Saved stack count changed");
            helper.assertTrue(ItemStack.isSameItemSameTags(inventory.getStackInSlot(0), expected), "Saved stack NBT changed");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void largeRecipeStacksKeepCountAndNbtAcrossNetwork(GameTestHelper helper) {
        for (int count : new int[] {1, 127, 128, 8192, Integer.MAX_VALUE}) {
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                var expected = new ItemStack(Items.DIAMOND, count);
                expected.setHoverName(Component.literal("Network count boundary"));
                LargeStackStreamCodecs.writeItemStack(buffer, expected);
                ItemStack actual = LargeStackStreamCodecs.readItemStack(buffer);
                helper.assertTrue(actual.getCount() == count, "Item stack count truncated: " + count);
                helper.assertTrue(ItemStack.isSameItemSameTags(expected, actual), "Item stack NBT changed");
                helper.assertTrue(!buffer.isReadable(), "Item stack codec left unread data");
            } finally {
                buffer.release();
            }
        }
        helper.succeed();
    }
}
