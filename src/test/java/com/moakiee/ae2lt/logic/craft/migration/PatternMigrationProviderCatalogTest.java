package com.moakiee.ae2lt.logic.craft.migration;

import static org.junit.jupiter.api.Assertions.*;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.util.inv.AppEngInternalInventory;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PatternMigrationProviderCatalogTest {
    @BeforeAll static void bootstrap() { PatternMigrationTest.bootstrap(); }

    @Test void hundredThousandRowsKeepIdentityAndInputsAcrossSeparateRemovals() {
        var f = new Fixture(100000);
        var wood = AEItemKey.of(Items.OAK_LOG);
        for (int i = 0; i < 100000; i++) f.add(i, new ItemStack(Items.PAPER), i==0||i==99999?wood:f.iron, i==0||i==99999);
        f.finish();
        var first = f.catalog.capture(f.inventory, 0);
        f.inventory.setItemDirect(0, ItemStack.EMPTY);
        assertTrue(f.apply(first));
        assertEquals(99999, f.patterns.size());
        assertTrue(f.inputs.contains(wood));
        var last = f.catalog.capture(f.inventory, 99999);
        f.inventory.setItemDirect(99999, ItemStack.EMPTY);
        assertTrue(f.apply(last));
        assertEquals(99998, f.patterns.size());
        assertEquals(Set.of(f.iron), f.inputs);
        assertEquals(2, f.catalog.updates());
    }

    @Test void sameBatchRemovesDuplicateDefinitionsAndMiddleRowsInPhysicalOrder() {
        var f = new Fixture(8);
        for (int i=0;i<8;i++) f.add(i,new ItemStack(Items.PAPER),f.iron,true);
        f.finish();
        var kept = List.of(f.patterns.get(0),f.patterns.get(2),f.patterns.get(4),f.patterns.get(5),f.patterns.get(7));
        var changes = new ArrayList<PatternMigrationProviderCatalog.Change>();
        for (int slot : new int[]{6,1,3}) {
            changes.add(f.catalog.capture(f.inventory,slot));
            f.inventory.setItemDirect(slot,ItemStack.EMPTY);
        }
        assertTrue(f.catalog.apply(f.inventory,f.patterns,f.inputs,changes));
        assertEquals(kept,f.patterns);
        var next=f.catalog.capture(f.inventory,5);
        f.inventory.setItemDirect(5,ItemStack.EMPTY);
        assertTrue(f.apply(next));
        assertEquals(4,f.patterns.size());
        assertTrue(f.inputs.contains(f.iron));
    }

    @Test void partialStackRemovalDoesNotRemoveItsRegisteredPattern() {
        var f = new Fixture(1); f.add(0,new ItemStack(Items.PAPER,12),f.iron,true); f.finish();
        var change=f.catalog.capture(f.inventory,0);
        f.inventory.getStackInSlot(0).shrink(1);
        assertTrue(f.apply(change));
        assertEquals(1,f.patterns.size());
        assertEquals(11,f.inventory.getStackInSlot(0).getCount());
        var removal=f.catalog.capture(f.inventory,0);
        f.inventory.setItemDirect(0,ItemStack.EMPTY);
        assertTrue(f.apply(removal));
        assertTrue(f.patterns.isEmpty()); assertTrue(f.inputs.isEmpty());
    }

    @Test void rollbackRebindsCopiedStackWithoutLosingItsCatalogEntry() {
        var f = new Fixture(1); f.add(0,new ItemStack(Items.PAPER),f.iron,true); f.finish();
        var change=f.catalog.capture(f.inventory,0);
        f.inventory.setItemDirect(0,ItemStack.EMPTY);
        f.inventory.setItemDirect(0,change.before().copy());
        assertTrue(f.apply(change)); assertEquals(1,f.patterns.size());
        var next=f.catalog.capture(f.inventory,0);
        f.inventory.setItemDirect(0,ItemStack.EMPTY);
        assertTrue(f.apply(next)); assertTrue(f.patterns.isEmpty());
    }

    @Test void replacementRejectsWholeBatchBeforeAnyCatalogMutation() {
        var f = new Fixture(2);
        f.add(0,new ItemStack(Items.PAPER),f.iron,true);f.add(1,new ItemStack(Items.PAPER),f.iron,true);f.finish();
        var a=f.catalog.capture(f.inventory,0);var b=f.catalog.capture(f.inventory,1);
        f.inventory.setItemDirect(0,ItemStack.EMPTY);f.inventory.setItemDirect(1,new ItemStack(Items.STONE));
        assertFalse(f.catalog.apply(f.inventory,f.patterns,f.inputs,List.of(a,b)));
        assertEquals(2,f.patterns.size()); assertTrue(f.inputs.contains(f.iron));
    }

    @Test void staleCatalogIdentityAndChangedInventoryRequireNativeRefresh() {
        var f = new Fixture(1);f.add(0,new ItemStack(Items.PAPER),f.iron,true);f.finish();
        var change=f.catalog.capture(f.inventory,0);f.inventory.setItemDirect(0,ItemStack.EMPTY);
        assertFalse(f.catalog.apply(new AppEngInternalInventory(1),f.patterns,f.inputs,List.of(change)));
        f.patterns.set(0,details(f.iron,true));
        assertFalse(f.apply(change)); assertEquals(1,f.patterns.size());
    }

    @Test void unregisteredAndSharedStackRowsCannotUseIncrementalRemoval() {
        var f = new Fixture(2);var stack=new ItemStack(Items.PAPER);
        f.add(0,stack,f.iron,true);f.add(1,stack,f.iron,true);f.finish();
        var change=f.catalog.capture(f.inventory,0);f.inventory.setItemDirect(0,ItemStack.EMPTY);
        assertFalse(f.apply(change)); assertEquals(2,f.patterns.size());
        var empty=new Fixture(1);empty.inventory.setItemDirect(0,new ItemStack(Items.PAPER));empty.finish();
        var unregistered=empty.catalog.capture(empty.inventory,0);empty.inventory.setItemDirect(0,ItemStack.EMPTY);
        assertFalse(empty.apply(unregistered));
    }

    @Test void missingBlockingInputFallsBackWithoutRemovingPatterns() {
        var f = new Fixture(1);f.add(0,new ItemStack(Items.PAPER),f.iron,true);f.finish();
        var change=f.catalog.capture(f.inventory,0);f.inventory.setItemDirect(0,ItemStack.EMPTY);f.inputs.clear();
        assertFalse(f.apply(change));assertEquals(1,f.patterns.size());
    }

    private static IPatternDetails details(AEKey input,boolean crafting) {
        var ingredient=(IPatternDetails.IInput)Proxy.newProxyInstance(IPatternDetails.IInput.class.getClassLoader(),
                new Class<?>[]{IPatternDetails.IInput.class},(p,m,a)->m.getName().equals("getPossibleInputs")
                        ?new GenericStack[]{new GenericStack(input,1)}:null);
        return (IPatternDetails)Proxy.newProxyInstance(IPatternDetails.class.getClassLoader(),crafting
                ?new Class<?>[]{IPatternDetails.class,IMolecularAssemblerSupportedPattern.class}:new Class<?>[]{IPatternDetails.class},
                (p,m,a)->switch(m.getName()) {
                    case "getInputs"->new IPatternDetails.IInput[]{ingredient};
                    case "equals"->p==a[0];case "hashCode"->System.identityHashCode(p);
                    default->null;
                });
    }

    private static final class Fixture {
        final AppEngInternalInventory inventory;
        final PatternMigrationProviderCatalog catalog;
        final List<IPatternDetails> patterns=new ArrayList<>();
        final Set<AEKey> inputs=new HashSet<>();
        final AEKey iron=AEItemKey.of(Items.IRON_INGOT);
        Fixture(int size) { inventory=new AppEngInternalInventory(size);catalog=new PatternMigrationProviderCatalog(inventory); }
        void add(int slot,ItemStack stack,AEKey input,boolean crafting) {
            inventory.setItemDirect(slot,stack);var details=details(input,crafting);patterns.add(details);
            inputs.add(input.dropSecondary());catalog.decoded(stack,details);
        }
        void finish() { catalog.finish(patterns,inputs); }
        boolean apply(PatternMigrationProviderCatalog.Change change) { return catalog.apply(inventory,patterns,inputs,List.of(change)); }
    }
}
