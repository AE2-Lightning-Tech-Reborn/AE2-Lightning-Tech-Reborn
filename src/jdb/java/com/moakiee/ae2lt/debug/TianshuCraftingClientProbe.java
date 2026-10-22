package com.moakiee.ae2lt.debug;

import appeng.api.config.*;
import appeng.api.implementations.menuobjects.IPortableTerminal;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.storage.MEStorage;
import appeng.api.util.IConfigManager;
import appeng.client.gui.style.StyleManager;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AETextField;
import appeng.menu.SlotSemantics;
import appeng.menu.ISubMenu;
import appeng.util.inv.AppEngInternalInventory;
import com.moakiee.ae2lt.client.tianshu.TianshuCraftingTermScreen;
import com.moakiee.ae2lt.integration.ae2wtlib.TianshuEnhancedWirelessCraftingMenu;
import com.moakiee.ae2lt.integration.ae2wtlib.client.TianshuEnhancedWirelessCraftingScreen;
import com.moakiee.ae2lt.logic.tianshu.terminal.*;
import com.moakiee.ae2lt.menu.TianshuCraftingTermMenu;
import com.moakiee.ae2lt.registry.ModItems;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/** Opens and renders every new screen against the installed AE2 and WTLib resources. */
@EventBusSubscriber(modid="ae2lt",value=Dist.CLIENT)
public final class TianshuCraftingClientProbe {
    private static int ticks,stage;
    private static boolean done;
    private static java.util.concurrent.CompletableFuture<Void> preparation;
    private static Screen expectedScreen;
    private static TianshuCraftingTermMenu wired;
    private static TianshuEnhancedWirelessCraftingMenu wireless;
    private static TianshuEnhancedWirelessCraftingScreen wirelessScreen;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || done || !Boolean.getBoolean("ae2lt.tianshuCraftingProbe"))return;
        var mc=Minecraft.getInstance();mc.options.pauseOnLostFocus=false;
        if(mc.player==null)return;
        try {
            if(preparation==null) {
                var server=mc.getSingleplayerServer();
                var playerId=mc.player.getUUID();
                preparation=server.submit(()->{
                    var player=server.getPlayerList().getPlayer(playerId);
                    if(player.isDeadOrDying())player=server.getPlayerList().respawn(player,false);
                    player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                    player.setHealth(player.getMaxHealth());player.setInvulnerable(true);
                    player.getAbilities().flying=true;player.onUpdateAbilities();
                    player.teleportTo(server.overworld(),.5,200,.5,java.util.Set.of(),0,0);
                });
            }
            if(!preparation.isDone())return;
            preparation.join();
            if(++ticks<100 || ticks%12!=0)return;
            if(mc.player.isDeadOrDying())throw new AssertionError("Client fixture player died before rendering stage "+stage);
            if(stage>0) {
                if(mc.screen!=expectedScreen)throw new AssertionError("Screen replaced at stage "+stage+": "+mc.screen);
                Screenshot.grab(mc.gameDirectory,"tianshu-stage-"+(stage-1)+".png",mc.getMainRenderTarget(),ignored->{});
            }
            if(stage==0) {
                var host=new Host();host.setLevel(mc.level);
                wired=new TianshuCraftingTermMenu(60,mc.player.getInventory(),host);
                mc.player.containerMenu=wired;
                verifyCraftingRightClicks(mc,wired,"/screens/terminals/tianshu_crafting_terminal.json");
            }
            if(stage<5) {
                wired.workPage=TianshuWorkPage.values()[stage];wired.updateSlotAccess();
                if(stage==3)wired.getStonecutter().getSlot(0).set(new ItemStack(Items.STONE));
                if(stage==4) {
                    wired.getSlots(com.moakiee.ae2lt.menu.Ae2ltSlotSemantics.TIANSHU_CELL).get(0).set(appeng.core.definitions.AEItems.ITEM_CELL_1K.stack());
                    wired.cellConfigSize=63;wired.cellUpgradeSize=2;
                }
                mc.setScreen(new TianshuCraftingTermScreen<>(wired,mc.player.getInventory(),Component.literal("Tianshu QA"),
                        StyleManager.loadStyleDoc("/screens/terminals/tianshu_crafting_terminal.json")));
            } else if(stage<10) {
                if(stage==5) {
                    var stack=new ItemStack(ModItems.TIANSHU_WIRELESS_CRAFTING_TERMINAL.get());
                    mc.player.getInventory().setItem(0,stack);
                    var host=new TianshuWirelessCraftingTermMenuHost(mc.player,0,stack,(p,m)->{});
                    wireless=new TianshuEnhancedWirelessCraftingMenu(61,mc.player.getInventory(),host);
                    mc.player.containerMenu=wireless;
                    verifyCraftingRightClicks(mc,wireless,"/screens/wireless_tianshu_enhanced_crafting_terminal.json");
                }
                wireless.workPage=TianshuWorkPage.values()[stage-5];wireless.updateSlotAccess();
                if(stage==8)wireless.getStonecutter().getSlot(0).set(new ItemStack(Items.STONE));
                if(stage==9) {
                    wireless.getSlots(com.moakiee.ae2lt.menu.Ae2ltSlotSemantics.TIANSHU_CELL).get(0).set(appeng.core.definitions.AEItems.ITEM_CELL_1K.stack());
                    wireless.cellConfigSize=63;wireless.cellUpgradeSize=2;
                }
                wirelessScreen=new TianshuEnhancedWirelessCraftingScreen(wireless,mc.player.getInventory(),Component.literal("Wireless Tianshu QA"),
                        StyleManager.loadStyleDoc("/screens/wireless_tianshu_enhanced_crafting_terminal.json"));
                mc.setScreen(wirelessScreen);
            } else if(stage==10 || stage==11) {
                var type=Class.forName("com.moakiee.ae2lt.integration.ae2wtlib.client.TianshuWirelessToolsScreen");
                var ctor=type.getDeclaredConstructor(TianshuEnhancedWirelessCraftingScreen.class,TianshuEnhancedWirelessCraftingMenu.WirelessPage.class);
                ctor.setAccessible(true);
                mc.setScreen((Screen)ctor.newInstance(wirelessScreen,stage==10?TianshuEnhancedWirelessCraftingMenu.WirelessPage.SETTINGS:TianshuEnhancedWirelessCraftingMenu.WirelessPage.TRASH));
            } else if(stage==12) {
                // The ordinary wireless style must resolve even though WTLib selects the enhanced screen.
                StyleManager.loadStyleDoc("/screens/wireless_tianshu_crafting_terminal.json");
                var type=Class.forName("com.moakiee.ae2lt.integration.ae2wtlib.client.TianshuMagnetScreen");
                var ctor=type.getDeclaredConstructor(TianshuEnhancedWirelessCraftingScreen.class);ctor.setAccessible(true);
                mc.setScreen((Screen)ctor.newInstance(wirelessScreen));
            } else {
                if(!(boolean)Class.forName("dev.emi.emi.runtime.EmiReloadManager").getMethod("isLoaded").invoke(null)) {
                    if(ticks>2400)throw new AssertionError("EMI reload did not finish");
                    return;
                }
                var recipes=dev.emi.emi.api.EmiApi.getRecipeManager();
                var factory=com.moakiee.ae2lt.registry.ModBlocks.OVERLOAD_PROCESSING_FACTORY.get().asItem();
                var viewerAudit=recipes.getCategories().stream()
                        .filter(category->category.getId().toString().contains("advanced") || category.getName().getString().contains("Reaction"))
                        .map(category->category.getId()+" name="+category.getName().getString()+" recipes="+recipes.getRecipes(category).size()
                                +" workstations="+recipes.getWorkstations(category).stream().flatMap(ingredient->ingredient.getEmiStacks().stream())
                                .map(stack->net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItemStack().getItem()).toString()).toList())
                        .toList().toString();
                Files.writeString(Path.of("tianshu-viewer-audit.txt"),viewerAudit);
                var reactionCategories=recipes.getCategories().stream()
                        .filter(category->category.getId().equals(new ResourceLocation("advanced_ae", "reaction_chamber"))).toList();
                if(reactionCategories.size()!=1)throw new AssertionError("Expected exactly one AdvancedAE reaction category: "+viewerAudit);
                var reactionRecipes=recipes.getRecipes(reactionCategories.get(0));
                if(reactionRecipes.stream().map(recipe->recipe.getId()).distinct().count()!=reactionRecipes.size())
                    throw new AssertionError("Duplicate AdvancedAE reaction recipes: "+viewerAudit);
                boolean linked=recipes.getCategories().stream()
                        .filter(category->category.getId().getNamespace().equals("advanced_ae"))
                        .anyMatch(category->recipes.getWorkstations(category).stream().flatMap(ingredient->ingredient.getEmiStacks().stream())
                                .anyMatch(stack->stack.getItemStack().is(factory)) && !recipes.getRecipes(category).isEmpty()
                                && !category.getName().getString().startsWith("emi.category."));
                if(!linked)throw new AssertionError("AdvancedAE reaction recipes have no overload-factory workstation: "+viewerAudit);
                Files.writeString(Path.of("tianshu-client-result.txt"),"PASS: wired and compact wireless crafting-grid left/right mouse dispatch, hidden/disabled/active anvil hit tests; five wired pages, five enhanced wireless pages, settings, trash, magnet, ordinary wireless style and AdvancedAE factory EMI recipes/workstation.\n");
                done=true;mc.player.containerMenu=mc.player.inventoryMenu;mc.setScreen(null);mc.stop();return;
            }
            expectedScreen=mc.screen;
            stage++;
        } catch(Throwable error) {
            error.printStackTrace();done=true;
            try {Files.writeString(Path.of("tianshu-client-result.txt"),"FAIL stage "+stage+": "+error);}catch(Exception ignored){}
            mc.player.containerMenu=mc.player.inventoryMenu;mc.setScreen(null);mc.stop();
        }
    }
    private static void verifyCraftingRightClicks(Minecraft mc,TianshuCraftingTermMenu menu,String style) throws Exception {
        menu.workPage=TianshuWorkPage.CRAFTING;
        menu.updateSlotAccess();
        var screen=new ClickProbeScreen(menu,mc.player.getInventory(),StyleManager.loadStyleDoc(style));
        mc.setScreen(screen);
        screen.prepare();
        var editorField=TianshuCraftingTermScreen.class.getDeclaredField("anvilName");
        editorField.setAccessible(true);
        var editor=(AETextField)editorField.get(screen);
        if(editor.visible || editor.active || editor.isMouseOver(editor.getX()+1,editor.getY()+1))
            throw new AssertionError("Hidden anvil editor must not participate in crafting hit tests");
        int overlaps=0;
        var grid=menu.getSlots(SlotSemantics.CRAFTING_GRID);
        for(var slot:grid) {
            slot.set(new ItemStack(Items.COBBLESTONE,4));
            double x=screen.slotX(slot)+8,y=screen.slotY(slot)+8;
            int overlapLeft=Math.max(screen.slotX(slot),editor.getX());
            int overlapTop=Math.max(screen.slotY(slot),editor.getY());
            int overlapRight=Math.min(screen.slotX(slot)+16,editor.getX()+editor.getWidth());
            int overlapBottom=Math.min(screen.slotY(slot)+16,editor.getY()+editor.getHeight());
            if(overlapLeft<overlapRight && overlapTop<overlapBottom) {
                overlaps++;
                x=(overlapLeft+overlapRight)/2.0;
                y=(overlapTop+overlapBottom)/2.0;
            }
            for(int button:new int[]{1,0}) {
                screen.clickedSlot=null;
                screen.mouseClicked(x,y,button);
                screen.mouseReleased(x,y,button);
                if(screen.clickedSlot!=slot || screen.clickedButton!=button)
                    throw new AssertionError("Crafting slot "+slot.index+" changed mouse button "+button+" to "+screen.clickedButton);
            }
            slot.set(ItemStack.EMPTY);
        }
        if(overlaps==0)throw new AssertionError("Fixture must exercise the hidden editor's overlapping grid slots");
        menu.workPage=TianshuWorkPage.ANVIL;
        screen.prepare();
        if(!editor.visible || editor.active || editor.isMouseOver(editor.getX()+1,editor.getY()+1))
            throw new AssertionError("Empty anvil editor must be visible and disabled");
        menu.getAnvil().getSlot(0).set(new ItemStack(Items.IRON_SWORD));
        screen.prepare();
        if(!editor.active || !editor.isMouseOver(editor.getX()+1,editor.getY()+1))
            throw new AssertionError("Active anvil editor must retain normal hit tests");
        menu.getAnvil().getSlot(0).set(ItemStack.EMPTY);
        menu.workPage=TianshuWorkPage.CRAFTING;
        menu.updateSlotAccess();
        Files.writeString(Path.of("tianshu-right-click-"+(menu.hasCompactWorkArea()?"wireless":"wired")+".txt"),
                "PASS: "+grid.size()+" grid slots, both mouse buttons, "+overlaps+" overlapping slots; hidden, disabled and active anvil editor hit tests.\n");
    }
    private static final class ClickProbeScreen extends TianshuCraftingTermScreen<TianshuCraftingTermMenu> {
        Slot clickedSlot;
        int clickedButton=-1;
        ClickProbeScreen(TianshuCraftingTermMenu menu,Inventory inventory,ScreenStyle style) {
            super(menu,inventory,Component.literal("Right-click regression"),style);
        }
        void prepare(){updateBeforeRender();}
        int slotX(Slot slot){return leftPos+slot.x;}
        int slotY(Slot slot){return topPos+slot.y;}
        @Override protected void slotClicked(Slot slot,int index,int button,ClickType type) {
            clickedSlot=slot;
            clickedButton=button;
        }
    }
    private static final class Host extends BlockEntity implements TianshuCraftingTerminalHost,IPortableTerminal {
        private final InternalInventory grid=new AppEngInternalInventory(null,9);
        private final TianshuWorkstationStorage workstations=new TianshuWorkstationStorage(data->{});
        private final appeng.util.ConfigManager config=new appeng.util.ConfigManager(()->{});
        Host(){super(BlockEntityType.CHEST,BlockPos.ZERO,Blocks.CHEST.defaultBlockState());
            config.registerSetting(Settings.SORT_BY,SortOrder.NAME);config.registerSetting(Settings.SORT_DIRECTION,SortDir.ASCENDING);
            config.registerSetting(Settings.VIEW_MODE,ViewItems.ALL);config.registerSetting(Settings.TYPE_FILTER,TypeFilter.ALL);}
        @Override public InternalInventory getSubInventory(ResourceLocation id){return grid;}
        @Override public TianshuWorkstationStorage getWorkstationStorage(){return workstations;}
        @Override public MEStorage getInventory(){return null;}
        @Override public IGridNode getActionableNode(){return null;}
        @Override public IConfigManager getConfigManager(){return config;}
        @Override public void returnToMainMenu(Player player,ISubMenu menu){}
        @Override public ItemStack getMainMenuIcon(){return new ItemStack(Items.CRAFTING_TABLE);}
        @Override public double extractAEPower(double amount,Actionable mode,PowerMultiplier multiplier){return amount;}
    }
}
