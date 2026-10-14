package qa;

import java.lang.reflect.*;
import java.util.*;
import appeng.api.config.Actionable;
import appeng.api.crafting.*;
import appeng.api.networking.*;
import appeng.api.networking.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.crafting.CraftingPlan;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import appeng.me.storage.NetworkStorage;
import com.moakiee.ae2lt.compat.gto.GtoCompatibleCraftingCpuLogic;
import com.moakiee.ae2lt.overload.pattern.PatternConversionService;
import com.moakiee.ae2lt.overload.runtime.model.*;
import com.moakiee.ae2lt.overload.runtime.pattern.OverloadPatternSupport;
import com.moakiee.ae2lt.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

@GameTestHolder("ae2lt")
@PrefixGameTestTemplate(false)
public class GtoCpuGameTests {
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty",timeoutTicks=100)
    public static void physicalGtoCpuPreservesOverloadPauseSaveReturnAndNativeFallback(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0,2,0),AEBlocks.CRAFTING_STORAGE_64K.block());
        helper.setBlock(new BlockPos(0,1,0),AEBlocks.CREATIVE_ENERGY_CELL.block());
        var fixture=new Fixture[1];
        helper.runAfterDelay(20,()->{
            try { fixture[0]=new Fixture(helper);fixture[0].startAndReload(); }
            catch(Exception failure){throw new RuntimeException(failure);}
        });
        helper.runAfterDelay(22,()->fixture[0].step());
        helper.runAfterDelay(24,()->{fixture[0].step();fixture[0].finish();helper.succeed();});
    }
    private static final class Fixture {
        final GameTestHelper h;
        final AEItemKey input=AEItemKey.of(Items.IRON_INGOT);
        final AEItemKey expected;
        final AEItemKey actual;
        final IPatternDetails pattern;
        final CraftingCPUCluster cpu;
        final IGrid grid;
        final CraftingService service;
        final NetworkStorage network;
        final Stock stock=new Stock();
        final IGridNode providerNode;
        GtoCompatibleCraftingCpuLogic logic;
        int pushes;
        int returns;
        Fixture(GameTestHelper helper) {
            h=helper;
            var block=(CraftingBlockEntity)helper.getBlockEntity(new BlockPos(0,2,0));
            cpu=block.getCluster();h.assertTrue(cpu!=null && cpu.isActive(),"powered physical CPU formed");
            grid=cpu.getGrid();service=(CraftingService)grid.getCraftingService();
            network=(NetworkStorage)grid.getStorageService().getInventory();
            network.mount(0,stock);stock.counts.add(input,3);
            var template=new ItemStack(Items.DIAMOND);template.setHoverName(Component.literal("expected"));
            var variant=new ItemStack(Items.DIAMOND);variant.setHoverName(Component.literal("actual"));
            expected=AEItemKey.of(template);actual=AEItemKey.of(variant);
            var source=PatternDetailsHelper.encodeProcessingPattern(new GenericStack[]{new GenericStack(input,1)},new GenericStack[]{new GenericStack(expected,1)});
            var parsed=OverloadPatternSupport.toParsedDefinition(source,PatternDetailsHelper.decodePattern(source,helper.getLevel()));
            var encoded=new PatternConversionService().createOverloadPatternStack(ModItems.OVERLOAD_PATTERN.get(),parsed,
                    EncodedOverloadPattern.builder().input(0,MatchMode.STRICT).output(0,MatchMode.ID_ONLY).build());
            pattern=PatternDetailsHelper.decodePattern(encoded,helper.getLevel());
            h.assertTrue(pattern!=null,"overload pattern decoded");
            var provider=new ICraftingProvider(){
                public List<IPatternDetails> getAvailablePatterns(){return List.of(pattern);}
                public boolean isBusy(){return false;}
                public boolean pushPattern(IPatternDetails details,KeyCounter[] inputs){
                    h.assertTrue(inputs.length==1 && inputs[0].get(input)==1,"one physical operation consumes one input");
                    inputs[0].clear();pushes++;return true;
                }
            };
            providerNode=(IGridNode)Proxy.newProxyInstance(IGridNode.class.getClassLoader(),new Class<?>[]{IGridNode.class},(p,m,a)->switch(m.getName()){
                case "getService"->a[0]==ICraftingProvider.class?provider:null;
                case "getOwner"->provider;case "getGrid"->grid;case "getLevel"->helper.getLevel();
                case "isActive","isOnline","isPowered","meetsChannelRequirements"->true;
                case "hashCode"->System.identityHashCode(p);case "equals"->p==a[0];
                case "toString"->"GTO QA provider";default->null;});
            service.addNode(providerNode,null);
            logic=(GtoCompatibleCraftingCpuLogic)cpu.craftingLogic;
        }
        void startAndReload() throws Exception {
            var used=new KeyCounter();used.add(input,3);
            var plan=new CraftingPlan(new GenericStack(expected,3),1,false,false,used,new KeyCounter(),new KeyCounter(),
                    new Object2LongOpenHashMap<>(Map.of(pattern,3L)));
            h.assertTrue(logic.trySubmitJob(grid,plan,IActionSource.empty(),null).successful(),"overload job submitted to physical GTO CPU");
            h.assertTrue(logic.usesLt(),"overload plan selected LT execution");
            logic.tickCraftingLogic(grid.getEnergyService(),service);
            h.assertTrue(pushes==1,"physical CPU respected one-operation budget");
            logic.setPaused(true);
            var save=new CompoundTag();logic.writeToNBT(save);
            h.assertTrue(save.contains("ae2ltGtoOverloadCpu"),"LT job saved under owned key");
            logic=new GtoCompatibleCraftingCpuLogic(cpu);cpu.craftingLogic=logic;logic.readFromNBT(save);
            h.assertTrue(logic.isPaused(),"pause persisted across reload");
            h.assertTrue(logic.getRemainingOutputAmount()==3,"remaining output persisted across reload");
            var linkOwner=appeng.crafting.CraftingLink.class.getDeclaredMethod("getCpu");linkOwner.setAccessible(true);
            h.assertTrue(linkOwner.invoke(logic.getLastLink())==cpu,"crafting link retains physical CPU identity");
            logic.tickCraftingLogic(grid.getEnergyService(),service);
            h.assertTrue(pushes==1,"paused job did not dispatch");
            acceptReturn();logic.setPaused(false);
        }
        void step(){logic.tickCraftingLogic(grid.getEnergyService(),service);h.assertTrue(pushes==returns+1,"next physical operation dispatched: pushes="+pushes+" returns="+returns+" remaining="+logic.getRemainingOutputAmount()+" stored="+logic.getStored(input)+" pending="+logic.getPendingOutputs(expected)+" waiting="+logic.getWaitingFor(expected)+" paused="+logic.isPaused()+" active="+cpu.isActive());acceptReturn();}
        void acceptReturn(){
            long accepted=logic.insert(actual,1,Actionable.MODULATE);
            stock.insert(actual,1-accepted,Actionable.MODULATE,IActionSource.empty());returns++;
            h.assertTrue(logic.getRemainingOutputAmount()==3-returns,"same-ID variant reduced remaining output exactly once");
        }
        void finish(){
            h.assertTrue(!logic.hasJob(),"overload job finished");
            h.assertTrue(stock.counts.get(input)==0 && stock.counts.get(actual)==3,"three inputs consumed and three actual outputs retained");
            var emitted=new KeyCounter();emitted.add(input,1);
            var ordinary=new CraftingPlan(new GenericStack(input,1),1,false,false,new KeyCounter(),emitted,new KeyCounter(),new Object2LongOpenHashMap<>());
            h.assertTrue(logic.trySubmitJob(grid,ordinary,IActionSource.empty(),null).successful(),"ordinary job submitted after overload completion");
            h.assertFalse(logic.usesLt(),"ordinary plan returned to native GTO execution");
            logic.insert(input,1,Actionable.MODULATE);
            h.assertFalse(logic.hasJob(),"native GTO job completed");
            service.removeNode(providerNode);network.unmount(stock);
        }
    }
    private static final class Stock implements MEStorage {
        final KeyCounter counts=new KeyCounter();
        public Component getDescription(){return Component.literal("QA stock");}
        public long insert(AEKey key,long amount,Actionable mode,IActionSource source){if(mode==Actionable.MODULATE)counts.add(key,amount);return amount;}
        public long extract(AEKey key,long amount,Actionable mode,IActionSource source){long got=Math.min(amount,Math.max(0,counts.get(key)));if(mode==Actionable.MODULATE)counts.remove(key,got);return got;}
        public void getAvailableStacks(KeyCounter out){out.addAll(counts);}
    }
}
