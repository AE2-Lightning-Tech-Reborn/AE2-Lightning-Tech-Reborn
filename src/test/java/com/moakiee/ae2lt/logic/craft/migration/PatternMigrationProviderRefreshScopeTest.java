package com.moakiee.ae2lt.logic.craft.migration;

import static org.junit.jupiter.api.Assertions.*;
import appeng.api.networking.crafting.ICraftingProvider;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PatternMigrationProviderRefreshScopeTest {
    @AfterEach void clearRefreshes() { PatternMigrationProviderRefreshScope.clear(); }

    @Test void tickEndRefreshCoalescesEachProviderWithoutDroppingOthers() {
        var first=provider();var second=provider();var calls=new ArrayList<String>();
        PatternMigrationProviderRefreshScope.enqueue(first,()->fail("obsolete refresh"));
        PatternMigrationProviderRefreshScope.enqueue(second,()->calls.add("second"));
        PatternMigrationProviderRefreshScope.enqueue(first,()->calls.add("first"));
        assertTrue(calls.isEmpty());
        PatternMigrationProviderRefreshScope.drain();
        assertEquals(2,calls.size());assertTrue(calls.containsAll(List.of("first","second")));
        PatternMigrationProviderRefreshScope.drain();assertEquals(2,calls.size());
    }

    @Test void refreshQueuedDuringDrainRunsOnTheNextTick() {
        var provider=provider();var calls=new AtomicInteger();
        PatternMigrationProviderRefreshScope.enqueue(provider,()->{
            calls.incrementAndGet();
            PatternMigrationProviderRefreshScope.enqueue(provider,calls::incrementAndGet);
        });
        PatternMigrationProviderRefreshScope.drain();assertEquals(1,calls.get());
        PatternMigrationProviderRefreshScope.drain();assertEquals(2,calls.get());
    }

    @Test void serverStopDiscardsPendingRefreshes() {
        PatternMigrationProviderRefreshScope.enqueue(provider(),()->fail("refresh leaked across server stop"));
        PatternMigrationProviderRefreshScope.clear();
        PatternMigrationProviderRefreshScope.drain();
    }

    @Test void wholeUnmountCompactsDuplicatesAndPreservesOtherMediumOrder() {
        var removed=provider();var first=provider();var last=provider();
        var list=new ArrayList<ICraftingProvider>();list.add(first);
        for(int i=0;i<100000;i++)list.add(removed);
        list.add(last);var resets=new AtomicInteger();
        PatternMigrationProviderRefreshScope.refresh(removed,()->{
            for(int i=0;i<100000;i++)assertTrue(PatternMigrationProviderRefreshScope.remove(
                    list,removed,list,resets::incrementAndGet));
        });
        assertEquals(List.of(first,last),list);assertEquals(1,resets.get());
        assertFalse(PatternMigrationProviderRefreshScope.remove(list,removed,list,resets::incrementAndGet));
    }

    @Test void unrelatedProvidersAndCustomListsUseTheOriginalRemoval() {
        var removed=provider();var other=provider();var list=new ArrayList<>(List.of(removed,other));
        PatternMigrationProviderRefreshScope.refresh(removed,()->{
            assertFalse(PatternMigrationProviderRefreshScope.remove(list,other,list,()->fail("unexpected reset")));
            assertFalse(PatternMigrationProviderRefreshScope.remove(new Object(),removed,List.of(removed),()->fail("unexpected reset")));
        });
        assertEquals(List.of(removed,other),list);
    }

    @Test void nestedRefreshAndExceptionsRestoreTheOuterContext() {
        var outer=provider();var inner=provider();var list=new ArrayList<>(List.of(outer,inner));
        PatternMigrationProviderRefreshScope.refresh(outer,()->{
            assertThrows(IllegalStateException.class,()->PatternMigrationProviderRefreshScope.refresh(inner,()->{
                assertTrue(PatternMigrationProviderRefreshScope.remove(list,inner,list,()->{}));
                throw new IllegalStateException("fixture");
            }));
            assertTrue(PatternMigrationProviderRefreshScope.remove(list,outer,list,()->{}));
        });
        assertTrue(list.isEmpty());
        assertFalse(PatternMigrationProviderRefreshScope.remove(list,outer,list,()->fail("leaked context")));
    }

    private static ICraftingProvider provider() {
        return (ICraftingProvider)Proxy.newProxyInstance(ICraftingProvider.class.getClassLoader(),
                new Class<?>[]{ICraftingProvider.class},(p,m,a)->switch(m.getName()) {
                    case "equals"->p==a[0];case "hashCode"->System.identityHashCode(p);default->null;
                });
    }
}
