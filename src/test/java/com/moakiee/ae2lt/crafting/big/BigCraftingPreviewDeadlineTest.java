package com.moakiee.ae2lt.crafting.big;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BigCraftingPreviewDeadlineTest {
    @BeforeAll static void bootstrap() { com.moakiee.ae2lt.testing.MinecraftTestBootstrap.bootstrap(); }

    @Test
    void ordinaryPreviewRemainsExecutableWithoutWorkerAccessToTheLiveGrid() throws Exception {
        assertTrue(BigCraftingService.preview(grid(),null,output(),BigInteger.ONE,IActionSource.empty())
                .get(5,TimeUnit.SECONDS).executable());
    }

    @Test
    void queueTimeConsumesTheOriginalThreeSecondDeadline() throws Exception {
        withOccupiedWorkers(pool -> {
            long start=System.nanoTime();
            var result=BigCraftingService.preview(grid(),null,output(),BigInteger.ONE,IActionSource.empty());
            assertFalse(result.isDone());
            while(System.nanoTime()-start<TimeUnit.MILLISECONDS.toNanos(3100))
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
            // Hold release is owned by withOccupiedWorkers; run it now so this queued request executes.
            release.get().countDown();
            var failure=assertThrows(Exception.class,()->result.get(2,TimeUnit.SECONDS));
            Throwable root=failure;
            while(root.getCause()!=null) root=root.getCause();
            assertInstanceOf(CancellationException.class,root,"Queueing must not grant the worker a fresh budget or missing-material result");
        });
    }

    @Test
    void cancellingAQueuedPreviewDoesNotStartAnOrderOrReenterTheGrid() throws Exception {
        withOccupiedWorkers(pool -> {
            var result=BigCraftingService.preview(grid(),null,output(),BigInteger.ONE,IActionSource.empty());
            assertTrue(result.cancel(true));
            release.get().countDown();
            try { pool.submit(()->{}).get(2,TimeUnit.SECONDS); }
            catch(Exception e) { throw new AssertionError(e); }
            assertTrue(result.isCancelled());
            assertThrows(CancellationException.class,result::join);
        });
    }

    private static final ThreadLocal<CountDownLatch> release=new ThreadLocal<>();

    private static void withOccupiedWorkers(Consumer<ThreadPoolExecutor> action) throws Exception {
        var field=BigCraftingService.class.getDeclaredField("PLANNERS"); field.setAccessible(true);
        var pool=(ThreadPoolExecutor)field.get(null);
        var entered=new CountDownLatch(2); var unblock=new CountDownLatch(1);
        int previous=pool.getCorePoolSize();
        synchronized(pool) {
            assertEquals(0,pool.getActiveCount(),"Unexpected in-flight test preview");
            assertTrue(pool.getQueue().isEmpty());
            pool.setCorePoolSize(2);
            var blockers=new ArrayList<Future<?>>();
            try {
                for(int i=0;i<2;i++) blockers.add(pool.submit(()->{
                    entered.countDown();
                    try { unblock.await(); } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
                }));
                assertTrue(entered.await(2,TimeUnit.SECONDS),"Test-owned workers did not enter");
                release.set(unblock); action.accept(pool);
            } finally {
                unblock.countDown(); release.remove();
                for(var blocker:blockers) blocker.get(2,TimeUnit.SECONDS);
                pool.setCorePoolSize(previous);
            }
        }
    }

    private static AEKey output() { return AEItemKey.of(Items.GOLD_INGOT); }

    private static IGrid grid() {
        var owner=Thread.currentThread();
        var pattern=new IPatternDetails() {
            public AEItemKey getDefinition() { return AEItemKey.of(Items.PAPER); }
            public IInput[] getInputs() { return new IInput[0]; }
            public GenericStack[] getOutputs() { return new GenericStack[]{new GenericStack(output(),1)}; }
            public boolean supportsPushInputsToExternalInventory() { return false; }
        };
        var storage=new MEStorage() {
            public long insert(AEKey k,long n,appeng.api.config.Actionable a,IActionSource s) { throw new AssertionError("Preview inserted storage"); }
            public long extract(AEKey k,long n,appeng.api.config.Actionable a,IActionSource s) { throw new AssertionError("Empty snapshot cannot extract"); }
            public Component getDescription() { return Component.literal("Preview snapshot"); }
        };
        var storageService=proxy(IStorageService.class,owner,(m,a)->switch(m) {
            case "getInventory" -> storage;
            case "getCachedInventory" -> new KeyCounter();
            default -> null;
        });
        var crafting=proxy(ICraftingService.class,owner,(m,a)->switch(m) {
            case "getCraftables" -> Set.of(output());
            case "getCraftingFor" -> output().equals(a[0]) ? List.of(pattern):List.of();
            default -> null;
        });
        return proxy(IGrid.class,owner,(m,a)->switch(m) {
            case "getStorageService" -> storageService;
            case "getCraftingService" -> crafting;
            case "getActiveMachines" -> Set.of();
            default -> null;
        });
    }

    private interface Call { Object apply(String method,Object[] arguments); }
    private static <T> T proxy(Class<T> type,Thread owner,Call call) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(p,m,a)->{
            assertSame(owner,Thread.currentThread(),"Worker reentered live Grid instead of captured graph");
            var value=call.apply(m.getName(),a);
            if(value!=null) return value;
            if(m.getReturnType()==boolean.class) return false;
            if(m.getReturnType()==long.class) return 0L;
            if(m.getReturnType()==int.class) return 0;
            return null;
        }));
    }
}
