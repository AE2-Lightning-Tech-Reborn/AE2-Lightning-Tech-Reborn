package com.moakiee.ae2lt.debug;

import appeng.api.config.*;
import appeng.api.implementations.menuobjects.IPortableTerminal;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.storage.MEStorage;
import appeng.api.util.IConfigManager;
import appeng.client.gui.style.StyleManager;
import appeng.menu.ISubMenu;
import appeng.util.inv.AppEngInternalInventory;
import com.moakiee.ae2lt.client.tianshu.TianshuCraftingTermScreen;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/** Verifies that wired screens stay usable without optional wireless/viewer mods. */
@EventBusSubscriber(modid="ae2lt",value=Dist.CLIENT)
public final class TianshuWiredClientProbe {
    private static int ticks,stage;
    private static boolean done;
    private static java.util.concurrent.CompletableFuture<Void> preparation;
    private static Screen expectedScreen;
    private static TianshuCraftingTermMenu menu;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || done || !Boolean.getBoolean("ae2lt.tianshuWiredProbe"))return;
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
            if(stage==0) {
                for(var mod:java.util.List.of("ae2wtlib","jei","emi"))
                    if(net.minecraftforge.fml.ModList.get().isLoaded(mod))throw new AssertionError("Optional mod still loaded: "+mod);
                if(ModItems.TIANSHU_WIRELESS_CRAFTING_TERMINAL.isPresent())throw new AssertionError("Wireless item registered without WTLib");
                var host=new Host();host.setLevel(mc.level);
                menu=new TianshuCraftingTermMenu(62,mc.player.getInventory(),host);
                mc.player.containerMenu=menu;
            }
            if(stage>0) {
                if(mc.screen!=expectedScreen)throw new AssertionError("Screen replaced at stage "+stage+": "+mc.screen);
                Screenshot.grab(mc.gameDirectory,"wired-stage-"+(stage-1)+".png",mc.getMainRenderTarget(),ignored->{});
            }
            if(stage==5) {
                Files.writeString(Path.of("tianshu-wired-client-result.txt"),"PASS: all five wired pages render with WTLib, JEI and EMI absent; wireless item is not registered.\n");
                done=true;mc.player.containerMenu=mc.player.inventoryMenu;mc.setScreen(null);mc.stop();return;
            }
            menu.workPage=TianshuWorkPage.values()[stage];menu.updateSlotAccess();
            if(stage==3)menu.getStonecutter().getSlot(0).set(new ItemStack(Items.STONE));
            if(stage==4) {
                menu.getSlots(com.moakiee.ae2lt.menu.Ae2ltSlotSemantics.TIANSHU_CELL).get(0).set(appeng.core.definitions.AEItems.ITEM_CELL_1K.stack());
                menu.cellConfigSize=63;menu.cellUpgradeSize=2;
            }
            mc.setScreen(new TianshuCraftingTermScreen<>(menu,mc.player.getInventory(),Component.literal("Wired Tianshu QA"),
                    StyleManager.loadStyleDoc("/screens/terminals/tianshu_crafting_terminal.json")));
            expectedScreen=mc.screen;
            stage++;
        } catch(Throwable error) {
            error.printStackTrace();done=true;
            try {Files.writeString(Path.of("tianshu-wired-client-result.txt"),"FAIL stage "+stage+": "+error);}catch(Exception ignored){}
            mc.player.containerMenu=mc.player.inventoryMenu;mc.setScreen(null);mc.stop();
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
