package qa;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import appeng.api.config.Actionable;
import appeng.api.crafting.*;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.*;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.block.TianshuSupercomputerControllerBlock;
import com.moakiee.ae2lt.blockentity.*;
import com.moakiee.ae2lt.compat.neoeco.NeoEcoCraftingCompat;
import com.moakiee.ae2lt.logic.tianshu.*;
import com.moakiee.ae2lt.registry.*;
import com.moakiee.thunderbolt.core.storage.big.BigStorageOps;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.common.Mod;

/** Real eight-host F9 network, physical FX lanes, coolant, LT CPU and infinite storage. */
@Mod.EventBusSubscriber(modid="gto_compat_qa")
public final class GtoFxQA {
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    private static final BlockPos CPU=new BlockPos(40,100,64), DRIVE=new BlockPos(62,102,65);
    private static final BigInteger N=BigInteger.ONE.shiftLeft(100).add(BigInteger.valueOf(37));
    private static final AEItemKey RAW=AEItemKey.of(Items.OAK_LOG), OUTPUT=AEItemKey.of(Items.OAK_PLANKS);
    private static final List<Object> hosts=new ArrayList<>();
    private static int ticks, phase, age, settled;
    private static boolean done;
    private static boolean chunksReady;
    private static final List<CompletableFuture<?>> chunks=new ArrayList<>();
    private static ICraftingProvider provider;
    private static UUID warmup;
    private static CompletableFuture<?> planning;
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static Object call(Object obj,String name,Object...args) throws ReflectiveOperationException {
        var method=Arrays.stream(obj.getClass().getMethods()).filter(m->m.getName().equals(name)&&m.getParameterCount()==args.length).findFirst().orElseThrow();
        return method.invoke(obj,args);
    }
    private static BlockState state(String id,Direction facing){
        var key=new ResourceLocation("neoecoae",id);require(BuiltInRegistries.BLOCK.containsKey(key),"missing real block "+key);
        var state=BuiltInRegistries.BLOCK.get(key).defaultBlockState();
        if(state.hasProperty(BlockStateProperties.HORIZONTAL_FACING))state=state.setValue(BlockStateProperties.HORIZONTAL_FACING,facing);
        return state;
    }
    private static BlockPos origin(int i){return new BlockPos(64,100,64+i*6);}
    private static void next(){phase++;age=0;}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||done||!"fx".equals(System.getProperty("ae2lt.extendedQa")))return;
        var level=e.getServer().overworld();
        try{
            appeng.hooks.ticking.TickHandler.instance().playerJoined=true;
            require(++ticks<1400,"FX fixture timed out phase="+phase);
            if(ticks==1){
                for(int cx=1;cx<=5;cx++)for(int cz=3;cz<=7;cz++){
                    var pos=new net.minecraft.world.level.ChunkPos(cx,cz);
                    level.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.FORCED,pos,2,pos);
                    chunks.add(level.getChunkSource().getChunkFuture(cx,cz,net.minecraft.world.level.chunk.ChunkStatus.FULL,true));
                }
                return;
            }
            if(!chunksReady){
                if(chunks.stream().anyMatch(f->!f.isDone()))return;
                chunks.forEach(CompletableFuture::join);chunksReady=true;
                for(int cx=1;cx<=5;cx++)for(int cz=3;cz<=7;cz++)level.setChunkForced(cx,cz,true);
            }
            age++;
            switch(phase){
                case 0->{
                    buildCpu(level);
                    level.setBlockAndUpdate(DRIVE,AEBlocks.DRIVE.block().defaultBlockState());
                    level.setBlockAndUpdate(DRIVE.below(),AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
                    ((DriveBlockEntity)level.getBlockEntity(DRIVE)).getInternalInventory().setItemDirect(0,new ItemStack(ModItems.INFINITE_STORAGE_CELL.get()));
                    for(int i=0;i<8;i++)buildHost(level,origin(i));
                    next();
                }
                case 1->{
                    if(age<40)return;
                    var cpu=(TianshuSupercomputerControllerBlockEntity)level.getBlockEntity(CPU);cpu.scanNow();
                    require(cpu.isFormed(),"real multidimensional CPU did not form: "+cpu.issueText());
                    var drive=(DriveBlockEntity)level.getBlockEntity(DRIVE);var root=drive.getMainNode().getNode();
                    var port=(TianshuSupercomputerPortBlockEntity)level.getBlockEntity(cpu.getPortPos());
                    GridHelper.createConnection(root,port.getMainNode().getNode());
                    for(int i=0;i<8;i++){
                        var host=level.getBlockEntity(origin(i).offset(1,2,0));call(host,"rebuildMultiblock");
                        require(Boolean.TRUE.equals(call(host,"isFormed")),"F9 host did not form index="+i);
                        call(host,"setNetworkFrequency",1);hosts.add(host);
                        var bus=level.getBlockEntity(origin(i).offset(3,3,1));
                        GridHelper.createConnection(root,((IManagedGridNode)call(bus,"getMainNode")).getNode());
                    }
                    next();
                }
                case 2->{
                    if(age<40)return;
                    var host=hosts.get(0);var cluster=call(host,"getCluster");var network=call(cluster,"getNetworkCluster");
                    require(network!=null&&((List<?>)call(network,"getMembers")).size()==8,"real switches did not form eight-host domain");
                    var bus=level.getBlockEntity(origin(0).offset(3,3,1));provider=(ICraftingProvider)bus;
                    ((InternalInventory)call(bus,"getPatternSlotInventory")).setItemDirect(0,encoded(level));
                    next();
                }
                case 3->{
                    if(age<10)return;
                    require(provider.getAvailablePatterns().size()==1,"FX pattern was not published");
                    warmup=UUID.randomUUID();var input=new KeyCounter[]{new KeyCounter()};input[0].add(RAW,1);
                    require(NeoEcoCraftingCompat.pushForJob(provider,provider.getAvailablePatterns().get(0),input,warmup),"real worker rejected verification warmup");
                    next();
                }
                case 4->{
                    if(age<40)return;
                    NeoEcoCraftingCompat.cancel(level,warmup);
                    next();
                }
                case 5->{
                    if(age<20)return;
                    var cpu=(TianshuSupercomputerControllerBlockEntity)level.getBlockEntity(CPU);var inv=cpu.getGrid().getStorageService().getInventory();
                    require(BigStorageOps.extract(inv,OUTPUT,BigInteger.valueOf(4),Actionable.MODULATE,IActionSource.empty()).equals(BigInteger.valueOf(4)),"warmup did not physically produce four planks");
                    var fluid=BuiltInRegistries.FLUID.get(new ResourceLocation("neoecoae:cryotheum_solution"));
                    require(fluid!=net.minecraft.world.level.material.Fluids.EMPTY,"actual tier-nine coolant absent");
                    for(int i=0;i<8;i++){
                        var hatch=level.getBlockEntity(origin(i).offset(0,3,1));
                        var tank=(IFluidHandler)hatch.getClass().getField("tank").get(hatch);
                        require(tank.fill(new FluidStack(fluid,16000),IFluidHandler.FluidAction.EXECUTE)==16000,"coolant hatch fill rejected");
                    }
                    var network=call(call(hosts.get(0),"getCluster"),"getNetworkCluster");
                    call(network,"setOverclocked",true);call(network,"setActiveCooling",true);
                    next();
                }
                case 6->{
                    if(age<20)return;
                    var cpu=(TianshuSupercomputerControllerBlockEntity)level.getBlockEntity(CPU);var inv=cpu.getGrid().getStorageService().getInventory();
                    var snapshot=call(hosts.get(0),"getCapabilitySnapshot");
                    require(Boolean.TRUE.equals(call(snapshot,"virtualMode")),"actual eight F9 hosts did not enable virtual FX: "+snapshot);
                    require(((Number)call(snapshot,"physicalFxCount")).intValue()==88,"actual FX topology count differs: "+snapshot);
                    require(BigStorageOps.insert(inv,RAW,N,Actionable.MODULATE,IActionSource.empty()).equals(N),"actual infinite cell rejected exact inputs");
                    planning=cpu.bigCrafting().preview(OUTPUT,N.multiply(BigInteger.valueOf(4)),IActionSource.empty());
                    LOG.info("GTO_EXTENDED_QA FX_TOPOLOGY {}",snapshot);next();
                }
                case 7->{
                    if(!planning.isDone())return;
                    var cpu=(TianshuSupercomputerControllerBlockEntity)level.getBlockEntity(CPU);var service=cpu.bigCrafting();
                    @SuppressWarnings("unchecked") var plan=(com.moakiee.thunderbolt.core.crafting.big.BigCraftingPlanner.Result<AEKey>)planning.join();
                    require(service.submit(OUTPUT,N.multiply(BigInteger.valueOf(4)),plan,IActionSource.empty()),"actual LT exact CPU rejected FX plan");
                    service.tick();
                    require(service.job()!=null&&service.job().acceptedCopies.equals(N),"physical FX narrowed or split the exact admitted task: "+(service.job()==null?"finished":service.job().acceptedCopies));
                    require(service.job().waiting.get(OUTPUT).equals(N.multiply(BigInteger.valueOf(4))),"exact waiting ownership missing");
                    LOG.info("GTO_EXTENDED_QA FX_ACCEPTED craftId={} exactCopies={} waiting={}",service.job().id,service.job().acceptedCopies,service.job().waiting);
                    next();
                }
                case 8->{
                    var cpu=(TianshuSupercomputerControllerBlockEntity)level.getBlockEntity(CPU);
                    if(cpu.bigCrafting().busy()){settled=0;return;}
                    if(++settled<80)return;
                    var inv=cpu.getGrid().getStorageService().getInventory();var total=N.multiply(BigInteger.valueOf(4));
                    require(BigStorageOps.extract(inv,OUTPUT,total.add(BigInteger.ONE),Actionable.SIMULATE,IActionSource.empty()).equals(total),"real FX exact outputs lost or duplicated");
                    require(BigStorageOps.extract(inv,RAW,N,Actionable.SIMULATE,IActionSource.empty()).signum()==0,"FX refunded accepted input");
                    for(int i=0;i<8;i++)for(int x=3;x<=13;x++)require(((List<?>)call(level.getBlockEntity(origin(i).offset(x,2,0)),"getThreadSnapshots")).isEmpty(),"physical FX lane retains completed job");
                    LOG.info("GTO_EXTENDED_QA PASS realFX hosts=8 physicalFx=88 exactCopies={} exactOutput={} originalRaw=0 duplicateReceipts=0",N,total);done=true;
                }
                default->throw new AssertionError("Unknown FX phase "+phase);
            }
        }catch(Throwable failure){done=true;LOG.error("GTO_EXTENDED_QA FAIL physical FX phase="+phase,failure);}
    }
    private static ItemStack encoded(ServerLevel level){
        var recipe=(CraftingRecipe)level.getRecipeManager().byKey(new ResourceLocation("ae2lt_qa:oak_planks")).orElseThrow();
        var slots=new ItemStack[9];Arrays.fill(slots,ItemStack.EMPTY);slots[0]=new ItemStack(Items.OAK_LOG);
        return PatternDetailsHelper.encodeCraftingPattern(recipe,slots,new ItemStack(Items.OAK_PLANKS,4),false,false);
    }
    private static void buildHost(ServerLevel level,BlockPos o){
        // Local Y=1..3 matches the upstream multiblock calculator's controller-relative surface.
        for(var p:BlockPos.betweenClosed(o.offset(0,1,0),o.offset(14,3,1)))level.setBlockAndUpdate(p,state("crafting_casing",Direction.NORTH));
        level.setBlockAndUpdate(o.offset(1,2,0),state("crafting_system_l9",Direction.NORTH));
        // The switch shares the controller's interface side; the other side starts the FX row.
        level.setBlockAndUpdate(o.offset(0,2,0),state("crafting_high_energy_network_switch",Direction.NORTH));
        level.setBlockAndUpdate(o.offset(0,2,1),state("crafting_interface",Direction.NORTH));
        level.setBlockAndUpdate(o.offset(0,3,1),state("input_hatch",Direction.NORTH));
        level.setBlockAndUpdate(o.offset(0,1,1),state("output_hatch",Direction.NORTH));
        for(int x=3;x<=13;x++){
            level.setBlockAndUpdate(o.offset(x,2,0),state("crafting_worker",Direction.NORTH));
            for(int y:new int[]{1,3}){
                level.setBlockAndUpdate(o.offset(x,y,0),state("crafting_parallel_core_l9",Direction.NORTH));
                level.setBlockAndUpdate(o.offset(x,y,1),state("crafting_pattern_bus",Direction.SOUTH));
            }
            level.setBlockAndUpdate(o.offset(x,2,1),state("crafting_vent",Direction.SOUTH));
        }
    }
    private static void buildCpu(ServerLevel level){
        for(int x=0;x<7;x++)for(int y=0;y<7;y++)for(int z=0;z<7;z++){
            var local=new BlockPos(x,y,z);var state=switch(TianshuMultiblockTemplate.roleAt(local)){
                case CASING->ModBlocks.TIANSHU_SUPERCOMPUTER_CASING.get().defaultBlockState();
                case COOLING->ModBlocks.PHASE_CHANGE_COOLING_UNIT.get().defaultBlockState();
                case GLASS->ModBlocks.TIANSHU_SUPERCOMPUTER_GLASS.get().defaultBlockState();
                case CONTROLLER->ModBlocks.TIANSHU_SUPERCOMPUTER_CONTROLLER.get().defaultBlockState().setValue(TianshuSupercomputerControllerBlock.FACING,Direction.EAST);
                case PORT_CANDIDATE->(local.equals(TianshuMultiblockTemplate.LOWER_PORT)?ModBlocks.TIANSHU_SUPERCOMPUTER_PORT.get():ModBlocks.PHASE_CHANGE_COOLING_UNIT.get()).defaultBlockState();
                case CORE_RESERVED->(local.equals(new BlockPos(3,3,3))?ModBlocks.MULTIDIMENSIONAL_SUPERCOMPUTING_UNIT.get():ModBlocks.TIANSHU_BLANK_UNIT.get()).defaultBlockState();
                case IGNORED->Blocks.AIR.defaultBlockState();
            };
            level.setBlockAndUpdate(TianshuMultiblockScanner.worldPos(CPU,local,Direction.EAST),state);
        }
    }
}
