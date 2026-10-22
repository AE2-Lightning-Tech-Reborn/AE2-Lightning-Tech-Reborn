package com.moakiee.ae2lt.debug;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEItems;
import com.moakiee.ae2lt.integration.ae2wtlib.Ae2wtlibIntegration;
import com.moakiee.ae2lt.integration.ae2wtlib.TianshuTerminalMerge;
import com.moakiee.ae2lt.integration.ae2wtlib.TianshuEnhancedWirelessCraftingMenu;
import com.moakiee.ae2lt.logic.tianshu.terminal.OmniversalPatternDraft;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWirelessCraftingTermMenuHost;
import com.moakiee.ae2lt.menu.OverloadAlloyAnvilMenu;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;
import com.mojang.authlib.GameProfile;
import de.mari_023.ae2wtlib.AE2wtlib;
import de.mari_023.ae2wtlib.AE2wtlibSlotSemantics;
import de.mari_023.ae2wtlib.wut.recipe.Common;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.AnvilRepairEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Exercises native item/menu persistence and callbacks in the Forge runtime. */
@GameTestHolder("ae2lt")
@PrefixGameTestTemplate(false)
public final class TianshuMainPortGameTests {
    @GameTest(template = "workstation_test")
    public static void mergePreservesEnergyInventoriesAndRejectsConflicts(GameTestHelper helper) {
        var target = new ItemStack(AE2wtlib.UNIVERSAL_TERMINAL);
        var source = new ItemStack(ModItems.TIANSHU_WIRELESS_CRAFTING_TERMINAL.get());
        target.getOrCreateTag().putDouble("internalCurrentPower", 250);
        source.getOrCreateTag().putDouble("internalCurrentPower", 150);
        var inventory = new appeng.util.inv.AppEngInternalInventory(null, 9);
        inventory.setItemDirect(0, new ItemStack(Items.DIAMOND, 5));
        inventory.writeToNBT(source.getOrCreateTag(), "craftingGrid");
        var originalTarget = target.copy();var originalSource = source.copy();
        var result = Common.mergeTerminal(target, source, Ae2wtlibIntegration.TIANSHU_CRAFTING_NAME);
        check(!result.isEmpty(), "native merge must use Tianshu adapter");
        check(result.getTag().getDouble("internalCurrentPower") == 400, "both energy balances retained");
        var restored = new appeng.util.inv.AppEngInternalInventory(null, 9);
        restored.readFromNBT(result.getTag(), "craftingGrid");
        check(restored.getStackInSlot(0).getCount() == 5, "physical grid survives merge");
        check(ItemStack.matches(target, originalTarget) && ItemStack.matches(source, originalSource), "merge is pure");
        var conflicting = target.copy();inventory.writeToNBT(conflicting.getOrCreateTag(), "craftingGrid");
        check(TianshuTerminalMerge.merge(conflicting, source, Ae2wtlibIntegration.TIANSHU_CRAFTING_NAME).isEmpty(),
                "even identical physical inventories must not overwrite one another");
        var emptyWork = new CompoundTag();
        var emptyInputs = new net.minecraft.nbt.ListTag();
        var consumedTool = new ItemStack(Items.DIAMOND_SWORD);
        consumedTool.setCount(0);
        emptyInputs.add(consumedTool.save(new CompoundTag()));
        emptyWork.put("inputs", emptyInputs);
        target.getOrCreateTag().put("ae2lt:tianshu_workstations", emptyWork.copy());
        var occupiedWork = emptyWork.copy();
        var occupiedInputs = new net.minecraft.nbt.ListTag();
        occupiedInputs.add(new ItemStack(Items.IRON_INGOT, 3).save(new CompoundTag()));
        occupiedWork.put("inputs", occupiedInputs);
        source.getOrCreateTag().put("ae2lt:tianshu_workstations", occupiedWork.copy());
        var workResult = TianshuTerminalMerge.merge(target, source, Ae2wtlibIntegration.TIANSHU_CRAFTING_NAME);
        check(!workResult.isEmpty() && workResult.getTag().getCompound("ae2lt:tianshu_workstations").equals(occupiedWork),
                "zero-count tagged stacks do not prevent preserving incoming workstation inputs");
        target.getOrCreateTag().put("ae2lt:tianshu_workstations", occupiedWork.copy());
        check(TianshuTerminalMerge.merge(target, source, Ae2wtlibIntegration.TIANSHU_CRAFTING_NAME).isEmpty(),
                "two occupied workstations reject merge");
        target.getOrCreateTag().remove("ae2lt:tianshu_workstations");
        source.getOrCreateTag().remove("ae2lt:tianshu_workstations");
        source.getOrCreateTag().putString("customLink", "other-network");
        target.getOrCreateTag().putString("customLink", "first-network");
        check(TianshuTerminalMerge.merge(target, source, Ae2wtlibIntegration.TIANSHU_CRAFTING_NAME).isEmpty(), "conflicting links reject merge");
        helper.succeed();
    }

