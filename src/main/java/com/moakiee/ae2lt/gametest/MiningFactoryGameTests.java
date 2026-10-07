package com.moakiee.ae2lt.gametest;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.StorageCells;
import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.MiningFactoryBlockEntity;
import com.moakiee.ae2lt.machine.miningfactory.MiningFactoryInventory;
import com.moakiee.ae2lt.machine.miningfactory.MiningLoot;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;
import com.moakiee.ae2lt.registry.ModMenuTypes;
import com.moakiee.ae2lt.menu.MiningFactoryMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AE2LightningTech.MODID)
@PrefixGameTestTemplate(false)
public final class MiningFactoryGameTests {
    private static final BlockPos POSITION = new BlockPos(1, 1, 1);

    private MiningFactoryGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void registrationAndExhaustedToolProtection(GameTestHelper helper) {
        helper.assertTrue(ModMenuTypes.MINING_FACTORY.get() == MiningFactoryMenu.TYPE,
                "mining factory menu must register");
        helper.assertTrue(helper.getLevel().getRecipeManager()
                .byKey(new ResourceLocation(AE2LightningTech.MODID, "mining_factory")).isPresent(),
                "mining factory recipe must load");
        helper.setBlock(POSITION, ModBlocks.MINING_FACTORY.get());
        var factory = (MiningFactoryBlockEntity) helper.getBlockEntity(POSITION);
        var broken = new ItemStack(Items.DIAMOND_PICKAXE);
        broken.setDamageValue(broken.getMaxDamage());
        helper.assertTrue(!MiningLoot.isTool(broken)
                && !factory.getInventory().isItemValid(MiningFactoryInventory.TOOL, broken),
                "exhausted tool must be rejected");
        var result = MiningLoot.roll(helper.getLevel(), helper.absolutePos(POSITION),
                Blocks.IRON_ORE.defaultBlockState(), broken, 64, 8);
        helper.assertTrue(result.processed() == 0 && result.drops().isEmpty(),
                "exhausted tool must not mine");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void actualGridPaysOneLightningForBatch(GameTestHelper helper) {
        helper.setBlock(POSITION, ModBlocks.MINING_FACTORY.get());
        var factory = (MiningFactoryBlockEntity) helper.getBlockEntity(POSITION);
        helper.setBlock(POSITION.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(POSITION.west(), AEBlocks.DRIVE.block());
        var cellStack = new ItemStack(ModItems.LIGHTNING_STORAGE_COMPONENT_I.get());
        var cell = StorageCells.getCellInventory(cellStack, null);
        helper.assertTrue(cell != null, "lightning cell must exist");
        helper.assertTrue(cell.insert(LightningKey.HIGH_VOLTAGE, 1, Actionable.MODULATE,
                IActionSource.ofMachine(factory)) == 1, "lightning cell must accept HV");
        cell.persist();
        ((appeng.blockentity.storage.DriveBlockEntity) helper.getBlockEntity(POSITION.west()))
                .getInternalInventory().setItemDirect(0, cellStack);
        factory.getInventory().setStackInSlot(MiningFactoryInventory.INPUT, new ItemStack(Items.IRON_ORE, 64));
        factory.getInventory().setStackInSlot(MiningFactoryInventory.TOOL, new ItemStack(Items.DIAMOND_PICKAXE));
        factory.getInventory().setStackInSlot(MiningFactoryInventory.MATRIX,
                new ItemStack(ModItems.LIGHTNING_COLLAPSE_MATRIX.get(), 1));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(factory.getAvailableLightning() == 1,
                        "waiting for actual AE2 drive to connect"))
                .thenExecute(() -> factory.getEnergyStorage().receiveEnergy(256 * 256, false))
                .thenExecuteAfter(7, () -> {
                    int output = 0;
                    for (int slot = MiningFactoryInventory.OUTPUT;
                            slot < MiningFactoryInventory.OUTPUT + MiningFactoryInventory.OUTPUT_COUNT; slot++) {
                        var stack = factory.getInventory().getStackInSlot(slot);
                        if (stack.is(Items.RAW_IRON)) {
                            output += stack.getCount();
                        }
                    }
                    helper.assertTrue(output == 64 && factory.getAvailableLightning() == 0,
                            "batch must conserve inputs and spend exactly one HV");
                    helper.assertTrue(factory.getEnergyStorage().getEnergyStored() == 256 * 256 - 64 * 256,
                            "batch must spend FE for only processed blocks");
                }).thenSucceed();
    }
}
