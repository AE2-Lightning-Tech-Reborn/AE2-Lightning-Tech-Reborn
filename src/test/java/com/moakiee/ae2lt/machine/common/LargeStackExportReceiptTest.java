package com.moakiee.ae2lt.machine.common;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import com.moakiee.ae2lt.machine.lightningassembly.LightningAssemblyChamberInventory;
import com.moakiee.ae2lt.machine.lightningchamber.LargeStackItemHandler;
import com.moakiee.ae2lt.machine.lightningchamber.LightningSimulationChamberInventory;
import com.moakiee.ae2lt.machine.miningfactory.MiningFactoryInventory;
import com.moakiee.ae2lt.machine.overloadfactory.OverloadProcessingFactoryInventory;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class LargeStackExportReceiptTest {
    @BeforeAll
    static void bootstrap() {
        if (LoadingModList.get() == null) LoadingModList.of(List.of(), List.of(),
                new net.minecraftforge.fml.loading.EarlyLoadingException("test bootstrap", null, List.of()));
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    record Layout(Function<Runnable, LargeStackItemHandler> create, int output) {}

    static Stream<Layout> layouts() {
        return Stream.of(new Layout(LightningSimulationChamberInventory::new, 4),
                new Layout(LightningAssemblyChamberInventory::new, 10),
                new Layout(OverloadProcessingFactoryInventory::new, 10),
                new Layout(MiningFactoryInventory::new, 2));
    }

    @ParameterizedTest @MethodSource("layouts")
    void unknownReceiptAfterCreditIsNeverReplayedOrRestored(Layout layout) {
        for (int fault = 0; fault < 5; fault++) {
            var inv = layout.create().apply(null);
            inv.setItemDirect(layout.output(), new ItemStack(Items.STONE, 64));
            long[] received = {0};
            int[] calls = {0};
            int selected = fault;
            assertDoesNotThrow(() -> {
                for (int retry = 0; retry < 1000; retry++) {
                    inv.exportOutput(layout.output(), offer -> {
                        calls[0]++;
                        received[0] += offer.getCount();
                        return switch (selected) {
                            case 0 -> -1;
                            case 1 -> offer.getCount() + 1L;
                            case 2 -> Long.MAX_VALUE;
                            case 3 -> throw new IllegalStateException("failure after credit");
                            default -> throw new NoSuchMethodError("ABI failure after credit");
                        };
                    });
                }
            });
            assertEquals(64, received[0]);
            assertEquals(1, calls[0]);
            assertTrue(inv.getStackInSlot(layout.output()).isEmpty());
            var saved = new CompoundTag();
            inv.saveToTag(saved, "items");
            var restored = layout.create().apply(null);
            restored.loadFromTag(saved, "items");
            assertTrue(restored.getStackInSlot(layout.output()).isEmpty());
            inv.setItemDirect(layout.output(), new ItemStack(Items.DIRT, 3));
            assertEquals(3, inv.extractItem(layout.output(), 3, false).getCount(),
                    "the reservation must unlock after every fault");
        }
    }

    @ParameterizedTest @MethodSource("layouts")
    void snapshotDuringRejectionGetsUpdatedAfterRemainderRestoration(Layout layout) {
        for (int receipt : new int[] {0, 17, 64}) {
            var persisted = new CompoundTag();
            LargeStackItemHandler[] holder = {null};
            int[] notices = {0};
            var inv = layout.create().apply(() -> {
                notices[0]++;
                holder[0].saveToTag(persisted, "items");
            });
            holder[0] = inv;
            var named = new ItemStack(Items.STONE, 64);
            named.setHoverName(Component.literal("Owned output"));
            inv.setItemDirect(layout.output(), named);
            notices[0] = 0;
            assertEquals(receipt, inv.exportOutput(layout.output(), offer -> {
                inv.saveToTag(persisted, "items");
                return receipt;
            }));
            var restored = layout.create().apply(null);
            restored.loadFromTag(persisted, "items");
            assertEquals(64 - receipt, restored.getStackInSlot(layout.output()).getCount(),
                    "a callback snapshot omitted the reserved output and needs a new save");
            if (receipt < 64) assertTrue(ItemStack.isSameItemSameTags(named,
                    restored.getStackInSlot(layout.output())));
            assertEquals(1, notices[0]);
        }
    }

    @ParameterizedTest @MethodSource("layouts")
    void ordinaryRejectionDoesNotNotifyOnEveryRetry(Layout layout) {
        int[] notices = {0};
        var inv = layout.create().apply(() -> notices[0]++);
        inv.setItemDirect(layout.output(), new ItemStack(Items.STONE, 64));
        notices[0] = 0;
        for (int retry = 0; retry < 1000; retry++) {
            assertEquals(0, inv.exportOutput(layout.output(), offer -> 0));
        }
        assertEquals(0, notices[0]);
        assertEquals(64, inv.getStackInSlot(layout.output()).getCount());
    }
}