    @GameTest(template = "workstation_test")
    public static void nativeMergeRecipesRejectInventoryLossInBothOrders(GameTestHelper helper) {
        var crafting = AEItems.WIRELESS_CRAFTING_TERMINAL.stack();
        var tianshu = new ItemStack(ModItems.TIANSHU_WIRELESS_CRAFTING_TERMINAL.get());
        var recipe = helper.getLevel().getRecipeManager().byKey(new net.minecraft.resources.ResourceLocation(
                "ae2lt", "wireless_universal_terminal/combine_tianshu_crafting_with_crafting")).orElseThrow();
        for (boolean reverse : new boolean[]{false,true}) {
            var input = ForgeCraftingInput.of(2,2,List.of(reverse ? tianshu : crafting, reverse ? crafting : tianshu, ItemStack.EMPTY,ItemStack.EMPTY));
            @SuppressWarnings("unchecked") var nativeRecipe = (net.minecraft.world.item.crafting.Recipe<net.minecraft.world.inventory.CraftingContainer>) recipe;
            check(nativeRecipe.matches(input,helper.getLevel()), "empty terminals combine in either order");
            var result=nativeRecipe.assemble(input,helper.getLevel().registryAccess());
            check(!result.isEmpty() && de.mari_023.ae2wtlib.wut.WUTHandler.hasTerminal(result, Ae2wtlibIntegration.TIANSHU_CRAFTING_NAME),"recipe keeps terminal registration");
        }
        var inventory = new appeng.util.inv.AppEngInternalInventory(null,9);
        inventory.setItemDirect(0,new ItemStack(Items.DIAMOND));
        inventory.writeToNBT(crafting.getOrCreateTag(),"craftingGrid");
        inventory.writeToNBT(tianshu.getOrCreateTag(),"craftingGrid");
        var input=ForgeCraftingInput.of(2,2,List.of(crafting,tianshu,ItemStack.EMPTY,ItemStack.EMPTY));
        @SuppressWarnings("unchecked") var nativeRecipe=(net.minecraft.world.item.crafting.Recipe<net.minecraft.world.inventory.CraftingContainer>)recipe;
        check(!nativeRecipe.matches(input,helper.getLevel()),"recipe matcher rejects physical collision before crafting");
        helper.succeed();
    }

    @GameTest(template = "workstation_test")
    public static void wirelessSubpagesGateHiddenSlotsAndClearTrash(GameTestHelper helper) {
        var player=FakePlayerFactory.get(helper.getLevel(),new GameProfile(UUID.randomUUID(),"TianshuPages"));
        player.getInventory().clearContent();
        var terminal=new ItemStack(ModItems.TIANSHU_WIRELESS_CRAFTING_TERMINAL.get());
        player.getInventory().setItem(0,terminal);
        var host=new TianshuWirelessCraftingTermMenuHost(player,0,terminal,(p,m)->{});
        var menu=new TianshuEnhancedWirelessCraftingMenu(31,player.getInventory(),host);
        player.containerMenu=menu;
        try {
            var trash=menu.getSlots(AE2wtlibSlotSemantics.TRASH).get(0);
            menu.setCarried(new ItemStack(Items.DIAMOND,3));
            menu.clicked(trash.index,0,ClickType.PICKUP,player);
            check(!trash.hasItem() && menu.getCarried().getCount()==3,"hidden trash rejects incoming click");
            menu.setWirelessPage(TianshuEnhancedWirelessCraftingMenu.WirelessPage.TRASH);
            menu.clicked(trash.index,0,ClickType.PICKUP,player);
            check(trash.getItem().getCount()==3 && menu.getCarried().isEmpty(),"active trash uses real slots");
            menu.setWirelessPage(TianshuEnhancedWirelessCraftingMenu.WirelessPage.MAIN);
            check(!trash.hasItem(),"leaving trash destroys its contents exactly once");
        } finally {menu.removed(player);player.containerMenu=player.inventoryMenu;player.getInventory().clearContent();}
        helper.succeed();
    }

