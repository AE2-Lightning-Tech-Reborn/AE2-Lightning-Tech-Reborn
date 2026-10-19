package qa;

import com.moakiee.ae2lt.lightning.LightningTransformRecipe;
import com.moakiee.ae2lt.lightning.LightningTransformService;
import com.moakiee.ae2lt.machine.lightningassembly.LightningAssemblyChamberInventory;
import com.moakiee.ae2lt.machine.lightningassembly.recipe.LightningAssemblyRecipe;
import com.moakiee.ae2lt.machine.lightningassembly.recipe.LightningAssemblyRecipeService;
import com.moakiee.ae2lt.machine.lightningchamber.LightningSimulationChamberInventory;
import com.moakiee.ae2lt.machine.lightningchamber.recipe.LightningSimulationRecipeService;
import com.moakiee.ae2lt.registry.ModRecipeTypes;
import com.moakiee.ae2lt.util.RecipeManagerByTypeAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Uses the actual pack recipes without replacing GTO's shared recipe maps. */
@GameTestHolder("ae2lt")
@PrefixGameTestTemplate(false)
public final class GtoRecipeGameTests {
    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void railgunSingleBatch(GameTestHelper helper) { assembly(helper, "electromagnetic_railgun"); }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void railgunCoreSingleBatch(GameTestHelper helper) { assembly(helper, "railgun_module_core"); }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void railgunBeamSingleBatch(GameTestHelper helper) { assembly(helper, "railgun_module_ehv_beam"); }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void tianshuAmplifierSingleBatch(GameTestHelper helper) { assembly(helper, "tianshu_amplifier_unit"); }

    private static void assembly(GameTestHelper helper, String name) {
        var recipe = LightningAssemblyRecipeService.findRecipeById(helper.getLevel(), id("lightning_assembly/" + name))
                .orElseThrow();
        var inventory = assemblyInputs(recipe);
        var selected = LightningAssemblyRecipeService.findFirstProcessable(helper.getLevel(), inventory,
                Long.MAX_VALUE, Long.MAX_VALUE).orElseThrow();
        helper.assertTrue(selected.recipe().getId().equals(recipe.getId()), "Wrong assembly recipe: " + selected.recipe().getId());
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void storageComponentSingleBatch(GameTestHelper helper) {
        var id = id("lightning_simulation/lightning_storage_component_i");
        var recipe = LightningSimulationRecipeService.findRecipeById(helper.getLevel(), id).orElseThrow();
        var inventory = new LightningSimulationChamberInventory(null);
        for (int i = 0; i < recipe.inputs().size(); i++) {
            var input = recipe.inputs().get(i);
            inventory.setStackInSlot(i, ingredient(input.ingredient(), input.count()));
        }
        var selected = LightningSimulationRecipeService.findFirstProcessable(helper.getLevel(), inventory,
                Long.MAX_VALUE, Long.MAX_VALUE).orElseThrow();
        helper.assertTrue(selected.recipe().getId().equals(id), "Wrong simulation recipe: " + selected.recipe().getId());
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void collectorSingleBatch(GameTestHelper helper) { transform(helper, "lightning_collector", false); }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void simulationRoomSingleBatch(GameTestHelper helper) { transform(helper, "lightning_simulation_room", false); }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void mixedTransformProducesBothOutputs(GameTestHelper helper) { transform(helper, "lightning_simulation_room", true); }

    private static void transform(GameTestHelper helper, String name, boolean mixed) {
        var manager = helper.getLevel().getRecipeManager();
        var type = ModRecipeTypes.LIGHTNING_TRANSFORM_TYPE.get();
        var recipe = RecipeManagerByTypeAccess.findById(manager, type, id("lightning_transform/" + name)).orElseThrow();
        var collector = RecipeManagerByTypeAccess.findById(manager, type, id("lightning_transform/lightning_collector")).orElseThrow();
        Vec3 position = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        addTransformInputs(helper, position, recipe);
        if (mixed) addTransformInputs(helper, position, collector);
        var bolt = EntityType.LIGHTNING_BOLT.create(helper.getLevel());
        if (bolt == null) throw new AssertionError("Cannot create lightning bolt");
        bolt.setPos(position);
        // Call the real transform once; do not add a ticking bolt that can execute again.
        LightningTransformService.handleLightning(helper.getLevel(), bolt);
        var outputs = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(position, position).inflate(3));
        ItemStack expected = recipe.getResultItem(helper.getLevel().registryAccess());
        helper.assertTrue(count(outputs, expected) == expected.getCount(), "Wrong transform output for " + recipe.getId());
        if (mixed) {
            ItemStack second = collector.getResultItem(helper.getLevel().registryAccess());
            helper.assertTrue(count(outputs, second) == second.getCount(), "Mixed inputs lost the collector output");
        }
        helper.assertTrue(outputs.stream().allMatch(entity -> ItemStack.isSameItemSameTags(entity.getItem(), expected)
                || mixed && ItemStack.isSameItemSameTags(entity.getItem(), collector.getResultItem(helper.getLevel().registryAccess()))),
                "Transform left inputs or produced an unrelated recipe");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void overlappingAssemblyUsesMoreSpecificRecipe(GameTestHelper helper) {
        var beam = LightningAssemblyRecipeService.findRecipeById(helper.getLevel(), id("lightning_assembly/railgun_module_ehv_beam"))
                .orElseThrow();
        var core = LightningAssemblyRecipeService.findRecipeById(helper.getLevel(), id("lightning_assembly/railgun_module_core"))
                .orElseThrow();
        var inventory = assemblyInputs(beam);
        int slot = beam.inputs().size();
        for (var input : core.inputs()) {
            inventory.setStackInSlot(slot++, ingredient(input.ingredient(), input.count()));
        }
        var selected = LightningAssemblyRecipeService.findFirstProcessable(helper.getLevel(), inventory,
                Long.MAX_VALUE, Long.MAX_VALUE).orElseThrow();
        helper.assertTrue(selected.recipe().getId().equals(beam.getId()), "Overlapping core recipe stole the beam inputs");
        helper.succeed();
    }

    private static LightningAssemblyChamberInventory assemblyInputs(LightningAssemblyRecipe recipe) {
        var inventory = new LightningAssemblyChamberInventory(null);
        for (int i = 0; i < recipe.inputs().size(); i++) {
            var input = recipe.inputs().get(i);
            inventory.setStackInSlot(i, ingredient(input.ingredient(), input.count()));
        }
        return inventory;
    }

    private static void addTransformInputs(GameTestHelper helper, Vec3 position, LightningTransformRecipe recipe) {
        for (var input : recipe.inputs()) {
            helper.getLevel().addFreshEntity(new ItemEntity(helper.getLevel(), position.x, position.y, position.z,
                    ingredient(input.ingredient(), input.count())));
        }
    }

    private static ItemStack ingredient(Ingredient ingredient, int count) {
        if (ingredient.getItems().length == 0) throw new AssertionError("Recipe ingredient has no resolved items");
        return ingredient.getItems()[0].copyWithCount(count);
    }

    private static int count(java.util.List<ItemEntity> entities, ItemStack expected) {
        return entities.stream().filter(entity -> ItemStack.isSameItemSameTags(entity.getItem(), expected))
                .mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    private static ResourceLocation id(String path) { return new ResourceLocation("ae2lt", path); }
}
