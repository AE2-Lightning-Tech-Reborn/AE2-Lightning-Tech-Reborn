package com.moakiee.ae2lt.debug;

import com.moakiee.ae2lt.blockentity.FumoBlockEntity;
import com.moakiee.ae2lt.client.RainbowPigmeeColors;
import com.moakiee.ae2lt.client.ctm.ConnectedTextureBakedModel;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModFumos;
import com.moakiee.ae2lt.registry.ModItems;
import java.util.List;
import java.util.Set;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Opt-in rendering and real JEI validation in its own disposable world. */
@JeiPlugin
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class RainbowPigmeeClientProbe implements IModPlugin {
    private static IJeiRuntime runtime;
    private static int ticks, phase, firstColor;
    private static boolean done;
    private static volatile boolean ready;
    private static volatile Throwable serverFailure;

    @Override public ResourceLocation getPluginUid() { return ResourceLocation.parse("ae2lt:rainbow_qa"); }
    @Override public void onRuntimeAvailable(IJeiRuntime value) { runtime = value; }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("ae2lt.rainbowClientProbe") || done) return;
        var mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.player == null || mc.getSingleplayerServer() == null || ++ticks % 40 != 0) return;
        try {
            if (serverFailure != null) throw new AssertionError(serverFailure);
            switch (phase) {
                case 0 -> {
                    mc.getWindow().setTitle("RAINBOW PIGMEE QA - disposable world");
                    mc.getWindow().setWindowed(1100, 800); mc.resizeDisplay();
                    mc.options.gamma().set(1.0); mc.options.fov().set(55);
                    mc.options.renderDistance().set(6); mc.options.hideGui = true;
                    mc.getSingleplayerServer().execute(() -> { try { fixture(); ready = true; } catch (Throwable t) { serverFailure = t; } });
                }
                case 1 -> {
                    if (!ready || runtime == null || !mc.level.getBlockState(new BlockPos(3,100,9)).is(ModFumos.RAINBOW_PIGMEE_FUMO.get())) {
                        require(ticks < 1600, "Client fixture/JEI timeout"); return;
                    }
                    checkSlabs(); checkJei();
                    firstColor = RainbowPigmeeColors.currentColor();

                }
                case 2 -> {
                    capture("rainbow-pigmee-slabs.png");
                    move(3.5, 101.4, 12.5, 180, 12);
                }
                case 3 -> {
                    require(firstColor != RainbowPigmeeColors.currentColor(), "Rainbow colour is frozen");
                    require(mc.getItemColors().getColor(new ItemStack(ModFumos.RAINBOW_PIGMEE_FUMO_ITEM.get()),0) == RainbowPigmeeColors.currentColor(), "Item tint missing");
                    capture("rainbow-pigmee-close.png");
                    mc.options.hideGui = false;
                    var focus = runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT,
                            VanillaTypes.ITEM_STACK, new ItemStack(ModFumos.RAINBOW_PIGMEE_FUMO_ITEM.get()));
                    var info = runtime.getRecipeManager().createRecipeLookup(RecipeTypes.INFORMATION).limitFocus(List.of(focus)).get().toList();
                    runtime.getRecipesGui().showRecipes(runtime.getRecipeManager().getRecipeCategory(RecipeTypes.INFORMATION), info, List.of(focus));
                }
                case 4 -> {
                    capture("rainbow-pigmee-jei-hint.png");
                    var recipes = runtime.getRecipeManager().createRecipeLookup(RecipeTypes.CRAFTING).get()
                            .filter(h -> h.id().getNamespace().equals("ae2lt") && h.id().getPath().startsWith("rainbow_dye/")).toList();
                    runtime.getRecipesGui().showRecipes(runtime.getRecipeManager().getRecipeCategory(RecipeTypes.CRAFTING), recipes, List.of());
                }
                case 5 -> {
                    capture("rainbow-pigmee-jei-dyes.png");
                    System.out.println("RAINBOW_QA PASS: animated block/item tint; 99 slab states; coplanar CTM and unculled inset faces; all 16 JEI layouts and hint-only acquisition.");
                    done = true; mc.stop();
                }
            }
            phase++;
        } catch (Throwable t) {
            t.printStackTrace(); System.out.println("RAINBOW_QA FAIL phase=" + phase + ": " + t);
            done = true; mc.stop();
        }
    }

    private static void fixture() {
        var mc = Minecraft.getInstance(); var server = mc.getSingleplayerServer(); var level = server.overworld();
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        level.setDayTime(6000); level.setWeatherParameters(6000,0,false,false);
        for (var pos : BlockPos.betweenClosed(-2,99,-2,23,104,13)) level.setBlockAndUpdate(pos, pos.getY()==99 ? Blocks.WHITE_CONCRETE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        for (var color : DyeColor.values()) {
            int x=color.getId();
            for (int row=0;row<3;row++) for (int framed=0;framed<2;framed++) {
                var block=(framed==0?ModBlocks.PIGMEE_BUILDING_SLABS:ModBlocks.PIGMEE_FRAMED_BUILDING_SLABS).get(color).get();
                level.setBlockAndUpdate(new BlockPos(x,100,row*2+framed),block.defaultBlockState().setValue(SlabBlock.TYPE,SlabType.values()[row]));
            }
        }
        level.setBlockAndUpdate(new BlockPos(1,100,9),ModFumos.PIGMEE_FUMO.get().defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(3,100,9),ModFumos.RAINBOW_PIGMEE_FUMO.get().defaultBlockState());
        ((FumoBlockEntity)level.getBlockEntity(new BlockPos(3,100,9))).toggleSpinning();
        level.setBlockAndUpdate(new BlockPos(5,100,9),ModBlocks.PIGMEE_BUILDING_SLAB.get().defaultBlockState());
        var slab=ModBlocks.PIGMEE_FRAMED_BUILDING_SLABS.get(DyeColor.WHITE).get();
        level.setBlockAndUpdate(new BlockPos(20,100,0),slab.defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(21,100,0),slab.defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(20,100,-1),slab.defaultBlockState().setValue(SlabBlock.TYPE,SlabType.TOP));
        var player=server.getPlayerList().getPlayer(mc.player.getUUID()); player.setGameMode(GameType.CREATIVE);
        player.getAbilities().flying = true; player.onUpdateAbilities();
        player.getInventory().clearContent();
        player.getInventory().setItem(0,new ItemStack(ModFumos.RAINBOW_PIGMEE_FUMO_ITEM.get()));
        player.getInventory().setItem(1,new ItemStack(ModItems.DYE_BASE.get(),4));
        player.teleportTo(level,8,109,20,Set.of(),180,35);
    }

    private static void checkSlabs() {
        var mc=Minecraft.getInstance(); var random=RandomSource.create(0);
        var blocks=new java.util.ArrayList<Block>(); blocks.add(ModBlocks.PIGMEE_BUILDING_SLAB.get());
        for(var color:DyeColor.values()) {blocks.add(ModBlocks.PIGMEE_BUILDING_SLABS.get(color).get());blocks.add(ModBlocks.PIGMEE_FRAMED_BUILDING_SLABS.get(color).get());}
        for(var block:blocks) {
            var itemModel = mc.getItemRenderer().getModel(new ItemStack(block), mc.level, mc.player, 0);
            require(!itemModel.getParticleIcon().contents().name().getPath().contains("missing"), "Missing slab item model");
        }
        for(var block:blocks) for(var type:SlabType.values()) {
            var state=block.defaultBlockState().setValue(SlabBlock.TYPE,type);var model=mc.getBlockRenderer().getBlockModel(state);
            require(!model.getParticleIcon().contents().name().getPath().contains("missing"),"Missing slab texture");
            var quads=new java.util.ArrayList<>(model.getQuads(state,null,random,ModelData.EMPTY,null));
            for(var face:Direction.values())quads.addAll(model.getQuads(state,face,random,ModelData.EMPTY,null));
            require(!quads.isEmpty(),"Empty slab model");
            double min=type==SlabType.TOP?.5:0,max=type==SlabType.BOTTOM?.5:1;
            for(var q:quads) {var v=q.getVertices();int stride=v.length/4;for(int k=0;k<4;k++) {float y=Float.intBitsToFloat(v[k*stride+1]);require(y>=min-.0001&&y<=max+.0001,"Slab vertex outside collision shape");}}
            if(model instanceof ConnectedTextureBakedModel && type!=SlabType.DOUBLE) {
                require(model.getQuads(state,type==SlabType.BOTTOM?Direction.UP:Direction.DOWN,random,ModelData.EMPTY,null).isEmpty(),"Inset face incorrectly neighbour-culled");
                require(!model.getQuads(state,null,random,ModelData.EMPTY,null).isEmpty(),"Inset face missing");
            }
        }
        var pos=new BlockPos(20,100,0);var state=mc.level.getBlockState(pos);var model=mc.getBlockRenderer().getBlockModel(state);
        var data=model.getModelData(mc.level,pos,state,ModelData.EMPTY);var conn=data.get(ConnectedTextureBakedModel.CONNECTION);
        require(conn!=null&&Integer.bitCount(conn.edges(Direction.UP))==1&&!conn.culled(Direction.UP),"Different slab heights must not join");
    }

    private static void checkJei() {
        var mc=Minecraft.getInstance();var manager=runtime.getRecipeManager();
        var recipes=manager.createRecipeLookup(RecipeTypes.CRAFTING).get().filter(h->h.id().getNamespace().equals("ae2lt")&&h.id().getPath().startsWith("rainbow_dye/")).toList();
        require(recipes.size()==16,"JEI is missing dyes: "+recipes.size());
        for(var recipe:recipes) require(manager.createRecipeLayoutDrawable(manager.getRecipeCategory(RecipeTypes.CRAFTING),recipe,
                runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).isPresent(),"JEI cannot lay out dye "+recipe.id());
        var focus=runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT,VanillaTypes.ITEM_STACK,new ItemStack(ModFumos.RAINBOW_PIGMEE_FUMO_ITEM.get()));
        var info=manager.createRecipeLookup(RecipeTypes.INFORMATION).limitFocus(List.of(focus)).get().toList();
        require(!info.isEmpty(),"JEI hint missing");
        var text=info.stream().flatMap(i->i.getDescription().stream()).map(net.minecraft.network.chat.FormattedText::getString).collect(java.util.stream.Collectors.joining());
        require(!text.contains("jeb_")&&!text.contains("Jeb_"),"JEI exposes the exact easter egg name");
        require(manager.createRecipeLookup(RecipeTypes.CRAFTING).get().noneMatch(h->h.value().getResultItem(mc.level.registryAccess()).is(ModFumos.RAINBOW_PIGMEE_FUMO_ITEM.get())),"Acquisition must not be a crafting recipe");
    }

    private static void move(double x,double y,double z,float yaw,float pitch) {var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());p.teleportTo(p.serverLevel(),x,y,z,Set.of(),yaw,pitch);});}
    private static void capture(String name) {var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),text->{});}
    private static void require(boolean value,String message) {if(!value)throw new AssertionError(message);}
}
