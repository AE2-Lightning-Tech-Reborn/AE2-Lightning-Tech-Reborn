package com.moakiee.ae2lt.overload.runtime.cpu;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.UUID;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.overload.runtime.model.EncodedOverloadPattern;
import com.moakiee.ae2lt.overload.runtime.model.MatchMode;
import com.moakiee.ae2lt.overload.runtime.pattern.OverloadPatternDetails;
import com.moakiee.ae2lt.overload.runtime.pattern.ParsedPatternDefinition;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OverloadActualOutputAmountsTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void dynamicAmountsKeepLongPrecisionAcrossSimulationAndPersistence() {
        var manager = OverloadCpuStateManager.INSTANCE;
        var owner = new Object(); var restored = new Object(); var id = UUID.randomUUID();
        var key = AEItemKey.of(Items.DIAMOND);
        var details = details(1);
        var reference = new OverloadPatternReference("dynamic-long-amount", details.sourcePattern());
        long perCopy = (long) Integer.MAX_VALUE + 37;
        long total = perCopy * 3;
        try {
            manager.registerActualOutputAmounts(owner, id, reference, details, List.of(new GenericStack(key, perCopy)), key, 3);
            assertEquals(total, manager.getRemainingForExactKey(owner, key));
            assertEquals(perCopy, manager.claim(owner, key, perCopy, Actionable.SIMULATE).claimedAmount());
            assertEquals(total, manager.getRemainingForExactKey(owner, key));
            var saved = manager.writeToTag(owner);
            manager.readFromTag(restored, id, saved);
            assertEquals(total, manager.getRemainingForExactKey(restored, key));
            assertEquals(total, manager.claim(restored, key, Long.MAX_VALUE, Actionable.MODULATE).claimedAmount());
            assertFalse(manager.hasAnyPending(restored));
        } finally { manager.clear(owner); manager.clear(restored); }
    }

    @Test void existingOverloadRegistrationRetainsItsEditorQuantityContract() {
        var manager = OverloadCpuStateManager.INSTANCE;
        var owner = new Object(); var key = AEItemKey.of(Items.DIAMOND);
        var details = details(2);
        try {
            manager.registerExpectedOutputs(owner, UUID.randomUUID(),
                    new OverloadPatternReference("existing-overload", details.sourcePattern()), details,
                    List.of(new GenericStack(key, 37)), key, 3);
            assertEquals(6, manager.getRemainingForExactKey(owner, key));
        } finally { manager.clear(owner); }
    }

    private static OverloadPatternDetails details(int quantity) {
        return new OverloadPatternDetails(ParsedPatternDefinition.builder(new ItemStack(Items.PAPER))
                .output(0, new ItemStack(Items.DIAMOND, quantity), true).build(),
                EncodedOverloadPattern.builder().output(0, MatchMode.ID_ONLY).build());
    }
}
