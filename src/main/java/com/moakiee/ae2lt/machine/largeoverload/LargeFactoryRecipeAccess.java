package com.moakiee.ae2lt.machine.largeoverload;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;

/** Source recipe type controls access; the recipe ID namespace or output item never grants access. */
public record LargeFactoryRecipeAccess(LargeFactoryComponent core, Set<Process> unlocked) {
    public LargeFactoryRecipeAccess {
        Objects.requireNonNull(core, "core");
        if (!core.isCore()) throw new IllegalArgumentException("Not a central factory core: " + core);
        unlocked = Set.copyOf(unlocked);
    }

    public boolean allows(ResourceLocation sourceRecipeType) {
        return Arrays.stream(Process.values()).filter(p -> p.type().equals(sourceRecipeType)).findFirst()
                .map(this::allows).orElse(false);
    }

    public boolean allows(Process process) {
        Objects.requireNonNull(process, "process");
        if (core == LargeFactoryComponent.FIRMAMENT_CORE) return process == Process.FIRMAMENT;
        return process != Process.FIRMAMENT && (process.base() || unlocked.contains(process));
    }

    /** Access alone never means an adapter can safely execute every recipe in this type. */
    public enum Process {
        OVERLOAD("ae2lt:overload_processing", true),
        REACTION("advanced_ae:reaction", true),
        SIMULATION("ae2lt:lightning_simulation", false),
        ASSEMBLY("ae2lt:lightning_assembly", false),
        CATALYZER("ae2lt:crystal_catalyzer", false),
        INTEGRATED_WORKSTATION("neoecoae:integrated_working_station", false),
        CRYSTAL_AGGREGATOR("ae2cs:crystal_aggregator_recipe", false),
        CRYSTAL_PULVERIZER("ae2cs:crystal_pulverizer_recipe", false),
        CIRCUIT_ETCHER("ae2cs:circuit_etcher_recipe", false),
        CRYSTAL_ASSEMBLER("extendedae:crystal_assembler", false),
        FIRMAMENT("ae2lt:firmament_conversion", false);

        private final ResourceLocation type;
        private final boolean base;

        Process(String type, boolean base) {
            this.type = new ResourceLocation(type);
            this.base = base;
        }

        public ResourceLocation type() { return type; }
        public boolean base() { return base; }
    }
}
