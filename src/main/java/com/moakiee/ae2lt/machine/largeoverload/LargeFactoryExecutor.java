package com.moakiee.ae2lt.machine.largeoverload;

import java.util.LinkedHashMap;
import java.util.Map;
import appeng.api.config.Actionable;
import appeng.api.config.PowerUnit;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEKey;
import com.moakiee.ae2lt.crafting.runtime.api.DeferredCraftingProvider;
import com.moakiee.ae2lt.me.key.LightningKey;

/** Synchronous, server-thread transaction. Quotes do not transfer ownership; actual receipts do. */
public final class LargeFactoryExecutor {
    private LargeFactoryExecutor() { }
    public record Quote(long copies, long operations, long energy, long externalEnergy,
            LargeFactoryLightningCost.Payment lightning, LargeFactoryRecipe recipe, IGrid grid, long capabilityVersion) {
        Quote limitCopies(long limit) {
            if (limit == copies) return this;
            if (limit <= 0 || limit > copies) throw new IllegalArgumentException("Invalid reduced quote");
            long reducedOperations = Math.multiplyExact(operations / copies, limit);
            long reducedEnergy = Math.multiplyExact(energy / copies, limit);
            return new Quote(limit, reducedOperations, reducedEnergy, Math.min(externalEnergy, reducedEnergy),
                    recipe.lightning().plan(reducedOperations, lightning.highVoltage(), lightning.extremeHighVoltage()).orElseThrow(),
                    recipe, grid, capabilityVersion);
        }
    }

    public static Quote quote(LargeFactoryHatchBlockEntity hatch, LargeFactoryHatchBlockEntity.Entry entry,
            Map<AEKey, Long> inputs, long requested) {
        if (requested <= 0 || !hatch.ready()) return null;
        var controller = hatch.controller();
        return quote(hatch, entry, inputs, requested, controller);
    }
    private static Quote quote(LargeFactoryHatchBlockEntity hatch, LargeFactoryHatchBlockEntity.Entry entry,
            Map<AEKey, Long> inputs, long requested, LargeFactoryControllerBlockEntity controller) {
        if (requested <= 0) return null;
        var recipe = hatch.bindRecipe(entry, inputs);
        var account = hatch.account();
        var grid = hatch.getMainNode().getGrid();
        if (recipe == null || account == null || grid == null || hatch.origin(grid) == null) return null;
        long operationsPerCopy = entry.operations();
        long copies = Math.min(requested, controller.budget().remainingOperations(hatch.getLevel().getGameTime()) / operationsPerCopy);
        for (long amount : inputs.values()) copies = Math.min(copies, Long.MAX_VALUE / amount);
        for (long amount : entry.outputs.values()) copies = Math.min(copies, Long.MAX_VALUE / amount);
        long energy;
        try { energy = LargeFactoryConfig.energy(recipe); }
        catch (ArithmeticException overflow) { hatch.status("cost_overflow"); return null; }
        if (energy > 0) copies = Math.min(copies, controller.remainingEnergyThroughput() / energy / operationsPerCopy);
        if (copies <= 0) return null;
        long operations = Math.multiplyExact(copies, operationsPerCopy);
        var cost = recipe.lightning();
        long extreme = cost.extremeHighVoltage() == 0 ? 0
                : hatch.extract(grid, LightningKey.EXTREME_HIGH_VOLTAGE, Long.MAX_VALUE, Actionable.SIMULATE);
        long high = cost.highVoltage() > 0 || cost.extremeHighVoltage() > 0 && extreme / cost.extremeHighVoltage() < operations
                ? hatch.extract(grid, LightningKey.HIGH_VOLTAGE, Long.MAX_VALUE, Actionable.SIMULATE) : 0;
        copies = Math.min(copies, recipe.lightning().maxPayableOperations(operations, high, extreme) / operationsPerCopy);
        if (copies <= 0) { hatch.status("missing_lightning"); return null; }
        operations = Math.multiplyExact(copies, operationsPerCopy);
        long totalEnergy = Math.multiplyExact(energy, operations);
        if (totalEnergy > controller.energyStored()) {
            long deficit = totalEnergy - controller.energyStored();
            double availableAE = account.energyCreditAE;
            if (controller.allowNetworkEnergy()) {
                double need = Math.max(0, PowerUnit.FE.convertTo(PowerUnit.AE, deficit) - availableAE);
                availableAE += hatch.extractEnergy(grid, need, Actionable.SIMULATE);
            }
            double availableFE = PowerUnit.AE.convertTo(PowerUnit.FE, availableAE);
            long affordable = availableFE >= deficit ? totalEnergy
                    : Math.addExact(controller.energyStored(), (long) Math.max(0, Math.floor(availableFE)));
            copies = Math.min(copies, affordable / energy / operationsPerCopy);
            if (copies <= 0) { hatch.status("missing_energy"); return null; }
        }
        operations = Math.multiplyExact(copies, operationsPerCopy);
        totalEnergy = Math.multiplyExact(energy, operations);
        var payment = recipe.lightning().plan(operations, high, extreme).orElseThrow();
        return new Quote(copies, operations, totalEnergy, Math.min(totalEnergy, controller.energyStored()), payment,
                recipe, grid, controller.capabilityVersion());
    }

