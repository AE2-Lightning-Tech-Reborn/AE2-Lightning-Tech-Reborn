package com.moakiee.ae2ltcpuselection;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.CraftingLink;
import appeng.crafting.inv.ListCraftingInventory;
import com.moakiee.ae2lt.api.compat.ValueIO;
import com.moakiee.ae2lt.overload.runtime.cpu.OverloadCpuStateManager;
import com.moakiee.ae2lt.overload.runtime.cpu.OverloadPatternReference;
import com.moakiee.ae2lt.overload.runtime.model.EncodedOverloadPattern;
import com.moakiee.ae2lt.overload.runtime.model.MatchMode;
import com.moakiee.ae2lt.overload.runtime.pattern.OverloadPatternDetails;
import com.moakiee.ae2lt.overload.runtime.pattern.ParsedPatternDefinition;
import com.moakiee.ae2lt.overload.runtime.pattern.ParsedPatternOutput;
import com.moakiee.ae2lt.overload.runtime.pattern.SourcePatternSnapshot;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Exercises native job persistence and transformed overload hooks with nonempty waiting state. */
final class CpuPendingPersistenceGameTests {
    private CpuPendingPersistenceGameTests() {}

    static void roundTrip(GameTestHelper helper, ICraftingCPU cpu, Object original, Object restored,
                          Consumer<ValueInput> readOriginal, Consumer<ValueOutput> writeOriginal,
                          Consumer<ValueInput> readRestored, Consumer<ValueOutput> writeRestored) {
        var registries = helper.getLevel().registryAccess();
        var manager = OverloadCpuStateManager.INSTANCE;
        var craftingId = UUID.randomUUID();
        var output = new ItemStack(Items.DIAMOND_PICKAXE);
        output.set(DataComponents.CUSTOM_NAME, Component.literal("Pending output template"));
        output.setDamageValue(7);
        var key = AEItemKey.of(output);
        long amount = 37;

        // The native serializer creates the fixture's link and waiting inventory entries.
        var fixture = new CompoundTag();
        var job = ValueIO.output(fixture, registries).child("job");
        new CraftingLink(craftingId, true, cpu).writeToNBT(job.child("link"));
        job.store("finalOutput", GenericStack.CODEC, new GenericStack(key, amount));
        job.putLong("remainingAmount", amount);
        var waiting = new ListCraftingInventory(ignored -> {});
        waiting.insert(key, amount, Actionable.MODULATE);
        waiting.serialize(job.childrenList("waitingFor"));
        readOriginal.accept(ValueIO.input(fixture, registries));

        var source = new SourcePatternSnapshot(Identifier.fromNamespaceAndPath(
                CpuSelectionTestMod.MODID, "pending_output"), null, null);
        var details = new OverloadPatternDetails(new ParsedPatternDefinition(source, List.of(),
                List.of(new ParsedPatternOutput(0, output, true))),
                EncodedOverloadPattern.builder().output(0, MatchMode.ID_ONLY).build());
        manager.registerExpectedOutputs(original, craftingId,
                new OverloadPatternReference(details.overloadPatternIdentity(), source), details,
                List.of(new GenericStack(key, 1)), key, amount);
        try {
            var saved = new CompoundTag();
            writeOriginal.accept(ValueIO.output(saved, registries));
            helper.assertTrue(!saved.getCompoundOrEmpty("ae2ltOverloadState").isEmpty(),
                    "Native CPU write must include pending overload outputs");
            manager.clear(original);
            readRestored.accept(ValueIO.input(saved, registries));
            var savedAgain = new CompoundTag();
            writeRestored.accept(ValueIO.output(savedAgain, registries));
            helper.assertTrue(saved.getCompoundOrEmpty("job").equals(savedAgain.getCompoundOrEmpty("job")),
                    "Native job, link, remaining amount and waiting inventory must survive reload");
            helper.assertTrue(saved.getCompoundOrEmpty("ae2ltOverloadState")
                            .equals(savedAgain.getCompoundOrEmpty("ae2ltOverloadState")),
                    "Pending output identity, components, amount and routing must survive reload");

            // ID_ONLY still accepts a different concrete component variant after the reload.
            var incoming = AEItemKey.of(Items.DIAMOND_PICKAXE);
            var simulated = manager.claim(restored, incoming, amount + 1, Actionable.SIMULATE);
            helper.assertTrue(simulated.claimedAmount() == amount
                            && simulated.claimedForRequester() == amount
                            && simulated.claims().getFirst().key().craftingId().equals(craftingId)
                            && simulated.claims().getFirst().exactExpectedKey().equals(key),
                    "Restored claims must retain ownership, amount, components and requester routing");
            helper.assertTrue(manager.claim(restored, incoming, 11, Actionable.MODULATE).claimedAmount() == 11,
                    "Partial returned output must decrement restored pending amount");
            helper.assertTrue(manager.claim(restored, incoming, amount, Actionable.MODULATE)
                            .claimedAmount() == amount - 11,
                    "Remaining claim must be capped by the restored pending amount");
            helper.assertTrue(manager.claim(restored, incoming, 1, Actionable.MODULATE).claimedAmount() == 0,
                    "Fully claimed output must not be claimed twice");
        } finally {
            manager.clear(original);
            manager.clear(restored);
        }
    }
}