    @GameTest(template = "workstation_test")
    public static void omniversalDraftCopiesSparseNbtAndPacketRoundTrips(GameTestHelper helper) {
        var input=new GenericStack(AEItemKey.of(Items.IRON_INGOT),128);
        var output=new GenericStack(AEItemKey.of(Items.GOLD_INGOT),64);
        var draft=OmniversalPatternDraft.empty().withSlots(java.util.Arrays.asList(input,null),List.of(output),List.of(new ItemStack(Items.DIAMOND)));
        check(draft.inputs().get(0).amount()==128 && draft.outputs().get(0).amount()==64,"AE2 15 sparse NBT retains amounts");
        var exposed=draft.pattern();exposed.setTag(new CompoundTag());
        check(draft.inputs().get(0).amount()==128,"draft does not expose mutable pattern");
        check(OmniversalPatternDraft.read(draft.write()).equals(draft),"draft NBT round trip");
        var buffer=new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {draft.writeToPacket(buffer);check(new OmniversalPatternDraft(buffer).equals(draft),"draft packet round trip");}
        finally {buffer.release();}
        helper.succeed();
    }

    @GameTest(template = "workstation_test")
    public static void alloyAnvilKeepsNativeXpAndCallbackWithoutWear(GameTestHelper helper) {
        var player=FakePlayerFactory.get(helper.getLevel(),new GameProfile(UUID.randomUUID(),"AlloyAnvilForge"));
        player.setGameMode(GameType.SURVIVAL);player.experienceLevel=100;
        var pos=helper.absolutePos(new BlockPos(2,2,2));int[] calls={0};
        Consumer<AnvilRepairEvent> listener=event->{if(event.getEntity()==player){calls[0]++;event.setBreakChance(1);}};
        MinecraftForge.EVENT_BUS.addListener(listener);
        try {
            for(boolean alloy:new boolean[]{true,false}) {
                var block=alloy?ModBlocks.OVERLOAD_ALLOY_ANVIL.get():Blocks.ANVIL;
                helper.getLevel().setBlockAndUpdate(pos,block.defaultBlockState());
                var menu=(AnvilMenu)block.defaultBlockState().getMenuProvider(helper.getLevel(),pos).createMenu(32,player.getInventory(),player);
                check((menu instanceof OverloadAlloyAnvilMenu)==alloy,"native block creates correct menu");
                menu.getSlot(0).set(new ItemStack(Items.DIAMOND_SWORD));menu.setItemName("Forge backport");
                int cost=menu.getCost(),before=player.experienceLevel;
                check(cost>0 && menu.getSlot(2).hasItem(),"native rename preview");
                menu.getSlot(2).onTake(player,menu.getSlot(2).remove(1));
                check(player.experienceLevel==before-cost && !menu.getSlot(0).hasItem(),"XP and inputs consumed by native callback");
                check(helper.getLevel().getBlockState(pos).is(alloy?ModBlocks.OVERLOAD_ALLOY_ANVIL.get():Blocks.CHIPPED_ANVIL),"wear protection applies only to alloy");
                menu.removed(player);
            }
            check(calls[0]==2,"Forge repair event preserved for both anvils");
        } finally {MinecraftForge.EVENT_BUS.unregister(listener);}
        helper.succeed();
    }

    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