    public static long execute(LargeFactoryHatchBlockEntity hatch, LargeFactoryHatchBlockEntity.Entry entry,
            Map<AEKey, Long> inputs, long requested, DeferredCraftingProvider.OutputSink returns, boolean passive) {
        return execute(hatch, entry, inputs, requested, returns, passive, null);
    }
    static long executePrepared(LargeFactoryHatchBlockEntity hatch, LargeFactoryHatchBlockEntity.Entry entry,
            Map<AEKey, Long> inputs, Quote quote) {
        return execute(hatch, entry, inputs, quote.copies(), null, true, quote);
    }
    private static long execute(LargeFactoryHatchBlockEntity hatch, LargeFactoryHatchBlockEntity.Entry entry,
            Map<AEKey, Long> inputs, long requested, DeferredCraftingProvider.OutputSink returns, boolean passive, Quote prepared) {
        if (!hatch.ready() || (!passive && hatch.passive()) || hatch.processing()
                || !LargeFactoryWorkBudget.take(hatch, LargeFactoryWorkBudget.Work.DISPATCH)) return 0;
        var controller = hatch.controller();
        var account = hatch.account();
        if (account == null || (!passive && !account.resources.isEmpty()) || !controller.enterExecution()) return 0;
        hatch.processing(true);
        try {
            long generation = controller.capabilityVersion();
            var grid = hatch.getMainNode().getGrid();
            // Readiness was checked at entry. Revalidate again after pricing, which can call ME.
            var quote = prepared == null ? quote(hatch, entry, inputs, requested, controller) : prepared;
            if (quote == null || !current(hatch, controller, grid, generation)) return 0;
            if (prepared != null && (quote.grid() != grid || quote.capabilityVersion() != generation
                    || hatch.bindRecipe(entry, inputs) != quote.recipe()
                    || LargeFactoryConfig.energy(quote.recipe()) != quote.energy() / quote.operations())) return 0;
            long nextCommit = Math.incrementExact(account.commitSequence);
            var origin = hatch.origin(grid);
            if (origin == null) return 0;
            var outputs = LargeFactoryAmounts.scale(entry.outputs, quote.copies());
            var consumedInputs = passive ? LargeFactoryAmounts.scale(inputs, quote.copies()) : Map.<AEKey, Long>of();
            // Check future resource amounts before making any new withdrawals.
            // Active dispatch requires an empty account. Its post-commit resources are exactly
            // the outputs, so it needs no second temporary copy of the same amounts.
            Map<AEKey, Long> after = outputs;
            if (passive) {
                var retained = new LinkedHashMap<>(account.resources);
                subtract(retained, consumedInputs);
                outputs.forEach((key, amount) -> LargeFactoryAmounts.add(retained, key, amount));
                after = retained;
            }
            try (var reservation = controller.budget().reserve(hatch.getLevel().getGameTime(), quote.copies(), entry.operations())) {
                if (reservation.copies() != quote.copies()) return 0;
                account.origin = origin;
                hatch.ledgerChanged();
                if (!withdraw(hatch, grid, LightningKey.EXTREME_HIGH_VOLTAGE, quote.lightning().extremeHighVoltage())
                        || !withdraw(hatch, grid, LightningKey.HIGH_VOLTAGE, quote.lightning().highVoltage())) {
                    hatch.status("payment_changed");
                    return 0;
                }
                double neededAE = PowerUnit.FE.convertTo(PowerUnit.AE, quote.energy() - quote.externalEnergy());
                double shortfall = Math.max(0, neededAE - account.energyCreditAE);
                if (shortfall > 0 && controller.allowNetworkEnergy()) {
                    double actual = hatch.extractEnergy(grid, shortfall, Actionable.MODULATE);
                    if (!Double.isFinite(actual) || actual < 0 || actual > shortfall + 0.0000001) throw new IllegalStateException("Invalid ME energy receipt");
                    account.energyCreditAE += actual;
                    hatch.ledgerChanged();
                }
                if (account.energyCreditAE < neededAE || !current(hatch, controller, grid, generation)
                        || !hatch.hasCatalyst(entry.bound()) || controller.energyStored() < quote.externalEnergy()
                        || controller.remainingEnergyThroughput() < quote.energy()) {
                    hatch.status("payment_changed");
                    return 0;
                }
                // The only commit point. Materials, lightning and energy are consumed once; products become durable here.
                controller.consumeEnergy(quote.externalEnergy(), quote.energy());
                account.resources.clear();
                account.resources.putAll(after);
                account.energyCreditAE -= neededAE;
                account.commitSequence = nextCommit;
                account.lastRecipe = entry.bound().id().toString();
                account.lastOperations = quote.operations();
                account.lastEnergyFE = quote.energy();
                account.lastHigh = quote.lightning().highVoltage();
                account.lastExtreme = quote.lightning().extremeHighVoltage();
                reservation.commit(quote.copies());
                hatch.deliverAfter(hatch.getLevel().getGameTime() + (passive ? 0 : 1));
                hatch.ledgerChanged();
                controller.completed();
                hatch.status("working");
                if (returns != null && enqueue(returns, outputs)) {
                    subtract(account.resources, outputs);
                    hatch.ledgerChanged();
                }
                return quote.copies();
            }
        } catch (ArithmeticException invalidAmount) {
            hatch.status("cost_overflow");
            return 0;
        } finally {
            hatch.processing(false);
            controller.leaveExecution();
        }
    }

