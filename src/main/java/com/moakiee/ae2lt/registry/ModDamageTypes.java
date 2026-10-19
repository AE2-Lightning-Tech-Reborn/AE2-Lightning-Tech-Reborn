package com.moakiee.ae2lt.registry;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.EntityDamageSource;
import net.minecraft.world.entity.Entity;

/** 1.19.2 expresses damage properties on the source rather than in a data registry. */
public final class ModDamageTypes {
    public static final String ELECTROMAGNETIC = "electromagnetic";
    public static final String ELECTROMAGNETIC_EHV_BEAM = "electromagnetic_ehv_beam";
    private ModDamageTypes() {}
    public static DamageSource electromagnetic(Entity attacker, boolean ehv) {
        return new EntityDamageSource(ehv ? ELECTROMAGNETIC_EHV_BEAM : ELECTROMAGNETIC, attacker).bypassArmor();
    }
    public static DamageSource genericKill() {
        return new DamageSource("genericKill").bypassArmor().bypassInvul().bypassMagic();
    }
    public static void clearCache() { }
}