    private static boolean enqueue(DeferredCraftingProvider.OutputSink returns, Map<AEKey, Long> outputs) {
        if (outputs.size() == 1) {
            var output = outputs.entrySet().iterator().next();
            return returns.enqueue(output.getKey(), output.getValue());
        }
        return returns.enqueue(LargeFactoryAmounts.counter(outputs));
    }

    private static boolean current(LargeFactoryHatchBlockEntity hatch, LargeFactoryControllerBlockEntity controller,
            IGrid grid, long generation) {
        return hatch.ready(controller) && hatch.getMainNode().getGrid() == grid
                && controller.capabilityVersion() == generation;
    }
    private static boolean withdraw(LargeFactoryHatchBlockEntity hatch, IGrid grid, AEKey key, long amount) {
        if (amount == 0) return true;
        long actual = hatch.extract(grid, key, amount, Actionable.MODULATE);
        if (actual < 0 || actual > amount) throw new IllegalStateException("Invalid ME extraction receipt");
        if (actual > 0) {
            LargeFactoryAmounts.add(hatch.account().resources, key, actual);
            hatch.ledgerChanged();
        }
        return actual == amount;
    }
    static void subtract(Map<AEKey, Long> from, Map<AEKey, Long> amounts) {
        for (var entry : amounts.entrySet()) {
            long previous = from.getOrDefault(entry.getKey(), 0L);
            if (previous < entry.getValue()) throw new IllegalStateException("Factory ownership underflow");
            if (previous == entry.getValue()) from.remove(entry.getKey());
            else from.put(entry.getKey(), previous - entry.getValue());
        }
    }
}
